package uk.rdfn.h.off.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.rdfn.h.off.connection.WasherRepository
import uk.rdfn.h.off.programs.FabricType
import uk.rdfn.h.off.programs.ProgramList
import uk.rdfn.h.off.programs.ProgramListChoices
import uk.rdfn.h.off.storage.AppStorage
import uk.rdfn.h.off.ui.Spacing
import uk.rdfn.h.off.washer.Formatters
import uk.rdfn.h.off.washer.WasherProgram
import uk.rdfn.h.off.washer.WasherService

/** The machine's programs, loaded once per screen, with a way to try again. */
class ProgramsLoad(val programs: List<WasherProgram>?, val errorMessage: String?, val reload: () -> Unit)

@Composable
fun rememberPrograms(washer: WasherService): ProgramsLoad {
	var attempt by remember { mutableIntStateOf(0) }
	var programs by remember(washer) { mutableStateOf<List<WasherProgram>?>(null) }
	var errorMessage by remember(washer) { mutableStateOf<String?>(null) }
	LaunchedEffect(washer, attempt) {
		errorMessage = null
		try {
			// The real machine downloads its catalogue only once, so this is quick after the first time.
			programs = washer.getPrograms()
		} catch (exception: Exception) {
			errorMessage = WasherRepository.describeError(exception)
		}
	}
	return ProgramsLoad(programs, errorMessage) { attempt += 1 }
}

/**
 * Every program the machine offers, with favourites first, fabric chips
 * and search, and an edit mode for hiding the ones you never use.
 * Ported from src/app/(tabs)/programs.tsx.
 */
@Composable
fun ProgramsScreen(
	washer: WasherService,
	storage: AppStorage,
	onOpenProgram: (WasherProgram) -> Unit,
	contentPadding: PaddingValues,
) {
	val load = rememberPrograms(washer)
	val choices by storage.programListChoices.collectAsStateWithLifecycle(initialValue = ProgramListChoices())
	val scope = rememberCoroutineScope()
	var isEditing by rememberSaveable { mutableStateOf(false) }
	var searchText by rememberSaveable { mutableStateOf("") }
	fun changeChoices(change: (ProgramListChoices) -> ProgramListChoices) {
		scope.launch { storage.updateProgramListChoices(change) }
	}

	val programs = load.programs
	if (load.errorMessage != null || programs == null) {
		Column(
			Modifier.fillMaxSize().padding(contentPadding).padding(Spacing.large),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			if (load.errorMessage != null) {
				Text(load.errorMessage, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Button(onClick = load.reload, modifier = Modifier.padding(top = Spacing.medium)) { Text("Try again") }
			} else {
				CircularProgressIndicator()
			}
		}
		return
	}

	val organised = ProgramList.organise(programs, choices, includeHidden = isEditing, searchText = searchText)
	val chosenFabric = organised.fabricFilter
	val isSearching = searchText.isNotBlank()
	val emptyListMessage = when {
		isSearching && chosenFabric != null -> "No ${chosenFabric.title.lowercase()} programs match \"${searchText.trim()}\"."
		isSearching -> "No programs match \"${searchText.trim()}\"."
		chosenFabric != null -> "Every ${chosenFabric.title.lowercase()} program is hidden."
		else -> null
	}
	val favouriteIds = choices.favouriteIds.toSet()
	val hiddenIds = choices.hiddenIds.toSet()

	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(
			start = Spacing.medium,
			end = Spacing.medium,
			top = contentPadding.calculateTopPadding() + Spacing.small,
			bottom = contentPadding.calculateBottomPadding() + Spacing.large,
		),
	) {
		item(key = "search") {
			OutlinedTextField(
				value = searchText,
				onValueChange = { searchText = it },
				placeholder = { Text("Search programs") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
		}
		item(key = "fabrics") {
			FabricChips(organised.availableFabrics, chosenFabric) { fabric -> changeChoices { it.chooseFabric(fabric) } }
		}
		item(key = "hint") {
			Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
				Text(
					if (isEditing) "Hide the programs you never use." else "Tap ☆ to add a favourite.",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.weight(1f),
				)
				TextButton(onClick = { isEditing = !isEditing }) { Text(if (isEditing) "Done" else "Edit list") }
			}
		}
		if (organised.sections.isEmpty() && emptyListMessage != null) {
			item(key = "empty") {
				Column(Modifier.fillMaxWidth().padding(top = Spacing.large), horizontalAlignment = Alignment.CenterHorizontally) {
					Text(emptyListMessage, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
					if (chosenFabric != null) {
						TextButton(onClick = { changeChoices { it.chooseFabric(null) } }) { Text("Show all fabrics") }
					}
				}
			}
		}
		organised.sections.forEach { section ->
			item(key = "section-${section.key}") {
				Text(
					section.title,
					style = MaterialTheme.typography.titleSmall,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.small),
				)
			}
			items(section.programs, key = { "${section.key}-${it.id}" }) { program ->
				ProgramRow(
					program = program,
					isEditing = isEditing,
					isFavourite = program.id in favouriteIds,
					isHidden = program.id in hiddenIds,
					onOpen = { onOpenProgram(program) },
					onToggleFavourite = { changeChoices { it.toggleFavourite(program.id) } },
					onToggleHidden = { changeChoices { it.toggleHidden(program.id) } },
				)
			}
		}
		if (!isEditing && organised.hiddenCount > 0) {
			item(key = "hidden-footer") {
				Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
					TextButton(onClick = { isEditing = true }) {
						Text(
							if (organised.hiddenCount == 1) "1 hidden program · Show" else "${organised.hiddenCount} hidden programs · Show",
						)
					}
				}
			}
		}
	}
}

