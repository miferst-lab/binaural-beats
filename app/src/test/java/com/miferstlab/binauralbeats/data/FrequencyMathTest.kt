package com.miferstlab.binauralbeats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for pure frequency helpers (no Android runtime required).
 * Run with: ./gradlew test
 */
class FrequencyMathTest {

    @Test
    fun leftRight_splitBeatSymmetricallyAroundCarrier() {
        val carrier = 220f
        val beat = 10f
        val left = FrequencyMath.leftHz(carrier, beat)
        val right = FrequencyMath.rightHz(carrier, beat)

        assertEquals(215f, left, 0.001f)
        assertEquals(225f, right, 0.001f)
        assertEquals(beat, FrequencyMath.beatFromEars(left, right), 0.001f)
        assertEquals(carrier, (left + right) / 2f, 0.001f)
    }

    @Test
    fun clampCarrier_boundsToDocumentedRange() {
        assertEquals(FrequencyMath.CARRIER_MIN, FrequencyMath.clampCarrier(10f), 0f)
        assertEquals(FrequencyMath.CARRIER_MAX, FrequencyMath.clampCarrier(9999f), 0f)
        assertEquals(200f, FrequencyMath.clampCarrier(200f), 0f)
    }

    @Test
    fun clampBeat_boundsToDocumentedRange() {
        assertEquals(FrequencyMath.BEAT_MIN, FrequencyMath.clampBeat(0.1f), 0f)
        assertEquals(FrequencyMath.BEAT_MAX, FrequencyMath.clampBeat(100f), 0f)
        assertEquals(9f, FrequencyMath.clampBeat(9f), 0f)
    }

    @Test
    fun clampVolume_zeroToOne() {
        assertEquals(0f, FrequencyMath.clampVolume(-0.5f), 0f)
        assertEquals(1f, FrequencyMath.clampVolume(1.5f), 0f)
        assertEquals(0.35f, FrequencyMath.clampVolume(0.35f), 0f)
    }

    @Test
    fun earFrequencies_neverBelowAudibleFloor() {
        // Extreme: tiny carrier with large beat — left would be negative without clamp.
        val left = FrequencyMath.leftHz(10f, 40f)
        val right = FrequencyMath.rightHz(10f, 40f)
        assertTrue(left >= FrequencyMath.EAR_FREQ_MIN)
        assertTrue(right >= FrequencyMath.EAR_FREQ_MIN)
        assertTrue(left <= FrequencyMath.EAR_FREQ_MAX)
        assertTrue(right <= FrequencyMath.EAR_FREQ_MAX)
    }
}
