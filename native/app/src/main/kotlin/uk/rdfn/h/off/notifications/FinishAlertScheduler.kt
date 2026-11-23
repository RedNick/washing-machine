package uk.rdfn.h.off.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uk.rdfn.h.off.HOffApplication
import uk.rdfn.h.off.alerts.FinishAlertStep

/**
 * Carries out the steps [uk.rdfn.h.off.alerts.FinishAlertPlanner] decides
 * on. A "wash finished" alert for later is an alarm that wakes
 * [FinishAlertReceiver] at the expected finish time.
 *
 * The alarm is inexact (Android may deliver it a few minutes late to save
 * battery), so the app doesn't need the special "alarms and reminders"
 * permission. A wash's end time is only an estimate anyway.
 */
class FinishAlertScheduler(private val context: Context, private val notifications: WashNotifications) {
	private val alarmManager = context.getSystemService(AlarmManager::class.java)

	/** Returns the id to remember for a newly scheduled alert, if one was scheduled. */
	fun carryOut(steps: List<FinishAlertStep>): String? {
		var scheduledId: String? = null
		for (step in steps) {
			when (step) {
				is FinishAlertStep.CancelScheduled -> alarmManager.cancel(alarmIntent(programName = null))
				is FinishAlertStep.Schedule -> {
					alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, step.fireAtMillis, alarmIntent(step.programName))
					scheduledId = ALARM_ID
				}
				is FinishAlertStep.ShowNow -> when (step.reason) {
					FinishAlertStep.Reason.FINISHED -> notifications.showFinished(step.programName)
					FinishAlertStep.Reason.ERROR -> notifications.showProblem(step.errorCode)
				}
			}
		}
		return scheduledId
	}

	fun cancelAll() {
		alarmManager.cancel(alarmIntent(programName = null))
	}

	private fun alarmIntent(programName: String?): PendingIntent = PendingIntent.getBroadcast(
		context,
		0,
		Intent(context, FinishAlertReceiver::class.java).putExtra(EXTRA_PROGRAM_NAME, programName),
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
	)

	companion object {
		/** There is only ever one finish alert, so it has a fixed id. */
		const val ALARM_ID = "finish-alarm"
		internal const val EXTRA_PROGRAM_NAME = "programName"
	}
}

/** Shows the "Washing is done" notification when the finish alarm goes off. */
class FinishAlertReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val container = (context.applicationContext as HOffApplication).container
		container.notifications.showFinished(intent.getStringExtra(FinishAlertScheduler.EXTRA_PROGRAM_NAME))
		container.notifications.cancelCountdown()
	}
}
