package com.spartan.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spartan.BuildConfig
import com.spartan.R
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.ReferenceRanges
import com.spartan.domain.engine.SexAtBirth
import com.spartan.domain.engine.StressPatterns
import com.spartan.ui.components.DetailPage
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.ListRow
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.ScreenTitle
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.WellnessFooter
import com.spartan.ui.theme.MinTouchTarget
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import java.util.Locale

/**
 * You: who Spartan is coaching, then five groups in a fixed order — Your plan, Data and devices,
 * Display, Privacy, About. The goal is row one: with no goal yet it opens [GoalWizardSheet] (a
 * custom goal still opens [GoalSetupSheet]); with an active or paused goal it goes to Progress.
 * Plan settings edit only the plan's constraints and never touch the goal. High-pressure windows
 * and Healthy ranges each say in their subtitle what they do, so no row is unexplained. About is
 * one row: the version, the full disclaimer and licences live on [AboutScreen], not in this list.
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
    onOpenAbout: () -> Unit,
    onStartGoalPlan: (GoalPreset, Double, Int, PlanConstraints) -> Unit = { _, _, _, _ -> },
    onSavePlanConstraints: (PlanConstraints) -> Unit = {},
    onSetCalmerTheme: (Boolean) -> Unit = {},
    onSetWallpaperColours: (Boolean) -> Unit = {},
) {
    // Saveable, so a sheet open mid-edit survives rotation and process recreation.
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showGoalSheet by rememberSaveable { mutableStateOf(false) }
    var showWizard by rememberSaveable { mutableStateOf(false) }
    var showPlanSettings by rememberSaveable { mutableStateOf(false) }
    val rows = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xs)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { ScreenTitle(stringResource(R.string.you_title)) }
            item { ProfileHeader(state) { showProfileSheet = true } }

            item { GroupHeader(stringResource(R.string.you_section_plan)) }
            item {
                SpartanCard(contentPadding = rows) {
                    val activeGoal = state.activeGoal
                    val pausedGoal = state.pausedGoal
                    ListRow(
                        Icons.Outlined.Flag,
                        stringResource(R.string.you_goal),
                        when {
                            activeGoal != null -> state.goalProgress?.summary ?: goalTitle(activeGoal)
                            pausedGoal != null -> stringResource(R.string.wizard_goal_paused, goalTitle(pausedGoal))
                            else -> stringResource(R.string.you_goal_none)
                        },
                        onClick = { if (activeGoal != null || pausedGoal != null) onOpenProgress() else showWizard = true },
                    )
                    ListRow(Icons.AutoMirrored.Outlined.EventNote, stringResource(R.string.plan_title), stringResource(R.string.you_training_subtitle), onOpenTraining)
                    ListRow(
                        Icons.Outlined.Settings,
                        stringResource(R.string.wizard_plan_settings_title),
                        stringResource(R.string.wizard_plan_settings_subtitle),
                        onClick = { showPlanSettings = true },
                    )
                    ListRow(Icons.Outlined.Notifications, stringResource(R.string.settings_reminders), stringResource(R.string.settings_reminders_subtitle), onOpenReminders)
                    // The subtitle states exactly what fires and when: a notification a few minutes before
                    // the window, on days the app is opened (MainViewModel arms it; the 04:00 worker does not).
                    val leadMinutes = StressPatterns.PRE_WINDOW_LEAD_MINUTES
                    val lead = pluralStringResource(R.plurals.you_windows_lead, leadMinutes, leadMinutes)
                    val windows = state.pressureWindows.size
                    ListRow(
                        Icons.Outlined.Schedule,
                        stringResource(R.string.you_windows),
                        if (windows == 0) stringResource(R.string.you_windows_none, lead)
                        else pluralStringResource(R.plurals.you_windows_set, windows, windows, lead),
                        onOpenWindows,
                        showDivider = false,
                    )
                }
            }

            item { GroupHeader(stringResource(R.string.you_section_data)) }
            item {
                SpartanCard(contentPadding = rows) {
                    ListRow(Icons.Outlined.Link, stringResource(R.string.settings_connections), stringResource(R.string.settings_connections_subtitle), onOpenConnections)
                    // The same cohort the page names, so the row is true with or without age and sex ("adults").
                    val cohort = ReferenceRanges.cohortPhrase(state.userAgeYears, state.userSexAtBirth)
                    ListRow(Icons.Outlined.Tune, stringResource(R.string.you_ranges), stringResource(R.string.you_ranges_subtitle, cohort), onOpenRanges, showDivider = false)
                }
            }

            item { GroupHeader(stringResource(R.string.more_section_display)) }
            item {
                // Dynamic colour needs Android 12 (API 31); below that the row would do nothing.
                val wallpaperAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SpartanCard(contentPadding = rows) {
                    SwitchRow(
                        Icons.Outlined.Contrast,
                        stringResource(R.string.more_calmer_theme),
                        stringResource(calmerThemeBodyRes(wallpaperAvailable, state.wallpaperColours)),
                        checked = state.calmerTheme,
                        onCheckedChange = onSetCalmerTheme,
                        showDivider = wallpaperAvailable,
                    )
                    if (wallpaperAvailable) {
                        SwitchRow(
                            Icons.Outlined.Palette,
                            stringResource(R.string.more_wallpaper_colours),
                            stringResource(R.string.more_wallpaper_colours_body),
                            checked = state.wallpaperColours,
                            onCheckedChange = onSetWallpaperColours,
                            showDivider = false,
                        )
                    }
                }
            }

            item { GroupHeader(stringResource(R.string.you_section_privacy)) }
            item {
                SpartanCard(Modifier.testTag(YOU_PRIVACY_CARD_TAG), contentPadding = rows) {
                    ListRow(Icons.Outlined.PrivacyTip, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_subtitle), onOpenPrivacy, showDivider = onOpenDiagnostics != null)
                    onOpenDiagnostics?.let {
                        ListRow(Icons.Outlined.BugReport, stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_subtitle), it, showDivider = false)
                    }
                }
            }

            item { GroupHeader(stringResource(R.string.you_section_about)) }
            item {
                SpartanCard(contentPadding = rows) {
                    ListRow(
                        Icons.Outlined.Info,
                        stringResource(R.string.settings_about),
                        stringResource(R.string.you_about_subtitle),
                        onOpenAbout,
                        showDivider = false,
                    )
                }
            }
        }
    }
    if (showProfileSheet) {
        ProfileSheet(state, onSave = { age, sex -> onSaveDemographics(age, sex); showProfileSheet = false }, onDismiss = { showProfileSheet = false })
    }
    if (showWizard) {
        GoalWizardSheet(
            state = state,
            onStart = { preset, target, weeks, constraints ->
                onStartGoalPlan(preset, target, weeks, constraints)
                showWizard = false
            },
            onCustomGoal = {
                showWizard = false
                showGoalSheet = true
            },
            onDismiss = { showWizard = false },
        )
    }
    if (showGoalSheet) {
        GoalSetupSheet(onSave = { type, target, weeks -> onSaveGoal(type, target, weeks); showGoalSheet = false }, onDismiss = { showGoalSheet = false })
    }
    if (showPlanSettings) {
        PlanConstraintsSheet(
            state = state,
            onSave = { constraints ->
                onSavePlanConstraints(constraints)
                showPlanSettings = false
            },
            onDismiss = { showPlanSettings = false },
        )
    }
}

/** Tags the Privacy group's card, so a test can see that release builds leave no divider in it. */
internal const val YOU_PRIVACY_CARD_TAG = "you-privacy-card"

