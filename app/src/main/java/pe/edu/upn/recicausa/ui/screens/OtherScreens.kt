package pe.edu.upn.recicausa.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.edu.upn.recicausa.data.reminders.Notifications
import pe.edu.upn.recicausa.data.sync.SyncState
import pe.edu.upn.recicausa.domain.ChallengeProgress
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.viewmodel.ChallengesViewModel
import pe.edu.upn.recicausa.ui.viewmodel.SettingsViewModel

// HU04 (retos) y HU06 (recordatorios). El progreso general vive en la pantalla Inicio.

@Composable
fun ChallengesScreen(viewModel: ChallengesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val done = state.items.count { it.completed }
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Retos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Completa retos con tus registros de reciclaje.", style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted)
            }
        }
        if (state.items.isNotEmpty()) {
            item { ChallengesSummary(done, state.items.size) }
        }
        items(state.items, key = { it.challenge.id }) { ChallengeItem(it) }
        item { Box(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ChallengesSummary(done: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().background(ReciColors.Green, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("$done/$total", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Column {
            Text("retos completados", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (done == total) "¡Los completaste todos!" else "Sigue registrando para avanzar.",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChallengeItem(item: ChallengeProgress) {
    val shownCurrent = item.current.coerceAtMost(item.challenge.target)
    Row(
        Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Estado en forma y texto (no solo color): ✓ completado, número si está en curso.
        Box(
            Modifier.size(44.dp).background(if (item.completed) ReciColors.Green else ReciColors.GreenSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (item.completed) "✓" else "$shownCurrent",
                color = if (item.completed) Color.White else ReciColors.Green,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (item.completed) {
                    Text(
                        "Completado", color = ReciColors.GreenDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(ReciColors.GreenSoft, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            // RN05: cada reto indica claramente la acción necesaria para completarlo.
            Text(item.challenge.description, style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(ReciColors.Track)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(item.fraction).background(ReciColors.Green, RoundedCornerShape(5.dp)))
            }
            Text("$shownCurrent de ${item.challenge.target}", style = MaterialTheme.typography.labelMedium, color = ReciColors.Muted)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var notificationsAllowed by remember { mutableStateOf(Notifications.hasPermission(context)) }
    var showDeniedHelp by remember { mutableStateOf(false) }

    // Android 13+: el permiso se pide al activar el interruptor, no al abrir la app.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
        showDeniedHelp = !granted
        if (granted) viewModel.onRemindersChange(true)
    }

    fun onToggle(enabled: Boolean) {
        if (!enabled) {
            showDeniedHelp = false
            viewModel.onRemindersChange(false)
        } else if (Notifications.hasPermission(context)) {
            notificationsAllowed = true
            viewModel.onRemindersChange(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text("Avisos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Un recordatorio al día para separar tus residuos.", style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted)
        }

        BackupCard(state.email, state.sync, onSync = viewModel::syncNow)

        Row(
            Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Recordarme separar mis residuos", fontWeight = FontWeight.Bold)
                Text(
                    if (state.remindersEnabled) "Activado · todos los días a las %02d:00".format(state.reminderHour) else "Desactivado",
                    style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
                )
            }
            Switch(checked = state.remindersEnabled && notificationsAllowed, onCheckedChange = ::onToggle)
        }

        // Permiso rechazado, o revocado luego desde los ajustes del teléfono.
        if (showDeniedHelp || (state.remindersEnabled && !notificationsAllowed)) {
            Column(
                Modifier.fillMaxWidth().background(ReciColors.AmberSoft, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Las notificaciones están desactivadas", fontWeight = FontWeight.Bold, color = ReciColors.AmberInk)
                Text(
                    "Sin el permiso de notificaciones no podemos enviarte el recordatorio. Puedes activarlo en los ajustes del teléfono; el resto de la app funciona igual.",
                    style = MaterialTheme.typography.bodyMedium, color = ReciColors.AmberInk
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Abrir ajustes", fontWeight = FontWeight.Bold) }
            }
        }

        if (state.remindersEnabled) {
            Column(
                Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Hora del recordatorio", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8, 12, 18, 20).forEach { hour ->
                        FilterChip(
                            selected = state.reminderHour == hour,
                            onClick = { viewModel.onHourChange(hour) },
                            label = { Text("%02d:00".format(hour)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Text(
                    "La hora es aproximada: Android puede retrasar el aviso unos minutos para ahorrar batería.",
                    style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
                )
            }
        }

        // Vista previa de cómo se verá el aviso.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Así se verá", style = MaterialTheme.typography.labelLarge, color = ReciColors.Muted)
            Row(
                Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(16.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(36.dp).background(ReciColors.Green, CircleShape), contentAlignment = Alignment.Center) {
                    pe.edu.upn.recicausa.ui.components.LeafGlyph(color = Color.White, size = 20.dp)
                }
                Column {
                    Text("ReciCausa", fontWeight = FontWeight.Bold)
                    Text("¿Ya separaste tus residuos hoy? Regístralo y suma puntos.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text(
            "Máximo un recordatorio al día. Puedes desactivarlo cuando quieras.",
            style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
        )
        Box(Modifier.height(12.dp))
    }
}

/** HU13 · Respaldo de actividades en la nube, con su estado y sincronización manual (CA13.3). */
@Composable
private fun BackupCard(email: String, sync: SyncState, onSync: () -> Unit) {
    val (status, isError) = when (sync) {
        SyncState.Idle -> "Aún no se ha sincronizado." to false
        SyncState.Syncing -> "Sincronizando…" to false
        SyncState.Offline -> "Sin conexión. Se sincronizará cuando vuelva internet." to false
        is SyncState.Synced -> "Respaldado a las " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sync.at)) to false
        is SyncState.Failed -> sync.message to true
    }
    Column(
        Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Respaldo en la nube", fontWeight = FontWeight.Bold)
        if (email.isNotBlank()) Text("Cuenta: $email", style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) ReciColors.Danger else ReciColors.Ink,
            fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal
        )
        OutlinedButton(onClick = onSync, enabled = sync != SyncState.Syncing, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Sincronizar ahora", fontWeight = FontWeight.Bold)
        }
        Text(
            "Se respaldan tus actividades (sin fotos). Tus fotos y tu ubicación nunca se envían.",
            style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
        )
    }
}
