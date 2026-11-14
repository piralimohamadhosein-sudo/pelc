package ir.pelc.feature.editor

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pelc.core.model.Layer
import ir.pelc.core.model.LayerKind
import ir.pelc.core.model.LetterOverride
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.model.TextContent
import ir.pelc.core.model.Transform
import ir.pelc.core.render.PelcRenderer
import ir.pelc.core.shaping.BBox
import ir.pelc.core.shaping.TextLayouter
import ir.pelc.core.shaping.VariantDetector
import ir.pelc.feature.fonts.FontRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LetterUi(val start: Int, val label: String)

/**
 * State holder of the editor. Editing follows the product's three levels:
 * layer, word and letter. History is snapshot-based (immutable model), so
 * undo/redo is trivially correct even for hundreds of steps.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val layouter: TextLayouter,
    private val renderer: PelcRenderer,
    private val fonts: FontRepository,
    private val variantDetector: VariantDetector,
) : ViewModel() {

    private val undoStack = ArrayDeque<PelcDocument>()
    private val redoStack = ArrayDeque<PelcDocument>()

    private val _document = MutableStateFlow(PelcDocument(title = "طرح تازه"))
    val document = _document.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId = _selectedLayerId.asStateFlow()

    val availableFonts = fonts.fonts

    private val _variants = MutableStateFlow<List<String>>(emptyList())
    val variants = _variants.asStateFlow()

    private val _preview = MutableStateFlow<ImageBitmap?>(null)
    val preview = _preview.asStateFlow()

    private val _layerBounds = MutableStateFlow<Map<String, BBox>>(emptyMap())
    val layerBounds = _layerBounds.asStateFlow()

    private val _letters = MutableStateFlow<List<LetterUi>>(emptyList())
    val letters = _letters.asStateFlow()

    private val _selectedLetter = MutableStateFlow<Int?>(null)
    val selectedLetter = _selectedLetter.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    init {
        viewModelScope.launch { fonts.initialize() }
        viewModelScope.launch {
            _document
                .debounce(PREVIEW_DEBOUNCE_MS)
                .collectLatest { doc -> renderPreview(doc) }
        }
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    private fun commit(next: PelcDocument) {
        undoStack.addLast(_document.value)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _document.value = next.copy(modifiedAtEpochMs = System.currentTimeMillis())
        _canUndo.value = true
        _canRedo.value = false
        refreshLetters()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_document.value)
        _document.value = previous
        refreshLetters()
        updateHistoryFlags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_document.value)
        _document.value = next
        refreshLetters()
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    // ------------------------------------------------------------------
    // Layer level
    // ------------------------------------------------------------------

    fun addText(text: String) {
        if (text.isBlank()) return
        val doc = _document.value
        val fontId = availableFonts.value.firstOrNull()?.id.orEmpty()
        val layer = Layer(
            name = text.take(12),
            kind = LayerKind.TEXT,
            transform = Transform(
                tx = doc.width * 0.12f,
                ty = doc.height * 0.4f,
            ),
            text = TextContent(
                text = text,
                fontId = fontId,
                fontSize = (doc.width / 6f).coerceIn(60f, 400f),
            ),
        )
        commit(doc.copy(layers = doc.layers + layer))
        _selectedLayerId.value = layer.id
        detectVariants()
    }

    fun selectLayer(id: String) {
        _selectedLayerId.value = id
        refreshLetters()
        detectVariants()
    }

    fun selectAt(docX: Float, docY: Float) {
        val bounds = _layerBounds.value
        for (layer in _document.value.layers.reversed()) {
            val box = bounds[layer.id] ?: continue
            val padding = (layer.text?.fontSize ?: 80f) * 0.6f
            if (box.contains(docX, docY, padding)) {
                selectLayer(layer.id)
                return
            }
        }
    }

    fun deleteSelected() {
        val id = _selectedLayerId.value ?: return
        val doc = _document.value
        commit(doc.copy(layers = doc.layers.filterNot { it.id == id }))
        _selectedLayerId.value = null
    }

    fun updateOpacity(opacity: Float) {
        updateSelectedLayer { it.copy(opacity = opacity.coerceIn(0f, 1f)) }
    }

    fun toggleVisibility() {
        updateSelectedLayer { it.copy(visible = !it.visible) }
    }

    // ------------------------------------------------------------------
    // Word level
    // ------------------------------------------------------------------

    fun updateText(text: String) {
        updateSelectedText { it.copy(text = text) }
    }

    fun updateFont(fontId: String) {
        updateSelectedText { it.copy(fontId = fontId) }
        detectVariants()
    }

    fun updateFontSize(size: Float) {
        updateSelectedText { it.copy(fontSize = size.coerceIn(20f, 1200f)) }
    }

    fun updateColor(color: Long) {
        updateSelectedText { it.copy(color = color) }
    }

    // ------------------------------------------------------------------
    // Letter level
    // ------------------------------------------------------------------

    fun selectLetter(cluster: Int) {
        _selectedLetter.value = cluster
    }

    fun applyVariantToSelectedLetter(tag: String?) {
        val cluster = _selectedLetter.value ?: return
        updateSelectedText { text ->
            val perLetter = text.perLetter.toMutableMap()
            if (tag == null) {
                perLetter[cluster]?.let { existing ->
                    val cleaned = existing.copy(variantTag = null)
                    if (cleaned == LetterOverride()) perLetter.remove(cluster) else perLetter[cluster] = cleaned
                }
            } else {
                perLetter[cluster] = (perLetter[cluster] ?: LetterOverride()).copy(variantTag = tag)
            }
            text.copy(perLetter = perLetter)
        }
    }

    fun setLetterFont(fontId: String?) {
        val cluster = _selectedLetter.value ?: return
        updateSelectedText { text ->
            val perLetter = text.perLetter.toMutableMap()
            if (fontId == null) {
                perLetter[cluster]?.let { existing ->
                    val cleaned = existing.copy(fontId = null)
                    if (cleaned == LetterOverride()) perLetter.remove(cluster) else perLetter[cluster] = cleaned
                }
            } else {
                perLetter[cluster] = (perLetter[cluster] ?: LetterOverride()).copy(fontId = fontId)
            }
            text.copy(perLetter = perLetter)
        }
    }

    fun resetSelectedLetter() {
        val cluster = _selectedLetter.value ?: return
        updateSelectedText { text ->
            text.copy(perLetter = text.perLetter - cluster)
        }
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            fonts.importFont(uri).onSuccess { font -> updateFont(font.id) }
        }
    }

    // ------------------------------------------------------------------
    // Gestures (drag does not spam history)
    // ------------------------------------------------------------------

    private var dragStartDoc: PelcDocument? = null

    fun dragStart() {
        dragStartDoc = _document.value
    }

    fun dragMove(dxDoc: Float, dyDoc: Float) {
        val id = _selectedLayerId.value ?: return
        val doc = _document.value
        val layers = doc.layers.map { layer ->
            if (layer.id == id) {
                layer.copy(
                    transform = layer.transform.copy(
                        tx = layer.transform.tx + dxDoc,
                        ty = layer.transform.ty + dyDoc,
                    ),
                )
            } else {
                layer
            }
        }
        _document.value = doc.copy(layers = layers)
    }

    fun dragEnd() {
        dragStartDoc?.let { start ->
            undoStack.addLast(start)
            redoStack.clear()
            _canUndo.value = true
            _canRedo.value = false
        }
        dragStartDoc = null
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun updateSelectedLayer(edit: (Layer) -> Layer) {
        val id = _selectedLayerId.value ?: return
        val doc = _document.value
        commit(doc.copy(layers = doc.layers.map { if (it.id == id) edit(it) else it }))
    }

    private fun updateSelectedText(edit: (TextContent) -> TextContent) {
        updateSelectedLayer { layer ->
            layer.text?.let { layer.copy(text = edit(it)) } ?: layer
        }
    }

    private fun refreshLetters() {
        val layer = _document.value.findLayer(_selectedLayerId.value)
        val text = layer?.text
        if (text == null) {
            _letters.value = emptyList()
            return
        }
        val list = ArrayList<LetterUi>()
        var i = 0
        while (i < text.text.length) {
            val codePoint = text.text.codePointAt(i)
            val length = Character.charCount(codePoint)
            list += LetterUi(start = i, label = text.text.substring(i, i + length))
            i += length
        }
        _letters.value = list
    }

    private fun detectVariants() {
        viewModelScope.launch {
            val layer = _document.value.findLayer(_selectedLayerId.value ?: return@launch)
            val fontId = layer?.text?.fontId ?: return@launch
            val font = fonts.resolve(fontId) ?: return@launch
            val sample = layer.text?.text?.takeIf { it.isNotBlank() && it.length <= 24 } ?: "بهنم"
            runCatching { variantDetector.detect(font, sample) }
                .onSuccess { _variants.value = it }
        }
    }

    private suspend fun renderPreview(doc: PelcDocument) {
        runCatching {
            val placed = layouter.layout(doc)
            _layerBounds.value = placed.associate { it.layerId to it.bounds }
            val bitmap = renderer.renderBitmap(doc, PREVIEW_WIDTH, precomputed = placed)
            _preview.value = bitmap.asImageBitmap()
        }
    }

    private companion object {
        const val MAX_HISTORY = 500
        const val PREVIEW_WIDTH = 1080
        const val PREVIEW_DEBOUNCE_MS = 60L
    }
}
