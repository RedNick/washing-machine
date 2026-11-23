package uk.rdfn.h.off.connection

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import androidx.glance.appwidget.updateAll
import uk.rdfn.h.off.tile.WasherTileService
import uk.rdfn.h.off.widget.WasherWidget

/**
 * The places outside the app that show the washer's status at a glance:
 * the home-screen widget and the Quick Settings tile. Both read the saved
 * [uk.rdfn.h.off.storage.StatusSnapshot]; this just tells them to redraw.
 */
class GlanceSurfaces(private val context: Context) {
	suspend fun refresh() {
		runCatching { WasherWidget().updateAll(context) }
			.onFailure { Log.w(TAG, "Couldn't update the widget", it) }
		runCatching { TileService.requestListeningState(context, ComponentName(context, WasherTileService::class.java)) }
			.onFailure { Log.w(TAG, "Couldn't update the Quick Settings tile", it) }
	}

	private companion object {
		const val TAG = "GlanceSurfaces"
	}
}