/** One fabric at a time; tapping the chosen one again goes back to All. */
@Composable
private fun FabricChips(availableFabrics: List<FabricType>, chosenFabric: FabricType?, onChoose: (FabricType?) -> Unit) {
	Row(
		Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Spacing.small),
		horizontalArrangement = Arrangement.spacedBy(Spacing.small),
	) {
		FilterChip(selected = chosenFabric == null, onClick = { onChoose(null) }, label = { Text("All") })
		availableFabrics.forEach { fabric ->
			FilterChip(
				selected = chosenFabric == fabric,
				onClick = { onChoose(if (chosenFabric == fabric) null else fabric) },
				label = { Text(fabric.title) },
				modifier = Modifier.semantics { contentDescription = "Show only ${fabric.title.lowercase()} programs" },
			)
		}
	}
}

@Composable
private fun ProgramRow(
	program: WasherProgram,
	isEditing: Boolean,
	isFavourite: Boolean,
	isHidden: Boolean,
	onOpen: () -> Unit,
	onToggleFavourite: () -> Unit,
	onToggleHidden: () -> Unit,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = Spacing.small)
			.clickable(enabled = !isEditing, onClick = onOpen),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
	) {
		Row(Modifier.padding(horizontal = Spacing.small, vertical = Spacing.small), verticalAlignment = Alignment.CenterVertically) {
			if (!isHidden) {
				IconButton(
					onClick = onToggleFavourite,
					modifier = Modifier.semantics {
						contentDescription = if (isFavourite) {
							"Remove ${program.displayName} from favourites"
						} else {
							"Add ${program.displayName} to favourites"
						}
					},
				) {
					Text(
						if (isFavourite) "★" else "☆",
						fontSize = 22.sp,
						color = if (isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
			Column(Modifier.weight(1f).padding(horizontal = Spacing.small)) {
				Text(program.displayName, fontWeight = FontWeight.SemiBold)
				if (program.description.isNotEmpty()) {
					Text(
						program.description,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
			val estimatedMinutes = program.estimatedMinutes
			when {
				isEditing -> TextButton(onClick = onToggleHidden) { Text(if (isHidden) "Show" else "Hide") }
				estimatedMinutes != null && estimatedMinutes > 0 -> Text(
					Formatters.formatDuration(estimatedMinutes),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(end = Spacing.small),
				)
			}
		}
	}
}
