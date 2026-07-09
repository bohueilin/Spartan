package com.spartan.data.whoop

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * WHOOP Developer API v1 (base https://api.prod.whoop.com/developer/). Bearer auth is added by an
 * OkHttp interceptor from [com.spartan.data.security.SecureTokenStore]. Read-only endpoints only.
 */
interface WhoopApi {
    @GET("v1/recovery")
    suspend fun recovery(
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int = 25,
    ): WhoopCollection<WhoopRecoveryRecord>

    @GET("v1/activity/sleep")
    suspend fun sleep(
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int = 25,
    ): WhoopCollection<WhoopSleepRecord>

    @GET("v1/cycle")
    suspend fun cycle(
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int = 25,
    ): WhoopCollection<WhoopCycleRecord>
}
