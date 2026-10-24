package com.spartan.robolectric

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spartan.data.local.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two display settings in DataStore: both read off until the user opts in, each round-trips on
 * its own (setting one never flips the other), and clear() (full data erasure) wipes them along with
 * everything else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisplayPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PreferencesStore(context)

    @Before
    fun startEmpty() {
        // The DataStore is a process-wide singleton per file, so each test starts from a cleared one.
        runBlocking { store.clear() }
    }

    @Test
    fun absentKeys_readAsOff() {
        runBlocking {
            assertFalse(store.calmerTheme.first())
            assertFalse(store.wallpaperColours.first())
        }
    }

    @Test
    fun calmerTheme_roundTripsWithoutTouchingWallpaperColours() {
        runBlocking {
            store.setCalmerTheme(true)
            assertTrue(store.calmerTheme.first())
            assertFalse(store.wallpaperColours.first())

            store.setCalmerTheme(false)
            assertFalse(store.calmerTheme.first())
        }
    }

    @Test
    fun wallpaperColours_roundTripsWithoutTouchingCalmerTheme() {
        runBlocking {
            store.setWallpaperColours(true)
            assertTrue(store.wallpaperColours.first())
            assertFalse(store.calmerTheme.first())

            store.setWallpaperColours(false)
            assertFalse(store.wallpaperColours.first())
        }
    }

    @Test
    fun clear_wipesTheDisplaySettingsWithEverythingElse() {
        runBlocking {
            store.setOnboardingComplete(true)
            store.setCalmerTheme(true)
            store.setWallpaperColours(true)

            store.clear()

            assertFalse(store.onboardingComplete.first())
            assertFalse(store.calmerTheme.first())
            assertFalse(store.wallpaperColours.first())
        }
    }
}
