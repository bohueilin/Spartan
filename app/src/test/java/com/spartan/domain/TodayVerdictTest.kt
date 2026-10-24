package com.spartan.domain

import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.engine.MorningVerdict
import com.spartan.domain.engine.PendingReason
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.VerdictEngine
import com.spartan.domain.engine.VerdictHeadline
import com.spartan.domain.engine.WhyToday
import com.spartan.domain.engine.WhyTodayEngine
import com.spartan.domain.engine.formatHoursMinutes
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.usecase.DailyPlanSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The morning verdict and its "Why today?" card: WHOOP's colour bands and strain zones, a verdict
 * only from today's completed cycle (Pending otherwise — never fabricated), and copy that reads
 * every number against the user's own context without diagnosing or claiming a cause.
 */
class TodayVerdictTest {
    private val day = 20_000L
    private val engine = CoachingEngine()
    private val safety = SafetyEngine()

    private val cycleRule =
        "A WHOOP cycle runs from when you fall asleep to when you next fall asleep, so recovery is scored after you wake."

    private fun signal(metric: MetricType, position: SignalPosition) =
        Signal(metric, 60.0, listOf(60.0), 55.0, 65.0, position)

    private fun ready(score: Int, previous: Int? = null, target: StrainZone? = null) = MorningVerdict.Ready(
        cycleEpochDay = day, recoveryScore = score, zone = RecoveryZone.of(score),
        band = ReadinessBand.fromRecovery(score), previousRecoveryScore = previous, strainTarget = target,
    )

    private fun planWith(target: StrainZone?) =
        DailyPlan(dateEpochDay = day, headline = "h", band = ReadinessBand.BALANCED, activities = emptyList(), strainTarget = target)

    private fun explainReady(
        readiness: ReadinessSnapshot? = null,
        signals: List<Signal> = emptyList(),
        slept: Double? = null,
        need: Double? = null,
        plan: DailyPlan? = null,
        verdict: MorningVerdict = ready(72),
    ): WhyToday = WhyTodayEngine.explain(readiness, verdict, plan, signals, slept, need)

    private fun assertSafe(why: WhyToday) {
        (listOf(why.summary, why.conclusion) + why.reasons).forEach {
            assertTrue("unsafe: $it", safety.validateCopy(it))
            assertFalse("causal: $it", it.contains("because", ignoreCase = true))
        }
    }

    // --- Zones ------------------------------------------------------------------------------

    @Test
    fun recoveryZone_usesWhoopsBands() {
        assertEquals(RecoveryZone.GREEN, RecoveryZone.of(100))
        assertEquals(RecoveryZone.GREEN, RecoveryZone.of(67))
        assertEquals(RecoveryZone.YELLOW, RecoveryZone.of(66))
        assertEquals(RecoveryZone.YELLOW, RecoveryZone.of(34))
        assertEquals(RecoveryZone.RED, RecoveryZone.of(33))
        assertEquals(RecoveryZone.RED, RecoveryZone.of(0))
        // Out-of-range input is clamped, never an exception.
        assertEquals(RecoveryZone.GREEN, RecoveryZone.of(140))
        assertEquals(RecoveryZone.RED, RecoveryZone.of(-5))
        assertEquals("Green", RecoveryZone.GREEN.label)
    }

    @Test
    fun strainZone_usesWhoopsZones() {
        assertEquals(StrainZone.LIGHT, StrainZone.of(0.0))
        assertEquals(StrainZone.LIGHT, StrainZone.of(9.9))
        assertEquals(StrainZone.MODERATE, StrainZone.of(10.0))
        assertEquals(StrainZone.MODERATE, StrainZone.of(13.9))
        assertEquals(StrainZone.HIGH, StrainZone.of(14.0))
        assertEquals(StrainZone.HIGH, StrainZone.of(17.9))
        assertEquals(StrainZone.ALL_OUT, StrainZone.of(18.0))
        assertEquals(StrainZone.ALL_OUT, StrainZone.of(21.0))
        assertEquals(StrainZone.ALL_OUT, StrainZone.of(30.0))
        assertEquals(StrainZone.LIGHT, StrainZone.of(-2.0))
        assertEquals(StrainZone.LIGHT, StrainZone.of(Double.NaN))
        assertEquals(14, StrainZone.HIGH.low)
        assertEquals(17, StrainZone.HIGH.high)
    }

