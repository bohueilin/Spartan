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
 * app/schemas/ and asserting pre-migration rows survive the whole chain.
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
    }
}
