package uk.rdfn.h.off.connection

import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.rdfn.h.off.alerts.FinishAlertPlanner
import uk.rdfn.h.off.describeAppVersion
import uk.rdfn.h.off.diagnostics.AppDetails
import uk.rdfn.h.off.diagnostics.Reports
import uk.rdfn.h.off.history.CycleLog
import uk.rdfn.h.off.notifications.FinishAlertScheduler
import uk.rdfn.h.off.notifications.WashNotifications
import uk.rdfn.h.off.reporting.WashReporter
import uk.rdfn.h.off.storage.AppStorage
import uk.rdfn.h.off.storage.StatusSnapshot
import uk.rdfn.h.off.washer.MachineInfo
import uk.rdfn.h.off.washer.StatusSummary
import uk.rdfn.h.off.washer.WasherStatus

/**
 * Everything that happens after a new status arrives, whether the app is
 * open or the 15-minute background check fetched it:
 *
 * 1. move, show or cancel the "wash finished" alert,
 * 2. add a just-finished wash to the wash log (and send a wash report, if
 *    you've switched those on),
 * 3. update the live countdown notification,
 * 4. refresh the home-screen widget and Quick Settings tile.
 */
class StatusFollowUp(
	private val storage: AppStorage,
	private val notifications: WashNotifications,
	private val alertScheduler: FinishAlertScheduler,
	private val washReporter: WashReporter,
	private val glanceSurfaces: GlanceSurfaces,
) {
	// The foreground poll and the background check could overlap.
	private val mutex = Mutex()

	suspend fun afterStatus(status: WasherStatus, machineInfo: MachineInfo, isDemo: Boolean) = mutex.withLock {
		val alertMemory = storage.loadFinishAlertMemory(isDemo)
		val plan = FinishAlertPlanner.plan(alertMemory, status, System.currentTimeMillis())
		val newAlertId = alertScheduler.carryOut(plan.steps)
		storage.saveFinishAlertMemory(
			isDemo,
			if (newAlertId != null) plan.nextMemory.copy(scheduledNotificationId = newAlertId) else plan.nextMemory,
		)

		val logUpdate = CycleLog.record(storage.loadCycleLog(isDemo), status)
		storage.saveCycleLog(isDemo, logUpdate.nextState)
		val finishedCycle = logUpdate.justFinishedCycle
		if (finishedCycle != null && !isDemo && storage.loadDiagnosticsSettings().sendWashReports) {
			washReporter.send(Reports.buildWashReport(finishedCycle, status, machineInfo, appDetails()))
		}

		notifications.updateCountdown(status, isDemo)

		val summary = StatusSummary.of(status)
		storage.saveStatusSnapshot(
			StatusSnapshot(
				stateName = status.state.name,
				headline = if (isDemo) "${summary.headline} · demo" else summary.headline,
				detail = summary.shortDetail,
				programName = status.programName ?: plan.nextMemory.lastKnownProgramName,
				finishAtMillis = summary.finishAt?.toEpochMilli(),
				checkedAtMillis = status.fetchedAt.toEpochMilli(),
			),
		)
		glanceSurfaces.refresh()
	}

	/** After signing out or leaving the demo: nothing should be left showing. */
	suspend fun clearAll() = mutex.withLock {
		alertScheduler.cancelAll()
		notifications.cancelCountdown()
		storage.saveStatusSnapshot(null)
		glanceSurfaces.refresh()
	}

	companion object {
		fun appDetails() = AppDetails(version = describeAppVersion(), osVersion = Build.VERSION.RELEASE ?: "unknown")
	}
}
