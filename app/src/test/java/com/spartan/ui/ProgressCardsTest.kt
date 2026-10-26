package com.spartan.ui

import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.MetricProjection
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.domain.engine.ReviewEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.StreakSummary
import com.spartan.domain.engine.Streaks
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WeeklyReviewSummary
import com.spartan.ui.components.Tone
import com.spartan.ui.screens.ConsistencyLine
import com.spartan.ui.screens.GoalRingFace
import com.spartan.ui.screens.MIN_AXIS_FRACTION
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.RunNotes
import com.spartan.ui.screens.WeekMean
import com.spartan.ui.screens.WeekStat
import com.spartan.ui.screens.WeekStatKind
import com.spartan.ui.screens.calmerThemeBodyRes
import com.spartan.ui.screens.consistencyIsTheLever
import com.spartan.ui.screens.consistencyLines
import com.spartan.ui.screens.consistencyStreak
import com.spartan.ui.screens.goalHeroNote
import com.spartan.ui.screens.goalRingBeside
import com.spartan.ui.screens.goalRingFace
import com.spartan.ui.screens.goalRingFraction
import com.spartan.ui.screens.goalRingSize
import com.spartan.ui.screens.needsAttentionTone
import com.spartan.ui.screens.projectionDomain
import com.spartan.ui.screens.projectionIsFlat
import com.spartan.ui.screens.runNotes
import com.spartan.ui.screens.statTileColumns
import com.spartan.ui.screens.weekMean
import com.spartan.ui.screens.weekStats
import com.spartan.ui.screens.weeklyRingCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The Progress screen's own logic: the goal ring states the real, uncapped session count (and
 * never a big 0), and the line under it never repeats that count; week stats average only the week
 * the subtitle names, and stay two to a row up to about 150% text; the Consistency card frames
 * runs forgivingly and never counts the active days the stat tile already counts; the review's
 * "nothing needs attention" line is never amber; projections lead with the lever at low consistency, and their axis never
 * shrinks to the band, so a small change never fills the chart; and the calmer theme's subtitle on
 * You only promises what still applies when wallpaper colours win.
 */
class ProgressCardsTest {

    private val today = 20_000L

    /** Data through Fri Oct 23; today is Sun Oct 25, so the week is Oct 19–25 (spec §1). */
    private val oct25 = LocalDate.of(2026, 10, 25)

    private fun goal(type: GoalType, target: Double) = Goal(
        id = "g", type = type, targetValue = target, baselineValue = null,
        startEpochDay = today - 7, targetEpochDay = today + 21,
    )

    private fun progress(sessions: Int?, fraction: Double = 1.0) = GoalProgress(
        fraction = fraction, timeFraction = 0.25, onTrack = true, summary = "", sessionsThisWeek = sessions,
    )

    /** "x" = a day with a completed activity, "." = none; oldest → today. */
    private fun days(pattern: String) = pattern.map { it == 'x' }

    /** A daily signal whose readings end Fri Oct 23, one a day. */
    private fun signal(metric: MetricType, vararg values: Double): Signal {
        val dates = values.indices.map { LocalDate.of(2026, 10, 23).minusDays((values.size - 1 - it).toLong()) }
        return Signal(metric, values.last(), values.toList(), null, null, SignalPosition.LEARNING, dates)
    }

    // --- Goal ring ---

    @Test
    fun weeklyRing_statesTheRealCount_evenAboveTheTarget() {
        // The ring itself is full (fraction capped at 1.0); the words still say 7, not 5.
        assertEquals(7 to 5, weeklyRingCount(goal(GoalType.FITNESS, 5.0), progress(7)))
        assertEquals(2 to 3, weeklyRingCount(goal(GoalType.STRESS_RESILIENCE, 3.0), progress(2)))
    }

    @Test
    fun weeklyRing_followsTheEngine_forEveryHabitGoal() {
        val fitness = goal(GoalType.FITNESS, 3.0)
        assertEquals(5 to 3, weeklyRingCount(fitness, GoalEngine.progress(fitness, emptyList(), today, trainingSessionsThisWeek = 5)))
        val calm = goal(GoalType.STRESS_RESILIENCE, 4.0)
        assertEquals(1 to 4, weeklyRingCount(calm, GoalEngine.progress(calm, emptyList(), today, breathworkThisWeek = 1)))
        val recovery = goal(GoalType.RECOVERY, 2.0)
        assertEquals(0 to 2, weeklyRingCount(recovery, GoalEngine.progress(recovery, emptyList(), today)))
    }

