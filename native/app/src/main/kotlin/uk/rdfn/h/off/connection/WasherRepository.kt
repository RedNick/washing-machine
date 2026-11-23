package uk.rdfn.h.off.connection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.rdfn.h.off.background.StatusCheckScheduler
import uk.rdfn.h.off.hon.HonAuthException
import uk.rdfn.h.off.hon.HonCloudWasher
import uk.rdfn.h.off.hon.HonConstants
import uk.rdfn.h.off.hon.HonLogin
import uk.rdfn.h.off.hon.HonSession
import uk.rdfn.h.off.hon.HttpSender
import uk.rdfn.h.off.hon.NoWasherFoundException
import uk.rdfn.h.off.hon.SessionStore
import uk.rdfn.h.off.storage.AppStorage
import uk.rdfn.h.off.washer.DemoWasher
import uk.rdfn.h.off.washer.MachineInfo
import uk.rdfn.h.off.washer.WasherService
import uk.rdfn.h.off.washer.WasherStatus

/** Where the app is with h.on. Decides which screen is shown. */
sealed interface ConnectionState {
	/** Restoring the saved sign-in and finding the machine. */
	data object Starting : ConnectionState

	data class SignedOut(val message: String? = null) : ConnectionState

	/** Signed in, but h.on couldn't be reached. "Try again" or "Sign out". */
	data class Unreachable(val message: String) : ConnectionState

	data class Connected(val washer: WasherService, val machineInfo: MachineInfo) : ConnectionState
}

/** The latest status, plus what went wrong fetching it, if anything. */
data class StatusState(
	val status: WasherStatus? = null,
	val errorMessage: String? = null,
	val isRefreshing: Boolean = false,
)

/**
 * The one place that talks to the washer for the whole app: the screens,
 * the background check, the widget and the tile all go through it.
 * Ported from src/washer/WasherContext.tsx.
 */
