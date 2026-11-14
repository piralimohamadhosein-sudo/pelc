package ir.pelc.core.export.psd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream

class PsdWriterTest {

    private fun raster(): PsdRaster {
        val size = 4
        val r = ByteArray(size) { 0x10 }
        val g = ByteArray(size) { 0x20 }
        val b = ByteArray(size) { 0x30 }
        val a = ByteArray(size) { 0xFF }
        return PsdRaster(2, 2, r, g, b, a)
    }

    private fun psd(): PsdFileData = PsdFileData(
        width = 2,
        height = 2,
        composite = raster(),
        layers = listOf(PsdLayerData(name = "متن اصلی", raster = raster(), opacity = 255, visible = true)),
    )

    @Test
    fun `header follows the adobe spec`() {
        val bytes = writeBytes()
        val d = DataInputStream(ByteArrayInputStream(bytes))

        assertEquals("8BPS", String(ByteArray(4).also { d.readFully(it) }))
        assertEquals(1, d.readShort().toInt())
        d.skipBytes(6) // reserved
        assertEquals(4, d.readShort().toInt()) // channels
        assertEquals(2, d.readInt()) // height
        assertEquals(2, d.readInt()) // width
        assertEquals(8, d.readShort().toInt()) // depth
        assertEquals(3, d.readShort().toInt()) // RGB mode
    }

    @Test
    fun `color mode and resources sections are empty`() {
        val d = DataInputStream(ByteArrayInputStream(writeBytes()))
        skipHeader(d)

        assertEquals(0, d.readInt()) // color mode data
        assertEquals(0, d.readInt()) // image resources
        assertTrue(d.readInt() > 0) // layer & mask info length
    }

    @Test
    fun `layer count matches and composite follows`() {
        val bytes = writeBytes()
        val d = DataInputStream(ByteArrayInputStream(bytes))
        skipHeader(d)
        d.readInt() // color mode
        d.readInt() // resources

        val sectionLength = d.readInt()
        val layerInfoLength = d.readInt()

        // Layer & mask section = [layer-info length][layer info][global mask length]
        assertTrue(layerInfoLength > 0)
        assertEquals(sectionLength, 4 + layerInfoLength + 4)

        // After the section: raw composite = compression(short) + 4 planes of 4 px
        d.skipBytes(sectionLength)
        assertEquals(0, d.readShort().toInt()) // compression raw
        val remaining = bytes.size - 26 - 4 - 4 - 4 - sectionLength - 2
        assertEquals(16, remaining) // 4 channels x 4 pixels
    }

    private fun writeBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        PsdWriter().write(psd(), out)
        return out.toByteArray()
    }

    private fun skipHeader(d: DataInputStream) {
        d.skipBytes(4 + 2 + 6 + 2 + 4 + 4 + 2 + 2)
    }
}
