package com.spartan.robolectric

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.spartan.data.calendar.CalendarAuthManager
import com.spartan.data.calendar.CalendarConfig
import com.spartan.data.security.InMemoryTokenStore
import com.spartan.data.security.SecureTokenStore.Companion.GOOGLE_ACCESS
import com.spartan.data.security.SecureTokenStore.Companion.GOOGLE_REFRESH
import com.spartan.data.security.SecureTokenStore.Companion.WHOOP_ACCESS
import com.spartan.data.security.SecureTokenStore.Companion.WHOOP_REFRESH
import com.spartan.data.whoop.WhoopAuthManager
import com.spartan.data.whoop.WhoopConfig
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthorizationRequest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** OAuth managers: PKCE/scopes of the consent request, and disconnect = local clear + best-effort revoke. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = InMemoryTokenStore()
    private val sent = CopyOnWriteArrayList<Request>()
    // Per request: sent off the main thread, after the local tokens were already gone.
    private val sentInBackgroundAfterClear = CopyOnWriteArrayList<Boolean>()
    private var failure: IOException? = null

    // Answers in place of WHOOP/Google (or fails like a dead network), so no real request is made.
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        sent += chain.request()
        sentInBackgroundAfterClear += !Looper.getMainLooper().isCurrentThread &&
            listOf(WHOOP_ACCESS, WHOOP_REFRESH, GOOGLE_ACCESS, GOOGLE_REFRESH).all { store.load(it) == null }
        failure?.let { throw it }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("".toResponseBody()).build()
    }.build()

    private val whoop = WhoopAuthManager(
        context,
        WhoopConfig(
            clientId = "client-id",
            clientSecret = "",
            redirectUri = "spartan://oauth/whoop",
            authUrl = "https://auth.example.test/oauth2/auth",
            tokenUrl = "https://auth.example.test/oauth2/token",
            apiBaseUrl = "https://api.example.test/developer/", // trailing slash must not double up
        ),
        store,
        lazyOf(client),
    )
    private val calendar = CalendarAuthManager(
        context,
        CalendarConfig(clientId = "client-id", redirectUri = "com.spartan:/oauth2redirect"),
        store,
        lazyOf(client),
    )

    @Test
    fun whoopAuthorizationRequest_usesPkceS256AndLeastPrivilegeScopes() {
        val request = whoop.authorizationRequest()
        assertEquals(
            setOf("read:recovery", "read:sleep", "read:workout", "read:cycles", "read:profile", "offline"),
            request.scopeSet,
        )
        assertEquals("spartan://oauth/whoop", request.redirectUri.toString())
        assertPkceS256(request)
    }

    @Test
    fun calendarAuthorizationRequest_asksForWriteScopeOnlyWhenOptedIn() {
        val readOnly = calendar.authorizationRequest()
        assertEquals(setOf(CalendarConfig.FREEBUSY_SCOPE), readOnly.scopeSet)
        assertEquals(
            setOf(CalendarConfig.FREEBUSY_SCOPE, CalendarConfig.EVENTS_SCOPE),
            calendar.authorizationRequest(includeWriteScope = true).scopeSet,
        )
        assertEquals("com.spartan:/oauth2redirect", readOnly.redirectUri.toString())
        assertPkceS256(readOnly)
    }

    @Test
    fun whoopDisconnect_clearsTokensThenRevokesWithBearerHeader() {
        store.save(WHOOP_ACCESS, "acc")
        store.save(WHOOP_REFRESH, "ref")

        whoop.disconnect()
        // Cleared synchronously: the local clear never waits on the network.
        assertNull(store.load(WHOOP_ACCESS))
        assertNull(store.load(WHOOP_REFRESH))

        awaitRevokes()
        val revoke = sent.single()
        assertEquals("DELETE", revoke.method)
        assertEquals("https://api.example.test/developer/v2/user/access", revoke.url.toString())
        assertEquals("Bearer acc", revoke.header("Authorization"))
        assertEquals(listOf(true), sentInBackgroundAfterClear)
    }

    @Test
    fun calendarDisconnect_clearsTokensThenRevokesRefreshTokenInFormBody() {
        store.save(GOOGLE_ACCESS, "acc")
        store.save(GOOGLE_REFRESH, "ref")

        calendar.disconnect()
        assertNull(store.load(GOOGLE_ACCESS))
        assertNull(store.load(GOOGLE_REFRESH))

        awaitRevokes()
        val revoke = sent.single()
        assertEquals("POST", revoke.method)
        assertEquals(CalendarConfig.REVOKE_URL, revoke.url.toString()) // token is not in the URL
        assertEquals("application/x-www-form-urlencoded", revoke.body?.contentType().toString())
        assertEquals("token=ref", revoke.bodyText())
        assertEquals(listOf(true), sentInBackgroundAfterClear)
    }

    @Test
    fun calendarDisconnect_fallsBackToAccessTokenWithoutRefreshToken() {
        store.save(GOOGLE_ACCESS, "acc")
        calendar.disconnect()
        awaitRevokes()
        assertEquals("token=acc", sent.single().bodyText())
    }

    @Test
    fun disconnect_withNoStoredToken_sendsNothing() {
        whoop.disconnect()
        calendar.disconnect()
        awaitRevokes()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun failedRevoke_isSwallowedAndTokensStayCleared() {
        failure = InterruptedIOException("timeout")
        store.save(WHOOP_ACCESS, "acc")
        store.save(GOOGLE_REFRESH, "ref")

        whoop.disconnect()
        calendar.disconnect()
        awaitRevokes()

        assertEquals(2, sent.size)
        assertNull(store.load(WHOOP_ACCESS))
        assertNull(store.load(GOOGLE_REFRESH))
    }

    @Test
    fun refresh_withoutRefreshToken_failsImmediately() = runBlocking {
        store.save(WHOOP_ACCESS, "acc")
        store.save(GOOGLE_ACCESS, "acc")
        assertTrue(whoop.refresh().isFailure)
        assertTrue(calendar.refresh().isFailure)
    }

    /** Lets any background revoke call finish before asserting on it. */
    private fun awaitRevokes() {
        client.dispatcher.executorService.shutdown()
        client.dispatcher.executorService.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun Request.bodyText() = Buffer().also { body!!.writeTo(it) }.readUtf8()

    private fun assertPkceS256(request: AuthorizationRequest) {
        assertEquals("S256", request.codeVerifierChallengeMethod)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(request.codeVerifier!!.toByteArray())
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(sha256), request.codeVerifierChallenge)
    }
}
