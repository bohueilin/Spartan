package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.model.ReflectionMood
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing

/**
 * The optional end-of-day reflection: how the day felt, in one tap, with an optional line of the
 * user's own words. Deliberately not a score and not a prompt to do better — the three moods are
 * neutral peers, and dismissing costs nothing. Offered at most once a day, in-app only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionSheet(
    onSave: (ReflectionMood, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mood by rememberSaveable { mutableStateOf<ReflectionMood?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.reflection_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.reflection_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                MoodChip(
                    label = stringResource(R.string.reflection_mood_tough),
                    selected = mood == ReflectionMood.TOUGH,
                    onSelect = { mood = ReflectionMood.TOUGH },
                    modifier = Modifier.weight(1f),
                )
                MoodChip(
                    label = stringResource(R.string.reflection_mood_okay),
                    selected = mood == ReflectionMood.OKAY,
                    onSelect = { mood = ReflectionMood.OKAY },
                    modifier = Modifier.weight(1f),
                )
                MoodChip(
                    label = stringResource(R.string.reflection_mood_strong),
                    selected = mood == ReflectionMood.STRONG,
                    onSelect = { mood = ReflectionMood.STRONG },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(140) },
                label = { Text(stringResource(R.string.reflection_note_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.reflection_not_tonight))
                }
                Button(
                    onClick = { mood?.let { onSave(it, note) } },
                    enabled = mood != null,
                    modifier = Modifier.heightIn(min = 48.dp).padding(start = Spacing.sm),
                ) {
                    Text(stringResource(R.string.reflection_save))
                }
            }
        }
    }
}

/**
 * One mood option. `selectable` with [Role.RadioButton] gives TalkBack the whole group's semantics
 * (position, selected state) and keeps the 48dp target while the visual stays a compact pill.
 */
@Composable
private fun MoodChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
