package com.miferstlab.binauralbeats.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless multi-cycle loop tests with a fake clock and fake players.
 *
 * [FakeVoice] mimics the ExoPlayer behaviour that matters here: async prepare, position that only
 * advances while READY and playing, a short re-buffer after every seek, REPEAT_MODE_ONE wrap (or
 * STATE_ENDED when repeat is off) and player errors that leave the player IDLE.
 */
class AmbientLoopControllerTest {

    private class World {
        var now = 0L
    }

    private class FakeVoice(
        private val world: World,
        private val clipMs: Long = CLIP_MS,
        private val repeat: Boolean = true,
    ) : LoopVoice {
        var playWhenReady = false
        var gainSetting = 0f
        var ended = false
        var idle = false
        var failOnNextPlay = false
        private var pos = 0L
        private var readyAt = PREPARE_MS
        private var last = 0L

        private fun ready() = !idle && world.now >= readyAt

        fun advance() {
            val now = world.now
            if (playWhenReady && !ended && !idle) {
                val from = maxOf(last, readyAt)
                if (now > from) {
                    pos += now - from
                    if (pos >= clipMs) {
                        if (repeat) pos %= clipMs else { pos = clipMs; ended = true }
                    }
                }
            }
            last = now
        }

        /** Audible gain right now (0 while preparing, re-buffering, paused, ended or failed). */
        fun gain(): Float = if (playWhenReady && ready() && !ended) gainSetting else 0f

        fun fail() { advance(); idle = true }

        override val durationMs: Long get() = if (world.now >= PREPARE_MS) clipMs else -1L
        override val positionMs: Long get() { advance(); return pos }
        override val needsRecovery: Boolean get() = idle

        override fun play() {
            advance()
            if (failOnNextPlay) { failOnNextPlay = false; idle = true; return }
            playWhenReady = true
        }
        override fun pause() { advance(); playWhenReady = false }
        override fun seekToStart() {
            advance(); pos = 0L; ended = false; readyAt = world.now + SEEK_MS
        }
        override fun setVolume(volume: Float) { gainSetting = volume }
        override fun recover() {
            advance(); if (idle) { idle = false; readyAt = world.now + PREPARE_MS }
        }
    }

    private class Result(val maxGapMs: Long, val handovers: Int, val selfWraps: Int)

    /**
     * Runs the host loop: calls tick() whenever it is due (plus [extraDelay] to inject jank /
     * a stalled main thread) and samples the summed audible gain every [STEP_MS].
     */
    private fun simulate(
        world: World,
        a: FakeVoice,
        b: FakeVoice,
        c: AmbientLoopController,
        durationMs: Long,
        extraDelay: (now: Long) -> Long = { 0L },
        onStep: (now: Long) -> Unit = {},
    ): Result {
        var nextTick = world.now
        var gapStart = -1L
        var maxGap = 0L
        val end = world.now + durationMs
        while (world.now < end) {
            onStep(world.now)
            if (world.now >= nextTick) {
                val delay = c.tick()
                nextTick = world.now + delay + extraDelay(world.now)
            }
            a.advance(); b.advance()
            val audible = c.isPlaying && (a.gain() + b.gain()) >= 0.5f * c.volume
            if (c.isPlaying && world.now > PREPARE_MS + SEEK_MS) {
                if (!audible) {
                    if (gapStart < 0) gapStart = world.now
                    maxGap = maxOf(maxGap, world.now - gapStart)
                } else gapStart = -1L
            }
            world.now += STEP_MS
        }
        return Result(maxGap, c.handovers, c.selfWraps)
    }

    private fun setup(repeat: Boolean = true): Triple<World, Pair<FakeVoice, FakeVoice>, AmbientLoopController> {
        val world = World()
        val a = FakeVoice(world, repeat = repeat)
        val b = FakeVoice(world, repeat = repeat)
        val c = AmbientLoopController(a, b, clock = { world.now })
        c.setVolume(0.4f)
        c.start()
        return Triple(world, a to b, c)
    }

    @Test
    fun loopsTwelveCyclesSeamlessly() {
        val (world, v, c) = setup()
        val r = simulate(world, v.first, v.second, c, durationMs = 12 * CLIP_MS + 30_000)
        assertEquals("one crossfade handover per pass", 12, r.handovers)
        assertEquals("never fell back to self-wrap", 0, r.selfWraps)
        assertTrue("no audible gap, max ${r.maxGapMs} ms", r.maxGapMs <= STEP_MS)
        assertTrue(c.isPlaying)
    }

