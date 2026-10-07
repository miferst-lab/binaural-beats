package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.FrequencyMath

/**
 * Seamless, endlessly looping ambient bed.
 *
 * Two prepared [ExoPlayer]s on the same `asset:///` clip; [AmbientLoopController] overlaps them with
 * a ~2 s equal-power crossfade before the active clip ends, which masks the OGG/Vorbis decoder seam
 * that single-player looping leaves every ~5 min.
 *
 * Safety nets (1.5.2), so the bed can never stop after one pass again:
 *  - both players use [Player.REPEAT_MODE_ONE]: if a handover is late or missed, the active
 *    player simply wraps by itself (at worst the old small seam) instead of reaching STATE_ENDED;
 *  - [C.WAKE_MODE_LOCAL] keeps the CPU awake while a player plays with the screen off, so the
 *    handover ticker keeps running on time;
 *  - a failed player (playback error → STATE_IDLE) is re-prepared and the other player takes over.
 *
 * AudioAttributes stay USAGE_MEDIA with handleAudioFocus=false, so the Spotify mix is unchanged.
 */
class AmbientPlayer(private val context: Context) {

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var controller: AmbientLoopController? = null
    private var current: AmbientSound = AmbientSound.OFF
    @Volatile private var volume: Float = 0.35f
    @Volatile private var wantPlaying: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val c = controller ?: return
            if (!wantPlaying) return
            val delay = try {
                c.tick()
            } catch (e: Exception) {
                Log.e(TAG, "loop tick failed", e)
                AmbientLoopController.NORMAL_TICK_MS
            }
            scheduleTick(delay)
        }
    }

    fun setVolume(volume: Float) {
        this.volume = FrequencyMath.clampVolume(volume)
        controller?.setVolume(this.volume)
    }

    fun setAmbient(sound: AmbientSound) {
        if (sound == current && (sound == AmbientSound.OFF || controller != null)) {
            if (wantPlaying && sound != AmbientSound.OFF) {
                resumeInternal()
            }
            return
        }
        releasePlayers()
        current = sound
        val assetPath = sound.assetPath ?: return
        try {
            val uri = Uri.parse("asset:///$assetPath")
            val a = createPrepared(uri)
            val b = createPrepared(uri)
            playerA = a
            playerB = b
            controller = AmbientLoopController(
                voiceA = ExoVoice(a),
                voiceB = ExoVoice(b),
                clock = { SystemClock.elapsedRealtime() }
            ).also { it.setVolume(volume) }
            if (wantPlaying) {
                resumeInternal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ambient ${sound.name}", e)
            releasePlayers()
            current = AmbientSound.OFF
        }
    }

    fun start() {
        wantPlaying = true
        if (current == AmbientSound.OFF) return
        if (controller == null) {
            setAmbient(current)
            return
        }
        resumeInternal()
    }

    fun pause() {
        wantPlaying = false
        cancelTick()
        try {
            controller?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "pause failed", e)
        }
    }

    fun stop() {
        wantPlaying = false
        releasePlayers()
        current = AmbientSound.OFF
    }

    /** Stop playback but keep selected ambient for next start. */
    fun stopKeepingSelection() {
        wantPlaying = false
        releasePlayers()
        // keep [current]
    }

    fun release() {
        wantPlaying = false
        releasePlayers()
        current = AmbientSound.OFF
    }

    private fun resumeInternal() {
        val c = controller ?: return
        try {
            c.setVolume(volume)
            if (!c.isPlaying) c.start()
            scheduleTick(0L)
        } catch (e: Exception) {
            Log.e(TAG, "resume failed", e)
        }
    }

    private fun scheduleTick(delayMs: Long) {
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, delayMs.coerceAtLeast(0L))
    }

    private fun cancelTick() {
        handler.removeCallbacks(ticker)
    }

    private fun createPrepared(uri: Uri): ExoPlayer {
        val exo = ExoPlayer.Builder(context).build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus= */ false
        )
        // Fallback loop: the crossfade normally hands over before the end, but if it is ever late
        // the player wraps by itself instead of ending (1.5.1 used REPEAT_MODE_OFF → one pass only).
        exo.repeatMode = Player.REPEAT_MODE_ONE
        // Keep the CPU awake while this player plays (screen off); released when paused.
        exo.setWakeMode(C.WAKE_MODE_LOCAL)
        exo.volume = 0f
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "ambient player error — will re-prepare / hand over", error)
                if (wantPlaying) scheduleTick(0L)
            }
        })
        exo.prepare()
        return exo
    }

    private fun releasePlayers() {
        cancelTick()
        controller = null
        releaseOne(playerA)
        releaseOne(playerB)
        playerA = null
        playerB = null
    }

    private fun releaseOne(player: ExoPlayer?) {
        if (player == null) return
        try {
            player.playWhenReady = false
            player.release()
        } catch (_: Exception) {
        }
    }

    /** [LoopVoice] backed by an [ExoPlayer]. Main-thread only, like the player itself. */
    private class ExoVoice(private val exo: ExoPlayer) : LoopVoice {
        override val durationMs: Long
            get() = exo.duration.let { if (it == C.TIME_UNSET) -1L else it }

        override val positionMs: Long
            get() = exo.currentPosition.coerceAtLeast(0L)

        override val needsRecovery: Boolean
            get() = exo.playbackState == Player.STATE_IDLE

        override fun play() {
            if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
            exo.playWhenReady = true
        }

        override fun pause() {
            exo.playWhenReady = false
        }

        override fun seekToStart() {
            exo.seekTo(0L)
        }

        override fun setVolume(volume: Float) {
            exo.volume = volume.coerceIn(0f, 1f)
        }

        override fun recover() {
            if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        }
    }

    companion object {
        private const val TAG = "AmbientPlayer"
    }
}
