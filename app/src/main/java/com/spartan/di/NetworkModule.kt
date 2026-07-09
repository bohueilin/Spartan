package com.spartan.di

import com.spartan.data.calendar.CalendarAuthManager
import com.spartan.data.calendar.GoogleCalendarApi
import com.spartan.data.security.SecureTokenStore
import com.spartan.data.whoop.WhoopApi
import com.spartan.data.whoop.WhoopAuthManager
import com.spartan.data.whoop.WhoopConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * Networking for the Phase-2 real integrations. These bindings are only exercised when a real
 * client is bound (i.e. `USE_MOCK_* = false` with credentials). Each request carries a bearer token
 * read from [SecureTokenStore]; on a 401 the [Authenticator] transparently refreshes the token once
 * and retries. Tokens never appear in URLs, logs, or request bodies.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // Single-flight guards: concurrent 401s refresh once, not N times (one lock per provider).
    private val whoopRefreshLock = Any()
    private val googleRefreshLock = Any()

    @Provides
    @Singleton
    fun provideWhoopApi(
        json: Json,
        config: WhoopConfig,
        tokenStore: SecureTokenStore,
        authManager: WhoopAuthManager,
    ): WhoopApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(bearerInterceptor(tokenStore, SecureTokenStore.WHOOP_ACCESS))
            .authenticator(refreshAuthenticator(tokenStore, SecureTokenStore.WHOOP_ACCESS, whoopRefreshLock) {
                runBlocking { authManager.refresh() }.isSuccess
            })
            .build()
        return Retrofit.Builder()
            .baseUrl(config.apiBaseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA))
            .build()
            .create(WhoopApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGoogleCalendarApi(
        json: Json,
        tokenStore: SecureTokenStore,
        authManager: CalendarAuthManager,
    ): GoogleCalendarApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(bearerInterceptor(tokenStore, SecureTokenStore.GOOGLE_ACCESS))
            .authenticator(refreshAuthenticator(tokenStore, SecureTokenStore.GOOGLE_ACCESS, googleRefreshLock) {
                runBlocking { authManager.refresh() }.isSuccess
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/calendar/v3/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA))
            .build()
            .create(GoogleCalendarApi::class.java)
    }

    private fun bearerInterceptor(tokenStore: SecureTokenStore, key: String) = Interceptor { chain ->
        val token = tokenStore.load(key)
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        }
        chain.proceed(request)
    }

    /**
     * On a 401, refresh the token once and retry with the new bearer. Gives up after a single
     * attempt (avoids infinite loops if refresh yields a token the server still rejects).
     * Single-flight: refresh runs under [lock]; if another request already refreshed while we
     * waited (token changed under us), we skip the redundant refresh and just retry.
     */
    private fun refreshAuthenticator(
        tokenStore: SecureTokenStore,
        key: String,
        lock: Any,
        refresh: () -> Boolean,
    ) = Authenticator { _: Route?, response: Response ->
        if (priorResponseCount(response) >= 1) return@Authenticator null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val refreshed = synchronized(lock) {
            val current = tokenStore.load(key)
            if (current != null && current != failedToken) true // another caller already refreshed
            else refresh()
        }
        if (!refreshed) return@Authenticator null
        val token = tokenStore.load(key) ?: return@Authenticator null
        response.request.newBuilder().header("Authorization", "Bearer $token").build()
    }

    private fun priorResponseCount(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun String.ensureTrailingSlash() = if (endsWith("/")) this else "$this/"

    private val JSON_MEDIA = "application/json".toMediaType()
}
