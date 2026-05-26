package cl.aiep.subtitulos.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudyPdfGenerator(private val context: Context) {
    fun generateRaw(sessionName: String, transcriptMarkdown: String): File =
        generate(
            sessionName = sessionName,
            markdown = buildString {
                appendLine("# $sessionName")
                appendLine()
                appendLine("## Transcripcion cruda")
                appendLine()
                appendLine("Este PDF contiene la bitacora original de la clase, con marcas de tiempo.")
                appendLine()
                append(transcriptMarkdown.ifBlank { "No hay transcripcion disponible." })
            },
        )

    fun generate(sessionName: String, markdown: String): File {
        val dir = File(context.cacheDir, "study-pdfs").apply { mkdirs() }
        val file = File(dir, StudyPdfFileName.build(sessionName))
        val document = PdfDocument()
        val writer = PdfWriter(document)

        writer.drawTitle("AIEP Subtítulos")
        writer.drawMuted(sessionName)
        writer.drawMuted(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date()))
        writer.space(18f)

        markdown.lineSequence().forEach { line ->
            writer.drawMarkdownLine(line)
        }

        writer.close()
        file.outputStream().use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    private class PdfWriter(private val document: PdfDocument) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 42f
        private val contentWidth = pageWidth - margin * 2
        private var pageNumber = 0
        private var page: PdfDocument.Page = newPage()
        private var canvas: Canvas = page.canvas
        private var y = margin

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 59, 112)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 59, 112)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(23, 32, 38)
            textSize = 11.5f
        }
        private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(98, 112, 128)
            textSize = 10f
        }

        fun drawTitle(text: String) = drawWrapped(text, titlePaint, 28f)
        fun drawMuted(text: String) = drawWrapped(text, mutedPaint, 14f)
        fun space(amount: Float) {
            y += amount
        }

        fun drawMarkdownLine(raw: String) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> space(8f)
                line.startsWith("# ") -> drawWrapped(line.removePrefix("# ").trim(), titlePaint, 28f)
                line.startsWith("## ") -> {
                    space(8f)
                    drawWrapped(line.removePrefix("## ").trim(), headingPaint, 20f)
                }
                line.startsWith("- ") -> drawWrapped("• ${line.removePrefix("- ").trim()}", bodyPaint, 16f)
                Regex("^\\d+\\.\\s+").containsMatchIn(line) -> drawWrapped(line, bodyPaint, 16f)
                else -> drawWrapped(line, bodyPaint, 16f)
            }
        }

        private fun drawWrapped(text: String, paint: Paint, lineHeight: Float) {
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return
            var current = ""
            for (word in words) {
                val next = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(next) <= contentWidth) {
                    current = next
                } else {
                    drawLine(current, paint, lineHeight)
                    current = word
                }
            }
            if (current.isNotBlank()) drawLine(current, paint, lineHeight)
            if (paint == bodyPaint) space(3f)
        }

        private fun drawLine(text: String, paint: Paint, lineHeight: Float) {
            ensureSpace(lineHeight)
            canvas.drawText(text, margin, y, paint)
            y += lineHeight
        }

        private fun ensureSpace(required: Float) {
            if (y + required < pageHeight - margin) return
            finishPage()
            page = newPage()
            canvas = page.canvas
            y = margin
        }

        private fun newPage(): PdfDocument.Page {
            pageNumber += 1
            return document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        }

        private fun finishPage() {
            document.finishPage(page)
        }

        fun close() {
            finishPage()
        }
    }
}
