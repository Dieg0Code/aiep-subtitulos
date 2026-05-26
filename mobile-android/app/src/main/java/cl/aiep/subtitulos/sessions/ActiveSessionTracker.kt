package cl.aiep.subtitulos.sessions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the id of the session that is currently recording, in-memory only.
 * Resets to null at process restart by design — a session whose recording got
 * killed by the OS is not "active" anymore on the next boot.
 */
object ActiveSessionTracker {
    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    fun setActive(id: String?) {
        _activeId.value = id
    }

    fun isActive(id: String): Boolean = _activeId.value == id
}
