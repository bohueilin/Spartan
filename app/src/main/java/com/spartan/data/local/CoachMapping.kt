package com.spartan.data.local

import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.PressureWindow

/** Entity ↔ domain mapping for the Coach hub (goals + pressure windows). */

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    type = type,
    targetValue = targetValue,
    baselineValue = baselineValue,
    startEpochDay = startEpochDay,
    targetEpochDay = targetEpochDay,
    status = status,
)

fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    type = type,
    targetValue = targetValue,
    baselineValue = baselineValue,
    startEpochDay = startEpochDay,
    targetEpochDay = targetEpochDay,
    status = status,
)

fun PressureWindowEntity.toDomain(): PressureWindow = PressureWindow(
    id = id,
    daysOfWeekMask = daysOfWeekMask,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    label = label,
)

fun PressureWindow.toEntity(): PressureWindowEntity = PressureWindowEntity(
    id = id,
    daysOfWeekMask = daysOfWeekMask,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    label = label,
)
