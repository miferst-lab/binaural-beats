package com.miferstlab.binauralbeats.data

/**
 * User appearance preference. Default is [NIGHT] (galactic night-first).
 * Persisted as SharedPreferences key `appearance_mode` in prefs file `binaural`.
 */
enum class AppearanceMode(val prefsValue: String) {
    NIGHT("night"),
    SYSTEM("system"),
    LIGHT("light");

    companion object {
        fun fromPrefs(value: String?): AppearanceMode =
            entries.find { it.prefsValue.equals(value, ignoreCase = true) } ?: NIGHT
    }
}
