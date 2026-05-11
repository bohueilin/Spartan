package com.vitalcompass.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.EventNote
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
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.WorkoutType
import com.vitalcompass.ui.screens.AddMetricScreen
import com.vitalcompass.ui.screens.MetricDetailScreen
import com.vitalcompass.ui.screens.MetricsScreen
import com.vitalcompass.ui.screens.OnboardingScreen
import com.vitalcompass.ui.screens.PlanScreen
import com.vitalcompass.ui.screens.PrivacyScreen
import com.vitalcompass.ui.screens.ReminderSettingsScreen
import com.vitalcompass.ui.screens.ReviewScreen
import com.vitalcompass.ui.screens.SettingsScreen
import com.vitalcompass.ui.screens.TodayScreen
import com.vitalcompass.ui.screens.WorkoutCompletionScreen
import com.vitalcompass.ui.screens.MainViewModel

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Outlined.FavoriteBorder),
    Tab("metrics", "Metrics", Icons.Outlined.Assessment),
    Tab("plan", "Plan", Icons.Outlined.EventNote),
    Tab("review", "Review", Icons.Outlined.Flag),
    Tab("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
fun VitalCompassRoot(viewModel: MainViewModel = hiltViewModel()) {
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
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
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
            composable("today") { TodayScreen(state, onMetricClick = { navController.navigate("detail/${it.name}") }) }
            composable("metrics") {
                MetricsScreen(
                    state = state,
                    onAdd = { navController.navigate("addMetric") },
                    onMetricClick = { navController.navigate("detail/${it.name}") },
                )
            }
            composable("detail/{type}") { entry ->
                val type = entry.arguments?.getString("type")?.let(MetricType::valueOf) ?: MetricType.CUSTOM
                MetricDetailScreen(state, type, onAdd = { navController.navigate("addMetric") })
            }
            composable("addMetric") { AddMetricScreen(onSave = viewModel::addMetric, onDone = { navController.popBackStack() }) }
            composable("plan") {
                PlanScreen(state) { workout ->
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
                    onReminders = { navController.navigate("reminders") },
                    onPrivacy = { navController.navigate("privacy") },
                )
            }
            composable("reminders") { ReminderSettingsScreen(state, onSave = viewModel::saveReminder) }
            composable("privacy") { PrivacyScreen(state, onDelete = viewModel::deleteAllLocalData) }
        }
    }
}
