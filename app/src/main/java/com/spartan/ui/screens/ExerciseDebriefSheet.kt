package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spartan.domain.model.DailyActivity
import kotlin.math.roundToInt

/**
 * Five-second exercise debrief after checking off a training activity. The three answers feed the
 * adaptive coaching rules directly: pain or repeated high effort deloads next week's plan. Fully
 * optional — "Skip" costs nothing and the check-off has already been recorded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDebriefSheet(
    activity: DailyActivity,
    onSave: (minutes: Int, rpe: Int, pain: Boolean) -> Unit,
    onSkip: () -> Unit,
) {
    var minutesText by rememberSaveable(activity.id) { mutableStateOf(activity.estimatedMinutes.toString()) }
    var rpe by rememberSaveable(activity.id) { mutableFloatStateOf(5f) }
    var pain by rememberSaveable(activity.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onSkip) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nice work. How did it go?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                activity.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                label = { Text("Minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Effort ${rpe.roundToInt()} of 10", style = MaterialTheme.typography.labelLarge)
            Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 1f..10f, steps = 8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pain or concerning symptoms", Modifier.weight(1f))
                Switch(checked = pain, onCheckedChange = { pain = it })
            }
            Button(
                onClick = {
                    onSave(minutesText.toIntOrNull() ?: activity.estimatedMinutes, rpe.roundToInt(), pain)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save session", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
            Text(
                "Your effort and pain answers shape next week's plan. Pain means we go gentler — never push through it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
