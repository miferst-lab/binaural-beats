package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.FrequencyMath
import kotlin.math.cos
import kotlin.math.sin

/**
 * Seamless ambient bed via dual [ExoPlayer] equal-power crossfade at loop boundaries.
 *
 * OGG/Vorbis + single-player [Player.REPEAT_MODE_ONE] leaves an audible decoder
 * gap/click every ~5 min even when assets are pre-crossfaded
 * (`tools/build_ambient_library.py`, 4 s end→start). Two prepared players on the
 * same `asset:///` URI overlap for [CROSSFADE_MS] before the active clip ends,
 * masking that wrap. AudioAttributes stay USAGE_MEDIA so Spotify mix is unchanged.
 */
class AmbientPlayer(private val context: Context) {

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    /** Index of the currently audible player: 0 = A, 1 = B. */
    private var activeSlot: Int = 0
    private var current: AmbientSound = AmbientSound.OFF
    @Volatile private var volume: Float = 0.35f
    @Volatile private var wantPlaying: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var crossfadeRunnable: Runnable? = null
    private var scheduleRunnable: Runnable? = null
    private var pollRunnable: Runnable? = null
    private var fading: Boolean = false

    fun setVolume(volume: Float) {
        this.volume = FrequencyMath.clampVolume(volume)
        if (!fading) {
            applyVolume(activePlayer(), this.volume)
            applyVolume(inactivePlayer(), 0f)
        }
    }

