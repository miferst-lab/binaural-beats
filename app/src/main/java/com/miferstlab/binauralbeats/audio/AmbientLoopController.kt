package com.miferstlab.binauralbeats.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * One prepared player of the ambient bed (an ExoPlayer in the app, a fake in unit tests).
 *
 * Every voice is expected to loop on its own as well (ExoPlayer `REPEAT_MODE_ONE`), so a late or
 * missed handover can only cost a tiny seam, never silence.
 */
interface LoopVoice {
    /** Track length in ms, or a value <= 0 while it is not known yet. */
    val durationMs: Long

    /** Current playback position in ms (wraps to 0 when the voice loops by itself). */
    val positionMs: Long

    /** True when the voice failed (player error / idle) and must be [recover]ed before playing. */
    val needsRecovery: Boolean

    fun play()
    fun pause()
    fun seekToStart()
    fun setVolume(volume: Float)

    /** Re-prepare after an error. Must not start playback by itself. */
    fun recover()
}

/**
 * Pure (Android-free) loop scheduler for the ambient bed: two voices on the same clip, equal-power
 * crossfade of [crossfadeMs] that starts [leadMs] before the active clip would end.
 *
 * Driven by [tick], which the host calls again after the returned delay. All timing is derived from
 * [clock] (elapsed real time) and the players' own positions, never from counting ticks, so a late
 * or skipped tick cannot stretch the fade or lose the handover state.
 *
 * Why this exists (1.5.2): in 1.5.1 both players ran with REPEAT_MODE_OFF and looping depended
 * entirely on the app-side handover firing on time. Whenever it did not, the active player reached
 * its end and stayed silent: the ambient bed played one full pass and stopped.
 */
