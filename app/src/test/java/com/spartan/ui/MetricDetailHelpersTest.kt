package com.spartan.ui

import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.ui.screens.detailChartSeries
import com.spartan.ui.screens.detailDayLabels
import com.spartan.ui.screens.improveActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Metric detail on the JVM: what its chart charts, the day under each point, and which actions it lists. */
class MetricDetailHelpersTest {

    private val friday = LocalDate.of(2026, 10, 23)

    @Test
    fun aDailySignal_chartsItsOwnHistory_withItsDays() {
        val dates = (5 downTo 0).map { friday.minusDays(it.toLong()) }
        val signal = Signal(MetricType.HRV_RMSSD, 80.0, listOf(60.0, 62.0, 64.0, 66.0, 68.0, 80.0), 62.0, 66.0, SignalPosition.ABOVE, dates)
        // The raw entries are ignored: a re-sync's duplicate day is already folded into the signal.
        val series = detailChartSeries(signal, listOf(MetricReading(MetricType.HRV_RMSSD, 1.0, friday)))
        assertEquals(signal.history, series.values)
        assertEquals(dates, series.dates)
        assertTrue(series.charted)
        // Days that do not line up one per value are dropped, never guessed.
        assertTrue(detailChartSeries(signal.copy(dates = listOf(friday)), emptyList()).dates.isEmpty())
    }

    @Test
    fun aLab_chartsItsLastThirtyEntries_oldestFirst_withTheDayEachWasRecorded() {
        val entries = (0 until 35).map { MetricReading(MetricType.FASTING_GLUCOSE, 80.0 + it, friday.minusDays(35L - it), id = it.toLong()) } +
            MetricReading(MetricType.FASTING_GLUCOSE, null, friday) // a pending entry has no point
        val series = detailChartSeries(null, entries)
        assertEquals((5 until 35).map { 80.0 + it }, series.values)
        assertEquals((5 until 35).map { friday.minusDays(35L - it) }, series.dates)
        assertFalse(detailChartSeries(null, entries.take(1)).charted) // one entry draws no line
    }

    @Test
    fun howToImproveIt_listsEveryActionButTheStickyBars() {
        val hrv = ActionPlaybook.forMetric(MetricType.HRV_RMSSD)
        val listed = improveActions(hrv)
        assertFalse(hrv.first() in listed)
        assertEquals(hrv.drop(1), listed)
        // One action: the sticky bar holds it and nothing is left to list; none: nothing at all.
        assertTrue(improveActions(ActionPlaybook.forMetric(MetricType.VITAMIN_D_25OH)).isEmpty())
        assertTrue(improveActions(ActionPlaybook.forMetric(MetricType.APOB)).isEmpty())
    }

    @Test
    fun dayLabels_areWeekdaysWithinAWeek_andDatesOverLongerSpans_withToday() {
        val week = (6 downTo 0).map { friday.minusDays(it.toLong()) }
        assertEquals(listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Today"), detailDayLabels(week, friday, "Today", Locale.US))
        // A month of readings: a weekday name would repeat, so each point is named by its date.
        val month = (30 downTo 0).map { friday.minusDays(it.toLong()) }
        val labels = detailDayLabels(month, friday.plusDays(2), "Today", Locale.US)
        assertEquals("Sep 23", labels.first())
        assertEquals("Oct 23", labels.last())
        assertEquals(month.size, labels.toSet().size)
        assertTrue(detailDayLabels(emptyList(), friday, "Today", Locale.US).isEmpty())
    }
}
