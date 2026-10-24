package com.spartan.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards local health data across upgrades: a broken migration silently drops everything the user
 * has logged. Walks a database created at v3 through every migration to the current version,
 * validating each migrated schema structurally against the committed schema history in
 * app/schemas/ and asserting pre-migration rows survive the whole chain; v8 → v9 also checks
 * imported WHOOP days keep every value and gain an empty sleep-need column.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate3To4To5_preservesDataAndCreatesNewTables() {
        val dbName = "migration-test"

        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO metric_entries (type, value, unit, note, recordedAtEpochDay, createdAtMillis) " +
                    "VALUES ('RESTING_HEART_RATE', 55.0, 'bpm', 'pre-migration row', 20000, 1)",
            )
            execSQL(
                "INSERT INTO reminders (id, title, body, hour, minute, enabled, frequency, daysOfWeekMask, updatedAtMillis) " +
                    "VALUES ('exercise', 'Exercise', 'Move', 8, 0, 1, 'DAILY', 127, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4).apply {
            // New v4 tables exist and are queryable.
            query("SELECT COUNT(*) FROM daily_activities").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            query("SELECT COUNT(*) FROM integration_connections").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5).apply {
            // Audit table exists.
            query("SELECT COUNT(*) FROM audit_events").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 6, true, AppDatabase.MIGRATION_5_6).apply {
            query("SELECT COUNT(*) FROM whoop_cycles").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 7, true, AppDatabase.MIGRATION_6_7).apply {
            // Coach tables exist.
            query("SELECT COUNT(*) FROM goals").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            query("SELECT COUNT(*) FROM pressure_windows").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            query("SELECT COUNT(*) FROM whoop_workouts").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 8, true, AppDatabase.MIGRATION_7_8).apply {
            // Reflections table exists and accepts a row.
            query("SELECT COUNT(*) FROM daily_reflections").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            execSQL(
                "INSERT INTO daily_reflections (dateEpochDay, mood, note, createdAtMillis) " +
                    "VALUES (20000, 'STRONG', 'felt good', 1)",
            )
            query("SELECT mood FROM daily_reflections WHERE dateEpochDay = 20000").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("STRONG", c.getString(0))
            }
            // Pre-migration user data survived the whole 3→8 chain.
            query("SELECT value, note FROM metric_entries WHERE type = 'RESTING_HEART_RATE'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(55.0, c.getDouble(0), 0.001)
                assertEquals("pre-migration row", c.getString(1))
            }
            query("SELECT enabled FROM reminders WHERE id = 'exercise'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 9, true, AppDatabase.MIGRATION_8_9).apply {
            // Still there at the current version.
            query("SELECT COUNT(*) FROM metric_entries").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            query("SELECT COUNT(*) FROM daily_reflections").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            close()
        }
    }

    @Test
    fun migrate8To9_keepsImportedDays_andTheirSleepNeedStartsUnknown() {
        val dbName = "migration-test-8-9"

        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO whoop_cycles (dateEpochDay, recoveryScore, hrvMs, sleepDurationHours, journalAlcohol, " +
                    "source, importedAtMillis) VALUES (20000, 64, 71.5, 7.25, 0, 'CSV_IMPORT', 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 9, true, AppDatabase.MIGRATION_8_9).apply {
            query(
                "SELECT recoveryScore, hrvMs, sleepDurationHours, journalAlcohol, source, sleepNeedHours " +
                    "FROM whoop_cycles WHERE dateEpochDay = 20000",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(64, c.getInt(0))
                assertEquals(71.5, c.getDouble(1), 0.001)
                assertEquals(7.25, c.getDouble(2), 0.001)
                assertEquals(0, c.getInt(3))
                assertEquals("CSV_IMPORT", c.getString(4))
                // No need is known until the next import — never a made-up number.
                assertTrue(c.isNull(5))
            }
            // The new column takes WHOOP's figure when an import brings one.
            execSQL("UPDATE whoop_cycles SET sleepNeedHours = 8.5 WHERE dateEpochDay = 20000")
            query("SELECT sleepNeedHours FROM whoop_cycles WHERE dateEpochDay = 20000").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(8.5, c.getDouble(0), 0.001)
            }
            close()
        }
    }
}
