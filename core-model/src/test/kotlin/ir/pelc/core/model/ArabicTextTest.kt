package ir.pelc.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های منطق کشیده، علامت‌ها و کلاسترهای حرف — معیارهای پذیرش فاز ۱:
 * کشیده فقط میان دو حرفِ متصل، شکستن «لا» ممنوع، نیم‌فاصله اتصال را می‌شکند و
 * بازنگاشت کلیدهای perLetter باید گونه/قلم-به-حرف را سر جایش نگه دارد.
 */
class ArabicTextTest {

    // ------------------------------------------------------------------
    // کلاسترها
    // ------------------------------------------------------------------

    @Test
    fun `plain letters split into single clusters`() {
        val clusters = letterClusters("بسم")
        assertEquals(3, clusters.size)
        assertEquals(listOf("ب", "س", "م"), clusters.map { it.base })
        assertEquals(listOf(0, 1, 2), clusters.map { it.start })
    }

    @Test
    fun `combining marks attach to their base letter`() {
        val text = "ب" + '\u064E' + "س" + '\u064B'
        val clusters = letterClusters(text)
        assertEquals(2, clusters.size)
        assertEquals("ب", clusters[0].base)
        assertEquals(listOf('\u064E'), clusters[0].marks)
        assertEquals("س", clusters[1].base)
        assertEquals(listOf('\u064B'), clusters[1].marks)
        // کلاستر س = شروع در ۲ + یک علامت → پایان انحصاری در ۴
        assertEquals(4, clusters[1].endExclusive)
    }

    @Test
    fun `kashida count is reported per cluster`() {
        val text = "ب" + TATWEEL + TATWEEL + "سم"
        val clusters = letterClusters(text)
        // تطویل خوشهٔ مستقل خودش است: ب، ـ، ـ، س، م
        assertEquals(5, clusters.size)
        assertEquals(TATWEEL.toString(), clusters[1].base)
        assertEquals(2, clusters[3].kashidaBefore) // پیش از س
        assertEquals(0, clusters[0].kashidaBefore)
    }

    // ------------------------------------------------------------------
    // قانون اتصال برای کشیده
    // ------------------------------------------------------------------

    @Test
    fun `kashida is legal between two joined letters`() {
        val text = "بسم"
        assertTrue(canKashidaBefore(text, 1)) // ب + س
        assertTrue(canKashidaBefore(text, 2)) // س + م
    }

    @Test
    fun `kashida is illegal at word start`() {
        assertFalse(canKashidaBefore("بسم", 0))
    }

    @Test
    fun `kashida is illegal when previous letter is right-joining`() {
        // در «کمال» میان «ا» و «ل» اتصالی از سمت «ا» وجود ندارد
        assertFalse(canKashidaBefore("کمال", 3))
        // ولی میان «م» و «ا» اتصال هست
        assertTrue(canKashidaBefore("کمال", 2))
    }

    @Test
    fun `kashida must not break the lam-alef ligature`() {
        assertFalse(canKashidaBefore("لا", 1))
        assertFalse(canKashidaBefore("خلا", 2)) // ل + ا در «خلا»
        assertTrue(canKashidaBefore("خلا", 1))  // خ + ل کشیده می‌پذیرد
    }

    @Test
    fun `ZWNJ and space block the join`() {
        val withZwnj = "می" + ZWNJ + "خواهم"
        assertFalse(canKashidaBefore(withZwnj, 3)) // بعد از نیم‌فاصله
        assertFalse(canKashidaBefore("علی رضا", 4)) // بعد از فاصله
    }

    @Test
    fun `kashida stacks on top of existing kashida`() {
        val text = "بـسم"
        assertTrue(canKashidaBefore(text, 2)) // تطویل دوجهته است؛ کشیده دوم مجاز
    }

    // ------------------------------------------------------------------
    // درج/حذف کشیده + بازنگاشت perLetter
    // ------------------------------------------------------------------

