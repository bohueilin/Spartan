package com.spartan.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.spartan.BuildConfig
import com.spartan.data.calendar.CalendarAuthManager
import com.spartan.data.calendar.CalendarClient
import com.spartan.data.calendar.CalendarConfig
import com.spartan.data.calendar.GoogleCalendarApi
import com.spartan.data.calendar.GoogleCalendarClient
import com.spartan.data.calendar.StubCalendarClient
import com.spartan.data.healthconnect.HealthConnectSource
import com.spartan.data.local.AppDatabase
import com.spartan.data.local.HealthDao
import com.spartan.data.local.PreferencesStore
import com.spartan.data.security.EncryptedTokenStore
import com.spartan.data.security.InMemoryTokenStore
import com.spartan.data.security.SecureTokenStore
import com.spartan.data.whoop.MockWhoopClient
import com.spartan.data.whoop.RealWhoopClient
import com.spartan.data.whoop.WhoopApi
import com.spartan.data.whoop.WhoopAuthManager
import com.spartan.data.whoop.WhoopClient
import com.spartan.data.whoop.WhoopConfig
import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.InsightEngine
import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.engine.PlanEngine
import com.spartan.domain.engine.ReminderEngine
import com.spartan.domain.engine.ReviewEngine
import com.spartan.domain.engine.RuleBasedRecommendationSource
import com.spartan.domain.engine.SafetyEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "spartan.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun provideHealthDao(database: AppDatabase): HealthDao = database.healthDao()

    @Provides
    @Singleton
    fun providePreferencesStore(@ApplicationContext context: Context): PreferencesStore = PreferencesStore(context)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    fun provideSafetyEngine(): SafetyEngine = SafetyEngine()

    @Provides
    fun provideMetricEngine(): MetricEngine = MetricEngine()

    @Provides
    fun provideInsightEngine(safetyEngine: SafetyEngine): InsightEngine = InsightEngine(safetyEngine)

    @Provides
    fun providePlanEngine(safetyEngine: SafetyEngine): PlanEngine = PlanEngine(safetyEngine)

    @Provides
    fun provideReviewEngine(planEngine: PlanEngine): ReviewEngine = ReviewEngine(planEngine)

    @Provides
    fun provideReminderEngine(): ReminderEngine = ReminderEngine()

    // --- Spartan coaching ---
    @Provides
    fun provideCoachingEngine(safetyEngine: SafetyEngine): CoachingEngine =
        CoachingEngine(RuleBasedRecommendationSource(safetyEngine), safetyEngine)

    // --- Secure token storage: encrypted when a real integration is enabled; in-memory for mock. ---
    @Provides
    @Singleton
    fun provideSecureTokenStore(
        inMemory: dagger.Lazy<InMemoryTokenStore>,
        encrypted: dagger.Lazy<EncryptedTokenStore>,
    ): SecureTokenStore =
        if (realIntegrationEnabled()) encrypted.get() else inMemory.get()

    // --- WHOOP integration (mock by default; real client is a Phase 2 stub) ---
    @Provides
    @Singleton
    fun provideWhoopConfig(): WhoopConfig = WhoopConfig(
        clientId = BuildConfig.WHOOP_CLIENT_ID,
        clientSecret = BuildConfig.WHOOP_CLIENT_SECRET,
        redirectUri = BuildConfig.WHOOP_REDIRECT_URI,
        authUrl = BuildConfig.WHOOP_AUTH_URL,
        tokenUrl = BuildConfig.WHOOP_TOKEN_URL,
        apiBaseUrl = BuildConfig.WHOOP_API_BASE_URL,
    )

    @Provides
    @Singleton
    fun provideWhoopAuthManager(
        @ApplicationContext context: Context,
        config: WhoopConfig,
        tokenStore: SecureTokenStore,
    ): WhoopAuthManager = WhoopAuthManager(context, config, tokenStore)

    @Provides
    @Singleton
    fun provideWhoopClient(
        config: WhoopConfig,
        mock: dagger.Lazy<MockWhoopClient>,
        api: dagger.Lazy<WhoopApi>,
        healthConnect: dagger.Lazy<HealthConnectSource>,
    ): WhoopClient = when {
        // Health Connect alternative (flag-gated; see HealthConnectSource for enable steps).
        BuildConfig.USE_HEALTH_CONNECT -> healthConnect.get()
        BuildConfig.USE_MOCK_WHOOP || !config.isConfigured -> mock.get()
        else -> RealWhoopClient(api.get())
    }

    // --- Google Calendar integration (stub by default; real client is a Phase 2 stub) ---
    @Provides
    @Singleton
    fun provideCalendarConfig(): CalendarConfig = CalendarConfig(
        clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
        redirectUri = BuildConfig.GOOGLE_OAUTH_REDIRECT_URI,
    )

    @Provides
    @Singleton
    fun provideCalendarAuthManager(
        @ApplicationContext context: Context,
        config: CalendarConfig,
        tokenStore: SecureTokenStore,
    ): CalendarAuthManager = CalendarAuthManager(context, config, tokenStore)

    @Provides
    @Singleton
    fun provideCalendarClient(
        config: CalendarConfig,
        stub: dagger.Lazy<StubCalendarClient>,
        api: dagger.Lazy<GoogleCalendarApi>,
    ): CalendarClient =
        if (BuildConfig.USE_MOCK_CALENDAR || !config.isConfigured) stub.get()
        else GoogleCalendarClient(api.get())

    private fun realIntegrationEnabled(): Boolean =
        !BuildConfig.USE_MOCK_WHOOP || !BuildConfig.USE_MOCK_CALENDAR
}