    @Test
    fun weeklyRing_isOnlyForGoalsCountedInSessions() {
        val weight = goal(GoalType.WEIGHT_LOSS, 6.0)
        assertNull(weeklyRingCount(weight, GoalEngine.progress(weight, emptyList(), today)))
        assertNull(weeklyRingCount(goal(GoalType.SLEEP_RECOVERY, 5.0), progress(null)))
    }

    @Test
    fun weeklyRing_guardsACorruptTarget() {
        assertNull(weeklyRingCount(goal(GoalType.FITNESS, Double.NaN), progress(2)))
        assertNull(weeklyRingCount(goal(GoalType.FITNESS, Double.POSITIVE_INFINITY), progress(2)))
        // A zero target reads as one session, as the engine scores it.
        assertEquals(2 to 1, weeklyRingCount(goal(GoalType.FITNESS, 0.0), progress(2)))
    }

    @Test
    fun ringFace_countsSessions_andShowsWhatIsToGo_insteadOfABigZero() {
        assertEquals(GoalRingFace.Count(2, 3), goalRingFace(goal(GoalType.FITNESS, 3.0), progress(2, 0.67)))
        assertEquals(GoalRingFace.ToGo(3), goalRingFace(goal(GoalType.FITNESS, 3.0), progress(0, 0.0)))
        // Outcome goals: the engine's real share, uncapped in words; nothing yet is only the mark.
        assertEquals(GoalRingFace.Percent(50), goalRingFace(goal(GoalType.WEIGHT_LOSS, 6.0), progress(null, 0.5)))
        assertEquals(GoalRingFace.Percent(120), goalRingFace(goal(GoalType.WEIGHT_LOSS, 6.0), progress(null, 1.2)))
        assertEquals(GoalRingFace.Mark, goalRingFace(goal(GoalType.WEIGHT_LOSS, 6.0), progress(null, 0.0)))
        assertEquals(GoalRingFace.Mark, goalRingFace(goal(GoalType.WEIGHT_LOSS, 6.0), progress(null, Double.NaN)))
    }

    @Test
    fun ringFraction_isTheEnginesValue_clamped() {
        assertEquals(0.5f, goalRingFraction(progress(null, 0.5)), 0f)
        assertEquals(1f, goalRingFraction(progress(null, 1.2)), 0f)
        assertEquals(0f, goalRingFraction(progress(null, Double.NaN)), 0f)
    }

    @Test
    fun ring_growsWithText_andStacksUnderTheWordsWhenTheyWouldSqueeze() {
        assertEquals(104.dp, goalRingSize(1f))
        assertEquals(208.dp, goalRingSize(2f))
        assertEquals(208.dp, goalRingSize(3f))
        // A 360dp phone leaves the hero 264dp: beside at 100%, stacked at 200%.
        assertTrue(goalRingBeside(264.dp, 1f))
        assertFalse(goalRingBeside(264.dp, 2f))
        assertTrue(goalRingBeside(504.dp, 1.5f))
    }

    // --- Week stats ---

    @Test
    fun weekMean_averagesOnlyTheWeekTheSubtitleNames() {
        // Oct 17–23; the week Oct 19–25 holds the last five readings (81 on Tue Oct 20).
        val recovery = signal(MetricType.RECOVERY_SCORE, 40.0, 40.0, 61.0, 81.0, 59.0, 63.0, 64.0)
        assertEquals(WeekMean(66, 5), weekMean(recovery, oct25))
        // Without dates it falls back to the last seven readings.
        assertEquals(WeekMean(58, 7), weekMean(recovery.copy(dates = emptyList()), oct25))
        // A week with no readings has no mean, rather than reaching back.
        assertNull(weekMean(recovery, oct25.plusDays(14)))
        assertNull(weekMean(null, oct25))
    }

