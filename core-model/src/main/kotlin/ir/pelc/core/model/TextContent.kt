package ir.pelc.core.model

import kotlinx.serialization.Serializable

/**
 * Text content of a TEXT layer.
 *
 * [text] is shaped with HarfBuzz in [fontId]. Individual letters can be
 * overridden through [perLetter]: the key is the UTF-16 cluster index of the
 * letter inside [text] (as reported by HarfBuzz), which groups a base letter
 * with its following marks.
 */
@Serializable
data class TextContent(
    val text: String = "بسم الله",
    val fontId: String = "",
    val fontSize: Float = 120f,
    val lineHeight: Float = 1.4f,
    val rtl: Boolean = true,
    val color: Long = 0xFF1A1A1AL,
    val perLetter: Map<Int, LetterOverride> = emptyMap(),
)

/**
 * Per-letter override. Everything is optional: only the fields that differ
 * from the layer defaults need to be set. [variantTag] is an OpenType feature
 * tag such as "ss01", "ss03", "salt", "cv02", "swsh" ...
 */
@Serializable
data class LetterOverride(
    val fontId: String? = null,
    val variantTag: String? = null,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val rotateDeg: Float = 0f,
    val color: Long? = null,
)

/** Metadata of a font available in the app (bundled or imported by the user). */
@Serializable
data class PelcFont(
    val id: String,
    val displayName: String,
    val style: String,
    val license: String,
    val file: String,
)
