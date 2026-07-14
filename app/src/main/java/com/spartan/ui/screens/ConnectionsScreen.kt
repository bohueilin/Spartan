package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spartan.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Consent + integration management. Explains exactly what Spartan reads and why, in plain language,
 * before any connection. Least privilege: WHOOP read-only health scopes; Google Calendar free/busy
 * only (never event contents) unless the user opts into event creation.
 */
@Composable
fun ConnectionsScreen(
    state: MainUiState,
    onConnectWhoop: () -> Unit,
    onDisconnectWhoop: () -> Unit,
    onImportWhoopCsv: () -> Unit,
    onDismissImportResult: () -> Unit,
    onConnectCalendar: () -> Unit,
    onDisconnectCalendar: () -> Unit,
    onManagePrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.connections_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.connections_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IntegrationCard(
            title = stringResource(R.string.connections_whoop_title),
            connected = state.whoopConnected,
            isSample = state.whoopIsMock,
            body = stringResource(R.string.connections_whoop_body),
            scopes = listOf(
                stringResource(R.string.connections_whoop_scope_recovery),
                stringResource(R.string.connections_whoop_scope_cycles),
                stringResource(R.string.connections_whoop_scope_profile),
            ),
            connectLabel = if (state.whoopIsMock) stringResource(R.string.connections_use_sample_data) else stringResource(R.string.connections_connect_whoop),
            onConnect = onConnectWhoop,
            onDisconnect = onDisconnectWhoop,
            secondaryActionLabel = stringResource(R.string.connections_import_whoop_csv),
            onSecondaryAction = onImportWhoopCsv,
            secondaryActionHint = stringResource(R.string.connections_import_whoop_hint),
        )

        state.whoopImport?.let { import ->
            WhoopImportResultCard(import = import, onDismiss = onDismissImportResult)
        }

        IntegrationCard(
            title = stringResource(R.string.connections_calendar_title),
            connected = state.calendarConnected,
            isSample = true,
            body = stringResource(R.string.connections_calendar_body),
            scopes = listOf(
                stringResource(R.string.connections_calendar_scope_freebusy),
                stringResource(R.string.connections_calendar_scope_events),
            ),
            connectLabel = stringResource(R.string.connections_connect_calendar),
            onConnect = onConnectCalendar,
            onDisconnect = onDisconnectCalendar,
        )

        Text(
            stringResource(R.string.connections_phase_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onManagePrivacy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connections_privacy_button))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IntegrationCard(
    title: String,
    connected: Boolean,
    isSample: Boolean,
    body: String,
    scopes: List<String>,
    connectLabel: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionHint: String? = null,
) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (connected) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                        Text(
                            stringResource(R.string.connections_connected_chip), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.connections_requests_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            scopes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            if (connected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connections_disconnect)) }
            } else {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text(connectLabel) }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth()) { Text(secondaryActionLabel) }
                secondaryActionHint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Outcome of a WHOOP CSV import: progress, a summary of what landed, or a gentle failure. */
@Composable
private fun WhoopImportResultCard(import: WhoopImportUiState, onDismiss: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                import.inProgress -> {
                    Text(
                        stringResource(R.string.connections_import_in_progress),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                import.failed -> {
                    Text(
                        stringResource(R.string.connections_import_failed_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (import.failedButRecognized.isEmpty()) {
                            stringResource(R.string.connections_import_failed_body)
                        } else {
                            stringResource(
                                R.string.connections_import_needs_cycles_body,
                                import.failedButRecognized.joinToString(),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connections_import_dismiss))
                    }
                }
                import.summary != null -> {
                    val summary = import.summary
                    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
                    val range = "${formatter.format(LocalDate.ofEpochDay(summary.firstDayEpoch))} – " +
                        formatter.format(LocalDate.ofEpochDay(summary.lastDayEpoch))
                    Text(
                        stringResource(R.string.connections_import_success_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(
                            R.string.connections_import_success_body,
                            summary.days, range, summary.workouts, summary.journalDays,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.skippedFiles.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.connections_import_skipped,
                                summary.skippedFiles.joinToString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connections_import_dismiss))
                    }
                }
            }
        }
    }
}
