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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.spartan.R
import com.spartan.data.local.HealthDao
import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.local.toDomain
import com.spartan.data.repository.HealthRepository
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.MetricType
import com.spartan.ui.theme.SpartanBands
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The widget's verdict line: today's WHOOP recovery as its colour word and number together, never
 * colour alone. [isSample] marks a reading from the sample feed, which the widget says out loud.
 */
internal data class WidgetVerdict(val zone: RecoveryZone, val recoveryScore: Int, val isSample: Boolean)

/**
 * The verdict from [todaysRecovery] — today's RECOVERY_SCORE row ([HealthDao.latestReadingOnDay]) —
 * or null, shown as "Plan pending", when there is none. Never a guess from an older day.
 */
internal fun widgetVerdict(todaysRecovery: MetricEntryEntity?): WidgetVerdict? {
    val reading = todaysRecovery ?: return null
    val score = reading.value?.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 100) ?: return null
    return WidgetVerdict(
        zone = RecoveryZone.of(score),
        recoveryScore = score,
        isSample = reading.note == HealthRepository.SAMPLE_WHOOP_NOTE,
    )
}

/**
 * WHOOP's recovery colour on the widget's dark surface (#121817, relative luminance 0.0085). WCAG
 * contrast (L1 + 0.05) / (L2 + 0.05): primedDark 8.96:1, easyDark 9.3:1, restDark 6.2:1 — all past
 * 4.5:1 for text, and always beside the zone's word and number.
 */
private fun zoneOnDark(zone: RecoveryZone) = when (zone) {
    RecoveryZone.GREEN -> SpartanBands.primedDark
    RecoveryZone.YELLOW -> SpartanBands.easyDark
    RecoveryZone.RED -> SpartanBands.restDark
}

/**
 * Home-screen widget: today's verdict and the next planned activity — nothing else, so it reads at
 * a glance and the app keeps the depth. Read-only projection over the DAO; tapping deep-links to
 * the check-in (spartan://today). Brand-dark like the app; refreshed by
 * [com.spartan.data.reminder.DailyPlanRefreshWorker] and the standard widget update period.
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
        val activities = runCatching {
            dao.observeActivitiesForDay(today).first().map { it.toDomain() }
        }.getOrNull().orEmpty()
        // Snoozed means "remind me later", not done — it stays in the open set.
        val next = activities
            .filter {
                it.status == ActivityStatus.PLANNED ||
                    it.status == ActivityStatus.RESCHEDULED ||
                    it.status == ActivityStatus.SNOOZED
            }
            .minByOrNull { it.priority.ordinal * 10 + it.bestTimeOfDay.ordinal }
        // "All done" is earned, never fabricated: an empty day (fresh install, post-erase) says
        // "No plan yet", and a day of only-skipped activities never claims completion.
        val anyDone = activities.any { it.status == ActivityStatus.DONE }
        val verdict = runCatching {
            widgetVerdict(dao.latestReadingOnDay(MetricType.RECOVERY_SCORE, today))
        }.getOrNull()
        val muted = ColorProvider(Color(0xFF9DB0AB))

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
                if (verdict != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = context.getString(
                                R.string.data_widget_verdict,
                                verdict.zone.label,
                                verdict.recoveryScore,
                            ),
                            style = TextStyle(
                                color = ColorProvider(zoneOnDark(verdict.zone)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = GlanceModifier.semantics {
                                contentDescription = context.getString(
                                    R.string.data_widget_verdict_a11y,
                                    verdict.zone.label,
                                    verdict.recoveryScore,
                                )
                            },
                        )
                        if (verdict.isSample) {
                            Text(
                                text = context.getString(R.string.data_widget_sample),
                                style = TextStyle(color = muted, fontSize = 11.sp),
                                modifier = GlanceModifier.padding(start = 8.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = context.getString(R.string.data_widget_plan_pending),
                        style = TextStyle(color = muted, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                }
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
                        text = context.getString(R.string.widget_minutes, next.estimatedMinutes),
                        style = TextStyle(color = muted, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(top = 2.dp),
                    )
                } else {
                    Text(
                        text = when {
                            activities.isEmpty() -> context.getString(R.string.widget_no_plan)
                            anyDone -> context.getString(R.string.widget_all_done)
                            else -> context.getString(R.string.widget_none_left)
                        },
                        style = TextStyle(color = ColorProvider(Color(0xFFEAF1EF)), fontSize = 14.sp),
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
