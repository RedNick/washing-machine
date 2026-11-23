package uk.rdfn.h.off

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.rdfn.h.off.background.StatusCheckScheduler
import uk.rdfn.h.off.connection.GlanceSurfaces
import uk.rdfn.h.off.connection.StatusFollowUp
import uk.rdfn.h.off.connection.WasherRepository
import uk.rdfn.h.off.hon.OkHttpSender
import uk.rdfn.h.off.notifications.FinishAlertScheduler
import uk.rdfn.h.off.notifications.WashNotifications
import uk.rdfn.h.off.reporting.CrashReporting
import uk.rdfn.h.off.reporting.WashReporter
import uk.rdfn.h.off.storage.AppStorage
import uk.rdfn.h.off.storage.KeystoreSessionStore
import uk.rdfn.h.off.storage.OldAppDataImport
import uk.rdfn.h.off.storage.appDataStore

/**
 * The app's shared objects, made once per process. Screens, the
 * background check, the alarm receiver, the widget and the tile all reach
 * them through [HOffApplication.container].
 */
class AppContainer(application: Application) {
	/** For work that must finish even if the screen that started it closes. */
	val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	val storage = AppStorage(application.appDataStore)
	val notifications = WashNotifications(application)
	val crashReporting = CrashReporting(application)
	val washReporter = WashReporter()
	val oldAppDataImport = OldAppDataImport(application, storage)
	private val statusFollowUp = StatusFollowUp(
		storage = storage,
		notifications = notifications,
		alertScheduler = FinishAlertScheduler(application, notifications),
		washReporter = washReporter,
		glanceSurfaces = GlanceSurfaces(application),
	)
	val washerRepository = WasherRepository(
		sessionStore = KeystoreSessionStore(storage),
		httpSender = OkHttpSender(),
		storage = storage,
		followUp = statusFollowUp,
		statusCheckScheduler = StatusCheckScheduler(application),
	)
}

class HOffApplication : Application() {
	lateinit var container: AppContainer
		private set

	override fun onCreate() {
		super.onCreate()
		container = AppContainer(this)
		container.notifications.createChannels()
		container.applicationScope.launch {
			// Before anything reads the settings, so the old app's choices are in place.
			container.oldAppDataImport.importOnce()
			container.crashReporting.setEnabled(container.storage.loadDiagnosticsSettings().sendCrashReports)
		}
	}
}