/**
 * The profile header: avatar initial, name, and the two facts that tune Spartan's ranges and picks
 * (or the hint to add whichever is missing). A header on the page, not another card, and the whole
 * row edits. Before onboarding gave a name it reads "Your profile", never a second "You" under the
 * title. The avatar is decorative: TalkBack reads the name, the details and the edit action only.
 */
@Composable
private fun ProfileHeader(state: MainUiState, onEdit: () -> Unit) {
    val name = state.profile?.displayName?.takeIf { it.isNotBlank() && it != "You" }
    val sex = when (state.userSexAtBirth) {
        SexAtBirth.FEMALE -> stringResource(R.string.coach_sex_female)
        SexAtBirth.MALE -> stringResource(R.string.coach_sex_male)
        SexAtBirth.UNSPECIFIED -> null
    }
    val details = when {
        state.userAgeYears != null && sex != null -> stringResource(R.string.you_profile_details, state.userAgeYears, sex)
        state.userAgeYears != null -> stringResource(R.string.you_profile_age_only, state.userAgeYears)
        sex != null -> stringResource(R.string.you_profile_sex_only, sex)
        else -> stringResource(R.string.you_profile_hint)
    }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .clickable(onClickLabel = stringResource(R.string.you_profile_a11y), role = Role.Button, onClick = onEdit)
            .heightIn(min = MinTouchTarget)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(colors.primaryContainer).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (name != null) {
                Text(name.take(1).uppercase(Locale.getDefault()), style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
            } else {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(name ?: stringResource(R.string.you_profile_unnamed), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.md))
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** A group's label: quieter than the screen title and the rows under it, and a TalkBack heading. */
@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xl, start = Spacing.xs).semantics { heading() },
    )
}

/**
 * A settings row that is one full-width switch: the whole row toggles (Role.Switch, so TalkBack
 * announces "on/off" with the title), with a 48dp+ target. Laid out like [ListRow].
 */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .heightIn(min = 64.dp)
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, MaterialTheme.colorScheme.onSurface, size = 40.dp)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.md))
            // The row owns the toggle; a null callback keeps the switch from being a second target.
            Switch(checked = checked, onCheckedChange = null)
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The calmer theme's subtitle. With Wallpaper colours on (Android 12+), the wallpaper palette wins
 * over the quieter colours, so the row promises only what still applies: no celebration motion.
 */
internal fun calmerThemeBodyRes(wallpaperAvailable: Boolean, wallpaperColours: Boolean): Int =
    if (wallpaperAvailable && wallpaperColours) R.string.more_calmer_theme_body_wallpaper else R.string.more_calmer_theme_body

/**
 * About, pushed from You's single About row: the version (its only place in the app), the full
 * wellness disclaimer — the one place it appears — then Today's in-context line, so About never says
 * less than Today ("not medical advice", and when to stop and seek care), where your data lives,
 * and the font licence.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    DetailPage(stringResource(R.string.settings_about), onBack) {
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        WellnessFooter()
        Text(stringResource(R.string.today_in_context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.about_privacy_line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.about_licences_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.xl).semantics { heading() },
        )
        Text(stringResource(R.string.you_licenses), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
