package com.vitalcompass.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserProfileEntity::class,
        MetricEntryEntity::class,
        TargetEntity::class,
        WorkoutSessionEntity::class,
        ReminderEntity::class,
        WeeklyReviewEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
}
