package uk.rdfn.h.off.hon

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rdfn.h.off.hon.HonFixtures.sampleCatalogue
import uk.rdfn.h.off.washer.ProgramOption

/** Ported from src/hon/__tests__/parseProgramCatalogue.test.ts. */
class ProgramCatalogueTest {
	private val programs = ProgramCatalogue.parsePrograms(sampleCatalogue)

	@Test
	fun turnsProgramIdsIntoReadableNames() {
		mapOf(
			"PROGRAMS.WM_WD.COTTONS" to "Cottons",
			"PROGRAMS.WM_WD.ECO_40_60_NEW_ENERGY_LABEL" to "Eco 40-60",
			"PROGRAMS.WM_WD.IOT_WASH_MEN_S_TROUSERS" to "Men's trousers",
			"PROGRAMS.WM_WD.RAPID_WASH_AND_DRY_59_MIN" to "Rapid wash and dry 59 min",
		).forEach { (programId, expectedName) ->
			assertEquals(expectedName, ProgramCatalogue.programDisplayName(programId))
		}
	}

	@Test
	fun listsBuiltInProgramsFirstThenAppExtrasAlphabetically() {
		assertEquals(
			listOf("Cottons", "Eco 40-60", "Hidden one", "Bed linen", "Men's trousers"),
			programs.map { it.displayName },
		)
	}

	@Test
	fun hidesRecipePrograms() {
		assertFalse(programs.any { it.id == "PROGRAMS.WM_WD.IOT_RECIPE_SECRET" })
	}

	@Test
	fun keepsProgramsMarkedRemoteVisibleZeroBecauseTheHonAppStartsThemAnyway() {
		assertTrue(programs.any { it.id == "PROGRAMS.WM_WD.HIDDEN_ONE" })
	}

	@Test
	fun keepsProgramsWhoseRemoteVisibleValueIsMissingOrEmpty() {
		val hiddenOne = sampleCatalogue["startProgram"]["PROGRAMS.WM_WD.HIDDEN_ONE"] as JsonObject
		val catalogueWithBlankVisibility = buildJsonObject {
			putJsonObject("startProgram") {
				put(
					"PROGRAMS.WM_WD.COTTON",
					JsonObject(
						hiddenOne + ("ancillaryParameters" to buildJsonObject {
							putJsonObject("remoteVisible") {
								put("category", "general")
								put("typology", "fixed")
								put("fixedValue", null as String?)
							}
						}),
					),
				)
				put("PROGRAMS.WM_WD.SYNTHETIC", JsonObject(hiddenOne + ("ancillaryParameters" to JsonObject(emptyMap()))))
			}
		}
		assertEquals(
			listOf("PROGRAMS.WM_WD.COTTON", "PROGRAMS.WM_WD.SYNTHETIC"),
			ProgramCatalogue.parsePrograms(catalogueWithBlankVisibility).map { it.id },
		)
	}

	@Test
	fun listsTheProgramsItLeavesOutWithTheReason() {
		val leftOut = ProgramCatalogue.listLeftOutPrograms(sampleCatalogue)
		assertEquals(listOf("PROGRAMS.WM_WD.IOT_RECIPE_SECRET"), leftOut.map { it.id })
		assertTrue(leftOut.single().reason.contains("recipe"))
	}

	@Test
	fun turnsParametersIntoOptionsTheUserCanChange() {
		val cottons = programs.first()
		assertEquals(163, cottons.estimatedMinutes)
		assertEquals(
			listOf(
				ProgramOption.Choice("temp", "Temperature", listOf("0", "20", "40", "60", "90"), "40", "°C"),
				ProgramOption.Choice("spinSpeed", "Spin speed", listOf("0", "800", "1400"), "1400", "rpm"),
				ProgramOption.Choice("extraRinses", "Extra rinses", listOf("0", "1", "2"), "1", null),
				ProgramOption.Range("delayTime", "Delay start", 0.0, 1410.0, 30.0, 0.0, "min"),
				ProgramOption.Toggle("prewash", "Prewash", isOnByDefault = false),
			),
			cottons.options,
		)
	}

	@Test
	fun leavesOutSettingsThatAreNotWashOptions() {
		val optionKeys = programs.first().options.map { it.key }
		assertFalse("lang" in optionKeys)
		assertFalse("delayStatus" in optionKeys)
	}

	@Test
	fun mapsTheMachinesProgramNumbersToNames() {
		val programNameByCode = ProgramCatalogue.buildProgramNameByCode(sampleCatalogue)
		assertEquals("Cottons", programNameByCode[201])
		assertEquals("Men's trousers", programNameByCode[300])
	}
}
