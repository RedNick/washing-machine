package uk.rdfn.h.off.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uk.rdfn.h.off.MainActivity
import uk.rdfn.h.off.R
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.StatusSummary
import uk.rdfn.h.off.washer.WasherStatus

/**
 * The app's two kinds of notification:
 *
 * - "Wash finished": a loud one when the wash is done or the machine
 *   reports a problem. Same channel id as the old app, so your sound and
 *   importance choices carry over.
 * - "Wash progress": a quiet, ongoing one with a live countdown while a
 *   wash runs.
 */
class WashNotifications(private val context: Context) {
	private val notificationManager = NotificationManagerCompat.from(context)

	/** Safe to call every start: Android ignores channels that already exist. */
	fun createChannels() {
		val manager = context.getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(
			NotificationChannel(WASH_FINISHED_CHANNEL_ID, "Wash finished", NotificationManager.IMPORTANCE_HIGH).apply {
				description = "Tells you when the washing machine is done"
			},
		)
		manager.createNotificationChannel(
			NotificationChannel(WASH_PROGRESS_CHANNEL_ID, "Wash progress", NotificationManager.IMPORTANCE_LOW).apply {
				description = "A countdown while the washing machine is running"
				setShowBadge(false)
			},
		)
	}

	fun canNotify(): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
			ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

	fun showFinished(programName: String?) = show(
		FINISHED_NOTIFICATION_ID,
		baseNotification(WASH_FINISHED_CHANNEL_ID)
			.setContentTitle("Washing is done")
			.setContentText(if (programName != null) "$programName has finished. Time to unload." else "Time to unload the machine.")
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setCategory(NotificationCompat.CATEGORY_REMINDER)
			.setAutoCancel(true),
	)

	fun showProblem(errorCode: String?) = show(
		FINISHED_NOTIFICATION_ID,
		baseNotification(WASH_FINISHED_CHANNEL_ID)
			.setContentTitle("Washing machine problem")
			.setContentText(if (errorCode != null) "The machine reports error $errorCode." else "The machine needs attention.")
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setCategory(NotificationCompat.CATEGORY_ERROR)
			.setAutoCancel(true),
	)

	/**
	 * Shows, moves or removes the live countdown to match the latest status.
	 * Android counts the clock down by itself between status checks.
	 */
	fun updateCountdown(status: WasherStatus, isDemo: Boolean) {
		val isCounting = status.state == MachineState.RUNNING && status.remainingMinutes > 0
		val isOnHold = status.state == MachineState.PAUSED || status.state == MachineState.SCHEDULED
		// The demo runs a minute every second, which a real-time countdown can't show.
		if (isDemo || (!isCounting && !isOnHold)) {
			notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
			return
		}
		val builder = baseNotification(WASH_PROGRESS_CHANNEL_ID)
			.setContentTitle(status.programName ?: "Wash")
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setSilent(true)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
		val summary = StatusSummary.of(status)
		val finishAt = summary.finishAt
		if (isCounting && finishAt != null) {
			builder
				.setContentText(listOfNotNull(summary.phaseText, summary.caption?.removePrefix("left · ")).joinToString(" · "))
				.setWhen(finishAt.toEpochMilli())
				.setShowWhen(true)
				.setUsesChronometer(true)
				.setChronometerCountDown(true)
				// In case no later check comes to clear it, it goes 30 minutes after the expected end.
				.setTimeoutAfter(finishAt.toEpochMilli() - System.currentTimeMillis() + 30 * 60_000L)
		} else {
			builder.setContentText(listOfNotNull(summary.timeText, summary.caption).joinToString(" "))
		}
		show(COUNTDOWN_NOTIFICATION_ID, builder)
	}

	fun cancelCountdown() = notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)

	private fun baseNotification(channelId: String): NotificationCompat.Builder {
		val openApp = PendingIntent.getActivity(
			context,
			0,
			Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)
		return NotificationCompat.Builder(context, channelId)
			.setSmallIcon(R.drawable.notification_icon)
			.setColor(ContextCompat.getColor(context, R.color.launcher_background))
			.setContentIntent(openApp)
	}

	private fun show(notificationId: Int, builder: NotificationCompat.Builder) {
		// Without permission Android drops it anyway; checking avoids a SecurityException.
		if (!canNotify()) return
		@Suppress("MissingPermission")
		notificationManager.notify(notificationId, builder.build())
	}

	companion object {
		const val WASH_FINISHED_CHANNEL_ID = "wash-finished"
		const val WASH_PROGRESS_CHANNEL_ID = "wash-progress"
		private const val FINISHED_NOTIFICATION_ID = 1
		private const val COUNTDOWN_NOTIFICATION_ID = 2
	}
}
