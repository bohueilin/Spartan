package com.spartan.data

import com.spartan.data.whoop.WhoopResponseMapper
import com.spartan.data.whoop.WhoopSleepNeeded
import com.spartan.data.whoop.WhoopSleepRecord
import com.spartan.data.whoop.WhoopSleepScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * The API path's sleep need: WHOOP's sleep_needed breakdown becomes one number of hours on the
 * snapshot — baseline + debt + recent strain, less the recent-nap credit — taken from the main
 * sleep only, and never invented when the baseline is missing.
 */
class WhoopSleepNeedApiTest {

    private val mapper = WhoopResponseMapper(ZoneId.of("UTC"))
    private val hour = 3_600_000L

    private fun sleep(need: WhoopSleepNeeded?, nap: Boolean = false, end: String = "2024-01-02T07:00:00Z") =
        WhoopSleepRecord(
            start = "2024-01-01T23:00:00Z",
            end = end,
            nap = nap,
            scoreState = "SCORED",
            score = WhoopSleepScore(sleepPerformancePercentage = 85.0, sleepNeeded = need),
        )

    private fun needFor(vararg sleeps: WhoopSleepRecord): Double? =
        mapper.toSnapshots(emptyList(), sleeps.toList(), emptyList()).single().sleepNeedHours

    @Test
    fun needIsBaselinePlusDebtPlusStrain_lessTheNapCredit() {
        val need = WhoopSleepNeeded(
            baselineMilli = 15 * hour / 2, // 7.5 h
            needFromSleepDebtMilli = hour / 2, // +0.5 h
            needFromRecentStrainMilli = hour / 4, // +0.25 h
            needFromRecentNapMilli = -hour / 2, // WHOOP reports the nap credit as a reduction
        )
        assertEquals(7.75, needFor(sleep(need))!!, 1e-9)
    }

    @Test
    fun aNapCreditSentAsAPositiveNumberStillReducesTheNeed() {
        val need = WhoopSleepNeeded(baselineMilli = 8 * hour, needFromRecentNapMilli = hour / 2)
        assertEquals(7.5, needFor(sleep(need))!!, 1e-9)
    }

    @Test
    fun missingAddOnsCountAsNothing_andTheNeedRoundsToTwoDecimals() {
        // 28,830,000 ms = 8.00833 h.
        assertEquals(8.01, needFor(sleep(WhoopSleepNeeded(baselineMilli = 28_830_000L)))!!, 1e-9)
    }

    @Test
    fun withoutABaselineThereIsNoNeed() {
        assertNull(needFor(sleep(WhoopSleepNeeded(needFromSleepDebtMilli = hour))))
        assertNull(needFor(sleep(need = null)))
    }

    @Test
    fun aNapsBreakdownNeverSetsTheNightsNeed() {
        val main = sleep(WhoopSleepNeeded(baselineMilli = 8 * hour))
        val nap = sleep(WhoopSleepNeeded(baselineMilli = 2 * hour), nap = true, end = "2024-01-02T15:00:00Z")
        assertEquals(8.0, needFor(main, nap)!!, 1e-9)
    }
}
