package com.miferstlab.binauralbeats.data

import com.miferstlab.binauralbeats.R

/**
 * Looping ambient beds layered under binaural tones.
 * [OFF] has no raw resource; others map to res/raw/*.ogg.
 */
enum class AmbientSound(
    val prefsValue: String,
    val labelRes: Int,
    val rawResId: Int?
) {
    OFF(
        prefsValue = "off",
        labelRes = R.string.ambient_off,
        rawResId = null
    ),
    FOREST_NIGHT(
        prefsValue = "forest_night",
        labelRes = R.string.ambient_forest_night,
        rawResId = R.raw.ambient_forest_night
    ),
    WAVES(
        prefsValue = "waves",
        labelRes = R.string.ambient_waves,
        rawResId = R.raw.ambient_waves
    ),
    MORNING_VILLAGE(
        prefsValue = "morning_village",
        labelRes = R.string.ambient_morning_village,
        rawResId = R.raw.ambient_morning_village
    ),
    RAIN(
        prefsValue = "rain",
        labelRes = R.string.ambient_rain,
        rawResId = R.raw.ambient_rain
    ),
    FIREPLACE(
        prefsValue = "fireplace",
        labelRes = R.string.ambient_fireplace,
        rawResId = R.raw.ambient_fireplace
    ),
    STREAM(
        prefsValue = "stream",
        labelRes = R.string.ambient_stream,
        rawResId = R.raw.ambient_stream
    );

    companion object {
        fun fromPrefs(value: String?): AmbientSound =
            entries.firstOrNull { it.prefsValue == value } ?: OFF
    }
}
