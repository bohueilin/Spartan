package com.spartan.ui.navigation

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.spartan.BuildConfig
import com.spartan.R
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WorkoutType
import com.spartan.ui.screens.AddMetricScreen
import com.spartan.ui.screens.CheckInScreen
import com.spartan.ui.screens.ConnectionsScreen
import com.spartan.ui.screens.DiagnosticsScreen
import com.spartan.ui.screens.HealthyRangesScreen
import com.spartan.ui.screens.InsightsScreen
import com.spartan.ui.screens.MainViewModel
import com.spartan.ui.screens.MetricDetailScreen
import com.spartan.ui.screens.OnboardingScreen
import com.spartan.ui.screens.PressureWindowsScreen
import com.spartan.ui.screens.PrivacyScreen
import com.spartan.ui.screens.ProgressScreen
import com.spartan.ui.screens.ReflectionSheet
import com.spartan.ui.screens.ReminderSettingsScreen
import com.spartan.ui.screens.TrainingWeekScreen
import com.spartan.ui.screens.WorkoutCompletionScreen
import com.spartan.ui.screens.YouScreen
import com.spartan.ui.theme.Motion

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("today", R.string.tab_today, Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    Tab("insights", R.string.tab_insights, Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab("progress", R.string.tab_progress, Icons.AutoMirrored.Outlined.TrendingUp, Icons.AutoMirrored.Filled.TrendingUp),
    Tab("you", R.string.tab_you, Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun SpartanRoot(
    viewModel: MainViewModel = hiltViewModel(),
    onShareExport: (String) -> Unit = {},
    onRequestNotifications: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.onboardingComplete) {
        OnboardingScreen(onComplete = viewModel::completeOnboarding)
        return
    }

    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    val openMetric: (MetricType) -> Unit = { navController.navigate("detail/${it.name}") }

    // singleTop MainActivity: deep links that arrive while the app is alive come through
    // onNewIntent, not composition — without this, notification/widget taps go nowhere.
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    // The optional evening reflection: in-app only, never a notification.
    val showReflection by viewModel.showReflectionSheet.collectAsStateWithLifecycle()
    if (showReflection) {
        ReflectionSheet(onSave = { mood, note -> viewModel.saveReflection(mood, note) }, onDismiss = viewModel::dismissReflection)
    }
    val bedtime by viewModel.bedtimePlan.collectAsStateWithLifecycle()

    // One-shot action feedback for the whole app: every action confirms what happened, and the
    // reversible ones carry Undo. Collected as an event so it never replays.
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = message.formatArg?.let { context.getString(message.textRes, it) } ?: context.getString(message.textRes)
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = message.undo?.let { context.getString(R.string.snackbar_undo) },
                withDismissAction = message.undo == null,
                duration = SnackbarDuration.Short,
            )
            val undo = message.undo
            if (result == SnackbarResult.ActionPerformed && undo != null) viewModel.undoActivityAction(undo)
        }
    }

    // Library-group-restricted, but the only full view of the stack: previousBackStackEntry is one
    // level deep, and detail → edit needs two to find the tab it was opened from.
    @SuppressLint("RestrictedApi")
    val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
    val routes = backStack.map { it.destination.route }
    val selectedTab = selectedTabRoute(routes)
    // Pushed pages are immersive, like a listing: the tab bar steps aside for their own sticky bar.
    val onTabRoot = routes.lastOrNull() in tabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (onTabRoot) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = selectedTab == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    // Re-tapping the highlighted tab returns to its root screen.
                                    if (selected) {
                                        navController.popBackStack(tab.route, inclusive = false)
                                    } else {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                // Decorative: the visible label already names the tab.
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            // Consumed so a pushed page's own Scaffold doesn't pad for the system bars a second time.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            // Motion.medium fades: the library default 700ms cross-fade reads as lag on tab switches.
            enterTransition = { fadeIn(tween(Motion.medium)) },
            exitTransition = { fadeOut(tween(Motion.medium)) },
            popEnterTransition = { fadeIn(tween(Motion.medium)) },
            popExitTransition = { fadeOut(tween(Motion.medium)) },
        ) {
            composable("today", deepLinks = listOf(navDeepLink { uriPattern = "spartan://today" })) {
                CheckInScreen(
                    state = state,
                    onComplete = viewModel::completeActivity,
                    onUncomplete = viewModel::uncompleteActivity,
                    onSnooze = { viewModel.snoozeActivity(it) },
                    onSkip = viewModel::skipActivity,
                    onSchedule = viewModel::scheduleActivity,
                    onManageConnections = { navController.navigate("connections") },
                    onLogExercise = viewModel::logExerciseDebrief,
                    onOpenRecoveryExplainer = { openMetric(MetricType.RECOVERY_SCORE) },
                    onOpenMetric = openMetric,
                    onRefresh = viewModel::loadToday,
                    onEnableReminders = {
                        // Settle first so the offer never re-renders behind the system dialog.
                        viewModel.settleRemindersOffer()
                        onRequestNotifications()
                    },
                    onDismissRemindersOffer = viewModel::settleRemindersOffer,
                    bedtime = bedtime,
                    onAddAction = viewModel::addAction,
                    onMorningRevealPlayed = viewModel::markMorningRevealPlayed,
                )
            }
            composable("insights") {
                InsightsScreen(state, openMetric, viewModel::addAction, onAddLabResult = { navController.navigate("addMetric") })
            }
            composable("progress") {
                ProgressScreen(
                    state = state,
                    onSaveGoal = viewModel::saveGoal,
                    onAbandonGoal = viewModel::abandonGoal,
                    onDismissGoalNotice = viewModel::dismissGoalNotice,
                    onOpenMetric = openMetric,
                    onOpenTrainingWeek = { navController.navigate("training") },
                    onStartGoalPlan = viewModel::startGoalPlan,
                    onWeeklyDecision = viewModel::recordWeeklyDecision,
                    onResumeGoal = viewModel::resumeGoal,
                )
            }
            composable("you") {
                YouScreen(
                    state = state,
                    onSaveDemographics = viewModel::updateDemographics,
                    onSaveGoal = viewModel::saveGoal,
                    onOpenProgress = {
                        navController.navigate("progress") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenTraining = { navController.navigate("training") },
                    onOpenWindows = { navController.navigate("windows") },
                    onOpenRanges = { navController.navigate("ranges") },
                    onOpenConnections = { navController.navigate("connections") },
                    onOpenReminders = { navController.navigate("reminders") },
                    onOpenPrivacy = { navController.navigate("privacy") },
                    onOpenDiagnostics = if (BuildConfig.DEBUG) { { navController.navigate("diagnostics") } } else null,
                    onStartGoalPlan = viewModel::startGoalPlan,
                    onSavePlanConstraints = viewModel::savePlanConstraints,
                )
            }
            composable("detail/{type}") { entry ->
                val type = entry.arguments?.getString("type")?.let(MetricType::valueOf) ?: MetricType.CUSTOM
                MetricDetailScreen(
                    state = state,
                    type = type,
                    onBack = back,
                    onAdd = { navController.navigate("addMetric") },
                    onEdit = { navController.navigate("editMetric/${it.id}") },
                    onAddAction = viewModel::addAction,
                )
            }
            composable("addMetric") { AddMetricScreen(onSave = viewModel::addMetric, onDone = back) }
            composable("editMetric/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0
                val reading = state.readings.firstOrNull { it.id == id }
                AddMetricScreen(
                    initialReading = reading,
                    onSave = { type, value, note -> if (reading == null) false else viewModel.updateMetric(reading.id, type, value, note) },
                    onDone = back,
                )
            }
            composable("training") {
                TrainingWeekScreen(
                    state = state,
                    onEditMinutes = viewModel::savePlanMinutes,
                    onComplete = { workout -> navController.navigate("complete/${workout.type.name}/${workout.minutes}") },
                    onBack = back,
                )
            }
            composable("windows") { PressureWindowsScreen(state, viewModel::addPressureWindow, viewModel::removePressureWindow, back) }
            composable("ranges") { HealthyRangesScreen(state, viewModel::updateDemographics, openMetric, back) }
            composable("complete/{type}/{minutes}") { entry ->
                val type = entry.arguments?.getString("type")?.let(WorkoutType::valueOf) ?: WorkoutType.ZONE_2
                val minutes = entry.arguments?.getString("minutes")?.toIntOrNull() ?: 30
                // Read once on entry: this screen's own save must not flash the notice as it fades out.
                val alreadyLogged = remember { (state.workoutsLoggedToday[type] ?: 0) > 0 }
                WorkoutCompletionScreen(type, minutes, alreadyLogged, onSave = viewModel::completeWorkout, onDone = back)
            }
            composable("diagnostics") { DiagnosticsScreen(back) }
            composable("connections", deepLinks = listOf(navDeepLink { uriPattern = "spartan://connections" })) {
                // WHOOP data export CSVs via the Storage Access Framework (no storage permission needed).
                val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    viewModel.importWhoopCsv(uris)
                }
                ConnectionsScreen(
                    state = state,
                    onConnectWhoop = viewModel::connectWhoop,
                    onDisconnectWhoop = viewModel::disconnectWhoop,
                    onImportWhoopCsv = {
                        csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/octet-stream"))
                    },
                    onDismissImportResult = viewModel::dismissWhoopImportResult,
                    onConnectCalendar = viewModel::connectCalendar,
                    onDisconnectCalendar = viewModel::disconnectCalendar,
                    onManagePrivacy = { navController.navigate("privacy") },
                    onBack = back,
                )
            }
            composable("reminders") {
                ReminderSettingsScreen(onBack = back, state = state, onRequestNotifications = onRequestNotifications, onSave = viewModel::saveReminder)
            }
            composable("privacy") { PrivacyScreen(state, onShareExport, viewModel::deleteAllLocalData, back) }
        }
    }
}

/**
 * The tab to highlight: the nearest tab at or below the top of the back stack, so a screen reached
 * from several tabs (Connections, metric detail) lights the tab it was actually opened from.
 */
internal fun selectedTabRoute(backStackRoutes: List<String?>): String? =
    backStackRoutes.lastOrNull { route -> tabs.any { it.route == route } }
