package com.spartan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spartan.BuildConfig
import com.spartan.R
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.SexAtBirth
import com.spartan.ui.components.ListRow
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.ScreenTitle
import com.spartan.ui.components.SectionHeader
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.theme.Spacing
import java.util.Locale

/**
 * You: who Spartan is coaching, the plan settings you set once and revisit rarely, your data
 * connections, and every privacy control — the profile tab of a travel app, for your health.
 */
@Composable
fun YouScreen(
    state: MainUiState,
    onSaveDemographics: (Int?, SexAtBirth) -> Unit,
    onSaveGoal: (GoalType, Double, Int) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenWindows: () -> Unit,
    onOpenRanges: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: (() -> Unit)?,
) {
    var showProfileSheet by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }
    val rows = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xs)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { ScreenTitle(stringResource(R.string.you_title)) }
            item { ProfileCard(state) { showProfileSheet = true } }

            item { SectionHeader(stringResource(R.string.you_section_plan)) }
            item {
                SpartanCard(contentPadding = rows) {
                    ListRow(
                        Icons.Outlined.Flag,
                        stringResource(R.string.you_goal),
                        state.goalProgress?.summary ?: stringResource(R.string.you_goal_none),
                        onClick = { if (state.activeGoal != null) onOpenProgress() else showGoalSheet = true },
                    )
                    ListRow(Icons.AutoMirrored.Outlined.EventNote, stringResource(R.string.plan_title), stringResource(R.string.you_training_subtitle), onOpenTraining)
                    ListRow(
                        Icons.Outlined.Schedule,
                        stringResource(R.string.you_windows),
                        if (state.pressureWindows.isEmpty()) stringResource(R.string.you_windows_none)
                        else stringResource(R.string.you_windows_count, state.pressureWindows.size),
                        onOpenWindows,
                    )
                    ListRow(Icons.Outlined.Tune, stringResource(R.string.you_ranges), stringResource(R.string.you_ranges_subtitle), onOpenRanges, showDivider = false)
                }
            }

            item { SectionHeader(stringResource(R.string.you_section_data)) }
            item {
                SpartanCard(contentPadding = rows) {
                    ListRow(Icons.Outlined.Link, stringResource(R.string.settings_connections), stringResource(R.string.settings_connections_subtitle), onOpenConnections)
                    ListRow(Icons.Outlined.Notifications, stringResource(R.string.settings_reminders), stringResource(R.string.settings_reminders_subtitle), onOpenReminders, showDivider = false)
                }
            }

            item { SectionHeader(stringResource(R.string.you_section_privacy)) }
            item {
                SpartanCard(contentPadding = rows) {
                    ListRow(Icons.Outlined.PrivacyTip, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_subtitle), onOpenPrivacy, showDivider = onOpenDiagnostics != null)
                    onOpenDiagnostics?.let {
                        ListRow(Icons.Outlined.BugReport, stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_subtitle), it, showDivider = false)
                    }
                }
            }

            item {
                Column(Modifier.padding(top = Spacing.section)) {
                    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                    Text(stringResource(R.string.you_licenses), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
        }
    }
    if (showProfileSheet) {
        ProfileSheet(state, onSave = { age, sex -> onSaveDemographics(age, sex); showProfileSheet = false }, onDismiss = { showProfileSheet = false })
    }
    if (showGoalSheet) {
        GoalSetupSheet(onSave = { type, target, weeks -> onSaveGoal(type, target, weeks); showGoalSheet = false }, onDismiss = { showGoalSheet = false })
    }
}

/** Avatar initial, name, and the two facts that tune Spartan's ranges and picks. */
@Composable
private fun ProfileCard(state: MainUiState, onEdit: () -> Unit) {
    val name = state.profile?.displayName?.takeIf { it.isNotBlank() && it != "You" } ?: stringResource(R.string.you_title)
    val sex = when (state.userSexAtBirth) {
        SexAtBirth.FEMALE -> stringResource(R.string.coach_sex_female)
        SexAtBirth.MALE -> stringResource(R.string.coach_sex_male)
        SexAtBirth.UNSPECIFIED -> null
    }
    val details = when {
        state.userAgeYears != null && sex != null -> stringResource(R.string.you_profile_details, state.userAgeYears, sex)
        state.userAgeYears != null -> stringResource(R.string.you_profile_age_only, state.userAgeYears)
        else -> stringResource(R.string.you_profile_hint)
    }
    SpartanCard(onClick = onEdit, onClickLabel = stringResource(R.string.you_profile_a11y)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(Locale.getDefault()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
