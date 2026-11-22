package uk.rdfn.h.off.programs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rdfn.h.off.washer.FabricHint
import uk.rdfn.h.off.washer.WasherProgram

/** Ported from src/programs/__tests__/organisePrograms.test.ts. */
class ProgramListTest {
	/** A program with just enough filled in for the list. */
	private fun makeProgram(lastIdSegment: String, displayName: String, fabricHint: FabricHint? = null) = WasherProgram(
		id = "PROGRAMS.WM_WD.$lastIdSegment",
		displayName = displayName,
		description = "",
		estimatedMinutes = null,
		isBuiltIn = true,
		fabricHint = fabricHint,
		options = emptyList(),
	)

	private val cottons = makeProgram("COTTONS", "Cottons")
	private val eco = makeProgram("ECO_40_60_NEW_ENERGY_LABEL", "Eco 40-60")
	private val care14 = makeProgram("CARE_14", "Care 14")
	private val wool = makeProgram("WOOL", "Wool")
	private val antiAllergy = makeProgram("ANTI_ALLERGY", "Anti-allergy")
	private val rinse = makeProgram("RINSE", "Rinse")
	private val mystery = makeProgram("IOT_WASH_MYSTERY", "Mystery")
	private val allPrograms = listOf(cottons, eco, care14, wool, antiAllergy, rinse, mystery)
	private val nothingChosen = ProgramListChoices()
	private val mixedNames = listOf("Eco 40-60", "Care 14", "Anti-allergy", "Rinse", "Mystery")

	private fun sectionSummary(choices: ProgramListChoices = nothingChosen, includeHidden: Boolean = false) =
		ProgramList.organise(allPrograms, choices, includeHidden).sections.map { section ->
			section.title to section.programs.map { it.displayName }
		}

	@Test
	fun putsEachProgramUnderTheRightFabric() {
		listOf(
			cottons to FabricType.COTTON,
			eco to FabricType.MIXED,
			care14 to FabricType.MIXED,
			wool to FabricType.DELICATES,
			antiAllergy to FabricType.MIXED,
			rinse to FabricType.MIXED,
			mystery to FabricType.MIXED,
			makeProgram("IOT_WASH_SILK", "Silk") to FabricType.DELICATES,
			makeProgram("SYNTHETIC_AND_COLOURED", "Synthetic and coloured") to FabricType.SYNTHETICS,
			makeProgram("IOT_WASH_MIX_AND_COLOURED_59", "Mix and coloured 59") to FabricType.MIXED,
			makeProgram("IOT_WASH_DELICATE_WHITES", "Delicate whites") to FabricType.DELICATES,
			makeProgram("IOT_WASH_COLOURED_SHIRTS", "Coloured shirts") to FabricType.COTTON,
			makeProgram("IOT_WASH_RAPID_59", "Rapid 59") to FabricType.MIXED,
			makeProgram("RAPID_WASH_AND_DRY_59_MIN", "Rapid wash and dry 59 min") to FabricType.MIXED,
			makeProgram("HIGH_DRY", "High dry") to FabricType.DRYING,
			makeProgram("WOOL_DRY", "Wool dry") to FabricType.DRYING,
			makeProgram("TUMBLING", "Tumbling") to FabricType.DRYING,
			makeProgram("IOT_DRY_WARM_EMBRACE", "Dry warm embrace") to FabricType.DRYING,
			makeProgram("IOT_DRY_SHIRTS", "Dry shirts") to FabricType.DRYING,
		).forEach { (program, expectedFabric) ->
			assertEquals(program.displayName, expectedFabric, FabricType.of(program))
		}
	}

	@Test
	fun sortsProgramsNamedAfterAColourByHonsDryTypeHint() {
		assertEquals(FabricType.SYNTHETICS, FabricType.of(makeProgram("IOT_WASH_COLOURED", "Coloured", FabricHint.SYNTHETICS)))
		assertEquals(FabricType.COTTON, FabricType.of(makeProgram("IOT_WASH_WHITES", "Whites", FabricHint.COTTON)))
		assertEquals(FabricType.MIXED, FabricType.of(makeProgram("IOT_WASH_DARK", "Dark", null)))
	}

	@Test
	fun sortsEverythingByFabricWhenNothingIsStarredOrHidden() {
		assertEquals(
			listOf("Cotton" to listOf("Cottons"), "Mixed" to mixedNames, "Delicates" to listOf("Wool")),
			sectionSummary(),
		)
	}

