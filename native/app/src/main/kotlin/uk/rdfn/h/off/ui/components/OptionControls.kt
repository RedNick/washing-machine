package uk.rdfn.h.off.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import uk.rdfn.h.off.ui.Spacing
import uk.rdfn.h.off.washer.DelayStart
import uk.rdfn.h.off.washer.Formatters
import uk.rdfn.h.off.washer.ProgramOption
import uk.rdfn.h.off.washer.formatHonNumber

/**
 * One program setting: a switch for on/off settings, chips for a short
 * list of choices, and − / + for a range. Ported from
 * src/components/ProgramOptionControl.tsx.
 */
@Composable
fun ProgramOptionControl(option: ProgramOption, value: String, onChange: (String) -> Unit) {
	when (option) {
		is ProgramOption.Toggle -> Row(
			Modifier.fillMaxWidth().padding(vertical = Spacing.small),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(option.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
			Switch(
				checked = value == ProgramOption.TOGGLE_ON,
				onCheckedChange = { isOn -> onChange(if (isOn) ProgramOption.TOGGLE_ON else ProgramOption.TOGGLE_OFF) },
				modifier = Modifier.semantics { contentDescription = option.label },
			)
		}

		is ProgramOption.Choice -> Column(Modifier.padding(vertical = Spacing.small)) {
			Text(option.label, fontWeight = FontWeight.SemiBold)
			Row(
				Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Spacing.small),
				horizontalArrangement = Arrangement.spacedBy(Spacing.small),
			) {
				option.choices.forEach { choice ->
					FilterChip(
						selected = choice == value,
						onClick = { onChange(choice) },
						label = { Text(Formatters.formatOptionValue(option.key, choice, option.unit)) },
					)
				}
			}
		}

		is ProgramOption.Range -> {
			val allowedValues = option.allowedValues()
			val currentNumber = value.toDoubleOrNull() ?: option.defaultNumber
			// The nearest allowed value, in case the machine's default isn't exactly on a step.
			val currentIndex = allowedValues.indices.minByOrNull { kotlin.math.abs(allowedValues[it] - currentNumber) } ?: 0
			Column(Modifier.padding(vertical = Spacing.small)) {
				Text(option.label, fontWeight = FontWeight.SemiBold)
				Stepper(
					text = Formatters.formatOptionValue(option.key, value, option.unit),
					canGoDown = currentIndex > 0,
					canGoUp = currentIndex < allowedValues.lastIndex,
					onDown = { onChange(formatHonNumber(allowedValues[currentIndex - 1])) },
					onUp = { onChange(formatHonNumber(allowedValues[currentIndex + 1])) },
				)
			}
		}
	}
}

/**
 * Delay start as clock times: Start now, Start at, or Finish by. The value
 * passed in and out is the delay in minutes, as the machine counts it.
 */
@Composable
fun DelayStartControl(option: ProgramOption.Range, estimatedMinutes: Int?, delayMinutes: Int, onChange: (Int) -> Unit) {
	var mode by rememberSaveable { mutableStateOf(if (delayMinutes > 0) DelayStart.Mode.START_AT else DelayStart.Mode.NOW) }
	Column(Modifier.padding(vertical = Spacing.small)) {
		Text(option.label, fontWeight = FontWeight.SemiBold)
		Row(Modifier.padding(top = Spacing.small), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
			DelayStart.availableModes(estimatedMinutes).forEach { choice ->
				FilterChip(
					selected = mode == choice,
					onClick = {
						mode = choice
						onChange(DelayStart.delayForMode(choice, delayMinutes, option))
					},
					label = { Text(choice.label) },
				)
			}
		}
		if (mode != DelayStart.Mode.NOW) {
			Stepper(
				text = DelayStart.pickedTime(mode, delayMinutes, estimatedMinutes),
				canGoDown = delayMinutes > DelayStart.smallestDelay(option),
				canGoUp = delayMinutes < option.maximum,
				onDown = { onChange(DelayStart.stepBy(-1, delayMinutes, option)) },
				onUp = { onChange(DelayStart.stepBy(1, delayMinutes, option)) },
				downDescription = "Earlier",
				upDescription = "Later",
			)
		}
		Text(
			DelayStart.summary(mode, delayMinutes, estimatedMinutes),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = Spacing.small),
		)
	}
}

@Composable
private fun Stepper(
	text: String,
	canGoDown: Boolean,
	canGoUp: Boolean,
	onDown: () -> Unit,
	onUp: () -> Unit,
	downDescription: String = "Less",
	upDescription: String = "More",
) {
	Row(
		Modifier.fillMaxWidth().padding(top = Spacing.small),
		horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
		verticalAlignment = Alignment.CenterVertically,
	) {
		OutlinedIconButton(onClick = onDown, enabled = canGoDown, modifier = Modifier.semantics { contentDescription = downDescription }) {
			Text("−", fontSize = 22.sp)
		}
		Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
		OutlinedIconButton(onClick = onUp, enabled = canGoUp, modifier = Modifier.semantics { contentDescription = upDescription }) {
			Text("+", fontSize = 22.sp)
		}
	}
}
