package cl.aiep.subtitulos.export

/**
 * Builds the "crudo" (raw) study markdown that wraps the original transcript.
 * Shared by the PDF (HTML) and Word (.docx) exporters so both formats start
 * from the same source. The leading `# $sessionName` is rendered as the
 * document cover/title block by each exporter, so renderers drop it from the body.
 */
object StudyMarkdown {
    fun raw(sessionName: String, transcriptMarkdown: String): String = buildString {
        appendLine("# $sessionName")
        appendLine()
        appendLine("## Transcripción cruda")
        appendLine()
        appendLine("Documento con la bitácora original de la clase, con marcas de tiempo.")
        appendLine()
        append(transcriptMarkdown.ifBlank { "No hay transcripción disponible." })
    }
}
