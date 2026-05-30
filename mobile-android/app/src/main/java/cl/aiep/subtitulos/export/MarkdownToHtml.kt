package cl.aiep.subtitulos.export

/**
 * Minimal, dependency-free Markdown -> HTML body converter tuned for the study
 * material structure produced by [StudyMaterialPrompt] and [StudyMarkdown].
 *
 * Supports: `##`/`###` headings, `-`/`*` bullet lists, `1.` ordered lists,
 * `**bold**` inline, and plain paragraphs. The first top-level `# ` heading is
 * dropped because the document title is rendered in the branded cover, not the body.
 *
 * Output is the inner HTML that gets injected into the `{{BODY}}` placeholder of
 * `study_template.html`. Pure and JVM-testable.
 */
object MarkdownToHtml {
    fun render(markdown: String): String {
        val out = StringBuilder()
        var listType: String? = null // "ul" | "ol" | null
        var droppedTitle = false

        fun closeList() {
            if (listType != null) {
                out.append("</").append(listType).append(">\n")
                listType = null
            }
        }

        fun openList(type: String) {
            if (listType != type) {
                closeList()
                out.append("<").append(type).append(">\n")
                listType = type
            }
        }

        for (rawLine in markdown.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> closeList()

                line.startsWith("### ") -> {
                    closeList()
                    out.append("<h3>").append(inline(line.removePrefix("### ").trim())).append("</h3>\n")
                }

                line.startsWith("## ") -> {
                    closeList()
                    out.append("<h2>").append(inline(line.removePrefix("## ").trim())).append("</h2>\n")
                }

                line.startsWith("# ") -> {
                    closeList()
                    if (!droppedTitle) {
                        droppedTitle = true // title lives in the cover; drop the first H1
                    } else {
                        out.append("<h1>").append(inline(line.removePrefix("# ").trim())).append("</h1>\n")
                    }
                }

                line.startsWith("- ") || line.startsWith("* ") -> {
                    openList("ul")
                    out.append("<li>").append(inline(line.drop(2).trim())).append("</li>\n")
                }

                ORDERED.containsMatchIn(line) -> {
                    openList("ol")
                    out.append("<li>").append(inline(line.replaceFirst(ORDERED, "").trim())).append("</li>\n")
                }

                else -> {
                    closeList()
                    out.append("<p>").append(inline(line)).append("</p>\n")
                }
            }
        }
        closeList()
        return out.toString().trim()
    }

    /** Escapes HTML special chars then applies `**bold**` inline formatting. */
    internal fun inline(text: String): String {
        val escaped = escape(text)
        return BOLD.replace(escaped) { "<strong>${it.groupValues[1]}</strong>" }
    }

    internal fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private val ORDERED = Regex("^\\d+\\.\\s+")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
}
