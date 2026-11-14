package ir.pelc.core.export.svg

import ir.pelc.core.model.FillMode
import ir.pelc.core.model.OutlineOps
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.shaping.PlacedText
import java.util.Locale

/**
 * Font-independent SVG writer: every glyph is emitted as real vector path
 * data (M/L/Q/C/Z), so the SVG opens identically everywhere, with no font
 * installation required.
 */
class SvgWriter {

    fun write(document: PelcDocument, placed: List<PlacedText>): String {
        val sb = StringBuilder(64 * 1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${document.width}\" " +
                "height=\"${document.height}\" viewBox=\"0 0 ${document.width} ${document.height}\">\n",
        )
        sb.append("  <title>${escape(document.title)}</title>\n")
        sb.append("  <metadata>Created by pelc - Persian calligraphy studio</metadata>\n")

        if (!document.background.transparent) {
            sb.append(
                "  <rect x=\"0\" y=\"0\" width=\"${document.width}\" height=\"${document.height}\" " +
                    "fill=\"#${hex(document.background.color)}\"/>\n",
            )
        }

        for ((index, text) in placed.withIndex()) {
            if (!text.visible) continue
            writeLayer(sb, index, text)
        }

        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun writeLayer(sb: StringBuilder, index: Int, text: PlacedText) {
        val t = text.layerTransform
        val transform =
            "translate(${num(t.tx)} ${num(t.ty)}) rotate(${num(t.rotationDeg)}) " +
                "scale(${num(t.scaleX)} ${num(t.scaleY)})"
        sb.append("  <g id=\"layer-$index\" data-pelc-name=\"${escape(text.layerName)}\" ")
        sb.append("transform=\"$transform\" opacity=\"${num(text.opacity)}\">\n")

        for (glyph in text.glyphs) {
            val override = glyph.override
            val color = override?.color ?: text.color
            val fill = when (text.fillMode) {
                FillMode.OUTLINE -> "none"
                FillMode.GOLD -> "#${hex(text.gold?.innerColor ?: color)}"
                else -> "#${hex(color)}"
            }
            val stroke = if (text.fillMode == FillMode.OUTLINE || text.strokeWidth > 0f) {
                " stroke=\"#${hex(text.strokeColor)}\" stroke-width=\"${num(text.strokeWidth)}\""
            } else {
                ""
            }
            var glyphTransform = "translate(${num(glyph.x)} ${num(glyph.y)}) scale(${num(glyph.scale)})"
            if (override != null && (override.dx != 0f || override.dy != 0f ||
                    override.rotateDeg != 0f || override.scale != 1f)
            ) {
                glyphTransform += " translate(${num(override.dx)} ${num(override.dy)}) " +
                    "rotate(${num(override.rotateDeg)}) scale(${num(override.scale)})"
            }
            sb.append("    <path transform=\"$glyphTransform\" d=\"${pathData(glyph.outline)}\" fill=\"$fill\"$stroke/>\n")
        }
        sb.append("  </g>\n")
    }

    /** Converts the outline command stream into SVG path data. */
    fun pathData(outline: FloatArray): String {
        val sb = StringBuilder(outline.size * 6)
        var i = 0
        while (i < outline.size) {
            when (outline[i].toInt()) {
                OutlineOps.MOVE -> {
                    sb.append("M${num(outline[i + 1])} ${num(outline[i + 2])}")
                    i += 3
                }
                OutlineOps.LINE -> {
                    sb.append("L${num(outline[i + 1])} ${num(outline[i + 2])}")
                    i += 3
                }
                OutlineOps.QUAD -> {
                    sb.append(
                        "Q${num(outline[i + 1])} ${num(outline[i + 2])} " +
                            "${num(outline[i + 3])} ${num(outline[i + 4])}",
                    )
                    i += 5
                }
                OutlineOps.CUBIC -> {
                    sb.append(
                        "C${num(outline[i + 1])} ${num(outline[i + 2])} " +
                            "${num(outline[i + 3])} ${num(outline[i + 4])} " +
                            "${num(outline[i + 5])} ${num(outline[i + 6])}",
                    )
                    i += 7
                }
                OutlineOps.CLOSE -> {
                    sb.append("Z")
                    i += 1
                }
                else -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private fun hex(color: Long): String = "%06X".format(Locale.US, color and 0xFFFFFF)

    private fun num(value: Float): String = String.format(Locale.US, "%.2f", value)

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
