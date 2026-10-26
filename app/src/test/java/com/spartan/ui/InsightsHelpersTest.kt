package com.spartan.ui

import com.spartan.R
import com.spartan.domain.engine.MetricGlossary
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.Trend
import com.spartan.domain.model.Confidence
import com.spartan.domain.model.InsightCard
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.Tone
import com.spartan.ui.screens.HIDDEN_METRICS
import com.spartan.ui.screens.POSITION_TONE
import com.spartan.ui.screens.SignalPill
import com.spartan.ui.screens.chartSeries
import com.spartan.ui.screens.debtTipShown
import com.spartan.ui.screens.insightMeaningRes
import com.spartan.ui.screens.recoveryMeaningRes
import com.spartan.ui.screens.shownLabInsights
import com.spartan.ui.screens.signalPills
import com.spartan.ui.screens.signalStatusRes
import com.spartan.ui.screens.signalZone
import com.spartan.ui.screens.zoneTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The Insights card's pure rules: the status label states the position (never a grade) in a
 * neutral colour that does not grade it either, Recovery is coloured only by its zone and its line
 * follows that zone, a chart shows exactly the readings its trend sentence counts, and "Lab results"
 * shows lab cards only — never a wearable metric's, and never blood pressure, by metric while its
 * data stays stored.
 */
class InsightsHelpersTest {

    private val friday = LocalDate.of(2026, 10, 23)
    private val positions = SignalPosition.entries
    private val placed = listOf(SignalPosition.ABOVE, SignalPosition.WITHIN, SignalPosition.BELOW)

    private fun signal(metric: MetricType, position: SignalPosition, latest: Double = 33.0) =
        Signal(metric, latest, listOf(30.0, latest), 28.0, 38.0, position)

    /** A Recovery reading in each zone: 80% Green, 64% Yellow, 20% Red. */
    private val zoneScores = mapOf(RecoveryZone.GREEN to 80.0, RecoveryZone.YELLOW to 64.0, RecoveryZone.RED to 20.0)

    // --- Status label and tone ---

    @Test
    fun statusLabel_isInYourUsualRange_onlyWhenWithin_otherwiseTheDirection() {
        assertEquals(R.string.signal_within, signalStatusRes(signal(MetricType.HRV_RMSSD, SignalPosition.WITHIN)))
        assertEquals(R.string.signal_above, signalStatusRes(signal(MetricType.HRV_RMSSD, SignalPosition.ABOVE)))
        assertEquals(R.string.signal_below, signalStatusRes(signal(MetricType.HRV_RMSSD, SignalPosition.BELOW)))
        assertEquals(R.string.signal_learning, signalStatusRes(signal(MetricType.HRV_RMSSD, SignalPosition.LEARNING)))
        // The direction is said the same way whichever way is healthy: never "better" or "worse".
        SignalEngine.DAILY.forEach { metric ->
            assertEquals(metric.name, R.string.signal_above, signalStatusRes(signal(metric, SignalPosition.ABOVE)))
            assertEquals(metric.name, R.string.signal_below, signalStatusRes(signal(metric, SignalPosition.BELOW)))
        }
    }

    @Test
    fun positionPill_isNeutral_onEveryMetricInEveryPosition_neverAGradeByColour() {
        // The pill says only the direction, so its colour must not say which way is "good".
        assertEquals(Tone.NEUTRAL, POSITION_TONE)
        SignalEngine.DAILY.forEach { metric ->
            positions.forEach { position ->
                assertEquals("$metric $position", Tone.NEUTRAL, signalPills(signal(metric, position)).first().tone)
            }
        }
        // The two the review found: HRV above its usual was green, resting HR above its usual amber.
        assertEquals(SignalPill(R.string.signal_above, Tone.NEUTRAL), signalPills(signal(MetricType.HRV_RMSSD, SignalPosition.ABOVE)).single())
        assertEquals(SignalPill(R.string.signal_above, Tone.NEUTRAL), signalPills(signal(MetricType.RESTING_HEART_RATE, SignalPosition.ABOVE)).single())
    }

    @Test
    fun recovery_isNeverTealOrGreenByStatus_itsColourIsItsZone() {
        // A 64% (Yellow) recovery's position pill is neutral in every position; only its zone pill has colour.
        positions.forEach { assertEquals(it.name, Tone.NEUTRAL, signalPills(signal(MetricType.RECOVERY_SCORE, it, latest = 64.0)).first().tone) }
        assertEquals(Tone.CAUTION, zoneTone(RecoveryZone.YELLOW))
        assertEquals(Tone.POSITIVE, zoneTone(RecoveryZone.GREEN))
        assertEquals(Tone.ALERT, zoneTone(RecoveryZone.RED))
    }

