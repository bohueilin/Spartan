package com.spartan.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.spartan.data.local.HealthDao
import com.spartan.data.local.toDomain
import com.spartan.domain.model.ActivityStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Home-screen widget: the next planned activity, glanceable without launching the app — the daily
 * loop minus a step. Read-only projection over the repository; tapping deep-links to the check-in
 * (spartan://today). Brand-dark like the app; refreshed by [com.spartan.data.reminder.DailyPlanRefreshWorker]
 * and the standard widget update period.
 */
class NextActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextActivityWidget()
}

class NextActivityWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun healthDao(): HealthDao
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .healthDao()
        val today = LocalDate.now().toEpochDay()
        val next = runCatching {
            dao.observeActivitiesForDay(today).first()
                .map { it.toDomain() }
                .filter { it.status == ActivityStatus.PLANNED || it.status == ActivityStatus.RESCHEDULED }
                .minByOrNull { it.priority.ordinal * 10 + it.bestTimeOfDay.ordinal }
        }.getOrNull()

        provideContent {
            val openToday = actionStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("spartan://today")).setPackage(context.packageName),
            )
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF121817)))
                    .cornerRadius(18.dp)
                    .padding(14.dp)
                    .clickable(openToday),
            ) {
                Text(
                    text = "SPARTAN",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF3FE0C8)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (next != null) {
                    Text(
                        text = next.title,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFEAF1EF)),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 2,
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "~${next.estimatedMinutes} min",
                        style = TextStyle(color = ColorProvider(Color(0xFF9DB0AB)), fontSize = 12.sp),
                        modifier = GlanceModifier.padding(top = 2.dp),
                    )
                } else {
                    Text(
                        text = "All done for today",
                        style = TextStyle(color = ColorProvider(Color(0xFFEAF1EF)), fontSize = 14.sp),
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
