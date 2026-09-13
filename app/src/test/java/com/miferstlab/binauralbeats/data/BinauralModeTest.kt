package com.miferstlab.binauralbeats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * JVM unit tests for preset / custom L-R frequency calculation.
 */
class BinauralModeTest {

    @Test
    fun relaxPreset_alphaBeatAround220() {
        val mode = BinauralMode.RELAKS
        val left = mode.leftHz()
        val right = mode.rightHz()

        assertEquals(220f - 9f / 2f, left, 0.001f)
        assertEquals(220f + 9f / 2f, right, 0.001f)
        assertEquals(9f, FrequencyMath.beatFromEars(left, right), 0.001f)
    }

    @Test
    fun focusPreset_betaBeat() {
        val mode = BinauralMode.SKUPIENIE
        assertEquals(16f, FrequencyMath.beatFromEars(mode.leftHz(), mode.rightHz()), 0.001f)
        assertEquals(220f, (mode.leftHz() + mode.rightHz()) / 2f, 0.001f)
    }

    @Test
    fun readingPreset_smrBeat() {
        val mode = BinauralMode.CZYTANIE
        assertEquals(13f, FrequencyMath.beatFromEars(mode.leftHz(), mode.rightHz()), 0.001f)
        assertEquals(210f, (mode.leftHz() + mode.rightHz()) / 2f, 0.001f)
    }

    @Test
    fun energyPreset_highBetaBeat() {
        val mode = BinauralMode.ENERGIA
        assertEquals(22f, FrequencyMath.beatFromEars(mode.leftHz(), mode.rightHz()), 0.001f)
        assertEquals(230f, (mode.leftHz() + mode.rightHz()) / 2f, 0.001f)
    }

    @Test
    fun sleepPreset_deltaBeat_lowerCarrier() {
        val mode = BinauralMode.SEN
        assertEquals(3f, FrequencyMath.beatFromEars(mode.leftHz(), mode.rightHz()), 0.001f)
        assertEquals(180f, (mode.leftHz() + mode.rightHz()) / 2f, 0.001f)
        assertEquals(0.22f, mode.defaultVolume, 0f)
    }

    @Test
    fun meditationPreset_thetaBeat() {
        val mode = BinauralMode.MEDYTACJA
        assertEquals(6f, FrequencyMath.beatFromEars(mode.leftHz(), mode.rightHz()), 0.001f)
        assertEquals(200f, (mode.leftHz() + mode.rightHz()) / 2f, 0.001f)
    }

    @Test
    fun presetIgnoresCustomCarrierAndBeatArgs() {
        val mode = BinauralMode.RELAKS
        val withDefaults = mode.leftHz() to mode.rightHz()
        val withCustomArgs = mode.leftHz(400f, 30f) to mode.rightHz(400f, 30f)
        assertEquals(withDefaults.first, withCustomArgs.first, 0f)
        assertEquals(withDefaults.second, withCustomArgs.second, 0f)
    }

    @Test
    fun customMode_usesClampedCarrierAndBeat() {
        val mode = BinauralMode.NIESTANDARDOWY
        val left = mode.leftHz(customCarrier = 300f, customBeat = 12f)
        val right = mode.rightHz(customCarrier = 300f, customBeat = 12f)

        assertEquals(300f - 6f, left, 0.001f)
        assertEquals(300f + 6f, right, 0.001f)
        assertEquals(12f, FrequencyMath.beatFromEars(left, right), 0.001f)
    }

    @Test
    fun customMode_clampsOutOfRangeInputs() {
        val mode = BinauralMode.NIESTANDARDOWY
        // Carrier below min and beat above max → clamped before L/R split.
        val (c, b) = mode.resolvedCarrierBeat(10f, 99f)
        assertEquals(FrequencyMath.CARRIER_MIN, c, 0f)
        assertEquals(FrequencyMath.BEAT_MAX, b, 0f)

        val left = mode.leftHz(10f, 99f)
        val right = mode.rightHz(10f, 99f)
        assertEquals(FrequencyMath.leftHz(c, b), left, 0f)
        assertEquals(FrequencyMath.rightHz(c, b), right, 0f)
        assertNotEquals(10f - 99f / 2f, left) // raw math must not win
    }

    @Test
    fun customMode_defaultsMatchEnumFields() {
        val mode = BinauralMode.NIESTANDARDOWY
        assertEquals(mode.carrierHz, mode.leftHz().let { it + mode.beatHz / 2f }, 0.001f)
        assertEquals(
            mode.beatHz,
            FrequencyMath.beatFromEars(mode.leftHz(), mode.rightHz()),
            0.001f
        )
    }
}
