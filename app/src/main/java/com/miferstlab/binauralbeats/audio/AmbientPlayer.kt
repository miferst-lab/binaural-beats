package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.FrequencyMath

/**
 * Gapless ambient bed via Media3 [ExoPlayer] with [Player.REPEAT_MODE_ONE].
 *
 * Dual [android.media.MediaPlayer] equal-power crossfade still produced audible
 * seams (overlap of mid-event content such as thunder/drips). Assets are
 * pre-processed (tools/build_ambient_library.py) with an equal-power 4 s end→start
 * crossfade; a single looping player then wraps without a second volume fade.
 * Files are ~5 min OGG Vorbis in `assets/ambient/`, played via `asset:///` URIs.
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
        val assetPath = sound.assetPath ?: return
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
                val uri = Uri.parse("asset:///$assetPath")
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
    }
}
