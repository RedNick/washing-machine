package uk.rdfn.h.off.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.rdfn.h.off.connection.ConnectionState
import uk.rdfn.h.off.connection.WasherRepository
import uk.rdfn.h.off.describeAppVersion
import uk.rdfn.h.off.ui.Spacing
import uk.rdfn.h.off.ui.components.DetailRow
import uk.rdfn.h.off.ui.components.SectionCard
import uk.rdfn.h.off.ui.components.WarningBanner
import uk.rdfn.h.off.ui.successColour
import uk.rdfn.h.off.washer.DiagnosticBlock
import uk.rdfn.h.off.washer.Formatters
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.StatusSummary
import uk.rdfn.h.off.washer.WasherService
import uk.rdfn.h.off.washer.WasherStatus
import uk.rdfn.h.off.washer.formatHonNumber

/** The commands the Now screen can send, for the busy spinner and error dialog. */
private enum class NowCommand(val label: String) { PAUSE("pause"), RESUME("resume"), STOP("stop") }

/**
 * The main screen: what the machine is doing, time left, and the buttons
 * that make sense right now. Ported from src/app/(tabs)/index.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
	connected: ConnectionState.Connected,
	repository: WasherRepository,
	onChooseProgram: () -> Unit,
	contentPadding: PaddingValues,
) {
	val statusState by repository.status.collectAsStateWithLifecycle()
	val scope = rememberCoroutineScope()
	var commandInProgress by remember { mutableStateOf<NowCommand?>(null) }
	var commandError by remember { mutableStateOf<Pair<NowCommand, String>?>(null) }
	var isConfirmingStop by remember { mutableStateOf(false) }

	fun run(command: NowCommand) {
		commandInProgress = command
		scope.launch {
			try {
				repository.sendCommand { washer ->
					when (command) {
						NowCommand.PAUSE -> washer.pause()
						NowCommand.RESUME -> washer.resume()
						NowCommand.STOP -> washer.stop()
					}
				}
			} catch (exception: Exception) {
				commandError = command to WasherRepository.describeError(exception)
			} finally {
				commandInProgress = null
			}
		}
	}

	PullToRefreshBox(
		isRefreshing = statusState.isRefreshing && statusState.status != null,
		onRefresh = { scope.launch { repository.refreshStatus() } },
		modifier = Modifier.fillMaxSize().padding(contentPadding),
	) {
		Column(
			Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(Spacing.medium),
		) {
			Text(
				connected.machineInfo.displayName + if (connected.washer.isDemo) " · demo" else "",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = Spacing.small),
			)
			statusState.errorMessage?.let { WarningBanner(it) }

			val status = statusState.status
			if (status == null) {
				Column(Modifier.fillMaxWidth().padding(top = Spacing.large), horizontalAlignment = Alignment.CenterHorizontally) {
					CircularProgressIndicator()
					Text("Checking the machine…", modifier = Modifier.padding(top = Spacing.medium))
				}
			} else {
				StatusSummaryBlock(status)
				if (!status.remoteControlEnabled && status.state != MachineState.OFFLINE) {
					WarningBanner(
						"Remote control is off on the machine, so it will ignore commands from your phone. " +
							"Turn it on at the machine to start, pause or stop from here.",
					)
				}
				Controls(
					status = status,
					commandInProgress = commandInProgress,
					onPause = { run(NowCommand.PAUSE) },
					onResume = { run(NowCommand.RESUME) },
					onStop = { isConfirmingStop = true },
					onChooseProgram = onChooseProgram,
				)
				CycleDetails(status)
				MachineReadings(status, connected.washer)
			}

			// Standing rule: every build shows its version on the main screen.
			Text(
				describeAppVersion(),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
				modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.large),
			)
		}
	}

	if (isConfirmingStop) {
		AlertDialog(
			onDismissRequest = { isConfirmingStop = false },
			title = { Text("Stop the wash?") },
			text = { Text("The program will be cancelled. The door may stay locked while water drains.") },
			dismissButton = { TextButton(onClick = { isConfirmingStop = false }) { Text("Keep washing") } },
			confirmButton = {
				TextButton(
					onClick = {
						isConfirmingStop = false
						run(NowCommand.STOP)
					},
					colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
				) { Text("Stop") }
			},
		)
	}
	commandError?.let { (command, message) ->
		AlertDialog(
			onDismissRequest = { commandError = null },
			title = { Text("Couldn't ${command.label}") },
			text = { Text(message) },
			confirmButton = { TextButton(onClick = { commandError = null }) { Text("OK") } },
		)
	}
}

@Composable
private fun StatusSummaryBlock(status: WasherStatus) {
	val summary = StatusSummary.of(status)
	Column(Modifier.padding(bottom = Spacing.medium)) {
		Text(
			summary.headline,
			style = MaterialTheme.typography.headlineLarge,
			fontWeight = FontWeight.ExtraBold,
			color = if (status.state == MachineState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
		)
		status.programName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
		summary.timeText?.let {
			Text(
				it,
				style = MaterialTheme.typography.displayMedium,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.padding(top = Spacing.small),
			)
		}
		summary.caption?.let {
			Text(
				it,
				color = when (status.state) {
					MachineState.FINISHED -> successColour
					MachineState.ERROR -> MaterialTheme.colorScheme.error
					else -> MaterialTheme.colorScheme.onSurfaceVariant
				},
			)
		}
		summary.phaseText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
		if (status.errorCode != null && status.state != MachineState.ERROR) {
			Text("Error code ${status.errorCode}", color = MaterialTheme.colorScheme.error)
		}
	}
}

/** Only the buttons that make sense right now. */
@Composable
private fun Controls(
	status: WasherStatus,
	commandInProgress: NowCommand?,
	onPause: () -> Unit,
	onResume: () -> Unit,
	onStop: () -> Unit,
	onChooseProgram: () -> Unit,
) {
	val isBusy = commandInProgress != null
	Row(Modifier.fillMaxWidth().padding(bottom = Spacing.medium), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
		when (status.state) {
			MachineState.RUNNING, MachineState.PAUSED, MachineState.SCHEDULED -> {
				if (status.state == MachineState.RUNNING) {
					FilledTonalButton(onClick = onPause, enabled = !isBusy, modifier = Modifier.weight(1f)) {
						ButtonLabel("Pause", commandInProgress == NowCommand.PAUSE)
					}
				}
				if (status.state == MachineState.PAUSED) {
					Button(onClick = onResume, enabled = !isBusy, modifier = Modifier.weight(1f)) {
						ButtonLabel("Resume", commandInProgress == NowCommand.RESUME)
					}
				}
				Button(
					onClick = onStop,
					enabled = !isBusy,
					modifier = Modifier.weight(1f),
					colors = ButtonDefaults.buttonColors(
						containerColor = MaterialTheme.colorScheme.error,
						contentColor = MaterialTheme.colorScheme.onError,
					),
				) {
					ButtonLabel("Stop", commandInProgress == NowCommand.STOP)
				}
			}
			MachineState.IDLE, MachineState.FINISHED ->
				Button(onClick = onChooseProgram, modifier = Modifier.weight(1f)) { Text("Choose a program") }
			else -> Unit
		}
	}
}

