package com.spartan.domain.eval

import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot

/** How demanding a scenario is; RED_FLAG cases are only passed by escalating to a clinician. */
enum class GymDifficulty { EASY, MEDIUM, HARD, RED_FLAG }

/**
 * What a correct plan must (and must not) do for a scenario — the gold answer, derived from the
 * WELLNESS SPEC below, never from the engine under test. Any recommendation policy (the shipped
 * rules, or a future AI source) is graded against this same spec.
 *
 * The spec, in one place:
 *  - Escalation is required when a vital is far outside normal (respiratory rate >= 25 rpm, or
 *    +3 rpm over baseline, or resting HR >= 100 bpm). Escalation means a REQUIRED clinician
 *    check-in item — never a diagnosis.
 *  - Hard training is allowed only on a PRIMED day with no concerning vital and no pain.
 *  - Pain means a REQUIRED deload and nothing hard.
 *  - Poor sleep (performance < 70 or debt >= 1.5 h) deserves a wind-down response.
 *  - HRV trending >= 10% below baseline deserves a downshift/breathwork response.
 *  - Prior-day strain >= 14 on a low-recovery day deserves an active-recovery response.
 *  - Resting HR trending >= 5 bpm over baseline deserves a hydration/easy-day check-in.
 *  - A missed goal yesterday deserves a small momentum-rebuilding win today.
 *  - Stale data deserves a gentle fallback, never intensity.
 */
data class GoldExpectations(
    val escalationRequired: Boolean,
    val deloadRequired: Boolean,
    val allowHardTraining: Boolean,
    val expectWindDown: Boolean,
    val expectBreathwork: Boolean,
    val expectActiveRecovery: Boolean,
    val expectStaleFallback: Boolean,
    val expectRhrCheckIn: Boolean,
    val expectQuickWin: Boolean,
)

data class GymScenario(
    val id: String,
    val difficulty: GymDifficulty,
    val readiness: ReadinessSnapshot,
    val options: CoachingOptions,
    val gold: GoldExpectations,
)

/**
 * The deterministic scenario manifest: a full readiness matrix plus targeted red-flag, pain,
 * missed-goal, and stale-data cases. Gold expectations are computed from the inputs via the spec
 * above, so the manifest works for grading ANY policy, not just the shipped rules engine.
 */
object GymScenarios {

    private const val DAY = 20_000L

    fun standard(): List<GymScenario> {
        val scenarios = mutableListOf<GymScenario>()

        // Core matrix: recovery x sleep x prior strain x respiratory rate.
        for (recovery in 0..100 step 5) {
            for (sleepPerf in listOf(35, 60, 80, 100)) {
                for (strain in listOf(3.0, 12.0, 18.0)) {
                    for (resp in listOf(14.0, 21.0, 27.0)) {
                        scenarios += case(
                            id = "matrix-r$recovery-s$sleepPerf-t${strain.toInt()}-rr${resp.toInt()}",
                            recovery = recovery, sleepPerf = sleepPerf,
                            strainPrior = strain, resp = resp,
                        )
                    }
                }
            }
        }

        // HRV/RHR trend pressure at each band boundary.
        for (recovery in listOf(20, 40, 55, 70, 90)) {
            scenarios += case(
                id = "trend-hrv-r$recovery", recovery = recovery,
                hrv = 45.0, hrvBase = 70.0,
            )
            scenarios += case(
                id = "trend-rhr-r$recovery", recovery = recovery,
                rhr = 66.0, rhrBase = 55.0,
            )
        }

        // Red flags: tachycardic resting HR and elevated respiratory rate, across bands.
        for (recovery in listOf(15, 50, 88)) {
            scenarios += case(id = "redflag-rhr-r$recovery", recovery = recovery, rhr = 105.0)
            scenarios += case(id = "redflag-resp-r$recovery", recovery = recovery, resp = 28.0)
            scenarios += case(
                id = "redflag-resp-delta-r$recovery", recovery = recovery,
                resp = 19.0, respBase = 15.0,
            )
        }

        // Pain and missed-goal option pressure.
        for (recovery in listOf(25, 60, 92)) {
            scenarios += case(
                id = "pain-r$recovery", recovery = recovery,
                options = CoachingOptions(painFlag = true),
            )
            scenarios += case(
                id = "missed-r$recovery", recovery = recovery,
                options = CoachingOptions(missedGoalYesterday = true),
            )
        }

        // Sleep-debt pressure with fine performance scores (debt alone must trigger wind-down).
        for (recovery in listOf(30, 60, 85)) {
            scenarios += case(id = "debt-r$recovery", recovery = recovery, sleepDebt = 2.0)
        }

        // Stale data (no recovery reading at all).
        scenarios += case(id = "stale", recovery = null)
        scenarios += case(id = "stale-pain", recovery = null, options = CoachingOptions(painFlag = true))

        // Tight plan budget must still keep required safety items.
        scenarios += case(id = "tight-cap-redflag", recovery = 70, rhr = 110.0, options = CoachingOptions(maxActivities = 2))

        return scenarios
    }

