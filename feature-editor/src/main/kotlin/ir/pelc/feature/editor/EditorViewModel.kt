package ir.pelc.feature.editor

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pelc.core.model.EditorHistory
import ir.pelc.core.model.Layer
import ir.pelc.core.model.LayerKind
import ir.pelc.core.model.LetterCluster
import ir.pelc.core.model.LetterOverride
import ir.pelc.core.model.MarkSpec
import ir.pelc.core.model.MarkType
import ir.pelc.core.model.MAX_KASHIDA_BEFORE
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.model.TATWEEL
import ir.pelc.core.model.TextContent
import ir.pelc.core.model.Transform
import ir.pelc.core.model.canKashidaBefore
import ir.pelc.core.model.clusterDisplayLabel
import ir.pelc.core.model.insertKashidaBefore
import ir.pelc.core.model.insertMarkAfter
import ir.pelc.core.model.letterClusters
import ir.pelc.core.model.reconcilePerLetter
import ir.pelc.core.model.remapPerLetter
import ir.pelc.core.model.removeKashidaBefore
import ir.pelc.core.model.removeMarkAt
import ir.pelc.core.render.PelcRenderer
import ir.pelc.core.shaping.BBox
import ir.pelc.core.shaping.PlacedText
import ir.pelc.core.shaping.TextLayouter
import ir.pelc.core.shaping.VariantDetector
import ir.pelc.feature.fonts.FontRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * مدل نمایشی یک حرف برای چیپ‌های تب «حرف»:
 * [start]/[end] بازهٔ UTF-16 کلاستر، [label] برچسب نمایشی،
 * [markCount] تعداد علامت و [kashidaCount] تعداد کشیده پیش از حرف.
 */
data class LetterUi(
    val start: Int,
    val end: Int,
    val label: String,
    val markCount: Int,
    val kashidaCount: Int,
)

