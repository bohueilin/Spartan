package com.spartan.data.whoop

import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WhoopSnapshot
import java.time.LocalDate

/**
 * The single normalization boundary between WHOOP-shaped data and Spartan's internal metric model.
 * Converts a [WhoopSnapshot] into [MetricReading]s so WHOOP data flows through the same
 * persistence, assessment, and trend pipeline as every other metric — no special-casing above here.
 *
 * When the real WHOOP REST client lands, it maps raw DTOs into [WhoopSnapshot] first; this mapper
 * stays unchanged.
 */
object WhoopMapper {

    fun toReadings(snapshot: WhoopSnapshot): List<MetricReading> {
        val date = LocalDate.ofEpochDay(snapshot.dateEpochDay)
        val note = if (snapshot.isMock) "WHOOP (sample)" else "WHOOP"
        val out = mutableListOf<MetricReading>()
        fun add(type: MetricType, value: Double?) {
            if (value != null) out += MetricReading(type = type, value = value, recordedAt = date, note = note)
        }
        add(MetricType.RECOVERY_SCORE, snapshot.recoveryScore?.toDouble())
        add(MetricType.HRV_RMSSD, snapshot.hrvMs)
        add(MetricType.RESTING_HEART_RATE, snapshot.restingHeartRate)
        add(MetricType.SLEEP_PERFORMANCE, snapshot.sleepPerformance?.toDouble())
        add(MetricType.SLEEP_DURATION, snapshot.sleepDurationHours)
        add(MetricType.SLEEP_DEBT, snapshot.sleepDebtHours)
        add(MetricType.RESPIRATORY_RATE, snapshot.respiratoryRate)
        add(MetricType.DAY_STRAIN, snapshot.dayStrain)
        add(MetricType.ENERGY_KCAL, snapshot.energyKcal)
        return out
    }

    fun toReadings(snapshots: List<WhoopSnapshot>): List<MetricReading> = snapshots.flatMap(::toReadings)
}
