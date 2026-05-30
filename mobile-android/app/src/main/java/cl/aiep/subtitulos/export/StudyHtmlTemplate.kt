package cl.aiep.subtitulos.export

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StudyVariant { Ai, Raw }

/**
 * Loads `assets/study_template.html` and fills it with the session title, date,
 * branded cover mark and the converted Markdown body. The result is loaded into
 * a WebView with base URL `file:///android_asset/` so the logo and fonts resolve.
 */
object StudyHtmlTemplate {
    fun build(
        context: Context,
        sessionName: String,
        bodyHtml: String,
        variant: StudyVariant,
        now: Long = System.currentTimeMillis(),
    ): String {
        val template = context.assets.open("study_template.html")
            .use { it.readBytes().toString(Charsets.UTF_8) }

        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(now))
        val badge = when (variant) {
            StudyVariant.Ai -> "Material de estudio · IA"
            StudyVariant.Raw -> "Transcripción cruda"
        }

        return template
            .replace("{{BRAND}}", brandMark(context))
            .replace("{{TITLE}}", MarkdownToHtml.escape(sessionName))
            .replace("{{DATE}}", MarkdownToHtml.escape(date))
            .replace("{{BADGE}}", MarkdownToHtml.escape(badge))
            .replace("{{BODY}}", bodyHtml)
    }

    /** Official logo when bundled; otherwise a typographic wordmark fallback. */
    private fun brandMark(context: Context): String =
        if (BrandAssets.hasLogo(context)) {
            "<img class=\"logo\" src=\"${BrandAssets.LOGO_ASSET}\" alt=\"AIEP\" />"
        } else {
            "<div class=\"wordmark\">AIEP<span class=\"sub\">de la Universidad Andrés Bello</span></div>"
        }
}
