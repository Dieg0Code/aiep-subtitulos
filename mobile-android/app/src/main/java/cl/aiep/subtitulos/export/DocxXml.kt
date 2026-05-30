package cl.aiep.subtitulos.export

/**
 * Pure helpers that map study Markdown to WordprocessingML (OOXML) fragments.
 * No dependencies — the resulting strings are zipped into a `.docx` by
 * [StudyDocxGenerator]. Everything here is JVM-testable.
 */
object DocxXml {
    /** XML-escapes text for use inside elements and attributes. */
    fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    /**
     * Converts a single Markdown line to a `<w:p>` paragraph, or `""` for a
     * blank line. Headings map to Word heading styles; bullets/numbered lines
     * become indented list paragraphs.
     */
    fun paragraphForLine(line: String): String {
        val t = line.trim()
        return when {
            t.isEmpty() -> ""
            t.startsWith("### ") -> paragraph("Heading3", runs(t.removePrefix("### ").trim()))
            t.startsWith("## ") -> paragraph("Heading2", runs(t.removePrefix("## ").trim()))
            t.startsWith("# ") -> paragraph("Heading1", runs(t.removePrefix("# ").trim()))
            t.startsWith("- ") || t.startsWith("* ") -> bullet(runs(t.drop(2).trim()))
            ORDERED.containsMatchIn(t) -> bullet(runs(t)) // keep the original number prefix
            else -> paragraph(null, runs(t))
        }
    }

    /**
     * Builds the body paragraphs from full Markdown, dropping the first top-level
     * `# ` heading (the document title is rendered as a branded block instead).
     */
    fun body(markdown: String): String {
        val sb = StringBuilder()
        var droppedTitle = false
        for (rawLine in markdown.lineSequence()) {
            val t = rawLine.trim()
            if (!droppedTitle && t.startsWith("# ")) {
                droppedTitle = true
                continue
            }
            val p = paragraphForLine(t)
            if (p.isNotEmpty()) sb.append(p).append('\n')
        }
        return sb.toString().trim()
    }

    /** A run sequence for [text], splitting `**bold**` segments into bold runs. */
    fun runs(text: String): String {
        val segments = splitBold(text)
        val sb = StringBuilder()
        for ((seg, bold) in segments) {
            if (seg.isEmpty()) continue
            sb.append("<w:r>")
            if (bold) sb.append("<w:rPr><w:b/></w:rPr>")
            sb.append("<w:t xml:space=\"preserve\">").append(escape(seg)).append("</w:t>")
            sb.append("</w:r>")
        }
        return sb.toString()
    }

    private fun paragraph(styleId: String?, runsXml: String): String {
        val ppr = if (styleId != null) "<w:pPr><w:pStyle w:val=\"$styleId\"/></w:pPr>" else ""
        return "<w:p>$ppr$runsXml</w:p>"
    }

    private fun bullet(runsXml: String): String {
        val ppr = "<w:pPr><w:pStyle w:val=\"ListParagraph\"/>" +
            "<w:ind w:left=\"360\" w:hanging=\"180\"/></w:pPr>"
        val glyph = "<w:r><w:t xml:space=\"preserve\">•  </w:t></w:r>"
        return "<w:p>$ppr$glyph$runsXml</w:p>"
    }

    private fun splitBold(text: String): List<Pair<String, Boolean>> {
        val result = mutableListOf<Pair<String, Boolean>>()
        var last = 0
        for (m in BOLD.findAll(text)) {
            if (m.range.first > last) result.add(text.substring(last, m.range.first) to false)
            result.add(m.groupValues[1] to true)
            last = m.range.last + 1
        }
        if (last < text.length) result.add(text.substring(last) to false)
        if (result.isEmpty()) result.add(text to false)
        return result
    }

    private val ORDERED = Regex("^\\d+\\.\\s+")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
}
