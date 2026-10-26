package com.spartan.ui

import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.BedtimePlan
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.TodayNextStep
import com.spartan.ui.screens.greetingRes
import com.spartan.ui.screens.heroRingBeside
import com.spartan.ui.screens.heroRingSize
import com.spartan.ui.screens.planFinished
import com.spartan.ui.screens.todayNextStep
import com.spartan.ui.screens.tonightNoTargetBody
import com.spartan.ui.screens.tonightShowsWindDown
import com.spartan.ui.screens.tonightWindDown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today's pure decisions: the one primary action (the next open action only — no button at all once
 * every action is resolved, so it never repeats the Tonight card's bedtime or wind-down; never a
 * button that only scrolls, never a time already gone, never an invented plan for tomorrow), when
 * the Tonight card offers its wind-down, the hero ring's size and whether the verdict text fits
 * beside it at large font sizes, the greeting, and the bedtime card's copy.
 */
class TodayNextStepTest {

    private fun activity(id: String, status: ActivityStatus = ActivityStatus.PLANNED) = DailyActivity(
        id = id,
        title = "Action $id",
        category = ActivityCategory.MOBILITY,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = "Gentle movement.",
        estimatedMinutes = 10,
        intensity = Intensity.EASY,
        bestTimeOfDay = TimeOfDay.MORNING,
        status = status,
        ruleId = "rule-$id",
    )

    // Wind down 9:55 PM, lights out 10:40 PM.
    private val bedtime = BedtimePlan(windDownMinute = 21 * 60 + 55, lightsOutMinute = 22 * 60 + 40, sleepHours = 9.83, debtHours = 0.73, needHours = 9.83)

    private val noon = 12 * 60

    // --- The primary action ---

    @Test
    fun theSleepAnchor_pastLightsOut_neverShowsItsGoneTime() {
        val anchor = activity("bed").copy(title = "In bed by 10:40 PM", ruleId = RuleIds.SLEEP_ANCHOR)
        val plan = listOf(activity("a", ActivityStatus.DONE), anchor)
        val before = todayNextStep(plan, bedtime, 22 * 60)!!
        assertFalse(before.lightsOutPassed)
        val after = todayNextStep(plan, bedtime, 22 * 60 + 58)!!
        assertTrue("past 10:40 PM the button says lights out soon", after.lightsOutPassed)
        // Any other action keeps its own title, whatever the hour.
        val other = todayNextStep(listOf(activity("x")), bedtime, 23 * 60)!!
        assertFalse(other.lightsOutPassed)
    }

    @Test
    fun noPlan_noPrimaryAction() {
        assertNull(todayNextStep(emptyList(), bedtime, noon))
    }

    @Test
    fun nothingResolvedYet_startsTodaysPlanWithTheFirstOpenAction() {
        val step = todayNextStep(listOf(activity("a"), activity("b")), bedtime, noon)

        assertEquals(TodayNextStep("a", "Action a", first = true), step)
    }

    @Test
    fun onceSomethingIsResolved_itIsTheNextOpenAction() {
        val done = todayNextStep(listOf(activity("a", ActivityStatus.DONE), activity("b")), bedtime, noon)
        val skipped = todayNextStep(listOf(activity("a", ActivityStatus.SKIPPED), activity("b")), bedtime, noon)

        assertEquals(TodayNextStep("b", "Action b", first = false), done)
        assertEquals(TodayNextStep("b", "Action b", first = false), skipped)
    }

    @Test
    fun snoozedAndRescheduledActionsAreStillOpen() {
        val step = todayNextStep(listOf(activity("a", ActivityStatus.DONE), activity("b", ActivityStatus.SNOOZED)), bedtime, noon)

        assertEquals(TodayNextStep("b", "Action b", first = false), step)
    }

    @Test
    fun everyActionResolved_leavesNoPrimaryStep_withOrWithoutABedtime() {
        val resolved = listOf(activity("a", ActivityStatus.DONE), activity("b", ActivityStatus.SKIPPED))
        val added = ActionPlaybook.toActivity(tonightWindDown(), MetricType.SLEEP_DEBT, dayEpoch = 20_000)

        // The Tonight card owns the night: no second "Set your usual bedtime" or "Add the wind-down".
        assertNull(todayNextStep(resolved, bedtime = null, nowMinuteOfDay = noon))
        for (minute in listOf(7 * 60, noon, 22 * 60 + 40, 23 * 60 + 30, 1 * 60)) {
            assertNull("at minute $minute", todayNextStep(resolved, bedtime, minute))
        }
        // The wind-down already done or skipped is resolved like any other action.
        assertNull(todayNextStep(resolved + added.copy(status = ActivityStatus.DONE), bedtime, noon))
        assertNull(todayNextStep(resolved + added.copy(status = ActivityStatus.SKIPPED), bedtime, noon))
    }

    // --- The Tonight card's wind-down ---

    @Test
    fun tonightsWindDown_isTheSleepDebtPlaybookAction_theTonightCardOffers() {
        assertEquals(ActionPlaybook.forMetric(MetricType.SLEEP_DEBT).first(), tonightWindDown())
    }

