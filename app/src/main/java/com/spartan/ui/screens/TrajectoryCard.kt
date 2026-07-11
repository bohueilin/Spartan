package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spartan.domain.engine.MetricProjection
import com.spartan.domain.engine.ProjectionEngine

/**
 * "Where this can take you" — the expected-improvement view. Shows, per metric, the typical range
 * after 8 weeks of following the plan at the user's current consistency. Deliberately framed as
 * ranges from general findings with an always-visible disclaimer: motivation without over-promising.
 */
@Composable
fun TrajectoryCard(projections: List<MetricProjection>) {
    if (projections.isEmpty()) return
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Where this can take you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                projections.first().assumption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            projections.forEach { projection ->
                val week8 = projection.points.last()
                val flat = week8.low == week8.high && week8.low == projection.currentValue
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        projection.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (flat) {
                            "${format(projection.currentValue)} ${projection.unit}".trim()
                        } else {
                            "${format(projection.currentValue)} → ${format(week8.low)}–${format(week8.high)} ${projection.unit} in 8 wks".trim()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (flat) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                ProjectionEngine.DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun format(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
