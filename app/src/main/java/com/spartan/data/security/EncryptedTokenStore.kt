package com.spartan.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2 secure token storage: OAuth access/refresh tokens encrypted at rest via
 * `EncryptedSharedPreferences` with an AES-256-GCM master key held in the Android Keystore.
 * Bound in DI whenever a real integration is enabled; otherwise [InMemoryTokenStore] is used.
 * Values never touch Room, logs, analytics, or crash reports.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecureTokenStore {

    private val prefs by lazy {
        // Keyset corruption (backup restore to a new device, cleared Keystore, OS bug) would
        // otherwise crash-loop every token access. Recovery is safe here BECAUSE this store holds
        // only re-obtainable OAuth tokens: drop the unreadable file and let the user re-connect.
        try {
            createPrefs()
        } catch (_: Exception) {
            context.deleteSharedPreferences(PREFS_FILE)
            createPrefs()
        }
    }

    private fun createPrefs() = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun load(key: String): String? = prefs.getString(key, null)

    override fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE = "spartan_secure_tokens"
    }
}
