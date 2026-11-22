package uk.rdfn.h.off.programs

import kotlinx.serialization.Serializable
import uk.rdfn.h.off.washer.WasherProgram

/** What you've chosen for the program list. Saved on the phone. */
@Serializable
data class ProgramListChoices(
	/** In the order you starred them. */
	val favouriteIds: List<String> = emptyList(),
	val hiddenIds: List<String> = emptyList(),
	/** The fabric chip picked at the top of the list (its enum name), or null for "All". */
	val fabricFilter: String? = null,
) {
	val chosenFabric: FabricType? get() = FabricType.fromSavedName(fabricFilter)

	/** Picks a fabric chip, or goes back to "All" when the picked one is tapped again. */
	fun chooseFabric(fabric: FabricType?): ProgramListChoices =
		copy(fabricFilter = if (chosenFabric == fabric) null else fabric?.name)

	/** Stars or un-stars a program. A new favourite goes to the end. */
	fun toggleFavourite(programId: String): ProgramListChoices =
		copy(favouriteIds = if (programId in favouriteIds) favouriteIds - programId else favouriteIds + programId)

	/** Hides a program, or shows it again. */
	fun toggleHidden(programId: String): ProgramListChoices =
		copy(hiddenIds = if (programId in hiddenIds) hiddenIds - programId else hiddenIds + programId)
}

data class ProgramSection(val key: String, val title: String, val programs: List<WasherProgram>)

data class OrganisedPrograms(
	val sections: List<ProgramSection>,
	val hiddenCount: Int,
	/** The fabric chips worth showing: only fabrics this machine has programs for. */
	val availableFabrics: List<FabricType>,
	/** The chip in effect (a saved fabric the machine has no programs for counts as "All"). */
	val fabricFilter: FabricType?,
)

/**
 * Building the Programs screen's list. Ported from src/programs/organisePrograms.ts.
 */
object ProgramList {
	/**
	 * True when every word typed appears at the start of a word in the
	 * program's name or description, so "cot 40" finds "Cottons 40 °C".
	 */
	fun matchesSearch(program: WasherProgram, searchText: String): Boolean {
		val typedWords = searchText.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
		if (typedWords.isEmpty()) return true
		val programWords = "${program.displayName} ${program.description}".lowercase().split(Regex("[^a-z0-9]+"))
		return typedWords.all { typedWord -> programWords.any { it.startsWith(typedWord) } }
	}

	/**
	 * Favourites first, then a section per fabric. With a fabric chip picked,
	 * only that fabric's programs are listed. Hidden programs are left out,
	 * unless you're editing the list, when they get a "Hidden" section of
	 * their own at the end so you can bring them back. With search text, only
	 * the matching programs are listed, in one section.
	 */
	fun organise(
		allPrograms: List<WasherProgram>,
		choices: ProgramListChoices,
		includeHidden: Boolean,
		searchText: String = "",
	): OrganisedPrograms {
		val hiddenIds = choices.hiddenIds.toSet()
		val fabricById = allPrograms.associate { it.id to FabricType.of(it) }
		val availableFabrics = FabricType.entries.filter { fabric -> allPrograms.any { fabricById[it.id] == fabric } }
		val fabricFilter = choices.chosenFabric?.takeIf { it in availableFabrics }
		val programs = if (fabricFilter != null) allPrograms.filter { fabricById[it.id] == fabricFilter } else allPrograms
		val hiddenCount = programs.count { it.id in hiddenIds }

		if (searchText.isNotBlank()) {
			val matches = programs.filter { (includeHidden || it.id !in hiddenIds) && matchesSearch(it, searchText) }
			val sections = if (matches.isNotEmpty()) listOf(ProgramSection("results", "Matching programs", matches)) else emptyList()
			return OrganisedPrograms(sections, hiddenCount, availableFabrics, fabricFilter)
		}

		val favouriteIds = choices.favouriteIds.toSet()
		val programById = programs.associateBy { it.id }
		val hidden = programs.filter { it.id in hiddenIds }
		// A program that is both hidden and a favourite shows as hidden only.
		val favourites = choices.favouriteIds.mapNotNull { programById[it] }.filter { it.id !in hiddenIds }
		val everythingElse = programs.filter { it.id !in hiddenIds && it.id !in favouriteIds }

		val sections = buildList {
			add(ProgramSection("favourites", "Favourites", favourites))
			FabricType.entries.forEach { fabric ->
				add(ProgramSection(fabric.name, fabric.title, everythingElse.filter { fabricById[it.id] == fabric }))
			}
			if (includeHidden) add(ProgramSection("hidden", "Hidden", hidden))
		}
		return OrganisedPrograms(sections.filter { it.programs.isNotEmpty() }, hiddenCount, availableFabrics, fabricFilter)
	}
}
