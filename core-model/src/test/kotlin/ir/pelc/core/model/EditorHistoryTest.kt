package ir.pelc.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * معیار پذیرش پروژه: تاریخچه باید ۵۰۰ عمل متوالی را بی‌خطا پشتیبانی کند.
 * ادغام گام‌های پیاپی، پرش به هر نقطه و بریده‌شدن با ظرفیت هم اینجا پوشش داده می‌شود.
 */
class EditorHistoryTest {

    private var now = 1_000_000L
    private val clock = { now }

    /** سند با شناسهٔ قطعی تا تساوی سندها قابل ارزیابی باشد */
    private fun doc(mark: Int): PelcDocument =
        PelcDocument(id = "pelc-doc", title = "نسخهٔ $mark")

    // ------------------------------------------------------------------
    // ۵۰۰ عمل
    // ------------------------------------------------------------------

    @Test
    fun `500 consecutive operations undo and redo fully`() {
        val history = EditorHistory(doc(0), clock = clock)
        assertEquals(0, history.position)

        repeat(500) { i -> history.apply(doc(i + 1), "ویرایش ${i + 1}") }
        assertEquals(500, history.stepCount)
        assertEquals("نسخهٔ 500", history.current.title)

        repeat(500) { history.undo() }
        assertEquals(0, history.position)
        assertEquals(doc(0), history.current)
        assertFalse(history.canUndo)

        repeat(500) { history.redo() }
        assertEquals("نسخهٔ 500", history.current.title)
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo returns previous state and redo returns it back`() {
        val history = EditorHistory(doc(0), clock = clock)
        history.apply(doc(1), "گام یک")
        history.apply(doc(2), "گام دو")

        assertEquals(doc(1), history.undo())
        assertEquals(doc(2), history.redo())
        assertNull(history.redo())
    }

    // ------------------------------------------------------------------
    // ادغام گام‌های پیاپی
    // ------------------------------------------------------------------

    @Test
    fun `typing bursts coalesce into a single step`() {
        val history = EditorHistory(doc(0), clock = clock)

        now = 10_000
        history.apply(doc(1), "ویرایش متن", coalesceKey = "text:l1")
        now = 10_500
        history.apply(doc(2), "ویرایش متن", coalesceKey = "text:l1")
        now = 11_000
        history.apply(doc(3), "ویرایش متن", coalesceKey = "text:l1")
        assertEquals(1, history.stepCount)
        assertEquals(doc(3), history.current)

        // پس از گذر پنجرهٔ زمانی، گام تازه ساخته می‌شود
        now = 20_000
        history.apply(doc(4), "ویرایش متن", coalesceKey = "text:l1")
        assertEquals(2, history.stepCount)
    }

    @Test
    fun `different coalesce keys never merge`() {
        val history = EditorHistory(doc(0), clock = clock)
        now = 1_000
        history.apply(doc(1), "افزودن علامت", coalesceKey = "mark:l1:0:0")
        now = 1_100
        history.apply(doc(2), "افزودن علامت", coalesceKey = "mark:l1:0:1")
        assertEquals(2, history.stepCount)
    }

    // ------------------------------------------------------------------
    // پرش و ظرفیت
    // ------------------------------------------------------------------

    @Test
    fun `jump lands on any requested step`() {
        val history = EditorHistory(doc(0), clock = clock)
        repeat(10) { i -> history.apply(doc(i + 1), "گام ${i + 1}") }

        assertEquals(doc(5), history.jumpTo(4))
        assertEquals(doc(0), history.jumpTo(-1))
        assertEquals(doc(10), history.jumpTo(9))
    }

    @Test
    fun `capacity trims the oldest steps but keeps the way back to base`() {
        val history = EditorHistory(doc(0), capacity = 5, clock = clock)
        repeat(10) { i -> history.apply(doc(i + 1), "گام ${i + 1}") }

        assertEquals(5, history.stepCount)
        assertEquals(doc(10), history.current)

        repeat(5) { history.undo() }
        assertEquals(doc(0), history.current)
        assertFalse(history.canUndo)
    }

    @Test
    fun `new edit after undo truncates the redo tail`() {
        val history = EditorHistory(doc(0), clock = clock)
        history.apply(doc(1), "گام یک")
        history.apply(doc(2), "گام دو")
        history.undo()

        history.apply(doc(3), "گام تازه")
        assertFalse(history.canRedo)
        // گام یک + گام تازه (دنبالهٔ redo بریده شد)
        assertEquals(2, history.stepCount)
        assertEquals(listOf("گام یک", "گام تازه"), history.labels())
        assertEquals(doc(3), history.current)
    }

    @Test
    fun `explicit push records a drag as one step`() {
        val history = EditorHistory(doc(0), clock = clock)
        // کشیدن: ViewModel مستقیم سند را عوض می‌کند و در پایان push می‌زند
        history.apply(doc(1), "افزودن متن")
        val moved = doc(1).copy(title = "نسخهٔ جابه‌جاشده")
        history.push(moved, "جابه‌جایی لایه")

        assertEquals(moved, history.current)
        assertEquals(listOf("افزودن متن", "جابه‌جایی لایه"), history.labels())
        assertEquals(doc(1), history.undo())
        assertNotNull(history.redo())
    }

    @Test
    fun `labels expose Persian step names for the history sheet`() {
        val history = EditorHistory(doc(0), clock = clock)
        history.apply(doc(1), "افزودن کشیده")
        history.apply(doc(2), "افزودن علامت فتحه")

        assertEquals(listOf("افزودن کشیده", "افزودن علامت فتحه"), history.labels())
        assertEquals(2, history.position)
    }
}
