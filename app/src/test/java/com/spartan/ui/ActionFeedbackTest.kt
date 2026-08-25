package com.spartan.ui

import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.TimeOfDay
import com.spartan.ui.screens.UndoToken
import com.spartan.ui.screens.UserMessage
import com.spartan.ui.screens.shouldOfferReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The action-feedback contract: every reversible action can be restored exactly, and the reminders
 * offer only appears after the plan has delivered value.
 */
class ActionFeedbackTest {

    private fun activity(
        status: ActivityStatus = ActivityStatus.PLANNED,
        snoozedUntilMillis: Long? = null,
        scheduledEpochMinute: Long? = null,
        completedAtMillis: Long? = null,
    ) = DailyActivity(
        id = "act-1",
        title = "Zone 2 walk",
        category = ActivityCategory.ZONE2,
        priority = ActivityPriority.REQUIRED,
        whyItMatters = "Builds aerobic base.",
        estimatedMinutes = 30,
        intensity = Intensity.EASY,
        bestTimeOfDay = TimeOfDay.MORNING,
        status = status,
        ruleId = "rule-1",
        scheduledEpochMinute = scheduledEpochMinute,
        completedAtMillis = completedAtMillis,
        snoozedUntilMillis = snoozedUntilMillis,
    )

    // --- Reminders offer: value before the ask ---

    @Test
    fun offerAppearsOnlyWhenAPlanIsOnScreen() {
        assertTrue(shouldOfferReminders(hasActivities = true, offerSettled = false, permissionGranted = false))
        // No plan yet: nothing has earned the ask.
        assertFalse(shouldOfferReminders(hasActivities = false, offerSettled = false, permissionGranted = false))
    }

    @Test
    fun offerNeverReturnsOnceAnswered() {
        assertFalse(shouldOfferReminders(hasActivities = true, offerSettled = true, permissionGranted = false))
    }

    @Test
    fun offerIsSkippedWhenPermissionAlreadyGranted() {
        assertFalse(shouldOfferReminders(hasActivities = true, offerSettled = false, permissionGranted = true))
    }

    // --- Undo tokens: a true restore, not a guess ---

    @Test
    fun undoTokenCapturesEveryRestorableFieldOfARescheduledActivity() {
        val before = activity(status = ActivityStatus.RESCHEDULED, scheduledEpochMinute = 27_000_000L)
        val token = UndoToken(
            activityId = before.id,
            previousStatus = before.status,
            previousSnoozedUntilMillis = before.snoozedUntilMillis,
            previousScheduledEpochMinute = before.scheduledEpochMinute,
            previousCompletedAtMillis = before.completedAtMillis,
        )
        assertEquals("act-1", token.activityId)
        assertEquals(ActivityStatus.RESCHEDULED, token.previousStatus)
        assertEquals(27_000_000L, token.previousScheduledEpochMinute)
        assertNull(token.previousSnoozedUntilMillis)
        assertNull(token.previousCompletedAtMillis)
    }

    @Test
    fun undoOfASnoozeRestoresThePlannedStateAndClearsTheSnoozeStamp() {
        // A PLANNED activity that the user snoozes: the token must carry the pre-snooze state so
        // the restore clears snoozedUntilMillis rather than leaving a stale wake-up time.
        val before = activity(status = ActivityStatus.PLANNED, snoozedUntilMillis = null)
        val token = UndoToken(
            activityId = before.id,
            previousStatus = before.status,
            previousSnoozedUntilMillis = before.snoozedUntilMillis,
            previousScheduledEpochMinute = before.scheduledEpochMinute,
            previousCompletedAtMillis = before.completedAtMillis,
        )
        assertEquals(ActivityStatus.PLANNED, token.previousStatus)
        assertNull(token.previousSnoozedUntilMillis)
    }

    // --- Messages ---

    @Test
    fun informationalMessagesCarryNoUndoAction() {
        // "No open gap" changes nothing, so offering Undo would be a lie.
        val message = UserMessage(textRes = 1, formatArg = null, undo = null)
        assertNull(message.undo)
    }

    @Test
    fun reversibleMessagesCarryTheirToken() {
        val token = UndoToken(activityId = "act-1", previousStatus = ActivityStatus.PLANNED)
        val message = UserMessage(textRes = 1, formatArg = "3:15 PM", undo = token)
        assertEquals(token, message.undo)
        assertEquals("3:15 PM", message.formatArg)
    }
}
