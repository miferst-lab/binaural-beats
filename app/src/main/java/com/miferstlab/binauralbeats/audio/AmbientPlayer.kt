package com.miferstlab.binauralbeats.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.FrequencyMath
import kotlin.math.cos
import kotlin.math.sin

/**
 * Seamless ambient bed via dual [MediaPlayer] crossfade.
 *
 * Single-player [MediaPlayer.isLooping] leaves an audible gap on OGG/Vorbis
 * (decoder flush + recreate). We keep two prepared players on the same raw
 * resource and overlap them for ~[CROSSFADE_MS] before the active clip ends,
 * preserving [AudioAttributes.USAGE_MEDIA] so Spotify mix behavior is unchanged.
 *
 * Assets are also pre-processed with end→start acrossfade; the long player
 * overlap still masks mid-event cuts (thunder) and any residual decoder gap.
 */
class AmbientPlayer(private val context: Context) {

    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null
    /** Index of the currently audible player: 0 = A, 1 = B. */
    private var activeSlot: Int = 0
    private var current: AmbientSound = AmbientSound.OFF
    @Volatile private var volume: Float = 0.35f
    @Volatile private var wantPlaying: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var crossfadeRunnable: Runnable? = null
    private var scheduleRunnable: Runnable? = null
    private var fading: Boolean = false

    fun setVolume(volume: Float) {
        this.volume = FrequencyMath.clampVolume(volume)
        if (!fading) {
            applyVolume(activePlayer(), this.volume)
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
        val resId = rawResId(sound) ?: return
        try {
            playerA = createPrepared(resId)
            playerB = createPrepared(resId)
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
            playerA?.takeIf { it.isPlaying }?.pause()
            playerB?.takeIf { it.isPlaying }?.pause()
        } catch (_: IllegalStateException) {
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
            if (!active.isPlaying) {
                active.start()
            }
            applyVolume(active, volume)
            applyVolume(inactivePlayer(), 0f)
            scheduleCrossfade()
        } catch (_: IllegalStateException) {
        }
    }

    private fun startActiveAndSchedule() {
        try {
            val active = activePlayer() ?: return
            applyVolume(active, volume)
            applyVolume(inactivePlayer(), 0f)
            if (!active.isPlaying) active.start()
            scheduleCrossfade()
        } catch (e: Exception) {
            Log.e(TAG, "startActive failed", e)
        }
    }

    private fun scheduleCrossfade() {
        cancelScheduledOnly()
        if (!wantPlaying) return
        val active = activePlayer() ?: return
        val duration = try {
            active.duration
        } catch (_: IllegalStateException) {
            return
        }
        if (duration <= 0) return
        val position = try {
            active.currentPosition
        } catch (_: IllegalStateException) {
            0
        }
        val remaining = (duration - position).coerceAtLeast(0)
        // Start the overlap early enough that Handler jitter cannot miss the end.
        val delay = (remaining - CROSSFADE_MS - SCHEDULE_LEAD_MS).coerceAtLeast(0).toLong()
        val run = Runnable { beginCrossfade() }
        scheduleRunnable = run
        handler.postDelayed(run, delay)
    }

    private fun beginCrossfade() {
        if (!wantPlaying || fading) return
        val outgoing = activePlayer() ?: return
        val incoming = inactivePlayer() ?: return
        fading = true

        // seekTo is async on many devices — start/fade only after seek completes.
        try {
            applyVolume(incoming, 0f)
            incoming.setOnSeekCompleteListener { mp ->
                mp.setOnSeekCompleteListener(null)
                if (!wantPlaying) {
                    fading = false
                    return@setOnSeekCompleteListener
                }
                try {
                    if (!mp.isPlaying) mp.start()
                } catch (e: Exception) {
                    Log.e(TAG, "incoming start failed", e)
                    fading = false
                    hardRestartActive()
                    return@setOnSeekCompleteListener
                }
                runEqualPowerFade(outgoing, incoming)
            }
            incoming.seekTo(0)
        } catch (e: Exception) {
            Log.e(TAG, "crossfade prepare failed", e)
            fading = false
            hardRestartActive()
        }
    }

    private fun runEqualPowerFade(outgoing: MediaPlayer, incoming: MediaPlayer) {
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
                // Equal-power crossfade: constant perceived loudness, no dip in the middle.
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

    private fun finishCrossfade(outgoing: MediaPlayer, incoming: MediaPlayer, target: Float) {
        fading = false
        try {
            if (outgoing.isPlaying) outgoing.pause()
            outgoing.seekTo(0)
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
            active.seekTo(0)
            applyVolume(active, volume)
            if (!active.isPlaying) active.start()
            scheduleCrossfade()
        } catch (e: Exception) {
            Log.e(TAG, "hardRestart failed", e)
        }
    }

    private fun activePlayer(): MediaPlayer? = if (activeSlot == 0) playerA else playerB
    private fun inactivePlayer(): MediaPlayer? = if (activeSlot == 0) playerB else playerA

    private fun applyVolume(mp: MediaPlayer?, v: Float) {
        try {
            mp?.setVolume(v, v)
        } catch (_: IllegalStateException) {
        }
    }

    private fun createPrepared(resId: Int): MediaPlayer {
        val mp = MediaPlayer.create(context, resId)
            ?: throw IllegalStateException("MediaPlayer.create failed for res=$resId")
        mp.isLooping = false
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        // Safety net: if scheduling missed the end, kick a crossfade immediately.
        mp.setOnCompletionListener { completed ->
            if (!wantPlaying) return@setOnCompletionListener
            handler.post {
                if (!wantPlaying || fading) return@post
                if (completed !== activePlayer()) return@post
                Log.w(TAG, "active completed before crossfade — forcing overlap")
                beginCrossfade()
            }
        }
        return mp
    }

    private fun cancelScheduled() {
        cancelScheduledOnly()
        crossfadeRunnable?.let { handler.removeCallbacks(it) }
        crossfadeRunnable = null
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

    private fun releaseOne(mp: MediaPlayer?) {
        if (mp == null) return
        try {
            mp.setOnCompletionListener(null)
            mp.setOnSeekCompleteListener(null)
            if (mp.isPlaying) mp.stop()
        } catch (_: Exception) {
        }
        try {
            mp.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "AmbientPlayer"
        /** Overlap window that masks OGG decoder gap and mid-event cuts (thunder). */
        private const val CROSSFADE_MS = 1800
        private const val STEP_MS = 40
        /** Extra headroom so a delayed Handler still starts before clip end. */
        private const val SCHEDULE_LEAD_MS = 120
        private const val HALF_PI = (Math.PI / 2.0)

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
