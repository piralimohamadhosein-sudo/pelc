package ir.pelc.core.shaping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects which OpenType features actually produce different glyph shapes for
 * a sample text. This powers the per-letter variant picker: a variant is only
 * offered when shaping with the feature enabled changes the glyph stream.
 */
class VariantDetector(private val shaper: Shaper = Shaper()) {

    suspend fun detect(font: LoadedFont, sample: String = DEFAULT_SAMPLE): List<String> =
        withContext(Dispatchers.Default) {
            val tags = font.gsubFeatureTags.filter { it in VARIANT_TAGS }
            if (tags.isEmpty()) return@withContext emptyList()

            val baseline = shaper.shapeSync(
                ShapeRequest(font, sample, SAMPLE_SIZE, includeOutlines = false),
            ).glyphs.map { it.glyphId }

            tags.mapNotNull { tag ->
                val ids = runCatching {
                    shaper.shapeSync(
                        ShapeRequest(
                            font,
                            sample,
                            SAMPLE_SIZE,
                            features = listOf(FeatureRange(tag)),
                            includeOutlines = false,
                        ),
                    ).glyphs.map { it.glyphId }
                }.getOrNull() ?: return@mapNotNull null
                if (ids != baseline) tag else null
            }
        }

    private companion object {
        const val SAMPLE_SIZE = 100f
        const val DEFAULT_SAMPLE = "بهنم"

        val VARIANT_TAGS: Set<String> = buildSet {
            add("salt")
            add("swsh")
            add("cswh")
            add("jalt")
            (1..20).forEach { add("ss%02d".format(it)) }
            (1..20).forEach { add("cv%02d".format(it)) }
        }
    }
}