	@Test
	fun offersChipsOnlyForFabricsThisMachineHas() {
		assertEquals(
			listOf(FabricType.COTTON, FabricType.MIXED, FabricType.DELICATES),
			ProgramList.organise(allPrograms, nothingChosen, includeHidden = false).availableFabrics,
		)
	}

	@Test
	fun listsOnlyTheChosenFabricFavouritesIncluded() {
		val choices = nothingChosen.toggleFavourite(wool.id).chooseFabric(FabricType.MIXED)
		assertEquals(listOf("Mixed" to mixedNames), sectionSummary(choices))
		assertEquals(listOf("Favourites" to listOf("Wool")), sectionSummary(choices.chooseFabric(FabricType.DELICATES)))
	}

	@Test
	fun goesBackToAllWhenTheChosenChipIsTappedAgain() {
		assertNull(nothingChosen.chooseFabric(FabricType.COTTON).chooseFabric(FabricType.COTTON).fabricFilter)
	}

	@Test
	fun treatsASavedFabricThisMachineHasNoProgramsForAsAll() {
		val choices = nothingChosen.chooseFabric(FabricType.DRYING)
		assertEquals(listOf("Cotton", "Mixed", "Delicates"), sectionSummary(choices).map { it.first })
	}

	@Test
	fun searchesWithinTheChosenFabric() {
		val choices = nothingChosen.chooseFabric(FabricType.DELICATES)
		assertTrue(ProgramList.organise(allPrograms, choices, includeHidden = false, searchText = "co").sections.isEmpty())
	}

	@Test
	fun putsFavouritesFirstInTheOrderTheyWereStarred() {
		val choices = nothingChosen.toggleFavourite(wool.id).toggleFavourite(cottons.id)
		assertEquals(
			listOf("Favourites" to listOf("Wool", "Cottons"), "Mixed" to mixedNames),
			sectionSummary(choices).take(2),
		)
	}

	@Test
	fun leavesHiddenProgramsOutAndCountsThem() {
		val choices = nothingChosen.toggleHidden(rinse.id).toggleHidden(mystery.id)
		val organised = ProgramList.organise(allPrograms, choices, includeHidden = false)
		assertEquals(2, organised.hiddenCount)
		assertFalse(organised.sections.flatMap { it.programs }.contains(rinse))
	}

	@Test
	fun showsHiddenProgramsInTheirOwnSectionWhileEditing() {
		assertEquals("Hidden" to listOf("Rinse"), sectionSummary(nothingChosen.toggleHidden(rinse.id), includeHidden = true).last())
	}

	@Test
	fun showsAHiddenFavouriteAsHiddenOnly() {
		val choices = nothingChosen.toggleFavourite(wool.id).toggleHidden(wool.id)
		assertFalse(sectionSummary(choices).map { it.first }.contains("Favourites"))
	}

	@Test
	fun ignoresSavedProgramsThisMachineNoLongerOffers() {
		assertEquals("Cotton", sectionSummary(nothingChosen.toggleFavourite("PROGRAMS.WM_WD.GONE")).first().first)
	}

	@Test
	fun unStarsAndUnHidesOnASecondTap() {
		assertEquals(emptyList<String>(), nothingChosen.toggleFavourite(wool.id).toggleFavourite(wool.id).favouriteIds)
		assertEquals(emptyList<String>(), nothingChosen.toggleHidden(wool.id).toggleHidden(wool.id).hiddenIds)
	}

	@Test
	fun searchMatchesTheStartOfWordsInAnyOrder() {
		assertTrue(ProgramList.matchesSearch(eco, "eco"))
		assertTrue(ProgramList.matchesSearch(eco, "40 ec"))
		assertFalse(ProgramList.matchesSearch(cottons, "tons"))
		assertTrue(ProgramList.matchesSearch(cottons, ""))
	}

	@Test
	fun searchListsOnlyTheMatchesInOneSectionLeavingOutHiddenOnes() {
		val choices = ProgramListChoices(favouriteIds = listOf(cottons.id), hiddenIds = listOf(wool.id))
		val sections = ProgramList.organise(allPrograms, choices, includeHidden = false, searchText = "co").sections
		assertEquals(listOf("Matching programs" to listOf("Cottons")), sections.map { it.title to it.programs.map { p -> p.displayName } })
		assertTrue(ProgramList.organise(allPrograms, choices, includeHidden = false, searchText = "wool").sections.isEmpty())
	}
}
