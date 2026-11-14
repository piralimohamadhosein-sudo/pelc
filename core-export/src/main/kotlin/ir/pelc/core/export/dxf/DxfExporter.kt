package ir.pelc.core.export.dxf

import ir.pelc.core.model.PelcDocument
import ir.pelc.core.shaping.TextLayouter
import java.io.OutputStream

class DxfExporter(private val layouter: TextLayouter) {

    suspend fun export(document: PelcDocument, out: OutputStream) {
        val placed = layouter.layout(document)
        val dxf = DxfWriter().write(document, placed)
        out.write(dxf.toByteArray(Charsets.US_ASCII))
        out.flush()
    }
}