@Composable
fun ButtonLabel(label: String, isBusy: Boolean) {
	if (isBusy) {
		CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = Spacing.small).size(20.dp))
	}
	Text(label)
}

/** Temperature, spin and what this wash has used so far. */
@Composable
private fun CycleDetails(status: WasherStatus) {
	if (status.state != MachineState.RUNNING && status.state != MachineState.PAUSED) return
	SectionCard("This wash") {
		status.temperatureCelsius?.let {
			DetailRow("Temperature", if (it == 0.0) "Cold" else "${formatHonNumber(it)} °C")
		}
		status.spinSpeedRpm?.takeIf { it > 0 }?.let { DetailRow("Spin", "${formatHonNumber(it)} rpm") }
		status.currentCycleEnergyKwh?.let { DetailRow("Energy so far", Formatters.formatAmount(it, "kWh", decimals = 2)) }
		status.currentCycleWaterLitres?.let { DetailRow("Water so far", Formatters.formatAmount(it, "L", decimals = 0)) }
		status.doorLocked?.let { DetailRow("Door", if (it) "Locked" else "Unlocked") }
	}
}

/**
 * Everything the machine reported, unprocessed, behind a "Show" button.
 * Lets a screenshot show what this particular model actually sends.
 */
@Composable
private fun MachineReadings(status: WasherStatus, washer: WasherService) {
	val scope = rememberCoroutineScope()
	var isExpanded by rememberSaveable { mutableStateOf(false) }
	var diagnostics by remember { mutableStateOf<List<DiagnosticBlock>?>(null) }
	var diagnosticsError by remember { mutableStateOf<String?>(null) }
	var isLoadingDiagnostics by remember { mutableStateOf(false) }

	SectionCard("Machine readings") {
		DetailRow("Checked", Formatters.formatPastDate(status.fetchedAt))
		TextButton(onClick = { isExpanded = !isExpanded }) {
			Text(if (isExpanded) "Hide raw readings" else "Show raw readings (${status.rawReadings.size})")
		}
		if (!isExpanded) return@SectionCard
		status.rawReadings.forEach { reading ->
			DetailRow(
				reading.name,
				reading.lastUpdated?.let { "${reading.value}  ·  ${Formatters.formatPastDate(it)}" } ?: reading.value,
			)
		}
		if (!washer.isDemo) {
			TextButton(
				enabled = !isLoadingDiagnostics,
				onClick = {
					isLoadingDiagnostics = true
					diagnosticsError = null
					scope.launch {
						try {
							diagnostics = washer.getDiagnostics()
						} catch (exception: Exception) {
							diagnosticsError = WasherRepository.describeError(exception)
						} finally {
							isLoadingDiagnostics = false
						}
					}
				},
			) {
				Text(
					when {
						isLoadingDiagnostics -> "Loading activity and history…"
						diagnostics != null -> "Reload activity and history"
						else -> "Load activity and history"
					},
				)
			}
		}
		diagnosticsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
		diagnostics?.forEach { block ->
			Text(
				block.title,
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = Spacing.small),
			)
			SelectionContainer {
				Text(block.json, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
			}
		}
	}
}
