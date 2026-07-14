package com.spartan.data.whoop.csv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.spartan.data.local.ConnectionStatus
import com.spartan.data.local.IntegrationProvider
import com.spartan.data.local.WhoopCycleDao
import com.spartan.data.repository.HealthRepository
import com.spartan.diagnostics.DebugLog
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports a WHOOP data export (the CSV files WHOOP emails from App Settings → Data Export) into
 * Spartan's local store. This is the no-credentials path to the user's REAL data: cycles land in
 * `whoop_cycles` (which [com.spartan.data.whoop.LocalFirstWhoopClient] then serves instead of
 * sample data), readings flow through the normal metric pipeline, and consent is recorded the
 * same way an OAuth connection would be. Everything stays on-device.
 */
@Singleton
class WhoopCsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cycleDao: WhoopCycleDao,
    private val repository: HealthRepository,
) {

    data class Summary(
        val days: Int,
        val firstDayEpoch: Long,
        val lastDayEpoch: Long,
        val workouts: Int,
        val journalDays: Int,
        val recognizedFiles: List<String>,
        val skippedFiles: List<String>,
    )

    sealed class ImportError(message: String) : Exception(message) {
        /** None of the picked files looked like a WHOOP export CSV. */
        class NoWhoopData(val skipped: List<String>) :
            ImportError("No WHOOP data recognized in: ${skipped.joinToString()}")

        /** Files were recognized (e.g. journal only) but contained nothing a plan can run on. */
        class NoUsableData(val recognized: List<String>) :
            ImportError("Recognized ${recognized.joinToString()} but no cycles/sleeps/workouts found")
    }

    suspend fun import(uris: List<Uri>): Result<Summary> = withContext(Dispatchers.IO) {
        val parsed = mutableListOf<ParsedWhoopFile>()
        val recognized = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        uris.forEach { uri ->
            val name = displayName(uri)
            val file = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    WhoopCsvParser.parse(stream.readBytes().toString(Charsets.UTF_8))
                }
            }.getOrNull()
            if (file == null) skipped += name else { parsed += file; recognized += name }
        }

        val data = WhoopCsvMerger.merge(parsed, importedAtMillis = System.currentTimeMillis())
        if (data.cycles.isEmpty() && data.workouts.isEmpty()) {
            return@withContext Result.failure(
                if (recognized.isEmpty()) ImportError.NoWhoopData(skipped)
                else ImportError.NoUsableData(recognized),
            )
        }

        // Persistence failures (full disk, DB corruption) surface as a Result, never a crash.
        runCatching {
            cycleDao.upsertCycles(data.cycles)
            cycleDao.upsertWorkouts(data.workouts)

            // Real data is in: no leftover sample row on any day may pose as real data again.
            repository.clearSampleWhoopReadings()

            // Same normalization pipeline as a live sync — idempotent per day. Exercise minutes
            // carry their own tag so snapshot-only re-syncs never delete them.
            val exerciseReadings = data.exerciseMinutesByDay.map { (day, minutes) ->
                MetricReading(
                    type = MetricType.EXERCISE_MINUTES,
                    value = minutes.toDouble(),
                    recordedAt = LocalDate.ofEpochDay(day),
                    note = "WHOOP workouts",
                )
            }
            repository.persistWhoopReadings(data.snapshots, extraReadings = exerciseReadings)
        }.onFailure { failure ->
            DebugLog.log("import", "persist failed: ${failure.javaClass.simpleName}")
            return@withContext Result.failure(failure)
        }

        val days = (data.cycles.map { it.dateEpochDay } + data.workouts.map { it.dateEpochDay })
        val summary = Summary(
            days = data.cycles.size,
            firstDayEpoch = days.min(),
            lastDayEpoch = days.max(),
            workouts = data.workouts.size,
            journalDays = data.cycles.count { it.journalCaffeine != null || it.journalAlcohol != null || it.journalLateMeal != null },
            recognizedFiles = recognized,
            skippedFiles = skipped,
        )

        // Consent records only what actually arrived; a workouts-only import doesn't flip the
        // connection to CONNECTED because plans would still run on sample data.
        if (data.cycles.isNotEmpty()) {
            val kinds = parsed.map { it.kind }.toSet()
            val scopes = buildList {
                if (WhoopCsvKind.PHYSIOLOGICAL_CYCLES in kinds) add("csv:cycles")
                if (WhoopCsvKind.SLEEPS in kinds) add("csv:sleeps")
                if (WhoopCsvKind.WORKOUTS in kinds) add("csv:workouts")
                if (WhoopCsvKind.JOURNAL_ENTRIES in kinds) add("csv:journal")
            }.joinToString(" ")
            repository.setConnection(
                provider = IntegrationProvider.WHOOP,
                status = ConnectionStatus.CONNECTED,
                scopes = scopes,
                accountLabel = "CSV import",
            )
            repository.markSynced(IntegrationProvider.WHOOP)
        }
        repository.logAudit("SYNC", "WHOOP_CSV_IMPORTED", "days=${summary.days} workouts=${summary.workouts}")
        DebugLog.log("import", "whoop csv ok: days=${summary.days} workouts=${summary.workouts} skipped=${skipped.size}")
        Result.success(summary)
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: (uri.lastPathSegment ?: "file")
}
