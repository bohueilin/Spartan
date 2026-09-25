package com.spartan.data.whoop

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * WHOOP Developer API v2 (base https://api.prod.whoop.com/developer/). Bearer auth is added by an
 * OkHttp interceptor from [com.spartan.data.security.SecureTokenStore]. Read-only endpoints only.
 *
 * v1 is no longer supported by WHOOP. Migration cost here was three path strings: we never
 * deserialize record ids (the v1 long -> v2 UUID change is the migration's main break) and the
 * Json reader ignores unknown keys, so v2's added fields parse harmlessly.
 *
 * Collections are paged: pass a response's `next_token` back as `nextToken`; a null token is omitted.
 */
interface WhoopApi {
    @GET("v2/recovery")
    suspend fun recovery(
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int = 25,
        @Query("nextToken") nextToken: String? = null,
    ): WhoopCollection<WhoopRecoveryRecord>

    @GET("v2/activity/sleep")
    suspend fun sleep(
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int = 25,
        @Query("nextToken") nextToken: String? = null,
    ): WhoopCollection<WhoopSleepRecord>

    @GET("v2/cycle")
    suspend fun cycle(
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int = 25,
        @Query("nextToken") nextToken: String? = null,
    ): WhoopCollection<WhoopCycleRecord>
}
