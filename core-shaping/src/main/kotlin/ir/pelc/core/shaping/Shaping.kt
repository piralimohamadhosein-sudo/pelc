package ir.pelc.core.shaping

import ir.pelc.core.model.OutlineOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One OpenType feature applied over a UTF-16 cluster range. */
data class FeatureRange(
    val tag: String,
    val start: Int = 0,
    /** UTF-16 index where the feature stops being applied; -1 = end of text. */
    val end: Int = -1,
    val enabled: Boolean = true,
)

/** One shaped glyph as produced by HarfBuzz. */
class ShapedGlyph(
    val glyphId: Int,
    /** UTF-16 index of the source character this glyph belongs to. */
    val cluster: Int,
    val xAdvance: Float,
    val yAdvance: Float,
    val xOffset: Float,
    /** Up-positive vertical offset (canvas coordinates are down-positive). */
    val yOffset: Float,
    val outline: FloatArray?,
)

/** Result of shaping a text with one font. */
class ShapeResult(
    val glyphs: List<ShapedGlyph>,
    val totalWidth: Float,
    val fontId: String,
    val fontSize: Float,
)

class ShapeRequest(
    val font: LoadedFont,
    val text: String,
    val fontSize: Float,
    val rtl: Boolean = true,
    val features: List<FeatureRange> = emptyList(),
    /**
     * When false, outlines are not extracted (cheaper); callers that need
     * vector outlines fetch them through the outline cache instead.
     */
    val includeOutlines: Boolean = false,
)

/** Base em size outlines are extracted at, shared with [OutlineOps.BASE_EM]. */
val OUTLINE_BASE_EM: Float = OutlineOps.BASE_EM
