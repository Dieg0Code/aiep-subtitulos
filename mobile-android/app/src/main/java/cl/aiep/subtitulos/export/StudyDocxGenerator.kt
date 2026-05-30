package cl.aiep.subtitulos.export

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates a Word (.docx) document by hand-building the OpenXML (OOXML) package
 * as a ZIP — no external dependencies. Opens identically in Word and Google Docs.
 * Visually aligned with the PDF: embedded AIEP logo, navy headings, red rule,
 * branded footer with page numbers.
 */
class StudyDocxGenerator(private val context: Context) {
    fun generate(sessionName: String, studyMarkdown: String): File =
        build(sessionName, studyMarkdown, StudyVariant.Ai)

    fun generateRaw(sessionName: String, transcriptMarkdown: String): File =
        build(sessionName, StudyMarkdown.raw(sessionName, transcriptMarkdown), StudyVariant.Raw)

    private fun build(sessionName: String, markdown: String, variant: StudyVariant): File {
        val dir = File(context.cacheDir, "study-docs").apply { mkdirs() }
        val file = File(dir, StudyPdfFileName.build(sessionName, extension = "docx"))
        val logo = BrandAssets.logoBytes(context)

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(hasLogo = logo != null))
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("word/_rels/document.xml.rels", documentRels(hasLogo = logo != null))
            zip.put("word/styles.xml", STYLES)
            zip.put("word/footer1.xml", FOOTER)
            zip.put("word/document.xml", document(sessionName, markdown, variant, hasLogo = logo != null))
            if (logo != null) {
                zip.putNextEntry(ZipEntry("word/media/logo_aiep.png"))
                zip.write(logo)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun document(
        sessionName: String,
        markdown: String,
        variant: StudyVariant,
        hasLogo: Boolean,
    ): String {
        val title = DocxXml.escape(sessionName)
        val badge = when (variant) {
            StudyVariant.Ai -> "MATERIAL DE ESTUDIO · IA"
            StudyVariant.Raw -> "TRANSCRIPCIÓN CRUDA"
        }
        val brand = if (hasLogo) LOGO_PARAGRAPH else WORDMARK_PARAGRAPH
        val redRule =
            "<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"18\" w:space=\"4\" " +
                "w:color=\"D71920\"/></w:pBdr><w:spacing w:after=\"160\"/></w:pPr></w:p>"
        val badgeParagraph =
            "<w:p><w:pPr><w:spacing w:after=\"40\"/></w:pPr><w:r><w:rPr><w:b/>" +
                "<w:color w:val=\"D71920\"/><w:sz w:val=\"16\"/></w:rPr>" +
                "<w:t xml:space=\"preserve\">${DocxXml.escape(badge)}</w:t></w:r></w:p>"
        val titleParagraph =
            "<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr><w:r><w:t xml:space=\"preserve\">" +
                "$title</w:t></w:r></w:p>"

        return DOCUMENT_OPEN +
            brand +
            redRule +
            badgeParagraph +
            titleParagraph +
            DocxXml.body(markdown) +
            SECT_PR +
            DOCUMENT_CLOSE
    }

    private fun contentTypes(hasLogo: Boolean): String {
        val png = if (hasLogo) "<Default Extension=\"png\" ContentType=\"image/png\"/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
$png
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
</Types>"""
    }

    private fun documentRels(hasLogo: Boolean): String {
        val image = if (hasLogo) {
            "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/logo_aiep.png\"/>"
        } else {
            ""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
$image
</Relationships>"""
    }

    private companion object {
        const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="23"/><w:color w:val="12212F"/></w:rPr></w:rPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:pPr><w:spacing w:after="120"/></w:pPr><w:rPr><w:b/><w:color w:val="003B70"/><w:sz w:val="52"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="80"/></w:pPr><w:rPr><w:b/><w:color w:val="003B70"/><w:sz w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="200" w:after="60"/></w:pPr><w:rPr><w:b/><w:color w:val="003B70"/><w:sz w:val="26"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="160" w:after="40"/></w:pPr><w:rPr><w:b/><w:color w:val="00528F"/><w:sz w:val="23"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="60"/></w:pPr></w:style>
</w:styles>"""

        const val FOOTER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:p><w:pPr><w:pBdr><w:top w:val="single" w:sz="4" w:space="1" w:color="D6DBE2"/></w:pBdr><w:jc w:val="center"/></w:pPr>
<w:r><w:rPr><w:color w:val="647385"/><w:sz w:val="16"/></w:rPr><w:t xml:space="preserve">AIEP Subtítulos · de la Universidad Andrés Bello · página </w:t></w:r>
<w:r><w:rPr><w:color w:val="647385"/><w:sz w:val="16"/></w:rPr><w:fldChar w:fldCharType="begin"/></w:r>
<w:r><w:rPr><w:color w:val="647385"/><w:sz w:val="16"/></w:rPr><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r>
<w:r><w:rPr><w:color w:val="647385"/><w:sz w:val="16"/></w:rPr><w:fldChar w:fldCharType="end"/></w:r>
</w:p>
</w:ftr>"""

        const val DOCUMENT_OPEN = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>"""

        const val DOCUMENT_CLOSE = "</w:body></w:document>"

        const val SECT_PR = """<w:sectPr><w:footerReference w:type="default" r:id="rId2"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>"""

        // Inline logo image: ~2.4in wide, aspect-matched to the 872x344 PNG. r:embed -> rId3.
        const val LOGO_PARAGRAPH = """<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="2194560" cy="865760"/><wp:docPr id="1" name="logo"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="1" name="logo"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId3"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2194560" cy="865760"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""

        const val WORDMARK_PARAGRAPH = """<w:p><w:r><w:rPr><w:b/><w:color w:val="003B70"/><w:sz w:val="48"/></w:rPr><w:t xml:space="preserve">AIEP</w:t></w:r></w:p><w:p><w:pPr><w:spacing w:after="80"/></w:pPr><w:r><w:rPr><w:color w:val="003B70"/><w:sz w:val="18"/></w:rPr><w:t xml:space="preserve">de la Universidad Andrés Bello</w:t></w:r></w:p>"""
    }
}
