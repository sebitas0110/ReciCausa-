package pe.edu.upn.recicausa.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import pe.edu.upn.recicausa.domain.ActivityValidator
import pe.edu.upn.recicausa.domain.Catalog
import pe.edu.upn.recicausa.ui.components.CameraGlyph
import pe.edu.upn.recicausa.ui.components.PhotoImage
import pe.edu.upn.recicausa.ui.components.dashedBorder
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.theme.toColor
import pe.edu.upn.recicausa.ui.viewmodel.RegisterViewModel

// Registrar actividad (HU02, HU03) con foto opcional de lo que se deposita.

private enum class RegisterStep { Form, Permission, Camera }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    editingId: Long? = null
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var step by rememberSaveable { mutableStateOf(RegisterStep.Form) }
    var denied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) step = RegisterStep.Camera
    }

    fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // Si ya tiene el permiso va directo al visor; si no, primero se explica para qué se pide.
    fun takePhoto() {
        step = if (hasCameraPermission()) RegisterStep.Camera else RegisterStep.Permission
    }

    // Modo edición: carga la actividad una sola vez.
    LaunchedEffect(editingId) { editingId?.let(viewModel::startEdit) }
    LaunchedEffect(state.notFound) {
        if (state.notFound) {
            Toast.makeText(context, "No se encontró la actividad.", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    // CA02.1 / CA03.1: confirmación con los puntos ganados y regreso al inicio.
    LaunchedEffect(state.lastPointsEarned) {
        state.lastPointsEarned?.let {
            val message = if (state.isEditing) "Cambios guardados. Esta actividad vale $it puntos."
            else "¡Registro guardado! Ganaste $it puntos."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onSaved()
        }
    }

    BackHandler(enabled = step != RegisterStep.Form) { step = RegisterStep.Form }

    when (step) {
        RegisterStep.Permission -> CameraPermissionStep(
            denied = denied,
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onSkip = { step = RegisterStep.Form },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
            modifier = modifier
        )

        RegisterStep.Camera -> CameraStep(
            newPhotoFile = viewModel::newPhotoFile,
            onCaptured = { path ->
                viewModel.onPhotoTaken(path)
                step = RegisterStep.Form
            },
            onClose = { step = RegisterStep.Form },
            modifier = modifier
        )

        RegisterStep.Form -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.isEditing) "← Volver" else "✕ Cerrar") }
            }
            Text(if (state.isEditing) "Editar actividad" else "Registrar actividad", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            PhotoCard(
                photoPath = state.photoPath,
                onTake = ::takePhoto,
                onRemove = viewModel::onPhotoRemoved
            )

            Text("¿Qué separaste?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalog.categories.forEach { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { viewModel.onCategoryChange(category.id) },
                        label = { Text(category.name) },
                        leadingIcon = {
                            // El color del contenedor acompaña al nombre; nunca es la única señal.
                            Box(Modifier.size(10.dp).background(category.bin.toColor(), CircleShape))
                        },
                        modifier = Modifier.heightIn(min = 48.dp) // objetivo táctil ≥ 48 dp (RNF02)
                    )
                }
            }
            state.errors.categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Cantidad", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("De 1 a ${ActivityValidator.MAX_QUANTITY} unidades", style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { viewModel.onQuantityStep(-1) }, modifier = Modifier.size(48.dp)) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        state.quantity.ifEmpty { "–" },
                        modifier = Modifier.width(48.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    FilledTonalIconButton(onClick = { viewModel.onQuantityStep(1) }, modifier = Modifier.size(48.dp)) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            state.errors.quantityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("Nota (opcional)") },
                placeholder = { Text("Ej.: botellas de gaseosa") },
                isError = state.errors.noteError != null,
                supportingText = { Text(state.errors.noteError ?: "${state.note.length}/${ActivityValidator.MAX_NOTE}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            state.pointsPreview?.let {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        (if (state.isEditing) "Valdrá $it puntos" else "Ganarás $it puntos"),
                        color = ReciColors.AmberInk, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(ReciColors.AmberSoft, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }
            Button(
                onClick = { viewModel.submit() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) { Text(if (state.isEditing) "Guardar cambios" else "Registrar", fontWeight = FontWeight.Bold) }
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PhotoCard(photoPath: String?, onTake: () -> Unit, onRemove: () -> Unit) {
    if (photoPath == null) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .dashedBorder(ReciColors.Line)
                .background(ReciColors.Card, RoundedCornerShape(20.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(52.dp).background(ReciColors.GreenSoft, CircleShape), contentAlignment = Alignment.Center) {
                CameraGlyph(color = ReciColors.Green, size = 26.dp)
            }
            Text("Foto de lo que vas a depositar", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("Opcional · se guarda solo en tu teléfono", style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
            Button(onClick = onTake, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) { Text("Tomar foto", fontWeight = FontWeight.Bold) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().height(168.dp).background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PhotoImage(
                photoPath,
                Modifier.width(144.dp).fillMaxSize().clip(RoundedCornerShape(14.dp)),
                contentDescription = "Foto de lo que vas a depositar"
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("Foto lista", color = ReciColors.Green, fontWeight = FontWeight.Bold)
                Text(
                    "Se guardará con este registro, solo en tu teléfono.",
                    style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                OutlinedButton(onClick = onTake, modifier = Modifier.heightIn(min = 48.dp)) { Text("Repetir foto") }
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Quitar foto", color = ReciColors.Danger, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
