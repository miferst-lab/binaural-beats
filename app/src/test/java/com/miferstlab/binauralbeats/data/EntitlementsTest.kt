package com.miferstlab.binauralbeats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    @Test
    fun trialActiveWithinSevenDays() {
        val start = 1_000_000L
        val day = 24L * 60L * 60L * 1000L
        assertTrue(Entitlements.isTrialActive(start, start + day))
        assertTrue(Entitlements.isTrialActive(start, start + 6 * day))
        assertFalse(Entitlements.isTrialActive(start, start + 7 * day))
        assertFalse(Entitlements.isTrialActive(start, start + 8 * day))
    }

    @Test
    fun remainingDaysCeil() {
        val start = 1_000_000L
        val day = 24L * 60L * 60L * 1000L
        assertEquals(7, Entitlements.trialRemainingDays(start, start))
        assertEquals(7, Entitlements.trialRemainingDays(start, start + 1))
        assertEquals(1, Entitlements.trialRemainingDays(start, start + 6 * day + 1))
        assertEquals(0, Entitlements.trialRemainingDays(start, start + 7 * day))
    }
}
