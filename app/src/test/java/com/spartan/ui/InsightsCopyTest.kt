package com.spartan.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spartan.R
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.model.MetricType
import com.spartan.ui.screens.insightMeaningRes
import com.spartan.ui.screens.recoveryMeaningRes
import com.spartan.ui.screens.signalStatusRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Insights copy as shipped (string resources), which metric detail's "What this means for you"
 * reads too: the status pill names the position only — "In your usual range" only inside it,
 * otherwise the direction — every plain-language line passes the safety gate with no
 * medical-sounding or predictive claims (spec §5–6), every line for a reading inside the usual
 * names it as the pill does, and Recovery's line never contradicts the zone pill beside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InsightsCopyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safety = SafetyEngine()

    /** A Recovery reading in each zone: 80% Green, 64% Yellow, 20% Red. */
    private val zoneScores = mapOf(RecoveryZone.GREEN to 80.0, RecoveryZone.YELLOW to 64.0, RecoveryZone.RED to 20.0)
    private val placed = listOf(SignalPosition.ABOVE, SignalPosition.WITHIN, SignalPosition.BELOW)

    private fun signal(metric: MetricType, position: SignalPosition, latest: Double = 1.0) =
        Signal(metric, latest, listOf(1.0, latest), 0.0, 2.0, position)

    private fun line(signal: Signal) = context.getString(insightMeaningRes(signal))

    @Test
    fun statusPill_saysInYourUsualRange_onlyWhenWithin() {
        fun label(position: SignalPosition) = context.getString(signalStatusRes(signal(MetricType.HRV_RMSSD, position)))
        assertEquals("In your usual range", label(SignalPosition.WITHIN))
        assertEquals("Above your usual", label(SignalPosition.ABOVE))
        assertEquals("Below your usual", label(SignalPosition.BELOW))
        assertEquals("Learning your usual", label(SignalPosition.LEARNING))
        // The ambiguous "Your normal" and the graded "Better than usual" are gone.
        SignalPosition.entries.forEach {
            assertFalse(label(it), label(it).contains("normal", ignoreCase = true))
            assertFalse(label(it), label(it).contains("better", ignoreCase = true))
        }
    }

    /**
     * Every line Insights and metric detail can show — each metric in each position and, for
     * Recovery, each zone — passes [SafetyEngine.validateCopy] and carries none of the medical or
     * predictive wording metric detail's old lines had ("your nervous system is well rested", "a
     * sign of good recovery", "recovery should follow", "when you are run down").
     */
    @Test
    fun everyMeaningLine_onInsightsAndInDetail_isSafeWellnessCopy() {
        val banned = listOf(
            "nervous system", "well rested", "should follow", "a sign of", "run down", "diagnos", "illness", "sick",
            "disease", "medication", "supplement", "doctor", "symptom",
        )
        val nonRecovery = SignalEngine.DAILY - MetricType.RECOVERY_SCORE + MetricType.ENERGY_KCAL + MetricType.SLEEP_DURATION
        val lines = (
            nonRecovery.flatMap { metric -> SignalPosition.entries.map { line(signal(metric, it)) } } +
                zoneScores.values.flatMap { score -> SignalPosition.entries.map { line(signal(MetricType.RECOVERY_SCORE, it, score)) } }
            ).toSet()
        // 6 metrics x 3 positions + Recovery's 3 zones x 3 positions + learning + the line for metrics without their own.
        assertEquals(6 * 3 + 3 * 3 + 1 + 1, lines.size)
        lines.forEach { line ->
            assertTrue("unsafe: $line", safety.validateCopy(line))
            banned.forEach { assertFalse("\"$it\" in: $line", line.contains(it, ignoreCase = true)) }
        }
    }

    /**
     * One name for one idea: the pill says "In your usual range", so every line for a reading inside
     * the usual — each metric, and Recovery in each zone — opens with those words, and no line says
     * "close to your usual" (which suggests just outside it).
     */
    @Test
    fun everyWithinLine_namesTheUsualAsThePillDoes() {
        val pill = context.getString(R.string.signal_within)
        val within = (SignalEngine.DAILY - MetricType.RECOVERY_SCORE).map { line(signal(it, SignalPosition.WITHIN)) } +
            zoneScores.values.map { line(signal(MetricType.RECOVERY_SCORE, SignalPosition.WITHIN, it)) }
        assertEquals(6 + 3, within.toSet().size)
        within.forEach { assertTrue(it, it.startsWith(pill)) }
        val every = (SignalEngine.DAILY - MetricType.RECOVERY_SCORE).flatMap { metric -> SignalPosition.entries.map { line(signal(metric, it)) } } +
            zoneScores.values.flatMap { score -> SignalPosition.entries.map { line(signal(MetricType.RECOVERY_SCORE, it, score)) } }
        every.forEach { assertFalse(it, it.contains("close to", ignoreCase = true)) }
    }

    @Test
    fun recoveryLine_neverContradictsItsZone() {
        zoneScores.forEach { (zone, score) ->
            placed.forEach { position ->
                val text = line(signal(MetricType.RECOVERY_SCORE, position, score))
                assertEquals(context.getString(recoveryMeaningRes(position, zone)), text)
                // The line names the same zone as the pill beside it.
                assertTrue(text, text.contains("${zone.label} zone"))
                when (zone) {
                    // Above its usual or not, a Yellow or Red day is never "a good day for a quality session".
                    RecoveryZone.YELLOW, RecoveryZone.RED -> listOf("good", "quality", "push").forEach {
                        assertFalse("$zone $position: \"$it\" in: $text", text.contains(it, ignoreCase = true))
                    }
                    // Below its usual or not, a Green day is never told to keep it gentle or easy.
                    RecoveryZone.GREEN -> listOf("gentle", "easy", "easier", "light", "rest").forEach {
                        assertFalse("$zone $position: \"$it\" in: $text", Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(text))
                    }
                }
            }
        }
    }

    @Test
    fun learningLine_saysNoCountAndNoSecondLearning() {
        // The pill says "Learning your usual" and the line under the value gives the one day count.
        val learning = line(signal(MetricType.HRV_RMSSD, SignalPosition.LEARNING))
        assertFalse(learning, learning.contains("learn", ignoreCase = true))
        assertFalse(learning, Regex("""\d|week""").containsMatchIn(learning))
    }

    @Test
    fun sleepDebtsPaybackTip_isOneSentenceAfterTheLine() {
        val within = line(signal(MetricType.SLEEP_DEBT, SignalPosition.WITHIN))
        val tip = context.getString(R.string.glossary_debt_payback)
        assertEquals("In your usual range for sleep debt. Pay it back over a few nights, not one.", context.getString(R.string.insights_sentences, within, tip))
    }

    @Test
    fun theUsualNote_isOneSentence_withNoDisclaimer() {
        // The full disclaimer lives in You → About; this line only says where "your usual" comes from.
        val note = context.getString(R.string.insights_usual_note)
        assertEquals(1, Regex("""[.!?](\s|$)""").findAll(note).count())
        assertFalse(note, note.contains("diagnos", ignoreCase = true))
        assertTrue(note, note.startsWith("Your usual is"))
    }
}
