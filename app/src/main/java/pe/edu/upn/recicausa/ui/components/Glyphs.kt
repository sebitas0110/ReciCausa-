package pe.edu.upn.recicausa.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hoja: marca de ReciCausa. Dibujada a mano para no depender de librerías de iconos. */
@Composable
fun LeafGlyph(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .drawBehind {
                val s = this.size.width / 24f
                val leaf = Path().apply {
                    moveTo(5 * s, 19 * s)
                    cubicTo(5 * s, 11 * s, 10 * s, 5 * s, 19 * s, 5 * s)
                    cubicTo(19 * s, 14 * s, 13 * s, 19 * s, 5 * s, 19 * s)
                    close()
                }
                val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(leaf, color, style = stroke)
                drawLine(color, Offset(5 * s, 19 * s), Offset(13 * s, 11 * s), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
            }
    )
}
