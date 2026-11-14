package ir.pelc.core.shaping

import ir.pelc.core.model.OutlineOps

/**
 * Thin JNI bridge to the native HarfBuzz library (libpelc_shaping.so).
 *
 * All methods are blocking and must be called from a background thread;
 * [LoadedFont] serializes access per font instance.
 */
object HarfBuzzJni {

    init {
        System.loadLibrary("pelc_shaping")
    }

    /** Loads a font from memory. Returns a native hb_font_t pointer, or 0 on failure. */
    external fun createFont(data: ByteArray, faceIndex: Int): Long

    external fun destroyFont(fontPtr: Long)

    /**
     * Shapes [text] with HarfBuzz.
     *
     * Returns a flat FloatArray laid out as
     * `[n, gid0, cluster0, xAdv0, yAdv0, xOff0, yOff0, gid1, ...]` where `n`
     * is the glyph count, or `null` on failure. Units are pixels for the given
     * [fontSize]; y offsets are up-positive (HarfBuzz convention).
     *
     * [features] is a flat IntArray with stride 4:
     * `[packedTag, value, startCluster, endCluster]` where endCluster `-1`
     * means "end of text" (HB_FEATURE_GLOBAL_END).
     */
    external fun shape(text: String, fontPtr: Long, rtl: Boolean, fontSize: Float, features: IntArray): FloatArray?

    /**
     * Outline of one glyph as outline-command floats (see [OutlineOps]).
     * Coordinates are in outline base units (an em of [OutlineOps.BASE_EM] pixels).
     */
    external fun glyphOutline(fontPtr: Long, glyphId: Int): FloatArray?

    /** All OpenType feature tags present in the font's GSUB table. */
    external fun gsubFeatureTags(fontPtr: Long): Array<String>

    /** Packs a 4-character OpenType tag such as "ss01" into a single Int. */
    fun packTag(tag: String): Int {
        require(tag.length == 4) { "OpenType tag must be exactly 4 characters: $tag" }
        var packed = 0
        for (c in tag) packed = (packed shl 8) or (c.code and 0xFF)
        return packed
    }
}
