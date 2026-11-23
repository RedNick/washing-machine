package uk.rdfn.h.off.reporting

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * "Send a report": saves the report as a JSON file and opens the share
 * sheet, so you choose where it goes (email, Drive, a chat...).
 */
object ReportSharing {
	private val readableJson = Json { prettyPrint = true }

	suspend fun share(context: Context, report: JsonElement) {
		val file = withContext(Dispatchers.IO) {
			val folder = File(context.cacheDir, "reports").apply { mkdirs() }
			// Only the newest report is kept.
			folder.listFiles()?.forEach { it.delete() }
			val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
			File(folder, "h-off-report-$stamp.json").apply {
				writeText(readableJson.encodeToString(JsonElement.serializer(), report))
			}
		}
		val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
		val sendIntent = Intent(Intent.ACTION_SEND)
			.setType("application/json")
			.putExtra(Intent.EXTRA_STREAM, fileUri)
			.putExtra(Intent.EXTRA_SUBJECT, "h.off report")
			.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		context.startActivity(Intent.createChooser(sendIntent, "Send h.off report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
	}
}
