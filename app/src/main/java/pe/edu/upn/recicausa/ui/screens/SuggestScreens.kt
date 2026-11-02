package pe.edu.upn.recicausa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import pe.edu.upn.recicausa.domain.Catalog
import pe.edu.upn.recicausa.domain.PuntoEstado
import pe.edu.upn.recicausa.domain.PuntoReciclaje
import pe.edu.upn.recicausa.ui.components.LocationPickerOsm
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.viewmodel.MyPointsViewModel
import pe.edu.upn.recicausa.ui.viewmodel.SuggestPointViewModel

// Sprint 5 - HU12: sugerir un punto nuevo, y ver / editar / eliminar mis sugerencias.

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, error: String?,
    singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, hint: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestPointScreen(
    viewModel: SuggestPointViewModel,
    editingId: Long? = null,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(editingId) { if (editingId != null) viewModel.startEdit(editingId) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(
                context,
                if (state.isEditing) "Cambios guardados." else "¡Gracias! Tu sugerencia quedó pendiente de aprobación.",
                Toast.LENGTH_LONG
            ).show()
            onSaved()
        }
    }
    val requestLocation = rememberLocationRequester(
        onGranted = viewModel::useMyLocation, onDenied = viewModel::onLocationDenied
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Volver") }
        Text(
            if (state.isEditing) "Editar sugerencia" else "Sugerir un punto",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
        )
        Text(
            "Tu sugerencia quedará pendiente hasta que el equipo la revise y la apruebe.",
            style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted
        )

        if (state.loadingEdit) {
            LoadingBox("Cargando sugerencia…")
            return@Column
        }

        Field("Nombre del punto", state.nombre, viewModel::onNombre, state.errors.nombreError)
        Field("Dirección", state.direccion, viewModel::onDireccion, state.errors.direccionError)

        Text("¿Qué residuos reciben?", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalog.categories.forEach { c ->
                FilterChip(
                    selected = c.id in state.categorias, onClick = { viewModel.onToggleCategoria(c.id) },
                    label = { Text(c.name) }, modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
        state.errors.categoriasError?.let { Text(it, color = ReciColors.Danger, style = MaterialTheme.typography.bodySmall) }

        Field("Horario (opcional)", state.horario, viewModel::onHorario, state.errors.horarioError, hint = "Ej.: Lun–Sáb 8:00–18:00")
        Field("Teléfono (opcional)", state.telefono, viewModel::onTelefono, state.errors.telefonoError, keyboardType = KeyboardType.Phone)
        Field("Descripción (opcional)", state.descripcion, viewModel::onDescripcion, state.errors.descripcionError, singleLine = false)

        Text("Ubicación", fontWeight = FontWeight.Bold)
        Text("Toca el mapa para marcar el punto, o usa tu ubicación actual.", style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
        LocationPickerOsm(
            latitud = state.latitud, longitud = state.longitud, onPick = viewModel::onLocationPicked,
            modifier = Modifier.fillMaxWidth().height(260.dp)
        )
        OutlinedButton(onClick = requestLocation, enabled = !state.locating, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (state.locating) "Buscando…" else "Usar mi ubicación actual")
        }
        Text(
            if (state.latitud != null && state.longitud != null) "Ubicación marcada ✓" else "Aún no marcaste la ubicación.",
            style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
        )
        state.errors.ubicacionError?.let { Text(it, color = ReciColors.Danger, style = MaterialTheme.typography.bodySmall) }

        state.formError?.let { Text(it, color = ReciColors.Danger, fontWeight = FontWeight.Bold) }

        Button(
            onClick = viewModel::submit, enabled = !state.saving,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) { Text(if (state.saving) "Guardando…" else if (state.isEditing) "Guardar cambios" else "Enviar sugerencia", fontWeight = FontWeight.Bold) }
        Box(Modifier.height(12.dp))
    }
}

// ---------- Mis sugerencias ----------
@Composable
fun MyPointsScreen(
    viewModel: MyPointsViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<PuntoReciclaje?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.messageShown() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Volver") }
            Text("Mis sugerencias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Puedes editar o eliminar una sugerencia mientras esté pendiente.",
                style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            when {
                state.loading -> LoadingBox("Cargando…")
                state.error != null -> ErrorBox(state.error!!, viewModel::load)
                state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Aún no has sugerido ningún punto.", color = ReciColors.Muted)
                    Button(onClick = onNew, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text("Sugerir un punto", fontWeight = FontWeight.Bold) }
                }
                else -> LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.items, key = { it.id }) { p ->
                        Column(
                            Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(p.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(p.direccion, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Recibe: " + p.categorias.mapNotNull { Catalog.categoryById(it)?.name }.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
                            )
                            // Estado en texto (no solo color).
                            Text(
                                "Estado: ${p.estado.label}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                                color = if (p.estado == PuntoEstado.RECHAZADO) ReciColors.Danger else ReciColors.GreenDark
                            )
                            if (p.estado == PuntoEstado.PENDIENTE) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEdit(p.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Editar") }
                                    TextButton(onClick = { toDelete = p }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("Eliminar", color = ReciColors.Danger, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    item { Box(Modifier.height(12.dp)) }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar sugerencia?") },
            text = { Text("Se eliminará \"${p.nombre}\". Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(p.id); toDelete = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Eliminar", color = ReciColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
            }
        )
    }
}
