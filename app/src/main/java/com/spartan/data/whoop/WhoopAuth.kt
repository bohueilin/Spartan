package com.spartan.data.whoop

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spartan.data.security.SecureTokenStore
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * WHOOP OAuth 2.0 configuration. Values come from `.env` / `local.properties` → BuildConfig (never
 * committed). With mock data these are blank and unused.
 */
data class WhoopConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val authUrl: String,
    val tokenUrl: String,
    val apiBaseUrl: String,
    val scopes: List<String> = DEFAULT_SCOPES,
) {
    // A client id alone is a valid (PKCE public-client) configuration; the secret is optional and
    // never present in release builds — see the release block in app/build.gradle.kts.
    val isConfigured: Boolean get() = clientId.isNotBlank()

    companion object {
        // Least privilege for the MVP. `read:body_measurement` is intentionally omitted.
        val DEFAULT_SCOPES = listOf(
            "read:recovery", "read:sleep", "read:workout", "read:cycles", "read:profile", "offline",
        )
    }
}

/**
 * Real WHOOP OAuth via AppAuth (authorization-code + PKCE). The UI launches [authorizationIntent];
 * the redirect (`spartan://oauth/whoop`, captured by AppAuth's RedirectUriReceiverActivity) returns
 * an [Intent] passed to [handleAuthResponse], which exchanges the code and persists tokens through
 * [SecureTokenStore]. Tokens are never logged or stored in Room.
 */
class WhoopAuthManager(
    private val context: Context,
    private val config: WhoopConfig,
    private val tokenStore: SecureTokenStore,
    // Lazy so the mock build, which never has a token to revoke, never builds a client.
    private val revokeClient: Lazy<OkHttpClient> = lazy { OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build() },
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(config.authUrl),
        Uri.parse(config.tokenUrl),
    )

    fun isConnected(): Boolean = tokenStore.load(SecureTokenStore.WHOOP_ACCESS) != null

    fun accessToken(): String? = tokenStore.load(SecureTokenStore.WHOOP_ACCESS)

    /** Intent to launch the consent screen. Caller starts it for result and forwards the result to [handleAuthResponse]. */
    fun authorizationIntent(): Intent =
        AuthorizationService(context).getAuthorizationRequestIntent(authorizationRequest())

    internal fun authorizationRequest(): AuthorizationRequest = AuthorizationRequest.Builder(
        serviceConfig,
        config.clientId,
        ResponseTypeValues.CODE,
        Uri.parse(config.redirectUri),
    ).setScope(config.scopes.joinToString(" ")).build()

    suspend fun handleAuthResponse(data: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (response == null) return Result.failure(ex ?: IllegalStateException("No authorization response"))
        return performTokenRequest(response.createTokenExchangeRequest())
    }

    /** Refresh the access token using the stored refresh token. */
    suspend fun refresh(): Result<Unit> {
        val refreshToken = tokenStore.load(SecureTokenStore.WHOOP_REFRESH)
            ?: return Result.failure(IllegalStateException("No WHOOP refresh token"))
        val request = TokenRequest.Builder(serviceConfig, config.clientId)
            .setGrantType("refresh_token")
            .setRefreshToken(refreshToken)
            .build()
        return performTokenRequest(request)
    }

    private suspend fun performTokenRequest(request: TokenRequest): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val service = AuthorizationService(context)
            // Public-client (PKCE-only) when no secret is compiled in — always the case in
            // release builds, where shipping a client secret inside the APK is prohibited.
            val clientAuth =
                if (config.clientSecret.isBlank()) NoClientAuthentication.INSTANCE
                else ClientSecretPost(config.clientSecret)
            service.performTokenRequest(request, clientAuth) { resp, ex ->
                service.dispose()
                if (resp != null) {
                    resp.accessToken?.let { tokenStore.save(SecureTokenStore.WHOOP_ACCESS, it) }
                    resp.refreshToken?.let { tokenStore.save(SecureTokenStore.WHOOP_REFRESH, it) }
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(Result.failure(ex ?: IllegalStateException("Token request failed")))
                }
            }
        }

    /**
     * Deletes the local tokens first (the source of truth), then asks WHOOP to revoke the grant in
     * the background. Best-effort: it never delays or fails a disconnect, and sends nothing when
     * no token was stored.
     */
    fun disconnect() {
        val access = tokenStore.load(SecureTokenStore.WHOOP_ACCESS)
        tokenStore.clear(SecureTokenStore.WHOOP_ACCESS)
        tokenStore.clear(SecureTokenStore.WHOOP_REFRESH)
        if (access == null) return
        val request = Request.Builder()
            .url("${config.apiBaseUrl.trimEnd('/')}/v2/user/access")
            .header("Authorization", "Bearer $access")
            .delete()
            .build()
        revokeClient.value.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }
}
