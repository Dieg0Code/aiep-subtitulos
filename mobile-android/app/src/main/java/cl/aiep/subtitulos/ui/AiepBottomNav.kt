package cl.aiep.subtitulos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.ui.theme.AiepCreamSoft
import cl.aiep.subtitulos.ui.theme.AiepLine
import cl.aiep.subtitulos.ui.theme.AiepNavy
import cl.aiep.subtitulos.ui.theme.AiepSurface

enum class AiepTab(val route: String, val labelRes: Int, val iconRes: Int) {
    Sessions("sessions", R.string.nav_sessions, R.drawable.ic_sessions_24),
    Settings("settings", R.string.nav_settings, R.drawable.ic_settings_24),
}

@Composable
fun AiepBottomNav(
    currentRoute: String?,
    onSelect: (AiepTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = AiepSurface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = AiepLine, shape = RoundedCornerShape(22.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AiepTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                NavPill(
                    tab = tab,
                    selected = selected,
                    onClick = { if (!selected) onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavPill(
    tab: AiepTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AiepNavy.copy(alpha = 0.10f) else AiepCreamSoft.copy(alpha = 0.0f)
    val fg = if (selected) AiepNavy else AiepNavy.copy(alpha = 0.62f)
    val label = stringResource(id = tab.labelRes)
    Row(
        modifier = modifier
            .height(52.dp)
            .background(color = bg, shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = tab.iconRes),
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}