    @Test
    fun tonightsWindDown_isOfferedWithoutATarget_andUntilLightsOutPasses() {
        // No target: no lights-out to pass, so the wind-down is always one tap away.
        for (minute in listOf(7 * 60, noon, 23 * 60 + 30, 1 * 60)) {
            assertTrue("no target, minute $minute", tonightShowsWindDown(plan = null, nowMinuteOfDay = minute, planned = false))
        }
        // With a target, through wind-down time and up to lights-out itself.
        assertTrue(tonightShowsWindDown(bedtime, noon, planned = false))
        assertTrue(tonightShowsWindDown(bedtime, 22 * 60 + 10, planned = false))
        assertTrue(tonightShowsWindDown(bedtime, 22 * 60 + 40, planned = false))
        // Past lights-out nothing offers a time already gone: "keep the wind-down short" says the card.
        assertFalse(tonightShowsWindDown(bedtime, 22 * 60 + 41, planned = false))
        assertFalse(tonightShowsWindDown(bedtime, 23 * 60 + 30, planned = false))
        assertFalse(tonightShowsWindDown(bedtime, 1 * 60, planned = false))
        // Already in the plan, it may still say so ("In today's plan").
        assertTrue(tonightShowsWindDown(bedtime, 23 * 60 + 30, planned = true))
        // The next morning it is this evening's wind-down again.
        assertTrue(tonightShowsWindDown(bedtime, 7 * 60, planned = false))
    }

    @Test
    fun addedWindDown_isAnOpenAction_soTheStepBecomesNextWindDown() {
        val added = ActionPlaybook.toActivity(tonightWindDown(), MetricType.SLEEP_DEBT, dayEpoch = 20_000)
        val step = todayNextStep(listOf(activity("a", ActivityStatus.DONE), added), bedtime, noon)

        assertEquals(TodayNextStep(added.id, tonightWindDown().title, first = false), step)
    }

    @Test
    fun allSkipped_isNotFinished_andHasNoPrimaryStep() {
        val skipped = listOf(activity("a", ActivityStatus.SKIPPED), activity("b", ActivityStatus.SKIPPED))

        assertFalse(planFinished(MainUiState(todayActivities = skipped)))
        assertNull(todayNextStep(skipped, bedtime, noon))
    }

    @Test
    fun planFinished_needsEverythingResolvedAndOneThingDone() {
        assertTrue(planFinished(MainUiState(todayActivities = listOf(activity("a", ActivityStatus.DONE), activity("b", ActivityStatus.SKIPPED)))))
        assertFalse(planFinished(MainUiState(todayActivities = listOf(activity("a", ActivityStatus.DONE), activity("b")))))
        assertFalse(planFinished(MainUiState()))
    }

    // --- The hero ring at large font sizes ---

    @Test
    fun heroRing_growsWithTheFont_neverShrinks() {
        assertEquals(112.dp, heroRingSize(1f))
        assertEquals(112.dp, heroRingSize(0.85f))
        assertEquals(168.dp, heroRingSize(1.5f))
        // Capped: at 200% the ring is 1.8× its default.
        assertEquals(heroRingSize(1.8f), heroRingSize(2f))
    }

    @Test
    fun heroText_sitsBesideTheRingWhenItFits_elseDropsBelow() {
        // A 360dp phone: 360 − 2×24 gutter − 2×24 hero padding = 264dp inside the hero.
        assertTrue(heroRingBeside(264.dp, heroRingSize(1f), 1f))
        assertFalse(heroRingBeside(264.dp, heroRingSize(1.3f), 1.3f))
        assertFalse(heroRingBeside(264.dp, heroRingSize(2f), 2f))
        // A narrow 320dp phone stacks even at the default size.
        assertFalse(heroRingBeside(224.dp, heroRingSize(1f), 1f))
        // The 600dp reading column keeps the ring beside the text at 200%.
        assertTrue(heroRingBeside(552.dp, heroRingSize(2f), 2f))
    }

    // --- Copy ---

    @Test
    fun greeting_followsTheDeviceClock() {
        // Late at night it is still evening, never "Good morning" at 1 AM.
        assertEquals(R.string.checkin_good_evening, greetingRes(0))
        assertEquals(R.string.checkin_good_evening, greetingRes(1 * 60 + 30))
        assertEquals(R.string.checkin_good_evening, greetingRes(4 * 60 + 59))
        assertEquals(R.string.checkin_good_morning, greetingRes(5 * 60))
        assertEquals(R.string.checkin_good_morning, greetingRes(8 * 60))
        assertEquals(R.string.checkin_good_morning, greetingRes(11 * 60 + 59))
        assertEquals(R.string.checkin_good_afternoon, greetingRes(12 * 60))
        assertEquals(R.string.checkin_good_afternoon, greetingRes(16 * 60 + 59))
        assertEquals(R.string.checkin_good_evening, greetingRes(17 * 60))
        assertEquals(R.string.checkin_good_evening, greetingRes(23 * 60 + 59))
    }

    @Test
    fun tonightWithoutATarget_saysWhatToDo() {
        assertEquals(R.string.today_tonight_set_bedtime_body, tonightNoTargetBody(hasLastNight = true))
        assertEquals(R.string.today_tonight_no_sleep_body, tonightNoTargetBody(hasLastNight = false))
    }
}
