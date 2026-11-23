package uk.rdfn.h.off.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.rdfn.h.off.connection.WasherRepository
import uk.rdfn.h.off.ui.Spacing
import uk.rdfn.h.off.ui.components.DelayStartControl
import uk.rdfn.h.off.ui.components.ProgramOptionControl
import uk.rdfn.h.off.ui.components.SectionCard
import uk.rdfn.h.off.ui.components.WarningBanner
import uk.rdfn.h.off.washer.ChosenOptions
import uk.rdfn.h.off.washer.Formatters
import uk.rdfn.h.off.washer.OptionKeys
import uk.rdfn.h.off.washer.ProgramOption
import uk.rdfn.h.off.washer.WasherProgram
import uk.rdfn.h.off.washer.WasherService

/**
 * Set up one program's options, then start it. After Start you go back to
 * the Now screen. Ported from src/app/program/[programId].tsx.
 */
@Composable
fun ProgramDetailScreen(
	programId: String,
	washer: WasherService,
	repository: WasherRepository,
	onStarted: () -> Unit,
	onTitleKnown: (String) -> Unit,
	contentPadding: PaddingValues,
) {
	val load = rememberPrograms(washer)
	val program = load.programs?.find { it.id == programId }
	val statusState by repository.status.collectAsStateWithLifecycle()

	LaunchedEffect(program) { program?.let { onTitleKnown(it.displayName) } }

	Box(Modifier.fillMaxSize().padding(contentPadding)) {
		when {
			load.errorMessage != null -> Text(
				load.errorMessage,
				textAlign = TextAlign.Center,
				modifier = Modifier.fillMaxWidth().padding(Spacing.large),
			)
			load.programs == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
			program == null -> Text(
				"This program isn't available on the machine any more.",
				textAlign = TextAlign.Center,
				modifier = Modifier.fillMaxWidth().padding(Spacing.large),
			)
			else -> ProgramSetUp(program, repository, statusState.status, onStarted)
		}
	}
}

@Composable
private fun ProgramSetUp(
	program: WasherProgram,
	repository: WasherRepository,
	status: uk.rdfn.h.off.washer.WasherStatus?,
	onStarted: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	// Starts from the program's defaults.
	var chosenOptions by remember(program.id) { mutableStateOf<ChosenOptions>(program.options.associate { it.key to it.defaultValue }) }
	var isStarting by remember { mutableStateOf(false) }
	var startError by remember { mutableStateOf<String?>(null) }
	val machineIsBusy = status?.state?.isMidCycle == true
	val remoteControlOff = status != null && !status.remoteControlEnabled

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.medium)) {
		if (program.description.isNotEmpty()) {
			Text(program.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.small))
		}
		program.estimatedMinutes?.takeIf { it > 0 }?.let {
			Text(
				"Takes about ${Formatters.formatDuration(it)}",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				modifier = Modifier.padding(bottom = Spacing.medium),
			)
		}
		if (program.options.isNotEmpty()) {
			SectionCard("Options") {
				program.options.forEach { option ->
					val value = chosenOptions[option.key] ?: option.defaultValue
					if (option.key == OptionKeys.DELAY && option is ProgramOption.Range) {
						DelayStartControl(
							option = option,
							estimatedMinutes = program.estimatedMinutes,
							delayMinutes = value.toDoubleOrNull()?.toInt() ?: 0,
							onChange = { newDelay -> chosenOptions = chosenOptions + (option.key to newDelay.toString()) },
						)
					} else {
						ProgramOptionControl(option, value) { newValue -> chosenOptions = chosenOptions + (option.key to newValue) }
					}
				}
			}
		}
		if (machineIsBusy) WarningBanner("A program is already running. Stop it before starting another.")
		if (remoteControlOff) WarningBanner("Turn on remote control at the machine first, or it will ignore this.")
		Button(
			onClick = {
				isStarting = true
				scope.launch {
					try {
						repository.sendCommand { washer -> washer.startProgram(program.id, chosenOptions) }
						onStarted()
					} catch (exception: Exception) {
						startError = WasherRepository.describeError(exception)
					} finally {
						isStarting = false
					}
				}
			},
			enabled = !machineIsBusy && !isStarting,
			modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
		) {
			ButtonLabel("Start", isStarting)
		}
	}

	startError?.let { message ->
		AlertDialog(
			onDismissRequest = { startError = null },
			title = { Text("Couldn't start the program") },
			text = { Text(message) },
			confirmButton = { TextButton(onClick = { startError = null }) { Text("OK") } },
		)
	}
}
