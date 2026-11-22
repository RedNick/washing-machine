package uk.rdfn.h.off.alerts

import kotlin.math.abs
import kotlinx.serialization.Serializable
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.WasherStatus

/** What we remember between checks. Saved on the phone. */
@Serializable
data class FinishAlertMemory(
	val scheduledNotificationId: String? = null,
	/** When the scheduled alert will go off, in milliseconds since 1970. */
	val scheduledForMillis: Long? = null,
	/** Machine state at the previous check. */
	val previousState: MachineState? = null,
	/**
	 * Last program name we saw. The background check skips the program list
	 * to save data, so it relies on this to name the program in the alert.
	 */
	val lastKnownProgramName: String? = null,
)

/** One thing to do about the alert. */
sealed interface FinishAlertStep {
	data class CancelScheduled(val notificationId: String) : FinishAlertStep
	data class Schedule(val fireAtMillis: Long, val programName: String?) : FinishAlertStep
	data class ShowNow(val reason: Reason, val programName: String?, val errorCode: String?) : FinishAlertStep

	enum class Reason { FINISHED, ERROR }
}

/**
 * Decides what to do about the "wash finished" alert each time we get a
 * new status. Kept free of any phone APIs so it can be tested on its own.
 *
 * How the alert works: while a wash runs, we schedule a phone notification
 * for the moment the machine says it will finish, and move it whenever the
 * time left changes. If we then see the wash has ended before that alert
 * went off (for example it finished early), we show the alert straight away.
 *
 * Ported from src/notifications/planFinishAlert.ts.
 */
object FinishAlertPlanner {
	/** Don't move the alert for small wobbles in the machine's estimate. */
	private const val RESCHEDULE_THRESHOLD_MILLIS = 2 * 60 * 1000L

	data class Plan(val steps: List<FinishAlertStep>, val nextMemory: FinishAlertMemory)

	fun plan(memory: FinishAlertMemory, status: WasherStatus, nowMillis: Long): Plan {
		val steps = mutableListOf<FinishAlertStep>()
		var scheduledNotificationId = memory.scheduledNotificationId
		var scheduledForMillis = memory.scheduledForMillis

		fun cancelScheduled() {
			scheduledNotificationId?.let { steps += FinishAlertStep.CancelScheduled(it) }
			scheduledNotificationId = null
			scheduledForMillis = null
		}

		val programName = status.programName ?: memory.lastKnownProgramName
		val wasMidCycle = memory.previousState?.isMidCycle == true
		val scheduledAlertAlreadyFired = scheduledForMillis?.let { it <= nowMillis } == true

		when (status.state) {
			MachineState.RUNNING -> {
				val expectedFinishMillis = nowMillis + status.remainingMinutes * 60_000L
				val needsNewAlert = scheduledForMillis
					?.let { abs(expectedFinishMillis - it) > RESCHEDULE_THRESHOLD_MILLIS } ?: true
				if (needsNewAlert && status.remainingMinutes > 0) {
					cancelScheduled()
					steps += FinishAlertStep.Schedule(expectedFinishMillis, programName)
					// The real id is filled in by whoever carries out the step.
					scheduledForMillis = expectedFinishMillis
				}
			}

			// The finish time is unknown until it runs again.
			MachineState.PAUSED, MachineState.SCHEDULED -> cancelScheduled()

			MachineState.FINISHED -> {
				val alertStillPending = scheduledForMillis != null && !scheduledAlertAlreadyFired
				val missedTheEnd = wasMidCycle && (scheduledForMillis == null || alertStillPending)
				cancelScheduled()
				if (missedTheEnd) steps += FinishAlertStep.ShowNow(FinishAlertStep.Reason.FINISHED, programName, null)
			}

			MachineState.ERROR -> {
				cancelScheduled()
				if (memory.previousState != MachineState.ERROR) {
					steps += FinishAlertStep.ShowNow(FinishAlertStep.Reason.ERROR, programName, status.errorCode)
				}
			}

			// Someone stopped the wash, or it was never started.
			MachineState.IDLE -> cancelScheduled()

			// A lost connection doesn't mean the wash stopped, so keep the alert.
			MachineState.OFFLINE -> Unit
		}

		return Plan(
			steps = steps,
			nextMemory = FinishAlertMemory(
				scheduledNotificationId = scheduledNotificationId,
				scheduledForMillis = scheduledForMillis,
				// Remember the last real state, so going offline briefly doesn't hide a finish.
				previousState = if (status.state == MachineState.OFFLINE) memory.previousState else status.state,
				lastKnownProgramName = programName,
			),
		)
	}
}
