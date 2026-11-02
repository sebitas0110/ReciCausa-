package pe.edu.upn.recicausa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import pe.edu.upn.recicausa.domain.BinColor

// Tema Material Design 3 con la paleta del diseño (Claude Design).
// Contraste: texto blanco sobre Green = 6.6:1; Ink sobre Ground = 14:1; Muted sobre Ground = 5.9:1 (RNF02).

object ReciColors {
    val Ground = Color(0xFFF4F1E8)
    val Ink = Color(0xFF15241A)
    val Muted = Color(0xFF4A5A4F)
    val Green = Color(0xFF1F6B34)
    val GreenDark = Color(0xFF164D26)
    val GreenSoft = Color(0xFFDCEBD9)
    val Track = Color(0xFFE3E8DE)
    val Line = Color(0xFF8C9B8F)
    val Amber = Color(0xFFF7C65A)
    val AmberSoft = Color(0xFFF7DFA3)
    val AmberInk = Color(0xFF4A3300)
    val Card = Color.White
    val Danger = Color(0xFF8A1C14)
    val CameraBg = Color(0xFF0D1711)
}

private val Colors = lightColorScheme(
    primary = ReciColors.Green,
    onPrimary = Color.White,
    primaryContainer = ReciColors.GreenSoft,
    onPrimaryContainer = ReciColors.GreenDark,
    secondary = ReciColors.Muted,
    onSecondary = Color.White,
    secondaryContainer = ReciColors.GreenSoft,
    onSecondaryContainer = ReciColors.GreenDark,
    background = ReciColors.Ground,
    onBackground = ReciColors.Ink,
    surface = ReciColors.Ground,
    onSurface = ReciColors.Ink,
    surfaceVariant = ReciColors.Track,
    onSurfaceVariant = ReciColors.Muted,
    surfaceContainer = ReciColors.Card,
    surfaceContainerHigh = ReciColors.Card,
    surfaceContainerHighest = ReciColors.Card,
    outline = ReciColors.Line,
    error = ReciColors.Danger
)

@Composable
fun ReciCausaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

/** Colores de contenedor NTP 900.058:2019. */
object BinColors {
    val Verde = Color(0xFF2E7D32)
    val Negro = Color(0xFF212121)
    val Marron = Color(0xFF6D4C41)
    val Rojo = Color(0xFFC62828)
}

fun BinColor.toColor(): Color = when (this) {
    BinColor.VERDE -> BinColors.Verde
    BinColor.NEGRO -> BinColors.Negro
    BinColor.MARRON -> BinColors.Marron
    BinColor.ROJO -> BinColors.Rojo
}
