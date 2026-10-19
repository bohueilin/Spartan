package com.spartan.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.spartan.R
import com.spartan.diagnostics.DebugLog
import com.spartan.ui.components.DetailScaffold

/**
 * Debug-builds-only operational log (sync outcomes, worker runs — never PHI). This is the
 * "debugging without analytics" surface: reachable from You only when BuildConfig.DEBUG, and
 * entirely absent from the user experience in release builds.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val entries = DebugLog.entries()
    DetailScaffold(stringResource(R.string.diagnostics_title), onBack) {
        item { Text(stringResource(R.string.diagnostics_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (entries.isEmpty()) {
            item { Text(stringResource(R.string.diagnostics_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(entries) { line -> Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}
