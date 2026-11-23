package uk.rdfn.h.off.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.rdfn.h.off.HOffApplication
import uk.rdfn.h.off.MainActivity
import uk.rdfn.h.off.washer.Formatters
import uk.rdfn.h.off.washer.MachineState

/**
 * A Quick Settings tile: pull down the notification shade to see what the
 * washer is doing. Lit up while a wash is running. Tap it to open h.off.
 */
class WasherTileService : TileService() {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	override fun onStartListening() {
		super.onStartListening()
		scope.launch {
			val snapshot = (applicationContext as HOffApplication).container.storage.statusSnapshot.first()
			val tile = qsTile ?: return@launch
			if (snapshot == null) {
				tile.state = Tile.STATE_INACTIVE
				tile.subtitle = "Not connected"
			} else {
				val isMidCycle = MachineState.entries.firstOrNull { it.name == snapshot.stateName }?.isMidCycle == true
				tile.state = if (isMidCycle) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
				tile.subtitle = snapshot.finishAtMillis
					?.let { "Done ~${Formatters.formatClockTime(Instant.ofEpochMilli(it))}" }
					?: snapshot.headline
			}
			tile.updateTile()
		}
	}

	override fun onClick() {
		super.onClick()
		val openApp = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startActivityAndCollapse(PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE))
		} else {
			@Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
			startActivityAndCollapse(openApp)
		}
	}

	override fun onDestroy() {
		scope.cancel()
		super.onDestroy()
	}
}
