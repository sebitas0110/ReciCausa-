package pe.edu.upn.recicausa.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import pe.edu.upn.recicausa.domain.Catalog
import pe.edu.upn.recicausa.domain.Geo
import pe.edu.upn.recicausa.domain.PuntoConDistancia
import pe.edu.upn.recicausa.ui.components.PuntosOsmMap
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.viewmodel.PuntosViewModel

// Sprint 5 - Puntos de reciclaje (HU10, HU11): lista, filtro por residuo, mapa (OpenStreetMap) y orden por cercanía.

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
)

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Devuelve una función que pide el permiso de ubicación SOLO cuando se llama (CA11.1), es decir,
 * al tocar el botón; si ya estaba concedido, no vuelve a preguntar.
 */
@Composable
fun rememberLocationRequester(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) onGranted() else onDenied()
    }
    return {
        if (hasLocationPermission(context)) onGranted() else launcher.launch(LOCATION_PERMISSIONS)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PuntosScreen(
    viewModel: PuntosViewModel,
    onSuggest: () -> Unit,
    onMySuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showMap by remember { mutableStateOf(false) }
    val requestLocation = rememberLocationRequester(
        onGranted = viewModel::useMyLocation,
        onDenied = viewModel::onLocationDenied
    )

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Puntos de reciclaje", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Encuentra dónde entregar tus residuos en Trujillo.", style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted)

            // Lista / Mapa
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showMap, onClick = { showMap = false }, label = { Text("Lista") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = showMap, onClick = { showMap = true }, label = { Text("Mapa") }, modifier = Modifier.heightIn(min = 48.dp))
            }

            // Filtro por tipo de residuo (CA10.2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.categoryFilter == null, onClick = { viewModel.onFilterChange(null) },
                    label = { Text("Todos") }, modifier = Modifier.heightIn(min = 48.dp)
                )
                Catalog.categories.forEach { c ->
                    FilterChip(
                        selected = state.categoryFilter == c.id, onClick = { viewModel.onFilterChange(c.id) },
                        label = { Text(c.name) }, modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            // Cerca de mí (CA11.1) · Sugerir · Mis sugerencias
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.nearMe) {
                    OutlinedButton(onClick = viewModel::clearNearMe, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ordenar por nombre") }
                } else {
                    Button(onClick = requestLocation, enabled = !state.locating, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (state.locating) "Buscando…" else "Cerca de mí", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onSuggest, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sugerir un punto") }
                TextButton(onClick = onMySuggestions, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mis sugerencias") }
            }
            if (!state.nearMe && state.locationNotice == null) {
                Text(
                    "Usamos tu ubicación solo para ordenar la lista. No se guarda ni se envía.",
                    style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
                )
            }
            state.locationNotice?.let { Banner(it, error = false) }
            if (state.showingCache) {
                Banner("${state.error} Mostrando los últimos puntos descargados.", error = false, actionLabel = "Reintentar", onAction = viewModel::refresh)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            when {
                state.loading && state.totalDownloaded == 0 -> LoadingBox("Cargando puntos…")
                state.error != null && state.totalDownloaded == 0 -> ErrorBox(state.error!!, viewModel::refresh)
                state.items.isEmpty() -> EmptyBox(
                    if (state.categoryFilter == null) "Aún no hay puntos aprobados. ¡Sugiere el primero!"
                    else "No hay puntos para este tipo de residuo todavía."
                )
                showMap -> PuntosOsmMap(state.items, Modifier.fillMaxSize())
                else -> LazyColumn(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.punto.id }) { PuntoCard(it) }
                    item { Box(Modifier.padding(bottom = 12.dp)) }
                }
            }
            if (state.loading && state.totalDownloaded > 0) {
                CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun PuntoCard(item: PuntoConDistancia) {
    val p = item.punto
    Column(
        Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(p.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            item.distanciaKm?.let {
                Text(
                    Geo.formatDistance(it), color = ReciColors.GreenDark, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).background(ReciColors.GreenSoft, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Text(p.direccion, style = MaterialTheme.typography.bodyMedium)
        // CA10.1: residuos que reciben, en texto (el color no es la única señal).
        Text(
            "Recibe: " + p.categorias.mapNotNull { Catalog.categoryById(it)?.name }.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium, color = ReciColors.Ink
        )
        if (p.horario.isNotBlank()) Text("Horario: ${p.horario}", style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
        if (p.telefono.isNotBlank()) Text("Tel.: ${p.telefono}", style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
        if (p.descripcion.isNotBlank()) Text(p.descripcion, style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted)
    }
}

// ---------- Estados reutilizables: cargando, error, vacío, aviso ----------
@Composable
fun LoadingBox(message: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text(message, modifier = Modifier.padding(top = 12.dp), color = ReciColors.Muted)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message, fontWeight = FontWeight.Bold, color = ReciColors.Danger,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text("Reintentar", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun EmptyBox(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = ReciColors.Muted)
    }
}

@Composable
fun Banner(text: String, error: Boolean, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (error) ReciColors.AmberSoft else ReciColors.AmberSoft, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ReciColors.AmberInk)
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel, fontWeight = FontWeight.Bold) }
        }
    }
}
