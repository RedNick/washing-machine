package uk.rdfn.h.off.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.rdfn.h.off.AppContainer
import uk.rdfn.h.off.connection.ConnectionState
import uk.rdfn.h.off.connection.StatusFollowUp
import uk.rdfn.h.off.connection.WasherRepository
import uk.rdfn.h.off.describeAppVersion
import uk.rdfn.h.off.diagnostics.Reports
import uk.rdfn.h.off.reporting.ReportSharing
import uk.rdfn.h.off.storage.DiagnosticsSettings
import uk.rdfn.h.off.ui.Spacing
import uk.rdfn.h.off.ui.components.SectionCard

/**
 * Choices about sending data back to help improve h.off. Everything is off
 * by default. Ported from src/app/settings.tsx.
 */
@Composable
fun SettingsScreen(connected: ConnectionState.Connected, container: AppContainer, contentPadding: PaddingValues) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings by container.storage.diagnosticsSettings.collectAsStateWithLifecycle(initialValue = DiagnosticsSettings())
	var isSharingReport by remember { mutableStateOf(false) }
	var reportError by remember { mutableStateOf<String?>(null) }

	fun changeSettings(changed: DiagnosticsSettings) {
		scope.launch { container.storage.saveDiagnosticsSettings(changed) }
	}

	Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(Spacing.medium)) {
		SectionCard("Report a problem") {
			Text(
				"Makes a file with what h.off can see of your machine: its model, current readings, recent activity and " +
					"program list. Sign-in details, the machine's name, MAC address and serial number are removed. " +
					"You choose where to send it.",
			)
			Button(
				enabled = !isSharingReport,
				onClick = {
					isSharingReport = true
					reportError = null
					scope.launch {
						try {
							val report = Reports.buildDiagnosticReport(
								washer = connected.washer,
								machineInfo = connected.machineInfo,
								app = StatusFollowUp.appDetails(),
								finishedCycles = container.storage.loadCycleLog(connected.washer.isDemo).entries,
							)
							ReportSharing.share(context, report)
						} catch (exception: Exception) {
							reportError = WasherRepository.describeError(exception)
						} finally {
							isSharingReport = false
						}
					}
				},
				modifier = Modifier.padding(vertical = Spacing.medium),
			) {
				ButtonLabel("Send a report", isSharingReport)
			}
			reportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
		}

		SectionCard("Automatic reports") {
			SettingSwitch(
				label = "Crash reports",
				explanation = "Sends details of what went wrong to Sentry (sentry.io) when h.off crashes. Private details are removed first.",
				isOn = settings.sendCrashReports,
				isAvailable = container.crashReporting.isSetUp,
				onChange = { isOn ->
					changeSettings(settings.copy(sendCrashReports = isOn))
					container.crashReporting.setEnabled(isOn)
				},
			)
			HorizontalDivider()
			SettingSwitch(
				label = "Wash reports",
				explanation = "At the end of each wash, sends the program, what it used and the machine's final readings to the " +
					"developer's server, to improve how h.off spots the end of a wash. No account details.",
				isOn = settings.sendWashReports,
				isAvailable = container.washReporter.isSetUp,
				onChange = { isOn -> changeSettings(settings.copy(sendWashReports = isOn)) },
			)
		}

		Text(
			describeAppVersion(),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
		)
	}
}

/** One on/off setting with a short explanation underneath. */
@Composable
private fun SettingSwitch(label: String, explanation: String, isOn: Boolean, isAvailable: Boolean, onChange: (Boolean) -> Unit) {
	Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f).padding(end = Spacing.medium)) {
			Text(label, fontWeight = FontWeight.SemiBold)
			Text(
				if (isAvailable) explanation else "$explanation Not set up yet.",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Switch(
			checked = isAvailable && isOn,
			enabled = isAvailable,
			onCheckedChange = onChange,
			modifier = Modifier.semantics { contentDescription = label },
		)
	}
}
