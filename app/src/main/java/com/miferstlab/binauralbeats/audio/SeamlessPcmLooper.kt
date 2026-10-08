package com.miferstlab.binauralbeats.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * One pass of an ambient clip as decoded 16-bit PCM (MediaCodec in the app, a fake in unit tests).
 *
 * Output is always interleaved stereo ([SeamlessPcmLooper.CHANNELS] samples per frame).
 */
interface PcmPassSource {
    /** (Re)opens the clip at its very first sample. May throw. */
    fun open()

    /**
     * Reads the next decoded samples into [dst].
     * @return number of samples written (> 0), 0 when nothing is ready yet, or [END_OF_PASS].
     * May throw on a decoder failure.
     */
    fun read(dst: ShortArray): Int

    /** Releases decoder resources. Must not throw. */
    fun close()

    companion object {
        const val END_OF_PASS = -1
    }
}

/** Blocking PCM output (an AudioTrack in the app). */
fun interface PcmSink {
    /** @return false if the output failed; the host may rebuild it, the pump keeps going. */
    fun write(data: ShortArray, length: Int): Boolean
}

/**
 * Turns repeated passes of the same clip into one endless PCM stream with an equal-power crossfade
 * of [crossfadeFrames] at every wrap.
 *
 * The last [crossfadeFrames] of each pass are held back in a delay line; when the pass ends they
 * are mixed (cos gain) with the first [crossfadeFrames] of the next pass (sin gain). The result is
 *
 *   clip[0 ‥ L−X) · mix(clip[L−X ‥ L), clip[0 ‥ X)) · clip[X ‥ L−X) · mix(…) · clip[X ‥ L−X) · …
 *
 * which is exactly what the 1.5.x dual-player crossfade aimed for, but sample-accurate and with
 * no player that can reach "ended": the stream only stops when the host stops pumping.
 */
class SeamlessPcmLooper(
    private val crossfadeFrames: Int,
    outputChunkSamples: Int = 4096,
) {
    init {
        require(crossfadeFrames > 0) { "crossfadeFrames must be > 0" }
    }

    private val delayCapacity = crossfadeFrames * CHANNELS
    private val delay = ShortArray(delayCapacity)
    private var delayStart = 0
    private var delaySize = 0

    /** Tail of the previous pass, being mixed into the head of the current one. */
    private val tail = ShortArray(delayCapacity)
    private var tailSize = 0
    private var tailPos = 0

    private val out = ShortArray(outputChunkSamples.coerceAtLeast(CHANNELS) / CHANNELS * CHANNELS)
    private var outSize = 0

    /** Completed passes (wraps), for logs and tests. */
    var passes: Int = 0
        private set

    /** Total samples handed to the sink. */
    var samplesOut: Long = 0
        private set

    val isMixing: Boolean get() = tailPos < tailSize

    /** Feeds decoded samples of the current pass (interleaved stereo). */
    fun feed(data: ShortArray, length: Int, sink: PcmSink) {
        var i = 0
        val n = length - length % CHANNELS
        while (i < n) {
            val s = data[i]
            if (tailPos < tailSize) {
                emit(mix(tail[tailPos], s, (tailPos / CHANNELS), tailSize / CHANNELS), sink)
                tailPos++
            } else {
                push(s, sink)
            }
            i++
        }
        flush(sink)
    }

    /**
     * The current pass ended (or was cut short by a decoder failure): hold its last samples as the
     * tail that the next pass fades in over. Calling it again before new samples arrive is a no-op.
     */
    fun endOfPass(sink: PcmSink) {
        if (delaySize == 0) {
            // Nothing new since the previous wrap (e.g. retry after a failed reopen).
            return
        }
        if (tailPos < tailSize) {
            // The pass was shorter than the crossfade: finish fading the old tail out over silence.
            while (tailPos < tailSize) {
                emit(mix(tail[tailPos], 0, tailPos / CHANNELS, tailSize / CHANNELS), sink)
                tailPos++
            }
        }
        // Move the delay line (in order) into the tail buffer.
        for (k in 0 until delaySize) {
            tail[k] = delay[(delayStart + k) % delayCapacity]
        }
        tailSize = delaySize
        tailPos = 0
        delayStart = 0
        delaySize = 0
        passes++
        flush(sink)
    }

    private fun push(s: Short, sink: PcmSink) {
        if (delaySize == delayCapacity) {
            emit(delay[delayStart], sink)
            delay[delayStart] = s
            delayStart = (delayStart + 1) % delayCapacity
        } else {
            delay[(delayStart + delaySize) % delayCapacity] = s
            delaySize++
        }
    }

    private fun emit(s: Short, sink: PcmSink) {
        out[outSize++] = s
        if (outSize == out.size) flush(sink)
    }

    private fun flush(sink: PcmSink) {
        if (outSize == 0) return
        sink.write(out, outSize)
        samplesOut += outSize
        outSize = 0
    }

    companion object {
        const val CHANNELS = 2

        /** Equal-power mix of [outgoing] (fading out) and [incoming] (fading in) at [frame] of [frames]. */
        fun mix(outgoing: Short, incoming: Short, frame: Int, frames: Int): Short {
            val t = (frame + 0.5) / frames
            val v = outgoing * cos(HALF_PI * t) + incoming * sin(HALF_PI * t)
            return v.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
        }

        private const val HALF_PI = Math.PI / 2.0
    }
}