class AmbientLoopController(
    voiceA: LoopVoice,
    voiceB: LoopVoice,
    private val clock: () -> Long,
    private val crossfadeMs: Long = DEFAULT_CROSSFADE_MS,
    private val leadMs: Long = DEFAULT_LEAD_MS,
) {
    private val voices = arrayOf(voiceA, voiceB)
    private var activeIndex = 0
    private var fadeStartMs = NOT_FADING
    private var fadeTarget = 0f
    private var lastActivePositionMs = 0L

    var volume: Float = 0.35f
        private set

    /** True while the user wants the ambient bed to play. */
    var isPlaying: Boolean = false
        private set

    val isFading: Boolean get() = fadeStartMs != NOT_FADING

    /** Crossfade handovers completed (for logs and tests). */
    var handovers: Int = 0
        private set

    /** Times the active voice wrapped by itself because the handover came too late (fallback loop). */
    var selfWraps: Int = 0
        private set

    /** Hard switches after a voice failed. */
    var recoveries: Int = 0
        private set

    val activeVoice: LoopVoice get() = voices[activeIndex]
    val inactiveVoice: LoopVoice get() = voices[1 - activeIndex]

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        if (!isFading) {
            activeVoice.setVolume(this.volume)
            inactiveVoice.setVolume(0f)
        } else {
            fadeTarget = this.volume
        }
    }

    fun start() {
        isPlaying = true
        if (activeVoice.needsRecovery) activeVoice.recover()
        if (isFading) {
            // Should not happen (pause snaps the fade), but keep both voices consistent.
            finishFade()
        }
        activeVoice.setVolume(volume)
        inactiveVoice.setVolume(0f)
        activeVoice.play()
        lastActivePositionMs = activeVoice.positionMs
    }

    fun pause() {
        isPlaying = false
        if (isFading) finishFade()
        activeVoice.pause()
        inactiveVoice.pause()
    }

    /**
     * Advance the loop state machine.
     * @return delay in ms until the next [tick] should run.
     */
    fun tick(): Long {
        if (!isPlaying) return IDLE_TICK_MS
        val now = clock()
        if (isFading) {
            advanceFade(now)
            return FADE_TICK_MS
        }

        val active = activeVoice
        val inactive = inactiveVoice
        if (active.needsRecovery) {
            hardSwitchFrom(active)
            return FADE_TICK_MS
        }
        if (inactive.needsRecovery) inactive.recover()

        val duration = active.durationMs
        if (duration <= 0L) return FADE_TICK_MS // still preparing

        val position = active.positionMs
        if (position + WRAP_DETECT_MS < lastActivePositionMs) {
            // The voice looped by itself (REPEAT_MODE_ONE): handover was late, playback continued.
            selfWraps++
        }
        lastActivePositionMs = position

        val remaining = duration - position
        val untilFade = remaining - crossfadeMs - leadMs
        if (untilFade <= 0L) {
            beginFade(now)
            return FADE_TICK_MS
        }
        return untilFade.coerceIn(FADE_TICK_MS, NORMAL_TICK_MS)
    }

    private fun beginFade(now: Long) {
        val incoming = inactiveVoice
        if (incoming.needsRecovery) {
            // Cannot hand over now; active keeps looping by itself, retry on a later tick.
            incoming.recover()
            return
        }
        fadeStartMs = now
        fadeTarget = volume
        incoming.setVolume(0f)
        if (incoming.positionMs > START_TOLERANCE_MS) incoming.seekToStart()
        incoming.play()
    }

    private fun advanceFade(now: Long) {
        if (inactiveVoice.needsRecovery) {
            // Incoming failed mid-fade: abort, keep the outgoing voice (it loops by itself) at full
            // volume and re-prepare the incoming one for the next attempt.
            abortFade()
            return
        }
        if (activeVoice.needsRecovery) {
            // Outgoing failed mid-fade: jump straight to the incoming voice.
            finishFade()
            activeVoice.setVolume(volume)
            return
        }
        val t = ((now - fadeStartMs).toFloat() / crossfadeMs).coerceIn(0f, 1f)
        val outGain = cos(HALF_PI * t).toFloat()
        val inGain = sin(HALF_PI * t).toFloat()
        activeVoice.setVolume(fadeTarget * outGain)
        inactiveVoice.setVolume(fadeTarget * inGain)
        if (t >= 1f) finishFade()
    }

    private fun finishFade() {
        val outgoing = activeVoice
        val incoming = inactiveVoice
        outgoing.pause()
        outgoing.setVolume(0f)
        outgoing.seekToStart() // re-arm for the next handover
        incoming.setVolume(volume)
        activeIndex = 1 - activeIndex
        fadeStartMs = NOT_FADING
        lastActivePositionMs = incoming.positionMs
        handovers++
    }

    private fun abortFade() {
        val outgoing = activeVoice
        val incoming = inactiveVoice
        fadeStartMs = NOT_FADING
        outgoing.setVolume(volume)
        incoming.pause()
        incoming.setVolume(0f)
        incoming.recover()
        lastActivePositionMs = outgoing.positionMs
    }

    private fun hardSwitchFrom(failed: LoopVoice) {
        failed.pause()
        failed.setVolume(0f)
        val other = inactiveVoice
        if (!other.needsRecovery) {
            other.setVolume(volume)
            other.play()
            activeIndex = 1 - activeIndex
            lastActivePositionMs = other.positionMs
            recoveries++
        }
        failed.recover()
        if (failed === activeVoice) {
            // Both voices broken: retry the failed one in place.
            failed.setVolume(volume)
            failed.play()
        } else {
            failed.seekToStart()
        }
    }

    companion object {
        const val DEFAULT_CROSSFADE_MS = 2000L
        const val DEFAULT_LEAD_MS = 150L
        const val FADE_TICK_MS = 40L
        const val NORMAL_TICK_MS = 500L
        const val IDLE_TICK_MS = 1000L
        private const val WRAP_DETECT_MS = 1000L
        private const val START_TOLERANCE_MS = 50L
        private const val NOT_FADING = Long.MIN_VALUE
        private const val HALF_PI = Math.PI / 2.0
    }
}
