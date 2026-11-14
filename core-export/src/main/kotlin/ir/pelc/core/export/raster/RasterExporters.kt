package ir.pelc.core.export.raster

import android.graphics.Bitmap
import ir.pelc.core.export.ExportOptions
import ir.pelc.core.model.Background
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.render.PelcRenderer
import ir.pelc.core.render.RenderOptions
import kotlin.math.roundToInt

/** PNG export with optional transparency, up to 4x and beyond. */
class PngExporter(private val renderer: PelcRenderer) {

    suspend fun export(document: PelcDocument, options: ExportOptions, out: java.io.OutputStream) {
        val widthPx = (document.width * options.scale).roundToInt().coerceAtLeast(1)
        val renderOptions = RenderOptions(paintBackground = !options.transparentPng)
        val bitmap = renderer.renderBitmap(document, widthPx, renderOptions)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
    }
}

/** JPG export. JPG has no alpha, so the canvas is composited over an opaque color. */
class JpgExporter(private val renderer: PelcRenderer) {

    suspend fun export(document: PelcDocument, options: ExportOptions, out: java.io.OutputStream) {
        val opaque = if (document.background.transparent) {
            document.copy(background = Background(color = options.backgroundColor, transparent = false))
        } else {
            document
        }
        val widthPx = (opaque.width * options.scale).roundToInt().coerceAtLeast(1)
        val bitmap = renderer.renderBitmap(opaque, widthPx, RenderOptions(paintBackground = true))
        val quality = options.jpgQuality.coerceIn(60, 100)
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
    }
}
