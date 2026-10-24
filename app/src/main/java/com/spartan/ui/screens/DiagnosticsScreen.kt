package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.spartan.R
import com.spartan.diagnostics.DebugLog
import com.spartan.ui.components.DetailScaffold
import com.spartan.ui.components.SectionHeader
import com.spartan.ui.theme.Spacing

/**
 * Debug-builds-only operational log (sync outcomes, worker runs — never PHI). This is the
 * "debugging without analytics" surface: reachable from You only when BuildConfig.DEBUG, and
 * entirely absent from the user experience in release builds. With [state] it also shows the
 * WHOOP import freshness stamp and what an export can't contain, the same lines Connections shows.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, state: MainUiState? = null) {
    val entries = DebugLog.entries()
    DetailScaffold(stringResource(R.string.diagnostics_title), onBack) {
        item { Text(stringResource(R.string.diagnostics_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state != null) {
            item(key = "import") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.more_diagnostics_import_header))
                    Text(
                        whoopFreshnessStamp(state) ?: stringResource(R.string.more_diagnostics_no_import),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ExportGaps()
                }
            }
        }
        if (entries.isEmpty()) {
            item { Text(stringResource(R.string.diagnostics_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(entries) { line -> Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}
