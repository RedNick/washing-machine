package uk.rdfn.h.off.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.time.Instant
import kotlinx.coroutines.flow.first
import uk.rdfn.h.off.HOffApplication
import uk.rdfn.h.off.MainActivity
import uk.rdfn.h.off.storage.StatusSnapshot
import uk.rdfn.h.off.washer.Formatters

/**
 * The home-screen widget: what the washer is doing and when it'll be done.
 * It shows the last status the app or the background check saw, and is
 * redrawn after every check. Tap it to open h.off.
 */
class WasherWidget : GlanceAppWidget() {
	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val snapshot = (context.applicationContext as HOffApplication).container.storage.statusSnapshot.first()
		provideContent {
			GlanceTheme {
				WidgetContent(snapshot)
			}
		}
	}
}

@Composable
private fun WidgetContent(snapshot: StatusSnapshot?) {
	Column(
		modifier = GlanceModifier
			.fillMaxSize()
			.background(GlanceTheme.colors.widgetBackground)
			.cornerRadius(16.dp)
			.padding(12.dp)
			.clickable(actionStartActivity<MainActivity>()),
	) {
		if (snapshot == null) {
			Text("h.off", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface))
			Text("Open the app to connect", style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
			return@Column
		}
		Text(
			snapshot.headline,
			style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
			maxLines = 1,
		)
		snapshot.programName?.let {
			Text(it, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface), maxLines = 1)
		}
		// A "42 min left" would go stale between checks, so a running wash shows its finish time.
		val detail = snapshot.finishAtMillis?.let { "Done around ${Formatters.formatClockTime(Instant.ofEpochMilli(it))}" }
			?: snapshot.detail
		if (detail.isNotEmpty()) {
			Text(detail, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.primary), maxLines = 2)
		}
	}
}

/** Lets Android find [WasherWidget]. Declared in the manifest. */
class WasherWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = WasherWidget()
}
