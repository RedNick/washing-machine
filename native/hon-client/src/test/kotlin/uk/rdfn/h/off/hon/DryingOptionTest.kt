package uk.rdfn.h.off.hon

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.rdfn.h.off.washer.Formatters
import uk.rdfn.h.off.washer.ProgramOption

/** Ported from src/hon/__tests__/dryingOption.test.ts. */
class DryingOptionTest {
	private val temperature = ProgramOption.Choice("temp", "Temperature", listOf("30", "40"), "40", "°C")
	private val dryLevel = ProgramOption.Choice("dryLevel", "dryLevel", listOf("0", "1", "2", "3"), "0", null)
	private val dryTime = ProgramOption.Range("dryTime", "dryTime", 0.0, 4.0, 1.0, 0.0, null)

	@Test
	fun turnsTheLevelAndTimeSettingsIntoOneDryingChoice() {
		val options = DryingOption.combine(listOf(temperature, dryLevel, dryTime))
		assertEquals(listOf("temp", "drying"), options.map { it.key })
		val drying = options[1] as ProgramOption.Choice
		assertEquals("Drying", drying.label)
		assertEquals(
			listOf("none", "level:1", "level:2", "level:3", "time:1", "time:2", "time:3", "time:4"),
			drying.choices,
		)
		assertEquals("none", drying.defaultValue)
	}

	@Test
	fun startsOnTheProgramsOwnDefaultLevelOrTime() {
		val withLevel = DryingOption.combine(listOf(dryLevel.copy(defaultValue = "2"), dryTime))
		assertEquals("level:2", withLevel[0].defaultValue)
		val withTime = DryingOption.combine(listOf(dryLevel, dryTime.copy(defaultNumber = 3.0)))
		assertEquals("time:3", withTime[0].defaultValue)
	}

	@Test
	fun leavesProgramsWithoutDryingAlone() {
		assertEquals(listOf(temperature), DryingOption.combine(listOf(temperature)))
	}

	@Test
	fun dryingChoicesHaveTheNamesShownOnTheMachineAndTimesInHalfHours() {
		assertEquals("No drying", Formatters.formatOptionValue("drying", "none", null))
		assertEquals("Iron dry", Formatters.formatOptionValue("drying", "level:1", null))
		assertEquals("Cupboard dry", Formatters.formatOptionValue("drying", "level:2", null))
		assertEquals("Extra dry", Formatters.formatOptionValue("drying", "level:3", null))
		assertEquals("30 min", Formatters.formatOptionValue("drying", "time:1", null))
		assertEquals("1 h 30 min", Formatters.formatOptionValue("drying", "time:3", null))
	}

	@Test
	fun sendsOnlyALevelOrOnlyATimeNeverBoth() {
		assertEquals(mapOf("dryLevel" to "2", "dryTime" to "0"), CommandPayload.expandCombinedOptions(mapOf("drying" to "level:2")))
		assertEquals(mapOf("dryLevel" to "0", "dryTime" to "4"), CommandPayload.expandCombinedOptions(mapOf("drying" to "time:4")))
		assertEquals(mapOf("dryLevel" to "0", "dryTime" to "0"), CommandPayload.expandCombinedOptions(mapOf("drying" to "none")))
	}
}
