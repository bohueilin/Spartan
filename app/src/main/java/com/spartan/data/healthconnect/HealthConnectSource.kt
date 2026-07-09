package com.spartan.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.spartan.data.whoop.WhoopClient
import com.spartan.domain.model.WhoopSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Health Connect as a WHOOP alternative (docs/Spartan_Enhancements.md §3): reads
 * recovery-adjacent signals (HRV RMSSD, resting HR, sleep sessions, energy) from Google's
 * on-device aggregator and maps them into the same [WhoopSnapshot] vocabulary — so
 * `ReadinessSnapshot.from`, every coaching rule, and the whole UI are reused unchanged. Same
 * local-first, consent-gated, non-diagnostic posture.
 *
 * FLAG-GATED OFF (`BuildConfig.USE_HEALTH_CONNECT = false`) for this release. Enabling requires:
 *  1. Manifest `<uses-permission android:name="android.permission.health.READ_*">` declarations
 *     + the permissions-rationale intent filter (deliberately NOT declared while unused — health
 *     permissions in the manifest trigger Play Health-apps review).
 *  2. A connect-time permission request via `PermissionController.createRequestPermissionResultContract()`.
 *  3. The Play Console Health apps declaration.
 * Until granted, this source degrades to an empty list (→ the existing "couldn't refresh" path),
 * never a crash. Health Connect has no "recovery score"; the readiness band derives from
 * HRV/RHR/sleep trends via the existing null-recovery fallback rules.
 */
class HealthConnectSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : WhoopClient {

    override val isMock: Boolean = false

    override suspend fun fetchRecentDays(days: Int): List<WhoopSnapshot> {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return emptyList()
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(REQUIRED_PERMISSIONS)) return emptyList()

        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days.toLong().coerceAtLeast(1)))
        val filter = TimeRangeFilter.between(start, end)

        val byDay = sortedMapOf<Long, Builder>()
        fun at(instant: Instant): Builder {
            val day = instant.atZone(zone).toLocalDate().toEpochDay()
            return byDay.getOrPut(day) { Builder(day) }
        }

        runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, filter))
                .records.forEach { at(it.time).hrvMs = it.heartRateVariabilityMillis }
            client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, filter))
                .records.forEach { at(it.time).restingHeartRate = it.beatsPerMinute.toDouble() }
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter))
                .records.forEach { session ->
                    val b = at(session.endTime)
                    b.sleepDurationHours =
                        Duration.between(session.startTime, session.endTime).toMinutes() / 60.0
                    b.bedMinuteOfDay = session.startTime.atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
                    b.wakeMinuteOfDay = session.endTime.atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
                }
            client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, filter))
                .records.forEach { at(it.endTime).energyKcal = it.energy.inKilocalories }
        }.getOrElse { return emptyList() } // permission revoked mid-read etc. — degrade, never crash

        return byDay.values.map { it.build() }.takeLast(days)
    }

    private class Builder(val day: Long) {
        var hrvMs: Double? = null
        var restingHeartRate: Double? = null
        var sleepDurationHours: Double? = null
        var energyKcal: Double? = null
        var bedMinuteOfDay: Int? = null
        var wakeMinuteOfDay: Int? = null

        fun build() = WhoopSnapshot(
            dateEpochDay = day,
            recoveryScore = null, // HC has no recovery concept; band falls back to trend rules
            hrvMs = hrvMs,
            restingHeartRate = restingHeartRate,
            sleepDurationHours = sleepDurationHours,
            energyKcal = energyKcal,
            bedMinuteOfDay = bedMinuteOfDay,
            wakeMinuteOfDay = wakeMinuteOfDay,
            isMock = false,
        )
    }

    companion object {
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        )
    }
}
