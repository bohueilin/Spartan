package com.spartan.domain.usecase

import com.spartan.data.repository.HealthRepository
import com.spartan.data.whoop.WhoopClient
import com.spartan.diagnostics.DebugLog
import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.WhoopSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one daily sync path: pull recent WHOOP data, normalize + persist it, build today's plan, and
 * seed the check-in — used by both [com.spartan.ui.screens.MainViewModel] (app open) and
 * [com.spartan.data.reminder.DailyPlanRefreshWorker] (background, ~04:00), so morning plans are
 * identical no matter which path ran first. A failed sync never throws out of here.
 */
@Singleton
class DailyPlanSync @Inject constructor(
    private val repository: HealthRepository,
    private val whoopClient: WhoopClient,
    private val coachingEngine: CoachingEngine,
) {
    data class Outcome(
        val readiness: ReadinessSnapshot?,
        val plan: DailyPlan?,
        val latestSnapshot: WhoopSnapshot?,
        val failed: Boolean,
    )

    /**
     * [forceReseed] regenerates the day's not-yet-completed activities instead of keeping the
     * existing plan — used right after a data-source change (CSV import, disconnect) so the plan
     * reflects the data the user just switched to.
     */
    suspend fun sync(dateEpochDay: Long, forceReseed: Boolean = false): Outcome {
        repository.reactivateExpiredSnoozes()
        val snapshots = runCatching { whoopClient.fetchRecentDays(7) }
            .getOrElse {
                DebugLog.log("sync", "fetch failed: ${it.javaClass.simpleName}")
                return Outcome(null, null, null, failed = true)
            }
        if (snapshots.isEmpty()) {
            DebugLog.log("sync", "fetch returned no data")
            return Outcome(null, null, null, failed = true)
        }
        repository.persistWhoopReadings(snapshots)
        val readiness = carryForward(
            ReadinessSnapshot.from(snapshots.last(), snapshots.dropLast(1)),
            dateEpochDay,
        )
        val plan = coachingEngine.buildPlan(readiness)
        if (forceReseed) repository.reseedDailyPlan(plan) else repository.seedDailyPlanIfNeeded(plan)
        // Operational counts only — the readiness band is health-derived and stays out of logs.
        DebugLog.log("sync", "ok: activities=${plan.activities.size} stale=${readiness.isStale}")
        return Outcome(readiness, plan, snapshots.last(), failed = false)
    }

    companion object {
        /**
         * Imported (or lagging) wearable data may end before [targetEpochDay]. The plan still has
         * to land on the day being planned, so the newest readiness is carried forward and marked
         * stale — the coaching engine then eases intensity and says why.
         */
        fun carryForward(readiness: ReadinessSnapshot, targetEpochDay: Long): ReadinessSnapshot =
            if (readiness.dateEpochDay >= targetEpochDay) readiness
            else readiness.copy(dateEpochDay = targetEpochDay, isStale = true)
    }
}
