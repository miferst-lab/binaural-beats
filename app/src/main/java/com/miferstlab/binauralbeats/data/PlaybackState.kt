package com.miferstlab.binauralbeats.data

data class PlaybackState(
    val isPlaying: Boolean = false,
    val mode: BinauralMode = BinauralMode.RELAX,
    val volume: Float = BinauralMode.RELAX.defaultVolume,
    val customCarrierHz: Float = 200f,
    val customBeatHz: Float = 10f,
    val mixWithOtherApps: Boolean = true,
    val keepScreenOn: Boolean = false,
    val appearanceMode: AppearanceMode = AppearanceMode.NIGHT,
    val ambient: AmbientSound = AmbientSound.OFF,
    val ambientVolume: Float = 0.35f,
    /** SharedPreferences / Billing entitlement. */
    val isPremium: Boolean = false,
    /** Trial still active (computed; full access while true unless premium). */
    val isTrialActive: Boolean = true,
    /** Whole days left in trial (0 when expired / premium). */
    val trialDaysRemaining: Int = 7,
    /** One-shot UI flag: trial expired, purchase required to play. */
    val showTrialExpiredDialog: Boolean = false
) {
    /** App is usable (all modes, all ambients): Premium, or still within the 7-day trial. */
    val hasFullAccess: Boolean get() = isPremium || isTrialActive
}
