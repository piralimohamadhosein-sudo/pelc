package ir.pelc.feature.export

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ir.pelc.core.export.ExportFormat
import ir.pelc.core.export.ExportOptions
import ir.pelc.core.export.ExportOrchestrator
import ir.pelc.core.model.PelcJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs the chosen export off the UI thread. With Hilt + WorkManager, all
 * engines (renderer, layouter, PSD writer, ...) are injected.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: ExportOrchestrator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val documentJson = inputData.getString(KEY_DOCUMENT) ?: return Result.failure()
            val document = PelcJson.decode(documentJson)
            val format = ExportFormat.parse(inputData.getString(KEY_FORMAT).orEmpty()) ?: ExportFormat.PNG
            val options = ExportOptions(
                scale = inputData.getFloat(KEY_SCALE, 1f),
                jpgQuality = inputData.getInt(KEY_QUALITY, 92),
                transparentPng = inputData.getBoolean(KEY_TRANSPARENT, false),
                backgroundColor = inputData.getLong(KEY_BACKGROUND, 0xFFFFFFFFL),
            )

            runCatching { setForeground(foregroundInfo()) }

            val dir = File(
                applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir,
                "exports",
            )
            val base = "${document.title.ifBlank { "pelc" }}-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

            val files = orchestrator.export(document, format, dir, base, options)
            Result.success(
                androidx.work.workDataOf(KEY_OUTPUT_FILES to files.joinToString("\n") { it.absolutePath }),
            )
        } catch (t: Throwable) {
            Result.failure(
                androidx.work.workDataOf(KEY_ERROR to (t.message ?: "خطای ناشناخته")),
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    private fun foregroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(ir.pelc.feature.export.R.string.export_running))
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_DOCUMENT = "document"
        const val KEY_FORMAT = "format"
        const val KEY_SCALE = "scale"
        const val KEY_QUALITY = "quality"
        const val KEY_TRANSPARENT = "transparent"
        const val KEY_BACKGROUND = "background"
        const val KEY_OUTPUT_FILES = "output_files"
        const val KEY_ERROR = "error"
        const val CHANNEL_ID = "pelc_export"
        const val NOTIFICATION_ID = 42
    }
}