    // --- The card's zone (its accent) and its pills ---

    @Test
    fun signalZone_isRecoverysZoneForItsLatestReading_andNullForEveryOtherMetric() {
        assertEquals(RecoveryZone.YELLOW, signalZone(signal(MetricType.RECOVERY_SCORE, SignalPosition.WITHIN, latest = 64.0)))
        assertEquals(RecoveryZone.GREEN, signalZone(signal(MetricType.RECOVERY_SCORE, SignalPosition.ABOVE, latest = 66.6))) // rounds to 67
        assertEquals(RecoveryZone.RED, signalZone(signal(MetricType.RECOVERY_SCORE, SignalPosition.BELOW, latest = 33.0)))
        // Recovery's zone does not wait for a usual: the zone is WHOOP's own scale.
        assertEquals(RecoveryZone.YELLOW, signalZone(signal(MetricType.RECOVERY_SCORE, SignalPosition.LEARNING, latest = 50.0)))
        (SignalEngine.DAILY - MetricType.RECOVERY_SCORE).forEach { assertNull(it.name, signalZone(signal(it, SignalPosition.WITHIN, latest = 64.0))) }
    }

    @Test
    fun recoveryPills_areThePositionInNeutral_thenTheZoneWordInTheZonesTone() {
        val above64 = signalPills(signal(MetricType.RECOVERY_SCORE, SignalPosition.ABOVE, latest = 64.0))
        assertEquals(
            listOf(SignalPill(R.string.signal_above, Tone.NEUTRAL), SignalPill(R.string.glossary_zone, Tone.CAUTION, RecoveryZone.YELLOW)),
            above64,
        )
        zoneScores.forEach { (zone, score) ->
            positions.forEach { position ->
                val pills = signalPills(signal(MetricType.RECOVERY_SCORE, position, latest = score))
                assertEquals("$zone $position", 2, pills.size)
                // The only colour is the zone's, and it always comes with the zone's word.
                assertEquals(Tone.NEUTRAL, pills[0].tone)
                assertEquals(SignalPill(R.string.glossary_zone, zoneTone(zone), zone), pills[1])
            }
        }
    }

    @Test
    fun otherSignals_haveOnePill_thePositionInNeutral() {
        (SignalEngine.DAILY - MetricType.RECOVERY_SCORE).forEach { metric ->
            positions.forEach { position ->
                val s = signal(metric, position)
                assertEquals("$metric $position", listOf(SignalPill(signalStatusRes(s), Tone.NEUTRAL)), signalPills(s))
            }
        }
    }

    // --- Plain-language line ---

    @Test
    fun everyMetricPositionAndRecoveryZone_hasItsOwnLine() {
        val others = (SignalEngine.DAILY - MetricType.RECOVERY_SCORE).flatMap { metric -> placed.map { insightMeaningRes(signal(metric, it)) } }
        val recovery = zoneScores.values.flatMap { score -> placed.map { insightMeaningRes(signal(MetricType.RECOVERY_SCORE, it, latest = score)) } }
        val lines = others + recovery
        assertEquals(6 * 3 + 3 * 3, lines.toSet().size)
        assertTrue(R.string.insights_meaning_other !in lines)
        // Learning says the same thing for every metric, and nothing else does.
        SignalEngine.DAILY.forEach { assertEquals(R.string.insights_meaning_learning, insightMeaningRes(signal(it, SignalPosition.LEARNING))) }
        assertTrue(R.string.insights_meaning_learning !in lines)
        assertEquals(R.string.insights_meaning_other, insightMeaningRes(signal(MetricType.ENERGY_KCAL, SignalPosition.WITHIN)))
    }

