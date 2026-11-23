package uk.rdfn.h.off.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.rdfn.h.off.ui.Spacing

/** A titled card, used for each block of details. */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
	Card(
		modifier = modifier.fillMaxWidth().padding(bottom = Spacing.medium),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
	) {
		Column(Modifier.padding(Spacing.medium)) {
			if (title != null) {
				Text(
					title,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.padding(bottom = Spacing.small),
				)
			}
			content()
		}
	}
}

/** "Label ........ value" on one line. */
@Composable
fun DetailRow(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.Top,
	) {
		Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
		Text(value, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f).padding(start = Spacing.small))
	}
}

/** A coloured box for something you need to know, e.g. "Remote control is off". */
@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
	Card(
		modifier = modifier.fillMaxWidth().padding(bottom = Spacing.medium),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.errorContainer,
			contentColor = MaterialTheme.colorScheme.onErrorContainer,
		),
	) {
		Text(text, modifier = Modifier.padding(Spacing.medium))
	}
}
