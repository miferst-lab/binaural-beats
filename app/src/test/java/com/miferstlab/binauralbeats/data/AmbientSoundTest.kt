package com.miferstlab.binauralbeats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AmbientSoundTest {

    @Test
    fun libraryIsLargeAndEveryCategoryHasTracks() {
        assertEquals(55, AmbientSound.tracks.size)
        AmbientCategory.entries.forEach { cat ->
            assertTrue("$cat has tracks", AmbientSound.inCategory(cat).size >= 4)
        }
    }

    @Test
    fun darkAmbientCategoryIsLastAndHasElevenLoopableTracks() {
        assertEquals(AmbientCategory.DARK, AmbientCategory.entries.last())
        assertEquals("dark", AmbientCategory.DARK.prefsValue)
        val dark = AmbientSound.inCategory(AmbientCategory.DARK)
        assertEquals(11, dark.size)
        dark.forEach { assertTrue(it.prefsValue, it.prefsValue.startsWith("dark_")) }
        assertEquals(AmbientSound.DARK_TECTONIC_CATHEDRAL, AmbientSound.fromPrefs("dark_tectonic_cathedral"))
    }

    @Test
    fun darkAmbientHasStringsInBothLanguages() {
        val en = File("src/main/res/values/strings.xml").readText()
        val pl = File("src/main/res/values-pl/strings.xml").readText()
        listOf(en, pl).forEach { xml ->
            assertTrue(xml.contains("name=\"ambient_cat_dark\""))
            AmbientSound.inCategory(AmbientCategory.DARK).forEach {
                assertTrue(it.prefsValue, xml.contains("name=\"ambient_${it.prefsValue}\""))
            }
        }
        assertTrue(en.contains(">Dark ambient<"))
        assertTrue(pl.contains(">Mroczny ambient<"))
    }

    @Test
    fun manifestListsDarkAmbientTracksWithDurations() {
        val manifest = File("src/main/assets/ambient/manifest.json")
        if (!manifest.exists()) return
        val text = manifest.readText()
        assertTrue(text.contains("\"key\": \"dark\""))
        AmbientSound.inCategory(AmbientCategory.DARK).forEach {
            assertTrue(it.prefsValue, text.contains("\"file\": \"ambient/${it.prefsValue}.ogg\""))
        }
        val dur = Regex("\"key\": \"(dark_[a-z_]+)\",\\s+\"category\": \"dark\",[^}]*?\"durationSec\": ([0-9.]+)")
            .findAll(text).map { it.groupValues[2].toDouble() }.toList()
        assertEquals(11, dur.size)
        dur.forEach { assertTrue("loop at least ~2.9 min: $it", it >= 170.0) }
    }

    @Test
    fun manifestHasNoPerTrackTier() {
        // Business model: full app during the 7-day trial, then Premium — no free/premium split per track.
        val manifest = File("src/main/assets/ambient/manifest.json")
        if (!manifest.exists()) return
        val text = manifest.readText()
        assertTrue("manifest must not contain a per-track premium flag", !text.contains("\"premium\""))
        assertTrue("manifest must not contain a per-track free flag", !text.contains("\"free\""))
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
