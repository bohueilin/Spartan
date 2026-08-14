package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.MetricExplainers
import com.spartan.domain.model.MetricType
import com.spartan.ui.theme.Radius

/**
 * Plain-language metric education, rendered on the metric detail screen: what the number is,
 * what moves it, what a good pattern looks like, and how Spartan's coaching uses it. Renders
 * nothing for metrics without an explainer.
 */
@Composable
fun MetricExplainerSection(type: MetricType) {
    val explainer = MetricExplainers.forMetric(type) ?: return
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.explainer_title, explainer.title.lowercase(java.util.Locale.getDefault())), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExplainerBlock(stringResource(R.string.explainer_what_it_is), explainer.whatItIs)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.explainer_what_moves_it), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                explainer.whatMovesIt.forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp))
                }
            }
            ExplainerBlock(stringResource(R.string.explainer_good_pattern), explainer.whatGoodLooksLike)
            ExplainerBlock(stringResource(R.string.explainer_how_used), explainer.howSpartanUsesIt)
        }
    }
}

@Composable
private fun ExplainerBlock(label: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