    @Test
    fun recoveryLine_followsItsZone_asWellAsItsPosition() {
        val green = setOf(
            R.string.insights_meaning_recovery_green_above, R.string.insights_meaning_recovery_green_within, R.string.insights_meaning_recovery_green_below,
        )
        val yellow = setOf(
            R.string.insights_meaning_recovery_yellow_above, R.string.insights_meaning_recovery_yellow_within, R.string.insights_meaning_recovery_yellow_below,
        )
        val red = setOf(
            R.string.insights_meaning_recovery_red_above, R.string.insights_meaning_recovery_red_within, R.string.insights_meaning_recovery_red_below,
        )
        val byZone = mapOf(RecoveryZone.GREEN to green, RecoveryZone.YELLOW to yellow, RecoveryZone.RED to red)
        zoneScores.forEach { (zone, score) ->
            placed.forEach { position ->
                val line = insightMeaningRes(signal(MetricType.RECOVERY_SCORE, position, latest = score))
                assertTrue("$zone $position", line in byZone.getValue(zone))
                assertEquals("$zone $position", recoveryMeaningRes(position, zone), line)
            }
        }
        // The two the review found: 64% above its usual is Yellow's line, 70% below its usual is Green's.
        assertEquals(R.string.insights_meaning_recovery_yellow_above, insightMeaningRes(signal(MetricType.RECOVERY_SCORE, SignalPosition.ABOVE, latest = 64.0)))
        assertEquals(R.string.insights_meaning_recovery_green_below, insightMeaningRes(signal(MetricType.RECOVERY_SCORE, SignalPosition.BELOW, latest = 70.0)))
    }

    @Test
    fun sleepDebtsPaybackTip_onlyAtOrAboveItsUsual() {
        assertTrue(debtTipShown(signal(MetricType.SLEEP_DEBT, SignalPosition.ABOVE)))
        assertTrue(debtTipShown(signal(MetricType.SLEEP_DEBT, SignalPosition.WITHIN)))
        assertFalse(debtTipShown(signal(MetricType.SLEEP_DEBT, SignalPosition.BELOW)))
        assertFalse(debtTipShown(signal(MetricType.SLEEP_DEBT, SignalPosition.LEARNING)))
        (SignalEngine.DAILY - MetricType.SLEEP_DEBT).forEach { metric -> positions.forEach { assertFalse("$metric $it", debtTipShown(signal(metric, it))) } }
    }

    // --- Chart points and their days ---

    @Test
    fun chart_showsTheLastSevenDays_eachPointWithItsOwnDate() {
        val dates = (9 downTo 0).map { friday.minusDays(it.toLong()) }
        val values = (1..10).map { it.toDouble() }
        val series = chartSeries(Signal(MetricType.HRV_RMSSD, 10.0, values, 3.0, 8.0, SignalPosition.ABOVE, dates))
        assertEquals((4..10).map { it.toDouble() }, series.values)
        assertEquals(dates.takeLast(7), series.dates)
        assertEquals(friday.minusDays(6), series.dates.first())
        assertTrue(series.charted)
    }

    @Test
    fun chart_withGapsInWear_chartsOnlyTheWeek_neverAnOlderReadingAsThisWeek() {
        // Readings on Oct 10, 18, 20 and 23 only: Oct 10 is outside the week ending Friday.
        val dates = listOf(10, 18, 20, 23).map { LocalDate.of(2026, 10, it) }
        val series = chartSeries(Signal(MetricType.RECOVERY_SCORE, 64.0, listOf(40.0, 70.0, 81.0, 64.0), null, null, SignalPosition.LEARNING, dates))
        assertEquals(listOf(70.0, 81.0, 64.0), series.values)
        assertEquals(dates.drop(1), series.dates)
    }

