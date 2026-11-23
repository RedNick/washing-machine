package uk.rdfn.h.off.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import uk.rdfn.h.off.describeAppVersion
import uk.rdfn.h.off.ui.Spacing
import uk.rdfn.h.off.ui.components.WarningBanner

/** A full-screen page with its content in the middle, for the start-up screens. */
@Composable
private fun CentredPage(content: @Composable () -> Unit) {
	Surface(Modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.safeDrawingPadding()
				.verticalScroll(rememberScrollState())
				.padding(Spacing.large),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			content()
		}
	}
}

/** Shown while the saved sign-in is restored and the machine is found. */
@Composable
fun StartingScreen() = CentredPage {
	CircularProgressIndicator()
	Text(
		"Connecting to your washing machine…",
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = Spacing.medium),
	)
}

/** Signed in, but h.on can't be reached right now. */
@Composable
fun UnreachableScreen(message: String, onTryAgain: () -> Unit, onSignOut: () -> Unit) = CentredPage {
	Text("Can't reach h.on", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
	Text(
		message,
		textAlign = TextAlign.Center,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(vertical = Spacing.medium),
	)
	Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
	OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = Spacing.small)) { Text("Sign out") }
}

/**
 * The welcome screen. Signing in happens on Haier's own page in a Chrome
 * Custom Tab, so the password never passes through h.off.
 */
@Composable
fun SignInScreen(message: String?, onSignIn: () -> Unit, onTryDemo: () -> Unit) = CentredPage {
	Text("🧺", fontSize = 64.sp, modifier = Modifier.clearAndSetSemantics { })
	Text(
		"h.off",
		style = MaterialTheme.typography.displaySmall,
		fontWeight = FontWeight.ExtraBold,
		modifier = Modifier.padding(top = Spacing.small),
	)
	Text(
		"A quicker way to run your h.on washing machine.",
		textAlign = TextAlign.Center,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.large),
	)
	message?.let { WarningBanner(it) }
	Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in with h.on") }
	OutlinedButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth().padding(top = Spacing.small)) {
		Text("Try the demo machine")
	}
	Text(
		"You sign in on Haier's own page in Chrome, where you can also use your Google account. " +
			"Your password never passes through this app, and your sign-in is kept encrypted on the phone.",
		style = MaterialTheme.typography.bodySmall,
		textAlign = TextAlign.Center,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = Spacing.large),
	)
	Text(
		describeAppVersion(),
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = Spacing.medium),
	)
}
