package com.spartan.data.whoop

import com.spartan.domain.model.WhoopSnapshot
import java.time.Instant
import java.time.ZoneId

/**
 * Pure normalization from WHOOP API DTOs to per-day [WhoopSnapshot]s. Recovery, sleep, and cycle
 * records are each keyed by their local date and joined. Kept free of Android/Retrofit types so it
 * is fully unit-testable — this is the single boundary where WHOOP's shape becomes Spartan's model.
 */
class WhoopResponseMapper(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun toSnapshots(
        recovery: List<WhoopRecoveryRecord>,
        sleep: List<WhoopSleepRecord>,
        cycle: List<WhoopCycleRecord>,
    ): List<WhoopSnapshot> {
        val byDay = sortedMapOf<Long, Builder>()
        fun at(day: Long) = byDay.getOrPut(day) { Builder(day) }

        recovery.forEach { r ->
            val day = epochDay(r.createdAt) ?: return@forEach
            val s = r.score ?: return@forEach
            at(day).apply {
                recoveryScore = s.recoveryScore?.toInt() ?: recoveryScore
                hrvMs = s.hrvRmssdMilli ?: hrvMs
                restingHeartRate = s.restingHeartRate ?: restingHeartRate
            }
        }
        sleep.filterNot { it.nap }.forEach { sl ->
            val day = epochDay(sl.end ?: sl.start) ?: return@forEach
            val s = sl.score ?: return@forEach
            at(day).apply {
                sleepPerformance = s.sleepPerformancePercentage?.toInt() ?: sleepPerformance
                respiratoryRate = s.respiratoryRate ?: respiratoryRate
                val inBed = s.stageSummary?.totalInBedMilli
                val awake = s.stageSummary?.totalAwakeMilli ?: 0L
                if (inBed != null) sleepDurationHours = (inBed - awake).coerceAtLeast(0L) / 3_600_000.0
                s.sleepNeeded?.needFromSleepDebtMilli?.let { sleepDebtHours = it / 3_600_000.0 }
                // Local bed/wake anchor the scheduling window so nudges never land in sleep.
                bedMinuteOfDay = minuteOfDay(sl.start) ?: bedMinuteOfDay
                wakeMinuteOfDay = minuteOfDay(sl.end) ?: wakeMinuteOfDay
            }
        }
        cycle.forEach { c ->
            val day = epochDay(c.start) ?: return@forEach
            val s = c.score ?: return@forEach
            at(day).apply {
                dayStrain = s.strain ?: dayStrain
                s.kilojoule?.let { energyKcal = it * KJ_TO_KCAL }
            }
        }
        return byDay.values.map { it.build() }
    }

    private fun epochDay(iso: String?): Long? = try {
        if (iso.isNullOrBlank()) null
        else Instant.parse(iso).atZone(zone).toLocalDate().toEpochDay()
    } catch (_: Exception) {
        null
    }

    private fun minuteOfDay(iso: String?): Int? = try {
        if (iso.isNullOrBlank()) null
        else Instant.parse(iso).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
    } catch (_: Exception) {
        null
    }

    private class Builder(val day: Long) {
        var recoveryScore: Int? = null
        var hrvMs: Double? = null
        var restingHeartRate: Double? = null
        var sleepPerformance: Int? = null
        var sleepDurationHours: Double? = null
        var sleepDebtHours: Double? = null
        var respiratoryRate: Double? = null
        var dayStrain: Double? = null
        var energyKcal: Double? = null
        var bedMinuteOfDay: Int? = null
        var wakeMinuteOfDay: Int? = null

        fun build() = WhoopSnapshot(
            dateEpochDay = day,
            recoveryScore = recoveryScore,
            hrvMs = hrvMs,
            restingHeartRate = restingHeartRate,
            sleepPerformance = sleepPerformance,
            sleepDurationHours = sleepDurationHours?.let { round1(it) },
            sleepDebtHours = sleepDebtHours?.let { round1(it) },
            respiratoryRate = respiratoryRate,
            dayStrain = dayStrain,
            energyKcal = energyKcal?.let { kotlin.math.round(it) },
            bedMinuteOfDay = bedMinuteOfDay,
            wakeMinuteOfDay = wakeMinuteOfDay,
            isMock = false,
        )
    }

    companion object {
        const val KJ_TO_KCAL = 0.239006
        private fun round1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
    }
}
