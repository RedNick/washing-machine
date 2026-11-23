package uk.rdfn.h.off

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uk.rdfn.h.off.connection.ConnectionState
import uk.rdfn.h.off.hon.HonLogin
import uk.rdfn.h.off.ui.AppRoot
import uk.rdfn.h.off.ui.HOffTheme

/**
 * The app's only activity. It also receives the hon://… link that Haier's
 * sign-in page sends you back to (see the intent filter in the manifest).
 */
class MainActivity : ComponentActivity() {
	private val container: AppContainer get() = (application as HOffApplication).container

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		// Only a fresh launch: after a rotation the link has already been used.
		if (savedInstanceState == null) handleSignInRedirect(intent)

		setContent {
			HOffTheme {
				AppRoot(container, onSignIn = ::openSignInPage)
			}
		}

		// While the app is on screen: connect, then keep the status fresh.
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				val repository = container.washerRepository
				launch { repository.connect() }
				repository.connection.collectLatest { connection ->
					if (connection is ConnectionState.Connected) repository.keepStatusFresh()
				}
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleSignInRedirect(intent)
	}

	/** Haier's sign-in page, in a Chrome Custom Tab. */
	private fun openSignInPage() {
		CustomTabsIntent.Builder()
			.setShowTitle(true)
			.build()
			.launchUrl(this, HonLogin.buildLoginPageUrl().toUri())
	}

	private fun handleSignInRedirect(intent: Intent?) {
		val link = intent?.dataString ?: return
		if (!HonLogin.isLoginRedirect(link)) return
		// Don't use the same link again if the activity is re-created.
		intent.data = null
		// The application scope, so leaving the screen doesn't cancel the sign-in half way.
		container.applicationScope.launch { container.washerRepository.signInWithRedirect(link) }
	}
}
