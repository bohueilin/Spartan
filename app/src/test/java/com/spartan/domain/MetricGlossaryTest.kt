package com.spartan.domain

import com.spartan.domain.engine.GlossaryEntry
import com.spartan.domain.engine.MetricGlossary
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.Trend
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MetricGlossaryTest {

    private val safety = SafetyEngine()

    private val day0 = LocalDate.of(2026, 10, 1)

    /** A signal whose history is [values], oldest first; the last value is the latest reading. */
    private fun signal(metric: MetricType, vararg values: Double) =
        Signal(metric, values.lastOrNull() ?: 0.0, values.toList(), null, null, SignalPosition.LEARNING)

    /** A dated signal from (day offset, value) pairs, oldest first — gaps are days without a reading. */
    private fun dated(metric: MetricType, vararg readings: Pair<Int, Double>) = Signal(
        metric, readings.last().second, readings.map { it.second }, null, null, SignalPosition.LEARNING,
        dates = readings.map { day0.plusDays(it.first.toLong()) },
    )

    private val entries: List<GlossaryEntry>
        get() = MetricGlossary.coachedMetrics.map { MetricGlossary.entry(it)!! }

    private fun allStrings(entry: GlossaryEntry): List<String> =
        listOfNotNull(entry.name, entry.sentence, entry.showAlongside, entry.populationNote)

    // --- Entries -------------------------------------------------------------------------------

    @Test
    fun coachedMetrics_areTheSevenDailySignals_inGlossaryOrder() {
        assertEquals(
            listOf(
                MetricType.RECOVERY_SCORE,
                MetricType.HRV_RMSSD,
                MetricType.RESTING_HEART_RATE,
                MetricType.SLEEP_PERFORMANCE,
                MetricType.SLEEP_DEBT,
                MetricType.DAY_STRAIN,
                MetricType.RESPIRATORY_RATE,
            ),
            MetricGlossary.coachedMetrics,
        )
    }

    @Test
    fun everyCoachedMetric_hasAMatchingEntry_withNoBlankText() {
        MetricGlossary.coachedMetrics.forEach { metric ->
            val entry = MetricGlossary.entry(metric)
            assertNotNull("expected a glossary entry for $metric", entry)
            assertEquals(metric, entry!!.metric)
            allStrings(entry).forEach { assertTrue("blank text on $metric", it.isNotBlank()) }
        }
        assertEquals("HRV", MetricGlossary.entry(MetricType.HRV_RMSSD)!!.name)
        assertEquals("Strain", MetricGlossary.entry(MetricType.DAY_STRAIN)!!.name)
    }

    @Test
    fun entry_isNullForLabsAndEveryOtherMetric() {
        assertNull(MetricGlossary.entry(MetricType.FASTING_GLUCOSE))
        assertNull(MetricGlossary.entry(MetricType.APOB))
        MetricType.entries.filterNot { it in MetricGlossary.coachedMetrics }.forEach { metric ->
            assertNull("expected no glossary entry for $metric", MetricGlossary.entry(metric))
        }
    }

    @Test
    fun everyString_passesSafetyEngine() {
        entries.forEach { entry ->
            allStrings(entry).forEach { copy ->
                assertTrue("SafetyEngine rejected copy for ${entry.metric}: \"$copy\"", safety.validateCopy(copy))
            }
        }
    }

    @Test
    fun hrv_neverGetsAPopulationGrade_andItsSentenceHasNoNumbers() {
        val hrv = MetricGlossary.entry(MetricType.HRV_RMSSD)!!
        assertNull(hrv.populationNote)
        assertFalse(hrv.hasPopulationGrade)
        assertFalse(Regex("""\d""").containsMatchIn(hrv.sentence))
        assertEquals(
            "How much the gaps between your heartbeats vary — a pattern close to your usual is the goal.",
            hrv.sentence,
        )
        assertTrue(hrv.showAlongside.contains("never a population grade"))
    }

    @Test
    fun populationReferences_existOnlyWhereMeaningful_andDeferToYourOwnUsual() {
        val withNote = entries.filter { it.hasPopulationGrade }.map { it.metric }.toSet()
        assertEquals(
            setOf(MetricType.RESTING_HEART_RATE, MetricType.DAY_STRAIN, MetricType.RESPIRATORY_RATE),
            withNote,
        )
        assertTrue(MetricGlossary.entry(MetricType.RESTING_HEART_RATE)!!.populationNote!!.contains("your own usual matters more"))
        assertTrue(MetricGlossary.entry(MetricType.RESPIRATORY_RATE)!!.populationNote!!.contains("your own steady rate matters more"))
        assertTrue(MetricGlossary.entry(MetricType.DAY_STRAIN)!!.populationNote!!.contains("no universally good strain"))
    }

    @Test
    fun noSentence_usesAFixedEightHourSleepTarget() {
        entries.forEach { entry ->
            listOfNotNull(entry.sentence, entry.populationNote).forEach { copy ->
                assertFalse("fixed sleep target in ${entry.metric}: \"$copy\"", copy.contains("8 hours"))
                assertFalse("fixed sleep target in ${entry.metric}: \"$copy\"", copy.contains("8h"))
            }
        }
        val sleep = MetricGlossary.entry(MetricType.SLEEP_PERFORMANCE)!!
        assertTrue(sleep.showAlongside.contains("hours got vs hours needed"))
        assertTrue(sleep.showAlongside.contains("never a fixed 8 hours"))
    }

    // --- Trend ---------------------------------------------------------------------------------

    @Test
    fun trend_isLearning_untilFourReadingsFallInTheWindow() {
        assertEquals(Trend.LEARNING, MetricGlossary.trend(signal(MetricType.HRV_RMSSD)))
        assertEquals(Trend.LEARNING, MetricGlossary.trend(signal(MetricType.HRV_RMSSD, 40.0, 60.0, 80.0)))
        assertEquals(Trend.RISING, MetricGlossary.trend(signal(MetricType.HRV_RMSSD, 40.0, 40.0, 80.0, 80.0)))
        // A window too short for four readings stays learning however long the history is.
        val long = signal(MetricType.HRV_RMSSD, 40.0, 40.0, 40.0, 80.0, 80.0, 80.0)
        assertEquals(Trend.LEARNING, MetricGlossary.trend(long, window = 3))
        assertEquals(Trend.LEARNING, MetricGlossary.trend(long, window = 0))
        assertEquals(Trend.LEARNING, MetricGlossary.trend(long, window = -2))
    }

    @Test
    fun trend_risesAndFalls_onMovesBeyondThreePercent() {
        // Older half 60, newer half 62: +2 beats the 1.8 (3%) threshold.
        assertEquals(Trend.RISING, MetricGlossary.trend(signal(MetricType.HRV_RMSSD, 60.0, 60.0, 60.0, 61.0, 62.0, 62.0, 62.0)))
        assertEquals(Trend.FALLING, MetricGlossary.trend(signal(MetricType.RESTING_HEART_RATE, 62.0, 62.0, 62.0, 61.0, 58.0, 58.0, 58.0)))
    }

    @Test
    fun trend_isSteady_withinThreePercent_andIgnoresTheOddMiddleReading() {
        // +1 on an older average of 60 is under the 1.8 threshold.
        assertEquals(Trend.STEADY, MetricGlossary.trend(signal(MetricType.HRV_RMSSD, 60.0, 60.0, 60.0, 60.0, 61.0, 61.0, 61.0)))
        // Seven readings split 3 / 3; the spike in the middle belongs to neither half.
        assertEquals(Trend.STEADY, MetricGlossary.trend(signal(MetricType.RECOVERY_SCORE, 50.0, 50.0, 50.0, 99.0, 50.0, 50.0, 50.0)))
    }

    @Test
    fun trend_readsOnlyTheLastWindowReadings() {
        // A big early rise, then a flat last week.
        val s = signal(MetricType.HRV_RMSSD, 40.0, 40.0, 40.0, 70.0, 70.0, 70.0, 70.0, 70.0, 70.0, 70.0)
        assertEquals(Trend.STEADY, MetricGlossary.trend(s))
        assertEquals(Trend.RISING, MetricGlossary.trend(s, window = 10))
        assertEquals(Trend.RISING, MetricGlossary.trend(s, window = 30)) // longer than the history
    }

    @Test
    fun trend_floor_keepsNearZeroValuesFromFlipping() {
        // Older average 0: the per-metric floor alone sets the threshold.
        assertEquals(Trend.RISING, MetricGlossary.trend(signal(MetricType.DAY_STRAIN, 0.0, 0.0, 0.15, 0.15)))
        assertEquals(Trend.FALLING, MetricGlossary.trend(signal(MetricType.RESPIRATORY_RATE, 0.15, 0.15, 0.0, 0.0)))
        assertEquals(Trend.STEADY, MetricGlossary.trend(signal(MetricType.SLEEP_DEBT, 0.0, 0.0, 0.2, 0.2)))
        assertEquals(Trend.RISING, MetricGlossary.trend(signal(MetricType.SLEEP_DEBT, 0.0, 0.0, 0.3, 0.3)))
        assertEquals(Trend.STEADY, MetricGlossary.trend(signal(MetricType.RECOVERY_SCORE, 0.0, 0.0, 0.5, 0.5)))
        // A small-scale metric with a real level still uses 3%: strain 10 → 10.5 beats 0.3.
        assertEquals(Trend.RISING, MetricGlossary.trend(signal(MetricType.DAY_STRAIN, 10.0, 10.0, 10.0, 10.5, 10.5, 10.5)))
    }

    @Test
    fun trend_worksOnSignalsBuiltBySignalEngine() {
        val readings = listOf(58.0, 58.0, 58.0, 59.0, 64.0, 64.0, 64.0).mapIndexed { i, v ->
            MetricReading(MetricType.RESTING_HEART_RATE, v, day0.plusDays(i.toLong()), id = i.toLong())
        }
        val s = SignalEngine.signal(MetricType.RESTING_HEART_RATE, readings)!!
        assertEquals(Trend.RISING, MetricGlossary.trend(s))
        assertEquals(7, MetricGlossary.trendSpanDays(s))
        assertEquals(64.0, MetricGlossary.previousValue(s)!!, 1e-9)
        assertEquals(day0.plusDays(5), MetricGlossary.previousDate(s))
        assertTrue(MetricGlossary.previousIsYesterday(s))
    }

    @Test
    fun trend_withDates_measuresTheWindowInDaysNotReadings() {
        // Seven readings over two weeks: the early high ones are outside the last 7 days.
        val s = dated(MetricType.HRV_RMSSD, 0 to 80.0, 1 to 80.0, 2 to 80.0, 10 to 60.0, 11 to 60.0, 12 to 60.0, 13 to 60.0)
        assertEquals(Trend.STEADY, MetricGlossary.trend(s))
        assertEquals(4, MetricGlossary.trendSpanDays(s)) // days 10–13, not "7 days"
        // The same values without dates would count the last seven readings.
        assertEquals(Trend.FALLING, MetricGlossary.trend(signal(MetricType.HRV_RMSSD, *s.history.toDoubleArray())))
        // A two-week window reaches back to the early readings.
        assertEquals(Trend.FALLING, MetricGlossary.trend(s, window = 14))
        assertEquals(14, MetricGlossary.trendSpanDays(s, window = 14))
    }

    @Test
    fun trend_withDates_staysLearning_whenGapsLeaveTooFewReadings() {
        // A reading every third day: only three land inside the last 7 days.
        val s = dated(MetricType.RESTING_HEART_RATE, 0 to 58.0, 3 to 58.0, 6 to 60.0, 9 to 64.0, 12 to 64.0)
        assertEquals(Trend.LEARNING, MetricGlossary.trend(s))
        assertEquals(7, MetricGlossary.trendSpanDays(s)) // days 6–12
        assertEquals(Trend.LEARNING, MetricGlossary.trend(s, window = 0))
        assertEquals(0, MetricGlossary.trendSpanDays(s, window = 0))
        assertEquals(0, MetricGlossary.trendSpanDays(s, window = -3))
    }

    @Test
    fun trendSpanDays_withoutDates_isTheCountOfReadingsUsed() {
        val s = signal(MetricType.HRV_RMSSD, 60.0, 61.0, 62.0, 63.0, 64.0, 65.0, 66.0, 67.0, 68.0)
        assertEquals(7, MetricGlossary.trendSpanDays(s))
        assertEquals(3, MetricGlossary.trendSpanDays(s, window = 3))
        assertEquals(2, MetricGlossary.trendSpanDays(signal(MetricType.HRV_RMSSD, 60.0, 61.0)))
        assertEquals(0, MetricGlossary.trendSpanDays(signal(MetricType.HRV_RMSSD)))
        assertEquals(0, MetricGlossary.trendSpanDays(s, window = 0))
    }

    @Test
    fun datesThatDoNotMatchTheHistory_areIgnored() {
        // One date for four values cannot say which reading it belongs to: fall back to counting readings.
        val s = Signal(
            MetricType.HRV_RMSSD, 80.0, listOf(40.0, 40.0, 80.0, 80.0), null, null, SignalPosition.LEARNING,
            dates = listOf(day0),
        )
        assertEquals(Trend.RISING, MetricGlossary.trend(s))
        assertEquals(4, MetricGlossary.trendSpanDays(s))
        assertNull(MetricGlossary.previousDate(s))
        assertFalse(MetricGlossary.previousIsYesterday(s))
    }

    // --- Previous value ------------------------------------------------------------------------

    @Test
    fun previousValue_isTheReadingBeforeTheLatest() {
        assertEquals(60.0, MetricGlossary.previousValue(signal(MetricType.HRV_RMSSD, 55.0, 60.0, 72.0))!!, 1e-9)
        assertEquals(60.0, MetricGlossary.previousValue(signal(MetricType.HRV_RMSSD, 60.0, 72.0))!!, 1e-9)
        assertNull(MetricGlossary.previousValue(signal(MetricType.HRV_RMSSD, 72.0)))
        assertNull(MetricGlossary.previousValue(signal(MetricType.HRV_RMSSD)))
    }

    @Test
    fun previousIsYesterday_onlyWhenItIsDatedTheDayBeforeTheLatest() {
        val daily = dated(MetricType.HRV_RMSSD, 4 to 55.0, 5 to 60.0, 6 to 72.0)
        assertEquals(day0.plusDays(5), MetricGlossary.previousDate(daily))
        assertTrue(MetricGlossary.previousIsYesterday(daily))

        // A night off the strap: the previous reading is from two days back, so it is not "yesterday".
        val gap = dated(MetricType.HRV_RMSSD, 3 to 55.0, 4 to 60.0, 6 to 72.0)
        assertEquals(60.0, MetricGlossary.previousValue(gap)!!, 1e-9)
        assertEquals(day0.plusDays(4), MetricGlossary.previousDate(gap))
        assertFalse(MetricGlossary.previousIsYesterday(gap))

        // One reading, or no dates at all: nothing can honestly be called yesterday.
        assertNull(MetricGlossary.previousDate(dated(MetricType.HRV_RMSSD, 6 to 72.0)))
        assertFalse(MetricGlossary.previousIsYesterday(dated(MetricType.HRV_RMSSD, 6 to 72.0)))
        assertNull(MetricGlossary.previousDate(signal(MetricType.HRV_RMSSD, 60.0, 72.0)))
        assertFalse(MetricGlossary.previousIsYesterday(signal(MetricType.HRV_RMSSD, 60.0, 72.0)))
    }
}
