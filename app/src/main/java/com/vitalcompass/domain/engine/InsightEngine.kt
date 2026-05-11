package com.vitalcompass.domain.engine

import com.vitalcompass.domain.model.ClinicalStatus
import com.vitalcompass.domain.model.Confidence
import com.vitalcompass.domain.model.InsightCard
import com.vitalcompass.domain.model.MetricAssessment
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.TargetStatus

class InsightEngine(
    private val safetyEngine: SafetyEngine = SafetyEngine(),
) {
    fun generate(assessments: List<MetricAssessment>): List<InsightCard> {
        val cards = assessments.mapNotNull { assessment ->
            when (assessment.reading.type) {
                MetricType.FASTING_GLUCOSE -> glucoseInsight(assessment)
                MetricType.TG_HDL_RATIO -> tgHdlInsight(assessment)
                MetricType.VITAMIN_D_25OH -> vitaminDInsight(assessment)
                MetricType.RESTING_HEART_RATE -> rhrInsight(assessment)
                MetricType.BMI -> bmiInsight(assessment)
                MetricType.SYSTOLIC_BP, MetricType.DIASTOLIC_BP -> bpInsight(assessment)
                else -> null
            }
        }
        return cards.map { validate(it) }
    }

    private fun glucoseInsight(assessment: MetricAssessment): InsightCard {
        val aboveRange = assessment.clinicalStatus == ClinicalStatus.ABOVE_RANGE
        return InsightCard(
            title = "Fasting glucose",
            explanation = if (aboveRange) {
                "This value is above the normal clinical range. One value is not a diagnosis."
            } else {
                "Your fasting glucose is currently within the common reference range."
            },
            whyItMatters = "Repeated fasting glucose trends can help you understand how sleep, nutrition, and activity patterns relate to metabolic health.",
            safeActions = listOf(
                "Take a 10 to 20 minute easy walk after meals when practical.",
                "Prioritize consistent sleep and repeat measurement under similar conditions.",
                "Pair higher-carbohydrate meals with protein, fiber, and unhurried eating.",
            ),
            clinicianTriggers = listOf(
                "Discuss repeated above-range fasting glucose values with a clinician.",
                "Ask whether additional lab context would be useful.",
            ),
            confidence = Confidence.MODERATE,
        )
    }

    private fun tgHdlInsight(assessment: MetricAssessment): InsightCard = InsightCard(
        title = "Metabolic signal",
        explanation = "TG/HDL ratio is a metabolic risk signal, not a diagnosis.",
        whyItMatters = "It can reflect patterns in triglycerides and HDL-C that are worth tracking alongside waist, activity, sleep, and repeat labs.",
        safeActions = listOf(
            "Build toward regular Zone 2 cardio and two strength sessions weekly.",
            "Track alcohol, refined carbohydrates, and late meals as possible contributors.",
        ),
        clinicianTriggers = listOf("Discuss persistent elevation or family history with a clinician."),
        confidence = if (assessment.clinicalStatus == ClinicalStatus.ABOVE_RANGE) Confidence.MODERATE else Confidence.LOW,
    )

    private fun vitaminDInsight(assessment: MetricAssessment): InsightCard = InsightCard(
        title = "Vitamin D target",
        explanation = if (assessment.targetStatus == TargetStatus.BELOW_PERSONAL_TARGET) {
            "This value is above basic adequacy in many references but below your personal target."
        } else {
            "This value is above basic adequacy in many references."
        },
        whyItMatters = "Vitamin D is best interpreted with context such as season, sun exposure, diet, and clinician guidance.",
        safeActions = listOf(
            "Track repeat labs rather than reacting to one value.",
            "Discuss food, sunlight, and supplement questions with a clinician before changing doses.",
        ),
        clinicianTriggers = listOf("Ask a clinician what target range is appropriate for you."),
        confidence = Confidence.MODERATE,
    )

    private fun rhrInsight(assessment: MetricAssessment): InsightCard = InsightCard(
        title = "Resting heart rate",
        explanation = if (assessment.targetStatus == TargetStatus.ABOVE_PERSONAL_TARGET) {
            "This is normal for many adults but above your personal fitness target."
        } else {
            "This is within the normal range for many adults."
        },
        whyItMatters = "Resting heart rate trends can reflect fitness, stress, sleep, illness, and recovery.",
        safeActions = listOf(
            "Progress Zone 2 volume gradually.",
            "Use higher resting heart rate days as a cue to keep training easier.",
        ),
        clinicianTriggers = listOf("Seek care for new symptoms, unusual palpitations, chest pain, or fainting."),
        confidence = Confidence.MODERATE,
    )

    private fun bmiInsight(assessment: MetricAssessment): InsightCard = InsightCard(
        title = "Body metrics",
        explanation = "BMI is a broad adult category. Trends, waist-to-height ratio, strength, and sustainable behavior are more useful for action.",
        whyItMatters = "Combining weight trend with waist and fitness measures gives a better view than BMI alone.",
        safeActions = listOf(
            "Use a 7-day weight average to reduce noise.",
            "Add waist-to-height ratio when available.",
            "Aim for consistent training and protein-forward meals without crash dieting.",
        ),
        clinicianTriggers = listOf("Discuss weight strategy if changes are rapid, unexplained, or stressful."),
        confidence = Confidence.HIGH,
    )

    private fun bpInsight(assessment: MetricAssessment): InsightCard = InsightCard(
        title = "Blood pressure",
        explanation = "Your current blood pressure entry is in the normal range.",
        whyItMatters = "Blood pressure is best confirmed with repeated seated readings under consistent conditions.",
        safeActions = listOf("Keep logging occasional readings and note stress, caffeine, and timing."),
        clinicianTriggers = listOf("Discuss repeated high or unusually low symptomatic readings with a clinician."),
        confidence = Confidence.MODERATE,
    )

    private fun validate(card: InsightCard): InsightCard {
        val allCopy = buildString {
            append(card.title).append(' ')
            append(card.explanation).append(' ')
            append(card.whyItMatters).append(' ')
            append(card.safeActions.joinToString(" ")).append(' ')
            append(card.clinicianTriggers.joinToString(" "))
        }
        safetyEngine.sanitize(allCopy)
        return card
    }
}
