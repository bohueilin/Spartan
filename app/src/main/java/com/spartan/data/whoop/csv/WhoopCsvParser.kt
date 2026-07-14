package com.spartan.data.whoop.csv

import java.time.LocalDateTime
import kotlin.math.round

/**
 * Parses the CSV files inside a WHOOP data export ("my_whoop_data_*") into typed rows —
 * pure Kotlin, no Android dependencies, so the whole format lives under unit test.
 *
 * Format notes, learned from real exports:
 *  - Timestamps ("2026-07-13 01:47:04") are LOCAL time; the "Cycle timezone" column names the
 *    offset but the wall-clock values are already local, so they are used as-is.
 *  - A cycle's day is the calendar date the user WOKE UP (a cycle that starts 23:09 belongs to
 *    the next morning). When wake onset is blank, the cycle start date is the fallback.
 *  - Blank cells are common (no-sleep cycles, missing SpO2) and must parse to null, not fail.
 *  - Sleep debt / asleep durations are exported in minutes; Spartan models hours.
 */
object WhoopCsvParser {

    /** Detects the file kind from the header and parses it, or null if this isn't a WHOOP CSV. */
    fun parse(text: String): ParsedWhoopFile? {
        val rows = tokenize(text)
        if (rows.isEmpty()) return null
        val header = rows.first().map { it.trim().lowercase() }
        val body = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }
        fun col(name: String): Int = header.indexOf(name)
        fun Row.cell(name: String): String? = col(name).takeIf { it in indices }?.let { this[it].trim().ifEmpty { null } }

