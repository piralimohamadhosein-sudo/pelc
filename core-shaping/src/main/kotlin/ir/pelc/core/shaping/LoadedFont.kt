package ir.pelc.core.shaping

/**
 * A font loaded into the native HarfBuzz runtime. Keeps a strong reference to
 * the raw bytes (required because the native blob may reference them) and
 * lazily creates the native font on first use.
 *
 * All access is serialized per instance: a single hb_font_t must not be used
 * from multiple threads at once.
 */
class LoadedFont(
    val fontId: String,
    val bytes: ByteArray,
    val faceIndex: Int = 0,
) {

    private var nativePtr: Long = 0

    val pointer: Long
        get() = synchronized(this) {
            if (nativePtr == 0L) {
                nativePtr = HarfBuzzJni.createFont(bytes, faceIndex)
                check(nativePtr != 0L) { "Failed to load font '$fontId' into HarfBuzz" }
            }
            nativePtr
        }

    /** Feature tags present in the font's GSUB table (basis of variant detection). */
    val gsubFeatureTags: List<String> by lazy {
        synchronized(this) { HarfBuzzJni.gsubFeatureTags(pointer).toList() }
    }

    fun shape(text: String, rtl: Boolean, fontSize: Float, features: IntArray): FloatArray? =
        synchronized(this) { HarfBuzzJni.shape(text, pointer, rtl, fontSize, features) }

    fun glyphOutline(glyphId: Int): FloatArray? =
        synchronized(this) { HarfBuzzJni.glyphOutline(pointer, glyphId) }

    fun close() = synchronized(this) {
        if (nativePtr != 0L) {
            HarfBuzzJni.destroyFont(nativePtr)
            nativePtr = 0L
        }
    }

    protected fun finalize() {
        close()
    }
}

/** Resolves a font id from the document model to a loaded native font. */
interface FontResolver {
    fun resolve(fontId: String): LoadedFont?
}
