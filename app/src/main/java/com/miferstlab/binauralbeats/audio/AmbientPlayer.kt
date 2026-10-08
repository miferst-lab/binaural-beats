package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.FrequencyMath

/**
 * Endless ambient bed: the clip is decoded pass after pass on a worker thread and written to our
 * own streaming [AudioTrack]; [SeamlessPcmLooper] crossfades the end of each pass into the start of
 * the next (equal power, [CROSSFADE_MS]).
 *
 * Why not ExoPlayer any more (1.5.3): up to 1.5.2 the bed was an ExoPlayer that had to wrap by itself
 * (REPEAT_MODE_ONE, 1.4.0–1.5.0) or be handed over by a main-thread timer to a second ExoPlayer
 * (1.5.1–1.5.2, with REPEAT_MODE_ONE as fallback). On the owner's phone the sound still stopped after
 * the first ~5 min pass on 1.5.0 *and* on 1.5.2, i.e. whenever playback depended on a player getting
 * past the end of the clip. Here nothing can "end": the worker keeps writing PCM until [stop]/[pause],
 * decoder failures reopen the clip, and a dead AudioTrack is rebuilt.
 *
 * Output stays USAGE_MEDIA without audio focus, like the binaural tone, so the Spotify mix is unchanged.
 */
class AmbientPlayer(private val context: Context) {

    private var current: AmbientSound = AmbientSound.OFF
    @Volatile private var volume: Float = 0.35f
    @Volatile private var wantPlaying: Boolean = false
    private var worker: LoopWorker? = null

    fun setVolume(volume: Float) {
        this.volume = FrequencyMath.clampVolume(volume)
        worker?.setVolume(this.volume)
    }

