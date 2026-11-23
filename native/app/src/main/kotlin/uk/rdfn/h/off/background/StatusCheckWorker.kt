package uk.rdfn.h.off.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import uk.rdfn.h.off.HOffApplication

/**
 * Checks the machine every 15 minutes (the shortest Android allows) while
 * the app is closed, so the "wash finished" alert still moves if the wash
 * runs long, and finished washes still reach the wash log.
 */
class StatusCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
	override suspend fun doWork(): Result {
		val repository = (applicationContext as HOffApplication).container.washerRepository
		return try {
			if (!repository.checkInBackground()) {
				StatusCheckScheduler(applicationContext).stop()
			}
			Result.success()
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: Exception) {
			// h.on or the network had a bad moment; the next check in 15 minutes tries again.
			Log.w(TAG, "Background status check failed", exception)
			Result.success()
		}
	}

	private companion object {
		const val TAG = "StatusCheckWorker"
	}
}

/** Starts and stops [StatusCheckWorker]. */
class StatusCheckScheduler(private val context: Context) {
	fun start() {
		val request = PeriodicWorkRequestBuilder<StatusCheckWorker>(15, TimeUnit.MINUTES)
			.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
			.build()
		// KEEP: starting again after every launch mustn't reset the 15-minute clock.
		WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
	}

	fun stop() {
		WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
	}

	private companion object {
		const val WORK_NAME = "status-check"
	}
}
