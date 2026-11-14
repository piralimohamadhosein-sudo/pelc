package ir.pelc.core.model

/**
 * ابزارهای متنی فارسی/عربی ویرایشگر خوشنویسی — کاملاً مستقل از پلتفرم و قابل تست روی JVM.
 *
 * سه مسئولیت:
 *  ۱. دسته‌بندی اتصال حروف (دوجهته / فقط-به-قبلی / بدون اتصال) برای تعیین نقاط مجاز «کشیده».
 *  ۲. مدل «کلاستر حرف»: حرف پایه + علائم چسبان همان حرف (فتحه، ضمه، تشدید، ...).
 *  ۳. جهش‌های متن (درج/حذف کشیده و علامت) همراه با بازنگاشت خودکار کلیدهای
 *     [TextContent.perLetter] — کلیدها اندیس UTF-16 آغازِ کلاستر حرف در متن‌اند و
 *     پس از هر درج/حذف باید جابه‌جا شوند تا گونه/قلم-به-حرف سر جایش بماند.
 *
 * یادداشت طراحی: کشیده به‌صورت نویسهٔ تَطویل (U+0640) در خودِ متن درج می‌شود، نه
 * به‌صورت ساختار جدا؛ نتیجه: شکل‌دهی کاملاً بر عهدهٔ HarfBuzz می‌ماند و اتصال حروف
 * اطراف کشیده هرگز نمی‌شکند. علامت‌ها هم به‌صورت نویسهٔ چسبان بعد از حرف پایه درج
 * می‌شوند تا جای‌گذاری اولیه با GPOS خود فونت درست باشد؛ تنظیم دقیق دستیِ هر علامت
 * در [LetterOverride.marks] نگه‌داری می‌شود.
 */

/** نویسهٔ کشیده (تطویل) */
const val TATWEEL: Char = '\u0640'

/** نیم‌فاصله */
const val ZWNJ: Char = '\u200C'

/** بیشینهٔ تعداد کشیده پشت‌سرهم در یک نقطه */
const val MAX_KASHIDA_BEFORE: Int = 8

/** علائم چسبانِ مجاز در ویرایشگر (فتحه، ضمه، کسره، تشدید، سکون، تنوین‌ها، مد، همزه) */
val COMBINING_MARKS: Set<Char> = setOf(
    '\u064B', '\u064C', '\u064D', '\u064E', '\u064F',
    '\u0650', '\u0651', '\u0652', '\u0653', '\u0654', '\u0655', '\u0670',
)

/** حروفی که از هر دو طرف می‌چسبند (دوجهته) — تطویل هم دوجهته است */
private val DUAL_JOINING: Set<Char> = setOf(
    'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ع', 'غ',
    'ف', 'ق', 'ك', 'ک', 'ل', 'م', 'ن', 'ه', 'ي', 'ی', 'ئ', 'پ', 'چ', 'گ', 'ى',
    TATWEEL,
)

/** حروفی که فقط به حرفِ قبل می‌چسبند (همچون ا د ذ ر ز ژ و) */
private val RIGHT_JOINING: Set<Char> = setOf(
    'ا', 'آ', 'أ', 'إ', 'ؤ', 'ة', 'د', 'ذ', 'ر', 'ز', 'ژ', 'و', 'ۀ',
)

/** آیا [c] به حرف بعدی می‌چسبد؟ */
fun joinsForward(c: Char): Boolean = c in DUAL_JOINING

/** آیا [c] به حرف قبلی می‌چسبد؟ */
fun joinsBackward(c: Char): Boolean = c in DUAL_JOINING || c in RIGHT_JOINING

/**
 * یک کلاستر حرف در متن: حرف پایه + علائم چسبانِ بلافاصله بعد از آن.
 * [start] اندیس UTF-16 حرف پایه و [endExclusive] اندیس یک-پس از آخرین علامت است؛
 * [kashidaBefore] تعداد تطویلِ بلافاصله قبل از این کلاستر است.
 */
data class LetterCluster(
    val start: Int,
    val endExclusive: Int,
    val base: String,
    val marks: List<Char>,
    val kashidaBefore: Int,
)

/** تجزیهٔ [text] به کلاسترهای حرف (حرف پایه + علامت‌هایش) */
fun letterClusters(text: String): List<LetterCluster> {
    val out = ArrayList<LetterCluster>()
    var i = 0
    while (i < text.length) {
        val start = i
        val baseLen = Character.charCount(text.codePointAt(i))
        i += baseLen
        val marks = ArrayList<Char>()
        while (i < text.length && text[i] in COMBINING_MARKS) {
            marks += text[i]
            i++
        }
        var kashida = 0
        while (start - 1 - kashida >= 0 && text[start - 1 - kashida] == TATWEEL) kashida++
        out += LetterCluster(
            start = start,
            endExclusive = i,
            base = text.substring(start, start + baseLen),
            marks = marks,
            kashidaBefore = kashida,
        )
    }
    return out
}

/**
 * آیا می‌توان پیش از کلاسترِ آغازشده در [clusterStart] کشیده درج کرد؟
 * قانون: حرف قبلی از جلو بچسبد، حرف فعلی از عقب بچسبد، میان‌شان نیم‌فاصله/فاصله
 * نباشد و جفت «لام + الف» (لیگاتور اجباری لا) را نشکند.
 */