    @Test
    fun chart_afterATenDayGap_hasOneReading_andDrawsNoLine() {
        // Ten days of wear (Oct 1–10), then nothing until Oct 20: only Oct 20 is in the week.
        val dates = (1..10).map { LocalDate.of(2026, 10, it) } + LocalDate.of(2026, 10, 20)
        val values = listOf(50.0, 52.0, 54.0, 56.0, 58.0, 60.0, 62.0, 64.0, 66.0, 68.0, 70.0)
        val series = chartSeries(Signal(MetricType.RECOVERY_SCORE, 70.0, values, 54.0, 64.0, SignalPosition.ABOVE, dates))
        assertEquals(listOf(70.0), series.values)
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), series.dates)
        assertFalse(series.charted)
    }

    @Test
    fun chart_coversExactlyTheReadingsTheTrendSentenceCounts() {
        val gappy = listOf(1, 3, 9, 10, 12, 13, 15).map { LocalDate.of(2026, 10, it) }
        val cases = listOf(
            Signal(MetricType.HRV_RMSSD, 7.0, (1..7).map { it.toDouble() }, null, null, SignalPosition.LEARNING, gappy),
            Signal(MetricType.HRV_RMSSD, 9.0, (1..9).map { it.toDouble() }, 3.0, 8.0, SignalPosition.ABOVE), // undated
            Signal(MetricType.HRV_RMSSD, 9.0, (1..9).map { it.toDouble() }, 3.0, 8.0, SignalPosition.ABOVE, listOf(friday)), // mismatched
            Signal(MetricType.SLEEP_DEBT, 0.5, listOf(1.0, 0.9, 0.8, 0.7, 0.6, 0.5), 0.6, 0.9, SignalPosition.BELOW, (5 downTo 0).map { friday.minusDays(it.toLong()) }),
        )
        cases.forEach { s ->
            val series = chartSeries(s)
            assertEquals(MetricGlossary.recentIndices(s, 7).map { s.history[it] }, series.values)
            if (series.dates.isNotEmpty()) {
                // "Over N days" names the span the chart draws.
                assertEquals(MetricGlossary.trendSpanDays(s).toLong(), series.dates.last().toEpochDay() - series.dates.first().toEpochDay() + 1)
            }
        }
        // The gappy week: Oct 9–15 holds five readings, so the trend is named and the chart has those five.
        assertEquals(listOf(3.0, 4.0, 5.0, 6.0, 7.0), chartSeries(cases[0]).values)
        assertEquals(Trend.RISING, MetricGlossary.trend(cases[0]))
    }

    @Test
    fun chart_withoutMatchingDates_keepsTheLine_andGivesNoDayLabels() {
        val values = (1..9).map { it.toDouble() }
        val undated = chartSeries(Signal(MetricType.HRV_RMSSD, 9.0, values, 3.0, 8.0, SignalPosition.ABOVE))
        assertEquals(values.takeLast(7), undated.values)
        assertTrue(undated.dates.isEmpty())
        // Fewer dates than values cannot be matched up: no day is guessed.
        val short = chartSeries(Signal(MetricType.HRV_RMSSD, 9.0, values, 3.0, 8.0, SignalPosition.ABOVE, listOf(friday)))
        assertEquals(values.takeLast(7), short.values)
        assertTrue(short.dates.isEmpty())
        // Nothing to chart, and a non-positive window, are empty rather than a crash.
        assertTrue(chartSeries(Signal(MetricType.HRV_RMSSD, 1.0, emptyList(), null, null, SignalPosition.LEARNING)).values.isEmpty())
        assertTrue(chartSeries(Signal(MetricType.HRV_RMSSD, 1.0, values, null, null, SignalPosition.LEARNING), days = 0).values.isEmpty())
    }

    // --- "Lab results": lab cards only, and blood pressure stays out of the UI ---

    private fun card(title: String, explanation: String, metric: MetricType?) =
        InsightCard(title, explanation, "", emptyList(), emptyList(), Confidence.MODERATE, metric)

    @Test
    fun bloodPressure_isHidden_byTheMetricItsCardIsAbout_notByItsWording() {
        assertEquals(setOf(MetricType.SYSTOLIC_BP, MetricType.DIASTOLIC_BP), HIDDEN_METRICS)
        val systolic = card("Blood pressure", "Your current blood pressure entry is in the normal range.", MetricType.SYSTOLIC_BP)
        val diastolic = card("BP", "Confirm with repeat seated readings.", MetricType.DIASTOLIC_BP) // never says "blood pressure"
        val glucose = card("Fasting glucose", "Your fasting glucose is currently within the common reference range.", MetricType.FASTING_GLUCOSE)
        // A card about another lab that mentions blood pressure in passing still shows.
        val bmi = card("Body metrics", "Read it like blood pressure: trends over single values.", MetricType.BMI)
        val general = card("Metabolic signal", "Worth tracking.", null)
        assertEquals(listOf(glucose, bmi, general), shownLabInsights(listOf(systolic, glucose, diastolic, bmi, systolic, general)))
        assertTrue(shownLabInsights(emptyList()).isEmpty())
    }

    @Test
    fun aWearableMetricsCard_isNeverALabResult_byItsMetric_notByItsTitle() {
        // WHOOP's resting heart rate has its signal card; its population-range card is not a lab result.
        val rhr = card("Resting heart rate", "This is within the normal range for many adults.", MetricType.RESTING_HEART_RATE)
        val renamed = card("Your heart at rest", "Worth tracking.", MetricType.RESTING_HEART_RATE)
        val vitaminD = card("Vitamin D target", "This value is above basic adequacy in many references.", MetricType.VITAMIN_D_25OH)
        assertEquals(listOf(vitaminD), shownLabInsights(listOf(rhr, vitaminD, renamed)))
        // No wearable metric's card shows, whichever one it is about.
        val wearable = SignalEngine.DAILY + MetricType.ENERGY_KCAL + MetricType.SLEEP_DURATION
        assertTrue(shownLabInsights(wearable.map { card(it.label, "", it) }).isEmpty())
    }
}
