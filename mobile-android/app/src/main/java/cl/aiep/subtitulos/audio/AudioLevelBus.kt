package cl.aiep.subtitulos.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioLevelBus {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    fun emit(value: Float) {
        _level.value = value.coerceIn(0f, 1f)
    }

    fun reset() {
        _level.value = 0f
    }
}
