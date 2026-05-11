package com.vitalcompass.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.vitalcompass.data.local.AppDatabase
import com.vitalcompass.data.local.HealthDao
import com.vitalcompass.data.local.PreferencesStore
import com.vitalcompass.domain.engine.InsightEngine
import com.vitalcompass.domain.engine.MetricEngine
import com.vitalcompass.domain.engine.PlanEngine
import com.vitalcompass.domain.engine.ReminderEngine
import com.vitalcompass.domain.engine.ReviewEngine
import com.vitalcompass.domain.engine.SafetyEngine
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
        Room.databaseBuilder(context, AppDatabase::class.java, "vital_compass.db").build()

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
    fun providePlanEngine(): PlanEngine = PlanEngine()

    @Provides
    fun provideReviewEngine(planEngine: PlanEngine): ReviewEngine = ReviewEngine(planEngine)

    @Provides
    fun provideReminderEngine(): ReminderEngine = ReminderEngine()
}
