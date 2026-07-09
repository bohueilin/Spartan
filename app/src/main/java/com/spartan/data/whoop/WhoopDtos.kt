package com.spartan.data.whoop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal DTOs for the WHOOP Developer API v1 collection endpoints. Only the fields Spartan uses
 * are modeled; the JSON parser is configured with `ignoreUnknownKeys = true`, so WHOOP can add
 * fields without breaking us. These DTOs never leave [com.spartan.data.whoop]; [WhoopResponseMapper]
 * normalizes them into the wearable-agnostic `WhoopSnapshot`.
 */

@Serializable
data class WhoopCollection<T>(
    val records: List<T> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
)

// --- /v1/recovery ---
@Serializable
data class WhoopRecoveryRecord(
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("score_state") val scoreState: String? = null,
    val score: WhoopRecoveryScore? = null,
)

@Serializable
data class WhoopRecoveryScore(
    @SerialName("recovery_score") val recoveryScore: Double? = null,
    @SerialName("resting_heart_rate") val restingHeartRate: Double? = null,
    @SerialName("hrv_rmssd_milli") val hrvRmssdMilli: Double? = null,
)

// --- /v1/activity/sleep ---
@Serializable
data class WhoopSleepRecord(
    val start: String? = null,
    val end: String? = null,
    val nap: Boolean = false,
    @SerialName("score_state") val scoreState: String? = null,
    val score: WhoopSleepScore? = null,
)

@Serializable
data class WhoopSleepScore(
    @SerialName("sleep_performance_percentage") val sleepPerformancePercentage: Double? = null,
    @SerialName("respiratory_rate") val respiratoryRate: Double? = null,
    @SerialName("stage_summary") val stageSummary: WhoopStageSummary? = null,
    @SerialName("sleep_needed") val sleepNeeded: WhoopSleepNeeded? = null,
)

@Serializable
data class WhoopStageSummary(
    @SerialName("total_in_bed_time_milli") val totalInBedMilli: Long? = null,
    @SerialName("total_awake_time_milli") val totalAwakeMilli: Long? = null,
)

@Serializable
data class WhoopSleepNeeded(
    @SerialName("baseline_milli") val baselineMilli: Long? = null,
    @SerialName("need_from_sleep_debt_milli") val needFromSleepDebtMilli: Long? = null,
)

// --- /v1/cycle ---
@Serializable
data class WhoopCycleRecord(
    val start: String? = null,
    val end: String? = null,
    @SerialName("score_state") val scoreState: String? = null,
    val score: WhoopCycleScore? = null,
)

@Serializable
data class WhoopCycleScore(
    val strain: Double? = null,
    val kilojoule: Double? = null,
)
