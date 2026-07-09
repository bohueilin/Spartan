package com.spartan.data.local

import com.spartan.domain.model.DailyActivity

/** Conversions between the persisted [DailyActivityEntity] and the domain [DailyActivity]. */

fun DailyActivity.toEntity(dateEpochDay: Long): DailyActivityEntity = DailyActivityEntity(
    id = id,
    dateEpochDay = dateEpochDay,
    title = title,
    category = category,
    priority = priority,
    whyItMatters = whyItMatters,
    relatedMetric = relatedMetric,
    instructions = instructions.joinToString("\n"),
    estimatedMinutes = estimatedMinutes,
    intensity = intensity,
    bestTimeOfDay = bestTimeOfDay,
    status = status,
    ruleId = ruleId,
    scheduledEpochMinute = scheduledEpochMinute,
    completedAtMillis = completedAtMillis,
    snoozedUntilMillis = snoozedUntilMillis,
    safetyNote = safetyNote,
)

fun DailyActivityEntity.toDomain(): DailyActivity = DailyActivity(
    id = id,
    title = title,
    category = category,
    priority = priority,
    whyItMatters = whyItMatters,
    relatedMetric = relatedMetric,
    instructions = if (instructions.isBlank()) emptyList() else instructions.split("\n"),
    estimatedMinutes = estimatedMinutes,
    intensity = intensity,
    bestTimeOfDay = bestTimeOfDay,
    status = status,
    ruleId = ruleId,
    scheduledEpochMinute = scheduledEpochMinute,
    completedAtMillis = completedAtMillis,
    snoozedUntilMillis = snoozedUntilMillis,
    safetyNote = safetyNote,
)
