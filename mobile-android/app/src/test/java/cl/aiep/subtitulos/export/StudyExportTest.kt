package cl.aiep.subtitulos.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyExportTest {
    @Test
    fun promptIncludesTranscriptAndFidelityRules() {
        val prompt = StudyMaterialPrompt.build(
            sessionName = "Clase motor",
            transcriptMarkdown = "[10:00:00] El profe explica calculo termico.",
        )

        assertTrue(prompt.contains("Clase motor"))
        assertTrue(prompt.contains("El profe explica calculo termico."))
        assertTrue(prompt.contains("No inventes"))
        assertTrue(prompt.contains("tono"))
    }

    @Test
    fun pdfFileNameIsSafeAndDated() {
        val name = StudyPdfFileName.build(
            sessionName = "Sesion: Motor / Calculo",
            now = 1_735_689_600_000L,
        )

        assertTrue(name.startsWith("aiep-subtitulos-sesion-motor-calculo-"))
        assertTrue(name.endsWith(".pdf"))
        assertTrue(!name.contains(":"))
        assertTrue(!name.contains("/"))
    }

    @Test
    fun detectsProviderFromTokenPrefix() {
        assertEquals(AiProviderMode.GitHubModels, AiProviderDetector.detect("github_pat_abc"))
        assertEquals(AiProviderMode.GitHubModels, AiProviderDetector.detect("ghp_abc"))
        assertEquals(AiProviderMode.GitHubModels, AiProviderDetector.detect("gho_abc"))
        assertEquals(AiProviderMode.Anthropic, AiProviderDetector.detect("sk-ant-abc"))
        assertEquals(AiProviderMode.OpenAI, AiProviderDetector.detect("sk-proj-abc"))
        assertEquals(AiProviderMode.OpenAI, AiProviderDetector.detect("sk-abc"))
        assertNull(AiProviderDetector.detect("token-sin-prefijo"))
    }

    @Test
    fun buildsProviderSpecificHeaders() {
        val github = AiProviderClient(
            AiProviderConfig("github_pat_abc", AiProviderMode.GitHubModels, ""),
        ).buildRequest(AiProviderMode.GitHubModels, "Clase", "Texto")
        val openAi = AiProviderClient(
            AiProviderConfig("sk-proj-abc", AiProviderMode.OpenAI, ""),
        ).buildRequest(AiProviderMode.OpenAI, "Clase", "Texto")
        val anthropic = AiProviderClient(
            AiProviderConfig("sk-ant-abc", AiProviderMode.Anthropic, ""),
        ).buildRequest(AiProviderMode.Anthropic, "Clase", "Texto")

        assertEquals("Bearer github_pat_abc", github.header("Authorization"))
        assertEquals("2026-03-10", github.header("X-GitHub-Api-Version"))
        assertEquals("Bearer sk-proj-abc", openAi.header("Authorization"))
        assertEquals("sk-ant-abc", anthropic.header("x-api-key"))
        assertEquals("2023-06-01", anthropic.header("anthropic-version"))
    }

    @Test
    fun docxFileNameUsesExtension() {
        val name = StudyPdfFileName.build(
            sessionName = "Clase motor",
            now = 1_735_689_600_000L,
            extension = "docx",
        )
        assertTrue(name.endsWith(".docx"))
        assertTrue(name.startsWith("aiep-subtitulos-clase-motor-"))
    }

    @Test
    fun markdownToHtmlConvertsStructureAndDropsTitle() {
        val html = MarkdownToHtml.render("# Título\n## Sección\n- punto **uno**\npárrafo")

        assertTrue("first H1 is the cover title and must be dropped", !html.contains("<h1>"))
        assertTrue(html.contains("<h2>Sección</h2>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<strong>uno</strong>"))
        assertTrue(html.contains("<p>párrafo</p>"))
    }

    @Test
    fun markdownToHtmlEscapesSpecialChars() {
        assertEquals("a &amp; b &lt;c&gt;", MarkdownToHtml.escape("a & b <c>"))
        assertTrue(MarkdownToHtml.render("5 < 6 & 7").contains("5 &lt; 6 &amp; 7"))
    }

    @Test
    fun docxParagraphForLineMapsTypes() {
        assertTrue(DocxXml.paragraphForLine("## Tema").contains("w:val=\"Heading2\""))
        assertTrue(DocxXml.paragraphForLine("- item").contains("ListParagraph"))
        assertEquals("", DocxXml.paragraphForLine("   "))

        val bold = DocxXml.paragraphForLine("hola **fuerte**")
        assertTrue(bold.contains("<w:b/>"))
        assertTrue(bold.contains("fuerte"))
    }

    @Test
    fun docxEscapesXml() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot;", DocxXml.escape("a & b <c> \"d\""))
    }

    @Test
    fun docxBodyDropsFirstTitle() {
        val body = DocxXml.body("# Clase\n## Tema\ntexto")

        assertTrue("title goes in the branded block, not the body", !body.contains(">Clase<"))
        assertTrue(body.contains("Heading2"))
        assertTrue(body.contains("texto"))
    }

    @Test
    fun parsesProviderResponses() {
        val client = AiProviderClient(AiProviderConfig("token", AiProviderMode.OpenAI, ""))
        val chatJson = """{"choices":[{"message":{"content":"# Clase\nContenido"}}]}"""
        val anthropicJson = """{"content":[{"type":"text","text":"# Clase"},{"type":"text","text":"\nContenido"}]}"""

        assertEquals("# Clase\nContenido", client.parseStudyMarkdown(AiProviderMode.OpenAI, chatJson))
        assertEquals("# Clase\nContenido", client.parseStudyMarkdown(AiProviderMode.GitHubModels, chatJson))
        assertEquals("# Clase\nContenido", client.parseStudyMarkdown(AiProviderMode.Anthropic, anthropicJson))
    }
}
