package ir.pelc.core.export.psd

import android.graphics.Bitmap
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.render.PelcRenderer
import ir.pelc.core.render.RenderOptions
import kotlin.math.roundToInt

/**
 * Turns a [PelcDocument] into [PsdFileData] by rendering the composite and
 * every visible layer separately (true open layers in Photoshop).
 */
class PsdComposer(private val renderer: PelcRenderer) {

    suspend fun compose(document: PelcDocument, scale: Float = 1f): PsdFileData {
        val widthPx = (document.width * scale).roundToInt().coerceAtLeast(1)

        val composite = rasterize(document, widthPx) { true }
        val layers = document.layers.asSequence()
            .filter { it.visible }
            .mapNotNull { layer ->
                val raster = rasterize(document, widthPx) { it == layer.id }
                if (raster.width == 0 || raster.height == 0) {
                    return@mapNotNull null
                }
                PsdLayerData(
                    name = layer.name.ifBlank { "Layer" },
                    raster = raster,
                    opacity = (layer.opacity * 255).roundToInt().coerceIn(0, 255),
                    visible = layer.visible,
                )
            }
            .toList()

        return PsdFileData(widthPx, composite.height, composite, layers)
    }

    private suspend fun rasterize(document: PelcDocument, widthPx: Int, filter: (String) -> Boolean): PsdRaster {
        val options = RenderOptions(paintBackground = false, layerFilter = filter)
        val bitmap = renderer.renderBitmap(document, widthPx, options)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()

        val planeSize = w * h
        val r = ByteArray(planeSize)
        val g = ByteArray(planeSize)
        val b = ByteArray(planeSize)
        val a = ByteArray(planeSize)
        for (i in 0 until planeSize) {
            val argb = pixels[i]
            r[i] = ((argb shr 16) and 0xFF).toByte()
            g[i] = ((argb shr 8) and 0xFF).toByte()
            b[i] = (argb and 0xFF).toByte()
            a[i] = ((argb shr 24) and 0xFF).toByte()
        }
        return PsdRaster(w, h, r, g, b, a)
    }
}
