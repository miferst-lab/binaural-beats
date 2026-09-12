package com.miferstlab.binauralbeats.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.miferstlab.binauralbeats.data.FrequencyMath
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time stereo sine generator via [AudioTrack] (PCM 16-bit; float only as fallback).
 *
 * Left and right channels use slightly different frequencies so the perceived
 * binaural beat equals |fL − fR|. Generation runs on a dedicated thread.
 *
 * Audio focus / Spotify coexistence:
 * Always uses USAGE_MEDIA so output follows the **media** volume slider
 * (USAGE_ASSISTANCE_SONIFICATION is often routed to a muted notification stream).
 * When mixWithOtherApps is on we do **not** request exclusive focus, so Spotify
 * can keep playing. When mix is off we request AUDIOFOCUS_GAIN.
 */
class BinauralAudioEngine(
    private val preferredSampleRate: Int = 44100
) {
    @Volatile private var leftFreqHz: Double = 215.5
    @Volatile private var rightFreqHz: Double = 224.5
    @Volatile private var amplitude: Float = 0.45f
    @Volatile private var running = false
    @Volatile private var paused = false

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var sampleRate: Int = preferredSampleRate
    private var useFloat: Boolean = false

    private var phaseL = 0.0
    private var phaseR = 0.0

    private val lock = Any()

    fun setFrequencies(leftHz: Float, rightHz: Float) {
        leftFreqHz = leftHz.toDouble().coerceIn(
            FrequencyMath.EAR_FREQ_MIN.toDouble(),
            FrequencyMath.EAR_FREQ_MAX.toDouble()
        )
        rightFreqHz = rightHz.toDouble().coerceIn(
            FrequencyMath.EAR_FREQ_MIN.toDouble(),
            FrequencyMath.EAR_FREQ_MAX.toDouble()
        )
    }

    fun setVolume(volume: Float) {
        amplitude = FrequencyMath.clampVolume(volume)
        synchronized(lock) {
            try {
                track?.setVolume(1f)
            } catch (_: IllegalStateException) {
                // track already released
            }
        }
    }

    fun isRunning(): Boolean = running && !paused

    /** True while the generation thread / AudioTrack session exists (including paused). */
    fun isSessionActive(): Boolean = running

    /**
     * @param mixWithOtherApps unused for routing (always media); kept for API stability.
     * Focus is handled by the service.
     */
    fun start(mixWithOtherApps: Boolean = true) {
        synchronized(lock) {
            if (running) {
                paused = false
                try {
                    track?.play()
                } catch (_: IllegalStateException) {
                    // ignore
                }
                return
            }

            val created = createInitializedTrack()
            if (created == null) {
                Log.e(TAG, "AudioTrack failed to initialize — no sound")
                return
            }

            running = true
            paused = false
            phaseL = 0.0
            phaseR = 0.0
            track = created
            created.setVolume(1f)
            created.play()
            Log.i(
                TAG,
                "AudioTrack started state=${created.state} play=${created.playState} " +
                    "sr=$sampleRate float=$useFloat mix=$mixWithOtherApps"
            )

            thread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                if (useFloat) {
                    writeLoopFloat(created.bufferSizeInFrames.coerceAtLeast(256))
                } else {
                    writeLoop16(created.bufferSizeInFrames.coerceAtLeast(256))
                }
            }, "BinauralAudioEngine").also { it.start() }
        }
    }

    fun pause() {
        synchronized(lock) {
            if (!running) return
            paused = true
            try {
                track?.pause()
            } catch (_: IllegalStateException) {
                // ignore
            }
        }
    }

    fun resume() {
        synchronized(lock) {
            if (!running) return
            paused = false
            try {
                track?.play()
            } catch (_: IllegalStateException) {
                // ignore
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            paused = false
            val localTrack = track
            val localThread = thread
            track = null
            thread = null

            if (localTrack != null) {
                try {
                    localTrack.pause()
                } catch (_: IllegalStateException) {
                }
                try {
                    localTrack.flush()
                } catch (_: IllegalStateException) {
                }
                try {
                    localTrack.stop()
                } catch (_: IllegalStateException) {
                }
            }

            try {
                localThread?.join(800)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            try {
                localTrack?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun createInitializedTrack(): AudioTrack? {
        val rates = intArrayOf(preferredSampleRate, 48000, 44100).distinct()
        // 16-bit first: PCM float is silent or ERROR_BAD_VALUE on many OEM devices.
        val encodings = intArrayOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)

        for (rate in rates) {
            for (encoding in encodings) {
                val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
                val minBuf = AudioTrack.getMinBufferSize(rate, channelConfig, encoding)
                if (minBuf <= 0) {
                    Log.w(TAG, "getMinBufferSize failed rate=$rate enc=$encoding rc=$minBuf")
                    continue
                }
                val bytesPerFrame = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 8 else 4
                val bufferSize = (minBuf * 2).coerceAtLeast(rate / 10 * bytesPerFrame)

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(encoding)
                    .setChannelMask(channelConfig)
                    .build()

                val localTrack = try {
                    AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "AudioTrack.Builder failed rate=$rate enc=$encoding", e)
                    continue
                }

                if (localTrack.state != AudioTrack.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioTrack not initialized rate=$rate enc=$encoding state=${localTrack.state}")
                    localTrack.release()
                    continue
                }

                sampleRate = rate
                useFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
                return localTrack
            }
        }
        return null
    }

    private fun writeLoopFloat(framesPerChunkHint: Int) {
        val framesPerChunk = framesPerChunkHint.coerceIn(256, 4096)
        val buffer = FloatArray(framesPerChunk * 2)
        val twoPi = 2.0 * PI

        while (running) {
            if (paused) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            val freqL = leftFreqHz
            val freqR = rightFreqHz
            val amp = (amplitude * 0.9f).coerceIn(0f, 0.9f)
            val stepL = twoPi * freqL / sampleRate
            val stepR = twoPi * freqR / sampleRate

            var i = 0
            while (i < framesPerChunk) {
                buffer[i * 2] = (sin(phaseL) * amp).toFloat()
                buffer[i * 2 + 1] = (sin(phaseR) * amp).toFloat()
                phaseL += stepL
                phaseR += stepR
                if (phaseL >= twoPi) phaseL -= twoPi
                if (phaseR >= twoPi) phaseR -= twoPi
                i++
            }

            val t = track ?: break
            val written = try {
                t.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            } catch (_: IllegalStateException) {
                break
            }
            if (written < 0) {
                Log.e(TAG, "AudioTrack.write float failed: $written")
                break
            }
        }
    }

    private fun writeLoop16(framesPerChunkHint: Int) {
        val framesPerChunk = framesPerChunkHint.coerceIn(256, 4096)
        val buffer = ShortArray(framesPerChunk * 2)
        val twoPi = 2.0 * PI

        while (running) {
            if (paused) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            val freqL = leftFreqHz
            val freqR = rightFreqHz
            val amp = (amplitude * 0.9f).coerceIn(0f, 0.9f)
            val stepL = twoPi * freqL / sampleRate
            val stepR = twoPi * freqR / sampleRate

            var i = 0
            while (i < framesPerChunk) {
                val sampleL = (sin(phaseL) * amp * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val sampleR = (sin(phaseR) * amp * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[i * 2] = sampleL.toShort()
                buffer[i * 2 + 1] = sampleR.toShort()
                phaseL += stepL
                phaseR += stepR
                if (phaseL >= twoPi) phaseL -= twoPi
                if (phaseR >= twoPi) phaseR -= twoPi
                i++
            }

            val t = track ?: break
            val written = try {
                t.write(buffer, 0, buffer.size)
            } catch (_: IllegalStateException) {
                break
            }
            if (written < 0) {
                Log.e(TAG, "AudioTrack.write 16-bit failed: $written")
                break
            }
        }
    }

    companion object {
        private const val TAG = "BinauralAudio"

        /**
         * Exclusive media focus — pauses other music apps.
         */
        fun requestExclusiveFocus(
            audioManager: AudioManager,
            holder: FocusHolder
        ): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { /* keep generating; service owns lifecycle */ }
                    .build()
                holder.focusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }

        /**
         * Soft request that ducks others slightly. Prefer skipping focus entirely
         * when mixing under Spotify — this is only used if we want a polite duck.
         */
        fun requestMixableFocus(
            audioManager: AudioManager,
            holder: FocusHolder
        ): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { /* keep playing; we want to mix */ }
                    .build()
                holder.focusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }

        fun abandonMixableFocus(audioManager: AudioManager, holder: FocusHolder) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                holder.focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        }
    }

    /** Holds API-26+ [AudioFocusRequest] so focus can be abandoned cleanly. */
    class FocusHolder {
        var focusRequest: AudioFocusRequest? = null
    }
}