/**
 * State holder of the editor. Editing follows the product's three levels:
 * layer, word and letter.
 *
 * Phase 1 additions:
 *  - کشیده: درج/حذف تطویل با قانون اتصال (شکستن لام‌الف و نیم‌فاصله رعایت می‌شود)
 *  - علامت‌ها: افزودن/حذف اِعراب و تنظیم دستی dx/dy/scale هر علامت
 *  - تاریخچهٔ برچسب‌دار با شیت «تاریخچهٔ ویرایش» و پرش به هر گام
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val layouter: TextLayouter,
    private val renderer: PelcRenderer,
    private val fonts: FontRepository,
    private val variantDetector: VariantDetector,
) : ViewModel() {

    private val history = EditorHistory(PelcDocument(title = "طرح تازه"))

    private val _document = MutableStateFlow(history.current)
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

    /** نتیجهٔ آخرین چیدمان برای hit-test حرف با لمس بوم */
    private val _placed = MutableStateFlow<List<PlacedText>>(emptyList())

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
        refreshLetters()
        refreshHistoryFlags()
    }

    // ------------------------------------------------------------------
    // History (Phase 1: برچسب‌دار، ادغام‌شونده، قابل پرش)
    // ------------------------------------------------------------------

    private fun commit(label: String, coalesceKey: String? = null, edit: (PelcDocument) -> PelcDocument) {
        val current = _document.value
        val edited = edit(current)
        if (edited == current) return
        _document.value = history.apply(edited.stamped(), label, coalesceKey)
        refreshHistoryFlags()
        refreshLetters()
    }

    private fun PelcDocument.stamped(): PelcDocument = copy(modifiedAtEpochMs = System.currentTimeMillis())

    fun undo() {
        history.undo()?.let {
            _document.value = it
            validateSelection()
            refreshHistoryFlags()
            refreshLetters()
        }
    }

    fun redo() {
        history.redo()?.let {
            _document.value = it
            validateSelection()
            refreshHistoryFlags()
            refreshLetters()
        }
    }

    /** برچسب گام‌های تاریخچه، قدیم به جدید (برای شیت تاریخچه) */
    fun historySteps(): List<String> = history.labels()

    /** موقعیت فعلی: ۰ = سند آغازین */
    fun historyPosition(): Int = history.position

    /** پرش به گام [index]؛ -۱ یعنی سند آغازین */
    fun jumpTo(index: Int) {
        _document.value = history.jumpTo(index)
        validateSelection()
        refreshHistoryFlags()
        refreshLetters()
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    private fun validateSelection() {
        val doc = _document.value
        if (_selectedLayerId.value?.let { doc.findLayer(it) } == null) {
            _selectedLayerId.value = null
            _selectedLetter.value = null
        }
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
        commit("افزودن متن") { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.id
        _selectedLetter.value = null
        refreshLetters()
        detectVariants()
    }

    fun selectLayer(id: String) {
        if (_selectedLayerId.value != id) _selectedLetter.value = null
        _selectedLayerId.value = id
        refreshLetters()
        detectVariants()
    }

    /**
     * انتخاب با لمس بوم: اول لایه، و اگر همان لایه انتخاب شده باشد، حرفِ
     * زیر انگشت (از جعبهٔ کلاسترها) هم انتخاب می‌شود.
     */
    fun selectAt(docX: Float, docY: Float) {
        val bounds = _layerBounds.value
        val hit = _document.value.layers.reversed().firstOrNull { layer ->
            val padding = (layer.text?.fontSize ?: 80f) * 0.6f
            bounds[layer.id]?.contains(docX, docY, padding) == true
        }
        if (hit == null) {
            _selectedLayerId.value = null
            _selectedLetter.value = null
            return
        }
        if (_selectedLayerId.value == hit.id) {
            val placed = _placed.value.firstOrNull { it.layerId == hit.id }
            val pad = (hit.text?.fontSize ?: 80f) * 0.08f
            _selectedLetter.value = placed?.clusterBoxes
                ?.entries
                ?.firstOrNull { it.value.contains(docX, docY, pad) }
                ?.key
        } else {
            _selectedLayerId.value = hit.id
            _selectedLetter.value = null
            refreshLetters()
            detectVariants()
        }
    }

    fun deleteSelected() {
        val id = _selectedLayerId.value ?: return
        commit("حذف لایه") { doc -> doc.copy(layers = doc.layers.filterNot { it.id == id }) }
        _selectedLayerId.value = null
        _selectedLetter.value = null
    }

    fun updateOpacity(opacity: Float) {
        updateSelectedLayer(label = "شفافیت لایه", coalesceKey = "opacity:${_selectedLayerId.value}") {
            it.copy(opacity = opacity.coerceIn(0f, 1f))
        }
    }

    fun toggleVisibility() {
        updateSelectedLayer(label = "نمایش/پنهان لایه") { it.copy(visible = !it.visible) }
    }

    // ------------------------------------------------------------------
    // Word level
    // ------------------------------------------------------------------

    fun updateText(text: String) {
        val id = _selectedLayerId.value ?: return
        commit("ویرایش متن", coalesceKey = "text:$id") { doc ->
            doc.mapTextLayer(id) { content ->
                content.copy(
                    text = text,
                    // علائم حذف‌شده با تایپ آزاد، از مدل هم پاک می‌شوند
                    perLetter = reconcilePerLetter(text, content.perLetter),
                )
            }
        }
    }

    fun updateFont(fontId: String) {
        updateSelectedText(label = "قلم واژه") { it.copy(fontId = fontId) }
        detectVariants()
    }

    fun updateFontSize(size: Float) {
        updateSelectedText(label = "اندازهٔ قلم", coalesceKey = "size:${_selectedLayerId.value}") {
            it.copy(fontSize = size.coerceIn(20f, 1200f))
        }
    }

    fun updateColor(color: Long) {
        updateSelectedText(label = "رنگ واژه", coalesceKey = "color:${_selectedLayerId.value}") {
            it.copy(color = color)
        }
    }

    // ------------------------------------------------------------------
    // Letter level — گونه و قلم (فاز ۰) + کشیده و علامت (فاز ۱)
    // ------------------------------------------------------------------

    fun selectLetter(cluster: Int) {
        _selectedLetter.value = cluster
    }

    fun applyVariantToSelectedLetter(tag: String?) {
        val cluster = _selectedLetter.value ?: return
        updateSelectedText(label = if (tag == null) "حذف گونهٔ حرف" else "گونهٔ حرف ($tag)") { text ->
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
        updateSelectedText(label = if (fontId == null) "قلم واژه برای حرف" else "قلم حرف") { text ->
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
        updateSelectedText(label = "بازنشانی حرف") { text ->
            text.copy(perLetter = text.perLetter - cluster)
        }
    }

    // ------------------------------------------------------------------
    // کشیده (Phase 1)
    // ------------------------------------------------------------------

    /** آیا پیش از کلاستر [clusterStart] کشیده قابل درج است؟ */
    fun canAddKashida(clusterStart: Int): Boolean {
        val text = selectedText() ?: return false
        return canKashidaBefore(text, clusterStart)
    }

    fun addKashida(clusterStart: Int) {
        val id = _selectedLayerId.value ?: return
        val text = selectedText() ?: return
        val already = letterClusters(text).firstOrNull { it.start == clusterStart }?.kashidaBefore ?: 0
        if (already >= MAX_KASHIDA_BEFORE) return
        if (!canKashidaBefore(text, clusterStart)) return
        commit("افزودن کشیده") { doc ->
            doc.mapTextLayer(id) { content ->
                val mutation = insertKashidaBefore(content.text, clusterStart) ?: return@mapTextLayer content
                content.copy(text = mutation.newText, perLetter = remapPerLetter(content.perLetter, mutation))
            }
        }
        // تطویل «قبل از» حرف درج شد؛ حرفِ انتخاب‌شده یک اندیس جلو می‌رود
        if (_selectedLetter.value == clusterStart) _selectedLetter.value = clusterStart + 1
    }

    fun removeKashida(clusterStart: Int) {
        val id = _selectedLayerId.value ?: return
        val text = selectedText() ?: return
        val cluster = letterClusters(text).firstOrNull { it.start == clusterStart } ?: return
        if (cluster.kashidaBefore == 0) return
        commit("حذف کشیده") { doc ->
            doc.mapTextLayer(id) { content ->
                val mutation = removeKashidaBefore(content.text, clusterStart) ?: return@mapTextLayer content
                content.copy(text = mutation.newText, perLetter = remapPerLetter(content.perLetter, mutation))
            }
        }
        if (_selectedLetter.value == clusterStart) _selectedLetter.value = clusterStart - 1
    }

    // ------------------------------------------------------------------
    // علامت‌ها (Phase 1)
    // ------------------------------------------------------------------

    fun addMark(clusterStart: Int, type: MarkType) {
        val id = _selectedLayerId.value ?: return
        commit("افزودن علامت ${type.title}") { doc ->
            doc.mapTextLayer(id) { content ->
                val mutation = insertMarkAfter(content.text, clusterStart, type.char)
                    ?: return@mapTextLayer content
                val perLetter = remapPerLetter(content.perLetter, mutation).toMutableMap()
                val current = perLetter[clusterStart] ?: LetterOverride()
                perLetter[clusterStart] = current.copy(marks = current.marks + MarkSpec(type = type))
                content.copy(text = mutation.newText, perLetter = perLetter)
            }
        }
        if (_selectedLetter.value != clusterStart) _selectedLetter.value = clusterStart
    }

    fun removeMark(clusterStart: Int, markIndex: Int) {
        val id = _selectedLayerId.value ?: return
        commit("حذف علامت") { doc ->
            doc.mapTextLayer(id) { content ->
                val mutation = removeMarkAt(content.text, clusterStart, markIndex)
                    ?: return@mapTextLayer content
                val perLetter = remapPerLetter(content.perLetter, mutation).toMutableMap()
                val current = perLetter[clusterStart] ?: return@mapTextLayer content
                val specs = current.marks.toMutableList()
                if (markIndex >= specs.size) return@mapTextLayer content
                specs.removeAt(markIndex)
                val cleaned = current.copy(marks = specs)
                if (cleaned == LetterOverride()) perLetter.remove(clusterStart) else perLetter[clusterStart] = cleaned
                content.copy(text = mutation.newText, perLetter = perLetter)
            }
        }
    }

    /** جابه‌جایی دستی یک علامت؛ گام‌های پیاپی در تاریخچه ادغام می‌شوند */
    fun nudgeMark(clusterStart: Int, markIndex: Int, ddx: Float, ddy: Float) {
        val id = _selectedLayerId.value ?: return
        commit("تنظیم جای علامت", coalesceKey = "mark-pos:$id:$clusterStart:$markIndex") { doc ->
            doc.mapTextLayer(id) { content ->
                content.withMark(clusterStart, markIndex) { mark ->
                    mark.copy(
                        dx = (mark.dx + ddx).coerceIn(MARK_LIMIT_MIN, MARK_LIMIT_MAX),
                        dy = (mark.dy + ddy).coerceIn(MARK_LIMIT_MIN, MARK_LIMIT_MAX),
                    )
                }
            }
        }
    }

    /** مقیاس یک علامت با ضریب [factor] */
    fun scaleMark(clusterStart: Int, markIndex: Int, factor: Float) {
        val id = _selectedLayerId.value ?: return
        commit("اندازهٔ علامت", coalesceKey = "mark-scale:$id:$clusterStart:$markIndex") { doc ->
            doc.mapTextLayer(id) { content ->
                content.withMark(clusterStart, markIndex) { mark ->
                    mark.copy(scale = (mark.scale * factor).coerceIn(MARK_SCALE_MIN, MARK_SCALE_MAX))
                }
            }
        }
    }

    private fun TextContent.withMark(
        clusterStart: Int,
        markIndex: Int,
        edit: (MarkSpec) -> MarkSpec,
    ): TextContent {
        val current = perLetter[clusterStart] ?: return this
        val specs = current.marks.toMutableList()
        val mark = specs.getOrNull(markIndex) ?: return this
        specs[markIndex] = edit(mark)
        return copy(perLetter = perLetter + (clusterStart to current.copy(marks = specs)))
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            fonts.importFont(uri).onSuccess { font -> updateFont(font.id) }
        }
    }

    // ------------------------------------------------------------------
    // Gestures (drag does not spam history)
    // ------------------------------------------------------------------

    private var dragStarted = false

    fun dragStart() {
        dragStarted = true
    }

    fun dragMove(dxDoc: Float, dyDoc: Float) {
        val id = _selectedLayerId.value ?: return
        _document.value = _document.value.copy(
            layers = _document.value.layers.map { layer ->
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
            },
        )
    }

    fun dragEnd() {
        if (dragStarted) {
            val after = _document.value
            if (after != history.current) {
                _document.value = history.push(after, "جابه‌جایی لایه")
                refreshHistoryFlags()
            }
        }
        dragStarted = false
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun selectedText(): String? =
        _document.value.findLayer(_selectedLayerId.value)?.text?.text

    private fun updateSelectedLayer(
        label: String,
        coalesceKey: String? = null,
        edit: (Layer) -> Layer,
    ) {
        val id = _selectedLayerId.value ?: return
        commit(label, coalesceKey) { doc ->
            doc.copy(layers = doc.layers.map { if (it.id == id) edit(it) else it })
        }
    }

    private fun updateSelectedText(
        label: String,
        coalesceKey: String? = null,
        edit: (TextContent) -> TextContent,
    ) {
        updateSelectedLayer(label, coalesceKey) { layer ->
            layer.text?.let { layer.copy(text = edit(it)) } ?: layer
        }
    }

    private fun PelcDocument.mapTextLayer(layerId: String, edit: (TextContent) -> TextContent): PelcDocument =
        copy(layers = layers.map { layer ->
            if (layer.id == layerId) layer.copy(text = layer.text?.let(edit) ?: layer.text) else layer
        })

    private fun refreshLetters() {
        val layer = _document.value.findLayer(_selectedLayerId.value)
        val text = layer?.text
        if (text == null) {
            _letters.value = emptyList()
            return
        }
        // چیپ‌های تب «حرف»: تطویل به‌عنوان حرف نمایش داده نمی‌شود
        // (تعدادش در [LetterUi.kashidaCount] حرف بعدی می‌آید)
        _letters.value = letterClusters(text.text)
            .filter { it.base != TATWEEL.toString() }
            .map { cluster: LetterCluster ->
                LetterUi(
                    start = cluster.start,
                    end = cluster.endExclusive,
                    label = clusterDisplayLabel(cluster),
                    markCount = cluster.marks.size,
                    kashidaCount = cluster.kashidaBefore,
                )
            }
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
            _placed.value = placed
            _layerBounds.value = placed.associate { it.layerId to it.bounds }
            val bitmap = renderer.renderBitmap(doc, PREVIEW_WIDTH, precomputed = placed)
            _preview.value = bitmap.asImageBitmap()
        }
    }

    private companion object {
        const val PREVIEW_WIDTH = 1080
        const val PREVIEW_DEBOUNCE_MS = 60L
        const val MARK_LIMIT_MIN = -400f
        const val MARK_LIMIT_MAX = 400f
        const val MARK_SCALE_MIN = 0.4f
        const val MARK_SCALE_MAX = 2.5f
    }
}
