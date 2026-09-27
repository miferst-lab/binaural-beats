package com.miferstlab.binauralbeats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AmbientSoundTest {

    @Test
    fun libraryIsLargeAndEveryCategoryHasFreeAndPremium() {
        assertTrue(AmbientSound.tracks.size >= 40)
        AmbientCategory.entries.forEach { cat ->
            val list = AmbientSound.inCategory(cat)
            assertTrue("$cat has tracks", list.size >= 4)
            assertTrue("$cat has a free track", list.any { !it.isPremium })
            assertTrue("$cat has a premium track", list.any { it.isPremium })
        }
    }

    @Test
    fun prefsValuesUniqueAndRoundTrip() {
        val keys = AmbientSound.entries.map { it.prefsValue }
        assertEquals(keys.size, keys.toSet().size)
        AmbientSound.entries.forEach { assertEquals(it, AmbientSound.fromPrefs(it.prefsValue)) }
        assertEquals(AmbientSound.OFF, AmbientSound.fromPrefs(null))
        assertEquals(AmbientSound.OFF, AmbientSound.fromPrefs("nope"))
        assertNull(AmbientSound.OFF.assetPath)
    }

    @Test
    fun legacyPrefsMapToNewTracks() {
        listOf(
            "forest_night", "waves", "morning_village", "rain", "fireplace", "stream",
            "mountain_wind", "cave_drip", "soft_thunder"
        ).forEach { assertTrue(it, AmbientSound.fromPrefs(it) != AmbientSound.OFF) }
    }

    @Test
    fun everyTrackHasCreditsAndCc0License() {
        AmbientSound.tracks.forEach {
            assertTrue(it.name, it.author.isNotBlank() && it.sourceTitle.isNotBlank())
            assertTrue(it.name, it.sourceUrl.startsWith("https://freesound.org/"))
            assertEquals("CC0 1.0", it.license)
        }
    }

    @Test
    fun assetFilesExist() {
        // Unit tests run with the module directory (app/) as working dir.
        val dir = File("src/main/assets")
        if (!dir.exists()) return
        AmbientSound.tracks.forEach {
            val f = File(dir, it.assetPath!!)
            assertTrue("missing ${f.path}", f.isFile && f.length() > 100_000)
        }
        assertTrue(File(dir, "ambient/manifest.json").isFile)
    }
}
