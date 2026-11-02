package pe.edu.upn.recicausa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.edu.upn.recicausa.domain.BinColor
import pe.edu.upn.recicausa.domain.WasteCategory
import pe.edu.upn.recicausa.ui.components.BinBadge
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.theme.toColor
import pe.edu.upn.recicausa.ui.viewmodel.GuideViewModel

// HU01: guía de residuos (RN01: se consulta sin registrar nada antes).
// Filtro por contenedor + tarjetas con franja del color NTP; el color siempre va con su nombre en texto.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideScreen(viewModel: GuideViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf<String?>(null) } // BinColor.name o null = todos
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    val shown = state.categories.filter { filter == null || it.bin.name == filter }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Guía de residuos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Aprende en qué contenedor va cada residuo (NTP 900.058:2019).",
                    style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted
                )
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == null, onClick = { filter = null },
                    label = { Text("Todos") }, modifier = Modifier.heightIn(min = 48.dp)
                )
                BinColor.entries.forEach { bin ->
                    FilterChip(
                        selected = filter == bin.name,
                        onClick = { filter = if (filter == bin.name) null else bin.name },
                        label = { Text(bin.label.substringBefore(" ·")) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(bin.toColor(), CircleShape)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
        items(shown, key = { it.id }) { category ->
            CategoryCard(
                category = category,
                expanded = expandedId == category.id,
                onToggle = { expandedId = if (expandedId == category.id) null else category.id }
            )
        }
        item { Box(Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(category: WasteCategory, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(20.dp))
            .background(ReciColors.Card)
            .clickable(onClick = onToggle)
    ) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(category.bin.toColor()))
        Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (expanded) "▲" else "▼", color = ReciColors.Muted)
            }
            BinBadge(category.bin)
            Text(
                "${category.pointsPerUnit} puntos por unidad",
                style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
            )
            if (expanded) {
                Text("Ejemplos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    category.examples.forEach {
                        Text(
                            it, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.background(ReciColors.GreenSoft, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    "Consejo: ${category.tip}",
                    style = MaterialTheme.typography.bodyMedium, color = ReciColors.AmberInk,
                    modifier = Modifier.fillMaxWidth().background(ReciColors.AmberSoft, RoundedCornerShape(14.dp)).padding(12.dp)
                )
            } else {
                Text(
                    category.examples.take(3).joinToString(", ") + "…",
                    style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted
                )
            }
        }
    }
}
