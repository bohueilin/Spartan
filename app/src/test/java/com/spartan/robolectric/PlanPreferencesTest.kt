package com.spartan.robolectric

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spartan.data.local.PreferencesStore
import com.spartan.data.local.avoidOptionsFrom
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.PlanConstraints
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The goal wizard's on-device settings in DataStore: plan constraints and the two day markers
 * round-trip, a field cleared in the wizard is removed rather than left behind, and clear() (full
 * data erasure) wipes them along with everything else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PreferencesStore(context)

    private val everything = PlanConstraints(
        minutesPerDay = 30,
        limitsNote = "Left knee: no jumping",
        bedtimeMinute = 22 * 60 + 30,
        wakeMinute = 6 * 60 + 45,
        avoid = setOf(AvoidOption.STRENGTH_TRAINING, AvoidOption.MORNING_SESSIONS),
    )

    @Before
    fun startEmpty() {
        // The DataStore is a process-wide singleton per file, so each test starts from a cleared one.
        runBlocking { store.clear() }
    }

    @Test
    fun absentKeys_readAsNotGiven() {
        runBlocking {
            val constraints = store.planConstraints.first()
            assertEquals(PlanConstraints(), constraints)
            assertTrue(constraints.isEmpty)
            assertNull(store.morningRevealPlayedDay.first())
            assertNull(store.lastGoalReviewDay.first())
            assertNull(store.reflectionDismissedDay.first())
        }
    }

    @Test
    fun planConstraints_roundTrip() {
        runBlocking {
            store.setPlanConstraints(everything)

            assertEquals(everything, store.planConstraints.first())
        }
    }

    @Test
    fun planConstraints_aClearedFieldIsRemovedNotLeftBehind() {
        runBlocking {
            store.setPlanConstraints(everything)
            store.setPlanConstraints(PlanConstraints(minutesPerDay = 20, limitsNote = "  "))

            assertEquals(PlanConstraints(minutesPerDay = 20), store.planConstraints.first())
        }
    }

    @Test
    fun dayMarkers_roundTrip() {
        runBlocking {
            store.setMorningRevealPlayedDay(20_000L)
            store.setLastGoalReviewDay(19_995L)
            store.setReflectionDismissedDay(20_001L)

            assertEquals(20_000L, store.morningRevealPlayedDay.first())
            assertEquals(19_995L, store.lastGoalReviewDay.first())
            // "Not tonight" outlives the process, so a cold start that evening doesn't ask again.
            assertEquals(20_001L, store.reflectionDismissedDay.first())
        }
    }

    @Test
    fun firstOpen_onboardingRestartsTheClock() {
        runBlocking {
            store.recordFirstOpenIfNeeded(1_000L)
            store.setFirstOpenMillis(5_000L)
            store.recordFirstOpenIfNeeded(9_000L)

            assertEquals(5_000L, store.firstOpenMillis.first())
        }
    }

    @Test
    fun clear_wipesTheWizardSettingsWithEverythingElse() {
        runBlocking {
            store.setOnboardingComplete(true)
            store.setPlanConstraints(everything)
            store.setMorningRevealPlayedDay(20_000L)
            store.setLastGoalReviewDay(19_995L)
            store.setReflectionDismissedDay(20_001L)

            store.clear()

            assertFalse(store.onboardingComplete.first())
            assertEquals(PlanConstraints(), store.planConstraints.first())
            assertNull(store.morningRevealPlayedDay.first())
            assertNull(store.lastGoalReviewDay.first())
            assertNull(store.reflectionDismissedDay.first())
        }
    }

    @Test
    fun avoidOptions_unknownNamesAreIgnored() {
        // A won't-do renamed or removed in an update must not crash the read or be guessed at.
        assertEquals(setOf(AvoidOption.BREATHWORK), avoidOptionsFrom(setOf("BREATHWORK", "SWIMMING", "")))
        assertEquals(emptySet<AvoidOption>(), avoidOptionsFrom(null))
    }
}
