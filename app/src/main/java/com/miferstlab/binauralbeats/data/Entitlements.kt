package com.miferstlab.binauralbeats.data

/**
 * Freemium limits and Play product ids.
 *
 * Free: basic ambients + hard 30-minute listen clock per session (accumulates
 * while [PlaybackState.isPlaying]; pauses freeze the clock; stop resets).
 * Paid: premium ambient slots + unlimited session length.
 */
object Entitlements {
    /** Hard free-tier listening limit (milliseconds of active playback). */
    const val FREE_LISTEN_LIMIT_MS: Long = 30L * 60L * 1000L

    /**
     * Google Play Billing product id for premium unlock.
     * Create a non-consumable (or subscription) with this id in Play Console,
     * then wire [com.miferstlab.binauralbeats.billing.BillingManager].
     */
    const val PRODUCT_ID_PREMIUM: String = "binaural_premium_unlock"

    fun remainingFreeMs(elapsedMs: Long): Long =
        (FREE_LISTEN_LIMIT_MS - elapsedMs).coerceAtLeast(0L)

    fun formatMmSs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return "%d:%02d".format(m, s)
    }
}
