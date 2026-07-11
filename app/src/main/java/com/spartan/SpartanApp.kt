package com.spartan

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.spartan.data.reminder.DailyPlanRefreshWorker
import com.spartan.data.reminder.EveningNudgeWorker
import com.spartan.data.reminder.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpartanApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // On-demand WorkManager init with Hilt-injected workers (default initializer removed in manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        ReminderWorker.ensureChannel(this)
        // Keep the ~04:00 daily plan refresh and the ~19:00 evening nudge armed
        // (KEEP policy — harmless if already enqueued).
        val workManager = WorkManager.getInstance(this)
        DailyPlanRefreshWorker.schedule(workManager)
        EveningNudgeWorker.schedule(workManager)
    }

    /** Debug-only: surface accidental main-thread I/O and leaked closables early. Never in release. */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
    }
}
