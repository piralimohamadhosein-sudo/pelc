package ir.pelc.core.export.project

import android.graphics.Bitmap
import ir.pelc.core.model.LetterOverride
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.model.PelcJson
import ir.pelc.core.shaping.FontResolver
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A .pelc file is a zip: document JSON + embedded fonts + thumbnail. */
object PelcFileContract {
    const val ENTRY_DOCUMENT = "document.json"
    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_THUMBNAIL = "thumb.png"
    const val FONTS_DIR = "fonts/"
    const val MAGIC_EXTENSION = ".pelc"
}

@Serializable
data class PelcManifest(
    val app: String = "pelc",
    val schemaVersion: Int = 1,
    val savedAtEpochMs: Long = 0,
    val fonts: List<EmbeddedFont> = emptyList(),
)

@Serializable
data class EmbeddedFont(val id: String, val file: String)

/** Writes a complete, self-contained .pelc project archive. */
class PelcProjectWriter(private val fontResolver: FontResolver) {

    suspend fun write(
        document: PelcDocument,
        out: OutputStream,
        thumbnail: Bitmap? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        ZipOutputStream(BufferedOutputStream(out, 1 shl 16)).use { zip ->
            val manifest = PelcManifest(
                schemaVersion = document.schemaVersion,
                savedAtEpochMs = nowEpochMs,
                fonts = usedFontIds(document).mapNotNull { id ->
                    fontResolver.resolve(id)?.let { EmbeddedFont(id, fontEntryName(id)) }
                },
            )
            zip.putNextEntry(ZipEntry(PelcFileContract.ENTRY_MANIFEST))
            zip.write(PelcJson.json.encodeToString(PelcManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PelcFileContract.ENTRY_DOCUMENT))
            zip.write(PelcJson.encode(document).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (fontId in usedFontIds(document)) {
                val font = fontResolver.resolve(fontId) ?: continue
                zip.putNextEntry(ZipEntry(PelcFileContract.FONTS_DIR + fontEntryName(fontId)))
                zip.write(font.bytes)
                zip.closeEntry()
            }

            thumbnail?.let { thumb ->
                zip.putNextEntry(ZipEntry(PelcFileContract.ENTRY_THUMBNAIL))
                thumb.compress(Bitmap.CompressFormat.PNG, 90, zip)
                zip.closeEntry()
            }
        }
    }

    private fun usedFontIds(document: PelcDocument): List<String> {
        val ids = LinkedHashSet<String>()
        for (layer in document.layers) {
            val text = layer.text ?: continue
            if (text.fontId.isNotBlank()) ids += text.fontId
            for (override in text.perLetter.values) {
                override.fontId?.takeIf { it.isNotBlank() }?.let { ids += it }
            }
        }
        return ids.toList()
    }

    private fun fontEntryName(fontId: String): String = "$fontId.ttf"
}

/** Reads a .pelc archive, extracting embedded fonts into [fontsDir]. */
class PelcProjectReader(private val fontsDir: File) {

    fun read(input: InputStream): PelcDocument {
        var document: PelcDocument? = null
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == PelcFileContract.ENTRY_DOCUMENT -> {
                        document = PelcJson.decode(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    entry.name.startsWith(PelcFileContract.FONTS_DIR) && !entry.isDirectory -> {
                        val fileName = entry.name.removePrefix(PelcFileContract.FONTS_DIR)
                        if (fileName.isNotBlank() && !fileName.contains("..")) {
                            fontsDir.mkdirs()
                            File(fontsDir, fileName).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return document ?: error("Invalid .pelc file: document.json entry is missing")
    }
}