class WasherRepository(
	private val sessionStore: SessionStore,
	private val httpSender: HttpSender,
	private val storage: AppStorage,
	private val followUp: StatusFollowUp,
	private val statusCheckScheduler: StatusCheckScheduler,
) {
	private val connectionFlow = MutableStateFlow<ConnectionState>(ConnectionState.Starting)
	val connection: StateFlow<ConnectionState> = connectionFlow.asStateFlow()

	private val statusFlow = MutableStateFlow(StatusState())
	val status: StateFlow<StatusState> = statusFlow.asStateFlow()

	private val connectMutex = Mutex()
	private var session: HonSession? = null

	/**
	 * Restores the saved sign-in and finds the machine. Does nothing once
	 * connected or signed out, so it is safe to call on every launch;
	 * pass [retry] for the "Try again" button.
	 */
	suspend fun connect(retry: Boolean = false) = connectMutex.withLock {
		val current = connectionFlow.value
		val shouldConnect = current is ConnectionState.Starting || (retry && current is ConnectionState.Unreachable)
		if (!shouldConnect) return@withLock
		connectionFlow.value = ConnectionState.Starting
		connectionFlow.value = try {
			val restored = HonSession.restore(sessionStore, httpSender)
			if (restored == null) ConnectionState.SignedOut() else connectWith(restored)
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: HonAuthException) {
			sessionStore.clear()
			ConnectionState.SignedOut(SIGN_IN_EXPIRED)
		} catch (exception: Exception) {
			ConnectionState.Unreachable(describeError(exception))
		}
	}

	/** Called with the hon://… link the sign-in page sends you back to. */
	suspend fun signInWithRedirect(redirectUrl: String) = connectMutex.withLock {
		val tokens = HonLogin.parseLoginRedirect(redirectUrl)
		if (tokens == null) {
			connectionFlow.value = ConnectionState.SignedOut("Sign-in didn't finish. Please try again.")
			return@withLock
		}
		connectionFlow.value = ConnectionState.Starting
		connectionFlow.value = try {
			connectWith(HonSession.fromLogin(tokens, sessionStore, httpSender))
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: NoWasherFoundException) {
			ConnectionState.SignedOut("Signed in, but there is no washing machine on this h.on account.")
		} catch (exception: Exception) {
			ConnectionState.SignedOut(describeError(exception))
		}
	}

	suspend fun startDemo() = connectMutex.withLock {
		val demo = DemoWasher()
		connectionFlow.value = ConnectionState.Connected(demo, demo.getMachineInfo())
	}

	/** Signs out of h.on, or leaves the demo. */
	suspend fun signOut() = connectMutex.withLock {
		val wasDemo = (connectionFlow.value as? ConnectionState.Connected)?.washer?.isDemo == true
		statusCheckScheduler.stop()
		followUp.clearAll()
		if (wasDemo) {
			storage.forgetDemo()
		} else {
			runCatching { session?.signOut() }
			sessionStore.clear()
			storage.forgetAlertMemory()
		}
		session = null
		statusFlow.value = StatusState()
		connectionFlow.value = ConnectionState.SignedOut()
	}

	/** Fetches the status for the screens, then does the alert, log and widget updates. */
	suspend fun refreshStatus() {
		val connected = connectionFlow.value as? ConnectionState.Connected ?: return
		statusFlow.update { it.copy(isRefreshing = true) }
		try {
			val status = connected.washer.getStatus()
			// Ignore an answer that arrives after you signed out.
			if (connectionFlow.value !== connected) return
			statusFlow.value = StatusState(status = status)
			followUp.afterStatus(status, connected.machineInfo, connected.washer.isDemo)
		} catch (exception: CancellationException) {
			statusFlow.update { it.copy(isRefreshing = false) }
			throw exception
		} catch (exception: HonAuthException) {
			expireSignIn()
		} catch (exception: Exception) {
			statusFlow.update { it.copy(errorMessage = describeError(exception), isRefreshing = false) }
		}
	}

	/**
	 * Keeps the status fresh while the app is on screen: every 30 seconds
	 * during a wash, every minute otherwise, and every 3 seconds for the
	 * demo (where a minute passes each second). Runs until cancelled.
	 */
	suspend fun keepStatusFresh() {
		while (true) {
			refreshStatus()
			val connected = connectionFlow.value as? ConnectionState.Connected ?: return
			val isMidCycle = statusFlow.value.status?.state?.isMidCycle == true
			delay(
				when {
					connected.washer.isDemo -> DEMO_POLL_MILLISECONDS
					isMidCycle -> HonConstants.STATUS_POLL_WHILE_RUNNING_MILLISECONDS
					else -> HonConstants.STATUS_POLL_WHILE_IDLE_MILLISECONDS
				},
			)
		}
	}

	/**
	 * Sends a command (start, pause, resume or stop), then fetches the new
	 * status. Throws [uk.rdfn.h.off.washer.WasherCommandException] with a
	 * readable message if the machine refuses.
	 */
	suspend fun sendCommand(command: suspend (WasherService) -> Unit) {
		val connected = connectionFlow.value as? ConnectionState.Connected ?: return
		try {
			command(connected.washer)
		} catch (exception: HonAuthException) {
			expireSignIn()
			throw exception
		}
		refreshStatus()
	}

	/**
	 * The 15-minute background check. Returns false when there's nothing to
	 * check any more (signed out), so the check can stop itself.
	 */
	suspend fun checkInBackground(): Boolean {
		connect()
		val connected = connectionFlow.value as? ConnectionState.Connected ?: return connectionFlow.value !is ConnectionState.SignedOut
		// The demo only runs while the app is open.
		if (connected.washer.isDemo) return true
		return try {
			val washer = connected.washer
			// Skips the large program catalogue; the alert remembers the program's name.
			val status = (washer as? HonCloudWasher)?.getStatusWithoutProgramName() ?: washer.getStatus()
			followUp.afterStatus(status, connected.machineInfo, isDemo = false)
			true
		} catch (exception: HonAuthException) {
			expireSignIn()
			false
		}
	}

	private suspend fun connectWith(newSession: HonSession): ConnectionState.Connected {
		val washer = HonCloudWasher.connect(newSession, httpSender)
		session = newSession
		statusCheckScheduler.start()
		return ConnectionState.Connected(washer, washer.getMachineInfo())
	}

	private suspend fun expireSignIn() {
		statusCheckScheduler.stop()
		followUp.clearAll()
		sessionStore.clear()
		session = null
		statusFlow.value = StatusState()
		connectionFlow.value = ConnectionState.SignedOut(SIGN_IN_EXPIRED)
	}

	companion object {
		private const val SIGN_IN_EXPIRED = "Your h.on sign-in expired. Please sign in again."
		private const val DEMO_POLL_MILLISECONDS = 3_000L

		/** h.on client errors already carry a readable message, e.g. "Couldn't reach h.on…". */
		fun describeError(exception: Exception): String = exception.message ?: "Something went wrong."
	}
}