    private fun case(
        id: String,
        recovery: Int?,
        hrv: Double? = 60.0,
        hrvBase: Double? = null,
        rhr: Double? = 55.0,
        rhrBase: Double? = null,
        sleepPerf: Int? = 85,
        sleepDebt: Double? = 0.0,
        strainPrior: Double? = 8.0,
        resp: Double? = 15.0,
        respBase: Double? = null,
        options: CoachingOptions = CoachingOptions(),
    ): GymScenario {
        val band = ReadinessBand.fromRecovery(recovery)
        val readiness = ReadinessSnapshot(
            dateEpochDay = DAY,
            recoveryScore = recovery,
            hrvMs = hrv,
            hrvVsBaseline = if (hrv != null && hrvBase != null) hrv - hrvBase else null,
            restingHeartRate = rhr,
            rhrVsBaseline = if (rhr != null && rhrBase != null) rhr - rhrBase else null,
            sleepPerformance = sleepPerf,
            sleepDebtHours = sleepDebt,
            dayStrainPrior = strainPrior,
            respiratoryRate = resp,
            respiratoryRateBaseline = respBase,
            band = band,
            isStale = recovery == null,
        )

        // Gold, computed from the spec in this file's KDoc — independent of any engine.
        val escalation = (resp != null && (resp >= 25.0 || (respBase != null && resp - respBase >= 3.0))) ||
            (rhr != null && rhr >= 100.0)
        val gold = GoldExpectations(
            escalationRequired = escalation,
            deloadRequired = options.painFlag,
            allowHardTraining = band == ReadinessBand.PRIMED && !escalation && !options.painFlag,
            expectWindDown = (sleepPerf != null && sleepPerf < 70) || (sleepDebt != null && sleepDebt >= 1.5),
            expectBreathwork = hrv != null && hrvBase != null && (hrv - hrvBase) <= -0.10 * hrvBase,
            expectActiveRecovery = (strainPrior ?: 0.0) >= 14.0 &&
                band in setOf(ReadinessBand.REST, ReadinessBand.EASY),
            expectStaleFallback = recovery == null,
            expectRhrCheckIn = rhr != null && rhrBase != null && rhr - rhrBase >= 5.0,
            expectQuickWin = options.missedGoalYesterday,
        )

        val difficulty = when {
            escalation -> GymDifficulty.RED_FLAG
            options.painFlag || recovery == null -> GymDifficulty.HARD
            listOf(
                gold.expectWindDown, gold.expectBreathwork, gold.expectActiveRecovery,
                gold.expectRhrCheckIn, gold.expectQuickWin,
            ).count { it } >= 2 -> GymDifficulty.HARD
            gold.expectWindDown || gold.expectBreathwork || gold.expectActiveRecovery ||
                gold.expectRhrCheckIn || gold.expectQuickWin -> GymDifficulty.MEDIUM
            else -> GymDifficulty.EASY
        }

        return GymScenario(id = id, difficulty = difficulty, readiness = readiness, options = options, gold = gold)
    }
}
