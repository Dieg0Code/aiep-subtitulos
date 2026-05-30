package cl.aiep.subtitulos.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StudyPdfFileName {
    fun build(
        sessionName: String,
        now: Long = System.currentTimeMillis(),
        extension: String = "pdf",
    ): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val safe = sessionName
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9áéíóúñü]+"), "-")
            .trim('-')
            .ifBlank { "sesion" }
            .take(48)
            .trim('-')
        return "aiep-subtitulos-$safe-$date.$extension"
    }
}
