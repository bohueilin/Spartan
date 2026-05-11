package com.vitalcompass.domain.model

enum class MetricType(
    val label: String,
    val unit: String,
    val lowerIsBetter: Boolean = false,
) {
    FASTING_GLUCOSE("Fasting glucose", "mg/dL", true),
    TRIGLYCERIDES("Triglycerides", "mg/dL", true),
    HDL_C("HDL-C", "mg/dL"),
    TG_HDL_RATIO("TG/HDL ratio", "", true),
    VITAMIN_D_25OH("Vitamin D 25-OH", "ng/mL"),
    RESTING_HEART_RATE("Resting heart rate", "bpm", true),
    SYSTOLIC_BP("Systolic BP", "mmHg", true),
    DIASTOLIC_BP("Diastolic BP", "mmHg", true),
    WEIGHT("Weight", "kg", true),
    WAIST_CIRCUMFERENCE("Waist circumference", "cm", true),
    BMI("BMI", "", true),
    WAIST_TO_HEIGHT_RATIO("Waist-to-height ratio", "", true),
    APOB("ApoB", "mg/dL", true),
    LPA("Lp(a)", "nmol/L", true),
    CAC("CAC", "score", true),
    SLEEP_DURATION("Sleep duration", "hours"),
    EXERCISE_MINUTES("Exercise minutes", "min"),
    STRENGTH_SESSIONS("Strength sessions", "sessions"),
    CUSTOM("Custom", "")
}
