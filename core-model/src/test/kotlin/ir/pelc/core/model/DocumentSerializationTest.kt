package ir.pelc.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSerializationTest {

    private val document = PelcDocument(
        title = "بسم الله الرحمن الرحیم",
        canvas = CanvasSpec(width = 1200, height = 800, dpi = 300),
        background = Background(color = 0xFFF5F0E6L, transparent = false),
        layers = listOf(
            Layer(
                name = "متن اصلی",
                kind = LayerKind.TEXT,
                transform = Transform(tx = 100f, ty = 200f, rotationDeg = 15f),
                style = StyleSpec(fillMode = FillMode.GOLD, shadow = ShadowSpec(radius = 6f)),
                text = TextContent(
                    text = "به نام خداوند جان و خرد",
                    fontId = "amiri",
                    fontSize = 96f,
                    perLetter = mapOf(
                        0 to LetterOverride(fontId = "reemkufi", dx = 4f, scale = 1.2f),
                        3 to LetterOverride(variantTag = "ss02"),
                    ),
                ),
            ),
            Layer(
                name = "قاب تذهیب",
                kind = LayerKind.ORNAMENT,
                ornament = OrnamentSpec(shape = OrnamentShape.FRAME, color = 0xFFD4AF37L),
            ),
        ),
    )

    @Test
    fun `round trip keeps every field`() {
        val json = PelcJson.encode(document)
        val restored = PelcJson.decode(json)

        assertEquals(document, restored)
        assertEquals("بسم الله الرحمن الرحیم", restored.title)
        assertEquals(2, restored.layers.size)
    }

    @Test
    fun `per letter overrides survive serialization`() {
        val restored = PelcJson.decode(PelcJson.encode(document))
        val text = restored.layers[0].text!!

        assertEquals(2, text.perLetter.size)
        assertEquals("reemkufi", text.perLetter[0]?.fontId)
        assertEquals(1.2f, text.perLetter[0]?.scale ?: 0f, 0.0001f)
        assertEquals("ss02", text.perLetter[3]?.variantTag)
    }

    @Test
    fun `unknown fields from newer versions are ignored`() {
        val json = PelcJson.encode(document).replace(
            "\"schemaVersion\": 1",
            "\"schemaVersion\": 2,\n    \"futureField\": 42",
        )

        val restored = PelcJson.decode(json)
        assertEquals(document.title, restored.title)
        assertTrue(restored.layers.isNotEmpty())
    }
}
