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

    suspend fun sync(dateEpochDay: Long): Outcome {
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
        val readiness = ReadinessSnapshot.from(snapshots.last(), snapshots.dropLast(1))
        val plan = coachingEngine.buildPlan(readiness)
        repository.seedDailyPlanIfNeeded(plan)
        DebugLog.log("sync", "ok: band=${readiness.band} activities=${plan.activities.size}")
        return Outcome(readiness, plan, snapshots.last(), failed = false)
    }
}
