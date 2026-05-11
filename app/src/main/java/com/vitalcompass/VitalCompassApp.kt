package com.vitalcompass

import android.app.Application
import com.vitalcompass.data.reminder.ReminderWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VitalCompassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderWorker.ensureChannel(this)
    }
}
