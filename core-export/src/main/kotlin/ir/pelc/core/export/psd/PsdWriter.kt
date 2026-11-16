package ir.pelc.core.export.psd

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream

/** Planar RGBA raster (non-premultiplied), sized width x height. */
class PsdRaster(
    val width: Int,
    val height: Int,
    val r: ByteArray,
    val g: ByteArray,
    val b: ByteArray,
    val a: ByteArray,
)

/** One PSD layer with its pixel data and Photoshop-visible metadata. */
class PsdLayerData(
    val name: String,
    val raster: PsdRaster,
    val opacity: Int,
    val visible: Boolean,
)

class PsdFileData(
    val width: Int,
    val height: Int,
    val composite: PsdRaster,
    /** Bottom-to-top order, matching the layer panel. */
    val layers: List<PsdLayerData>,
)

/**
 * Streaming PSD (Photoshop) writer implemented directly from the Adobe
 * "Photoshop File Formats" specification. Produces an 8-bit RGB file with
 * one real layer per pelc layer (open layers!) plus a flattened composite.
 *
 * Layer names are written twice: as a Pascal string and as a "luni" Unicode
 * block, so Persian layer names appear correctly in Photoshop, Affinity and
 * Photopea.
 */
class PsdWriter {

    fun write(data: PsdFileData, out: OutputStream) {
        val d = DataOutputStream(BufferedOutputStream(out, 1 shl 16))

        // --- File header ---
        d.write(SIGNATURE)
        d.writeShort(FILE_VERSION_1)
        d.write(ByteArray(6)) // reserved
        d.writeShort(4) // channels: R, G, B, A
        d.writeInt(data.height)
        d.writeInt(data.width)
        d.writeShort(8) // depth
        d.writeShort(3) // mode: RGB

        // --- Color mode data ---
        d.writeInt(0)

        // --- Image resources ---
        d.writeInt(0)

        // --- Layer and mask information ---
        val records = ByteArrayOutputStream()
        val rd = DataOutputStream(records)
        val channelData = ByteArrayOutputStream()
        val cd = DataOutputStream(channelData)

        rd.writeShort(data.layers.size)
        for (layer in data.layers) {
            writeLayerRecord(rd, layer)
            writeChannelData(cd, layer.raster)
        }

        val layerInfoBytes = records.toByteArray()
        val channelBytes = channelData.toByteArray()

        val layerAndMask = ByteArrayOutputStream()
        val ld = DataOutputStream(layerAndMask)
        // طبق مشخصهٔ Adobe: طولِ بخش، خودِ فیلد ۴بایتی را نمی‌شمارد — پس بافر با
        // فیلد «طولِ layer info» آغاز می‌شود و طولِ بخش فقط بخشِ پس از خودش را می‌گیرد
        ld.writeInt(layerInfoBytes.size + channelBytes.size) // layer info length
        ld.write(layerInfoBytes)
        ld.write(channelBytes)
        ld.writeInt(0) // global layer mask info: empty
        d.writeInt(layerAndMask.size())
        d.write(layerAndMask.toByteArray())

        // --- Image data (flattened composite) ---
        d.writeShort(COMPRESSION_RAW)
        writePlane(d, data.composite.r)
        writePlane(d, data.composite.g)
        writePlane(d, data.composite.b)
        writePlane(d, data.composite.a)

        d.flush()
    }

    private fun writeLayerRecord(rd: DataOutputStream, layer: PsdLayerData) {
        val raster = layer.raster
        val planeSize = raster.width * raster.height

        // Rectangle: top, left, bottom, right
        rd.writeInt(0)
        rd.writeInt(0)
        rd.writeInt(raster.height)
        rd.writeInt(raster.width)

        // Channel information: id + data length (compression field + plane)
        rd.writeShort(4)
        for (id in CHANNEL_IDS) {
            rd.writeShort(id)
            rd.writeInt(2 + planeSize)
        }

        // Blend mode
        rd.write(BLEND_SIGNATURE)
        rd.write(BLEND_NORMAL)

        // Opacity, clipping, flags, filler
        rd.writeByte(layer.opacity.coerceIn(0, 255))
        rd.writeByte(0)
        rd.writeByte(if (layer.visible) 0 else 2) // bit 1 set = hidden
        rd.writeByte(0)

        // Extra data: mask (empty) + blending ranges (empty) + Pascal name + luni
        val extra = ByteArrayOutputStream()
        val ed = DataOutputStream(extra)
        ed.writeInt(0)
        ed.writeInt(0)
        writePascalName(ed, layer.name)
        writeUnicodeName(ed, layer.name)
        rd.writeInt(extra.size())
        rd.write(extra.toByteArray())
    }

    private fun writeChannelData(cd: DataOutputStream, raster: PsdRaster) {
        cd.writeShort(COMPRESSION_RAW)
        cd.write(raster.r)
        cd.write(raster.g)
        cd.write(raster.b)
        cd.write(raster.a)
    }

    private fun writePascalName(ed: DataOutputStream, name: String) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val n = bytes.size.coerceAtMost(255)
        ed.writeByte(n)
        ed.write(bytes, 0, n)
        val total = 1 + n
        val padding = (4 - total % 4) % 4
        ed.write(ByteArray(padding))
    }

    /** "luni" tagged block: makes Persian layer names show up correctly. */
    private fun writeUnicodeName(ed: DataOutputStream, name: String) {
        val utf16 = name.toByteArray(Charsets.UTF_16BE)
        ed.write(BLEND_SIGNATURE)
        ed.write(KEY_UNICODE_NAME)
        ed.writeInt(4 + utf16.size)
        ed.writeInt(name.length) // number of UTF-16 code units
        ed.write(utf16)
    }

    private fun writePlane(d: DataOutputStream, plane: ByteArray) {
        d.write(plane)
    }

    private companion object {
        const val FILE_VERSION_1 = 1
        const val COMPRESSION_RAW = 0

        val SIGNATURE = byteArrayOf(0x38, 0x42, 0x50, 0x53) // "8BPS"
        val BLEND_SIGNATURE = byteArrayOf(0x38, 0x42, 0x49, 0x4D) // "8BIM"
        val BLEND_NORMAL = byteArrayOf(0x6E, 0x6F, 0x72, 0x6D) // "norm"
        val KEY_UNICODE_NAME = byteArrayOf(0x6C, 0x75, 0x6E, 0x69) // "luni"

        /** PSD channel ids: -1 alpha, 0 R, 1 G, 2 B. */
        val CHANNEL_IDS = intArrayOf(-1, 0, 1, 2)
    }
}
