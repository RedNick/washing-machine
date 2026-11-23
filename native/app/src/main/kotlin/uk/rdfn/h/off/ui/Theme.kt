package uk.rdfn.h.off.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The h.off blue, used where the phone can't supply its own colours. */
val hOffBlue = Color(0xFF1663D6)

/** Space between things, so every screen lines up the same way. */
object Spacing {
	val small = 8.dp
	val medium = 16.dp
	val large = 24.dp
}

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
		darkTheme -> darkColorScheme(primary = Color(0xFF9DBEFF))
		else -> lightColorScheme(primary = hOffBlue)
	}
	MaterialTheme(colorScheme = colourScheme, content = content)
}

/** Green for good news ("Ready to unload."), readable in light and dark mode. */
val successColour: Color
	@Composable @ReadOnlyComposable
	get() = if (isSystemInDarkTheme()) Color(0xFF81C995) else Color(0xFF1E7E34)
