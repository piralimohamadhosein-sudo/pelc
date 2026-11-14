package ir.pelc.core.export.svg

import ir.pelc.core.model.CanvasSpec
import ir.pelc.core.model.FillMode
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.shaping.PlacedGlyph
import ir.pelc.core.shaping.PlacedText
import ir.pelc.core.model.Transform
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgWriterTest {

    /** Synthetic outline: move (0,0) -> line (100,0) -> line (100,100) -> close. */
    private val outline = floatArrayOf(
        0f, 0f, 0f,
        1f, 100f, 0f,
        1f, 100f, 100f,
        4f,
    )

    private fun document(): PelcDocument = PelcDocument(title = "بسم الله", canvas = CanvasSpec(1200, 800))

    private fun placed(): PlacedText = PlacedText(
        layerId = "l1",
        layerName = "متن اصلی",
        fontId = "amiri",
        visible = true,
        opacity = 1f,
        color = 0xFF101010L,
        fillMode = FillMode.SOLID,
        strokeWidth = 0f,
        strokeColor = 0xFF101010L,
        shadow = null,
        gold = null,
        layerTransform = Transform(tx = 50f, ty = 60f),
        fontSize = 120f,
        glyphs = listOf(
            PlacedGlyph(
                glyphId = 7,
                fontId = "amiri",
                outline = outline,
                x = 10f,
                y = 200f,
                scale = 1.2f,
            ),
        ),
    )

    @Test
    fun `svg contains root, viewBox and persian layer name`() {
        val svg = SvgWriter().write(document(), listOf(placed()))

        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("<svg xmlns=\"http://www.w3.org/2000/svg\""))
        assertTrue(svg.contains("viewBox=\"0 0 1200 800\""))
        assertTrue(svg.contains("data-pelc-name=\"متن اصلی\""))
        assertTrue(svg.contains("<title>بسم الله</title>"))
    }

    @Test
    fun `glyph becomes vector path data - font independent`() {
        val svg = SvgWriter().write(document(), listOf(placed()))

        assertTrue(svg.contains("<path"))
        assertTrue(svg.contains("d=\"M0.00 0.00L100.00 0.00L100.00 100.00Z\""))
        assertTrue(svg.contains("scale(1.20)"))
        assertTrue(svg.contains("fill=\"#101010\""))
    }

    @Test
    fun `opaque background becomes a rect`() {
        val svg = SvgWriter().write(document(), listOf(placed()))
        assertTrue(svg.contains("<rect x=\"0\" y=\"0\" width=\"1200\" height=\"800\""))
    }
}
