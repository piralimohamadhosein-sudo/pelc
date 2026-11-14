package ir.pelc.core.export

import android.graphics.Bitmap
import ir.pelc.core.export.dxf.DxfExporter
import ir.pelc.core.export.pdf.PdfExporter
import ir.pelc.core.export.project.PelcProjectWriter
import ir.pelc.core.export.psd.PsdComposer
import ir.pelc.core.export.psd.PsdWriter
import ir.pelc.core.export.raster.JpgExporter
import ir.pelc.core.export.raster.PngExporter
import ir.pelc.core.export.svg.SvgExporter
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.render.PelcRenderer
import ir.pelc.core.shaping.FontResolver
import ir.pelc.core.shaping.TextLayouter
import java.io.File
import java.io.FileOutputStream

/**
 * Single entry point used by the export UI and the background worker.
 *
 * Product rule: the user picks the format when finishing; the editable
 * project is ALWAYS also saved as .pelc next to the exported file.
 */
class ExportOrchestrator(
    private val renderer: PelcRenderer,
    private val layouter: TextLayouter,
    private val fontResolver: FontResolver,
) {

    suspend fun export(
        document: PelcDocument,
        format: ExportFormat,
        targetDir: File,
        baseName: String,
        options: ExportOptions = ExportOptions(),
    ): List<File> {
        targetDir.mkdirs()
        val safeBase = baseName.ifBlank { "pelc" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(80)
        val files = ArrayList<File>()

        when (format) {
            ExportFormat.PNG -> {
                val file = File(targetDir, "$safeBase.png")
                FileOutputStream(file).use { PngExporter(renderer).export(document, options, it) }
                files += file
            }
            ExportFormat.JPG -> {
                val file = File(targetDir, "$safeBase.jpg")
                FileOutputStream(file).use { JpgExporter(renderer).export(document, options, it) }
                files += file
            }
            ExportFormat.SVG -> {
                val file = File(targetDir, "$safeBase.svg")
                FileOutputStream(file).use { SvgExporter(layouter).export(document, it) }
                files += file
            }
            ExportFormat.PDF -> {
                val file = File(targetDir, "$safeBase.pdf")
                FileOutputStream(file).use { PdfExporter(renderer).export(document, it) }
                files += file
            }
            ExportFormat.DXF -> {
                val file = File(targetDir, "$safeBase.dxf")
                FileOutputStream(file).use { DxfExporter(layouter).export(document, it) }
                files += file
            }
            ExportFormat.PSD -> {
                val file = File(targetDir, "$safeBase.psd")
                val composer = PsdComposer(renderer)
                val psd = composer.compose(document, options.scale)
                FileOutputStream(file).use { PsdWriter().write(psd, it) }
                files += file
            }
            ExportFormat.PELC -> {
                // Handled below together with the thumbnail.
            }
        }

        // The editable project is always saved as .pelc next to the export
        // (product rule) — and is the only artifact when PELC itself is chosen.
        val pelcFile = File(targetDir, "$safeBase.pelc")
        FileOutputStream(pelcFile).use {
            PelcProjectWriter(fontResolver).write(document, it)
        }
        files += pelcFile
        return files
    }

    /** Renders a small preview used as the .pelc thumbnail. */
    suspend fun thumbnail(document: PelcDocument, widthPx: Int = 512): Bitmap =
        renderer.renderBitmap(document, widthPx)
}
