package com.spartan.data.security

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction for storing sensitive OAuth tokens. Tokens are NEVER written to Room, logs,
 * analytics, or crash reports.
 *
 * AppModule binds [EncryptedTokenStore] (Keystore-backed) when a real integration is enabled, and
 * [InMemoryTokenStore] for the default mock build, which has no real tokens to protect.
 */
interface SecureTokenStore {
    fun save(key: String, value: String)
    fun load(key: String): String?
    fun clear(key: String)
    fun clearAll()

    companion object {
        // Namespaced keys keep providers isolated.
        const val WHOOP_ACCESS = "whoop.access_token"
        const val WHOOP_REFRESH = "whoop.refresh_token"
        const val GOOGLE_ACCESS = "google.access_token"
        const val GOOGLE_REFRESH = "google.refresh_token"
    }
}

/**
 * Default Phase-1 implementation. Holds tokens only in process memory, so nothing sensitive is
 * ever persisted to disk. Cleared on process death and on data deletion.
 */
@Singleton
class InMemoryTokenStore @Inject constructor() : SecureTokenStore {
    private val map = ConcurrentHashMap<String, String>()
    override fun save(key: String, value: String) { map[key] = value }
    override fun load(key: String): String? = map[key]
    override fun clear(key: String) { map.remove(key) }
    override fun clearAll() { map.clear() }
}
