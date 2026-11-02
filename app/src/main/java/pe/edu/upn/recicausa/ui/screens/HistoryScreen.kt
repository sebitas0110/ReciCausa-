package pe.edu.upn.recicausa.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import pe.edu.upn.recicausa.domain.Catalog
import pe.edu.upn.recicausa.domain.RecycleActivity
import pe.edu.upn.recicausa.ui.components.EmptyState
import pe.edu.upn.recicausa.ui.components.LeafGlyph
import pe.edu.upn.recicausa.ui.components.PhotoThumb
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.theme.toColor
import pe.edu.upn.recicausa.ui.viewmodel.HistoryViewModel

// Historial de actividades: Leer (lista), Actualizar (Editar) y Eliminar con confirmación.

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("es-PE")) }
    var toDelete by remember { mutableStateOf<RecycleActivity?>(null) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Volver") }
                Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${state.items.size} actividades registradas. Puedes editarlas o eliminarlas.",
                    style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted
                )
            }
        }
        if (state.loaded && state.items.isEmpty()) {
            item { EmptyState("Aún no registras actividades.") }
        }
        items(state.items, key = { it.id }) { activity ->
            HistoryRow(activity, dateFormat, onEdit = { onEdit(activity.id) }, onDelete = { toDelete = activity })
        }
        item { Box(Modifier.size(12.dp)) }
    }

    toDelete?.let { activity ->
        val name = Catalog.categoryById(activity.categoryId)?.name ?: activity.categoryId
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar actividad?") },
            text = {
                Text("Se eliminará «$name × ${activity.quantity}»${if (activity.photoPath != null) " y su foto" else ""}. Perderás ${activity.points} puntos. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(activity.id)
                        toDelete = null
                        Toast.makeText(context, "Actividad eliminada.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Eliminar", color = ReciColors.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun HistoryRow(activity: RecycleActivity, dateFormat: SimpleDateFormat, onEdit: () -> Unit, onDelete: () -> Unit) {
    val category = Catalog.categoryById(activity.categoryId)
    Column(
        Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (activity.photoPath != null) {
                PhotoThumb(activity.photoPath, contentDescription = "Foto de ${category?.name ?: "la actividad"}")
            } else {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(category?.bin?.toColor() ?: ReciColors.Muted),
                    contentAlignment = Alignment.Center
                ) { LeafGlyph(color = Color.White, size = 22.dp) }
            }
            Column(Modifier.weight(1f)) {
                Text("${category?.name ?: activity.categoryId} × ${activity.quantity}", fontWeight = FontWeight.Bold)
                Text(dateFormat.format(Date(activity.createdAt)), style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
                if (activity.note.isNotBlank()) {
                    Text(activity.note, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Text("+${activity.points} pts", color = ReciColors.Green, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Editar") }
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Eliminar", color = ReciColors.Danger, fontWeight = FontWeight.Bold)
            }
        }
    }
}
