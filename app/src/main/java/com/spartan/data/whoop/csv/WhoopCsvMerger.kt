package com.spartan.data.whoop.csv

import com.spartan.data.local.WhoopCycleEntity
import com.spartan.data.local.WhoopWorkoutEntity
import com.spartan.domain.model.WhoopSnapshot

/** Everything one import produced, ready to persist. */
data class WhoopImportData(
    val cycles: List<WhoopCycleEntity>,
    val workouts: List<WhoopWorkoutEntity>,
) {
    val snapshots: List<WhoopSnapshot> get() = cycles.map { it.toWhoopSnapshot() }

    /** Real minutes of recorded exercise per calendar day, for the EXERCISE_MINUTES trend. */
    val exerciseMinutesByDay: Map<Long, Int>
        get() = workouts.groupBy { it.dateEpochDay }.mapValues { (_, w) -> w.sumOf { it.durationMinutes } }
}

/**
 * Joins the parsed WHOOP export files into one per-day record set. physiological_cycles.csv is
 * authoritative for recovery/strain/vitals; sleeps.csv refines bed/wake times to the MAIN sleep
 * (the cycles file sometimes lists a nap's window); journal_entries.csv contributes behavior
 * flags; workouts.csv becomes its own table. Pure Kotlin — unit-tested against export fixtures.
 */
object WhoopCsvMerger {

    fun merge(files: List<ParsedWhoopFile>, importedAtMillis: Long): WhoopImportData {
        val cycleRows = files.flatMap { it.cycles }
        val sleepRows = files.flatMap { it.sleeps }
        val journalRows = files.flatMap { it.journal }
        val workoutRows = files.flatMap { it.workouts }

        // Main (non-nap) sleep per cycle, longest first when a cycle has several.
        val mainSleepByCycle = sleepRows.filter { !it.nap }
            .groupBy { it.cycleStartRaw }
            .mapValues { (_, sleeps) -> sleeps.maxBy { it.sleepDurationHours ?: 0.0 } }

        val journalByCycle = journalRows.groupBy { it.cycleStartRaw }.mapValues { (_, rows) ->
            JournalFlags(
                caffeine = rows.answerTo("caffeine"),
                alcohol = rows.answerTo("alcohol"),
                lateMeal = rows.answerTo("food close to bedtime"),
            )
        }

        val fromCycles = cycleRows.map { c ->
            val sleep = mainSleepByCycle[c.cycleStartRaw]
            val journal = journalByCycle[c.cycleStartRaw]
            WhoopCycleEntity(
                dateEpochDay = c.dateEpochDay,
                recoveryScore = c.recoveryScore,
                hrvMs = c.hrvMs,
                restingHeartRate = c.restingHeartRate,
                sleepPerformance = c.sleepPerformance,
                sleepDurationHours = c.sleepDurationHours,
                sleepDebtHours = c.sleepDebtHours,
                respiratoryRate = c.respiratoryRate,
                dayStrain = c.dayStrain,
                energyKcal = c.energyKcal,
                bedMinuteOfDay = sleep?.bedMinuteOfDay ?: c.bedMinuteOfDay,
                wakeMinuteOfDay = sleep?.wakeMinuteOfDay ?: c.wakeMinuteOfDay,
                journalCaffeine = journal?.caffeine,
                journalAlcohol = journal?.alcohol,
                journalLateMeal = journal?.lateMeal,
                importedAtMillis = importedAtMillis,
            )
        }

        // Sleeps with no cycle row (user imported only sleeps.csv): keep the sleep-side fields
        // so the sleep trends still populate rather than dropping the day.
        val cycleDays = fromCycles.map { it.dateEpochDay }.toSet()
        val fromSleepsOnly = mainSleepByCycle.values
            .filter { it.dateEpochDay !in cycleDays }
            .map { s ->
                WhoopCycleEntity(
                    dateEpochDay = s.dateEpochDay,
                    recoveryScore = null,
                    hrvMs = null,
                    restingHeartRate = null,
                    sleepPerformance = s.sleepPerformance,
                    sleepDurationHours = s.sleepDurationHours,
                    sleepDebtHours = s.sleepDebtHours,
                    respiratoryRate = s.respiratoryRate,
                    dayStrain = null,
                    energyKcal = null,
                    bedMinuteOfDay = s.bedMinuteOfDay,
                    wakeMinuteOfDay = s.wakeMinuteOfDay,
                    journalCaffeine = journalByCycle[s.cycleStartRaw]?.caffeine,
                    journalAlcohol = journalByCycle[s.cycleStartRaw]?.alcohol,
                    journalLateMeal = journalByCycle[s.cycleStartRaw]?.lateMeal,
                    importedAtMillis = importedAtMillis,
                )
            }

        // One record per day: when two cycles land on the same date, keep the richer one.
        val cycles = (fromCycles + fromSleepsOnly)
            .groupBy { it.dateEpochDay }
            .map { (_, candidates) -> candidates.maxBy { it.nonNullFieldCount() } }
            .sortedBy { it.dateEpochDay }

        val workouts = workoutRows.map { w ->
            WhoopWorkoutEntity(
                id = "${w.dateEpochDay}:${w.startMinuteOfDay ?: 0}:${w.activityName}",
                dateEpochDay = w.dateEpochDay,
                startMinuteOfDay = w.startMinuteOfDay,
                durationMinutes = w.durationMinutes,
                activityName = w.activityName,
                strain = w.strain,
                energyKcal = w.energyKcal,
                maxHr = w.maxHr,
                averageHr = w.averageHr,
                hrZone1Pct = w.hrZonePercents.getOrNull(0),
                hrZone2Pct = w.hrZonePercents.getOrNull(1),
                hrZone3Pct = w.hrZonePercents.getOrNull(2),
                hrZone4Pct = w.hrZonePercents.getOrNull(3),
                hrZone5Pct = w.hrZonePercents.getOrNull(4),
                importedAtMillis = importedAtMillis,
            )
        }.distinctBy { it.id }.sortedWith(compareBy({ it.dateEpochDay }, { it.startMinuteOfDay ?: 0 }))

        return WhoopImportData(cycles = cycles, workouts = workouts)
    }

    private data class JournalFlags(val caffeine: Boolean?, val alcohol: Boolean?, val lateMeal: Boolean?)

    /** The yes/no answer to the question containing [keyword], or null if it was never asked. */
    private fun List<WhoopJournalRow>.answerTo(keyword: String): Boolean? =
        filter { it.questionText.contains(keyword, ignoreCase = true) }
            .takeIf { it.isNotEmpty() }
            ?.any { it.answeredYes }

    private fun WhoopCycleEntity.nonNullFieldCount(): Int = listOf(
        recoveryScore, hrvMs, restingHeartRate, sleepPerformance, sleepDurationHours,
        sleepDebtHours, respiratoryRate, dayStrain, energyKcal, bedMinuteOfDay, wakeMinuteOfDay,
    ).count { it != null }
}
