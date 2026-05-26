package cl.aiep.subtitulos.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptionPreview(
    val text: String = "",
    val isFinal: Boolean = false,
    val timestampMs: Long = 0L,
)

object CaptionPreviewBus {
    private val _preview = MutableStateFlow(CaptionPreview())
    val preview: StateFlow<CaptionPreview> = _preview.asStateFlow()

    fun emit(text: String, isFinal: Boolean) {
        _preview.value = CaptionPreview(
            text = text,
            isFinal = isFinal,
            timestampMs = System.currentTimeMillis(),
        )
    }

    fun reset() {
        _preview.value = CaptionPreview()
    }
}
