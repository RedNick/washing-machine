package uk.rdfn.h.off.washer

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from src/washer/__tests__/formatters.test.ts. */
class FormattersTest {
	private val london = ZoneId.of("Europe/London")
	private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
		LocalDateTime.of(year, month, day, hour, minute).atZone(london).toInstant()

	@Test
	fun formatsDurations() {
		mapOf(0 to "0 min", 45 to "45 min", 60 to "1 h", 83 to "1 h 23 min", 163 to "2 h 43 min").forEach { (minutes, expected) ->
			assertEquals(expected, Formatters.formatDuration(minutes))
		}
	}

	@Test
	fun usesFriendlyWordsForSpecialOptionValues() {
		assertEquals("Cold", Formatters.formatOptionValue("temp", "0", "°C"))
		assertEquals("40 °C", Formatters.formatOptionValue("temp", "40", "°C"))
		assertEquals("No spin", Formatters.formatOptionValue("spinSpeed", "0", "rpm"))
		assertEquals("Off", Formatters.formatOptionValue("delayTime", "0", "min"))
		assertEquals("1 h 30 min", Formatters.formatOptionValue("delayTime", "90", "min"))
		assertEquals("None", Formatters.formatOptionValue("extraRinses", "0", null))
		assertEquals("2", Formatters.formatOptionValue("extraRinses", "2", null))
	}

	@Test
	fun saysTodayAndYesterdayForRecentDates() {
		val now = at(2026, 10, 25, 15, 0)
		assertEquals("Today 09:30", Formatters.formatPastDate(at(2026, 10, 25, 9, 30), now, london))
		assertTrue(Formatters.formatPastDate(at(2026, 10, 24, 21, 0), now, london).startsWith("Yesterday "))
		assertEquals("12 Oct 18:20", Formatters.formatPastDate(at(2026, 10, 12, 18, 20), now, london))
	}

	@Test
	fun saysTomorrowOnlyWhenTheTimeIsOnTheNextDay() {
		val now = at(2026, 10, 28, 21, 15)
		assertFalse(Formatters.formatClockTimeFromNow(90, now, london).contains("tomorrow"))
		assertEquals("tomorrow 00:15", Formatters.formatClockTimeFromNow(180, now, london))
	}
}
