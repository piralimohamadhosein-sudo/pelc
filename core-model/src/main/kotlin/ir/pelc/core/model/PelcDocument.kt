package ir.pelc.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Current .pelc document schema version. Bump when the model changes and add a migration. */
const val CURRENT_SCHEMA_VERSION = 1

fun newId(): String = UUID.randomUUID().toString()

/** Canvas of a document. Defaults to A4 portrait at 300 dpi. */
@Serializable
data class CanvasSpec(
    val width: Int = 2480,
    val height: Int = 3508,
    val dpi: Int = 300,
)

/** Document background. JPG export always needs an opaque color. */
@Serializable
data class Background(
    val color: Long = 0xFFFFFFFFL,
    val transparent: Boolean = false,
)

/** Affine transform of a layer, applied around the layer origin (tx, ty). */
@Serializable
data class Transform(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
)

enum class LayerKind { TEXT, ORNAMENT }

enum class FillMode { SOLID, OUTLINE, GOLD }

/** Shadow effect drawn behind the fill. */
@Serializable
data class ShadowSpec(
    val radius: Float = 8f,
    val dx: Float = 0f,
    val dy: Float = 4f,
    val color: Long = 0x66000000L,
)

/** Two-tone gold effect for calligraphy fills. */
@Serializable
data class GoldSpec(
    val enabled: Boolean = false,
    val innerColor: Long = 0xFFFFD700L,
    val outerColor: Long = 0xFF8A5A00L,
)

/** Decorative ornament layers (frames, stars, borders). */
@Serializable
data class OrnamentSpec(
    val shape: OrnamentShape = OrnamentShape.STAR,
    val color: Long = 0xFFD4AF37L,
    val size: Float = 120f,
)

enum class OrnamentShape { STAR, CIRCLE, FRAME, BORDER }

/** Visual style shared by all layer kinds. */
@Serializable
data class StyleSpec(
    val fillMode: FillMode = FillMode.SOLID,
    val color: Long = 0xFF1A1A1AL,
    val strokeWidth: Float = 0f,
    val strokeColor: Long = 0xFF101010L,
    val shadow: ShadowSpec? = null,
    val gold: GoldSpec? = null,
)

/**
 * A single layer of the document. Layers are stored bottom-to-top (drawing order).
 * Text layers carry [text]; ornament layers carry [ornament].
 */
@Serializable
data class Layer(
    val id: String = newId(),
    val name: String = "لایه",
    val kind: LayerKind = LayerKind.TEXT,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val transform: Transform = Transform(),
    val style: StyleSpec = StyleSpec(),
    val text: TextContent? = null,
    val ornament: OrnamentSpec? = null,
)

/**
 * Root document model. Everything the render engine and every exporter needs
 * lives here; no platform graphics types are allowed in this module.
 */
@Serializable
data class PelcDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String = newId(),
    val title: String = "بدون عنوان",
    val canvas: CanvasSpec = CanvasSpec(),
    val background: Background = Background(),
    val layers: List<Layer> = emptyList(),
    val createdAtEpochMs: Long = 0L,
    val modifiedAtEpochMs: Long = 0L,
) {
    val width: Int get() = canvas.width
    val height: Int get() = canvas.height

    fun findLayer(id: String): Layer? = layers.firstOrNull { it.id == id }
}
