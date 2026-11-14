package ir.pelc.core.shaping

import ir.pelc.core.model.FillMode
import ir.pelc.core.model.GoldSpec
import ir.pelc.core.model.Layer
import ir.pelc.core.model.LayerKind
import ir.pelc.core.model.LetterOverride
import ir.pelc.core.model.OutlineOps
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.model.ShadowSpec
import ir.pelc.core.model.TextContent
import ir.pelc.core.model.Transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rough bounding box of a laid-out layer in document coordinates. */
data class BBox(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    fun contains(x: Float, y: Float, padding: Float = 0f): Boolean =
        x >= minX - padding && x <= maxX + padding && y >= minY - padding && y <= maxY + padding
}

/** One glyph placed in document-space coordinates, ready for any renderer. */
class PlacedGlyph(
    val glyphId: Int,
    val fontId: String,
    val outline: FloatArray,
    val x: Float,
    val y: Float,
    /** Scale from outline base units ([OutlineOps.BASE_EM]) to document units. */
    val scale: Float,
    val override: LetterOverride? = null,
)

/** A fully laid-out text layer, renderer- and exporter-independent. */
class PlacedText(
    val layerId: String,
    val layerName: String,
    val fontId: String,
    val visible: Boolean,
    val opacity: Float,
    val color: Long,
    val fillMode: FillMode,
    val strokeWidth: Float,
    val strokeColor: Long,
    val shadow: ShadowSpec?,
    val gold: GoldSpec?,
    val layerTransform: Transform,
    val fontSize: Float,
    val glyphs: List<PlacedGlyph>,
) {
    /** Approximate bounds (glyph origins only) for hit-testing. */
    val bounds: BBox by lazy {
        if (glyphs.isEmpty()) {
            BBox(0f, 0f, 0f, 0f)
        } else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (g in glyphs) {
                if (g.x < minX) minX = g.x
                if (g.y < minY) minY = g.y
                if (g.x > maxX) maxX = g.x
                if (g.y > maxY) maxY = g.y
            }
            BBox(minX, minY, maxX, maxY)
        }
    }
}

/**
 * Lays out the document's text layers into [PlacedText]s.
 *
 * Key behaviours:
 *  - Whole runs are shaped with HarfBuzz so joins stay correct.
 *  - A letter can use a different font ([LetterOverride.fontId]); the line is
 *    then split into font runs and laid out right-to-left.
 *  - A letter can use a variant ([LetterOverride.variantTag]); the variant
 *    feature is applied only to that letter's cluster range, keeping joins.
 */