    @Test
    fun weekStats_carryEveryRealValue_andStrengthOnlyWithTheReview() {
        val state = MainUiState(
            consistencyDays7 = 1,
            signals = listOf(
                signal(MetricType.RECOVERY_SCORE, 61.0, 81.0, 59.0, 63.0, 64.0),
                signal(MetricType.SLEEP_PERFORMANCE, 80.0, 82.0, 78.0, 79.0, 77.0),
                signal(MetricType.HRV_RMSSD, 30.0, 31.0, 32.0, 33.0, 33.0),
            ),
        )
        assertEquals(
            listOf(
                WeekStat(WeekStatKind.ACTIVE_DAYS, 1),
                WeekStat(WeekStatKind.AVG_RECOVERY, 66, 5),
                WeekStat(WeekStatKind.AVG_SLEEP_PERFORMANCE, 79, 5),
                WeekStat(WeekStatKind.AVG_HRV, 32, 5),
            ),
            weekStats(state, oct25),
        )
        val withReview = weekStats(state.copy(review = review(strength = 0)), oct25)
        assertEquals(WeekStat(WeekStatKind.STRENGTH_SESSIONS, 0), withReview.last())
        assertEquals(listOf(WeekStat(WeekStatKind.ACTIVE_DAYS, 0)), weekStats(MainUiState(), oct25))
    }

    @Test
    fun statTiles_stayTwoToARow_untilTheTextGenuinelyNeedsOneColumn() {
        val phone = 312.dp // 360dp less the 24dp gutters
        assertEquals(2, statTileColumns(phone, 1f))
        // Android's "Large" font (115%) and the next step up still fit two tiles a row.
        assertEquals(2, statTileColumns(phone, 1.15f))
        assertEquals(2, statTileColumns(phone, 1.3f))
        assertEquals(2, statTileColumns(phone, 1.5f))
        assertEquals(1, statTileColumns(phone, 1.75f))
        assertEquals(1, statTileColumns(phone, 2f))
        assertEquals(2, statTileColumns(552.dp, 2f)) // the 600dp column on a tablet
    }

    // --- Consistency ---

    @Test
    fun consistency_readsTheTwoWeekWindow_soAReturnAfterAGapIsBackOnTrack() {
        // Active eight days ago, then a quiet week, then today: the week alone shows only today.
        val state = MainUiState(
            streakFlags = days(".....x.......x"),
            consistencyFlags = days("......x"),
        )
        val streak = consistencyStreak(state)
        assertTrue(streak.backOnTrack)
        assertEquals(1, streak.longestRun)
        assertFalse(Streaks.summarize(state.consistencyFlags).backOnTrack)
    }

    @Test
    fun consistency_fallsBackToTheWeek_untilTheLongerWindowLoads() {
        val week = days("xx.xx..")
        assertEquals(Streaks.summarize(week), consistencyStreak(MainUiState(consistencyFlags = week)))
        assertEquals(0, consistencyStreak(MainUiState()).longestRun)
    }

    @Test
    fun consistencyLines_frameRunsOnly_backOnTrackLeading() {
        // A comeback after a gap: "Back on track", and a one-day run is not named on top of it.
        val back = StreakSummary(currentRun = 1, longestRun = 1, backOnTrack = true, graceUsed = false)
        assertEquals(listOf(ConsistencyLine.BackOnTrack), consistencyLines(back))
        // A comeback that the grace day kept going: the run and the grace note follow.
        val graced = StreakSummary(currentRun = 3, longestRun = 5, backOnTrack = true, graceUsed = true)
        assertEquals(
            listOf(ConsistencyLine.BackOnTrack, ConsistencyLine.CurrentRun(3), ConsistencyLine.LongestRun(5), ConsistencyLine.GraceUsed),
            consistencyLines(graced),
        )

        // A steady run leads with itself.
        val steady = StreakSummary(currentRun = 3, longestRun = 3, backOnTrack = false, graceUsed = false)
        assertEquals(listOf(ConsistencyLine.CurrentRun(3)), consistencyLines(steady))
        // The first active day: a run has started, never "1 day" and never an invitation to start one.
        assertEquals(listOf(ConsistencyLine.RunStarted), consistencyLines(StreakSummary(1, 1, backOnTrack = false, graceUsed = false)))
        // No run going: an invitation, with the earlier longer run still credited — never a 0 as the headline.
        assertEquals(listOf(ConsistencyLine.StartNext), consistencyLines(StreakSummary(0, 0, false, false)))
        assertEquals(listOf(ConsistencyLine.StartNext, ConsistencyLine.LongestRun(4)), consistencyLines(StreakSummary(0, 4, false, false)))
    }

