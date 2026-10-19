package com.spartan.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spartan.R

/**
 * The "SAMPLE DATA" provenance chip. Rendered on EVERY surface that shows WHOOP-derived numbers
 * while the app runs on sample data (check-in header, Metrics, Metric Detail, Plan, Review,
 * Coach, Connections) — a user must never mistake fabricated readings for their own. Surfaces
 * that can't host the chip carry the same provenance as text: the Privacy export prepends a
 * sample-data line, the readiness ring's TalkBack label appends "sample data", and a stub-calendar
 * "Find a time" result reads "(sample calendar)".
 */
@Composable
fun SampleDataChip(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier,
    ) {
        val a11y = stringResource(R.string.sample_data_a11y)
        Text(
            stringResource(R.string.checkin_sample_data),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            // Spoken label: all-caps "SAMPLE DATA" can be spelled out letter-by-letter by TalkBack.
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics { contentDescription = a11y },
        )
    }
}
