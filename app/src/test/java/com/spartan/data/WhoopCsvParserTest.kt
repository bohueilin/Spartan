package com.spartan.data

import com.spartan.data.whoop.csv.WhoopCsvKind
import com.spartan.data.whoop.csv.WhoopCsvMerger
import com.spartan.data.whoop.csv.WhoopCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Fixtures mirror the real WHOOP export format exactly (headers, local timestamps, blank cells,
 * minutes-based durations) with synthetic values. Edge rows reproduce real export quirks:
 * no-sleep cycles with blank recovery, nap-only rows, cycles that cross midnight, and an
 * in-progress cycle with no end time.
 */
class WhoopCsvParserTest {

    private val cyclesHeader =
        "Cycle start time,Cycle end time,Cycle timezone,Recovery score %,Resting heart rate (bpm)," +
            "Heart rate variability (ms),Skin temp (celsius),Blood oxygen %,Day Strain,Energy burned (cal)," +
            "Max HR (bpm),Average HR (bpm),Sleep onset,Wake onset,Sleep performance %,Respiratory rate (rpm)," +
            "Asleep duration (min),In bed duration (min),Light sleep duration (min),Deep (SWS) duration (min)," +
            "REM duration (min),Awake duration (min),Sleep need (min),Sleep debt (min),Sleep efficiency %,Sleep consistency %"

    private val sleepsHeader =
        "Cycle start time,Cycle end time,Cycle timezone,Sleep onset,Wake onset,Sleep performance %," +
            "Respiratory rate (rpm),Asleep duration (min),In bed duration (min),Light sleep duration (min)," +
            "Deep (SWS) duration (min),REM duration (min),Awake duration (min),Sleep need (min),Sleep debt (min)," +
            "Sleep efficiency %,Sleep consistency %,Nap"

    private val workoutsHeader =
        "Cycle start time,Cycle end time,Cycle timezone,Workout start time,Workout end time,Duration (min)," +
            "Activity name,Activity Strain,Energy burned (cal),Max HR (bpm),Average HR (bpm)," +
            "HR Zone 1 %,HR Zone 2 %,HR Zone 3 %,HR Zone 4 %,HR Zone 5 %,GPS enabled"

    private val journalHeader = "Cycle start time,Cycle end time,Cycle timezone,Question text,Answered yes,Notes"

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    @Test
    fun parse_detectsAllFourKinds() {
        assertEquals(WhoopCsvKind.PHYSIOLOGICAL_CYCLES, WhoopCsvParser.parse(cyclesHeader)?.kind)
        assertEquals(WhoopCsvKind.SLEEPS, WhoopCsvParser.parse(sleepsHeader)?.kind)
        assertEquals(WhoopCsvKind.WORKOUTS, WhoopCsvParser.parse(workoutsHeader)?.kind)
        assertEquals(WhoopCsvKind.JOURNAL_ENTRIES, WhoopCsvParser.parse(journalHeader)?.kind)
    }

    @Test
    fun parse_unknownCsv_returnsNull() {
        assertNull(WhoopCsvParser.parse("Date,Steps,Calories\n2030-01-10,9000,2100"))
        assertNull(WhoopCsvParser.parse(""))
    }

    @Test
    fun cycles_mapValues_andConvertMinutesToHours() {
        val csv = cyclesHeader + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
            "2030-01-10 00:40:00,2030-01-10 07:40:00,80,14.8,390,420,180,90,120,30,540,90,93,85"
        val row = WhoopCsvParser.parse(csv)!!.cycles.single()
        assertEquals(day("2030-01-10"), row.dateEpochDay)
        assertEquals(75, row.recoveryScore)
        assertEquals(58.0, row.restingHeartRate!!, 0.0)
        assertEquals(82.0, row.hrvMs!!, 0.0)
        assertEquals(9.5, row.dayStrain!!, 0.0)
        assertEquals(2100.0, row.energyKcal!!, 0.0)
        assertEquals(80, row.sleepPerformance)
        assertEquals(14.8, row.respiratoryRate!!, 0.0)
        assertEquals(6.5, row.sleepDurationHours!!, 1e-9) // 390 min
        assertEquals(1.5, row.sleepDebtHours!!, 1e-9) // 90 min
        assertEquals(40, row.bedMinuteOfDay)
        assertEquals(7 * 60 + 40, row.wakeMinuteOfDay)
    }

    // --- WHOOP's own sleep need ("Sleep need (min)") ---------------------------------------

    @Test
    fun cycles_sleepNeedColumn_parsesToHours() {
        val csv = cyclesHeader + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
            "2030-01-10 00:40:00,2030-01-10 07:40:00,80,14.8,390,420,180,90,120,30,540,90,93,85"
        assertEquals(9.0, WhoopCsvParser.parse(csv)!!.cycles.single().sleepNeedHours!!, 1e-9) // 540 min
    }

