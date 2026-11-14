package ir.pelc.core.shaping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** High-level shaping API: HarfBuzz calls wrapped in a clean Kotlin surface. */
class Shaper {

    suspend fun shape(request: ShapeRequest): ShapeResult = withContext(Dispatchers.Default) {
        shapeSync(request)
    }

    fun shapeSync(request: ShapeRequest): ShapeResult {
        val featureArray = buildFeatureArray(request.features)
        val raw = request.font.shape(request.text, request.rtl, request.fontSize, featureArray)
            ?: error("HarfBuzz shaping failed for font '${request.font.fontId}'")

        val glyphCount = raw[0].toInt()
        val glyphs = ArrayList<ShapedGlyph>(glyphCount)
        var index = 1
        var width = 0f
        repeat(glyphCount) {
            val glyphId = raw[index].toInt()
            val cluster = raw[index + 1].toInt()
            val xAdvance = raw[index + 2]
            val yAdvance = raw[index + 3]
            val xOffset = raw[index + 4]
            val yOffset = raw[index + 5]
            index += 6
            width += xAdvance

            val outline = if (request.includeOutlines) request.font.glyphOutline(glyphId) else null
            glyphs += ShapedGlyph(glyphId, cluster, xAdvance, yAdvance, xOffset, yOffset, outline)
        }
        return ShapeResult(glyphs, width, request.font.fontId, request.fontSize)
    }

    /**
     * Contiguous UTF-16 cluster ranges of a shaping result:
     * a base letter grouped with its following combining marks.
     */
    fun clusterRanges(result: ShapeResult, textLength: Int): List<Pair<Int, Int>> {
        val starts = result.glyphs.map { it.cluster }.distinct().sorted()
        return starts.mapIndexed { i, start ->
            val end = if (i + 1 < starts.size) starts[i + 1] else textLength
            start to end
        }
    }

    private fun buildFeatureArray(features: List<FeatureRange>): IntArray =
        features.flatMap { f ->
            listOf(HarfBuzzJni.packTag(f.tag), if (f.enabled) 1 else 0, f.start, f.end)
        }.toIntArray()
}
