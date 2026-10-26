package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.test.core.app.ApplicationProvider
import com.spartan.R
import com.spartan.domain.engine.InsightEngine
import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.model.ClinicalStatus
import com.spartan.domain.model.Confidence
import com.spartan.domain.model.InsightCard
import com.spartan.domain.model.MetricAssessment
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TargetStatus
import com.spartan.ui.components.ChartDrawSession
import com.spartan.ui.components.Tone
import com.spartan.ui.components.toneColor
import com.spartan.ui.screens.AddMetricScreen
import com.spartan.ui.screens.GlossaryFacts
import com.spartan.ui.screens.INSIGHTS_USUAL_NOTE_TAG
import com.spartan.ui.screens.InsightsScreen
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.SIGNAL_FACTS
import com.spartan.ui.screens.SIGNAL_LINE
import com.spartan.ui.screens.SIGNAL_PILL
import com.spartan.ui.screens.SIGNAL_VALUE
import com.spartan.ui.screens.formatMetricValue
import com.spartan.ui.screens.formatNumber
import com.spartan.ui.screens.glossaryFacts
import com.spartan.ui.screens.signalAccent
import com.spartan.ui.screens.signalCardTag
import com.spartan.ui.screens.signalStatus
import com.spartan.ui.screens.zoneTone
import com.spartan.ui.theme.SpartanTheme
import com.spartan.ui.theme.metricAccent
import com.spartan.ui.theme.zoneColor
import com.spartan.ui.theme.zoneMarkColor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Insights on the JVM, with the real data from spec §1 (never the AI images' numbers). Each
 * signal card speaks its numbers once, through its chart — value, usual and trend in one sentence
 * — and Recovery 64% reads Yellow, never "Good", even above its usual. The usual's numbers are on
 * the card whether or not a chart can be drawn. Blood pressure is hidden (owner decision): a
 * screen holding both halves shows neither and still scrolls end to end (two "Blood pressure"
 * cards once crashed it with a duplicate LazyColumn key, found on a Pixel). WHOOP's resting heart
 * rate is a signal card and never also a "Lab results" card. A usual range whose two ends round to
 * one value reads as that value, never "78–78 ms". A position pill — on Insights, Today and metric
 * detail alike — is neutral: its words give the direction, and no colour grades it.
 *
 * The card's value block and each chart are one TalkBack node (clearAndSetSemantics), so their
 * visible parts are read from the unmerged tree by test tag ([signalCardTag]) — where they still
 * are — and every "none of" check runs where a matching text has been shown to appear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InsightsScreenTest {

    private val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** As in CalmThemeTest: release-variant unit tests have no ui-test-manifest activity entry. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Context>()
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = app.packageName
                },
            )
        }
    }).around(composeRule)

    @After
    fun forgetDrawnCharts() = ChartDrawSession.reset()

    // Spec §1: data through Fri Oct 23; the week Sat Oct 17 – Fri Oct 23.
    private val week = (6 downTo 0).map { LocalDate.of(2026, 10, 23).minusDays(it.toLong()) }
    private val recovery = Signal(MetricType.RECOVERY_SCORE, 64.0, listOf(52.0, 55.0, 58.0, 81.0, 60.0, 62.0, 64.0), 49.0, 78.0, SignalPosition.WITHIN, week)
    private val hrv = Signal(MetricType.HRV_RMSSD, 33.0, listOf(29.0, 30.0, 31.0, 30.0, 32.0, 31.0, 33.0), 28.0, 38.0, SignalPosition.WITHIN, week)
    private val sleep = Signal(MetricType.SLEEP_PERFORMANCE, 77.0, listOf(78.0, 76.0, 77.0, 79.0, 76.0, 78.0, 77.0), 73.0, 83.0, SignalPosition.WITHIN, week)
    private val debt = Signal(MetricType.SLEEP_DEBT, 44 / 60.0, listOf(1.6, 1.5, 1.4, 1.0, 0.9, 0.8, 44 / 60.0), 34 / 60.0, 103 / 60.0, SignalPosition.WITHIN, week)
    private val realData = MainUiState(signals = listOf(recovery, hrv, sleep, debt), sleptHours = 7 + 34 / 60.0, sleepNeedHours = 9 + 50 / 60.0)

    private fun show(state: MainUiState) {
        composeRule.setContent {
            SpartanTheme { InsightsScreen(state, onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {}) }
        }
    }

    private fun string(id: Int): String = context.getString(id)

    /** Every text and spoken description on screen, from the unmerged tree. */
    private fun everythingSaid(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }

    /** The texts of the card part tagged [signalCardTag] ([metric], [part]) — the node itself or inside it — in order. */
    private fun shown(metric: MetricType, part: String): List<String> {
        val tag = signalCardTag(metric, part)
        return composeRule.onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    }

    // --- Signal cards ---

    @Test
    fun everySignalCard_speaksItsValueUsualAndTrend_onceThroughItsChart() {
        show(realData)
        listOf(
            "Recovery 64%, Yellow zone, within your usual of 49 to 78%, rising over 7 days",
            "HRV 33 ms, within your usual of 28 to 38 ms, rising over 7 days",
            "Sleep performance 77%, within your usual of 73 to 83%, steady over 7 days, 7h 34m of about 9h 50m needed",
            "Sleep debt 44m, within your usual of 34m to 1h 43m, falling over 7 days",
        ).forEach { summary ->
            composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(summary, substring = true))
            // One node says it — the chart — never a second copy on the value block.
            val speakers = composeRule.onAllNodes(hasContentDescription(summary, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(summary, 1, speakers.size)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun recovery64_inItsUsual_readsYellow_withTheYellowLine() {
        show(MainUiState(signals = listOf(recovery)))
        assertEquals(listOf("In your usual range", "Yellow zone"), shown(MetricType.RECOVERY_SCORE, SIGNAL_PILL))
        assertEquals(listOf(string(R.string.insights_meaning_recovery_yellow_within)), shown(MetricType.RECOVERY_SCORE, SIGNAL_LINE))
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun recovery64_aboveItsUsual_isStillYellow_neverAGoodDay() {
        // Usual 40–60%: 64% is above it, and still a Yellow-zone score.
        val above = recovery.copy(normalLow = 40.0, normalHigh = 60.0, position = SignalPosition.ABOVE)
        show(MainUiState(signals = listOf(above)))
        assertEquals(listOf("Above your usual", "Yellow zone"), shown(MetricType.RECOVERY_SCORE, SIGNAL_PILL))
        assertEquals(listOf(string(R.string.insights_meaning_recovery_yellow_above)), shown(MetricType.RECOVERY_SCORE, SIGNAL_LINE))
        val said = everythingSaid()
        assertTrue(said.toString(), said.any { "Recovery 64%, Yellow zone, above your usual of 40 to 60%" in it })
        // The pills, the facts, the line and the chart's summary are all in [said]: none grades the day.
        listOf("good", "better", "quality").forEach { word -> assertTrue(word, said.none { it.contains(word, ignoreCase = true) }) }
    }

    @Test
    fun signalAccent_isRecoverysZoneMark_neverTealOrGrey_andEachOtherMetricsOwn() {
        val recoveryAccent = mutableMapOf<RecoveryZone, Pair<Color, Color>>()
        val pills = mutableMapOf<RecoveryZone, Pair<Color, Color>>()
        var teal = Color.Unspecified
        var grey = Color.Unspecified
        var hrvAccent = Color.Unspecified
        var hrvOwn = Color.Unspecified
        composeRule.setContent {
            SpartanTheme {
                mapOf(RecoveryZone.GREEN to 80.0, RecoveryZone.YELLOW to 64.0, RecoveryZone.RED to 20.0).forEach { (zone, score) ->
                    recoveryAccent[zone] = signalAccent(recovery.copy(latest = score)) to zoneMarkColor(zone)
                    pills[zone] = toneColor(zoneTone(zone)) to zoneColor(zone)
                }
                teal = MaterialTheme.colorScheme.primary
                grey = MaterialTheme.colorScheme.onSurfaceVariant
                hrvAccent = signalAccent(hrv)
                hrvOwn = metricAccent(MetricType.HRV_RMSSD)
            }
        }
        composeRule.waitForIdle()
        recoveryAccent.forEach { (zone, colours) ->
            assertEquals(zone.name, colours.second, colours.first)
            assertNotEquals(zone.name, teal, colours.first)
            assertNotEquals(zone.name, grey, colours.first)
        }
        assertEquals(hrvOwn, hrvAccent)
        // The zone pill is the zone's own text colour, for every zone.
        pills.forEach { (zone, colours) -> assertEquals(zone.name, colours.second, colours.first) }
    }

    @Test
    fun positionStatus_isNeutral_wherePillsAreShown_soColourNeverGradesTheDirection() {
        // signalStatus feeds Today's driver tiles and metric detail's pill as well as these cards.
        val rhr = Signal(MetricType.RESTING_HEART_RATE, 70.0, listOf(60.0, 61.0, 59.0, 60.0, 62.0, 70.0), 60.0, 61.0, SignalPosition.ABOVE, week.drop(1))
        val cases = listOf(
            hrv.copy(position = SignalPosition.ABOVE), hrv.copy(position = SignalPosition.BELOW), hrv,
            rhr, rhr.copy(position = SignalPosition.BELOW), recovery.copy(position = SignalPosition.ABOVE),
        )
        val tones = arrayOfNulls<Pair<String, Tone>>(cases.size)
        composeRule.setContent { SpartanTheme { cases.forEachIndexed { i, signal -> tones[i] = signalStatus(signal) } } }
        composeRule.waitForIdle()
        val said = tones.map { it!! }
        assertEquals(listOf("Above your usual", "Below your usual", "In your usual range", "Above your usual", "Below your usual", "Above your usual"), said.map { it.first })
        said.forEach { (label, tone) -> assertEquals(label, Tone.NEUTRAL, tone) }
    }

    @Test
    fun aCardsValue_isANumber_notASecondHeadline() {
        var numberSize = TextUnit.Unspecified
        composeRule.setContent {
            SpartanTheme {
                numberSize = MaterialTheme.typography.headlineSmall.fontSize
                InsightsScreen(MainUiState(signals = listOf(hrv)), onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {})
            }
        }
        val node = composeRule.onNodeWithTag(signalCardTag(MetricType.HRV_RMSSD, SIGNAL_VALUE), useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(it) }.single()
        assertEquals("33 ms", layout.layoutInput.text.text)
        assertEquals(numberSize, layout.layoutInput.style.fontSize)
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp") // the whole list composed, so a lab card cannot hide off-screen
    fun aWhoopRestingHeartRate_isASignalCard_neverALabResult() {
        // Six nights of WHOOP resting heart rate: assessed and given an insight card like any reading.
        val nights = listOf(58.0, 60.0, 59.0, 61.0, 60.0, 64.0).mapIndexed { i, v -> MetricReading(MetricType.RESTING_HEART_RATE, v, week[i + 1], id = i.toLong()) }
        val assessments = listOf(MetricEngine().assess(nights.last()))
        val insights = InsightEngine().generate(assessments)
        val rhrCard = insights.single()
        assertEquals(MetricType.RESTING_HEART_RATE, rhrCard.metric)
        val signal = SignalEngine.signal(MetricType.RESTING_HEART_RATE, nights)!!
        show(MainUiState(signals = listOf(signal), readings = nights, assessments = assessments, insights = insights))

        // Its one card is the signal card; "Lab results" holds only the empty state.
        assertEquals(1, composeRule.onAllNodesWithText("Resting heart rate", useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(string(R.string.insights_labs_empty)).assertExists()
        val said = everythingSaid()
        assertTrue(said.toString(), said.any { "Resting heart rate 64 bpm" in it })
        // None of the lab card's population range or care prompt — those live in metric detail.
        (listOf(rhrCard.explanation, rhrCard.whyItMatters) + rhrCard.clinicianTriggers).forEach { text -> assertTrue(text, text !in said) }
        assertTrue(said.none { it.contains("60–100") || it.contains("chest pain", ignoreCase = true) })
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp") // every card on screen at once, so a repeat would be seen
    fun tryActions_neverRepeatAcrossTheCards() {
        val respiratory = Signal(MetricType.RESPIRATORY_RATE, 15.0, listOf(14.8, 15.0, 14.9, 15.1, 15.0, 14.9, 15.0), 14.9, 15.1, SignalPosition.WITHIN, week)
        show(realData.copy(signals = listOf(recovery, hrv, sleep, debt, respiratory)))
        fun count(title: String) = composeRule.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(1, count("Sleep wind-down routine")) // sleep performance keeps it
        assertEquals(1, count("Caffeine cutoff at 2 PM")) // sleep debt's own lever
        assertEquals(1, count("5-minute breathing protocol")) // HRV keeps it
        assertEquals(1, count("Front-load your water")) // respiratory rate's next lever, not HRV's breathing again
        assertEquals(1, count("10-minute mobility flow"))
        assertEquals(5, count("TRY"))
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun learningSignal_saysLearningOnce_withOneDayCount_andHasNoBand() {
        val learning = Signal(MetricType.HRV_RMSSD, 40.0, listOf(38.0, 40.0), null, null, SignalPosition.LEARNING)
        lateinit var facts: GlossaryFacts
        composeRule.setContent {
            SpartanTheme {
                facts = glossaryFacts(learning, MainUiState())
                // Recovery alongside: its chart has a band legend, so the no-legend check below can see one.
                InsightsScreen(MainUiState(signals = listOf(recovery, learning)), onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {})
            }
        }
        assertNull(facts.usualLabel)
        assertEquals("1 of 5 days so far", facts.factsLine)
        assertEquals("HRV 40 ms, Spartan is still learning your usual, 1 of 5 days", facts.cardSummary)
        // On the card: "learning" once (the pill), the day count once (the line under it), and a plain line with neither.
        val card = shown(MetricType.HRV_RMSSD, SIGNAL_PILL) + shown(MetricType.HRV_RMSSD, SIGNAL_FACTS) + shown(MetricType.HRV_RMSSD, SIGNAL_LINE)
        assertEquals(listOf("Learning your usual", "1 of 5 days so far", string(R.string.insights_meaning_learning)), card)
        assertEquals(1, card.count { it.contains("learn", ignoreCase = true) })
        assertEquals(1, card.count { Regex("""\d""").containsMatchIn(it) })
        // TalkBack hears the count once, in the card's one sentence.
        assertEquals(1, composeRule.onAllNodes(hasContentDescription("still learning your usual", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        // Only Recovery's band is labelled: the learning card has no legend.
        assertEquals(listOf("Your usual 49–78%"), everythingSaid().filter { Regex("""^Your usual \d""").containsMatchIn(it) })
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun aLearnedUsualWithoutAChart_isShownAsText_afterATenDayGap() {
        // Ten days of wear (Oct 1–10), then nothing until Oct 20: one reading in the week, so no chart.
        val days = (1..10).map { LocalDate.of(2026, 10, it) } + LocalDate.of(2026, 10, 20)
        val values = listOf(40.0, 42.0, 44.0, 46.0, 48.0, 50.0, 52.0, 54.0, 56.0, 58.0, 64.0)
        val readings = values.mapIndexed { i, v -> MetricReading(MetricType.RECOVERY_SCORE, v, days[i], id = i.toLong()) }
        val gap = SignalEngine.signal(MetricType.RECOVERY_SCORE, readings)!!
        assertEquals(SignalPosition.ABOVE, gap.position) // usual 44.5–53.5 from the ten earlier days
        lateinit var facts: GlossaryFacts
        composeRule.setContent {
            SpartanTheme {
                facts = glossaryFacts(gap, MainUiState())
                InsightsScreen(MainUiState(signals = listOf(gap)), onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {})
            }
        }
        assertEquals("Your usual 45–54%", facts.usualLabel)
        assertTrue(facts.factsLine, facts.factsLine!!.startsWith("Your usual 45–54%"))
        // Visible on the card, beside "Above your usual" and "Yellow zone" — not only to TalkBack.
        assertEquals(listOf("Above your usual", "Yellow zone"), shown(MetricType.RECOVERY_SCORE, SIGNAL_PILL))
        assertEquals(listOf(facts.factsLine), shown(MetricType.RECOVERY_SCORE, SIGNAL_FACTS))
        // No chart, so the value block is the one node that says the numbers.
        val summary = "Recovery 64%, Yellow zone, above your usual of 45 to 54%"
        assertEquals(1, composeRule.onAllNodes(hasContentDescription(summary, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun sleepDebtsPaybackTip_followsTheLine_onlyAtOrAboveItsUsual() {
        val tip = string(R.string.glossary_debt_payback)
        var shownDebt by mutableStateOf(debt)
        composeRule.setContent {
            SpartanTheme { InsightsScreen(MainUiState(signals = listOf(shownDebt)), onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {}) }
        }
        mapOf(
            debt.copy(position = SignalPosition.ABOVE) to true,
            debt to true,
            debt.copy(position = SignalPosition.BELOW) to false,
            debt.copy(position = SignalPosition.LEARNING, normalLow = null, normalHigh = null) to false,
        ).forEach { (signal, withTip) ->
            composeRule.runOnIdle { shownDebt = signal }
            composeRule.waitForIdle()
            val line = shown(MetricType.SLEEP_DEBT, SIGNAL_LINE).single()
            assertEquals("${signal.position}: $line", withTip, line.endsWith(tip))
        }
    }

    // --- Where "your usual" comes from ---

    @Test
    fun usualNote_isOneSentence_justUnderTheSignalsHeader_aboveTheCards() {
        show(realData)
        val header = composeRule.onNodeWithText(string(R.string.insights_signals)).getUnclippedBoundsInRoot()
        val note = composeRule.onNodeWithTag(INSIGHTS_USUAL_NOTE_TAG)
            .assertTextEquals("Your usual is the middle half of your own recent daily readings (up to 30), so one odd night barely moves it.")
            .getUnclippedBoundsInRoot()
        val firstCard = composeRule.onAllNodesWithText("Recovery", useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
        assertTrue("$header / $note", header.bottom <= note.top)
        assertTrue("$note / $firstCard", note.bottom <= firstCard.top)
    }

    // --- Usual ranges ---

    private fun hrvFacts(low: Double, high: Double): GlossaryFacts {
        val hrv = Signal(MetricType.HRV_RMSSD, 58.0, listOf(78.0, 70.0, 66.0, 75.0, 72.0, 58.0), low, high, SignalPosition.BELOW)
        lateinit var facts: GlossaryFacts
        composeRule.setContent {
            SpartanTheme {
                facts = glossaryFacts(hrv, MainUiState())
                InsightsScreen(MainUiState(signals = listOf(hrv)), onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {})
            }
        }
        composeRule.waitForIdle()
        return facts
    }

    @Test
    fun collapsedUsual_readsAsOneValue() {
        val facts = hrvFacts(low = 78.0, high = 78.0)

        assertEquals("Your usual 78 ms", facts.usualLabel) // the chart band's legend
        listOfNotNull(facts.usualLine, facts.factsLine, facts.summary, facts.cardSummary).forEach {
            assertFalse(it, "78–78" in it || "78 to 78" in it)
        }
        composeRule.onNode(hasContentDescription("HRV 58 ms, below your usual of 78 ms", substring = true)).assertExists()
    }

    @Test
    fun spreadUsual_keepsTheRange_andTheChartLabelsItsBand() {
        val facts = hrvFacts(low = 62.0, high = 78.0)

        assertEquals("Your usual 62–78 ms", facts.usualLabel)
        composeRule.onNode(hasContentDescription("HRV 58 ms, below your usual of 62 to 78 ms", substring = true)).assertExists()
        // The chart draws the band's legend with its numbers (silent to TalkBack, which hears the summary).
        assertTrue(everythingSaid().contains("Your usual 62–78 ms"))
        // With a chart, the line under the value leaves the usual to the legend.
        assertFalse(facts.factsLine.orEmpty(), facts.factsLine.orEmpty().contains("Your usual"))
    }

    @Test
    fun valuesReadTheSameEverywhere() {
        assertEquals("1h 48m", formatMetricValue(1.8, MetricType.SLEEP_DEBT))
        // The low end of a sleep-debt range uses the same form as the high end.
        assertEquals("12m", formatNumber(0.2, MetricType.SLEEP_DEBT))
        assertEquals("1 session", formatMetricValue(1.0, MetricType.STRENGTH_SESSIONS))
        assertEquals("3 sessions", formatMetricValue(3.0, MetricType.STRENGTH_SESSIONS))
    }

    // --- Blood pressure stays out of the UI ---

    private fun lab(type: MetricType, value: Double) = MetricAssessment(
        MetricReading(type, value, LocalDate.of(2026, 10, 20), id = type.ordinal.toLong()),
        ClinicalStatus.NORMAL,
        TargetStatus.NO_TARGET,
        clinicalMessage = "",
        targetMessage = "",
    )

    @Test
    @Config(qualifiers = "w411dp-h3000dp") // the whole list composed, so a hidden card cannot hide off-screen
    fun bloodPressure_isNotShown_andTheScreenStillScrollsToTheEnd() {
        val bp = InsightCard(
            title = "Blood pressure",
            explanation = "Your current blood pressure entry is in the normal range.",
            whyItMatters = "Blood pressure is best confirmed with repeated seated readings under consistent conditions.",
            safeActions = emptyList(),
            clinicianTriggers = emptyList(),
            confidence = Confidence.MODERATE,
            metric = MetricType.SYSTOLIC_BP,
        )
        val glucose = bp.copy(
            title = "Fasting glucose",
            explanation = "Your fasting glucose is currently within the common reference range.",
            whyItMatters = "",
            metric = MetricType.FASTING_GLUCOSE,
        )
        show(
            MainUiState(
                insights = listOf(bp, bp.copy(metric = MetricType.DIASTOLIC_BP), glucose),
                assessments = listOf(lab(MetricType.SYSTOLIC_BP, 102.0), lab(MetricType.DIASTOLIC_BP, 67.0), lab(MetricType.FASTING_GLUCOSE, 92.0)),
            ),
        )

        // The other labs still show — the row and, last on the screen, its insight — so the checks below can see cards.
        val scroller = composeRule.onNode(hasScrollToNodeAction())
        scroller.performScrollToNode(hasText("92 mg/dL"))
        scroller.performScrollToNode(hasText("Your fasting glucose is currently within the common reference range."))
        val said = everythingSaid()
        listOf("blood pressure", "Systolic", "Diastolic", "mmHg").forEach { word ->
            assertTrue(word, said.none { it.contains(word, ignoreCase = true) })
        }
        // No disclaimer footer on Insights: the full one lives in You → About.
        assertTrue(said.none { it.contains("None of this is a diagnosis", ignoreCase = true) })
    }

    @Test
    fun addAResult_doesNotOfferBloodPressure() {
        composeRule.setContent { SpartanTheme { AddMetricScreen(onSave = { _, _, _ -> true }, onDone = {}) } }
        composeRule.onNodeWithText("Fasting glucose").performClick()
        composeRule.onNodeWithText("ApoB").assertExists() // the picker is open
        assertTrue(composeRule.onAllNodesWithText("Systolic BP").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Diastolic BP").fetchSemanticsNodes().isEmpty())
    }
}