    @Test
    fun lateTicksAtEveryLoopPointStillNeverGoSilent() {
        val (world, v, c) = setup()
        // Main thread stalls for 8 s just before each loop point (jank, CPU doze, …).
        val r = simulate(world, v.first, v.second, c, durationMs = 6 * CLIP_MS, extraDelay = { now ->
            val inClip = now % CLIP_MS
            if (inClip in (CLIP_MS - 10_000)..(CLIP_MS - 2_500)) 8_000L else 0L
        })
        assertTrue("no silence even with missed handovers, max ${r.maxGapMs} ms", r.maxGapMs <= STEP_MS)
        assertTrue(c.isPlaying)
    }

    @Test
    fun ticksStoppedCompletelyStillLoopsForAnHour() {
        val (world, v, c) = setup()
        val a = v.first
        // Let it get going, then never tick again: ExoPlayer's own REPEAT_MODE_ONE must keep it alive.
        val r = simulate(world, a, v.second, c, durationMs = 3_600_000L, extraDelay = { 10_000_000L })
        assertTrue("bed never stopped, max gap ${r.maxGapMs} ms", r.maxGapMs <= STEP_MS)
        assertTrue(a.gain() > 0f)
    }

    @Test
    fun legacyRepeatOffWithMissedHandoverStopsAfterOnePass() {
        // Documents the 1.5.1 failure: REPEAT_MODE_OFF + handover that does not run → one pass, then silence.
        val (world, v, c) = setup(repeat = false)
        val r = simulate(world, v.first, v.second, c, durationMs = 2 * CLIP_MS, extraDelay = { 10_000_000L })
        assertTrue("legacy path goes silent (gap ${r.maxGapMs} ms)", r.maxGapMs > 60_000L)
        assertTrue(v.first.ended)
    }

    @Test
    fun failedIncomingPlayerIsRecoveredAndLoopingContinues() {
        val (world, v, c) = setup()
        v.second.failOnNextPlay = true // B fails right at the first handover
        val r = simulate(world, v.first, v.second, c, durationMs = 5 * CLIP_MS)
        assertTrue("never silent, max gap ${r.maxGapMs} ms", r.maxGapMs <= STEP_MS)
        assertTrue("later handovers still happen (${r.handovers})", r.handovers >= 3)
    }

    @Test
    fun failedActivePlayerHandsOverImmediately() {
        val (world, v, c) = setup()
        var failed = false
        val r = simulate(world, v.first, v.second, c, durationMs = 4 * CLIP_MS, onStep = { now ->
            if (!failed && now >= 100_000L) { failed = true; c.activeVoice.let { (it as FakeVoice).fail() } }
        })
        assertEquals(1, c.recoveries)
        assertTrue("gap after failure ${r.maxGapMs} ms", r.maxGapMs <= 500L)
        assertTrue(r.handovers >= 3)
    }

    @Test
    fun pauseDuringCrossfadeThenResumeKeepsLooping() {
        val (world, v, c) = setup()
        var paused = false
        var resumed = false
        simulate(world, v.first, v.second, c, durationMs = 4 * CLIP_MS, onStep = { now ->
            if (!paused && c.isFading) { paused = true; c.pause() }
            if (paused && !resumed && now >= CLIP_MS + 60_000) { resumed = true; c.start() }
        })
        assertTrue(paused && resumed)
        assertTrue(c.isPlaying)
        assertTrue(c.handovers >= 3)
        assertTrue(c.activeVoice.let { (it as FakeVoice).gain() } > 0f || c.isFading)
    }

    @Test
    fun volumeChangeDuringFadeLandsOnNewVolume() {
        val (world, v, c) = setup()
        var changed = false
        simulate(world, v.first, v.second, c, durationMs = CLIP_MS + 10_000, onStep = {
            if (!changed && c.isFading) { changed = true; c.setVolume(0.8f) }
        })
        assertTrue(changed)
        assertEquals(0.8f, (c.activeVoice as FakeVoice).gainSetting, 1e-4f)
        assertEquals(0f, (c.inactiveVoice as FakeVoice).gainSetting, 1e-4f)
    }

    companion object {
        private const val CLIP_MS = 300_000L // assets are ~5 min
        private const val PREPARE_MS = 120L
        private const val SEEK_MS = 40L
        private const val STEP_MS = 10L
    }
}
