package cl.aiep.subtitulos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.ui.theme.AiepCreamSoft
import cl.aiep.subtitulos.ui.theme.AiepInk
import cl.aiep.subtitulos.ui.theme.AiepLine
import cl.aiep.subtitulos.ui.theme.AiepMuted
import cl.aiep.subtitulos.ui.theme.AiepNavy
import cl.aiep.subtitulos.ui.theme.AiepNavyDeep
import cl.aiep.subtitulos.ui.theme.AiepRed
import cl.aiep.subtitulos.ui.theme.AiepSurface

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AiepSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(width = 1.dp, color = AiepLine),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = AiepRed,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun ComingSoonBadge() {
    Text(
        text = stringResource(R.string.badge_coming_soon).uppercase(),
        color = AiepNavyDeep,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color = AiepNavy.copy(alpha = 0.10f), shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun aiepFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AiepCreamSoft,
    unfocusedContainerColor = AiepCreamSoft,
    disabledContainerColor = AiepCreamSoft.copy(alpha = 0.6f),
    focusedIndicatorColor = AiepNavy,
    unfocusedIndicatorColor = AiepLine,
    focusedLabelColor = AiepNavy,
    unfocusedLabelColor = AiepMuted,
    cursorColor = AiepNavy,
    focusedTextColor = AiepInk,
    unfocusedTextColor = AiepInk,
    disabledTextColor = AiepMuted,
)
