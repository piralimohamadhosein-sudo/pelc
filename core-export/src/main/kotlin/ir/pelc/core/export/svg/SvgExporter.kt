package ir.pelc.core.export.svg

import ir.pelc.core.model.PelcDocument
import ir.pelc.core.shaping.TextLayouter
import java.io.OutputStream

class SvgExporter(private val layouter: TextLayouter) {

    suspend fun export(document: PelcDocument, out: OutputStream) {
        val placed = layouter.layout(document)
        val svg = SvgWriter().write(document, placed)
        out.write(svg.toByteArray(Charsets.UTF_8))
        out.flush()
    }
}
