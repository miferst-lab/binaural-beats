package com.miferstlab.binauralbeats.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.miferstlab.binauralbeats.data.FrequencyMath
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time stereo sine generator via [AudioTrack] (PCM float when available, else 16-bit).
 *
 * Left and right channels use slightly different frequencies so the perceived
 * binaural beat equals |fL − fR|. Generation runs on a dedicated thread.
 *
 * Audio focus / Spotify coexistence:
 * Uses USAGE_ASSISTANCE_SONIFICATION + CONTENT_TYPE_SONIFICATION and requests
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK (or none when mixWithOtherApps), so Spotify
 * and other music apps are not paused. See README.
 */
class BinauralAudioEngine(
    private val sampleRate: Int = 44100
) {
    @Volatile private var leftFreqHz: Double = 215.5
    @Volatile private var rightFreqHz: Double = 224.5
    @Volatile private var amplitude: Float = 0.35f
    @Volatile private var running = false
    @Volatile private var paused = false

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    private var phaseL = 0.0
    private var phaseR = 0.0

    private val lock = Any()

    private val useFloat: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

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
        // Amplitude is applied in the PCM buffer; keep track gain at unity.
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
     * @param mixWithOtherApps when true, use sonification usage so we don't fight Spotify.
     */
    fun start(mixWithOtherApps: Boolean = true) {
        synchronized(lock) {
            if (running) {
                // Already generating — ensure AudioTrack is playing (fixes pause→play race).
                paused = false
                try {
                    track?.play()
                } catch (_: IllegalStateException) {
                    // ignore
                }
                return
            }
            running = true
            paused = false
            phaseL = 0.0
            phaseR = 0.0

            val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            val encoding = if (useFloat) {
                AudioFormat.ENCODING_PCM_FLOAT
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
            val bufferSize = (minBuf * 2).coerceAtLeast(sampleRate / 10 * if (useFloat) 8 else 4)

            val attrsBuilder = AudioAttributes.Builder()
            if (mixWithOtherApps) {
                attrsBuilder
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            } else {
                attrsBuilder
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            }

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelConfig)
                .build()

            val localTrack = AudioTrack.Builder()
                .setAudioAttributes(attrsBuilder.build())
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track = localTrack
            localTrack.play()

            thread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                if (useFloat) {
                    writeLoopFloat(bufferSize)
                } else {
                    writeLoop16(bufferSize)
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

            // Stop/flush first so a blocked WRITE_BLOCKING unblocks before join.
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

    private fun writeLoopFloat(bufferSizeBytes: Int) {
        // stereo float: 4 bytes * 2 channels per frame
        val framesPerChunk = (bufferSizeBytes / 8).coerceAtLeast(256)
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
            // Soft ceiling: keep headroom so slider 100% does not hard-clip on some DACs.
            val amp = (amplitude * 0.9f).coerceIn(0f, 0.9f)
            val stepL = twoPi * freqL / sampleRate
            val stepR = twoPi * freqR / sampleRate

            var i = 0
            while (i < framesPerChunk) {
                val sampleL = (sin(phaseL) * amp).toFloat()
                val sampleR = (sin(phaseR) * amp).toFloat()
                buffer[i * 2] = sampleL
                buffer[i * 2 + 1] = sampleR
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
            if (written < 0) break
        }
    }

    private fun writeLoop16(bufferSizeBytes: Int) {
        val framesPerChunk = (bufferSizeBytes / 4).coerceAtLeast(256)
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
            if (written < 0) break
        }
    }

    companion object {
        /**
         * Request audio focus that allows mixing / ducking under music apps.
         * Stores the request on API 26+ so [abandonMixableFocus] can release it.
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
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
