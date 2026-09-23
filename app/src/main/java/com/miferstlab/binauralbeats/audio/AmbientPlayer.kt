package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.FrequencyMath

/**
 * Gapless ambient bed via Media3 [ExoPlayer] with [Player.REPEAT_MODE_ONE].
 *
 * Dual [android.media.MediaPlayer] equal-power crossfade still produced audible
 * seams (overlap of mid-event content such as thunder/drips). Assets are
 * pre-processed with equal-power end→start acrossfade (~2.5s); a single looping
 * player then wraps without a second volume fade.
 *
 * AudioAttributes stay USAGE_MEDIA so Spotify mix behavior is unchanged.
 */
class AmbientPlayer(private val context: Context) {

    private var player: ExoPlayer? = null
    private var current: AmbientSound = AmbientSound.OFF
    @Volatile private var volume: Float = 0.35f
    @Volatile private var wantPlaying: Boolean = false

    fun setVolume(volume: Float) {
        this.volume = FrequencyMath.clampVolume(volume)
        player?.volume = this.volume
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
        val resId = rawResId(sound) ?: return
        try {
            val exo = ExoPlayer.Builder(context).build().also { p ->
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus= */ false
                )
                p.repeatMode = Player.REPEAT_MODE_ONE
                p.volume = volume
                val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                p.setMediaItem(MediaItem.fromUri(uri))
                p.prepare()
            }
            player = exo
            if (wantPlaying) {
                exo.playWhenReady = true
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
            player?.playWhenReady = false
        } catch (_: Exception) {
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
        val p = player ?: return
        try {
            p.volume = volume
            p.playWhenReady = true
            if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
        } catch (e: Exception) {
            Log.e(TAG, "resume failed", e)
        }
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    companion object {
        private const val TAG = "AmbientPlayer"

        fun rawResId(sound: AmbientSound): Int? = when (sound) {
            AmbientSound.OFF -> null
            AmbientSound.FOREST_NIGHT -> R.raw.ambient_forest_night
            AmbientSound.WAVES -> R.raw.ambient_waves
            AmbientSound.MORNING_VILLAGE -> R.raw.ambient_morning_village
            AmbientSound.RAIN -> R.raw.ambient_rain
            AmbientSound.FIREPLACE -> R.raw.ambient_fireplace
            AmbientSound.STREAM -> R.raw.ambient_stream
            AmbientSound.MOUNTAIN_WIND -> R.raw.ambient_mountain_wind
            AmbientSound.CAVE_DRIP -> R.raw.ambient_cave_drip
            AmbientSound.SOFT_THUNDER -> R.raw.ambient_soft_thunder
        }
    }
}
