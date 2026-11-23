package uk.rdfn.h.off.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import uk.rdfn.h.off.connection.ConnectionState
import uk.rdfn.h.off.connection.WasherRepository
import uk.rdfn.h.off.history.FinishedCycle
import uk.rdfn.h.off.storage.AppStorage
import uk.rdfn.h.off.ui.Spacing
import uk.rdfn.h.off.ui.components.DetailRow
import uk.rdfn.h.off.ui.components.SectionCard
import uk.rdfn.h.off.ui.components.WarningBanner
import uk.rdfn.h.off.washer.Formatters
import uk.rdfn.h.off.washer.PastCommand
import uk.rdfn.h.off.washer.TemperatureBand
import uk.rdfn.h.off.washer.UsageSummary

private val COMMAND_LABELS = mapOf(
	"startProgram" to "Started",
	"stopProgram" to "Stopped",
	"pauseProgram" to "Paused",
	"resumeProgram" to "Resumed",
)

/**
 * Energy and water use, past washes, recent commands, and the Settings and
 * sign-out buttons. Ported from src/app/(tabs)/history.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
	connected: ConnectionState.Connected,
	storage: AppStorage,
	onOpenSettings: () -> Unit,
	onSignOut: () -> Unit,
	contentPadding: PaddingValues,
) {
	val washer = connected.washer
	var usage by remember { mutableStateOf<UsageSummary?>(null) }
	var finishedCycles by remember { mutableStateOf<List<FinishedCycle>>(emptyList()) }
	var recentCommands by remember { mutableStateOf<List<PastCommand>>(emptyList()) }
	var loadError by remember { mutableStateOf<String?>(null) }
	var isLoading by remember { mutableStateOf(false) }
	var attempt by remember { mutableIntStateOf(0) }

	// Reloads each time the tab is opened, and on pull to refresh.
	LaunchedEffect(washer, attempt) {
		isLoading = true
		loadError = null
		try {
			coroutineScope {
				val loadedUsage = async { washer.getUsageSummary() }
				val loadedCommands = async { washer.getRecentCommands() }
				finishedCycles = storage.loadCycleLog(washer.isDemo).entries
				usage = loadedUsage.await()
				recentCommands = loadedCommands.await().take(15)
			}
		} catch (exception: Exception) {
			loadError = WasherRepository.describeError(exception)
		} finally {
			isLoading = false
		}
	}

	PullToRefreshBox(
		isRefreshing = isLoading,
		onRefresh = { attempt += 1 },
		modifier = Modifier.fillMaxSize().padding(contentPadding),
	) {
		Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.medium)) {
			loadError?.let { WarningBanner(it) }

			usage?.let { totals ->
				SectionCard("Since the machine was new") {
					DetailRow("Washes", totals.totalCycles?.let { "%.0f".format(Locale.UK, it) } ?: "–")
					DetailRow("Energy", totals.totalEnergyKwh?.let { "%.1f kWh".format(Locale.UK, it) } ?: "–")
					DetailRow("Water", totals.totalWaterLitres?.let { "%,.0f L".format(Locale.UK, it) } ?: "–")
					val cycles = totals.totalCycles
					val energy = totals.totalEnergyKwh
					val water = totals.totalWaterLitres
					if (cycles != null && cycles > 0 && energy != null && water != null) {
						Text(
							"About ${"%.2f".format(Locale.UK, energy / cycles)} kWh and ${Math.round(water / cycles)} L per wash.",
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = Spacing.small),
						)
					}
				}
				if (totals.temperatureBands.isNotEmpty()) {
					SectionCard("Temperatures used") { TemperatureBars(totals.temperatureBands) }
				}
			}

			SectionCard("Your washes") {
				if (finishedCycles.isEmpty()) {
					Text(
						"Washes appear here once they finish while this app is installed.",
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				} else {
					finishedCycles.take(20).forEachIndexed { index, cycle ->
						if (index > 0) HorizontalDivider()
						HistoryRow(
							title = cycle.programName ?: "Wash",
							detail = listOfNotNull(
								runCatching { Formatters.formatPastDate(Instant.parse(cycle.finishedAt)) }.getOrNull(),
								cycle.energyKwh?.let { "%.2f kWh".format(Locale.UK, it) },
								cycle.waterLitres?.let { "${Math.round(it)} L" },
							).joinToString(" · "),
						)
					}
				}
			}

			if (recentCommands.isNotEmpty()) {
				SectionCard("Recent commands") {
					recentCommands.forEachIndexed { index, command ->
						if (index > 0) HorizontalDivider()
						HistoryRow(
							title = (COMMAND_LABELS[command.commandName] ?: command.commandName) +
								(command.programName?.let { " · $it" } ?: ""),
							detail = command.sentAt?.let(Formatters::formatPastDate),
						)
					}
				}
			}

			SectionCard("Machine") {
				DetailRow("Name", connected.machineInfo.displayName)
				DetailRow("Model", connected.machineInfo.modelName)
				if (connected.machineInfo.brand.isNotEmpty()) DetailRow("Brand", connected.machineInfo.brand)
			}

			OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings and reports") }
			OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = Spacing.small, bottom = Spacing.large)) {
				Text(if (washer.isDemo) "Leave the demo" else "Sign out")
			}
		}
	}
}

@Composable
private fun HistoryRow(title: String, detail: String?) {
	Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
		Text(title, fontWeight = FontWeight.SemiBold)
		if (!detail.isNullOrEmpty()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

/** A tiny bar chart of how often each temperature band is used. */
@Composable
private fun TemperatureBars(bands: List<TemperatureBand>) {
	val highestCount = maxOf(1, bands.maxOf { it.count })
	bands.forEach { band ->
		Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
			Text(band.label, modifier = Modifier.width(80.dp))
			Box(
				Modifier
					.weight(1f)
					.height(10.dp)
					.padding(horizontal = Spacing.small)
					.clip(RoundedCornerShape(5.dp))
					.background(MaterialTheme.colorScheme.surfaceVariant),
			) {
				Box(
					Modifier
						.fillMaxHeight()
						.fillMaxWidth(band.count.toFloat() / highestCount)
						.clip(RoundedCornerShape(5.dp))
						.background(MaterialTheme.colorScheme.primary),
				)
			}
			Text(
				band.count.toString(),
				textAlign = TextAlign.End,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.width(32.dp),
			)
		}
	}
}