        return when {
            "question text" in header -> ParsedWhoopFile(
                kind = WhoopCsvKind.JOURNAL_ENTRIES,
                journal = body.mapNotNull { row ->
                    val start = row.cell("cycle start time") ?: return@mapNotNull null
                    val question = row.cell("question text") ?: return@mapNotNull null
                    WhoopJournalRow(
                        cycleStartRaw = start,
                        dateEpochDay = dateTime(start)?.toLocalDate()?.toEpochDay() ?: return@mapNotNull null,
                        questionText = question,
                        answeredYes = row.cell("answered yes")?.equals("true", ignoreCase = true) == true,
                    )
                },
            )

            "workout start time" in header -> ParsedWhoopFile(
                kind = WhoopCsvKind.WORKOUTS,
                workouts = body.mapNotNull { row ->
                    val start = dateTime(row.cell("workout start time") ?: return@mapNotNull null)
                    val duration = row.cell("duration (min)")?.toDoubleOrNull()?.toInt()
                        ?: return@mapNotNull null
                    WhoopWorkoutRow(
                        dateEpochDay = (start ?: return@mapNotNull null).toLocalDate().toEpochDay(),
                        startMinuteOfDay = start.toLocalTime().let { it.hour * 60 + it.minute },
                        durationMinutes = duration,
                        activityName = row.cell("activity name") ?: "Activity",
                        strain = row.cell("activity strain")?.toDoubleOrNull(),
                        energyKcal = row.cell("energy burned (cal)")?.toDoubleOrNull(),
                        maxHr = row.cell("max hr (bpm)")?.toDoubleOrNull()?.toInt(),
                        averageHr = row.cell("average hr (bpm)")?.toDoubleOrNull()?.toInt(),
                        hrZonePercents = (1..5).map { z -> row.cell("hr zone $z %")?.toDoubleOrNull() },
                    )
                },
            )

            "nap" in header -> ParsedWhoopFile(
                kind = WhoopCsvKind.SLEEPS,
                sleeps = body.mapNotNull { row ->
                    val cycleStart = row.cell("cycle start time") ?: return@mapNotNull null
                    val onset = dateTime(row.cell("sleep onset"))
                    val wake = dateTime(row.cell("wake onset"))
                    val day = (wake ?: onset ?: dateTime(cycleStart))?.toLocalDate()?.toEpochDay()
                        ?: return@mapNotNull null
                    WhoopSleepRow(
                        cycleStartRaw = cycleStart,
                        dateEpochDay = day,
                        nap = row.cell("nap")?.equals("true", ignoreCase = true) == true,
                        sleepPerformance = row.cell("sleep performance %")?.toDoubleOrNull()?.toInt(),
                        respiratoryRate = row.cell("respiratory rate (rpm)")?.toDoubleOrNull(),
                        sleepDurationHours = minutesToHours(row.cell("asleep duration (min)")),
                        sleepDebtHours = minutesToHours(row.cell("sleep debt (min)")),
                        bedMinuteOfDay = onset?.minuteOfDay(),
                        wakeMinuteOfDay = wake?.minuteOfDay(),
                    )
                },
            )

            "recovery score %" in header -> ParsedWhoopFile(
                kind = WhoopCsvKind.PHYSIOLOGICAL_CYCLES,
                cycles = body.mapNotNull { row ->
                    val cycleStart = row.cell("cycle start time") ?: return@mapNotNull null
                    val startAt = dateTime(cycleStart)
                    val onset = dateTime(row.cell("sleep onset"))
                    val wake = dateTime(row.cell("wake onset"))
                    // The recovery in a cycle describes the day the user woke into.
                    val day = (wake ?: startAt)?.toLocalDate()?.toEpochDay() ?: return@mapNotNull null
                    WhoopCycleRow(
                        cycleStartRaw = cycleStart,
                        dateEpochDay = day,
                        recoveryScore = row.cell("recovery score %")?.toDoubleOrNull()?.toInt(),
                        restingHeartRate = row.cell("resting heart rate (bpm)")?.toDoubleOrNull(),
                        hrvMs = row.cell("heart rate variability (ms)")?.toDoubleOrNull(),
                        dayStrain = row.cell("day strain")?.toDoubleOrNull(),
                        energyKcal = row.cell("energy burned (cal)")?.toDoubleOrNull(),
                        sleepPerformance = row.cell("sleep performance %")?.toDoubleOrNull()?.toInt(),
                        respiratoryRate = row.cell("respiratory rate (rpm)")?.toDoubleOrNull(),
                        sleepDurationHours = minutesToHours(row.cell("asleep duration (min)")),
                        sleepDebtHours = minutesToHours(row.cell("sleep debt (min)")),
                        bedMinuteOfDay = onset?.minuteOfDay(),
                        wakeMinuteOfDay = wake?.minuteOfDay(),
                    )
                },
            )

            else -> null
        }
    }

    /** Minutes → hours, rounded to 2 decimals so 83 min reads as 1.38 h, not 1.3833333333333333. */
    private fun minutesToHours(cell: String?): Double? =
        cell?.toDoubleOrNull()?.let { round(it / 60.0 * 100.0) / 100.0 }

    private fun LocalDateTime.minuteOfDay(): Int = hour * 60 + minute

    private fun dateTime(raw: String?): LocalDateTime? {
        val trimmed = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        // Export format "yyyy-MM-dd HH:mm:ss"; tolerate a T separator and fractional seconds.
        return runCatching { LocalDateTime.parse(trimmed.replace(' ', 'T')) }.getOrNull()
    }

    // --- CSV tokenizer (RFC 4180: quoted fields may contain commas, quotes, and newlines) ---

    private fun tokenize(text: String): List<Row> {
        val rows = mutableListOf<Row>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val src = text.removePrefix("\uFEFF")

        fun endField() { row.add(field.toString()); field = StringBuilder() }
        fun endRow() { endField(); rows.add(row); row = mutableListOf() }

        while (i < src.length) {
            val ch = src[i]
            when {
                inQuotes && ch == '"' && i + 1 < src.length && src[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ',' -> endField()
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < src.length && src[i + 1] == '\n') i++
                    endRow()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}

private typealias Row = List<String>
