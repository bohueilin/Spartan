package com.spartan.data.whoop

import com.spartan.BuildConfig
import com.spartan.diagnostics.DebugLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RealWhoopClientTest {

    /** Serves canned pages keyed by the `nextToken` it is asked for, and records every request. */
    private class FakeWhoopApi(
        val recoveryPages: Map<String?, WhoopCollection<WhoopRecoveryRecord>> = emptyMap(),
        val sleepPages: Map<String?, WhoopCollection<WhoopSleepRecord>> = emptyMap(),
        val cyclePages: Map<String?, WhoopCollection<WhoopCycleRecord>> = emptyMap(),
    ) : WhoopApi {
        val calls = mutableListOf<String>()
        override suspend fun recovery(start: String, end: String, limit: Int, nextToken: String?) =
            recoveryPages[nextToken].also { calls += "recovery:$nextToken" } ?: WhoopCollection()
        override suspend fun sleep(start: String, end: String, limit: Int, nextToken: String?) =
            sleepPages[nextToken].also { calls += "sleep:$nextToken" } ?: WhoopCollection()
        override suspend fun cycle(start: String, end: String, limit: Int, nextToken: String?) =
            cyclePages[nextToken].also { calls += "cycle:$nextToken" } ?: WhoopCollection()
    }

    private fun client(api: WhoopApi) = RealWhoopClient(api, WhoopResponseMapper(ZoneId.of("UTC")))
    private fun day(d: Int) = LocalDate.of(2024, 1, d).toEpochDay()
    private fun recovery(d: Int, score: Double) =
        WhoopRecoveryRecord(createdAt = "2024-01-0${d}T07:00:00Z", score = WhoopRecoveryScore(recoveryScore = score))
    private fun sleep(d: Int, perf: Double, nap: Boolean = false) = WhoopSleepRecord(
        start = "2024-01-0${d}T01:00:00Z", end = "2024-01-0${d}T06:00:00Z", nap = nap,
        score = WhoopSleepScore(sleepPerformancePercentage = perf),
    )
    private fun cycle(d: Int, strain: Double) =
        WhoopCycleRecord(start = "2024-01-0${d}T00:00:00Z", score = WhoopCycleScore(strain = strain))
    // DebugLog is a process-wide buffer, so tests compare counts before/after rather than absolutes.
    private fun cappedLines() = DebugLog.entries().filter { "paging capped" in it }

    @Test
    fun followsNextToken_andAccumulatesEveryPage_forAllThreeCollections() = runTest {
        val api = FakeWhoopApi(
            recoveryPages = mapOf(null to WhoopCollection(listOf(recovery(2, 50.0)), "r2"), "r2" to WhoopCollection(listOf(recovery(1, 40.0)))),
            sleepPages = mapOf(null to WhoopCollection(listOf(sleep(2, 80.0)), "s2"), "s2" to WhoopCollection(listOf(sleep(1, 70.0)))),
            cyclePages = mapOf(null to WhoopCollection(listOf(cycle(2, 10.0)), "c2"), "c2" to WhoopCollection(listOf(cycle(1, 8.0)))),
        )
        val cappedBefore = cappedLines().size

        val days = client(api).fetchRecentDays(7)

        assertEquals("a complete fetch must not report truncation", cappedBefore, cappedLines().size)
        assertEquals(listOf("recovery:null", "recovery:r2", "sleep:null", "sleep:s2", "cycle:null", "cycle:c2"), api.calls)
        assertEquals(listOf(day(1), day(2)), days.map { it.dateEpochDay })
        assertEquals(listOf(40, 50), days.map { it.recoveryScore })
        assertEquals(listOf(70, 80), days.map { it.sleepPerformance })
        assertEquals(listOf(8.0, 10.0), days.map { it.dayStrain })
    }

    @Test
    fun blankNextToken_endsPaging() = runTest {
        val api = FakeWhoopApi(recoveryPages = mapOf(null to WhoopCollection(listOf(recovery(1, 40.0)), " ")))
        client(api).fetchRecentDays(7)
        assertEquals(listOf("recovery:null", "sleep:null", "cycle:null"), api.calls)
    }

    @Test
    fun repeatedNextToken_stopsInsteadOfLooping() = runTest {
        val api = FakeWhoopApi(
            recoveryPages = mapOf(
                null to WhoopCollection(listOf(recovery(1, 40.0)), "a"),
                "a" to WhoopCollection(listOf(recovery(2, 50.0)), "a"),
            ),
        )
        val days = client(api).fetchRecentDays(7)
        assertEquals(listOf("recovery:null", "recovery:a", "sleep:null", "cycle:null"), api.calls)
        assertEquals(listOf(40, 50), days.map { it.recoveryScore })
    }

    @Test
    fun endlessTokens_stopAtPageCap_andLogTruncationWithoutTokens() = runTest {
        val tokens = listOf(null) + (1..20).map { "tok-$it" }
        val api = FakeWhoopApi(
            recoveryPages = tokens.zipWithNext().associate { (t, next) -> t to WhoopCollection(listOf(recovery(1, 40.0)), next) },
        )
        val cappedBefore = cappedLines().size

        client(api).fetchRecentDays(7)

        assertEquals(RealWhoopClient.MAX_PAGES, api.calls.count { it.startsWith("recovery:") })
        // DebugLog is a no-op in release builds, which testReleaseUnitTest (run by Kover) exercises.
        if (!BuildConfig.DEBUG) return@runTest
        assertEquals(cappedBefore + 1, cappedLines().size)
        val line = cappedLines().first()
        assertTrue(line.contains("recovery paging capped at ${RealWhoopClient.MAX_PAGES} pages"))
        assertFalse(line.contains("tok-"))
    }

    @Test
    fun napOnLaterPage_doesNotReplaceThatDaysMainSleep() = runTest {
        // The nap is iterated last, so without the mapper's nap filter it would overwrite the main sleep.
        val api = FakeWhoopApi(
            sleepPages = mapOf(
                null to WhoopCollection(listOf(sleep(2, 85.0)), "s2"),
                "s2" to WhoopCollection(listOf(sleep(2, 40.0, nap = true))),
            ),
        )
        assertEquals(85, client(api).fetchRecentDays(7).single().sleepPerformance)
    }
}
