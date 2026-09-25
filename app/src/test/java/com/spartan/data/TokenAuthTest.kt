package com.spartan.data

import com.spartan.data.security.InMemoryTokenStore
import com.spartan.data.security.SecureTokenStore.Companion.GOOGLE_ACCESS
import com.spartan.data.security.SecureTokenStore.Companion.WHOOP_ACCESS
import com.spartan.data.security.SecureTokenStore.Companion.WHOOP_REFRESH
import com.spartan.di.NetworkModule
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Token storage plus the bearer/401-refresh plumbing every real API call goes through. */
class TokenAuthTest {

    private val store = InMemoryTokenStore()
    private var refreshCalls = 0

    @Test
    fun inMemoryStore_roundTripsAndClears() {
        store.save(WHOOP_ACCESS, "a")
        store.save(WHOOP_REFRESH, "r")
        store.save(GOOGLE_ACCESS, "g")
        assertEquals("a", store.load(WHOOP_ACCESS))

        store.clear(WHOOP_ACCESS)
        assertNull(store.load(WHOOP_ACCESS))
        assertEquals("r", store.load(WHOOP_REFRESH))

        store.clearAll()
        assertNull(store.load(WHOOP_REFRESH))
        assertNull(store.load(GOOGLE_ACCESS))
    }

    @Test
    fun bearerInterceptor_addsHeaderOnlyWhenTokenStored() {
        val seen = mutableListOf<Request>()
        // The second interceptor answers in place of the server, so no network is touched.
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkModule.bearerInterceptor(store, WHOOP_ACCESS))
            .addInterceptor { chain ->
                seen += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
            .build()
        val call = { client.newCall(Request.Builder().url(URL).build()).execute().close() }

        call()
        store.save(WHOOP_ACCESS, "t1")
        call()

        assertNull(seen[0].header("Authorization"))
        assertEquals("Bearer t1", seen[1].header("Authorization"))
    }

    @Test
    fun authenticator_refreshesOnceAndRetriesWithNewBearer() {
        store.save(WHOOP_ACCESS, "old")
        val retry = authenticator { store.save(WHOOP_ACCESS, "new"); true }.authenticate(null, unauthorized("old"))
        assertEquals("Bearer new", retry?.header("Authorization"))
        assertEquals(1, refreshCalls)
    }

    @Test
    fun authenticator_givesUpWhenRefreshFails() {
        store.save(WHOOP_ACCESS, "old")
        assertNull(authenticator { false }.authenticate(null, unauthorized("old")))
        assertEquals(1, refreshCalls)
    }

    @Test
    fun authenticator_givesUpOnSecond401WithoutRefreshing() {
        store.save(WHOOP_ACCESS, "new")
        val second = unauthorized("new", prior = unauthorized("old"))
        assertNull(authenticator { true }.authenticate(null, second))
        assertEquals(0, refreshCalls)
    }

    @Test
    fun authenticator_skipsRedundantRefreshWhenTokenAlreadyChanged() {
        store.save(WHOOP_ACCESS, "fresh") // another caller refreshed while this request was in flight
        val retry = authenticator { true }.authenticate(null, unauthorized("stale"))
        assertEquals("Bearer fresh", retry?.header("Authorization"))
        assertEquals(0, refreshCalls)
    }

    private fun authenticator(refresh: () -> Boolean) =
        NetworkModule.refreshAuthenticator(store, WHOOP_ACCESS, Any()) { refreshCalls++; refresh() }

    private fun unauthorized(bearer: String, prior: Response? = null): Response =
        Response.Builder()
            .request(Request.Builder().url(URL).header("Authorization", "Bearer $bearer").build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(prior)
            .build()

    private companion object {
        const val URL = "https://api.example.test/v2/cycle"
    }
}
