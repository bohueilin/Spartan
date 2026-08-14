package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spartan.R
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
            Text(stringResource(R.string.debrief_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                activity.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                label = { Text(stringResource(R.string.debrief_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.debrief_effort, rpe.roundToInt()), style = MaterialTheme.typography.labelLarge)
            val effortDescription = stringResource(R.string.a11y_effort_slider)
            Slider(
                value = rpe,
                onValueChange = { rpe = it },
                valueRange = 1f..10f,
                steps = 8,
                // The visible "Effort N of 10" text is a separate node; without this TalkBack
                // announces an anonymous slider — and this value feeds the deload rules.
                modifier = Modifier.semantics { contentDescription = effortDescription },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Row-level toggleable merges label + role + state into one TalkBack announcement
                // and makes the whole row the target — this is the safety-critical pain input.
                modifier = Modifier.toggleable(value = pain, role = Role.Switch, onValueChange = { pain = it }),
            ) {
                Text(stringResource(R.string.workout_pain_label), Modifier.weight(1f))
                Switch(checked = pain, onCheckedChange = null)
            }
            Button(
                onClick = {
                    onSave(minutesText.toIntOrNull() ?: activity.estimatedMinutes, rpe.roundToInt(), pain)
                },
                // heightIn, not height: the label must grow with font scale, not clip.
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.debrief_save), fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debrief_skip)) }
            Text(
                stringResource(R.string.debrief_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
