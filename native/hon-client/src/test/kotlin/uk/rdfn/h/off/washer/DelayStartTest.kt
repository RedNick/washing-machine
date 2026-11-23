package uk.rdfn.h.off.washer

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DelayStartTest {
	private val delayOption = ProgramOption.Range(
		key = OptionKeys.DELAY,
		label = "Delay start",
		minimum = 0.0,
		maximum = 1440.0,
		step = 30.0,
		defaultNumber = 0.0,
		unit = "min",
	)
	private val london = ZoneId.of("Europe/London")
	private val now = Instant.parse("2026-11-22T13:00:00Z")

	@Test
	fun `finish by is only offered when the program's length is known`() {
		assertEquals(DelayStart.Mode.entries, DelayStart.availableModes(95))
		assertEquals(listOf(DelayStart.Mode.NOW, DelayStart.Mode.START_AT), DelayStart.availableModes(null))
	}

	@Test
	fun `steps stay between one step and the maximum`() {
		assertEquals(30, DelayStart.delayForMode(DelayStart.Mode.START_AT, 0, delayOption))
		assertEquals(0, DelayStart.delayForMode(DelayStart.Mode.NOW, 90, delayOption))
		assertEquals(30, DelayStart.stepBy(-1, 30, delayOption))
		assertEquals(60, DelayStart.stepBy(1, 30, delayOption))
		assertEquals(1440, DelayStart.stepBy(1, 1440, delayOption))
	}

	@Test
	fun `the summary explains the start and finish times`() {
		assertEquals(
			"Starts straight away and finishes about 14:35.",
			DelayStart.summary(DelayStart.Mode.NOW, 0, 95, now, london),
		)
		assertEquals(
			"Starts in 2 h, finishes about 16:35.",
			DelayStart.summary(DelayStart.Mode.START_AT, 120, 95, now, london),
		)
		assertEquals(
			"Starts at 15:00 (in 2 h) so it's done by 16:35.",
			DelayStart.summary(DelayStart.Mode.FINISH_BY, 120, 95, now, london),
		)
		assertEquals("16:35", DelayStart.pickedTime(DelayStart.Mode.FINISH_BY, 120, 95, now, london))
	}
}
