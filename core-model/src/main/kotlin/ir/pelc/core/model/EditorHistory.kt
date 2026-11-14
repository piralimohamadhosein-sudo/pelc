package ir.pelc.core.model

/** یک گام تاریخچه: برچسب فارسی برای نمایش + وضعیت سندِ پس از آن گام */
data class HistoryStep(val label: String, val document: PelcDocument)

/**
 * تاریخچهٔ ویرایش سند بر پایهٔ عکس‌فوری کامل (مدل سند تغییرناپذیر است، پس عکس‌فوری
 * ارزان و بی‌خطاست) و «حالت آغازین + فهرست گام‌ها»:
 *
 *  - `undo` فقط مکان‌نما را عقب می‌برد؛ حتی با بریده‌شدن دنباله به‌خاطر ظرفیت،
 *    بازگشت تا سند آغازین همیشه ممکن است.
 *  - گام‌های پیاپی هم‌نوع (تایپ، جابه‌جایی علامت) با [apply] و کلید ادغام به یک گام
 *    تبدیل می‌شوند تا تاریخچه برای کاربر معنادار بماند.
 *  - کشیدن لایه روی بوم بدون نوشتن در تاریخچه پیش‌نمایش می‌شود و در پایان با
 *    [push] یک‌جا ثبت می‌گردد (بدون پرش تاریخ).
 *
 * باید ۵۰۰ عمل متوالی را بی‌خطا پشتیبانی کند (معیار پذیرش پروژه).
 */
class EditorHistory(
    initial: PelcDocument,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val base: PelcDocument = initial
    private val steps = ArrayList<HistoryStep>()
    private var cursor = -1

    private var lastCoalesceKey: String? = null
    private var lastCommitAt = 0L

    /** شمارندهٔ تغییرات برای مشاهده‌گرها */
    var version: Int = 0
        private set

    /** سند جاری بنا بر مکان‌نمای تاریخچه */
    val current: PelcDocument
        get() = if (cursor < 0) base else steps[cursor].document

    val canUndo: Boolean get() = cursor >= 0
    val canRedo: Boolean get() = cursor < steps.size - 1
    val stepCount: Int get() = steps.size

    /** ۰ یعنی سند آغازین، [stepCount] یعنی آخرین گام */
    val position: Int get() = cursor + 1

    /** برچسب گام‌ها به ترتیب قدیم به جدید (برای شیت تاریخچه) */
    fun labels(): List<String> = steps.map { it.label }

    /**
     * اعمال یک ویرایش. اگر [coalesceKey] داده شده و گام قبلی هم‌نوع و در بازهٔ
     * زمانی مجاور باشد، گام قبلی درجا به‌روز می‌شود (ادغام گام‌ها).
     */
    fun apply(
        next: PelcDocument,
        label: String,
        coalesceKey: String? = null,
        coalesceWindowMs: Long = DEFAULT_COALESCE_MS,
    ): PelcDocument {
        val now = clock()
        val coalesce = coalesceKey != null &&
            coalesceKey == lastCoalesceKey &&
            cursor >= 0 &&
            steps[cursor].label == label &&
            now - lastCommitAt <= coalesceWindowMs

        if (coalesce) {
            steps[cursor] = HistoryStep(label, next)
        } else {
            truncateRedo()
            steps += HistoryStep(label, next)
            if (steps.size > capacity) steps.removeAt(0)
            cursor = steps.size - 1
        }
        lastCoalesceKey = coalesceKey
        lastCommitAt = now
        version++
        return current
    }

    /**
     * ثبت صریح یک وضعیت (مثلاً پایان کشیدن لایه). وضعیت «قبل» همان [current]
     * جاری فرض می‌شود؛ در طول کشیدن، ViewModel مستقیم سند را به‌روز می‌کند و
     * تاریخچه دست‌نخورده می‌ماند.
     */
    fun push(next: PelcDocument, label: String): PelcDocument {
        truncateRedo()
        steps += HistoryStep(label, next)
        if (steps.size > capacity) steps.removeAt(0)
        cursor = steps.size - 1
        lastCoalesceKey = null
        version++
        return current
    }

    fun undo(): PelcDocument? {
        if (!canUndo) return null
        cursor--
        version++
        return current
    }

    fun redo(): PelcDocument? {
        if (!canRedo) return null
        cursor++
        version++
        return current
    }

    /** پرش به گام [index]؛ -۱ یعنی سند آغازین */
    fun jumpTo(index: Int): PelcDocument {
        require(index in -1..steps.lastIndex) { "step index out of range: $index" }
        cursor = index
        version++
        return current
    }

    private fun truncateRedo() {
        if (cursor < steps.size - 1) steps.subList(cursor + 1, steps.size).clear()
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val DEFAULT_COALESCE_MS = 1200L
    }
}
