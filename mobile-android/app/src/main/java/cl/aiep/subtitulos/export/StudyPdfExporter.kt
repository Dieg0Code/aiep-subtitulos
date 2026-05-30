package cl.aiep.subtitulos.export

import android.content.Context
import java.io.File

/**
 * Builds the branded study PDF from Markdown by rendering `study_template.html`
 * (the single source of professional styling) through [HtmlPdfRenderer].
 * Mirrors the old Canvas generator's API so callers stay simple.
 */
class StudyPdfExporter(private val context: Context) {
    suspend fun generate(sessionName: String, studyMarkdown: String): File =
        export(sessionName, studyMarkdown, StudyVariant.Ai)

    suspend fun generateRaw(sessionName: String, transcriptMarkdown: String): File =
        export(sessionName, StudyMarkdown.raw(sessionName, transcriptMarkdown), StudyVariant.Raw)

    private suspend fun export(sessionName: String, markdown: String, variant: StudyVariant): File {
        val bodyHtml = MarkdownToHtml.render(markdown)
        val html = StudyHtmlTemplate.build(context, sessionName, bodyHtml, variant)
        val dir = File(context.cacheDir, "study-pdfs").apply { mkdirs() }
        val file = File(dir, StudyPdfFileName.build(sessionName, extension = "pdf"))
        return HtmlPdfRenderer.render(context, html, file)
    }
}
