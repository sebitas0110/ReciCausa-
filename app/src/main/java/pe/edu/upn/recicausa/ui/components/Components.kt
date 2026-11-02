package pe.edu.upn.recicausa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.edu.upn.recicausa.domain.BinColor
import pe.edu.upn.recicausa.domain.ChallengeProgress
import pe.edu.upn.recicausa.ui.theme.BinColors

// Sprint 1 - Componentes reutilizables.

/** Etiqueta del contenedor. Incluye el nombre del color en texto: el color nunca es la única señal. */
@Composable
fun BinBadge(bin: BinColor, modifier: Modifier = Modifier) {
    val color = when (bin) {
        BinColor.VERDE -> BinColors.Verde
        BinColor.NEGRO -> BinColors.Negro
        BinColor.MARRON -> BinColors.Marron
        BinColor.ROJO -> BinColors.Rojo
    }
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = "Contenedor ${bin.label}" }
    ) {
        Text(bin.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun PointsHeader(totalPoints: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Tus puntos", style = MaterialTheme.typography.labelLarge)
            Text("$totalPoints", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ChallengeCard(item: ChallengeProgress, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.challenge.title, style = MaterialTheme.typography.titleMedium)
                if (item.completed) Text("Completado", color = MaterialTheme.colorScheme.primary)
            }
            // RN05: cada reto indica claramente la acción necesaria para completarlo.
            Text(item.challenge.description, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { item.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "${item.current.coerceAtMost(item.challenge.target)} de ${item.challenge.target}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
