package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.BedtimePlan
import com.spartan.domain.engine.MorningVerdict
import com.spartan.domain.engine.PendingReason
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.SuggestedAction
import com.spartan.domain.engine.WhyToday
import com.spartan.domain.engine.WhyTodayEngine
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.TimeOfDay
import com.spartan.ui.components.OneShot
import com.spartan.ui.components.OneShotKey
import com.spartan.ui.screens.CheckInScreen
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.TODAY_HERO_TAG
import com.spartan.ui.screens.TODAY_PRIMARY_TAG
import com.spartan.ui.theme.SpartanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Today rendered on the JVM with the real screenshot data (spec §1: Recovery 64%, Yellow; 7h 34m
 * of about 9h 50m needed; data two days old) — never the AI images' values. One hero and one
 * primary state (a finished holding plan is never also "Day complete", nor "Plan pending"), the
 * zone as word + number with the ring's one sweep (and no ring at all while pending), "Sample data"
 * once on screen, a primary action only while an action is open, a bedtime card that always acts
 * and owns the night (so nothing is offered twice), a "Why today?" that never repeats the hero,
 * done rows without strikethrough, and the short in-context line in place of the full disclaimer. A plain Application keeps app
 * start-up out of UI tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class TodayScreenTest {

    private val composeRule = createComposeRule()

    /** As in LottieMomentsTest: release-variant unit tests have no ui-test-manifest activity entry. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Context>()
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = app.packageName
                },
            )
        }
    }).around(composeRule)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now().toEpochDay()
    private val windDown: SuggestedAction = ActionPlaybook.forMetric(MetricType.SLEEP_DEBT).first()

    private fun activity(id: String, title: String, status: ActivityStatus = ActivityStatus.PLANNED) = DailyActivity(
        id = id,
        title = title,
        // Breathwork opens no exercise debrief, so checking a row here stays a plain check.
        category = ActivityCategory.BREATHWORK,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = "A calm few minutes.",
        estimatedMinutes = 10,
        intensity = Intensity.EASY,
        bestTimeOfDay = TimeOfDay.ANYTIME,
        status = status,
        ruleId = "rule-$id",
    )

    /** Recovery 64%, Yellow zone, from today's completed cycle. */
    private fun ready(
        revealPlayedForDay: Long? = null,
        activities: List<DailyActivity> = emptyList(),
        previousRecoveryScore: Int? = null,
        strainTarget: StrainZone? = null,
    ) = MainUiState(
        verdict = MorningVerdict.Ready(
            cycleEpochDay = today,
            recoveryScore = 64,
            zone = RecoveryZone.YELLOW,
            band = ReadinessBand.BALANCED,
            previousRecoveryScore = previousRecoveryScore,
            strainTarget = strainTarget,
        ),
        whyToday = WhyToday("Yellow · 64%", listOf("Sleep was 7h 34m of about 9h 50m needed."), "So today keeps things steady."),
        dataThroughEpochDay = today,
        sleptHours = 7 + 34 / 60.0,
        sleepNeedHours = 9 + 50 / 60.0,
        morningRevealPlayedForDay = revealPlayedForDay,
        todayActivities = activities,
        whoopIsMock = false,
        whoopConnected = true,
    )

    /** Tonight comfortably ahead of now (wind-down in 2h 15m), so nothing ticks past lights-out mid-test. */
    private fun tonightAhead(): BedtimePlan {
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        return BedtimePlan(
            windDownMinute = (now + 135) % (24 * 60),
            lightsOutMinute = (now + 180) % (24 * 60),
            sleepHours = 9 + 50 / 60.0,
            debtHours = 44 / 60.0,
            needHours = 9 + 50 / 60.0,
        )
    }

    /** A minute of day as the screen formats it, on the device's 12/24-hour clock. */
    private fun clockLabel(minute: Int): String {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return LocalTime.of(minute / 60 % 24, minute % 60).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    private fun show(
        state: MainUiState,
        bedtime: BedtimePlan? = null,
        onRevealPlayed: (Long) -> Unit = {},
        onSavePlanConstraints: (PlanConstraints) -> Unit = {},
    ) {
        composeRule.setContent {
            SpartanTheme {
                CheckInScreen(
                    state = state,
                    onComplete = {},
                    onUncomplete = {},
                    onSnooze = {},
                    onSkip = {},
                    onSchedule = {},
                    onManageConnections = {},
                    bedtime = bedtime,
                    onMorningRevealPlayed = onRevealPlayed,
                    onSavePlanConstraints = onSavePlanConstraints,
                )
            }
        }
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** The style a Text actually laid out with — decoration and colour included. */
    private fun laidOutStyle(text: String): TextStyle {
        val node = composeRule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        return results.first().layoutInput.style
    }

    /** The opened action sheet's title is a heading; plan row titles are not. */
    private fun sheetHeading(title: String) = isHeading() and hasText(title)

    // --- One hero, one primary state ---

    /** A holding plan, done and skipped, under a [reason] verdict — the engine's own "Why today?" copy. */
    private fun finishedHolding(reason: PendingReason, lastCycleEpochDay: Long?): MainUiState {
        val verdict = MorningVerdict.Pending(reason, lastCycleEpochDay = lastCycleEpochDay)
        return MainUiState(
            verdict = verdict,
            whyToday = WhyTodayEngine.explain(null, verdict, null, emptyList(), null, null),
            dataThroughEpochDay = lastCycleEpochDay,
            todayActivities = listOf(
                activity("a", "Box breathing", ActivityStatus.DONE),
                activity("b", "Easy walk", ActivityStatus.SKIPPED),
            ),
            whoopIsMock = false,
            whoopConnected = true,
        )
    }

    @Test
    fun finishedHoldingPlan_stale_isOneHero_withADataOnlyNote_neverPlanPending() {
        val state = finishedHolding(PendingReason.STALE, lastCycleEpochDay = today - 2)
        // The engine's summary leads with "Plan pending" — exactly what must not sit under "complete".
        assertTrue(state.whyToday!!.summary.startsWith("Plan pending"))
        show(state)

        val newest = LocalDate.ofEpochDay(today - 2).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        composeRule.onAllNodes(hasTestTag(TODAY_HERO_TAG)).assertCountEquals(1)
        composeRule.onNodeWithTag(TODAY_HERO_TAG)
            .assert(hasText(string(R.string.today_holding_plan_complete)))
            .assert(hasText(string(R.string.today_holding_note_stale_date, newest)))
            .assert(!hasText("Plan pending", substring = true, ignoreCase = true))
            // The note already names the newest day; the freshness line does not repeat it.
            .assert(!hasText(string(R.string.today_fresh_through_date, newest)))
        // The pending title and the "here's a holding plan" offer give way to the one finished state.
        composeRule.onAllNodesWithText(string(R.string.today_pending_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(state.whyToday!!.conclusion).assertCountEquals(0)
        // Nothing is left open: no primary button; the Tonight card carries the one bedtime action.
        composeRule.onAllNodes(hasTestTag(TODAY_PRIMARY_TAG)).assertCountEquals(0)

        assertNoDayCompleteCopy()
        // Down the whole list: still no second "complete" card, and still just the one hero.
        scrollTo(hasText(string(R.string.today_in_context)))
        assertNoDayCompleteCopy()
        assertTrue(composeRule.onAllNodes(hasTestTag(TODAY_HERO_TAG)).fetchSemanticsNodes().size <= 1)
        assertTrue(composeRule.onAllNodesWithText(string(R.string.today_holding_plan_complete), useUnmergedTree = true).fetchSemanticsNodes().size <= 1)
    }

    @Test
    fun finishedHoldingPlan_cycleStillScoring_saysSo_once() {
        show(finishedHolding(PendingReason.NO_COMPLETED_CYCLE, lastCycleEpochDay = today - 1))

        composeRule.onNodeWithTag(TODAY_HERO_TAG)
            .assert(hasText(string(R.string.today_holding_plan_complete)))
            .assert(hasText(string(R.string.today_holding_note_cycle)))
            .assert(!hasText("Plan pending", substring = true, ignoreCase = true))
            // Said once: the freshness line's "still in progress" would only repeat the note.
            .assert(!hasText(string(R.string.today_fresh_cycle_in_progress)))
    }

    private fun assertNoDayCompleteCopy() {
        composeRule.onAllNodesWithText("Recovery earned", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Day complete", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun finishedReadyPlan_keepsTheRingAndZone_underOneCompleteHeadline() {
        show(ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing", ActivityStatus.DONE))))

        composeRule.onNodeWithTag(TODAY_HERO_TAG)
            .assert(hasText(string(R.string.today_plan_complete)))
            .assert(hasText("Yellow · 64%"))
        // The verdict's own headline gives way; there is no second card saying the day is done.
        composeRule.onAllNodesWithText(string(R.string.today_verdict_balanced)).assertCountEquals(0)
        assertNoDayCompleteCopy()
    }

    // --- The Pending hero ---

    @Test
    fun unfinishedPendingHero_hasNoRingAndNoNumber() {
        show(
            MainUiState(
                verdict = MorningVerdict.Pending(PendingReason.NO_COMPLETED_CYCLE, lastCycleEpochDay = today - 1),
                whyToday = WhyToday(
                    summary = "Today's WHOOP cycle is still being scored.",
                    reasons = listOf("A WHOOP cycle runs from when you fall asleep to when you next fall asleep, so recovery is scored after you wake."),
                    conclusion = "Here's a gentle holding plan until it arrives.",
                ),
                dataThroughEpochDay = today - 1,
                todayActivities = listOf(activity("a", "Box breathing", ActivityStatus.DONE), activity("b", "Easy walk")),
                whoopIsMock = false,
                whoopConnected = true,
            ),
        )

        val hero = composeRule.onNodeWithTag(TODAY_HERO_TAG)
        hero.assert(hasText(string(R.string.today_pending_title)))
        // No ring — the plan rows' checks carry OneShotKey too, so only the hero's descendants count.
        val inHero = hasAnyAncestor(hasTestTag(TODAY_HERO_TAG))
        composeRule.onAllNodes(inHero and SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true).assertCountEquals(0)
        // No number: nothing in the hero reads as a percentage, on screen or to TalkBack.
        composeRule.onAllNodes(inHero and hasText("%", substring = true), useUnmergedTree = true).assertCountEquals(0)
        hero.assert(!hasText("%", substring = true) and !hasContentDescription("percent", substring = true))
    }

    // --- The Ready hero ---

    @Test
    fun readyVerdict_saysTheZoneWordAndNumber_andSweepsTheRingOnce() {
        val played = mutableListOf<Long>()
        show(ready(revealPlayedForDay = null), onRevealPlayed = { played += it })

        composeRule.onNodeWithTag(TODAY_HERO_TAG)
            .assert(hasText("Yellow · 64%"))
            .assert(hasText(string(R.string.today_verdict_balanced)))
            // One TalkBack stop: headline, zone word and number, and where the numbers come from.
            .assert(hasContentDescription("A steady day. Recovery Yellow, 64 percent", substring = true))
            .assert(hasContentDescription(string(R.string.today_fresh_through_today), substring = true))
            .assert(hasClickAction())
        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(OneShotKey, OneShot.PLAY_ONCE))
        assertEquals(listOf(today), played)
    }

    @Test
    fun readyHero_carriesTheComparisonAndTheStrainTarget_butNotTonightsBedtime() {
        val plan = tonightAhead()
        val strain = StrainZone.MODERATE
        show(ready(revealPlayedForDay = today, previousRecoveryScore = 81, strainTarget = strain), bedtime = plan)

        val expected = listOf(
            string(R.string.today_verdict_balanced),
            string(R.string.today_zone_a11y, "Yellow", 64),
            string(R.string.today_vs_down, 81),
            string(R.string.today_strain_range_a11y, strain.low, strain.high, strain.label),
            string(R.string.today_fresh_through_today),
        ).joinToString(". ")
        val lightsOut = clockLabel(plan.lightsOutMinute)
        composeRule.onNodeWithTag(TODAY_HERO_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected)))
            .assert(hasText(string(R.string.today_strain_range, strain.low, strain.high, strain.label)))
            // Lights-out lives in the Tonight card, not on the hero.
            .assert(!hasText(lightsOut, substring = true))
        scrollTo(hasText(string(R.string.tonight_title, lightsOut)))
    }

    @Test
    fun sampleData_showsOnceAtTheTop_andTalkBackStillHearsItWithTheVerdict() {
        show(ready(revealPlayedForDay = today).copy(whoopIsMock = true))

        composeRule.onAllNodesWithText(string(R.string.checkin_sample_data), useUnmergedTree = true).assertCountEquals(1)
        val inHero = hasAnyAncestor(hasTestTag(TODAY_HERO_TAG))
        composeRule.onAllNodes(inHero and hasText(string(R.string.today_fresh_sample), substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag(TODAY_HERO_TAG)
            .assert(hasText(string(R.string.today_fresh_through_today)))
            .assert(hasContentDescription(string(R.string.today_fresh_sample), substring = true))
    }

    @Test
    fun readyVerdict_alreadyRevealedToday_showsTheFinalRing() {
        val played = mutableListOf<Long>()
        show(ready(revealPlayedForDay = today), onRevealPlayed = { played += it })

        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(OneShotKey, OneShot.SHOW_FINAL))
        assertEquals(emptyList<Long>(), played)
    }

    @Test
    fun stackedHero_atLargeText_centresTheRingOnTheCard() {
        var fontScale by mutableFloatStateOf(1.3f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpartanTheme {
                    CheckInScreen(ready(revealPlayedForDay = today, previousRecoveryScore = 81), {}, {}, {}, {}, {}, {})
                }
            }
        }
        val oneDp = composeRule.density.density
        val inHero = hasAnyAncestor(hasTestTag(TODAY_HERO_TAG))

        for (scale in listOf(1.3f, 2f)) {
            fontScale = scale
            composeRule.waitForIdle()
            val hero = composeRule.onNodeWithTag(TODAY_HERO_TAG).fetchSemanticsNode().boundsInRoot
            val ring = composeRule.onNode(inHero and SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val zone = composeRule.onNode(inHero and hasText("Yellow · 64%"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // Stacked at this size on a 411dp phone: the verdict text sits under the ring, not beside it.
            assertTrue("text under the ring at $scale×", zone.top >= ring.bottom)
            assertEquals("ring centred on the hero at $scale×", hero.center.x, ring.center.x, oneDp)
        }
    }

    // --- The one primary action ---

    @Test
    fun primaryAction_opensTheFirstActionsSheet_thenTheNextActionsSheet() {
        var state by mutableStateOf(ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing"), activity("b", "Easy walk"))))
        composeRule.setContent {
            SpartanTheme {
                CheckInScreen(
                    state = state,
                    onComplete = { id ->
                        state = state.copy(todayActivities = state.todayActivities.map { if (it.id == id) it.copy(status = ActivityStatus.DONE) else it })
                    },
                    onUncomplete = {},
                    onSnooze = {},
                    onSkip = {},
                    onSchedule = {},
                    onManageConnections = {},
                )
            }
        }

        composeRule.onAllNodes(sheetHeading("Box breathing"), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag(TODAY_PRIMARY_TAG)
            .assert(hasText(string(R.string.today_primary_start)))
            .assert(hasContentDescription(string(R.string.today_primary_start_a11y, "Box breathing")))
            .performClick()
        // The first action's own sheet, not just any sheet.
        composeRule.onNode(sheetHeading("Box breathing"), useUnmergedTree = true).assertExists()
        composeRule.onAllNodes(sheetHeading("Easy walk"), useUnmergedTree = true).assertCountEquals(0)

        // Done from the sheet: it closes, and the same button names — and opens — the next action.
        composeRule.onNodeWithText(string(R.string.checkin_mark_done)).performClick()
        composeRule.onAllNodes(sheetHeading("Box breathing"), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.today_primary_start)).assertCountEquals(0)
        composeRule.onNodeWithTag(TODAY_PRIMARY_TAG)
            .assert(hasText(string(R.string.today_primary_next, "Easy walk")))
            .assert(hasContentDescription(string(R.string.today_primary_next_a11y, "Easy walk")))
            .performClick()
        composeRule.onNode(sheetHeading("Easy walk"), useUnmergedTree = true).assertExists()
        composeRule.onAllNodes(sheetHeading("Box breathing"), useUnmergedTree = true).assertCountEquals(0)
    }

    /** The Tonight card's "+ Add" for tonight's wind-down. */
    private val addWindDown = hasContentDescription(string(R.string.insights_add_a11y, windDown.title)) and hasClickAction()

    @Test
    fun finishedPlan_withoutABedtime_hasNoPrimary_andOneSetBedtimeButton_inTheTonightCard() {
        show(ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing", ActivityStatus.DONE))), bedtime = null)

        // Nothing is left open, so there is no primary button to repeat the Tonight card's action.
        composeRule.onAllNodes(hasTestTag(TODAY_PRIMARY_TAG)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.wizard_plan_settings_save)).assertCountEquals(0)
        scrollTo(addWindDown)
        composeRule.onAllNodesWithText(string(R.string.today_set_bedtime)).assertCountEquals(1)
        composeRule.onNode(hasText(string(R.string.today_set_bedtime)) and hasClickAction())
            .assert(hasContentDescription(string(R.string.today_set_bedtime_a11y)))
            .performClick()
        composeRule.onNodeWithText(string(R.string.wizard_plan_settings_save)).assertExists()
    }

    @Test
    fun finishedPlan_withABedtime_hasNoPrimary_theTonightCardAddsTheWindDown_thenItIsTheNextAction() {
        val added = mutableListOf<Pair<SuggestedAction, MetricType>>()
        var state by mutableStateOf(ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing", ActivityStatus.DONE))))
        composeRule.setContent {
            SpartanTheme {
                CheckInScreen(
                    state, {}, {}, {}, {}, {}, {},
                    bedtime = tonightAhead(),
                    onAddAction = { action, metric ->
                        added += action to metric
                        state = state.copy(todayActivities = state.todayActivities + ActionPlaybook.toActivity(action, metric, today))
                    },
                )
            }
        }

        composeRule.onAllNodes(hasTestTag(TODAY_PRIMARY_TAG)).assertCountEquals(0)
        scrollTo(addWindDown)
        composeRule.onAllNodes(addWindDown).assertCountEquals(1)
        composeRule.onNode(addWindDown).performClick()
        // Filed under sleep debt.
        assertEquals(listOf(windDown to MetricType.SLEEP_DEBT), added)
        // Now it is an open action: the card says it is planned, and the primary button opens it like any other.
        composeRule.onAllNodes(addWindDown).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.insights_in_plan)).assertExists()
        scrollTo(hasTestTag(TODAY_PRIMARY_TAG))
        composeRule.onNodeWithTag(TODAY_PRIMARY_TAG).assert(hasText(string(R.string.today_primary_next, windDown.title)))
    }

    @Test
    fun nothingLeftToDo_hasNoPrimaryButton_andLast7DaysIsAFactOnly() {
        val plannedWindDown = ActionPlaybook.toActivity(windDown, MetricType.SLEEP_DEBT, today).copy(status = ActivityStatus.DONE)
        val plan = tonightAhead()
        show(
            ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing", ActivityStatus.DONE), plannedWindDown))
                .copy(consistencyFlags = listOf(false, false, false, false, false, false, true)),
            bedtime = plan,
        )

        composeRule.onNodeWithTag(TODAY_HERO_TAG).assert(hasText(string(R.string.today_plan_complete)))
        composeRule.onAllNodes(hasTestTag(TODAY_PRIMARY_TAG)).assertCountEquals(0)
        // The Tonight card states the night instead.
        scrollTo(hasText(string(R.string.tonight_title, clockLabel(plan.lightsOutMinute))))
        // Last 7 days keeps its fact and repeats no next step: one TalkBack stop that reads the title
        // and the facts line once each — no description overriding them, and the dots stay silent.
        scrollTo(hasText(string(R.string.checkin_consistency_label)))
        composeRule.onNode(hasText(string(R.string.checkin_consistency_label)))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Text,
                    listOf(AnnotatedString(string(R.string.checkin_consistency_label)), AnnotatedString(string(R.string.checkin_consistency_a11y, 1))),
                ),
            )
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    // --- Tonight ---

    @Test
    fun tonightWithoutATarget_setsTheUsualBedtime_andSavesThroughToday() {
        val saved = mutableListOf<PlanConstraints>()
        show(
            ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing"))).copy(sleptHours = null, sleepNeedHours = null),
            bedtime = null,
            onSavePlanConstraints = { saved += it },
        )

        scrollTo(hasText(string(R.string.today_tonight_no_target_title)))
        composeRule.onNodeWithText(string(R.string.tonight_eyebrow)).assertExists()
        // No sleep data either: it says what to do, not only what is missing.
        composeRule.onNodeWithText(string(R.string.today_tonight_no_sleep_body)).assertExists()
        // Without a target the wind-down is still one tap away, below the bedtime button.
        scrollTo(addWindDown)
        val setBedtime = composeRule.onNode(hasText(string(R.string.today_set_bedtime)) and hasClickAction())
        val windDownTop = composeRule.onNode(hasText(windDown.title), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue(windDownTop > setBedtime.fetchSemanticsNode().boundsInRoot.bottom)
        // The primary step only ever opens an action, so this is the card's own button.
        setBedtime
            .assert(hasContentDescription(string(R.string.today_set_bedtime_a11y)))
            .performClick()

        composeRule.onNodeWithText(string(R.string.wizard_plan_settings_save)).performClick()
        assertEquals(1, saved.size)
    }

    @Test
    fun pastLightsOut_theTonightCardOffersNoWindDown_unlessItIsAlreadyPlanned() {
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        // Lights-out was half an hour ago: "Lights out soon", and no step for a time already gone.
        val late = BedtimePlan(
            windDownMinute = Math.floorMod(now - 75, 24 * 60),
            lightsOutMinute = Math.floorMod(now - 30, 24 * 60),
            sleepHours = 9 + 50 / 60.0,
            debtHours = 44 / 60.0,
            needHours = 9 + 50 / 60.0,
        )
        val base = ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing", ActivityStatus.DONE)))
        var state by mutableStateOf(base)
        composeRule.setContent {
            SpartanTheme { CheckInScreen(state, {}, {}, {}, {}, {}, {}, bedtime = late) }
        }

        scrollTo(hasText(string(R.string.tonight_title_late)))
        composeRule.onNodeWithText(string(R.string.tonight_body_late, clockLabel(late.lightsOutMinute))).assertExists()
        composeRule.onAllNodes(addWindDown).assertCountEquals(0)
        composeRule.onAllNodesWithText(windDown.title).assertCountEquals(0)

        // Already in the plan, the card may still say so — but never offers to add it.
        state = base.copy(todayActivities = base.todayActivities + ActionPlaybook.toActivity(windDown, MetricType.SLEEP_DEBT, today))
        scrollTo(hasText(string(R.string.insights_in_plan)))
        composeRule.onAllNodes(addWindDown).assertCountEquals(0)
    }

    // --- Why today? ---

    /** A Ready day up from 58% yesterday, explained by the real engine (its summary repeats the hero). */
    private fun readyUpFromYesterday(): MainUiState {
        val state = ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing")), previousRecoveryScore = 58)
        val why = WhyTodayEngine.explain(null, state.verdict!!, null, emptyList(), state.sleptHours, state.sleepNeedHours)
        return state.copy(whyToday = why)
    }

    @Test
    fun whyToday_collapsed_neverRepeatsTheHerosVerdictLine() {
        val state = readyUpFromYesterday()
        assertTrue(state.whyToday!!.summary.contains("up from 58% yesterday"))
        show(state)

        composeRule.onNodeWithTag(TODAY_HERO_TAG).assert(hasText(string(R.string.today_vs_up, 58)))
        scrollTo(hasText(string(R.string.today_why_title)))
        composeRule.onNode(hasText(string(R.string.today_why_title)) and hasClickAction())
            .assert(hasText(string(R.string.today_why_subtitle)))
            .assert(!hasText("Yellow · 64%", substring = true))
            .assert(!hasText("from 58% yesterday", substring = true, ignoreCase = true))
        // The comparison is said once on the whole screen: in the hero.
        composeRule.onAllNodes(hasText("from 58% yesterday", substring = true, ignoreCase = true) and !hasTestTag(TODAY_HERO_TAG))
            .assertCountEquals(0)
        // The reasons are still one tap away.
        composeRule.onNode(hasText(string(R.string.today_why_title)) and hasClickAction()).performClick()
        composeRule.onNodeWithText(state.whyToday!!.reasons.first()).assertExists()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun whyToday_twoPaneHeader_neverRepeatsTheHerosVerdictLine() {
        val state = readyUpFromYesterday()
        show(state)

        composeRule.onNodeWithTag(TODAY_HERO_TAG).assert(hasText(string(R.string.today_vs_up, 58)))
        composeRule.onNodeWithText(string(R.string.today_why_subtitle)).assertExists()
        composeRule.onNodeWithText(state.whyToday!!.reasons.first()).assertExists()
        composeRule.onAllNodes(hasText("from 58% yesterday", substring = true, ignoreCase = true) and !hasTestTag(TODAY_HERO_TAG))
            .assertCountEquals(0)
    }

    // --- Action rows ---

    @Test
    fun doneRow_hasNoStrikethrough_andKeepsItsFullContrastTitle() {
        lateinit var colors: ColorScheme
        composeRule.setContent {
            SpartanTheme {
                colors = MaterialTheme.colorScheme
                CheckInScreen(
                    ready(
                        revealPlayedForDay = today,
                        activities = listOf(
                            activity("a", "Box breathing", ActivityStatus.DONE),
                            activity("b", "Easy walk", ActivityStatus.SKIPPED),
                            activity("c", "Evening stretch"),
                        ),
                    ),
                    {}, {}, {}, {}, {}, {},
                )
            }
        }

        val done = laidOutStyle("Box breathing")
        assertNotEquals(TextDecoration.LineThrough, done.textDecoration)
        assertEquals(colors.onSurface, done.color)
        // A skipped row is quieter, but never struck through either.
        val skipped = laidOutStyle("Easy walk")
        assertNotEquals(TextDecoration.LineThrough, skipped.textDecoration)
        assertEquals(colors.onSurfaceVariant, skipped.color)
        composeRule.onNodeWithText(string(R.string.checkin_skipped_today), useUnmergedTree = true).assertExists()
        // Already done on a visit: the filled check shows its final frame, no replayed celebration.
        composeRule.onAllNodes(SemanticsMatcher.expectValue(OneShotKey, OneShot.SHOW_FINAL), useUnmergedTree = true).assertCountEquals(2)
    }

    // --- The in-context line ---

    @Test
    fun theListEndsWithTheInContextLine_notTheFullDisclaimer() {
        show(ready(revealPlayedForDay = today, activities = listOf(activity("a", "Box breathing"))))

        scrollTo(hasText(string(R.string.today_in_context)))
        composeRule.onNodeWithText(
            "Wellness and fitness guidance, not medical advice. Stop and seek care for pain, illness, or unusual symptoms.",
        ).assertExists()
        composeRule.onAllNodesWithText(string(R.string.wellness_disclaimer)).assertCountEquals(0)
    }
}
