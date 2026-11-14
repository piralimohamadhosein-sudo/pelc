package ir.pelc.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import ir.pelc.core.model.FillMode
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.shaping.PlacedGlyph
import ir.pelc.core.shaping.PlacedText
import ir.pelc.core.shaping.TextLayouter
import kotlin.math.roundToInt

data class RenderOptions(
    /** Draw the document background color behind the content. */
    val paintBackground: Boolean = true,
    /** Show only layers whose id matches (used for PSD per-layer rendering). */
    val layerFilter: (layerId: String) -> Boolean = { true },
)

/**
 * Renders a [PelcDocument] onto any android Canvas (bitmap, PdfDocument page, ...)
 * at an arbitrary scale, using the shared [TextLayouter] so all exporters and
 * the on-screen preview stay pixel-identical in geometry.
 */
class PelcRenderer(
    private val layouter: TextLayouter,
    private val outlineCache: GlyphOutlineCache = GlyphOutlineCache(),
) {

    suspend fun renderBitmap(
        document: PelcDocument,
        targetWidthPx: Int,
        options: RenderOptions = RenderOptions(),
        precomputed: List<PlacedText>? = null,
    ): Bitmap {
        val scale = targetWidthPx / document.width.toFloat()
        val heightPx = (document.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderToCanvas(canvas, document, targetWidthPx, options, precomputed)
        return bitmap
    }

    suspend fun renderToCanvas(
        canvas: Canvas,
        document: PelcDocument,
        targetWidthPx: Int,
        options: RenderOptions = RenderOptions(),
        precomputed: List<PlacedText>? = null,
    ) {
        val scale = targetWidthPx / document.width.toFloat()

        if (options.paintBackground && !document.background.transparent) {
            canvas.drawColor(document.background.color.toInt())
        }

        val placed = precomputed ?: layouter.layout(document)
        for (text in placed) {
            drawPlacedText(canvas, text, scale, options)
        }
    }

    private fun drawPlacedText(canvas: Canvas, text: PlacedText, scale: Float, options: RenderOptions) {
        if (!text.visible || !options.layerFilter(text.layerId)) return

        canvas.save()
        // Layer transform: translate -> rotate -> scale, around the layer origin.
        val t = text.layerTransform
        canvas.translate(t.tx * scale, t.ty * scale)
        canvas.rotate(t.rotationDeg)
        canvas.scale(t.scaleX, t.scaleY)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (text.fillMode == FillMode.OUTLINE) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = text.strokeWidth * scale
        }
        val shader = if (text.fillMode == FillMode.GOLD && text.gold != null) {
            val height = (text.bounds.maxY - text.bounds.minY).coerceAtLeast(1f) * scale
            LinearGradient(
                0f, 0f, 0f, height,
                text.gold.innerColor.toInt(),
                text.gold.outerColor.toInt(),
                Shader.TileMode.CLAMP,
            )
        } else {
            null
        }

        for (glyph in text.glyphs) {
            val override = glyph.override
            val color = override?.color?.toInt() ?: text.color.toInt()
            fill.color = if (text.fillMode == FillMode.GOLD && shader != null) {
                fill.shader = shader
                0xFFFFFFFF.toInt()
            } else {
                color
            }

            text.shadow?.let { shadow ->
                fill.setShadowLayer(
                    shadow.radius * scale,
                    shadow.dx * scale,
                    shadow.dy * scale,
                    shadow.color.toInt(),
                )
            } ?: fill.clearShadowLayer()

            canvas.save()
            canvas.translate(glyph.x * scale, glyph.y * scale)
            override?.let {
                canvas.translate(it.dx * scale, it.dy * scale)
                canvas.rotate(it.rotateDeg)
                canvas.scale(it.scale, it.scale)
            }
            canvas.scale(glyph.scale, glyph.scale)

            val path = outlineCache.get(glyph.fontId, glyph.glyphId, glyph.outline)
            canvas.drawPath(path, fill)
            if (text.fillMode != FillMode.OUTLINE && text.strokeWidth > 0f) {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = text.strokeWidth * scale
                    this.color = text.strokeColor.toInt()
                }
                canvas.drawPath(path, stroke)
            }
            canvas.restore()
        }
        canvas.restore()
    }
}
