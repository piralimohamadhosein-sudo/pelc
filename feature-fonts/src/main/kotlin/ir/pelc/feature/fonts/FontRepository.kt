package ir.pelc.feature.fonts

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pelc.core.model.PelcFont
import ir.pelc.core.model.PelcJson
import ir.pelc.core.shaping.FontResolver
import ir.pelc.core.shaping.LoadedFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of every font available to the app:
 *  - fonts bundled in assets/fonts (declared in fonts.json, OFL-licensed),
 *  - fonts imported by the user through SAF (stored in filesDir/fonts).
 *
 * Also implements [FontResolver] so the shaping/layout pipeline can turn a
 * font id from the document model into a loaded native font.
 */
@Singleton
class FontRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : FontResolver {

    private val _fonts = MutableStateFlow<List<PelcFont>>(emptyList())
    val fonts: StateFlow<List<PelcFont>> = _fonts.asStateFlow()

    private val loadedFonts = ConcurrentHashMap<String, LoadedFont>()
    private val loadMutex = Mutex()

    /** Called once from the app graph; loads bundled fonts and user fonts. */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val all = mutableListOf<PelcFont>()
        all += loadBundledFonts()
        all += loadImportedFonts()
        _fonts.value = all.distinctBy { it.id }
    }

    fun importFont(uri: Uri, onResult: (Result<PelcFont>) -> Unit) {
        kotlinx.coroutines.MainScope().launch {
            onResult(runCatching { importFontBlocking(uri) })
        }
    }

    suspend fun importFont(uri: Uri): Result<PelcFont> = withContext(Dispatchers.IO) {
        runCatching { importFontBlocking(uri) }
    }

    private suspend fun importFontBlocking(uri: Uri): PelcFont {
        val resolver = context.contentResolver
        val fileName = queryFileName(uri) ?: "imported-${System.currentTimeMillis()}.ttf"
        val target = userFontsDir()
        target.mkdirs()

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot open font file")
        check(bytes.size > 4) { "Font file is too small" }
        check(isFontBytes(bytes)) { "Not a TTF/OTF font file" }

        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(target, safeName)
        file.writeBytes(bytes)

        val displayName = fileName.substringBeforeLast('.')
        val font = PelcFont(
            id = "user-$displayName",
            displayName = displayName,
            style = "واردشده",
            license = "کاربر تأیید می‌کند مجوز استفاده دارد",
            file = "user/$safeName",
        )
        loadMutex.withLock {
            loadedFonts.remove(font.id)
            _fonts.value = _fonts.value.filter { it.id != font.id } + font
        }
        return font
    }

    override fun resolve(fontId: String): LoadedFont? {
        loadedFonts[fontId]?.let { return it }
        val font = _fonts.value.firstOrNull { it.id == fontId } ?: return null
        val bytes = readFontBytes(font) ?: return null
        return try {
            val loaded = LoadedFont(fontId, bytes)
            loadedFonts[fontId] = loaded
            loaded
        } catch (_: Exception) {
            null
        }
    }

    private fun loadBundledFonts(): List<PelcFont> = try {
        val manifest = context.assets.open("fonts/fonts.json").use { it.readBytes().toString(Charsets.UTF_8) }
        val parsed = PelcJson.json.decodeFromString(BundledManifest.serializer(), manifest)
        parsed.fonts.filter { bundled ->
            runCatching { context.assets.open("fonts/${bundled.file}").use { true } }.getOrDefault(false)
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun loadImportedFonts(): List<PelcFont> {
        val dir = userFontsDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && isFontBytes(f.readBytes()) }
            .orEmpty()
            .map { file ->
                val name = file.nameWithoutExtension
                PelcFont(
                    id = "user-$name",
                    displayName = name,
                    style = "واردشده",
                    license = "واردشده توسط کاربر",
                    file = "user/${file.name}",
                )
            }
    }

    private fun readFontBytes(font: PelcFont): ByteArray? = try {
        if (font.file.startsWith("user/")) {
            File(userFontsDir(), font.file.removePrefix("user/")).takeIf { it.exists() }?.readBytes()
        } else {
            context.assets.open("fonts/${font.file}").use { it.readBytes() }
        }
    } catch (_: Exception) {
        null
    }

    private fun userFontsDir(): File = File(context.filesDir, "fonts")

    private fun queryFileName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun isFontBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val tag = String(bytes, 0, 4, Charsets.US_ASCII)
        return tag == "\u0000\u0001\u0000\u0000" || tag == "true" || tag == "OTTO" ||
            tag == "ttcf" || tag == "wOFF" || tag == "wOF2"
    }

    @kotlinx.serialization.Serializable
    private data class BundledManifest(val fonts: List<PelcFont> = emptyList())
}
