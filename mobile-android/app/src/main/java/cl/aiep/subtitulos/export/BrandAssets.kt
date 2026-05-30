package cl.aiep.subtitulos.export

import android.content.Context

/**
 * Loads AIEP brand assets bundled under `src/main/assets`. Null-safe: if the
 * official logo PNG has not been dropped in yet, callers fall back gracefully
 * (a typographic wordmark in HTML, or no image in the .docx).
 */
object BrandAssets {
    const val LOGO_ASSET = "logo_aiep.png"

    fun logoBytes(context: Context): ByteArray? =
        runCatching {
            context.assets.open(LOGO_ASSET).use { it.readBytes() }
        }.getOrNull()

    fun hasLogo(context: Context): Boolean =
        runCatching {
            context.assets.list("")?.contains(LOGO_ASSET) == true
        }.getOrDefault(false)
}