    @Test
    fun cycles_withoutTheSleepNeedColumn_needIsNull_andEveryOtherCellStillLandsByName() {
        // An export without the column (older, or trimmed): the need is unknown, never a neighbour's value.
        val header = cyclesHeader.replace(",Sleep need (min)", "")
        val csv = header + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
            "2030-01-10 00:40:00,2030-01-10 07:40:00,80,14.8,390,420,180,90,120,30,90,93,85"
        val row = WhoopCsvParser.parse(csv)!!.cycles.single()
        assertNull(row.sleepNeedHours)
        assertEquals(1.5, row.sleepDebtHours!!, 1e-9) // 90 min, read from its own column
        assertEquals(75, row.recoveryScore)
    }

    @Test
    fun cycles_blankSleepNeedCell_isNull() {
        val csv = cyclesHeader + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
            "2030-01-10 00:40:00,2030-01-10 07:40:00,80,14.8,390,420,180,90,120,30,,90,93,85"
        assertNull(WhoopCsvParser.parse(csv)!!.cycles.single().sleepNeedHours)
    }

    @Test
    fun cycles_reorderedColumns_parseByHeaderName() {
        // Same data, columns shuffled (and most of them absent): positions are never assumed.
        val csv = "Sleep need (min),Wake onset,Recovery score %,Cycle start time,Sleep onset," +
            "Asleep duration (min),Sleep performance %,Sleep debt (min)\n" +
            "480,2030-01-10 07:40:00,75,2030-01-10 00:40:00,2030-01-10 00:40:00,390,80,90"
        val parsed = WhoopCsvParser.parse(csv)!!
        assertEquals(WhoopCsvKind.PHYSIOLOGICAL_CYCLES, parsed.kind)
        val row = parsed.cycles.single()
        assertEquals(day("2030-01-10"), row.dateEpochDay)
        assertEquals(75, row.recoveryScore)
        assertEquals(8.0, row.sleepNeedHours!!, 1e-9)
        assertEquals(6.5, row.sleepDurationHours!!, 1e-9)
        assertEquals(1.5, row.sleepDebtHours!!, 1e-9)
        assertEquals(80, row.sleepPerformance)
        assertEquals(40, row.bedMinuteOfDay)
        assertEquals(7 * 60 + 40, row.wakeMinuteOfDay)
        assertNull(row.hrvMs) // a column the file doesn't have
    }

    @Test
    fun sleeps_sleepNeedParsed_withAndWithoutTheColumn() {
        val withNeed = WhoopCsvParser.parse(
            sleepsHeader + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00," +
                "61,16.0,283,304,100,115,68,21,579,126,95,83,false",
        )!!.sleeps.single()
        assertEquals(9.65, withNeed.sleepNeedHours!!, 1e-9) // 579 min, rounded to 2 decimals
        assertEquals(2.1, withNeed.sleepDebtHours!!, 1e-9) // 126 min

        val withoutNeed = WhoopCsvParser.parse(
            sleepsHeader.replace(",Sleep need (min)", "") + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00," +
                "61,16.0,283,304,100,115,68,21,126,95,83,false",
        )!!.sleeps.single()
        assertNull(withoutNeed.sleepNeedHours)
        assertEquals(2.1, withoutNeed.sleepDebtHours!!, 1e-9)
        assertFalse(withoutNeed.nap)
    }

    @Test
    fun merger_sleepNeed_prefersTheMainSleep_overTheNapAndTheCycleRow() {
        val cycles = WhoopCsvParser.parse(
            cyclesHeader + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
                "2030-01-10 19:29:00,2030-01-10 20:27:00,80,14.8,390,420,180,90,120,30,540,90,93,85",
        )!!
        val sleeps = WhoopCsvParser.parse(
            sleepsHeader + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 19:29:00,2030-01-10 20:27:00," +
                "13,15.9,56,58,24,32,0,2,581,127,97,,true\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00," +
                "61,16.0,283,304,100,115,68,21,579,126,95,83,false",
        )!!

        val joined = WhoopCsvMerger.merge(listOf(cycles, sleeps), importedAtMillis = 0L)
        assertEquals(9.65, joined.cycles.single().sleepNeedHours!!, 1e-9) // main sleep, not the nap's 581
        // It reaches the snapshot the plan is built from.
        assertEquals(9.65, joined.snapshots.single().sleepNeedHours!!, 1e-9)

        // Without sleeps.csv the cycle row's own need is used.
        val cyclesOnly = WhoopCsvMerger.merge(listOf(cycles), importedAtMillis = 0L)
        assertEquals(9.0, cyclesOnly.cycles.single().sleepNeedHours!!, 1e-9)

        // A sleeps-only import keeps it too.
        val sleepsOnly = WhoopCsvMerger.merge(listOf(sleeps), importedAtMillis = 0L)
        assertEquals(9.65, sleepsOnly.cycles.single().sleepNeedHours!!, 1e-9)
    }

