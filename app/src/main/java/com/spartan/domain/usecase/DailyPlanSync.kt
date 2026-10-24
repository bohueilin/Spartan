package com.spartan.domain.usecase

import android.content.Context
import android.text.format.DateFormat
import com.spartan.data.local.PreferencesStore
import com.spartan.data.repository.HealthRepository
import com.spartan.data.whoop.WhoopClient
import com.spartan.diagnostics.DebugLog
import com.spartan.domain.engine.BedtimePlan
import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.engine.GoalStatus
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.engine.SleepNeed
import com.spartan.domain.engine.SleepPlanner
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.WhoopSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one daily sync path: pull recent WHOOP data, normalize + persist it, build today's plan
 * (around the user's plan settings, primary goal and tonight's bedtime), and seed the check-in —
 * used by both [com.spartan.ui.screens.MainViewModel] (app open) and
 * [com.spartan.data.reminder.DailyPlanRefreshWorker] (background, ~04:00), so morning plans are
 * identical no matter which path ran first. A failed sync never throws out of here.
 */
@Singleton
class DailyPlanSync @Inject constructor(
    private val repository: HealthRepository,
    private val whoopClient: WhoopClient,
    private val coachingEngine: CoachingEngine,
    private val preferencesStore: PreferencesStore,
    @param:ApplicationContext private val appContext: Context,
) {
    data class Outcome(
        val readiness: ReadinessSnapshot?,
        val plan: DailyPlan?,
        val latestSnapshot: WhoopSnapshot?,
        val failed: Boolean,
        /** Tonight's sleep target the plan's sleep anchor was built from; null when there is none. */
        val bedtime: BedtimePlan? = null,
        /**
         * True when this sync replaced a holding plan — saved before today's cycle was scored —
         * with the plan from today's completed cycle (see [replacesHoldingPlan]). Completed items
         * were kept; the rest were replaced.
         */
        val replacedHoldingPlan: Boolean = false,
        /**
         * Replaced activities that had a snooze or reschedule reminder armed. The caller cancels
         * them, or they would fire for items that are no longer in the plan.
         */
        val replacedReminderIds: List<String> = emptyList(),
        /**
         * True when all local data was erased while this sync was in flight: it wrote nothing, and
         * nothing it fetched may be shown — the user just deleted it. Not a failure (no retry).
         */
        val superseded: Boolean = false,
    )

    /**
     * [forceReseed] regenerates the day's not-yet-completed activities instead of keeping the
     * existing plan — used right after a data-source change (CSV import, disconnect) so the plan
     * reflects the data the user just switched to. Without it the saved plan is kept, except a
     * holding plan once today's completed cycle arrives ([replacesHoldingPlan]).
     *
     * [erasureGeneration] is the store's [HealthRepository.erasureGeneration] when the caller
     * started (by default, when this call did). The fetch runs unlocked; everything this sync
     * writes runs under the store lock and only if no erasure has landed since — otherwise the
     * outcome is [Outcome.superseded] and nothing is written.
     */
    suspend fun sync(
        dateEpochDay: Long,
        forceReseed: Boolean = false,
        erasureGeneration: Long = repository.erasureGeneration,
    ): Outcome {
        // Harmless after an erasure (there is nothing left to wake), so it needs no lock.
        repository.reactivateExpiredSnoozes()
        val snapshots = runCatching { whoopClient.fetchRecentDays(7) }
            .getOrElse {
                if (it is CancellationException) throw it
                DebugLog.log("sync", "fetch failed: ${it.javaClass.simpleName}")
                return Outcome(null, null, null, failed = true)
            }
        if (snapshots.isEmpty()) {
            DebugLog.log("sync", "fetch returned no data")
            return Outcome(null, null, null, failed = true)
        }
        return repository.writeUnlessErasedSince(erasureGeneration) {
            persistAndPlan(dateEpochDay, forceReseed, snapshots)
        } ?: Outcome(null, null, null, failed = false, superseded = true).also {
            DebugLog.log("sync", "superseded: local data was erased mid-sync")
        }
    }

    /** The write half of [sync], run under the store lock: persist, plan, seed. */
    private suspend fun persistAndPlan(
        dateEpochDay: Long,
        forceReseed: Boolean,
        snapshots: List<WhoopSnapshot>,
    ): Outcome {
        repository.persistWhoopReadings(snapshots)
        val latest = snapshots.last()
        val readiness = carryForward(
            ReadinessSnapshot.from(latest, snapshots.dropLast(1)),
            dateEpochDay,
        )
        // Recent pain has to reach the engine here, at the production call site: without it the
        // REQUIRED pain-deload card and the hard-training suppression are unreachable, and a user
        // who reported pain yesterday can be handed a progression session this morning.
        val recentLogs = repository.workoutLogsSince(dateEpochDay - PAIN_LOOKBACK_DAYS)
        // What the user told the goal wizard. An unreadable preferences file must never cost the
        // morning plan: it is planned as if nothing was given.
        val constraints = try {
            preferencesStore.planConstraints.first()
        } catch (e: IOException) {
            DebugLog.log("sync", "constraints unreadable: ${e.javaClass.simpleName}")
            PlanConstraints()
        }
        val activeGoal = repository.goals.first().firstOrNull { it.status == GoalStatus.ACTIVE }
        // An older night never sizes tonight's target or its sleep-debt line: with stale data the
        // target comes from the user's own bed / wake times only.
        val bedtime = tonightFor(lastNight(latest, dateEpochDay), constraints)
        val plan = coachingEngine.buildPlan(
            readiness,
            CoachingOptions(
                maxActivities = MAX_DAILY_ACTIONS,
                minActivities = MIN_DAILY_ACTIONS,
                painFlag = recentLogs.any { it.painFlag },
                bedtime = bedtime,
                goalType = activeGoal?.type,
                constraints = constraints,
                // The sleep anchor's "In bed by …" title is stored, so it follows the device's
                // 12/24-hour setting the way the rest of Today does.
                use24HourClock = DateFormat.is24HourFormat(appContext),
            ),
        )
        val replaceHolding = !forceReseed &&
            replacesHoldingPlan(repository.activitiesForDay(plan.dateEpochDay), readiness, plan)
        val replacedReminderIds: List<String> = if (forceReseed || replaceHolding) {
            repository.reseedDailyPlan(plan)
        } else {
            repository.seedDailyPlanIfNeeded(plan)
            emptyList()
        }
        // Operational counts only — the readiness band is health-derived and stays out of logs.
        DebugLog.log(
            "sync",
            "ok: activities=${plan.activities.size} stale=${readiness.isStale} replacedHolding=$replaceHolding",
        )
        return Outcome(
            readiness = readiness,
            plan = plan,
            latestSnapshot = latest,
            failed = false,
            bedtime = bedtime,
            replacedHoldingPlan = replaceHolding,
            replacedReminderIds = replacedReminderIds,
        )
    }

    companion object {
        /**
         * How far back a reported pain flag keeps easing the daily plan. Long enough to cover a
         * sore weekend, short enough that it lifts on its own without the user hunting for a reset.
         */
        const val PAIN_LOOKBACK_DAYS = 3L

        /**
         * Checkable actions a day's plan may hold. Adherence research favours a short list — at most
         * five, ideally four — so the cap leaves room for tonight's sleep anchor beside four others.
         */
        const val MAX_DAILY_ACTIONS = 5

        /** The floor of that short list: a day's plan offers at least three actions. */
        const val MIN_DAILY_ACTIONS = 3

        /**
         * Tonight's sleep target from the newest night and the user's own bed / wake times: the
         * night's need itself — WHOOP's own when the data has it, else the estimate
         * ([SleepNeed.forSnapshot]); WHOOP's need already counts debt — never a fixed eight hours. The one
         * place this is derived, so Today's bedtime card and the plan's sleep anchor always agree.
         * Pass [latest] through [lastNight] first, so an older night never sizes tonight's target.
         */
        fun tonightFor(latest: WhoopSnapshot?, constraints: PlanConstraints): BedtimePlan? =
            SleepPlanner.tonight(
                wakeMinuteOfDay = latest?.wakeMinuteOfDay,
                sleepDebtHours = latest?.sleepDebtHours,
                needHours = SleepNeed.forSnapshot(latest),
                userBedtimeMinute = constraints.bedtimeMinute,
                userWakeMinute = constraints.wakeMinute,
            )

        /**
         * The newest snapshot when its night is the one that just ended for [targetEpochDay] — dated
         * that day, or later, which [carryForward] clamps to it — else null. An older night (lagging
         * sync, old import) is never presented as "last night" and never sizes tonight's target.
         */
        fun lastNight(latest: WhoopSnapshot?, targetEpochDay: Long): WhoopSnapshot? =
            latest?.takeIf { it.dateEpochDay >= targetEpochDay }

        /**
         * The plan always lands on [targetEpochDay], the day being planned. Imported (or lagging)
         * wearable data may end before it: the newest readiness is carried forward and marked stale
         * — the coaching engine then eases intensity and says why. A cycle dated after it (an export
         * made in a time zone already a day ahead) is clamped to it and stays a completed cycle, so
         * its plan is saved under the planned day rather than a future one. Either way
         * [ReadinessSnapshot.cycleEpochDay] still names the original cycle.
         */
        fun carryForward(readiness: ReadinessSnapshot, targetEpochDay: Long): ReadinessSnapshot = when {
            readiness.dateEpochDay == targetEpochDay -> readiness
            readiness.dateEpochDay > targetEpochDay -> readiness.copy(dateEpochDay = targetEpochDay)
            else -> readiness.copy(dateEpochDay = targetEpochDay, isStale = true)
        }

        /**
         * Whether [saved] — the day's persisted activities — is a holding plan that [plan] should
         * replace. A plan is seeded once a day, so one saved before today's cycle was scored (the
         * ~04:00 refresh, or the midnight rollover) would otherwise outlive the morning's verdict,
         * and "It updates when today's cycle arrives" would never come true. True when:
         *  - [readiness] is today's completed cycle (not stale, with a recovery score); and
         *  - [saved] still carries the stale-data stand-in ([RuleIds.STALE_DATA_FALLBACK]) not yet
         *    done — or, when the user already did it, [plan] has not been applied yet (some of its
         *    activities are not saved). A replacement removes every stand-in not done, so once
         *    [plan] is in, a completed stand-in kept as history never triggers another one.
         */
        fun replacesHoldingPlan(saved: List<DailyActivity>, readiness: ReadinessSnapshot, plan: DailyPlan): Boolean {
            if (readiness.isStale || readiness.recoveryScore == null) return false
            val standIns = saved.filter { it.ruleId == RuleIds.STALE_DATA_FALLBACK }
            if (standIns.isEmpty()) return false
            if (standIns.any { it.status != ActivityStatus.DONE }) return true
            val savedIds = saved.mapTo(HashSet()) { it.id }
            return plan.activities.any { it.id !in savedIds }
        }
    }
}
