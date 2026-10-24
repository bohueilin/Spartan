package com.spartan.data.local

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.PlanConstraints
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

    // --- Goal wizard: plan constraints + weekly check-in bookkeeping (on-device only, never backed up) ---

    /** What the user told the wizard. An absent key reads as "not given" (null / empty), never a guess. */
    val planConstraints: Flow<PlanConstraints> = context.dataStore.data.map { prefs ->
        PlanConstraints(
            minutesPerDay = prefs[PLAN_MINUTES_PER_DAY],
            limitsNote = prefs[PLAN_LIMITS_NOTE].orEmpty(),
            bedtimeMinute = prefs[PLAN_BEDTIME_MINUTE],
            wakeMinute = prefs[PLAN_WAKE_MINUTE],
            avoid = avoidOptionsFrom(prefs[PLAN_AVOID]),
        )
    }

    /** Replaces every constraint at once: a field cleared in the wizard is removed, not left behind. */
    suspend fun setPlanConstraints(constraints: PlanConstraints) {
        context.dataStore.edit { prefs ->
            prefs.setOrRemove(PLAN_MINUTES_PER_DAY, constraints.minutesPerDay)
            prefs.setOrRemove(PLAN_LIMITS_NOTE, constraints.limitsNote.takeIf { it.isNotBlank() })
            prefs.setOrRemove(PLAN_BEDTIME_MINUTE, constraints.bedtimeMinute)
            prefs.setOrRemove(PLAN_WAKE_MINUTE, constraints.wakeMinute)
            prefs.setOrRemove(PLAN_AVOID, constraints.avoid.takeIf { it.isNotEmpty() }?.map { it.name }?.toSet())
        }
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    /** The cycle day the morning reveal last played for, so it plays once per verdict, not per visit. */
    val morningRevealPlayedDay: Flow<Long?> = context.dataStore.data.map { it[MORNING_REVEAL_PLAYED_DAY] }

    suspend fun setMorningRevealPlayedDay(epochDay: Long) {
        context.dataStore.edit { it[MORNING_REVEAL_PLAYED_DAY] = epochDay }
    }

    /** The day of the last weekly goal check-in (kept, changed, paused, resumed, or a new goal). */
    val lastGoalReviewDay: Flow<Long?> = context.dataStore.data.map { it[LAST_GOAL_REVIEW_DAY] }

    suspend fun setLastGoalReviewDay(epochDay: Long) {
        context.dataStore.edit { it[LAST_GOAL_REVIEW_DAY] = epochDay }
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
        val PLAN_MINUTES_PER_DAY = intPreferencesKey("plan_minutes_per_day")
        val PLAN_LIMITS_NOTE = stringPreferencesKey("plan_limits_note")
        val PLAN_BEDTIME_MINUTE = intPreferencesKey("plan_bedtime_minute")
        val PLAN_WAKE_MINUTE = intPreferencesKey("plan_wake_minute")
        val PLAN_AVOID = stringSetPreferencesKey("plan_avoid")
        val MORNING_REVEAL_PLAYED_DAY = longPreferencesKey("morning_reveal_played_day")
        val LAST_GOAL_REVIEW_DAY = longPreferencesKey("last_goal_review_day")
    }
}

/**
 * Stored won't-do names back to options. A name this build doesn't know (an option renamed or
 * removed in an update) is dropped rather than crashing the read or guessing what it meant.
 */
internal fun avoidOptionsFrom(names: Set<String>?): Set<AvoidOption> =
    names.orEmpty().mapNotNull { name -> AvoidOption.entries.firstOrNull { it.name == name } }.toSet()
