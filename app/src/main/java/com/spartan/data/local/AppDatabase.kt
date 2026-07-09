package com.spartan.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        MetricEntryEntity::class,
        TargetEntity::class,
        WorkoutSessionEntity::class,
        PlanWorkoutOverrideEntity::class,
        ReminderEntity::class,
        WeeklyReviewEntity::class,
        DailyActivityEntity::class,
        IntegrationConnectionEntity::class,
        AuditEventEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN frequency TEXT NOT NULL DEFAULT 'DAILY'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN daysOfWeekMask INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE reminders ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_workout_overrides (
                        slotKey TEXT NOT NULL PRIMARY KEY,
                        minutes INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        // Spartan: daily coaching plan + integration consent. Additive, no data loss.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_activities (
                        id TEXT NOT NULL PRIMARY KEY,
                        dateEpochDay INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        whyItMatters TEXT NOT NULL,
                        relatedMetric TEXT,
                        instructions TEXT NOT NULL,
                        estimatedMinutes INTEGER NOT NULL,
                        intensity TEXT NOT NULL,
                        bestTimeOfDay TEXT NOT NULL,
                        status TEXT NOT NULL,
                        ruleId TEXT NOT NULL,
                        scheduledEpochMinute INTEGER,
                        completedAtMillis INTEGER,
                        snoozedUntilMillis INTEGER,
                        safetyNote TEXT,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_activities_dateEpochDay ON daily_activities(dateEpochDay)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS integration_connections (
                        provider TEXT NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        consentGrantedAtMillis INTEGER,
                        scopes TEXT NOT NULL,
                        lastSyncMillis INTEGER,
                        accountLabel TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

        // Spartan: append-only, non-PHI audit trail. Additive, no data loss.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audit_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampMillis INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        action TEXT NOT NULL,
                        detail TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
