package com.miferstlab.binauralbeats.data

data class PlaybackState(
    val isPlaying: Boolean = false,
    val mode: BinauralMode = BinauralMode.RELAKS,
    val volume: Float = BinauralMode.RELAKS.defaultVolume,
    val customCarrierHz: Float = 200f,
    val customBeatHz: Float = 10f,
    val mixWithOtherApps: Boolean = true,
    val keepScreenOn: Boolean = false,
    val appearanceMode: AppearanceMode = AppearanceMode.NIGHT,
    val ambient: AmbientSound = AmbientSound.OFF,
    val ambientVolume: Float = 0.35f,
    /** SharedPreferences / Billing entitlement. */
    val isPremium: Boolean = false,
    /** Accumulated active playback ms in the current free session. */
    val sessionElapsedMs: Long = 0L,
    /** One-shot UI flag: free 30:00 limit hit. */
    val showFreeLimitDialog: Boolean = false,
    /** One-shot UI flag: user tapped a locked premium ambient. */
    val showPremiumUpsellDialog: Boolean = false
)
