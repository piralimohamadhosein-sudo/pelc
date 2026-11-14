package ir.pelc.core.export.pdf

import android.graphics.pdf.PdfDocument
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.render.PelcRenderer
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Vector PDF export: the page canvas of PdfDocument records real path drawing
 * commands, so the output stays fully vector (no rasterization).
 */
class PdfExporter(private val renderer: PelcRenderer) {

    suspend fun export(document: PelcDocument, out: OutputStream) {
        val widthPt = (document.width * 72f / document.canvas.dpi).roundToInt().coerceAtLeast(1)
        val heightPt = (document.height * 72f / document.canvas.dpi).roundToInt().coerceAtLeast(1)

        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, PAGE_NUMBER).create()
            val page = pdf.startPage(pageInfo)
            renderer.renderToCanvas(page.canvas, document, widthPt)
            pdf.finishPage(page)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private companion object {
        const val PAGE_NUMBER = 1
    }
}