    @Test
    fun merger_exportWithoutSleepNeed_leavesItNull() {
        val cycles = WhoopCsvParser.parse(
            cyclesHeader.replace(",Sleep need (min)", "") + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
                "2030-01-10 00:40:00,2030-01-10 07:40:00,80,14.8,390,420,180,90,120,30,90,93,85",
        )!!
        val merged = WhoopCsvMerger.merge(listOf(cycles), importedAtMillis = 0L)
        assertNull(merged.cycles.single().sleepNeedHours)
        assertNull(merged.snapshots.single().sleepNeedHours)
    }

    @Test
    fun cycles_noSleepCycle_blanksParseAsNull() {
        // Real exports contain cycles with no recorded sleep: recovery/HRV/sleep cells all blank.
        val csv = cyclesHeader + "\n" +
            "2030-01-12 00:57:00,2030-01-13 00:22:00,UTC-07:00,,,,,,4.5,1500,140,82,,,,,,,,,,,,,,"
        val row = WhoopCsvParser.parse(csv)!!.cycles.single()
        assertEquals(day("2030-01-12"), row.dateEpochDay) // falls back to cycle start date
        assertNull(row.recoveryScore)
        assertNull(row.hrvMs)
        assertNull(row.sleepPerformance)
        assertNull(row.bedMinuteOfDay)
        assertEquals(4.5, row.dayStrain!!, 0.0)
    }

    @Test
    fun cycles_dayIsTheDateTheUserWokeUp() {
        // A cycle that starts before midnight belongs to the NEXT day (the morning it wakes into).
        val csv = cyclesHeader + "\n" +
            "2030-01-14 23:09:00,2030-01-16 00:11:00,UTC-07:00,60,55,70,33.80,94.50,7.0,2000,147,86," +
            "2030-01-14 23:09:00,2030-01-15 05:56:00,85,15.2,398,406,186,92,120,8,569,113,98,90"
        val row = WhoopCsvParser.parse(csv)!!.cycles.single()
        assertEquals(day("2030-01-15"), row.dateEpochDay)
    }

    @Test
    fun cycles_inProgressCycleWithoutEndTime_parses() {
        val csv = cyclesHeader + "\n" +
            "2030-01-20 01:47:00,,UTC-07:00,92,52,88,33.70,97.00,,,,," +
            "2030-01-20 01:47:00,2030-01-20 08:23:00,82,15.9,369,396,176,79,114,27,537,83,93,81"
        val row = WhoopCsvParser.parse(csv)!!.cycles.single()
        assertEquals(92, row.recoveryScore)
        assertNull(row.dayStrain)
    }

    @Test
    fun sleeps_napColumnParsed_bothWays() {
        val csv = sleepsHeader + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 19:29:00,2030-01-10 20:27:00," +
            "13,15.9,56,58,24,32,0,2,581,127,97,,true\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00," +
            "61,16.0,283,304,100,115,68,21,579,126,95,83,false"
        val sleeps = WhoopCsvParser.parse(csv)!!.sleeps
        assertEquals(listOf(true, false), sleeps.map { it.nap })
        assertEquals(2, sleeps.size)
    }

    @Test
    fun workouts_parseDurationsZonesAndHeartRates() {
        val csv = workoutsHeader + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 19:04:00,2030-01-10 19:25:59,21," +
            "Running,11.6,298.0,168,150,6,10,21,20,41,false"
        val w = WhoopCsvParser.parse(csv)!!.workouts.single()
        assertEquals(day("2030-01-10"), w.dateEpochDay)
        assertEquals(19 * 60 + 4, w.startMinuteOfDay)
        assertEquals(21, w.durationMinutes)
        assertEquals("Running", w.activityName)
        assertEquals(11.6, w.strain!!, 0.0)
        assertEquals(168, w.maxHr)
        assertEquals(150, w.averageHr)
        assertEquals(listOf(6.0, 10.0, 21.0, 20.0, 41.0), w.hrZonePercents.map { it!! })
    }

    @Test
    fun journal_quotedNoteWithCommaAndNewline_doesNotBreakRows() {
        val csv = journalHeader + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Consumed caffeine?,true,\"late espresso,\nregretted it\"\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Have any alcoholic drinks?,false,"
        val journal = WhoopCsvParser.parse(csv)!!.journal
        assertEquals(2, journal.size)
        assertTrue(journal.first().answeredYes)
        assertFalse(journal.last().answeredYes)
    }