/**
 * Drives a [PcmPassSource] through endless passes into a [PcmSink] via [SeamlessPcmLooper].
 *
 * Every failure (open, decode) is treated like an early end of pass: the source is reopened at its
 * start and the next pass fades in over what was already decoded. Nothing here can end playback;
 * only the host stops calling [step].
 */
class AmbientLoopPump(
    private val source: PcmPassSource,
    private val sink: PcmSink,
    crossfadeFrames: Int,
    readChunkSamples: Int = 4096,
) {
    val looper = SeamlessPcmLooper(crossfadeFrames)
    private val buffer = ShortArray(readChunkSamples)
    private var opened = false
    private var consecutiveFailures = 0
    private var idleReads = 0

    /** Source failures recovered from (open or read threw). */
    var failures: Int = 0
        private set

    val passes: Int get() = looper.passes

    /**
     * Performs one unit of work.
     * @return milliseconds the host should wait before the next call (0 = immediately).
     */
    fun step(): Long {
        if (!opened) {
            try {
                source.open()
                opened = true
            } catch (e: Exception) {
                return onFailure()
            }
        }
        val n = try {
            source.read(buffer)
        } catch (e: Exception) {
            return onFailure()
        }
        return when {
            n == PcmPassSource.END_OF_PASS -> {
                looper.endOfPass(sink)
                closeSource()
                consecutiveFailures = 0
                idleReads = 0
                0L
            }
            n > 0 -> {
                looper.feed(buffer, n, sink)
                consecutiveFailures = 0
                idleReads = 0
                0L
            }
            else -> {
                // Decoder has nothing yet; avoid a hot spin but stay responsive.
                if (++idleReads > MAX_IDLE_READS) onFailure() else 1L
            }
        }
    }

    /** Releases the source (host shutdown). */
    fun close() {
        closeSource()
    }

    private fun onFailure(): Long {
        failures++
        consecutiveFailures++
        idleReads = 0
        closeSource()
        looper.endOfPass(sink)
        return when {
            consecutiveFailures <= 3 -> 20L
            consecutiveFailures <= 10 -> 250L
            else -> 1000L
        }
    }

    private fun closeSource() {
        if (!opened) return
        opened = false
        try {
            source.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Empty reads in a row (each already waits on the decoder) before the source is reopened. */
        const val MAX_IDLE_READS = 50
    }
}
