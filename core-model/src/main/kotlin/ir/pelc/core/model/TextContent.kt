package ir.pelc.core.model

import kotlinx.serialization.Serializable

/**
 * Text content of a TEXT layer.
 *
 * [text] is shaped with HarfBuzz in [fontId]. Individual letters can be
 * overridden through [perLetter]: the key is the UTF-16 cluster index of the
 * letter inside [text] (as reported by HarfBuzz), which groups a base letter
 * with its following marks.
 */
@Serializable
data class TextContent(
    val text: String = "بسم الله",
    val fontId: String = "",
    val fontSize: Float = 120f,
    val lineHeight: Float = 1.4f,
    val rtl: Boolean = true,
    val color: Long = 0xFF1A1A1AL,
    val perLetter: Map<Int, LetterOverride> = emptyMap(),
)

/**
 * علامتِ چسبانِ یک حرف. نویسهٔ علامت در خودِ متن می‌نشیند (تا HarfBuzz با GPOS
 * جای اولیه را بگذارد) و [MarkSpec] تنظیمِ دستی کاربر روی همان علامت را نگه
 * می‌دارد: ترتیب عناصر این فهرست = ترتیب نویسه‌های علامت در کلاستر.
 */
enum class MarkType(
    val char: Char,
    val title: String,
    val above: Boolean,
) {
    FATHA('\u064E', "فتحه", true),
    DAMMA('\u064F', "ضمه", true),
    KASRA('\u0650', "کسره", false),
    SHADDA('\u0651', "تشدید", true),
    SUKUN('\u0652', "سکون", true),
    FATHATAN('\u064B', "تنوین فتح", true),
    DAMMATAN('\u064C', "تنوین ضم", true),
    KASRATAN('\u064D', "تنوین کسر", false),
}

@Serializable
data class MarkSpec(
    val type: MarkType = MarkType.FATHA,
    /** جابه‌جایی افقی بر حسب واحد سند */
    val dx: Float = 0f,
    /** جابه‌جایی عمودی بر حسب واحد سند (مثبت = پایین‌تر) */
    val dy: Float = 0f,
    /** مقیاس نسبی علامت نسبت به اندازهٔ پیش‌فرض */
    val scale: Float = 1f,
)

/**
 * Per-letter override. Everything is optional: only the fields that differ
 * from the layer defaults need to be set. [variantTag] is an OpenType feature
 * tag such as "ss01", "ss03", "salt", "cv02", "swsh" ...
 * [marks] تنظیم دستی علامت‌های همان حرف است (به ترتیب نویسه‌های علامت در متن).
 */
@Serializable
data class LetterOverride(
    val fontId: String? = null,
    val variantTag: String? = null,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val rotateDeg: Float = 0f,
    val color: Long? = null,
    val marks: List<MarkSpec> = emptyList(),
)

/** Metadata of a font available in the app (bundled or imported by the user). */
@Serializable
data class PelcFont(
    val id: String,
    val displayName: String,
    val style: String,
    val license: String,
    val file: String,
)