    // --- merger ---------------------------------------------------------------

    @Test
    fun merger_joinsSleepJournalAndCyclesPerDay() {
        val cycles = WhoopCsvParser.parse(
            cyclesHeader + "\n" +
                // The cycles file lists the NAP window (19:29) as its sleep onset — a real export quirk.
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
                "2030-01-10 19:29:00,2030-01-10 20:27:00,80,14.8,390,420,180,90,120,30,540,90,93,85",
        )!!
        val sleeps = WhoopCsvParser.parse(
            sleepsHeader + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 19:29:00,2030-01-10 20:27:00," +
                "13,15.9,56,58,24,32,0,2,581,127,97,,true\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00," +
                "61,16.0,283,304,100,115,68,21,579,126,95,83,false",
        )!!
        val journal = WhoopCsvParser.parse(
            journalHeader + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Consumed caffeine?,true,\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Have any alcoholic drinks?,false,\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Ate food close to bedtime?,true,",
        )!!

        val merged = WhoopCsvMerger.merge(listOf(cycles, sleeps, journal), importedAtMillis = 42L)
        val dayRecord = merged.cycles.single()
        assertEquals(75, dayRecord.recoveryScore)
        // Bed/wake refined to the MAIN sleep, not the nap the cycles file listed.
        assertEquals(40, dayRecord.bedMinuteOfDay)
        assertEquals(7 * 60 + 18, dayRecord.wakeMinuteOfDay)
        assertEquals(true, dayRecord.journalCaffeine)
        assertEquals(false, dayRecord.journalAlcohol)
        assertEquals(true, dayRecord.journalLateMeal)
        assertEquals(42L, dayRecord.importedAtMillis)

        val snapshot = merged.snapshots.single()
        assertFalse(snapshot.isMock)
        assertEquals(75, snapshot.recoveryScore)
    }

    @Test
    fun merger_dedupesSameDay_keepingTheRicherRecord() {
        val csv = cyclesHeader + "\n" +
            // Sparse cycle waking on Jan 15.
            "2030-01-14 23:09:00,2030-01-15 06:00:00,UTC-07:00,,,,,,3.0,900,120,80," +
            "2030-01-14 23:09:00,2030-01-15 05:56:00,,,,,,,,,,,,\n" +
            // Rich cycle also waking on Jan 15.
            "2030-01-15 06:20:00,2030-01-16 01:12:00,UTC-07:00,44,60,65,32.35,94.00,13.1,2125,143,96," +
            "2030-01-15 06:20:00,2030-01-15 08:09:00,17,16.1,84,108,44,40,0,24,527,127,78,57"
        val merged = WhoopCsvMerger.merge(listOf(WhoopCsvParser.parse(csv)!!), importedAtMillis = 0L)
        val record = merged.cycles.single()
        assertEquals(44, record.recoveryScore)
    }

    @Test
    fun merger_sleepsWithoutCycles_stillProduceSleepDays() {
        val sleeps = WhoopCsvParser.parse(
            sleepsHeader + "\n" +
                "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00," +
                "61,16.0,283,304,100,115,68,21,579,126,95,83,false",
        )!!
        val merged = WhoopCsvMerger.merge(listOf(sleeps), importedAtMillis = 0L)
        val record = merged.cycles.single()
        assertEquals(61, record.sleepPerformance)
        assertNull(record.recoveryScore)
        assertEquals(4.72, record.sleepDurationHours!!, 1e-9) // 283 min, rounded to 2 decimals
    }

    @Test
    fun merger_sumsExerciseMinutesPerDay_andDedupesWorkouts() {
        val csv = workoutsHeader + "\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 14:16:00,2030-01-10 14:43:59,27," +
            "Activity,5.5,137.0,132,119,93,0,0,0,0,false\n" +
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 15:16:00,2030-01-10 15:30:59,14," +
            "Activity,5.1,86.0,143,123,79,9,0,0,0,false\n" +
            // Exact duplicate row (double import of the same file).
            "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 15:16:00,2030-01-10 15:30:59,14," +
            "Activity,5.1,86.0,143,123,79,9,0,0,0,false"
        val merged = WhoopCsvMerger.merge(listOf(WhoopCsvParser.parse(csv)!!), importedAtMillis = 0L)
        assertEquals(2, merged.workouts.size)
        assertEquals(mapOf(LocalDate.parse("2030-01-10").toEpochDay() to 41), merged.exerciseMinutesByDay)
    }
}
