package ir.pelc.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** علامت‌های چسبانِ هر حرف باید در سریال‌سازی .pelc سالم بمانند و سازگارِ رو به جلو باشند. */
class MarksSerializationTest {

    private val document = PelcDocument(
        title = "بَسم",
        layers = listOf(
            Layer(
                name = "علامت‌دار",
                kind = LayerKind.TEXT,
                text = TextContent(
                    text = "بَّسم",
                    fontId = "amiri",
                    perLetter = mapOf(
                        0 to LetterOverride(
                            fontId = "amiri",
                            marks = listOf(
                                MarkSpec(type = MarkType.FATHA, dy = -6f),
                                MarkSpec(type = MarkType.SHADDA, dy = -14f, scale = 1.2f),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `marks survive a round trip`() {
        val restored = PelcJson.decode(PelcJson.encode(document))
        val marks = restored.layers[0].text!!.perLetter[0]!!.marks

        assertEquals(2, marks.size)
        assertEquals(MarkType.FATHA, marks[0].type)
        assertEquals(-6f, marks[0].dy, 0.0001f)
        assertEquals(MarkType.SHADDA, marks[1].type)
        assertEquals(1.2f, marks[1].scale, 0.0001f)
    }

    @Test
    fun `documents saved before phase 1 decode with empty marks`() {
        val legacy = """
            {
              "schemaVersion": 1,
              "title": "سند قدیمی",
              "canvas": { "width": 1000, "height": 1000, "dpi": 300 },
              "background": { "color": 4294967295, "transparent": false },
              "layers": [
                {
                  "id": "l1",
                  "name": "متن",
                  "kind": "TEXT",
                  "visible": true,
                  "locked": false,
                  "opacity": 1.0,
                  "transform": { "tx": 0.0, "ty": 0.0, "scaleX": 1.0, "scaleY": 1.0, "rotationDeg": 0.0 },
                  "style": { "fillMode": "SOLID", "color": 4278190090, "strokeWidth": 0.0, "strokeColor": 4278878746 },
                  "text": {
                    "text": "بسم",
                    "fontId": "amiri",
                    "fontSize": 120.0,
                    "lineHeight": 1.4,
                    "rtl": true,
                    "color": 4278190090,
                    "perLetter": { "0": { "fontId": "katibeh" } }
                  }
                }
              ],
              "createdAtEpochMs": 0,
              "modifiedAtEpochMs": 0
            }
        """.trimIndent()

        val restored = PelcJson.decode(legacy)
        val perLetter = restored.layers[0].text!!.perLetter
        assertEquals("katibeh", perLetter[0]?.fontId)
        assertEquals(emptyList<MarkSpec>(), perLetter[0]?.marks)
    }
}
