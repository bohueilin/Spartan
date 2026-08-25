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

    /**
     * True once the user has answered the in-app reminders offer (either way). The system
     * permission dialog is one-shot per install, so it is only ever launched from an explicit tap
     * on that offer — never automatically at launch.
     */
    val remindersOfferSettled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REMINDERS_OFFER_SETTLED] ?: false
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

    suspend fun setRemindersOfferSettled(settled: Boolean) {
        context.dataStore.edit { it[REMINDERS_OFFER_SETTLED] = settled }
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
        val REMINDERS_OFFER_SETTLED = booleanPreferencesKey("reminders_offer_settled")
        val FIRST_OPEN_MILLIS = longPreferencesKey("first_open_millis")
        val LAST_REVIEW_PROMPT_MILLIS = longPreferencesKey("last_review_prompt_millis")
    }
}
