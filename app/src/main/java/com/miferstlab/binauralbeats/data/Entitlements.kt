package com.miferstlab.binauralbeats.data

/**
 * Business model: 7-day free trial of the full app from first install, then purchase required.
 * There is no free tier and no per-track tier.
 *
 * During trial: full app access (all modes, all ambients, unlimited listening).
 * After trial without Premium: playback blocked until purchase.
 * Purchased ([PRODUCT_ID_PREMIUM]): unlimited, no trial nag.
 *
 * The old 30-minute per-session listen clock is retired.
 */
object Entitlements {
    /** Trial length from first install (wall-clock based, with rollback clamp). */
    const val TRIAL_DURATION_MS: Long = 7L * 24L * 60L * 60L * 1000L

    /**
     * Google Play Billing product id for premium unlock.
     * Create a non-consumable (or subscription) with this id in Play Console,
     * then wire [com.miferstlab.binauralbeats.billing.BillingManager].
     */
    const val PRODUCT_ID_PREMIUM: String = "binaural_premium_unlock"

    /** SharedPreferences shared by the ViewModel and the playback service. */
    const val PREFS_NAME: String = "binaural"
    const val KEY_PREMIUM: String = "is_premium"
    const val KEY_TRIAL_START: String = "trial_start_ms"
    const val KEY_FURTHEST_NOW: String = "trial_furthest_now_ms"

    /** The single gate: playback is allowed with Premium or during the 7-day trial. */
    fun hasAccess(isPremium: Boolean, trialStartMs: Long, nowMs: Long): Boolean =
        isPremium || isTrialActive(trialStartMs, nowMs)

    fun trialRemainingMs(trialStartMs: Long, nowMs: Long): Long {
        if (trialStartMs <= 0L) return TRIAL_DURATION_MS
        val end = trialStartMs + TRIAL_DURATION_MS
        return (end - nowMs).coerceAtLeast(0L)
    }

    fun isTrialActive(trialStartMs: Long, nowMs: Long): Boolean =
        trialRemainingMs(trialStartMs, nowMs) > 0L

    /** Whole days remaining (ceil), for UI. 0 when expired. */
    fun trialRemainingDays(trialStartMs: Long, nowMs: Long): Int {
        val rem = trialRemainingMs(trialStartMs, nowMs)
        if (rem <= 0L) return 0
        val dayMs = 24L * 60L * 60L * 1000L
        return ((rem + dayMs - 1L) / dayMs).toInt().coerceAtLeast(1)
    }
}
