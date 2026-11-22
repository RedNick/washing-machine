package uk.rdfn.h.off

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * The app's only activity. Phase 0 shows just the name and version; the
 * Now, Programs and History screens arrive in later phases.
 */
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			HOffTheme {
				PlaceholderScreen()
			}
		}
	}
}

/** The h.off blue, used where the phone can't supply its own colours. */
private val hOffBlue = Color(0xFF1663D6)

/**
 * Material You colours taken from the phone's wallpaper on Android 12 and
 * later. Android 10 and 11 don't have them, so those get the h.off blue.
 */
@Composable
fun HOffTheme(content: @Composable () -> Unit) {
	val context = LocalContext.current
	val darkTheme = isSystemInDarkTheme()
	val colourScheme = when {
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		darkTheme -> darkColorScheme(primary = hOffBlue)
		else -> lightColorScheme(primary = hOffBlue)
	}
	MaterialTheme(colorScheme = colourScheme, content = content)
}

@Composable
private fun PlaceholderScreen() {
	Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text("h.off", style = MaterialTheme.typography.displayMedium)
			Text("Native version, coming together", style = MaterialTheme.typography.bodyLarge)
			// Standing rule: every build shows its version on the main screen.
			Text(
				describeAppVersion(),
				style = MaterialTheme.typography.labelMedium,
				modifier = Modifier.padding(top = 24.dp),
			)
		}
	}
}
