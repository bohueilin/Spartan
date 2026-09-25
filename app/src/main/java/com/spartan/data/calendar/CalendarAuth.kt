package com.spartan.data.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spartan.data.security.SecureTokenStore
import com.spartan.domain.model.TimeWindow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Google Calendar OAuth 2.0 config. Least privilege: [readScope] is free/busy only; the write scope
 * is requested only when the user opts into event creation.
 */
data class CalendarConfig(
    val clientId: String,
    val redirectUri: String,
    val readScope: String = FREEBUSY_SCOPE,
    val writeScope: String = EVENTS_SCOPE,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()

    companion object {
        const val FREEBUSY_SCOPE = "https://www.googleapis.com/auth/calendar.freebusy"
        const val EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
    }
}

/**
 * Real Google Calendar OAuth via AppAuth (auth-code + PKCE; installed-app public client, no secret).
 * The write scope is only requested when [includeWriteScope] is set (i.e. the user opted into event
 * creation). Tokens persist only through [SecureTokenStore].
 */
class CalendarAuthManager(
    private val context: Context,
    private val config: CalendarConfig,
    private val tokenStore: SecureTokenStore,
    // Lazy so the stub build, which never has a token to revoke, never builds a client.
    private val revokeClient: Lazy<OkHttpClient> = lazy { OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build() },
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(CalendarConfig.AUTH_URL),
        Uri.parse(CalendarConfig.TOKEN_URL),
    )

    fun isConnected(): Boolean = tokenStore.load(SecureTokenStore.GOOGLE_ACCESS) != null

    fun accessToken(): String? = tokenStore.load(SecureTokenStore.GOOGLE_ACCESS)

    fun authorizationIntent(includeWriteScope: Boolean = false): Intent =
        AuthorizationService(context).getAuthorizationRequestIntent(authorizationRequest(includeWriteScope))

    internal fun authorizationRequest(includeWriteScope: Boolean = false): AuthorizationRequest {
        val scopes = buildList {
            add(config.readScope)
            if (includeWriteScope) add(config.writeScope)
        }.joinToString(" ")
        return AuthorizationRequest.Builder(
            serviceConfig,
            config.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(config.redirectUri),
        ).setScope(scopes).build()
    }

    suspend fun handleAuthResponse(data: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (response == null) return Result.failure(ex ?: IllegalStateException("No authorization response"))
        return performTokenRequest(response.createTokenExchangeRequest())
    }

    suspend fun refresh(): Result<Unit> {
        val refreshToken = tokenStore.load(SecureTokenStore.GOOGLE_REFRESH)
            ?: return Result.failure(IllegalStateException("No Google refresh token"))
        val request = TokenRequest.Builder(serviceConfig, config.clientId)
            .setGrantType("refresh_token")
            .setRefreshToken(refreshToken)
            .build()
        return performTokenRequest(request)
    }

    private suspend fun performTokenRequest(request: TokenRequest): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val service = AuthorizationService(context)
            service.performTokenRequest(request, NoClientAuthentication.INSTANCE) { resp, ex ->
                service.dispose()
                if (resp != null) {
                    resp.accessToken?.let { tokenStore.save(SecureTokenStore.GOOGLE_ACCESS, it) }
                    resp.refreshToken?.let { tokenStore.save(SecureTokenStore.GOOGLE_REFRESH, it) }
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(Result.failure(ex ?: IllegalStateException("Token request failed")))
                }
            }
        }

    /**
     * Deletes the local tokens first (the source of truth), then asks Google to revoke the grant in
     * the background. Best-effort: it never delays or fails a disconnect, and sends nothing when
     * no token was stored.
     */
    fun disconnect() {
        // Revoking the refresh token ends the whole grant; the access token is the fallback.
        val token = tokenStore.load(SecureTokenStore.GOOGLE_REFRESH) ?: tokenStore.load(SecureTokenStore.GOOGLE_ACCESS)
        tokenStore.clear(SecureTokenStore.GOOGLE_ACCESS)
        tokenStore.clear(SecureTokenStore.GOOGLE_REFRESH)
        if (token == null) return
        // Token in the form body, never the URL: URLs end up in server and proxy logs.
        val request = Request.Builder()
            .url(CalendarConfig.REVOKE_URL)
            .post(FormBody.Builder().add("token", token).build())
            .build()
        revokeClient.value.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }
}

/**
 * Real Google Calendar client. Reads use the free/busy endpoint (busy intervals only); event
 * creation is opt-in. Bound only when `USE_MOCK_CALENDAR = false` and credentials are present.
 */
class GoogleCalendarClient(
    private val api: GoogleCalendarApi,
) : CalendarClient {

    override val isStub: Boolean = false

    override suspend fun freeBusy(startEpochMinute: Long, endEpochMinute: Long): List<TimeWindow> {
        val request = FreeBusyRequest(
            timeMin = Instant.ofEpochSecond(startEpochMinute * 60).toString(),
            timeMax = Instant.ofEpochSecond(endEpochMinute * 60).toString(),
        )
        return CalendarResponseMapper.toBusyWindows(api.freeBusy(request))
    }

    override suspend fun createEvent(title: String, startEpochMinute: Long, durationMinutes: Int): Result<String> =
        runCatching {
            val start = Instant.ofEpochSecond(startEpochMinute * 60).toString()
            val end = Instant.ofEpochSecond((startEpochMinute + durationMinutes) * 60).toString()
            val response = api.insertEvent(
                calendarId = "primary",
                event = CalendarEvent(
                    summary = title,
                    description = "Scheduled by Spartan",
                    start = EventDateTime(start),
                    end = EventDateTime(end),
                ),
            )
            response.id ?: "created"
        }
}
