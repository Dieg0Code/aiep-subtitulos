package cl.aiep.subtitulos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AiepColorScheme = lightColorScheme(
    primary = AiepNavy,
    onPrimary = AiepSurface,
    primaryContainer = AiepNavyDeep,
    onPrimaryContainer = AiepSurface,
    secondary = AiepNavyDeep,
    onSecondary = AiepSurface,
    tertiary = AiepRed,
    onTertiary = AiepSurface,
    background = AiepCream,
    onBackground = AiepInk,
    surface = AiepSurface,
    onSurface = AiepInk,
    surfaceVariant = AiepCreamSoft,
    onSurfaceVariant = AiepMuted,
    outline = AiepLine,
    outlineVariant = AiepLine,
)

@Composable
fun AiepSubtitulosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AiepColorScheme,
        typography = AiepTypography,
        content = content,
    )
}