    @Test
    fun consistencyLines_neverCountTheActiveDays_theStatTileDoes() {
        // Spec §1's week: active on 1 of 7 days, today only. The count is the stat tile's alone.
        val week = consistencyStreak(MainUiState(consistencyDays7 = 1, consistencyFlags = days("......x")))
        val lines = consistencyLines(week)
        assertEquals(listOf(ConsistencyLine.RunStarted), lines)
        assertEquals(1, weekStats(MainUiState(consistencyDays7 = 1), oct25).count { it.kind == WeekStatKind.ACTIVE_DAYS })
        // Every case the streak engine can produce yields at least one line, so the card is never empty.
        for (pattern in listOf(".......", "......x", ".....x.", "xx.xx..", "x.....x", "xxxxxxx")) {
            assertTrue(pattern, consistencyLines(Streaks.summarize(days(pattern))).isNotEmpty())
        }
    }

    @Test
    fun needsAttention_isNeutral_whenTheEngineSaysNothingNeedsIt() {
        val quiet = ReviewEngine().summarize(emptyList(), emptyList(), referenceDate = oct25)
        assertEquals(listOf(Tone.NEUTRAL), quiet.needsAttention.map(::needsAttentionTone))
        // A real flag keeps its caution dot.
        assertEquals(Tone.CAUTION, needsAttentionTone("Pain was reported; avoid progression until resolved."))
    }

    @Test
    fun heroNote_aHabitGoalAddsOnlyItsContext_neverTheCountAgain() {
        val fitness = Goal("g", GoalType.FITNESS, targetValue = 3.0, baselineValue = 62.0, startEpochDay = today - 7, targetEpochDay = today + 21)
        val restingHr = (0..6).map { MetricReading(MetricType.RESTING_HEART_RATE, 60.5, LocalDate.ofEpochDay(today - it)) }

        val withContext = GoalEngine.progress(fitness, restingHr, today, trainingSessionsThisWeek = 2)
        // The engine's summary is unchanged (You still shows it); the context is also exposed alone.
        assertEquals("2 of 3 training sessions this week · resting HR -1.5 bpm since your goal started", withContext.summary)
        assertEquals("resting HR -1.5 bpm since your goal started", withContext.contextNote)
        assertEquals("Resting HR -1.5 bpm since your goal started", goalHeroNote(withContext))

        // Before the first session and without readings: no line at all, never "0 of 3" under the ring.
        val zero = GoalEngine.progress(fitness, emptyList(), today)
        assertEquals("0 of 3 training sessions this week", zero.summary)
        assertNull(zero.contextNote)
        assertNull(goalHeroNote(zero))

        // An outcome goal has no ring count, so it keeps the engine's summary.
        val weight = Goal("w", GoalType.WEIGHT_LOSS, targetValue = 6.0, baselineValue = 80.0, startEpochDay = today - 7, targetEpochDay = today + 21)
        val outcome = GoalEngine.progress(weight, listOf(MetricReading(MetricType.WEIGHT, 79.0, LocalDate.ofEpochDay(today))), today)
        assertNull(outcome.contextNote)
        assertEquals(outcome.summary, goalHeroNote(outcome))
    }

    @Test
    fun runNotes_nameRunsFromTwoDays_soOneDayNeverReadsAsFailure() {
        assertEquals(RunNotes(null, null), runNotes(StreakSummary(1, 1, backOnTrack = true, graceUsed = false)))
        assertEquals(RunNotes(3, null), runNotes(StreakSummary(3, 3, backOnTrack = false, graceUsed = false)))
        assertEquals(RunNotes(null, 4), runNotes(StreakSummary(1, 4, backOnTrack = true, graceUsed = false)))
        assertEquals(RunNotes(2, 4), runNotes(StreakSummary(2, 4, backOnTrack = false, graceUsed = true)))
    }

    // --- Projections ---

    @Test
    fun lowConsistency_makesConsistencyTheLever() {
        assertTrue(consistencyIsTheLever(0))
        assertTrue(consistencyIsTheLever(1))
        assertTrue(consistencyIsTheLever(2))
        assertFalse(consistencyIsTheLever(3))
        assertFalse(consistencyIsTheLever(7))
    }

    /** A signal carrying only the user's usual range for [metric]. */
    private fun usual(metric: MetricType, latest: Double, low: Double, high: Double) =
        Signal(metric, latest, listOf(latest), low, high, SignalPosition.WITHIN)

    /** Spec §1's usual ranges for HRV and Recovery; resting HR's is illustrative. */
    private val usualRanges = mapOf(
        MetricType.HRV_RMSSD to usual(MetricType.HRV_RMSSD, 33.0, 28.0, 38.0),
        MetricType.RESTING_HEART_RATE to usual(MetricType.RESTING_HEART_RATE, 66.0, 62.0, 70.0),
        MetricType.RECOVERY_SCORE to usual(MetricType.RECOVERY_SCORE, 64.0, 49.0, 78.0),
    )