class TextLayouter(
    private val shaper: Shaper = Shaper(),
    private val fontResolver: FontResolver,
) {

    suspend fun layout(document: PelcDocument): List<PlacedText> = withContext(Dispatchers.Default) {
        val out = ArrayList<PlacedText>()
        for (layer in document.layers) {
            if (!layer.visible || layer.kind != LayerKind.TEXT) continue
            val content = layer.text ?: continue
            layoutLayer(content, layer)?.let { out += it }
        }
        out
    }

    private fun layoutLayer(content: TextContent, layer: Layer): PlacedText? {
        val glyphs = ArrayList<PlacedGlyph>()
        var baselineY = layer.transform.ty + content.fontSize * FIRST_BASELINE_FACTOR
        for (line in content.text.split('\n')) {
            layoutLine(line, content, baselineY, glyphs)
            baselineY += content.fontSize * content.lineHeight
        }
        if (glyphs.isEmpty()) return null

        return PlacedText(
            layerId = layer.id,
            layerName = layer.name,
            fontId = content.fontId,
            visible = layer.visible,
            opacity = layer.opacity,
            color = content.color,
            fillMode = layer.style.fillMode,
            strokeWidth = layer.style.strokeWidth,
            strokeColor = layer.style.strokeColor,
            shadow = layer.style.shadow,
            gold = layer.style.gold,
            layerTransform = layer.transform,
            fontSize = content.fontSize,
            glyphs = glyphs,
        )
    }

    private fun layoutLine(line: String, content: TextContent, baselineY: Float, out: MutableList<PlacedGlyph>) {
        if (line.isBlank()) return

        val runs = splitRuns(line, content)
        val shaped = runs.map { shapeRun(it, content) }
        val totalWidth = shaped.sumOf { it.totalWidth.toDouble() }.toFloat()

        // Lay runs out; for RTL the first logical run occupies the right side.
        var prefix = 0f
        for ((run, result) in runs.zip(shaped)) {
            val left = if (content.rtl) totalWidth - prefix - result.totalWidth else prefix
            prefix += result.totalWidth

            for (g in result.glyphs) {
                val outline = cachedOutline(run.font, g.glyphId) ?: continue
                val globalCluster = run.start + g.cluster
                out += PlacedGlyph(
                    glyphId = g.glyphId,
                    fontId = run.font.fontId,
                    outline = outline,
                    x = left + g.xOffset,
                    // HarfBuzz y is up-positive; document y is down-positive.
                    y = baselineY - g.yOffset,
                    scale = run.fontSize / OutlineOps.BASE_EM,
                    override = content.perLetter[globalCluster],
                )
            }
        }
    }

    /**
     * Shapes one font run. When the run carries variant overrides, a first
     * plain pass discovers cluster boundaries and a second pass applies the
     * variant features restricted to those clusters — so neighbouring letters
     * keep their contextual shapes.
     */
    private fun shapeRun(run: TextRun, content: TextContent): ShapeResult {
        val plain = ShapeRequest(run.font, run.text, run.fontSize, content.rtl, includeOutlines = false)
        val variantOverrides = content.perLetter.filterKeys { it >= run.start && it < run.start + run.text.length }
            .values.filter { !it.variantTag.isNullOrBlank() }

        if (variantOverrides.isEmpty()) return shaper.shapeSync(plain)

        val base = shaper.shapeSync(plain)
        val ranges = shaper.clusterRanges(base, run.text.length)
        val features = ArrayList<FeatureRange>()
        for ((globalCluster, override) in content.perLetter) {
            if (globalCluster < run.start || globalCluster >= run.start + run.text.length) continue
            val tag = override.variantTag ?: continue
            val relative = globalCluster - run.start
            val range = ranges.firstOrNull { relative >= it.first && relative < it.second }
                ?: (relative to relative + 1)
            features += FeatureRange(tag, range.first, range.second)
        }
        return shaper.shapeSync(ShapeRequest(run.font, run.text, run.fontSize, content.rtl, features))
    }

    /** Splits a line into consecutive runs that share the same font. */
    private fun splitRuns(line: String, content: TextContent): List<TextRun> {
        val runs = ArrayList<TextRun>()
        var currentStart = -1
        var currentFontId: String? = null
        var utf16 = 0
        while (utf16 < line.length) {
            val override = content.perLetter[utf16]
            val fontId = override?.fontId ?: content.fontId
            if (currentFontId == null) {
                currentFontId = fontId
                currentStart = utf16
            } else if (fontId != currentFontId) {
                makeRun(line.substring(currentStart, utf16), currentStart, currentFontId, content)
                    ?.let { runs += it }
                currentFontId = fontId
                currentStart = utf16
            }
            utf16 += Character.charCount(line.codePointAt(utf16))
        }
        if (currentFontId != null && currentStart >= 0) {
            makeRun(line.substring(currentStart), currentStart, currentFontId, content)
                ?.let { runs += it }
        }
        return runs
    }

    private fun makeRun(text: String, start: Int, fontId: String, content: TextContent): TextRun? {
        val font = fontResolver.resolve(fontId) ?: fontResolver.resolve(content.fontId) ?: return null
        return TextRun(start, text, font, content.fontSize)
    }

    private class TextRun(
        val start: Int,
        val text: String,
        val font: LoadedFont,
        val fontSize: Float,
    )

    /** Simple LRU cache of extracted outlines, shared across renders. */
    private fun cachedOutline(font: LoadedFont, glyphId: Int): FloatArray? {
        val key = "${font.fontId}#$glyphId"
        synchronized(outlineCache) {
            if (outlineCache.containsKey(key)) return outlineCache[key]
        }
        val value = font.glyphOutline(glyphId)
        synchronized(outlineCache) {
            if (outlineCache.size >= OUTLINE_CACHE_MAX) {
                val it = outlineCache.entries.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            outlineCache[key] = value
        }
        return value
    }

    private companion object {
        const val FIRST_BASELINE_FACTOR = 0.9f
        const val OUTLINE_CACHE_MAX = 4096
    }

    private val outlineCache = LinkedHashMap<String, FloatArray?>(256, 0.75f, true)
}
