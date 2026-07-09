package com.spartan.data.whoop

import com.spartan.domain.model.WhoopSnapshot
import java.time.Duration
import java.time.Instant

/**
 * Real WHOOP Developer API client. Pulls recovery, sleep, and cycle collections for the window and
 * normalizes them via [WhoopResponseMapper] into per-day [WhoopSnapshot]s — so [WhoopMapper] and
 * everything above the client stay unchanged. Bearer auth is injected by the OkHttp interceptor
 * configured in DI. Bound only when `USE_MOCK_WHOOP = false` and credentials are present.
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
        val recovery = api.recovery(startIso, endIso).records
        val sleep = api.sleep(startIso, endIso).records
        val cycle = api.cycle(startIso, endIso).records
        return mapper.toSnapshots(recovery, sleep, cycle).takeLast(days)
    }
}
