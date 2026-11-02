package pe.edu.upn.recicausa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import pe.edu.upn.recicausa.domain.Catalog
import pe.edu.upn.recicausa.domain.CategoryCount
import pe.edu.upn.recicausa.domain.ChallengeProgress
import pe.edu.upn.recicausa.domain.RecycleActivity
import pe.edu.upn.recicausa.ui.components.EmptyState
import pe.edu.upn.recicausa.ui.components.LeafGlyph
import pe.edu.upn.recicausa.ui.components.PhotoThumb
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.theme.toColor
import pe.edu.upn.recicausa.ui.viewmodel.HomeUiState
import pe.edu.upn.recicausa.ui.viewmodel.HomeViewModel

// Inicio: resumen del progreso (reemplaza a la antigua pestaña "Progreso").

@Composable
fun HomeScreen(viewModel: HomeViewModel, onLogout: () -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM · HH:mm", Locale.forLanguageTag("es-PE")) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HomeHeader(state.userName, onLogout) }
        item { PointsCard(state) }
        item { ChallengeSummary(state.nextChallenge, allDone = state.challengesTotal > 0 && state.nextChallenge == null) }
        if (state.totalActivities == 0) {
            item { EmptyState("Aún no registras actividades. ¡Empieza hoy!") }
        } else {
            item { CategoriesCard(state.topCategories) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Últimas actividades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Ver y editar", fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(state.recent, key = { it.id }) { RecentRow(it, dateFormat) }
        }
    }
}

@Composable
private fun HomeHeader(userName: String, onLogout: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                if (userName.isNotEmpty()) "Hola, $userName" else "Hola",
                style = MaterialTheme.typography.bodyLarge, color = ReciColors.Muted
            )
            Text("Tu progreso", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Box {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ReciColors.GreenSoft)
                    .semantics { contentDescription = "Cuenta de $userName. Abrir menú" }
                    .clickable(role = Role.Button) { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    userName.firstOrNull()?.uppercase() ?: "?",
                    color = ReciColors.GreenDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Cerrar sesión") }, onClick = { menuOpen = false; onLogout() })
            }
        }
    }
}

@Composable
private fun PointsCard(state: HomeUiState) {
    Column(
        Modifier.fillMaxWidth().background(ReciColors.Green, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Puntos acumulados", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${state.totalPoints}", color = ReciColors.Amber,
                    style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold
                )
            }
            LeafGlyph(color = Color.White, size = 44.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${state.totalActivities}", "actividades", Modifier.weight(1f))
            StatTile("${state.challengesDone} de ${state.challengesTotal}", "retos completados", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChallengeSummary(next: ChallengeProgress?, allDone: Boolean) {
    if (next == null && !allDone) return
    Column(
        Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (next == null) {
            Text("¡Completaste todos los retos!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reto en curso: ${next.challenge.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${next.current} de ${next.challenge.target}", color = ReciColors.Green, fontWeight = FontWeight.Bold)
        }
        Text(next.challenge.description, style = MaterialTheme.typography.bodyMedium, color = ReciColors.Muted)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(ReciColors.Track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(next.fraction).background(ReciColors.Green, RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
private fun CategoriesCard(top: List<CategoryCount>) {
    val max = top.maxOfOrNull { it.count } ?: 1
    Column(
        Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Por categoría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        top.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.category.name, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(ReciColors.Track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(item.count.toFloat() / max).background(item.category.bin.toColor()))
                }
                Text("${item.count}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RecentRow(activity: RecycleActivity, dateFormat: SimpleDateFormat) {
    val category = Catalog.categoryById(activity.categoryId)
    Row(
        Modifier.fillMaxWidth().background(ReciColors.Card, RoundedCornerShape(16.dp)).padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            Text(
                dateFormat.format(Date(activity.createdAt)) + if (activity.photoPath != null) " · con foto" else " · sin foto",
                style = MaterialTheme.typography.bodySmall, color = ReciColors.Muted
            )
        }
        Text("+${activity.points} pts", color = ReciColors.Green, fontWeight = FontWeight.Bold)
    }
}
