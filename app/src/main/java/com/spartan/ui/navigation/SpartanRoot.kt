package com.spartan.ui.navigation

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
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
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

/**
 * The largest font scale the navigation labels follow — the system bars' convention: bar and rail
 * labels grow with the user's text size up to 1.3x, then hold, so at 200% "Progress" stays one
 * word on one line instead of breaking mid-word in a fixed-height item. A narrow item takes less
 * (see [navLabelFontScale]).
 */
internal const val NAV_LABEL_MAX_FONT_SCALE = 1.3f

/** How far [navLabelFontScale] steps down from the cap, toward 1.0, while a label is too wide. */
internal const val NAV_LABEL_SCALE_STEP = 0.05f

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
        ReflectionSheet(
            initialMood = state.lastReflectionMood,
            onSave = { mood, note -> viewModel.saveReflection(mood, note) },
            onDismiss = viewModel::dismissReflection,
        )
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

    // Bottom bar on compact width, navigation rail from 600dp — M3's own breakpoints, which keep a bar
    // for short landscape windows. The same four tabs either way; none at all on pushed pages.
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val layoutType = if (onTabRoot) adaptiveLayout else NavigationSuiteType.None
    val colors = MaterialTheme.colorScheme
    // A transparent indicator: the filled icon and the primary colour mark the selected tab.
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.primary,
            selectedTextColor = colors.primary,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
            indicatorColor = Color.Transparent,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.primary,
            selectedTextColor = colors.primary,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
            indicatorColor = Color.Transparent,
        ),
    )

    // The suite's bar keeps M3's default 3dp tonal elevation, which would tint a `surface` bar with
    // the accent; tonal elevation is off for the suite and back on for the pages inside it.
    CompositionLocalProvider(LocalTonalElevationEnabled provides false) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                tabs.forEach { tab ->
                    val selected = selectedTab == tab.route
                    item(
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
                        label = { NavLabel(stringResource(tab.labelRes)) },
                        colors = itemColors,
                    )
                }
            },
            layoutType = layoutType,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = colors.surface,
                navigationRailContainerColor = colors.surface,
            ),
            containerColor = colors.background,
        ) {
            CompositionLocalProvider(LocalTonalElevationEnabled provides true) {
                // The bar or rail already pads for these insets, so they are consumed here (as the suite
                // itself does; consuming twice is idempotent) and this Scaffold pads only for what is
                // left: the status bar, plus the gesture bar on pushed pages. Never doubled.
                val suiteInsets = when (layoutType) {
                    NavigationSuiteType.NavigationBar -> NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
                    NavigationSuiteType.NavigationRail -> NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
                    else -> WindowInsets(0, 0, 0, 0)
                }
                Box(Modifier.fillMaxSize().consumeWindowInsets(suiteInsets)) {
                    Scaffold(
                        containerColor = colors.background,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                    onSetCalmerTheme = viewModel::setCalmerTheme,
                                    onSetWallpaperColours = viewModel::setWallpaperColours,
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
                            composable("diagnostics") { DiagnosticsScreen(back, state) }
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
                    // The hairline that separated the bar from the page, now on whichever edge the suite sits.
                    when (layoutType) {
                        NavigationSuiteType.NavigationBar ->
                            HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = colors.outlineVariant)
                        NavigationSuiteType.NavigationRail ->
                            VerticalDivider(Modifier.align(Alignment.CenterStart), color = colors.outlineVariant)
                        else -> Unit
                    }
                }
            }
        }
    }
}

/**
 * A tab label on one line whose text follows the font scale up to [NAV_LABEL_MAX_FONT_SCALE], then
 * shrinks to fit its item: on a 320dp window a bar item leaves "Progress" about 66dp, too narrow
 * for it at 1.3x, so the label takes the largest scale whose single line fits, never below the
 * default size. Only a label too wide even at 1.0 ellipsizes. Only the label's density changes:
 * icons and the 48dp targets around them are unchanged.
 */
@Composable
private fun NavLabel(text: String) {
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val maxWidthPx = constraints.maxWidth
        val fitted = remember(text, style, density, maxWidthPx) {
            // The user's own density whenever the scale is theirs, so Android 14's non-linear font
            // scaling still applies; a plain linear Density only for a scale reduced to fit.
            val densityAt = { scale: Float -> if (scale == density.fontScale) density else Density(density.density, scale) }
            densityAt(
                navLabelFontScale(density.fontScale, maxWidthPx) { scale ->
                    measurer.measure(text, style, maxLines = 1, density = densityAt(scale)).size.width
                },
            )
        }
        CompositionLocalProvider(LocalDensity provides fitted) {
            Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The font scale a navigation label is drawn at. Up to 1.0 it is the user's own, untouched. Above
 * that it is the largest scale from min(user, [NAV_LABEL_MAX_FONT_SCALE]) down toward 1.0, in
 * [NAV_LABEL_SCALE_STEP] steps, whose one-line width ([widthAt], px) fits [maxWidthPx]. When none
 * fits it is 1.0 — never below the default size — and the label ellipsizes as a last resort.
 */
internal fun navLabelFontScale(userScale: Float, maxWidthPx: Int, widthAt: (Float) -> Int): Float {
    if (userScale <= 1f) return userScale
    val top = minOf(userScale, NAV_LABEL_MAX_FONT_SCALE)
    val steps = ((top - 1f) / NAV_LABEL_SCALE_STEP).toInt()
    return (0..steps)
        .map { (top - it * NAV_LABEL_SCALE_STEP).coerceAtLeast(1f) }
        .firstOrNull { widthAt(it) <= maxWidthPx }
        ?: 1f
}

/**
 * The tab to highlight: the nearest tab at or below the top of the back stack, so a screen reached
 * from several tabs (Connections, metric detail) lights the tab it was actually opened from.
 */
internal fun selectedTabRoute(backStackRoutes: List<String?>): String? =
    backStackRoutes.lastOrNull { route -> tabs.any { it.route == route } }
