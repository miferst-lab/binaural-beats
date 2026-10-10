package com.miferstlab.binauralbeats.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Multi-cycle tests of the 1.5.3 ambient loop path (decoder pass → crossfade looper → sink).
 *
 * Regression target: on the owner's phone the ambient bed went silent after the first ~5 min pass
 * (1.5.0 and 1.5.2). These tests drive many passes and assert that output never stops, that every
 * wrap is the exact equal-power crossfade of tail and head, and that decoder failures only cost a
 * restart of the clip, never the end of playback.
 */
class SeamlessPcmLooperTest {

    /** Collects everything written, like an AudioTrack that never fails. */
    private class CollectingSink : PcmSink {
        private var data = ShortArray(1 shl 16)
        var size = 0
            private set
        var writes = 0
            private set

        override fun write(data: ShortArray, length: Int): Boolean {
            if (size + length > this.data.size) this.data = this.data.copyOf(maxOf(this.data.size * 2, size + length))
            System.arraycopy(data, 0, this.data, size, length)
            size += length
            writes++
            return true
        }

        fun toArray(): ShortArray = data.copyOf(size)
    }

    /**
     * Fake decoder for a clip of [frames] stereo frames. Returns random chunk sizes, optionally
     * fails on chosen opens / mid-pass reads, and counts how often it was (re)opened.
     */
    private class FakeSource(
        val clip: ShortArray,
        private val random: Random = Random(7),
        private val maxChunk: Int = 777,
    ) : PcmPassSource {
        var opens = 0
        var isOpen = false
        var pos = 0
        val failOpenAt = mutableSetOf<Int>()          // open numbers (1-based) that throw
        val failReadAt = mutableMapOf<Int, Int>()     // open number -> sample position that throws
        var emptyReadsEvery = 0

        private var reads = 0

        override fun open() {
            opens++
            if (opens in failOpenAt) throw IllegalStateException("open #$opens failed")
            isOpen = true
            pos = 0
        }

        override fun read(dst: ShortArray): Int {
            check(isOpen) { "read while closed" }
            reads++
            if (emptyReadsEvery > 0 && reads % emptyReadsEvery == 0) return 0
            failReadAt[opens]?.let { at -> if (pos >= at) throw IllegalStateException("decoder died") }
            if (pos >= clip.size) return PcmPassSource.END_OF_PASS
            var n = minOf(dst.size, clip.size - pos, 2 * (1 + random.nextInt(maxChunk)))
            failReadAt[opens]?.let { at -> if (pos < at) n = minOf(n, at - pos) }
            System.arraycopy(clip, pos, dst, 0, n)
            pos += n
            return n
        }

        override fun close() {
            isOpen = false
        }
    }

    private fun clip(frames: Int, seed: Int = 1): ShortArray {
        val r = Random(seed)
        // Never zero, so a gap (silence) in the output cannot hide.
        return ShortArray(frames * 2) { (1000 + r.nextInt(20000)).toShort() }
    }

    /** Reference stream for [passes] wraps: c[0‥L−X) · (mix(c[L−X‥L), c[0‥X)) · c[X‥L−X))×passes. */
    private fun expected(c: ShortArray, xFrames: Int, passes: Int): ShortArray {
        val x = xFrames * 2
        val out = ArrayList<Short>()
        for (i in 0 until c.size - x) out.add(c[i])
        repeat(passes) {
            for (i in 0 until x) {
                out.add(SeamlessPcmLooper.mix(c[c.size - x + i], c[i], i / 2, xFrames))
            }
            for (i in x until c.size - x) out.add(c[i])
        }
        return out.toShortArray()
    }

    private fun runUntilPasses(pump: AmbientLoopPump, passes: Int, maxSteps: Int = 2_000_000) {
        var steps = 0
        while (pump.passes < passes) {
            pump.step()
            assertTrue("pump stalled", ++steps < maxSteps)
        }
    }

    @Test
    fun twelvePasses_streamIsEndlessAndEveryWrapIsTheExactCrossfade() {
        val frames = 5000
        val x = 400
        val c = clip(frames)
        val sink = CollectingSink()
        val source = FakeSource(c)
        val pump = AmbientLoopPump(source, sink, crossfadeFrames = x, readChunkSamples = 1024)

        runUntilPasses(pump, 12)

        // Output keeps coming after the first pass: 12 wraps, source reopened every time.
        assertEquals(12, pump.passes)
        assertEquals(12, source.opens) // the 13th pass opens on the next step
        // Everything up to the 12th wrap's held-back tail is the reference stream, sample for sample.
        val got = sink.toArray()
        val want = expected(c, x, 11)
        assertTrue("output ${got.size} shorter than ${want.size}", got.size >= want.size)
        assertArrayEquals(want, got.copyOf(want.size))
        // No silence anywhere (clip has no zero samples; equal-power gains sum to >= 1).
        assertTrue(got.none { it.toInt() == 0 })
    }