    fun setAmbient(sound: AmbientSound) {
        if (sound == current && (sound == AmbientSound.OFF || playerA != null)) {
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
            playerA = createPrepared(uri)
            playerB = createPrepared(uri)
            activeSlot = 0
            applyVolume(playerA, volume)
            applyVolume(playerB, 0f)
            if (wantPlaying) {
                startActiveAndSchedule()
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
        if (playerA == null) {
            setAmbient(current)
            return
        }
        resumeInternal()
    }

    fun pause() {
        wantPlaying = false
        cancelScheduled()
        try {
            playerA?.playWhenReady = false
            playerB?.playWhenReady = false
        } catch (_: Exception) {
        }
        fading = false
        applyVolume(activePlayer(), volume)
        applyVolume(inactivePlayer(), 0f)
    }

    fun stop() {
        wantPlaying = false
        cancelScheduled()
        releasePlayers()
        current = AmbientSound.OFF
    }

    /** Stop playback but keep selected ambient for next start. */
    fun stopKeepingSelection() {
        wantPlaying = false
        cancelScheduled()
        releasePlayers()
        // keep [current]
    }

    fun release() {
        wantPlaying = false
        cancelScheduled()
        releasePlayers()
        current = AmbientSound.OFF
    }

    private fun resumeInternal() {
        try {
            val active = activePlayer() ?: return
            applyVolume(active, volume)
            applyVolume(inactivePlayer(), 0f)
            active.playWhenReady = true
            if (active.playbackState == Player.STATE_IDLE) {
                active.prepare()
            }
            scheduleCrossfade()
            startPoll()
        } catch (e: Exception) {
            Log.e(TAG, "resume failed", e)
        }
    }

    private fun startActiveAndSchedule() {
        try {
            val active = activePlayer() ?: return
            applyVolume(active, volume)
            applyVolume(inactivePlayer(), 0f)
            active.playWhenReady = true
            scheduleCrossfade()
            startPoll()
        } catch (e: Exception) {
            Log.e(TAG, "startActive failed", e)
        }
    }

    private fun scheduleCrossfade() {
        cancelScheduledOnly()
        if (!wantPlaying || fading) return
        val active = activePlayer() ?: return
        val duration = active.duration
        if (duration <= 0L || duration == C.TIME_UNSET) {
            // Not ready yet — poll will retry once duration is known.
            return
        }
        val position = active.currentPosition.coerceAtLeast(0L)
        val remaining = (duration - position).coerceAtLeast(0L)
        val delay = (remaining - CROSSFADE_MS - SCHEDULE_LEAD_MS).coerceAtLeast(0L)
        val run = Runnable { beginCrossfade() }
        scheduleRunnable = run
        handler.postDelayed(run, delay)
    }

    private fun startPoll() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        val poll = object : Runnable {
            override fun run() {
                if (!wantPlaying) return
                if (!fading) {
                    val active = activePlayer()
                    if (active != null) {
                        val duration = active.duration
                        if (duration > 0L && duration != C.TIME_UNSET) {
                            val remaining = duration - active.currentPosition
                            if (remaining in 1 until (CROSSFADE_MS + SCHEDULE_LEAD_MS)) {
                                // Missed or late schedule — start overlap now.
                                beginCrossfade()
                            } else if (scheduleRunnable == null) {
                                scheduleCrossfade()
                            }
                        }
                    }
                }
                if (wantPlaying) {
                    handler.postDelayed(this, POLL_MS)
                    pollRunnable = this
                }
            }
        }
        pollRunnable = poll
        handler.postDelayed(poll, POLL_MS)
    }

    private fun beginCrossfade() {
        if (!wantPlaying || fading) return
        val outgoing = activePlayer() ?: return
        val incoming = inactivePlayer() ?: return
        fading = true
        cancelScheduledOnly()

        try {
            applyVolume(incoming, 0f)
            // Already-prepared second player: seek to start, then overlap.
            incoming.seekTo(0L)
            if (incoming.playbackState == Player.STATE_IDLE ||
                incoming.playbackState == Player.STATE_ENDED
            ) {
                incoming.prepare()
            }
            incoming.playWhenReady = true
            runEqualPowerFade(outgoing, incoming)
        } catch (e: Exception) {
            Log.e(TAG, "crossfade prepare failed", e)
            fading = false
            hardRestartActive()
        }
    }

    private fun runEqualPowerFade(outgoing: ExoPlayer, incoming: ExoPlayer) {
        val steps = (CROSSFADE_MS / STEP_MS).coerceAtLeast(1)
        var step = 0
        val target = volume
        val run = object : Runnable {
            override fun run() {
                if (!wantPlaying) {
                    fading = false
                    return
                }
                step++
                val t = (step.toFloat() / steps).coerceIn(0f, 1f)
                // Equal-power: constant perceived loudness across the overlap.
                val outGain = cos(HALF_PI * t).toFloat()
                val inGain = sin(HALF_PI * t).toFloat()
                applyVolume(outgoing, target * outGain)
                applyVolume(incoming, target * inGain)
                if (step < steps) {
                    handler.postDelayed(this, STEP_MS.toLong())
                    crossfadeRunnable = this
                } else {
                    finishCrossfade(outgoing, incoming, target)
                }
            }
        }
        crossfadeRunnable = run
        handler.post(run)
    }

    private fun finishCrossfade(outgoing: ExoPlayer, incoming: ExoPlayer, target: Float) {
        fading = false
        try {
            outgoing.playWhenReady = false
            outgoing.seekTo(0L)
        } catch (_: Exception) {
        }
        applyVolume(outgoing, 0f)
        applyVolume(incoming, target)
        activeSlot = 1 - activeSlot
        scheduleCrossfade()
    }

    private fun hardRestartActive() {
        try {
            val active = activePlayer() ?: return
            active.seekTo(0L)
            applyVolume(active, volume)
            applyVolume(inactivePlayer(), 0f)
            active.playWhenReady = true
            scheduleCrossfade()
        } catch (e: Exception) {
            Log.e(TAG, "hardRestart failed", e)
        }
    }

    private fun activePlayer(): ExoPlayer? = if (activeSlot == 0) playerA else playerB
    private fun inactivePlayer(): ExoPlayer? = if (activeSlot == 0) playerB else playerA

    private fun applyVolume(player: ExoPlayer?, v: Float) {
        try {
            player?.volume = v.coerceIn(0f, 1f)
        } catch (_: Exception) {
        }
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
        // We loop via dual-player crossfade — do not use REPEAT_MODE_ONE (OGG gap).
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.volume = 0f
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                if (!wantPlaying || fading) return
                handler.post {
                    if (!wantPlaying || fading) return@post
                    if (exo !== activePlayer()) return@post
                    Log.w(TAG, "active ended before crossfade — forcing overlap")
                    beginCrossfade()
                }
            }
        })
        exo.prepare()
        return exo
    }

    private fun cancelScheduled() {
        cancelScheduledOnly()
        crossfadeRunnable?.let { handler.removeCallbacks(it) }
        crossfadeRunnable = null
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
        fading = false
    }

    private fun cancelScheduledOnly() {
        scheduleRunnable?.let { handler.removeCallbacks(it) }
        scheduleRunnable = null
    }

    private fun releasePlayers() {
        cancelScheduled()
        releaseOne(playerA)
        releaseOne(playerB)
        playerA = null
        playerB = null
        activeSlot = 0
    }

    private fun releaseOne(player: ExoPlayer?) {
        if (player == null) return
        try {
            player.playWhenReady = false
            player.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "AmbientPlayer"
        /** Overlap that masks OGG decoder wrap; short enough to limit double-events. */
        private const val CROSSFADE_MS = 2000L
        private const val STEP_MS = 40L
        /** Headroom so a delayed Handler still starts before clip end. */
        private const val SCHEDULE_LEAD_MS = 150L
        /** Backup position check while playing (~5 min beds). */
        private const val POLL_MS = 500L
        private const val HALF_PI = Math.PI / 2.0
    }
}
