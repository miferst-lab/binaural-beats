package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.FrequencyMath

/**
 * Looping ambient bed via [MediaPlayer] with [AudioAttributes.USAGE_MEDIA]
 * so it shares the media stream with binaural tones and Spotify mix behavior.
 */
class AmbientPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var current: AmbientSound = AmbientSound.OFF
    @Volatile private var volume: Float = 0.35f
    @Volatile private var wantPlaying: Boolean = false

    fun setVolume(volume: Float) {
        this.volume = FrequencyMath.clampVolume(volume)
        applyVolume()
    }

    fun setAmbient(sound: AmbientSound) {
        if (sound == current && (sound == AmbientSound.OFF || player != null)) {
            if (wantPlaying && sound != AmbientSound.OFF) {
                resumeInternal()
            }
            return
        }
        releasePlayer()
        current = sound
        if (sound == AmbientSound.OFF || sound.rawResId == null) {
            return
        }
        try {
            val mp = MediaPlayer.create(context, sound.rawResId) ?: run {
                Log.e(TAG, "MediaPlayer.create failed for ${sound.name}")
                return
            }
            mp.isLooping = true
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player = mp
            applyVolume()
            if (wantPlaying) {
                mp.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ambient ${sound.name}", e)
            releasePlayer()
            current = AmbientSound.OFF
        }
    }

    fun start() {
        wantPlaying = true
        if (current == AmbientSound.OFF) return
        if (player == null) {
            setAmbient(current)
            return
        }
        resumeInternal()
    }

    fun pause() {
        wantPlaying = false
        try {
            val mp = player ?: return
            if (mp.isPlaying) mp.pause()
        } catch (_: IllegalStateException) {
        }
    }

    fun stop() {
        wantPlaying = false
        releasePlayer()
        current = AmbientSound.OFF
    }

    /** Stop playback but keep selected ambient for next start. */
    fun stopKeepingSelection() {
        wantPlaying = false
        releasePlayer()
        // keep [current]
    }

    fun release() {
        wantPlaying = false
        releasePlayer()
        current = AmbientSound.OFF
    }

    private fun resumeInternal() {
        try {
            val mp = player ?: return
            if (!mp.isPlaying) mp.start()
        } catch (_: IllegalStateException) {
        }
    }

    private fun applyVolume() {
        val v = volume
        try {
            player?.setVolume(v, v)
        } catch (_: IllegalStateException) {
        }
    }

    private fun releasePlayer() {
        try {
            player?.setOnCompletionListener(null)
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    companion object {
        private const val TAG = "AmbientPlayer"
    }
}
