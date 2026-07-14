package com.spartan.data.whoop

import com.spartan.data.local.WhoopCycleDao
import com.spartan.data.whoop.csv.toWhoopSnapshot
import com.spartan.domain.model.WhoopSnapshot

/**
 * Serves the user's imported WHOOP data (CSV export in `whoop_cycles`) when any exists, and
 * falls back to [delegate] (the mock, or the real OAuth client when configured) otherwise.
 * This is what turns a CSV import into "the app now runs on my real data" without touching
 * the sync path: DailyPlanSync keeps calling the same [WhoopClient] seam.
 *
 * Disconnecting WHOOP clears `whoop_cycles`, which automatically reverts this client to the
 * delegate on the next sync.
 */
class LocalFirstWhoopClient(
    private val delegate: WhoopClient,
    private val cycleDao: WhoopCycleDao,
) : WhoopClient {

    /**
     * Reflects the source of the LAST fetch; real imported data is never labeled sample.
     * Until the first fetch of a process this reports the delegate's value — acceptable because
     * MainViewModel syncs on launch and the UI's primary flag rides on the plan's own isMock.
     */
    @Volatile
    private var lastServedImported = false

    override val isMock: Boolean
        get() = if (lastServedImported) false else delegate.isMock

    override suspend fun fetchRecentDays(days: Int): List<WhoopSnapshot> {
        val imported = cycleDao.latestCycles(days.coerceAtLeast(1))
        lastServedImported = imported.isNotEmpty()
        if (imported.isEmpty()) return delegate.fetchRecentDays(days)
        return imported.sortedBy { it.dateEpochDay }.map { it.toWhoopSnapshot() }
    }
}
