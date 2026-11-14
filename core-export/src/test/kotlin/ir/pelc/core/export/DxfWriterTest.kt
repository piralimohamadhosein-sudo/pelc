package ir.pelc.core.export.dxf

import ir.pelc.core.model.CanvasSpec
import ir.pelc.core.model.FillMode
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.shaping.PlacedGlyph
import ir.pelc.core.shaping.PlacedText
import ir.pelc.core.model.Transform
import org.junit.Assert.assertTrue
import org.junit.Test

class DxfWriterTest {

    private val outline = floatArrayOf(
        0f, 0f, 0f,
        1f, 100f, 0f,
        1f, 100f, 100f,
        4f,
    )

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
        layerTransform = Transform(tx = 0f, ty = 0f),
        fontSize = 120f,
        glyphs = listOf(PlacedGlyph(7, "amiri", outline, 10f, 200f, 1f)),
    )

    @Test
    fun `dxf has standard sections`() {
        val dxf = DxfWriter().write(
            PelcDocument(canvas = CanvasSpec(1200, 800)),
            listOf(placed()),
        )

        assertTrue(dxf.contains("SECTION"))
        assertTrue(dxf.contains("HEADER"))
        assertTrue(dxf.contains("\$ACADVER"))
        assertTrue(dxf.contains("AC1009"))
        assertTrue(dxf.contains("TABLES"))
        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("EOF"))
    }

    @Test
    fun `glyph contours become closed polylines`() {
        val dxf = DxfWriter().write(
            PelcDocument(canvas = CanvasSpec(1200, 800)),
            listOf(placed()),
        )

        assertTrue(dxf.contains("POLYLINE"))
        assertTrue(dxf.contains("VERTEX"))
        assertTrue(dxf.contains("SEQEND"))
        assertTrue(dxf.contains("PELC_L1"))
    }

    @Test
    fun `persian layer names are sanitized to ascii`() {
        // Fully Persian names collapse to indexed names.
        assertEquals("PELC_L1", DxfWriter.dxfLayerName(0, "متن اصلی"))
        assertEquals("PELC_L4", DxfWriter.dxfLayerName(3, "متن"))
        // ASCII names keep their content after sanitizing spaces.
        assertTrue(DxfWriter.dxfLayerName(1, "abc def").startsWith("PELC_2_"))
    }
}