    @Test
    fun formatHoursMinutes_roundsToTheNearestMinute() {
        assertEquals("7h 40m", formatHoursMinutes(7.6667))
        assertEquals("7h 05m", formatHoursMinutes(7.0833))
        assertEquals("8h", formatHoursMinutes(8.0))
        assertEquals("8h", formatHoursMinutes(7.9999)) // rounds across the hour
        assertEquals("45m", formatHoursMinutes(0.75))
        assertEquals("10h 30m", formatHoursMinutes(10.5))
        assertEquals("0m", formatHoursMinutes(0.0))
        assertEquals("0m", formatHoursMinutes(-1.5))
        assertEquals("0m", formatHoursMinutes(Double.NaN))
    }

    // --- Verdict ----------------------------------------------------------------------------

    @Test
    fun verdict_isPendingWithoutData() {
        assertEquals(MorningVerdict.Pending(PendingReason.NO_DATA), VerdictEngine.verdict(null, StrainZone.HIGH))
    }

    @Test
    fun verdict_isPendingUntilTheCycleHasARecoveryScore() {
        val scoring = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = null))
        assertEquals(
            MorningVerdict.Pending(PendingReason.NO_COMPLETED_CYCLE, day),
            VerdictEngine.verdict(scoring, StrainZone.HIGH),
        )
    }

    @Test
    fun verdict_isPendingWhenTheNewestCycleIsFromAnEarlierDay() {
        val carried = DailyPlanSync.carryForward(
            ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day - 1, recoveryScore = 72)),
            targetEpochDay = day,
        )
        // Yesterday's 72% is never presented as today's verdict.
        assertEquals(MorningVerdict.Pending(PendingReason.STALE, day - 1), VerdictEngine.verdict(carried, null))
    }

    @Test
    fun verdict_isReadyFromTodaysCompletedCycle() {
        val readiness = ReadinessSnapshot.from(
            WhoopSnapshot(dateEpochDay = day, recoveryScore = 72),
            listOf(WhoopSnapshot(dateEpochDay = day - 1, recoveryScore = 58)),
        )
        assertEquals(
            MorningVerdict.Ready(
                cycleEpochDay = day, recoveryScore = 72, zone = RecoveryZone.GREEN, band = ReadinessBand.PRIMED,
                previousRecoveryScore = 58, strainTarget = StrainZone.HIGH,
            ),
            VerdictEngine.verdict(readiness, StrainZone.HIGH),
        )
    }

    @Test
    fun verdict_aFailedSyncWithNothingToShowIsSyncFailed_notNoData() {
        assertEquals(MorningVerdict.Pending(PendingReason.SYNC_FAILED), VerdictEngine.verdict(null, null, syncFailed = true))
        assertEquals(MorningVerdict.Pending(PendingReason.NO_DATA), VerdictEngine.verdict(null, null, syncFailed = false))
        // Readiness on hand wins over the failure: the verdict reads from what Spartan has.
        val today = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = 72))
        assertTrue(VerdictEngine.verdict(today, null, syncFailed = true) is MorningVerdict.Ready)
    }

    @Test
    fun verdict_anOldCycleIsStale_evenWithABlankRecoveryOrNoStaleFlag() {
        // A weeks-old cycle with a blank recovery is stale, not "no completed cycle yet today".
        val oldBlank = ReadinessSnapshot(dateEpochDay = day, recoveryScore = null, cycleEpochDay = day - 3, isStale = true)
        assertEquals(MorningVerdict.Pending(PendingReason.STALE, day - 3), VerdictEngine.verdict(oldBlank, null))
        val why = WhyTodayEngine.explain(oldBlank, VerdictEngine.verdict(oldBlank, null), null, emptyList(), null, null)
        assertEquals("Plan pending — your newest WHOOP data is from 3 days ago.", why.summary)
        // An earlier day's cycle is never today's verdict, even when nothing marked it stale.
        val unmarked = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 80, cycleEpochDay = day - 1)
        assertEquals(MorningVerdict.Pending(PendingReason.STALE, day - 1), VerdictEngine.verdict(unmarked, StrainZone.HIGH))
    }

    // --- Headline ---------------------------------------------------------------------------

    @Test
    fun headline_followsTheBand() {
        assertEquals(VerdictHeadline.BIG_DAY, VerdictEngine.headline(ready(80), emptyList()))
        assertEquals(VerdictHeadline.STEADY, VerdictEngine.headline(ready(60), emptyList()))
        assertEquals(VerdictHeadline.EASIER, VerdictEngine.headline(ready(40), emptyList()))
        assertEquals(VerdictHeadline.RECOVERY, VerdictEngine.headline(ready(20), emptyList()))
    }

    @Test
    fun headline_isGentleOnAPrimedPainOrCheckInDay() {
        val primed = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 80, band = ReadinessBand.PRIMED)
        fun headlineFor(readiness: ReadinessSnapshot, options: CoachingOptions = CoachingOptions()): VerdictHeadline {
            val plan = engine.buildPlan(readiness, options)
            return VerdictEngine.headline(VerdictEngine.verdict(readiness, plan.strainTarget) as MorningVerdict.Ready, plan.activities)
        }
        assertEquals(VerdictHeadline.BIG_DAY, headlineFor(primed))
        assertEquals(VerdictHeadline.GENTLE, headlineFor(primed, CoachingOptions(painFlag = true)))
        val rrUp = primed.copy(respiratoryRate = 18.0, respiratoryRateBaseline = 14.0, rrBaselineDays = 7)
        val rhrUp = primed.copy(restingHeartRate = 70.0, rhrVsBaseline = 12.0, rhrBaselineDays = 7)
        assertEquals(VerdictHeadline.GENTLE, headlineFor(rrUp))
        assertEquals(VerdictHeadline.GENTLE, headlineFor(rhrUp))
        // Still inside the learning period (under five nights): no "usual" yet, so no check-in day either.
        assertEquals(VerdictHeadline.BIG_DAY, headlineFor(rrUp.copy(rrBaselineDays = 4)))
        assertEquals(VerdictHeadline.BIG_DAY, headlineFor(rhrUp.copy(rhrBaselineDays = 1)))
    }

    // --- Why today: ready -------------------------------------------------------------------

    @Test
    fun whyToday_readsLikeTheResearchTemplate() {
        val readiness = ReadinessSnapshot.from(
            WhoopSnapshot(dateEpochDay = day, recoveryScore = 72, sleepPerformance = 94, sleepDurationHours = 7.6667),
            listOf(WhoopSnapshot(dateEpochDay = day - 1, recoveryScore = 58, dayStrain = 15.1)),
        )
        val plan = engine.buildPlan(readiness)
        val verdict = VerdictEngine.verdict(readiness, plan.strainTarget)
        val why = WhyTodayEngine.explain(
            readiness, verdict, plan,
            signals = listOf(signal(MetricType.HRV_RMSSD, SignalPosition.WITHIN), signal(MetricType.RESTING_HEART_RATE, SignalPosition.WITHIN)),
            sleptHours = readiness.sleepDurationHours,
            sleepNeedHours = 8.0 + 10.0 / 60,
        )
        assertEquals("Green · 72% — up from 58% yesterday", why.summary)
        assertEquals(
            listOf(
                "Sleep was 7h 40m of about 8h 10m needed.",
                "HRV is near your usual.",
                "Yesterday's strain was 15.1 (High).",
            ),
            why.reasons,
        )
        assertEquals("So today leans into training, with bedtime held to protect tomorrow.", why.conclusion)
        assertSafe(why)
    }

    @Test
    fun whyToday_summaryComparesWithYesterdayOnlyWhenKnown() {
        assertEquals("Yellow · 45% — down from 58% yesterday", explainReady(verdict = ready(45, previous = 58)).summary)
        assertEquals("Green · 72% — same as yesterday", explainReady(verdict = ready(72, previous = 72)).summary)
        assertEquals("Red · 20%", explainReady(verdict = ready(20)).summary)
    }

    @Test
    fun whyToday_sleepReasonFallsBackToDebt() {
        val inDebt = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 50, sleepDebtHours = 1.0 + 20.0 / 60)
        assertEquals(listOf("You're carrying about 1h 20m of sleep debt."), explainReady(readiness = inDebt).reasons)
        // Hours slept without the need is no comparison, so debt is still the line.
        assertEquals(listOf("You're carrying about 1h 20m of sleep debt."), explainReady(readiness = inDebt, slept = 7.0).reasons)
        // A sliver of debt is not worth a line.
        val tinyDebt = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 50, sleepDebtHours = 0.1)
        assertTrue(explainReady(readiness = tinyDebt).reasons.isEmpty())
        assertTrue(explainReady(readiness = null).reasons.isEmpty())
    }

    @Test
    fun whyToday_hrvIsOnlyEverReadAgainstTheUsersUsual() {
        val lines = mapOf(
            SignalPosition.WITHIN to "HRV is near your usual.",
            SignalPosition.ABOVE to "HRV is above your usual.",
            SignalPosition.BELOW to "HRV is below your usual.",
            SignalPosition.LEARNING to "Spartan is still learning your usual HRV.",
        )
        lines.forEach { (position, line) ->
            val reasons = explainReady(signals = listOf(signal(MetricType.HRV_RMSSD, position))).reasons
            assertEquals(listOf(line), reasons)
            assertFalse("no population HRV number: $line", line.any { it.isDigit() })
        }
    }

    @Test
    fun whyToday_restingHeartRateOnlyWhenOffTheUsual() {
        fun reasons(position: SignalPosition) =
            explainReady(signals = listOf(signal(MetricType.RESTING_HEART_RATE, position))).reasons
        assertEquals(listOf("Resting heart rate is above your usual."), reasons(SignalPosition.ABOVE))
        assertEquals(listOf("Resting heart rate is below your usual."), reasons(SignalPosition.BELOW))
        assertTrue(reasons(SignalPosition.WITHIN).isEmpty())
        assertTrue(reasons(SignalPosition.LEARNING).isEmpty())
    }

    @Test
    fun whyToday_respiratoryRateOnlyWhenAboveTheUsual() {
        fun reasons(position: SignalPosition) =
            explainReady(signals = listOf(signal(MetricType.RESPIRATORY_RATE, position))).reasons
        assertEquals(listOf("Respiratory rate is above your usual."), reasons(SignalPosition.ABOVE))
        listOf(SignalPosition.BELOW, SignalPosition.WITHIN, SignalPosition.LEARNING).forEach {
            assertTrue("$it", reasons(it).isEmpty())
        }
    }

    @Test
    fun whyToday_yesterdaysStrainCarriesItsZone() {
        // "Yesterday" needs the strain to come from the day before the cycle (previousCycleEpochDay).
        fun strainLine(strain: Double) = explainReady(
            readiness = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 70, dayStrainPrior = strain, previousCycleEpochDay = day - 1),
        ).reasons
        assertEquals(listOf("Yesterday's strain was 19.2 (All out)."), strainLine(19.24))
        assertEquals(listOf("Yesterday's strain was 4.0 (Light)."), strainLine(4.0))
        assertTrue(strainLine(Double.NaN).isEmpty())
    }

    @Test
    fun whyToday_afterADayWithoutTheStrap_nothingIsCalledYesterday() {
        val gap = ReadinessSnapshot.from(
            WhoopSnapshot(dateEpochDay = day, recoveryScore = 70),
            listOf(WhoopSnapshot(dateEpochDay = day - 2, recoveryScore = 58, dayStrain = 13.0)),
        )
        val why = explainReady(readiness = gap, verdict = VerdictEngine.verdict(gap, null))
        assertEquals("Green · 70%", why.summary)
        assertEquals(listOf("Your last recorded strain was 13.0 (Moderate)."), why.reasons)
        // No known earlier day at all reads the same way.
        assertEquals(
            listOf("Your last recorded strain was 4.0 (Light)."),
            explainReady(readiness = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 70, dayStrainPrior = 4.0)).reasons,
        )
        assertSafe(why)
    }

    @Test
    fun whyToday_conclusionFollowsThePlan() {
        assertEquals("So today leans into training, with bedtime held to protect tomorrow.", explainReady(plan = planWith(StrainZone.HIGH)).conclusion)
        assertEquals("So today leans into training, with bedtime held to protect tomorrow.", explainReady(plan = planWith(StrainZone.ALL_OUT)).conclusion)
        assertEquals("So today holds steady: moderate effort and a protected bedtime.", explainReady(plan = planWith(StrainZone.MODERATE)).conclusion)
        assertEquals("So today is lighter so tomorrow can be bigger — rest is part of the plan.", explainReady(plan = planWith(StrainZone.LIGHT)).conclusion)
        assertEquals("So today keeps things steady.", explainReady(plan = planWith(null)).conclusion)
        assertEquals("So today keeps things steady.", explainReady(plan = null).conclusion)
    }

    @Test
    fun whyToday_painOverridesTheStrainConclusion() {
        val readiness = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 80, band = ReadinessBand.PRIMED)
        val plan = engine.buildPlan(readiness, CoachingOptions(painFlag = true))
        val why = explainReady(readiness = readiness, plan = plan, verdict = VerdictEngine.verdict(readiness, plan.strainTarget))
        assertEquals("You flagged pain recently, so today stays gentle — stop anything that hurts.", why.conclusion)
        assertSafe(why)
    }

    @Test
    fun whyToday_checkInDayStaysEasy_andNamesARisingRespiratoryRate() {
        // Respiratory rate 4 over the user's baseline on a primed day: the clinician check-in fires.
        val readiness = ReadinessSnapshot(
            dateEpochDay = day, recoveryScore = 80, band = ReadinessBand.PRIMED,
            respiratoryRate = 19.0, respiratoryRateBaseline = 15.0, rrBaselineDays = 7,
        )
        val plan = engine.buildPlan(readiness)
        assertEquals(StrainZone.LIGHT, plan.strainTarget)
        val why = WhyTodayEngine.explain(
            readiness, VerdictEngine.verdict(readiness, plan.strainTarget), plan,
            listOf(signal(MetricType.RESPIRATORY_RATE, SignalPosition.ABOVE)), null, null,
        )
        val checkIn = "One of today's readings is outside your usual range, so today stays easy — notice how you feel. " +
            "One reading is not a diagnosis."
        assertEquals(listOf("Respiratory rate is above your usual."), why.reasons)
        assertEquals(checkIn, why.conclusion)
        assertFalse(why.conclusion.contains("moderate", ignoreCase = true))
        assertSafe(why)
        // Pain on the same day: the check-in line still leads.
        val both = engine.buildPlan(readiness, CoachingOptions(painFlag = true))
        assertEquals(checkIn, explainReady(readiness = readiness, plan = both).conclusion)
    }

    // --- Why today: pending -----------------------------------------------------------------

    @Test
    fun whyToday_noCompletedCycleExplainsTheCycleRule() {
        val readiness = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = null))
        val plan = engine.buildPlan(readiness)
        val why = WhyTodayEngine.explain(readiness, VerdictEngine.verdict(readiness, plan.strainTarget), plan, emptyList(), null, null)
        assertEquals("Plan pending — no completed WHOOP cycle yet today.", why.summary)
        assertEquals(listOf(cycleRule), why.reasons)
        assertEquals("Here's a gentle holding plan. It updates when today's cycle arrives.", why.conclusion)
        assertSafe(why)
    }

    @Test
    fun whyToday_staleDataSaysHowOldItIs_andNeverQuotesItsScore() {
        fun staleFrom(daysAgo: Long): WhyToday {
            val carried = DailyPlanSync.carryForward(
                ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day - daysAgo, recoveryScore = 72)),
                targetEpochDay = day,
            )
            val plan = engine.buildPlan(carried)
            return WhyTodayEngine.explain(
                carried, VerdictEngine.verdict(carried, plan.strainTarget), plan,
                listOf(signal(MetricType.HRV_RMSSD, SignalPosition.WITHIN)), 7.5, 8.0,
            )
        }
        val yesterday = staleFrom(1)
        assertEquals("Plan pending — your newest WHOOP data is from yesterday.", yesterday.summary)
        assertEquals(listOf(cycleRule), yesterday.reasons)
        assertEquals("Here's a gentle holding plan until a new cycle arrives.", yesterday.conclusion)
        assertEquals("Plan pending — your newest WHOOP data is from 3 days ago.", staleFrom(3).summary)
        listOf(yesterday, staleFrom(3)).forEach { why ->
            (listOf(why.summary, why.conclusion) + why.reasons).forEach {
                assertFalse("no recovery number in pending copy: $it", it.contains("72") || it.contains("%"))
            }
            assertSafe(why)
        }
    }

    @Test
    fun whyToday_staleWithoutAKnownAgeStaysVague() {
        val earlier = "Plan pending — your newest WHOOP data is from an earlier day."
        assertEquals(earlier, WhyTodayEngine.explain(null, MorningVerdict.Pending(PendingReason.STALE, day - 2), null, emptyList(), null, null).summary)
        val readiness = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 60, isStale = true)
        assertEquals(earlier, WhyTodayEngine.explain(readiness, MorningVerdict.Pending(PendingReason.STALE, null), null, emptyList(), null, null).summary)
        assertEquals(earlier, WhyTodayEngine.explain(readiness, MorningVerdict.Pending(PendingReason.STALE, day), null, emptyList(), null, null).summary)
        // A cycle dated after the device's today is named as such, never "from an earlier day".
        assertEquals(
            "Plan pending — your newest WHOOP data is dated after today.",
            WhyTodayEngine.explain(readiness, MorningVerdict.Pending(PendingReason.STALE, day + 1), null, emptyList(), null, null).summary,
        )
    }

    @Test
    fun whyToday_failedSyncSaysSo_withoutAskingToConnectOrPromisingAPlan() {
        val why = WhyTodayEngine.explain(null, VerdictEngine.verdict(null, null, syncFailed = true), null, emptyList(), null, null)
        assertEquals("Couldn't refresh your WHOOP data.", why.summary)
        assertEquals(listOf("Spartan keeps what it already has and tries again next time."), why.reasons)
        assertEquals("Pull down to try again.", why.conclusion)
        (listOf(why.summary, why.conclusion) + why.reasons).forEach {
            assertFalse(it, it.contains("connect", ignoreCase = true))
            assertFalse(it, it.contains("holding plan", ignoreCase = true))
        }
        assertSafe(why)
    }

    @Test
    fun whyToday_noDataInvitesAConnection() {
        val why = WhyTodayEngine.explain(null, VerdictEngine.verdict(null, null), null, emptyList(), null, null)
        assertEquals("No WHOOP data yet.", why.summary)
        assertEquals(listOf("Connect WHOOP or import your WHOOP export to get a morning verdict."), why.reasons)
        assertEquals("Until then, here's a gentle holding plan.", why.conclusion)
        assertSafe(why)
    }

    @Test
    fun whyToday_everyCombinationIsSafeCopy() {
        val verdicts = listOf<MorningVerdict>(
            ready(80, 60, StrainZone.HIGH), ready(50, 50, StrainZone.MODERATE), ready(20, 40, StrainZone.LIGHT), ready(70),
            MorningVerdict.Pending(PendingReason.NO_DATA),
            MorningVerdict.Pending(PendingReason.NO_COMPLETED_CYCLE, day),
            MorningVerdict.Pending(PendingReason.STALE, day - 1),
            MorningVerdict.Pending(PendingReason.SYNC_FAILED),
        )
        val readiness = ReadinessSnapshot(dateEpochDay = day, recoveryScore = 50, sleepDebtHours = 2.0, dayStrainPrior = 18.4)
        val checkInPlan = engine.buildPlan(
            ReadinessSnapshot(
                dateEpochDay = day, recoveryScore = 80, band = ReadinessBand.PRIMED,
                respiratoryRate = 18.0, respiratoryRateBaseline = 14.0, rrBaselineDays = 7,
            ),
        )
        val plans = StrainZone.entries.map { planWith(it) } + listOf(planWith(null), null, checkInPlan)
        for (verdict in verdicts) for (plan in plans) for (position in SignalPosition.entries) {
            val signals = listOf(
                signal(MetricType.HRV_RMSSD, position), signal(MetricType.RESTING_HEART_RATE, position),
                signal(MetricType.RESPIRATORY_RATE, position),
            )
            assertSafe(WhyTodayEngine.explain(readiness, verdict, plan, signals, 6.5, 7.75))
            assertSafe(WhyTodayEngine.explain(readiness, verdict, plan, signals, null, null))
        }
    }
}
