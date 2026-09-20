package com.miferstlab.binauralbeats.data

/**
 * Looping ambient beds layered under binaural tones.
 * Resource ids live next to UI/player (same pattern as [BinauralMode]),
 * so this type compiles without Android R.
 *
 * [isPremium] beds require an entitlement unlock; free tier keeps the original six.
 */
enum class AmbientSound(
    val prefsValue: String,
    val rawName: String?,
    val isPremium: Boolean = false
) {
    OFF("off", null),
    FOREST_NIGHT("forest_night", "ambient_forest_night"),
    WAVES("waves", "ambient_waves"),
    MORNING_VILLAGE("morning_village", "ambient_morning_village"),
    RAIN("rain", "ambient_rain"),
    FIREPLACE("fireplace", "ambient_fireplace"),
    STREAM("stream", "ambient_stream"),
    /** Premium placeholder — asset TBD; currently reuses waves. */
    MOUNTAIN_WIND("mountain_wind", "ambient_waves", isPremium = true),
    /** Premium placeholder — asset TBD; currently reuses stream. */
    CAVE_DRIP("cave_drip", "ambient_stream", isPremium = true),
    /** Premium placeholder — asset TBD; currently reuses rain. */
    SOFT_THUNDER("soft_thunder", "ambient_rain", isPremium = true);

    companion object {
        fun fromPrefs(value: String?): AmbientSound =
            entries.firstOrNull { it.prefsValue == value } ?: OFF

        val freeEntries: List<AmbientSound> = entries.filter { !it.isPremium }
        val premiumEntries: List<AmbientSound> = entries.filter { it.isPremium }
    }
}
