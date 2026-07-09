package com.spartan.data.whoop

import com.spartan.domain.model.WhoopSnapshot

/**
 * The seam Spartan depends on for wearable data. Implementations:
 *  - [MockWhoopClient]  — clearly-labeled sample data; the default when no credentials exist.
 *  - [RealWhoopClient]  — Phase-2 OAuth2 + REST against the WHOOP Developer API.
 *
 * Returning normalized [WhoopSnapshot]s (not raw WHOOP DTOs) keeps everything above this
 * boundary wearable-agnostic, so additional wearables can be added as new WhoopClient-style
 * adapters without touching the coaching engine or UI.
 */
interface WhoopClient {
    /** Most recent [days] of data, oldest first, today last. May be empty if unavailable. */
    suspend fun fetchRecentDays(days: Int = 7): List<WhoopSnapshot>

    /** Whether this client is serving mock/sample data (surfaced in the UI for honesty). */
    val isMock: Boolean
}
