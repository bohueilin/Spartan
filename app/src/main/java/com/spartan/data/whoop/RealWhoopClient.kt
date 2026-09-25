package com.spartan.data.whoop

import com.spartan.diagnostics.DebugLog
import com.spartan.domain.model.WhoopSnapshot
import java.time.Duration
import java.time.Instant

/**
 * Real WHOOP Developer API client. Pulls recovery, sleep, and cycle collections for the window and
 * normalizes them via [WhoopResponseMapper] into per-day [WhoopSnapshot]s — so [WhoopMapper] and
 * everything above the client stay unchanged. Bearer auth is injected by the OkHttp interceptor
 * configured in DI. Bound only when `USE_MOCK_WHOOP = false` and credentials are present.
 *
 * Deliberately does not call `v2/activity/workout`: exercise minutes come from the WHOOP CSV import,
 * and pain/RPE adaptation comes from the user's own exercise debriefs.
 */
class RealWhoopClient(
    private val api: WhoopApi,
    private val mapper: WhoopResponseMapper = WhoopResponseMapper(),
) : WhoopClient {

    override val isMock: Boolean = false

    override suspend fun fetchRecentDays(days: Int): List<WhoopSnapshot> {
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days.toLong().coerceAtLeast(1)))
        val startIso = start.toString()
        val endIso = end.toString()
        val recovery = allPages("recovery") { api.recovery(startIso, endIso, nextToken = it) }
        val sleep = allPages("sleep") { api.sleep(startIso, endIso, nextToken = it) }
        val cycle = allPages("cycle") { api.cycle(startIso, endIso, nextToken = it) }
        return mapper.toSnapshots(recovery, sleep, cycle).takeLast(days)
    }

    /** Follows `next_token` until it is blank, repeats (server bug), or [MAX_PAGES] is reached. */
    private suspend fun <T> allPages(name: String, fetch: suspend (String?) -> WhoopCollection<T>): List<T> {
        val records = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        var token: String? = null
        repeat(MAX_PAGES) {
            val page = fetch(token)
            records += page.records
            val next = page.nextToken
            if (next.isNullOrBlank() || !seen.add(next)) return records
            token = next
        }
        DebugLog.log("whoop", "$name paging capped at $MAX_PAGES pages (${records.size} records)")
        return records
    }

    companion object {
        // 25 records/page (WHOOP's max) x 10 = 250 — far above the 7-day window we sync.
        const val MAX_PAGES = 10
    }
}
