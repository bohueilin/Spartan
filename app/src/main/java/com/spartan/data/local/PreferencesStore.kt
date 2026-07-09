package com.spartan.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("spartan_preferences")

class PreferencesStore(private val context: Context) {
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETE] ?: false
    }

    val notificationPermissionDenied: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_PERMISSION_DENIED] ?: false
    }

    val demoSeedCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEMO_SEED_COMPLETED] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setNotificationPermissionDenied(denied: Boolean) {
        context.dataStore.edit { it[NOTIFICATION_PERMISSION_DENIED] = denied }
    }

    suspend fun setDemoSeedCompleted(completed: Boolean) {
        context.dataStore.edit { it[DEMO_SEED_COMPLETED] = completed }
    }

    // --- In-app review prompt bookkeeping (timestamps only; no content) ---
    val firstOpenMillis: Flow<Long?> = context.dataStore.data.map { it[FIRST_OPEN_MILLIS] }
    val lastReviewPromptMillis: Flow<Long?> = context.dataStore.data.map { it[LAST_REVIEW_PROMPT_MILLIS] }

    suspend fun recordFirstOpenIfNeeded(nowMillis: Long) {
        context.dataStore.edit { prefs ->
            if (prefs[FIRST_OPEN_MILLIS] == null) prefs[FIRST_OPEN_MILLIS] = nowMillis
        }
    }

    suspend fun setLastReviewPromptMillis(millis: Long) {
        context.dataStore.edit { it[LAST_REVIEW_PROMPT_MILLIS] = millis }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val NOTIFICATION_PERMISSION_DENIED = booleanPreferencesKey("notification_permission_denied")
        val DEMO_SEED_COMPLETED = booleanPreferencesKey("demo_seed_completed")
        val FIRST_OPEN_MILLIS = longPreferencesKey("first_open_millis")
        val LAST_REVIEW_PROMPT_MILLIS = longPreferencesKey("last_review_prompt_millis")
    }
}
