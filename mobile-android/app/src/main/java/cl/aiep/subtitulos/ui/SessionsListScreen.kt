package cl.aiep.subtitulos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.prefs.AppPreferences
import cl.aiep.subtitulos.sessions.ActiveSessionTracker
import cl.aiep.subtitulos.sessions.SessionMeta
import cl.aiep.subtitulos.sessions.SessionsRepository
import cl.aiep.subtitulos.ui.theme.AiepAmber
import cl.aiep.subtitulos.ui.theme.AiepCream
import cl.aiep.subtitulos.ui.theme.AiepCreamSoft
import cl.aiep.subtitulos.ui.theme.AiepGreen
import cl.aiep.subtitulos.ui.theme.AiepInk
import cl.aiep.subtitulos.ui.theme.AiepLine
import cl.aiep.subtitulos.ui.theme.AiepMuted
import cl.aiep.subtitulos.ui.theme.AiepNavy
import cl.aiep.subtitulos.ui.theme.AiepNavyDeep
import cl.aiep.subtitulos.ui.theme.AiepSurface
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsListScreen(
    onOpenSession: (String) -> Unit,
    onCreateSession: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SessionsRepository.get(context) }
    val sessions by repo.sessions.collectAsStateWithLifecycle()
    val activeId by ActiveSessionTracker.activeId.collectAsState()
    val prefs by AppPreferences.stateFlow(context)
        .collectAsStateWithLifecycle(initialValue = AppPreferences.State())
    val coroutineScope = rememberCoroutineScope()

    var pendingDelete by remember { mutableStateOf<SessionMeta?>(null) }
    val activeSession = sessions.firstOrNull { it.id == activeId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AiepCream)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "AIEP Subtítulos",
                color = AiepNavy,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Sesiones de clase y transcripción en vivo",
                color = AiepMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (activeSession != null) {
            RecordingBanner(
                activeSessionName = activeSession.name,
                onClick = { onOpenSession(activeSession.id) },
            )
        }

        BatteryOptimizationBannerSlot(
            dismissed = prefs.batteryPromptDismissed,
            onDismiss = { coroutineScope.launch { AppPreferences.setBatteryPromptDismissed(context) } },
        )

        if (sessions.isEmpty()) {
            Text(
                text = "Crea tu primera sesión para comenzar.",
                color = AiepMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(top = 2.dp, bottom = 18.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                CreateSessionCard(onClick = onCreateSession)
            }
            items(sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    isActive = session.id == activeId,
                    onClick = { onOpenSession(session.id) },
                    onDelete = { pendingDelete = session },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar sesión") },
            text = { Text("¿Eliminar \"${target.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { repo.deleteSession(target.id) }
                    pendingDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
            containerColor = AiepSurface,
        )
    }
}

@Composable
private fun CreateSessionCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .background(color = AiepSurface, shape = RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = AiepNavy.copy(alpha = 0.24f), shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(AiepNavy, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", color = AiepSurface, fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Crear sesión",
                color = AiepNavy,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Prepara una nueva clase para conectar el micrófono.",
                color = AiepMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionMeta,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .background(AiepSurface, RoundedCornerShape(14.dp))
            .border(1.dp, AiepLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(AiepGreen, CircleShape),
                    )
                    Text(
                        text = "Grabando",
                        color = AiepGreen,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = session.name,
                color = AiepInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatRelative(session.updatedAt),
                color = AiepMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = modeBadge(session.mode),
                    color = AiepNavyDeep,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(AiepCreamSoft, shape = CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    text = "${session.captionCount} línea${if (session.captionCount == 1) "" else "s"}",
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (session.lastSnippet.isNotBlank()) {
                Text(
                    text = session.lastSnippet,
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            Text(
                text = "⋯",
                color = AiepMuted,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clickable { menuOpen = true }
                    .padding(horizontal = 6.dp),
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = AiepSurface,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isActive) "Eliminar (detén la grabación)" else "Eliminar",
                            color = if (isActive) AiepMuted else MaterialTheme.colorScheme.error,
                        )
                    },
                    enabled = !isActive,
                    onClick = {
                        menuOpen = false
                        if (!isActive) onDelete()
                    },
                )
            }
        }
    }
}

private fun modeBadge(mode: CaptureMode): String = when (mode) {
    CaptureMode.Speech -> "Speech"
    CaptureMode.Pcm -> "Whisper"
}

@Composable
private fun BatteryOptimizationBannerSlot(
    dismissed: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var ignored by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ignored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    if (ignored || dismissed) return

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AiepAmber.copy(alpha = 0.13f)),
        border = BorderStroke(width = 1.dp, color = AiepAmber.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AiepAmber.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "!", color = AiepInk, fontWeight = FontWeight.Black)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.battery_banner_text),
                    color = AiepInk,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.battery_banner_activate),
                    color = AiepNavy,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(AiepSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, AiepNavy.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            IconButton(onClick = onDismiss) {
                Text(text = "×", color = AiepMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal fun formatRelative(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < hour -> {
            val m = maxOf(1, (diff / minute).toInt())
            if (m == 1) "hace 1 min" else "hace $m min"
        }
        diff < day -> {
            val h = (diff / hour).toInt()
            if (h == 1) "hace 1 h" else "hace $h h"
        }
        diff < 2 * day -> "ayer"
        diff < 7 * day -> "hace ${(diff / day).toInt()} días"
        else -> SimpleDateFormat("d MMM yyyy", Locale("es", "CL")).format(Date(ms))
    }
}