fun canKashidaBefore(text: String, clusterStart: Int): Boolean {
    val clusters = letterClusters(text)
    val idx = clusters.indexOfFirst { it.start == clusterStart }
    if (idx <= 0) return false
    val prev = clusters[idx - 1]
    val cur = clusters[idx]
    if (prev.endExclusive != cur.start) return false
    if (prev.base.length != 1 || cur.base.length != 1) return false
    val p = prev.base[0]
    val c = cur.base[0]
    if (!joinsForward(p) || !joinsBackward(c)) return false
    if (p == 'ل' && c in "اأإآ") return false
    return true
}

/**
 * یک جهش متن: متنِ تازه + نگاشت کلیدهای perLetter.
 * هر کلید >= [shiftFrom] به اندازهٔ [delta] جابه‌جا می‌شود؛ چون همهٔ جهش‌های این
 * فایل «درج/حذف تک‌نقطه‌ای»اند، این نگاشت همیشه یک‌به‌یک می‌ماند.
 */
class TextMutation internal constructor(
    val newText: String,
    private val shiftFrom: Int,
    private val delta: Int,
) {
    /** بازنگاشت اندیس کلاستر پس از این جهش */
    fun remap(key: Int): Int = if (key >= shiftFrom) key + delta else key
}

/** اعمال بازنگاشت [TextMutation] روی کل نقشهٔ perLetter */
fun remapPerLetter(perLetter: Map<Int, LetterOverride>, mutation: TextMutation): Map<Int, LetterOverride> {
    if (perLetter.isEmpty()) return perLetter
    val out = HashMap<Int, LetterOverride>(perLetter.size)
    for ((key, value) in perLetter) out[mutation.remap(key)] = value
    return out
}

/** درج یک کشیده پیش از کلاستر [clusterStart] (اگر قانون اتصال اجازه دهد) */
fun insertKashidaBefore(text: String, clusterStart: Int): TextMutation? {
    if (clusterStart !in 1..text.length) return null
    if (!canKashidaBefore(text, clusterStart)) return null
    return TextMutation(
        text.substring(0, clusterStart) + TATWEEL + text.substring(clusterStart),
        clusterStart,
        +1,
    )
}

/** حذف یک کشیده از پیشِ کلاستر [clusterStart] */
fun removeKashidaBefore(text: String, clusterStart: Int): TextMutation? {
    if (clusterStart !in 1..text.length) return null
    if (text[clusterStart - 1] != TATWEEL) return null
    return TextMutation(
        text.substring(0, clusterStart - 1) + text.substring(clusterStart),
        clusterStart,
        -1,
    )
}

/** درج علامت [mark] در انتهای کلاستر [clusterStart] (بعد از علامت‌های موجود) */
fun insertMarkAfter(text: String, clusterStart: Int, mark: Char): TextMutation? {
    if (mark !in COMBINING_MARKS) return null
    val cluster = letterClusters(text).firstOrNull { it.start == clusterStart } ?: return null
    return TextMutation(
        text.substring(0, cluster.endExclusive) + mark + text.substring(cluster.endExclusive),
        cluster.endExclusive,
        +1,
    )
}

/** حذف علامت شمارهٔ [markIndex] از کلاستر [clusterStart] */
fun removeMarkAt(text: String, clusterStart: Int, markIndex: Int): TextMutation? {
    val cluster = letterClusters(text).firstOrNull { it.start == clusterStart } ?: return null
    if (markIndex !in cluster.marks.indices) return null
    val position = cluster.start + cluster.base.length + markIndex
    val removed = text.getOrNull(position) ?: return null
    if (removed !in COMBINING_MARKS) return null
    return TextMutation(
        text.substring(0, position) + text.substring(position + 1),
        position + 1,
        -1,
    )
}

/**
 * همگام‌سازی perLetter با متن تازه (بعد از تایپ آزاد کاربر):
 * کلیدهای بی‌کلاستر حذف و فهرست علامت هر حرف به تعداد علائم واقعی متن بریده می‌شود.
 */
fun reconcilePerLetter(text: String, perLetter: Map<Int, LetterOverride>): Map<Int, LetterOverride> {
    if (perLetter.isEmpty()) return perLetter
    val clusters = letterClusters(text).associateBy { it.start }
    val out = HashMap<Int, LetterOverride>()
    for ((key, override) in perLetter) {
        val cluster = clusters[key] ?: continue
        val trimmed = override.copy(marks = override.marks.take(cluster.marks.size))
        if (trimmed != LetterOverride()) out[key] = trimmed
    }
    return out
}

/** برچسب نمایشی یک کلاستر برای چیپ‌های تب «حرف» */
fun clusterDisplayLabel(cluster: LetterCluster): String {
    val base = when (cluster.base) {
        ZWNJ.toString() -> "نیم‌فاصله"
        " " -> "فاصله"
        "\n" -> "شکست خط"
        else -> cluster.base
    }
    return base + cluster.marks.joinToString("")
}
