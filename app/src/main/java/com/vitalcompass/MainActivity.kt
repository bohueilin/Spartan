package com.vitalcompass

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.vitalcompass.ui.navigation.VitalCompassRoot
import com.vitalcompass.ui.screens.MainViewModel
import com.vitalcompass.ui.theme.VitalCompassTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VitalCompassTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> viewModel.setNotificationDenied(!granted) }
                LaunchedEffect(Unit) {
                    viewModel.seed()
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                VitalCompassRoot(viewModel)
            }
        }
    }
}
