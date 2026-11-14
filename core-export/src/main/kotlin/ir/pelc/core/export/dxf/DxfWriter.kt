package ir.pelc.core.export.dxf

import ir.pelc.core.model.OutlineOps
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.shaping.PlacedText
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal but compatible DXF (R12/AC1009) writer. Every glyph contour is
 * flattened into closed POLYLINE entities, grouped in one DXF layer per pelc
 * layer. The y axis is flipped to the CAD convention (y up).
 */
class DxfWriter {

    fun write(document: PelcDocument, placed: List<PlacedText>): String {
        val sb = StringBuilder(64 * 1024)

        header(sb)
        layers(sb, placed)
        sb.append("0\nSECTION\n2\nENTITIES\n")
        for ((index, text) in placed.withIndex()) {
            if (!text.visible) continue
            writeLayer(sb, index, text)
        }
        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }

    private fun header(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n")
        sb.append("9\n\$INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("0\nENDSEC\n")
    }

    private fun layers(sb: StringBuilder, placed: List<PlacedText>) {
        val used = placed.filter { it.visible }
        sb.append("0\nSECTION\n2\nTABLES\n")
        sb.append("0\nTABLE\n2\nLAYER\n70\n${used.size}\n")
        for ((index, text) in used.withIndex()) {
            sb.append("0\nLAYER\n2\n${dxfLayerName(index, text.layerName)}\n70\n0\n62\n${aciColor(index)}\n6\nCONTINUOUS\n")
        }
        sb.append("0\nENDTAB\n0\nENDSEC\n")
    }

    private fun writeLayer(sb: StringBuilder, index: Int, text: PlacedText) {
        val layerName = dxfLayerName(index, text.layerName)
        val flipY = text.bounds.maxY + text.fontSize
        for (glyph in text.glyphs) {
            for (contour in flatten(glyph.outline, glyph.scale)) {
                val points = contour.map { p ->
                    // Letter override transform (about glyph origin), then glyph
                    // placement, then layer transform, then y-flip for CAD.
                    var x = p.first * glyph.scale
                    var y = p.second * glyph.scale
                    glyph.override?.let { o ->
                        x *= o.scale
                        y *= o.scale
                        val rad = Math.toRadians(o.rotateDeg.toDouble())
                        val rx = x * cos(rad) - y * sin(rad)
                        val ry = x * sin(rad) + y * cos(rad)
                        x = rx + o.dx
                        y = ry + o.dy
                    }
                    x += glyph.x
                    y += glyph.y
                    val t = text.layerTransform
                    val rad = Math.toRadians(t.rotationDeg.toDouble())
                    val lx = t.scaleX * (x * cos(rad) - y * sin(rad)) + t.tx
                    val ly = t.scaleY * (x * sin(rad) + y * cos(rad)) + t.ty
                    Pair(lx, flipY - ly)
                }
                if (points.size < 2) continue
                sb.append("0\nPOLYLINE\n8\n$layerName\n66\n1\n70\n1\n10\n0.0\n20\n0.0\n30\n0.0\n")
                for ((px, py) in points) {
                    sb.append("0\nVERTEX\n8\n$layerName\n10\n${num(px)}\n20\n${num(py)}\n30\n0.0\n")
                }
                sb.append("0\nSEQEND\n8\n$layerName\n")
            }
        }
    }

    /**
     * Flattens outline commands into contours of points (outline base units).
     * Curves are subdivided into fixed-count segments.
     */
    fun flatten(outline: FloatArray, scale: Float = 1f): List<List<Pair<Double, Double>>> {
        val contours = ArrayList<List<Pair<Double, Double>>>()
        var current = ArrayList<Pair<Double, Double>>()
        var cx = 0.0
        var cy = 0.0
        var contourStartX = 0.0
        var contourStartY = 0.0
        var i = 0
        while (i < outline.size) {
            when (outline[i].toInt()) {
                OutlineOps.MOVE -> {
                    if (current.size >= 2) contours += current
                    current = ArrayList()
                    cx = outline[i + 1].toDouble()
                    cy = outline[i + 2].toDouble()
                    contourStartX = cx
                    contourStartY = cy
                    current += Pair(cx, cy)
                    i += 3
                }
                OutlineOps.LINE -> {
                    cx = outline[i + 1].toDouble()
                    cy = outline[i + 2].toDouble()
                    current += Pair(cx, cy)
                    i += 3
                }
                OutlineOps.QUAD -> {
                    val qx = outline[i + 1].toDouble()
                    val qy = outline[i + 2].toDouble()
                    val ex = outline[i + 3].toDouble()
                    val ey = outline[i + 4].toDouble()
                    for (s in 1..QUAD_SEGMENTS) {
                        val t = s.toDouble() / QUAD_SEGMENTS
                        val mt = 1 - t
                        val x = mt * mt * cx + 2 * mt * t * qx + t * t * ex
                        val y = mt * mt * cy + 2 * mt * t * qy + t * t * ey
                        current += Pair(x, y)
                    }
                    cx = ex
                    cy = ey
                    i += 5
                }
                OutlineOps.CUBIC -> {
                    val c1x = outline[i + 1].toDouble()
                    val c1y = outline[i + 2].toDouble()
                    val c2x = outline[i + 3].toDouble()
                    val c2y = outline[i + 4].toDouble()
                    val ex = outline[i + 5].toDouble()
                    val ey = outline[i + 6].toDouble()
                    for (s in 1..CUBIC_SEGMENTS) {
                        val t = s.toDouble() / CUBIC_SEGMENTS
                        val mt = 1 - t
                        val x = mt * mt * mt * cx + 3 * mt * mt * t * c1x + 3 * mt * t * t * c2x + t * t * t * ex
                        val y = mt * mt * mt * cy + 3 * mt * mt * t * c1y + 3 * mt * t * t * c2y + t * t * t * ey
                        current += Pair(x, y)
                    }
                    cx = ex
                    cy = ey
                    i += 7
                }
                OutlineOps.CLOSE -> {
                    if (current.size >= 2) {
                        current += Pair(contourStartX, contourStartY)
                        contours += current
                    }
                    current = ArrayList()
                    i += 1
                }
                else -> i = outline.size
            }
        }
        if (current.size >= 2) contours += current
        return contours
    }

    private fun num(value: Double): String = String.format(Locale.US, "%.4f", value)

    companion object {
        private const val QUAD_SEGMENTS = 16
        private const val CUBIC_SEGMENTS = 24

        /** DXF layer names are ASCII; Persian names become indexed names. */
        fun dxfLayerName(index: Int, name: String): String {
            val ascii = name.map { c -> if (c.code in 0x20..0x7E && c !in " /<>:?\"|*") c else '_' }
                .joinToString("")
                .take(24)
            return if (ascii.isBlank() || ascii.all { it == '_' }) "PELC_L${index + 1}" else "PELC_${index + 1}_$ascii"
        }

        private fun aciColor(index: Int): Int = ACI_COLORS[index % ACI_COLORS.size]

        private val ACI_COLORS = listOf(7, 2, 3, 4, 5, 6, 1, 30, 40, 50)
    }
}