    @Test
    fun `inserting kashida keeps per letter overrides aligned`() {
        val text = "بسم"
        val perLetter = mapOf(
            0 to LetterOverride(fontId = "amiri"),
            2 to LetterOverride(variantTag = "ss01"),
        )
        val mutation = insertKashidaBefore(text, 1)!! // «بـسم»

        assertEquals("بـسم", mutation.newText)
        val remapped = remapPerLetter(perLetter, mutation)
        assertEquals("amiri", remapped[0]?.fontId)          // ب دست‌نخورده
        assertEquals("ss01", remapped[3]?.variantTag)       // س از ۲ به ۳ رفت
        assertNull(remapped[2])
    }

    @Test
    fun `removing kashida restores previous indices`() {
        val text = "بـسم"
        val perLetter = mapOf(3 to LetterOverride(fontId = "katibeh"))
        val mutation = removeKashidaBefore(text, 2)!! // «بسم»

        assertEquals("بسم", mutation.newText)
        val remapped = remapPerLetter(perLetter, mutation)
        assertEquals("katibeh", remapped[2]?.fontId)
    }

    @Test
    fun `remove kashida is null when there is none`() {
        assertNull(removeKashidaBefore("بسم", 2))
    }

    @Test
    fun `insert kashida is null where join is illegal`() {
        assertNull(insertKashidaBefore("سلام", 3)) // میان ا و م
        assertNull(insertKashidaBefore("لا", 1))   // لام‌الف
    }

    // ------------------------------------------------------------------
    // درج/حذف علامت + همگام‌سازی
    // ------------------------------------------------------------------

    @Test
    fun `inserting a mark appends it after the letter`() {
        val text = "بسم"
        val mutation = insertMarkAfter(text, 0, MarkType.FATHA.char)!!

        assertEquals("ب" + '\u064E' + "سم", mutation.newText)
        val cluster = letterClusters(mutation.newText)[0]
        assertEquals(listOf('\u064E'), cluster.marks)
        // کلیدهای حروف بعدی یک واحد جابه‌جا می‌شوند
        assertEquals(4, mutation.remap(3))
        assertEquals(0, mutation.remap(0))
    }

    @Test
    fun `second mark is appended after the first`() {
        var text = "بسم"
        text = insertMarkAfter(text, 0, MarkType.FATHA.char)!!.newText
        text = insertMarkAfter(text, 0, MarkType.SHADDA.char)!!.newText

        assertEquals("ب" + '\u064E' + '\u0651' + "سم", text)
        assertEquals(listOf('\u064E', '\u0651'), letterClusters(text)[0].marks)
    }

    @Test
    fun `removing a mark by index is precise`() {
        var text = "بسم"
        text = insertMarkAfter(text, 0, MarkType.FATHA.char)!!.newText
        text = insertMarkAfter(text, 0, MarkType.SHADDA.char)!!.newText

        val mutation = removeMarkAt(text, 0, 0)!! // حذف فتحه، تشدید می‌ماند
        assertEquals("ب" + '\u0651' + "سم", mutation.newText)
        assertEquals(listOf('\u0651'), letterClusters(mutation.newText)[0].marks)
        assertNull(removeMarkAt(mutation.newText, 0, 1)) // فقط یک علامت مانده
    }

    @Test
    fun `marks list is capped to actual text marks after typing`() {
        val text = "ب" + '\u064E'
        val perLetter = mapOf(
            0 to LetterOverride(marks = listOf(MarkSpec(MarkType.FATHA), MarkSpec(MarkType.SHADDA))),
            5 to LetterOverride(fontId = "gone"), // کلید بیرون از متن
        )
        val reconciled = reconcilePerLetter(text, perLetter)

        assertEquals(setOf(0), reconciled.keys)
        assertEquals(1, reconciled[0]?.marks?.size)
        assertEquals(MarkType.FATHA, reconciled[0]?.marks?.first()?.type)
    }

    @Test
    fun `joining classification covers Persian core letters`() {
        assertTrue(joinsForward('ب'))
        assertTrue(joinsBackward('ا'))
        assertFalse(joinsForward('ا'))
        assertFalse(joinsBackward('ء'))  // همزه هیچ‌وقت نمی‌چسبد
        assertFalse(joinsForward('ژ'))   // ژ فقط به قبلی می‌چسبد
        assertTrue(joinsBackward('ژ'))
    }
}
