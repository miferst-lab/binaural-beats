package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Decodes one pass of an `assets/` audio clip (OGG/Vorbis beds) to interleaved stereo 16-bit PCM
 * with the platform [MediaExtractor] + [MediaCodec].
 *
 * Every [open] creates a fresh extractor and decoder, so a wrap never depends on a decoder seeking,
 * flushing or being reused across the end of the stream. Mono is duplicated to stereo, extra
 * channels are dropped, float PCM is converted. Single-threaded (the ambient worker thread).
 */
class MediaCodecPassSource(
    private val context: Context,
    private val assetPath: String,
) : PcmPassSource {

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private val info = MediaCodec.BufferInfo()
    private var inputEos = false
    private var outputEos = false
    private var dryDequeues = 0

    /** Sample rate of the decoded PCM (valid after [open]). */
    @Volatile var sampleRate: Int = 44100
        private set

    private var channels: Int = 2
    private var floatPcm: Boolean = false

    private var pending = ShortArray(0)
    private var pendingPos = 0
    private var pendingLen = 0

    override fun open() {
        close()
        val ex = MediaExtractor()
        try {
            setDataSource(ex)
            val track = (0 until ex.trackCount).firstOrNull { i ->
                ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("no audio track in $assetPath")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            floatPcm = false
            val dec = MediaCodec.createDecoderByType(mime)
            try {
                dec.configure(format, null, null, 0)
                dec.start()
            } catch (e: Exception) {
                dec.release()
                throw e
            }
            extractor = ex
            codec = dec
            inputEos = false
            outputEos = false
            dryDequeues = 0
            pendingPos = 0
            pendingLen = 0
        } catch (e: Exception) {
            ex.release()
            throw e
        }
    }

    override fun read(dst: ShortArray): Int {
        if (pendingPos < pendingLen) return drainPending(dst)
        if (outputEos) return PcmPassSource.END_OF_PASS
        val ex = extractor ?: throw IllegalStateException("not open")
        val dec = codec ?: throw IllegalStateException("not open")
        repeat(MAX_ATTEMPTS_PER_READ) {
            val n = decodeStep(ex, dec, dst)
            if (n != 0) return n
        }
        return 0
    }

    /** One input + one output dequeue. @return samples, 0 (nothing yet) or END_OF_PASS. */
    private fun decodeStep(ex: MediaExtractor, dec: MediaCodec, dst: ShortArray): Int {
        if (!inputEos) {
            val inIdx = dec.dequeueInputBuffer(0L)
            if (inIdx >= 0) {
                val buf = dec.getInputBuffer(inIdx) ?: throw IllegalStateException("null input buffer")
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    dec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputEos = true
                } else {
                    dec.queueInputBuffer(inIdx, 0, size, ex.sampleTime.coerceAtLeast(0L), 0)
                    ex.advance()
                }
            }
        }

        val outIdx = dec.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
            outIdx >= 0 -> {
                dryDequeues = 0
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (info.size > 0) {
                    val buf = dec.getOutputBuffer(outIdx)
                    if (buf != null) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        convert(buf.slice().order(ByteOrder.nativeOrder()))
                    }
                }
                dec.releaseOutputBuffer(outIdx, false)
                if (eos) outputEos = true
                if (pendingPos < pendingLen) return drainPending(dst)
                return if (eos) PcmPassSource.END_OF_PASS else 0
            }
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val f = dec.outputFormat
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                return 0
            }
            else -> {
                if (++dryDequeues > MAX_DRY_DEQUEUES) throw IllegalStateException("decoder stalled")
                return 0
            }
        }
    }

    override fun close() {
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        try {
            extractor?.release()
        } catch (_: Exception) {
        }
        codec = null
        extractor = null
        pendingPos = 0
        pendingLen = 0
    }

    private fun setDataSource(ex: MediaExtractor) {
        try {
            context.assets.openFd(assetPath).use { afd ->
                ex.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        } catch (e: java.io.IOException) {
            // Asset stored compressed: decode from a one-time copy in the cache directory instead.
            val copy = File(context.cacheDir, "ambient_" + assetPath.substringAfterLast('/'))
            if (!copy.exists() || copy.length() == 0L) {
                val tmp = File(copy.path + ".tmp")
                context.assets.open(assetPath).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(copy)
            }
            ex.setDataSource(copy.path)
        }
    }

    private fun convert(buf: java.nio.ByteBuffer) {
        val inCh = channels.coerceAtLeast(1)
        val frames = if (floatPcm) buf.remaining() / 4 / inCh else buf.remaining() / 2 / inCh
        val needed = frames * SeamlessPcmLooper.CHANNELS
        if (pending.size < needed) pending = ShortArray(needed)
        if (floatPcm) {
            val fb = buf.asFloatBuffer()
            for (f in 0 until frames) {
                val l = fb.get(f * inCh)
                val r = if (inCh > 1) fb.get(f * inCh + 1) else l
                pending[f * 2] = floatToShort(l)
                pending[f * 2 + 1] = floatToShort(r)
            }
        } else {
            val sb = buf.asShortBuffer()
            for (f in 0 until frames) {
                val l = sb.get(f * inCh)
                pending[f * 2] = l
                pending[f * 2 + 1] = if (inCh > 1) sb.get(f * inCh + 1) else l
            }
        }
        pendingPos = 0
        pendingLen = needed
    }

    private fun drainPending(dst: ShortArray): Int {
        val n = minOf(dst.size - dst.size % SeamlessPcmLooper.CHANNELS, pendingLen - pendingPos)
        System.arraycopy(pending, pendingPos, dst, 0, n)
        pendingPos += n
        return n
    }

    private fun floatToShort(v: Float): Short =
        (v.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val MAX_ATTEMPTS_PER_READ = 32

        /** ~5 s of empty 10 ms dequeues: the decoder is considered stuck and the pass is reopened. */
        private const val MAX_DRY_DEQUEUES = 500
    }
}
