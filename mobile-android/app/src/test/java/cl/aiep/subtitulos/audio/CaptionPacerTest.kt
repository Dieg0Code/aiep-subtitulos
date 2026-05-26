package cl.aiep.subtitulos.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPacerTest {

    @Test
    fun partial_that_retracts_words_replaces_instead_of_accumulating() {
        val pacer = CaptionPacer()
        pacer.set("el de la")
        val after = pacer.set("el perro le ladra a")

        assertEquals("el perro le ladra a", after.visibleText)
        assertEquals("", after.committedTail)
        assertEquals("el perro le ladra a", after.currentTail)
    }

    @Test
    fun final_commits_current_utterance() {
        val pacer = CaptionPacer()
        pacer.set("el perro", isFinal = false)
        val after = pacer.set("el perro corre", isFinal = true)

        assertEquals("el perro corre", after.visibleText)
        assertEquals("el perro corre", after.committedTail)
        assertEquals("", after.currentTail)
    }

    @Test
    fun cross_utterance_repetition_is_preserved() {
        val pacer = CaptionPacer()
        pacer.set("el perro", isFinal = true)
        val after = pacer.set("el perro", isFinal = true)

        assertEquals("el perro el perro", after.visibleText)
        assertEquals("el perro el perro", after.committedTail)
        assertEquals("", after.currentTail)
    }

    @Test
    fun in_flight_words_render_after_committed() {
        val pacer = CaptionPacer()
        pacer.set("hola mundo", isFinal = true)
        val after = pacer.set("como estas")

        assertEquals("hola mundo como estas", after.visibleText)
        assertEquals("hola mundo", after.committedTail)
        assertEquals("como estas", after.currentTail)
    }

    @Test
    fun instant_placeholder_is_cleared_by_first_real_partial() {
        val pacer = CaptionPacer()
        val placeholder = pacer.set("Escuchando...", instant = true)
        assertEquals("Escuchando...", placeholder.visibleText)

        val after = pacer.set("hola")
        assertEquals("hola", after.visibleText)
        assertEquals("", after.committedTail)
        assertEquals("hola", after.currentTail)
    }

    @Test
    fun reset_clears_everything() {
        val pacer = CaptionPacer()
        pacer.set("hola mundo", isFinal = true)
        pacer.set("estoy hablando")
        val after = pacer.reset()

        assertEquals("", after.visibleText)
        assertEquals("", after.committedTail)
        assertEquals("", after.currentTail)
    }

    @Test
    fun visible_window_keeps_last_n_words_anchored_to_current() {
        val pacer = CaptionPacer(maxVisibleWords = 5)
        pacer.set("uno dos tres cuatro cinco seis siete", isFinal = true)
        val after = pacer.set("ocho nueve")

        assertEquals("seis siete ocho nueve", after.visibleText.split(" ").takeLast(4).joinToString(" "))
        assertEquals("ocho nueve", after.currentTail)
        assertTrue("committedTail must end with siete", after.committedTail.endsWith("siete"))
    }

    @Test
    fun history_trim_drops_oldest_committed_words() {
        val pacer = CaptionPacer(maxVisibleWords = 1000, maxHistoryWords = 4)
        pacer.set("a b c", isFinal = true)
        pacer.set("d e f", isFinal = true)

        val snap = pacer.snapshot
        assertEquals("c d e f", snap.visibleText)
        assertEquals("c d e f", snap.committedTail)
    }

    @Test
    fun final_with_blank_text_just_closes_the_utterance_without_appending() {
        val pacer = CaptionPacer()
        pacer.set("hola", isFinal = false)
        pacer.set("hola", isFinal = true)
        val after = pacer.set("", isFinal = true)

        assertEquals("hola", after.visibleText)
        assertEquals("hola", after.committedTail)
    }
}
