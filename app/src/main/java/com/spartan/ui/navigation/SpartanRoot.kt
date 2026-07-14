package com.spartan.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WorkoutType
import com.spartan.ui.screens.AddMetricScreen
import com.spartan.ui.screens.CheckInScreen
import com.spartan.ui.screens.ConnectionsScreen
import com.spartan.ui.screens.DiagnosticsScreen
import com.spartan.ui.screens.MetricDetailScreen
import com.spartan.ui.screens.MetricsScreen
import com.spartan.ui.screens.OnboardingScreen
import com.spartan.ui.screens.PlanScreen
import com.spartan.ui.screens.PrivacyScreen
import com.spartan.ui.screens.ReminderSettingsScreen
import com.spartan.ui.screens.ReviewScreen
import com.spartan.ui.screens.SettingsScreen
import com.spartan.ui.screens.WorkoutCompletionScreen
import com.spartan.ui.screens.MainViewModel

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Outlined.FavoriteBorder),
    Tab("metrics", "Metrics", Icons.Outlined.Assessment),
    Tab("plan", "Plan", Icons.AutoMirrored.Outlined.EventNote),
    Tab("review", "Review", Icons.Outlined.Flag),
    Tab("settings", "Settings", Icons.Outlined.Settings),
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
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentDestination = backStack?.destination
            val selectedTabRoute = parentTabRoute(currentDestination?.route)
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTabRoute == tab.route || currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable(
                "today",
                deepLinks = listOf(navDeepLink { uriPattern = "spartan://today" }),
            ) {
                CheckInScreen(
                    state = state,
                    onComplete = viewModel::completeActivity,
                    onUncomplete = viewModel::uncompleteActivity,
                    onSnooze = { viewModel.snoozeActivity(it) },
                    onSkip = viewModel::skipActivity,
                    onSchedule = viewModel::scheduleActivity,
                    onManageConnections = { navController.navigate("connections") },
                    onLogExercise = viewModel::logExerciseDebrief,
                    onOpenRecoveryExplainer = { navController.navigate("detail/RECOVERY_SCORE") },
                )
            }
            composable("metrics") {
                MetricsScreen(
                    state = state,
                    onAdd = { navController.navigate("addMetric") },
                    onMetricClick = { navController.navigate("detail/${it.name}") },
                )
            }
            composable("detail/{type}") { entry ->
                val type = entry.arguments?.getString("type")?.let(MetricType::valueOf) ?: MetricType.CUSTOM
                MetricDetailScreen(
                    state = state,
                    type = type,
                    onAdd = { navController.navigate("addMetric") },
                    onEdit = { navController.navigate("editMetric/${it.id}") },
                )
            }
            composable("addMetric") { AddMetricScreen(onSave = viewModel::addMetric, onDone = { navController.popBackStack() }) }
            composable("editMetric/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0
                val reading = state.readings.firstOrNull { it.id == id }
                AddMetricScreen(
                    initialReading = reading,
                    onSave = { type, value, note ->
                        if (reading == null) false else viewModel.updateMetric(reading.id, type, value, note)
                    },
                    onDone = { navController.popBackStack() },
                )
            }
            composable("plan") {
                PlanScreen(
                    state = state,
                    onEditMinutes = viewModel::savePlanMinutes,
                ) { workout ->
                    navController.navigate("complete/${workout.type.name}/${workout.minutes}")
                }
            }
            composable("complete/{type}/{minutes}") { entry ->
                val type = entry.arguments?.getString("type")?.let(WorkoutType::valueOf) ?: WorkoutType.ZONE_2
                val minutes = entry.arguments?.getString("minutes")?.toIntOrNull() ?: 30
                WorkoutCompletionScreen(type, minutes, onSave = viewModel::completeWorkout, onDone = { navController.popBackStack() })
            }
            composable("review") { ReviewScreen(state) }
            composable("settings") {
                SettingsScreen(
                    onConnections = { navController.navigate("connections") },
                    onReminders = { navController.navigate("reminders") },
                    onPrivacy = { navController.navigate("privacy") },
                    onDiagnostics = if (com.spartan.BuildConfig.DEBUG) {
                        { navController.navigate("diagnostics") }
                    } else null,
                )
            }
            composable("diagnostics") { DiagnosticsScreen() }
            composable(
                "connections",
                deepLinks = listOf(navDeepLink { uriPattern = "spartan://connections" }),
            ) {
                // WHOOP data export CSVs, picked via the Storage Access Framework (no storage
                // permission needed; read access is granted per-pick by the system picker).
                val csvPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris -> viewModel.importWhoopCsv(uris) }
                ConnectionsScreen(
                    state = state,
                    onConnectWhoop = viewModel::connectWhoop,
                    onDisconnectWhoop = viewModel::disconnectWhoop,
                    onImportWhoopCsv = {
                        csvPicker.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/csv",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onDismissImportResult = viewModel::dismissWhoopImportResult,
                    onConnectCalendar = viewModel::connectCalendar,
                    onDisconnectCalendar = viewModel::disconnectCalendar,
                    onManagePrivacy = { navController.navigate("privacy") },
                )
            }
            composable("reminders") {
                ReminderSettingsScreen(
                    state = state,
                    onRequestNotifications = onRequestNotifications,
                    onSave = viewModel::saveReminder,
                )
            }
            composable("privacy") {
                PrivacyScreen(
                    state = state,
                    onShare = onShareExport,
                    onDelete = viewModel::deleteAllLocalData,
                )
            }
        }
    }
}

private fun parentTabRoute(route: String?): String? = when {
    route == null -> null
    route == "detail/{type}" || route == "addMetric" || route == "editMetric/{id}" -> "metrics"
    route == "complete/{type}/{minutes}" -> "plan"
    route == "reminders" || route == "privacy" || route == "diagnostics" -> "settings"
    route == "connections" -> "today"
    else -> route
}
