package com.vitalcompass.data.local

import androidx.room.TypeConverter
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.WorkoutType

class Converters {
    @TypeConverter
    fun metricTypeToString(type: MetricType): String = type.name

    @TypeConverter
    fun stringToMetricType(value: String): MetricType = MetricType.valueOf(value)

    @TypeConverter
    fun workoutTypeToString(type: WorkoutType): String = type.name

    @TypeConverter
    fun stringToWorkoutType(value: String): WorkoutType = WorkoutType.valueOf(value)
}
