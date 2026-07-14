package com.spartan.domain.engine

import com.spartan.domain.model.ClinicalRange
import com.spartan.domain.model.ClinicalStatus
import com.spartan.domain.model.MetricAssessment
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TargetStatus
import com.spartan.domain.model.TargetValue
import kotlin.math.pow
import kotlin.math.round

class MetricEngine(
    private val safetyEngine: SafetyEngine = SafetyEngine(),
) {
    private val clinicalRanges = mapOf(
        MetricType.FASTING_GLUCOSE to ClinicalRange(normalHigh = 99.0, description = "Normal fasting glucose is commonly below 100 mg/dL."),
        MetricType.TRIGLYCERIDES to ClinicalRange(normalHigh = 149.0, description = "Triglycerides below 150 mg/dL are commonly considered normal."),
        MetricType.HDL_C to ClinicalRange(normalLow = 40.0, description = "HDL-C at or above 40 mg/dL is a common adult reference point."),
        MetricType.TG_HDL_RATIO to ClinicalRange(normalHigh = 3.0, description = "TG/HDL ratio is a metabolic risk signal, not a diagnosis."),
        MetricType.VITAMIN_D_25OH to ClinicalRange(normalLow = 20.0, description = "Many clinical references treat 20 ng/mL or higher as basic adequacy."),
        MetricType.RESTING_HEART_RATE to ClinicalRange(normalLow = 60.0, normalHigh = 100.0, description = "Resting heart rate from 60 to 100 bpm is normal for many adults."),
        MetricType.SYSTOLIC_BP to ClinicalRange(normalLow = 90.0, normalHigh = 119.0, description = "Systolic BP below 120 mmHg is commonly considered normal when diastolic is also normal."),
        MetricType.DIASTOLIC_BP to ClinicalRange(normalLow = 60.0, normalHigh = 79.0, description = "Diastolic BP below 80 mmHg is commonly considered normal."),
        MetricType.BMI to ClinicalRange(normalLow = 18.5, normalHigh = 24.9, description = "Adult BMI from 25.0 to 29.9 is categorized as overweight."),
        MetricType.WAIST_TO_HEIGHT_RATIO to ClinicalRange(normalHigh = 0.5, description = "Waist-to-height ratio below 0.5 is a common cardiometabolic risk target."),
    )

    fun bmi(weightKg: Double, heightCm: Double): Double {
        require(weightKg > 0) { "Weight must be positive." }
        require(heightCm > 0) { "Height must be positive." }
        return roundOneDecimal(weightKg / (heightCm / 100.0).pow(2))
    }

    fun tgHdlRatio(triglycerides: Double, hdl: Double): Double {
        require(triglycerides > 0) { "Triglycerides must be positive." }
        require(hdl > 0) { "HDL must be positive." }
        return roundTwoDecimals(triglycerides / hdl)
    }

    fun waistToHeightRatio(waistCm: Double, heightCm: Double): Double {
        require(waistCm > 0) { "Waist must be positive." }
        require(heightCm > 0) { "Height must be positive." }
        return roundTwoDecimals(waistCm / heightCm)
    }

    fun validate(type: MetricType, value: Double?): Boolean {
        if (value == null) return type in pendingCapableMetrics
        // Negative readings are never valid; zero is decided per metric by the ranges below —
        // real WHOOP data legitimately reports 0 (sleep debt fully paid, a zero-strain day).
        if (value < 0.0) return false
        return when (type) {
            MetricType.FASTING_GLUCOSE -> value in 40.0..400.0
            MetricType.TRIGLYCERIDES -> value in 20.0..1500.0
            MetricType.HDL_C -> value in 10.0..150.0
            MetricType.TG_HDL_RATIO -> value in 0.1..30.0
            MetricType.VITAMIN_D_25OH -> value in 1.0..200.0
            MetricType.RESTING_HEART_RATE -> value in 30.0..220.0
            MetricType.SYSTOLIC_BP -> value in 60.0..260.0
            MetricType.DIASTOLIC_BP -> value in 30.0..160.0
            MetricType.WEIGHT -> value in 20.0..400.0
            MetricType.WAIST_CIRCUMFERENCE -> value in 40.0..250.0
            MetricType.BMI -> value in 10.0..80.0
            MetricType.WAIST_TO_HEIGHT_RATIO -> value in 0.2..1.5
            MetricType.APOB -> value in 10.0..300.0
            MetricType.LPA -> value in 0.0..500.0
            MetricType.CAC -> value in 0.0..5000.0
            MetricType.SLEEP_DURATION -> value in 0.5..24.0
            MetricType.EXERCISE_MINUTES -> value in 0.0..1440.0
            MetricType.STRENGTH_SESSIONS -> value in 0.0..14.0
            MetricType.RECOVERY_SCORE -> value in 0.0..100.0
            MetricType.HRV_RMSSD -> value in 5.0..300.0
            MetricType.SLEEP_PERFORMANCE -> value in 0.0..100.0
            MetricType.SLEEP_DEBT -> value in 0.0..24.0
            MetricType.RESPIRATORY_RATE -> value in 5.0..40.0
            MetricType.DAY_STRAIN -> value in 0.0..21.0
            MetricType.ENERGY_KCAL -> value in 0.0..10000.0
            MetricType.CUSTOM -> true
        }
    }

    fun assess(reading: MetricReading, target: TargetValue? = null): MetricAssessment {
        require(validate(reading.type, reading.value)) { "Invalid value for ${reading.type.label}." }
        val clinicalStatus = classifyClinical(reading)
        val targetStatus = compareTarget(reading, target)
        return MetricAssessment(
            reading = reading,
            clinicalStatus = clinicalStatus,
            targetStatus = targetStatus,
            clinicalMessage = safetyEngine.sanitize(clinicalMessage(reading, clinicalStatus)),
            targetMessage = safetyEngine.sanitize(targetMessage(reading, targetStatus)),
        )
    }

    fun classifyClinical(reading: MetricReading): ClinicalStatus {
        val value = reading.value ?: return ClinicalStatus.PENDING
        val range = clinicalRanges[reading.type] ?: return ClinicalStatus.UNKNOWN
        if (range.normalLow != null && value < range.normalLow) return ClinicalStatus.BELOW_RANGE
        if (range.normalHigh != null && value > range.normalHigh) return ClinicalStatus.ABOVE_RANGE
        return ClinicalStatus.NORMAL
    }

    fun compareTarget(reading: MetricReading, target: TargetValue?): TargetStatus {
        val value = reading.value ?: return TargetStatus.PENDING
        if (target == null) return TargetStatus.NO_TARGET
        if (target.minValue != null && value < target.minValue) return TargetStatus.BELOW_PERSONAL_TARGET
        if (target.maxValue != null && value > target.maxValue) return TargetStatus.ABOVE_PERSONAL_TARGET
        return TargetStatus.MEETS_TARGET
    }

    private fun clinicalMessage(reading: MetricReading, status: ClinicalStatus): String = when {
        status == ClinicalStatus.PENDING -> "${reading.type.label} is pending. Add the result when available."
        reading.type == MetricType.FASTING_GLUCOSE && status == ClinicalStatus.ABOVE_RANGE ->
            "This value is above the normal clinical range. One value is not a diagnosis."
        reading.type == MetricType.VITAMIN_D_25OH && status == ClinicalStatus.NORMAL ->
            "This value is above basic adequacy in many references."
        reading.type == MetricType.TG_HDL_RATIO ->
            "TG/HDL ratio is a metabolic risk signal, not a diagnosis."
        reading.type == MetricType.BMI && status == ClinicalStatus.ABOVE_RANGE ->
            "BMI is in the adult overweight category. Trends, waist-to-height ratio, and sustainable behavior matter more than one number."
        status == ClinicalStatus.NORMAL -> "This value is within the common clinical reference range."
        status == ClinicalStatus.ABOVE_RANGE -> "This value is above the normal clinical range."
        status == ClinicalStatus.BELOW_RANGE -> "This value is below the normal clinical range."
        else -> "No standard clinical classification is shown for this metric."
    }

    private fun targetMessage(reading: MetricReading, status: TargetStatus): String = when (status) {
        TargetStatus.MEETS_TARGET -> "This meets your personal optimization target."
        TargetStatus.ABOVE_PERSONAL_TARGET -> "This is above your personal optimization target."
        TargetStatus.BELOW_PERSONAL_TARGET -> "This is below your personal optimization target."
        TargetStatus.NO_TARGET -> "No personal target is set."
        TargetStatus.PENDING -> "${reading.type.label} target comparison is pending."
    }

    private fun roundOneDecimal(value: Double) = round(value * 10.0) / 10.0
    private fun roundTwoDecimals(value: Double) = round(value * 100.0) / 100.0

    companion object {
        private val pendingCapableMetrics = setOf(MetricType.APOB, MetricType.LPA, MetricType.CAC)
    }
}