    @Test
    fun crossfadeIsEqualPower_constantSignalStaysAtLeastAsLoudAcrossTheWrap() {
        val frames = 3000
        val x = 1000
        val c = ShortArray(frames * 2) { 10000 }
        val sink = CollectingSink()
        val pump = AmbientLoopPump(FakeSource(c), sink, crossfadeFrames = x)
        runUntilPasses(pump, 3)
        val got = sink.toArray()
        // cos + sin of the same signal: 1 at the edges, √2 in the middle of the fade.
        assertTrue(got.all { it >= 10000 })
        val mid = (frames - x) * 2 + x // middle of the first crossfade (in samples)
        assertEquals(14142.0, got[mid].toDouble(), 30.0)
    }

    @Test
    fun manyPasses_totalLengthGrowsByClipMinusCrossfadeEachWrap() {
        val frames = 2000
        val x = 300
        val sink = CollectingSink()
        val pump = AmbientLoopPump(FakeSource(clip(frames, 3), Random(11), 333), sink, x)
        for (p in 1..50) {
            runUntilPasses(pump, p)
            // After p wraps everything except the held-back tail has been written.
            val expectedSamples = (frames - x).toLong() * 2 * p
            assertEquals("after $p passes", expectedSamples, pump.looper.samplesOut)
            assertEquals(expectedSamples, sink.size.toLong())
        }
    }

    @Test
    fun decoderDyingMidPass_restartsClipWithCrossfadeAndKeepsLooping() {
        val frames = 4000
        val x = 200
        val c = clip(frames, 5)
        val source = FakeSource(c).apply {
            failReadAt[2] = 3000 * 2   // second pass dies at frame 3000
            failReadAt[5] = 100 * 2    // fifth open dies almost immediately
        }
        val sink = CollectingSink()
        val pump = AmbientLoopPump(source, sink, x)

        runUntilPasses(pump, 10)

        assertEquals(2, pump.failures)
        assertTrue(source.opens >= 11)
        val got = sink.toArray()
        assertTrue(got.none { it.toInt() == 0 })
        // The early cut is crossfaded: right after pass 1's normal wrap, pass 2 runs to frame 3000,
        // then frames [2800‥3000) of pass 2 fade out over the head of pass 3.
        val pass1 = (frames - x) * 2
        val wrap1 = x * 2
        val pass2Body = (3000 - x - x) * 2
        val cutStart = pass1 + wrap1 + pass2Body
        for (i in 0 until x * 2) {
            val want = SeamlessPcmLooper.mix(c[(3000 - x) * 2 + i], c[i], i / 2, x)
            assertEquals("sample $i of the recovery crossfade", want, got[cutStart + i])
        }
    }

    @Test
    fun failedReopens_backOffButNeverGiveUp() {
        val frames = 1500
        val x = 100
        val source = FakeSource(clip(frames, 9)).apply { failOpenAt += setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13) }
        val sink = CollectingSink()
        val pump = AmbientLoopPump(source, sink, x)
        var waited = 0L
        var steps = 0
        while (pump.passes < 4) {
            waited += pump.step()
            assertTrue(++steps < 1_000_000)
        }
        assertEquals(12, pump.failures)
        assertTrue("backs off between retries", waited >= 3 * 20 + 7 * 250 + 2 * 1000)
        // Repeated endOfPass calls during the outage did not duplicate or drop the held tail.
        assertEquals((frames - x).toLong() * 2 * 4, pump.looper.samplesOut)
    }

    @Test
    fun emptyReads_doNotEndThePass() {
        val frames = 1200
        val x = 100
        val c = clip(frames, 13)
        val source = FakeSource(c).apply { emptyReadsEvery = 3 }
        val sink = CollectingSink()
        val pump = AmbientLoopPump(source, sink, x)
        runUntilPasses(pump, 6)
        assertEquals(0, pump.failures)
        val want = expected(c, x, 5)
        assertArrayEquals(want, sink.toArray().copyOf(want.size))
    }

    @Test
    fun clipShorterThanCrossfade_stillLoopsWithoutGaps() {
        val c = clip(150, 21)
        val sink = CollectingSink()
        val pump = AmbientLoopPump(FakeSource(c), sink, crossfadeFrames = 400)
        runUntilPasses(pump, 20)
        assertTrue(sink.size > 0)
        assertTrue(pump.failures == 0)
    }
}
