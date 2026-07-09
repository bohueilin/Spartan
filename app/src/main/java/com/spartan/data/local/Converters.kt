package com.spartan.data.local

import androidx.room.TypeConverter
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WorkoutType

class Converters {
    @TypeConverter
    fun metricTypeToString(type: MetricType): String = type.name

    @TypeConverter
    fun stringToMetricType(value: String): MetricType = MetricType.valueOf(value)

    @TypeConverter
    fun workoutTypeToString(type: WorkoutType): String = type.name

    @TypeConverter
    fun stringToWorkoutType(value: String): WorkoutType = WorkoutType.valueOf(value)

    @TypeConverter
    fun reminderFrequencyToString(frequency: ReminderFrequency): String = frequency.name

    @TypeConverter
    fun stringToReminderFrequency(value: String): ReminderFrequency = ReminderFrequency.valueOf(value)

    @TypeConverter
    fun activityCategoryToString(v: ActivityCategory): String = v.name

    @TypeConverter
    fun stringToActivityCategory(v: String): ActivityCategory = ActivityCategory.valueOf(v)

    @TypeConverter
    fun activityPriorityToString(v: ActivityPriority): String = v.name

    @TypeConverter
    fun stringToActivityPriority(v: String): ActivityPriority = ActivityPriority.valueOf(v)

    @TypeConverter
    fun activityStatusToString(v: ActivityStatus): String = v.name

    @TypeConverter
    fun stringToActivityStatus(v: String): ActivityStatus = ActivityStatus.valueOf(v)

    @TypeConverter
    fun intensityToString(v: Intensity): String = v.name

    @TypeConverter
    fun stringToIntensity(v: String): Intensity = Intensity.valueOf(v)

    @TypeConverter
    fun timeOfDayToString(v: TimeOfDay): String = v.name

    @TypeConverter
    fun stringToTimeOfDay(v: String): TimeOfDay = TimeOfDay.valueOf(v)

    @TypeConverter
    fun integrationProviderToString(v: IntegrationProvider): String = v.name

    @TypeConverter
    fun stringToIntegrationProvider(v: String): IntegrationProvider = IntegrationProvider.valueOf(v)

    @TypeConverter
    fun connectionStatusToString(v: ConnectionStatus): String = v.name

    @TypeConverter
    fun stringToConnectionStatus(v: String): ConnectionStatus = ConnectionStatus.valueOf(v)
}
