package com.spartan.data.whoop.csv

import com.spartan.data.local.WhoopCycleEntity
import com.spartan.domain.model.WhoopSnapshot

/** Which WHOOP export file a CSV is, detected from its header row. */
enum class WhoopCsvKind { PHYSIOLOGICAL_CYCLES, SLEEPS, WORKOUTS, JOURNAL_ENTRIES }

/**
 * One parsed WHOOP export file. Only the list matching [kind] is populated; the raw
 * cycle-start string is kept on each row so files can be joined per physiological cycle.
 */
data class ParsedWhoopFile(
    val kind: WhoopCsvKind,
    val cycles: List<WhoopCycleRow> = emptyList(),
    val sleeps: List<WhoopSleepRow> = emptyList(),
    val workouts: List<WhoopWorkoutRow> = emptyList(),
    val journal: List<WhoopJournalRow> = emptyList(),
)

/** A row of physiological_cycles.csv. Nullable fields mirror blank cells in the export. */
data class WhoopCycleRow(
    val cycleStartRaw: String,
    val dateEpochDay: Long,
    val recoveryScore: Int?,
    val restingHeartRate: Double?,
    val hrvMs: Double?,
    val dayStrain: Double?,
    val energyKcal: Double?,
    val sleepPerformance: Int?,
    val respiratoryRate: Double?,
    val sleepDurationHours: Double?,
    val sleepDebtHours: Double?,
    val bedMinuteOfDay: Int?,
    val wakeMinuteOfDay: Int?,
)

/** A row of sleeps.csv (naps included; the merger prefers the main non-nap sleep). */
data class WhoopSleepRow(
    val cycleStartRaw: String,
    val dateEpochDay: Long,
    val nap: Boolean,
    val sleepPerformance: Int?,
    val respiratoryRate: Double?,
    val sleepDurationHours: Double?,
    val sleepDebtHours: Double?,
    val bedMinuteOfDay: Int?,
    val wakeMinuteOfDay: Int?,
)

/** A row of workouts.csv. The date is the calendar day the workout started. */
data class WhoopWorkoutRow(
    val dateEpochDay: Long,
    val startMinuteOfDay: Int?,
    val durationMinutes: Int,
    val activityName: String,
    val strain: Double?,
    val energyKcal: Double?,
    val maxHr: Int?,
    val averageHr: Int?,
    val hrZonePercents: List<Double?>, // zones 1..5; null when the column is blank
)

/** A row of journal_entries.csv — one behavior question answered for one cycle. */
data class WhoopJournalRow(
    val cycleStartRaw: String,
    val dateEpochDay: Long,
    val questionText: String,
    val answeredYes: Boolean,
)

fun WhoopCycleEntity.toWhoopSnapshot(): WhoopSnapshot = WhoopSnapshot(
    dateEpochDay = dateEpochDay,
    recoveryScore = recoveryScore,
    hrvMs = hrvMs,
    restingHeartRate = restingHeartRate,
    sleepPerformance = sleepPerformance,
    sleepDurationHours = sleepDurationHours,
    sleepDebtHours = sleepDebtHours,
    respiratoryRate = respiratoryRate,
    dayStrain = dayStrain,
    energyKcal = energyKcal,
    bedMinuteOfDay = bedMinuteOfDay,
    wakeMinuteOfDay = wakeMinuteOfDay,
    isMock = false,
)
