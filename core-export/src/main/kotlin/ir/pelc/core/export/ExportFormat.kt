package ir.pelc.core.export

import java.io.File

/** All export formats pelc supports. No watermark in any of them. */
enum class ExportFormat(val fileExtension: String) {
    PNG("png"),
    JPG("jpg"),
    SVG("svg"),
    PDF("pdf"),
    DXF("dxf"),
    PSD("psd"),
    PELC("pelc"),
    ;

    companion object {
        fun parse(extension: String): ExportFormat? =
            entries.firstOrNull { it.fileExtension.equals(extension, ignoreCase = true) }
    }
}

/** User-tunable export options. */
data class ExportOptions(
    /** Raster scale multiplier (1x = document canvas size, 4x = 4 megapixel-times). */
    val scale: Float = 1f,
    /** JPG quality, 60..100 (default 92, 4:4:4-friendly). */
    val jpgQuality: Int = 92,
    /** PNG with alpha background. */
    val transparentPng: Boolean = false,
    /** Opaque background used by JPG (JPG has no alpha channel). */
    val backgroundColor: Long = 0xFFFFFFFFL,
)

data class ExportResult(val files: List<File>) {
    val primary: File get() = files.first()
}
