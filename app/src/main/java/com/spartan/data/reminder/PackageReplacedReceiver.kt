package com.spartan.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.spartan.diagnostics.DebugLog

/**
 * Re-arms scheduling after an app update. WorkManager persists its own jobs across reboot, but a
 * package replace is the moment to re-assert the periodic refresh and rebuild today's reminders
 * (see docs/Spartan_Enhancements.md §2 — reboot re-arm is WorkManager-native; update re-arm is ours).
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        DebugLog.log("receiver", "package replaced — re-arming refresh")
        val workManager = WorkManager.getInstance(context)
        DailyPlanRefreshWorker.schedule(workManager)
        EveningNudgeWorker.schedule(workManager)
        DailyPlanRefreshWorker.refreshNow(workManager)
    }
}
