package com.spartan

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.spartan.ui.navigation.SpartanRoot
import com.spartan.ui.screens.MainViewModel
import com.spartan.ui.theme.SpartanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Edge-to-edge with system-managed bar contrast (fixes forced-light bars in dark theme).
        enableEdgeToEdge()
        setContent {
            SpartanTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> viewModel.setNotificationDenied(!granted) }
                LaunchedEffect(Unit) {
                    viewModel.seed()
                }
                // In-app review at positive moments only (rate-limited in the ViewModel); the Play
                // API decides whether a dialog is actually shown.
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(uiState.requestReview) {
                    if (uiState.requestReview) {
                        val manager = ReviewManagerFactory.create(this@MainActivity)
                        manager.requestReviewFlow().addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                manager.launchReviewFlow(this@MainActivity, task.result)
                            }
                            viewModel.onReviewPromptShown()
                        }
                    }
                }
                SpartanRoot(
                    viewModel = viewModel,
                    onShareExport = { exportText ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Spartan local export")
                            putExtra(Intent.EXTRA_TEXT, exportText)
                        }
                        startActivity(Intent.createChooser(intent, "Share local export"))
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
    }
}
