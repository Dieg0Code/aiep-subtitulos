package cl.aiep.subtitulos.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionProcessorTest {
    @Test
    fun normalizesAiepAcronym() {
        assertEquals("AIEP", CaptionProcessor.normalize("a i e p"))
    }

    @Test
    fun skipsExactDuplicateFinal() {
        val result = CaptionProcessor.mergeLine(
            previous = "bienvenidos a AIEP",
            incoming = "bienvenidos a AIEP",
            withinGap = true,
        )

        assertTrue(result is MergeResult.Skip)
    }

    @Test
    fun extendsPreviousLineWhenThereIsOverlap() {
        val result = CaptionProcessor.mergeLine(
            previous = "hoy veremos motores de cálculo",
            incoming = "motores de cálculo en una transmisión térmica",
            withinGap = false,
        )

        assertEquals(
            MergeResult.Merged("hoy veremos motores de cálculo en una transmisión térmica"),
            result,
        )
    }

    @Test
    fun createsNewLineWhenThereIsNoOverlapAndGapIsLong() {
        val result = CaptionProcessor.mergeLine(
            previous = "cerramos la primera idea",
            incoming = "ahora pasamos al ejercicio",
            withinGap = false,
        )

        assertEquals(MergeResult.NewLine("ahora pasamos al ejercicio"), result)
    }

    @Test
    fun pacerOnlyAddsNovelWordsFromGrowingCaptions() {
        val pacer = CaptionPacer(maxVisibleWords = 6)

        pacer.set("hola curso")
        pacer.tick()
        pacer.tick()
        pacer.set("hola curso hoy vemos AIEP")
        pacer.tick()
        pacer.tick()
        pacer.tick()

        assertEquals("hola curso hoy vemos AIEP", pacer.snapshot.visibleText)
    }

    @Test
    fun pacerDoesNotRequeueRepeatedCaptionWindow() {
        val pacer = CaptionPacer(maxVisibleWords = 6)

        pacer.set("uno dos tres")
        repeat(3) { pacer.tick() }
        pacer.set("uno dos tres")

        assertEquals(0, pacer.snapshot.pendingWords)
    }
}
