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
    fun parsesProviderResponses() {
        val client = AiProviderClient(AiProviderConfig("token", AiProviderMode.OpenAI, ""))
        val chatJson = """{"choices":[{"message":{"content":"# Clase\nContenido"}}]}"""
        val anthropicJson = """{"content":[{"type":"text","text":"# Clase"},{"type":"text","text":"\nContenido"}]}"""

        assertEquals("# Clase\nContenido", client.parseStudyMarkdown(AiProviderMode.OpenAI, chatJson))
        assertEquals("# Clase\nContenido", client.parseStudyMarkdown(AiProviderMode.GitHubModels, chatJson))
        assertEquals("# Clase\nContenido", client.parseStudyMarkdown(AiProviderMode.Anthropic, anthropicJson))
    }
}