    fun setAmbient(sound: AmbientSound) {
        if (sound == current && (sound == AmbientSound.OFF || worker != null)) {
            if (wantPlaying && sound != AmbientSound.OFF) worker?.play()
            return
        }
        releaseWorker()
        current = sound
        val assetPath = sound.assetPath ?: return
        try {
            worker = LoopWorker(context.applicationContext, assetPath, sound.name).also {
                it.setVolume(volume)
                it.begin()
                if (wantPlaying) it.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ambient ${sound.name}", e)
            releaseWorker()
            current = AmbientSound.OFF
        }
    }

    fun start() {
        wantPlaying = true
        if (current == AmbientSound.OFF) return
        val w = worker
        if (w == null) {
            setAmbient(current)
            return
        }
        w.setVolume(volume)
        w.play()
    }

    fun pause() {
        wantPlaying = false
        worker?.pause()
    }

    fun stop() {
        wantPlaying = false
        releaseWorker()
        current = AmbientSound.OFF
    }

    /** Stop playback but keep selected ambient for next start. */
    fun stopKeepingSelection() {
        wantPlaying = false
        releaseWorker()
        // keep [current]
    }

    fun release() {
        wantPlaying = false
        releaseWorker()
        current = AmbientSound.OFF
    }

    private fun releaseWorker() {
        worker?.shutdown()
        worker = null
    }

    /**
     * One clip on one thread: decode → crossfade looper → AudioTrack. [play]/[pause]/[setVolume]
     * may be called from any thread.
     */
    private class LoopWorker(
        private val appContext: Context,
        assetPath: String,
        private val label: String,
    ) {
        private val source = MediaCodecPassSource(appContext, assetPath)
        private val lock = Object()
        @Volatile private var playing = false
        @Volatile private var alive = true
        @Volatile private var gain = 0.35f
        @Volatile private var track: AudioTrack? = null
        private var trackRate = 0
        private var thread: Thread? = null
        private val wakeLock: PowerManager.WakeLock? = runCatching {
            (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BinauralWaves:ambient")
                .apply { setReferenceCounted(false) }
        }.getOrNull()

        private val sink = PcmSink { data, length -> writeAll(data, length) }
        // The beds are all 44.1 kHz (tools/build_ambient_library.py); at another rate the fade is
        // just proportionally shorter/longer.
        private val pump = AmbientLoopPump(
            source = source,
            sink = sink,
            crossfadeFrames = (44100L * CROSSFADE_MS / 1000L).toInt()
        )

        fun begin() {
            thread = Thread({ run() }, "AmbientLoop-$label").also { it.start() }
        }

        fun setVolume(v: Float) {
            gain = v
            try {
                track?.setVolume(v)
            } catch (_: IllegalStateException) {
            }
        }

        fun play() {
            synchronized(lock) {
                playing = true
                try {
                    track?.play()
                } catch (_: IllegalStateException) {
                }
                wakeLockAt = 0L
                lock.notifyAll()
            }
        }

        fun pause() {
            synchronized(lock) {
                playing = false
                try {
                    track?.pause()
                } catch (_: IllegalStateException) {
                }
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
            }
        }

        fun shutdown() {
            synchronized(lock) {
                alive = false
                playing = false
                lock.notifyAll()
            }
            val t = track
            if (t != null) {
                // Unblocks a pending blocking write().
                try { t.pause() } catch (_: IllegalStateException) {}
                try { t.flush() } catch (_: IllegalStateException) {}
                try { t.stop() } catch (_: IllegalStateException) {}
            }
            try {
                thread?.join(800)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            thread = null
            releaseTrack()
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }

        @Volatile private var wakeLockAt = 0L

        /** Keeps the partial wake lock alive while playing (cheap: at most once a minute). */
        private fun refreshWakeLock() {
            if (!playing) return
            val now = SystemClock.elapsedRealtime()
            if (now - wakeLockAt < 60_000L && wakeLock?.isHeld == true) return
            wakeLockAt = now
            runCatching { wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS) }
        }

        private var loggedPasses = 0

        private fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            Log.i(TAG, "ambient $label loop worker started")
            try {
                while (alive) {
                    synchronized(lock) {
                        while (alive && !playing) lock.wait(1000)
                    }
                    if (!alive) break
                    refreshWakeLock()
                    val waitMs = try {
                        pump.step()
                    } catch (e: Exception) {
                        Log.e(TAG, "ambient loop step failed", e)
                        250L
                    }
                    if (pump.passes != loggedPasses) {
                        loggedPasses = pump.passes
                        Log.i(TAG, "ambient $label wrap #$loggedPasses (crossfaded), recoveries=${pump.failures}")
                    }
                    if (waitMs > 0L) Thread.sleep(waitMs)
                }
            } catch (_: InterruptedException) {
            } finally {
                pump.close()
                Log.i(TAG, "ambient $label stopped after ${pump.passes} passes, ${pump.failures} recoveries")
            }
        }

        /** Blocking write of all samples; rebuilds the AudioTrack if it died. */
        private fun writeAll(data: ShortArray, length: Int): Boolean {
            var offset = 0
            var rebuilt = 0
            while (offset < length && alive) {
                val t = ensureTrack() ?: return false
                val n = try {
                    t.write(data, offset, length - offset, AudioTrack.WRITE_BLOCKING)
                } catch (e: IllegalStateException) {
                    AudioTrack.ERROR_INVALID_OPERATION
                }
                if (n > 0) {
                    offset += n
                } else if (n == 0) {
                    // Paused (pause() interrupts a blocking write) or stopping: wait for play().
                    if (!alive) return false
                    synchronized(lock) {
                        if (alive && !playing) lock.wait(1000)
                    }
                    if (playing) Thread.sleep(5)
                } else {
                    if (!alive) return false
                    Log.w(TAG, "ambient AudioTrack.write failed ($n) — rebuilding")
                    releaseTrack()
                    if (++rebuilt > 3) {
                        Thread.sleep(500)
                        return false
                    }
                }
            }
            return true
        }

        private fun ensureTrack(): AudioTrack? {
            val rate = source.sampleRate
            track?.let { if (trackRate == rate) return it }
            releaseTrack()
            val t = buildTrack(rate) ?: return null
            synchronized(lock) {
                if (!alive) {
                    t.release()
                    return null
                }
                track = t
                trackRate = rate
                t.setVolume(gain)
                if (playing) t.play()
            }
            return t
        }

        private fun releaseTrack() {
            val t = track ?: return
            track = null
            try { t.stop() } catch (_: IllegalStateException) {}
            try { t.release() } catch (_: Exception) {}
        }

        private fun buildTrack(rate: Int): AudioTrack? {
            val channelMask = AudioFormat.CHANNEL_OUT_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioTrack.getMinBufferSize(rate, channelMask, encoding)
            if (minBuf <= 0) {
                Log.e(TAG, "getMinBufferSize failed rate=$rate rc=$minBuf")
                return null
            }
            // ~0.5 s of headroom so reopening the decoder at a wrap never starves the output.
            val bufferBytes = maxOf(minBuf * 4, rate / 2 * 4)
            return try {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setEncoding(encoding)
                            .setChannelMask(channelMask)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (t.state != AudioTrack.STATE_INITIALIZED) {
                    t.release()
                    null
                } else {
                    t
                }
            } catch (e: Exception) {
                Log.e(TAG, "ambient AudioTrack build failed rate=$rate", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "AmbientPlayer"
        const val CROSSFADE_MS = 2000L

        /** Re-acquired every minute while playing; the timeout only guards against a leak. */
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
