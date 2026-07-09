package com.spartan.data.whoop

import com.spartan.domain.model.WhoopSnapshot
import java.time.LocalDate
import javax.inject.Inject

/**
 * SAMPLE DATA ONLY. Produces a plausible 7-day WHOOP-style series so Spartan can be developed,
 * demoed, and tested with no credentials and no network. Every snapshot is tagged `isMock = true`
 * and the UI surfaces a "Sample data" label. This is NOT real WHOOP data and must never be
 * presented as if it were.
 *
 * The series ends on an easy-recovery day with mild sleep debt and a slightly elevated resting
 * heart rate, so the coaching engine has meaningful signals to demonstrate.
 */
class MockWhoopClient @Inject constructor() : WhoopClient {

    override val isMock: Boolean = true

    override suspend fun fetchRecentDays(days: Int): List<WhoopSnapshot> {
        val today = LocalDate.now().toEpochDay()
        // recovery, hrv, rhr, sleepPerf, sleepHrs, sleepDebtHrs, resp, strain, kcal
        val series = listOf(
            Sample(72, 78.0, 52.0, 88, 7.6, 0.2, 14.2, 12.4, 2450.0),
            Sample(64, 71.0, 53.0, 82, 7.1, 0.6, 14.0, 15.1, 2610.0),
            Sample(58, 66.0, 54.0, 76, 6.8, 1.0, 14.3, 16.8, 2740.0),
            Sample(49, 61.0, 55.0, 71, 6.5, 1.4, 14.6, 17.9, 2810.0),
            Sample(55, 63.0, 55.0, 74, 6.9, 1.1, 14.4, 11.2, 2380.0),
            Sample(61, 68.0, 54.0, 80, 7.2, 0.7, 14.1, 13.0, 2500.0),
            Sample(42, 58.0, 59.0, 63, 6.1, 1.8, 15.1, 9.4, 2210.0), // today
        )
        val window = series.takeLast(days.coerceIn(1, series.size))
        val startDay = today - (window.size - 1)
        return window.mapIndexed { index, s ->
            WhoopSnapshot(
                dateEpochDay = startDay + index,
                recoveryScore = s.recovery,
                hrvMs = s.hrv,
                restingHeartRate = s.rhr,
                sleepPerformance = s.sleepPerf,
                sleepDurationHours = s.sleepHours,
                sleepDebtHours = s.sleepDebt,
                respiratoryRate = s.resp,
                dayStrain = s.strain,
                energyKcal = s.kcal,
                bedMinuteOfDay = 22 * 60 + 45, // sample bedtime 22:45
                wakeMinuteOfDay = 6 * 60 + 30, // sample wake 06:30
                isMock = true,
            )
        }
    }

    private data class Sample(
        val recovery: Int, val hrv: Double, val rhr: Double, val sleepPerf: Int,
        val sleepHours: Double, val sleepDebt: Double, val resp: Double, val strain: Double, val kcal: Double,
    )
}
