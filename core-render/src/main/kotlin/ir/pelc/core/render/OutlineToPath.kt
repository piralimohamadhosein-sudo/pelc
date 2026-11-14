package ir.pelc.core.render

import android.graphics.Path
import android.util.LruCache
import ir.pelc.core.model.OutlineOps

/**
 * Converts pelc's outline command stream (see [OutlineOps]) into an
 * android.graphics.Path.
 */
object OutlineToPath {

    fun toPath(outline: FloatArray): Path {
        val path = Path()
        var i = 0
        while (i < outline.size) {
            when (outline[i].toInt()) {
                OutlineOps.MOVE -> {
                    path.moveTo(outline[i + 1], outline[i + 2])
                    i += 3
                }
                OutlineOps.LINE -> {
                    path.lineTo(outline[i + 1], outline[i + 2])
                    i += 3
                }
                OutlineOps.QUAD -> {
                    path.quadTo(outline[i + 1], outline[i + 2], outline[i + 3], outline[i + 4])
                    i += 5
                }
                OutlineOps.CUBIC -> {
                    path.cubicTo(
                        outline[i + 1], outline[i + 2],
                        outline[i + 3], outline[i + 4],
                        outline[i + 5], outline[i + 6],
                    )
                    i += 7
                }
                OutlineOps.CLOSE -> {
                    path.close()
                    i += 1
                }
                else -> return path
            }
        }
        return path
    }
}

/**
 * LRU cache of converted glyph Paths, keyed by (fontId, glyphId).
 * Avoids re-parsing outlines every frame while dragging.
 */
class GlyphOutlineCache(maxEntries: Int = 2048) {

    private val cache = object : LruCache<String, Path>(maxEntries) {
        override fun sizeOf(key: String, value: Path): Int = 1
    }

    fun get(fontId: String, glyphId: Int, outline: FloatArray): Path {
        val key = "$fontId#$glyphId"
        cache.get(key)?.let { return it }
        val path = OutlineToPath.toPath(outline)
        cache.put(key, path)
        return path
    }

    fun clear() = cache.evictAll()
}