    /** The share of its chart's value axis that a projection's drawn range (now to week 8) takes up. */
    private fun bandShare(projection: MetricProjection, usual: Signal?): Double {
        val domain = projectionDomain(projection, usual)
        val drawn = projection.points.flatMap { listOf(it.low, it.high) } + projection.currentValue
        // Everything drawn sits inside the axis.
        drawn.forEach { assertTrue("${projection.metric}: $it outside $domain", it in domain) }
        return (drawn.max() - drawn.min()) / (domain.endInclusive - domain.start)
    }

    @Test
    fun bandAxis_neverStretchesASmallHrvChangeToFillTheChart() {
        // HRV 33 ms at 1 active day a week: about a 1–2 ms range by week 8.
        val hrv = ProjectionEngine().project(null, 33.0, null, 1).single()
        val domain = projectionDomain(hrv, usual = null)
        assertTrue("domain $domain", domain.endInclusive - domain.start >= 33.0 * MIN_AXIS_FRACTION - 1e-9)
        val share = bandShare(hrv, null)
        assertTrue("share $share", share < 0.3)
    }

    @Test
    fun bandAxis_neverShrinksToTheBand_atThreeAndFiveActiveDays() {
        // The old axis was the band plus 20% headroom: about 83% of the chart from 3 active days.
        for (days in listOf(3, 5)) {
            val projections = ProjectionEngine().project(66.0, 33.0, 64, days)
            assertEquals(
                setOf(MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE, MetricType.RECOVERY_SCORE),
                projections.map { it.metric }.toSet(),
            )
            projections.forEach { p ->
                assertFalse(projectionIsFlat(p))
                val withUsual = bandShare(p, usualRanges.getValue(p.metric))
                val withoutUsual = bandShare(p, null)
                assertTrue("${p.metric} at $days days, usual range known: $withUsual", withUsual <= 0.55)
                assertTrue("${p.metric} at $days days, no usual range yet: $withoutUsual", withoutUsual <= 0.55)
            }
        }
    }

    @Test
    fun bandAxis_spansAtLeastTheUsualRange_ofTheSameMetricOnly() {
        // HRV 33 ms at 5 active days: about 33–38 ms by week 8, inside a usual range of 28–38 ms.
        val hrv = ProjectionEngine().project(null, 33.0, null, 5).single()
        val usualHrv = usualRanges.getValue(MetricType.HRV_RMSSD)
        val domain = projectionDomain(hrv, usualHrv)
        assertTrue("domain $domain", domain.endInclusive - domain.start >= 10.0 - 1e-9)
        // Another metric's range never sizes this axis.
        val wide = usual(MetricType.RECOVERY_SCORE, 64.0, 10.0, 90.0)
        assertEquals(projectionDomain(hrv, null), projectionDomain(hrv, wide))
    }

    @Test
    fun noActiveDays_projectsFlat_soThereIsNoBandToDraw() {
        ProjectionEngine().project(66.0, 33.0, 64, 0).forEach { assertTrue(projectionIsFlat(it)) }
        ProjectionEngine().project(66.0, 33.0, 64, 3).forEach { assertFalse(projectionIsFlat(it)) }
    }

    // --- You → Display ---

    @Test
    fun calmerThemeSubtitle_dropsTheColourPromise_whenWallpaperColoursWin() {
        assertEquals(R.string.more_calmer_theme_body_wallpaper, calmerThemeBodyRes(wallpaperAvailable = true, wallpaperColours = true))
        assertEquals(R.string.more_calmer_theme_body, calmerThemeBodyRes(wallpaperAvailable = true, wallpaperColours = false))
        // Below Android 12 a stored "on" does nothing, so the calmer colours still apply.
        assertEquals(R.string.more_calmer_theme_body, calmerThemeBodyRes(wallpaperAvailable = false, wallpaperColours = true))
        assertEquals(R.string.more_calmer_theme_body, calmerThemeBodyRes(wallpaperAvailable = false, wallpaperColours = false))
    }

    private fun review(strength: Int) = WeeklyReviewSummary(
        adherencePercent = 0, zone2Minutes = 0, strengthSessions = strength,
        latestWeight = null, sevenDayWeightAverage = null, latestRhr = null, sevenDayRhrAverage = null,
        latestBp = null, latestFastingGlucose = null,
        improved = emptyList(), needsAttention = emptyList(), nextWeekFocus = "",
    )
}
