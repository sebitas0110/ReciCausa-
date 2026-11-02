package pe.edu.upn.recicausa.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.edu.upn.recicausa.ui.theme.ReciColors

// Utilidades para mostrar las fotos de las actividades y dibujar el icono de cámara.

private object PhotoDecoder {
    /** Decodifica reducida (para no gastar memoria) y respeta la orientación EXIF que deja CameraX. */
    fun decode(path: String, maxSide: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bitmap == null) null else {
            val orientation = ExifInterface(path)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val rotated = if (degrees == 0f) bitmap else Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true
            )
            rotated.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}

/** Foto de una actividad. Se carga fuera del hilo principal; mientras tanto muestra un fondo neutro. */
@Composable
fun PhotoImage(path: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { PhotoDecoder.decode(path, maxSide = 640) }
    }
    val loaded = bitmap
    if (loaded == null) {
        Box(modifier.background(ReciColors.Track))
    } else {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
fun PhotoThumb(path: String, size: Dp = 48.dp, contentDescription: String? = null) {
    PhotoImage(path, Modifier.size(size).clip(RoundedCornerShape(12.dp)), contentDescription)
}

/** Icono de cámara dibujado a mano (evita agregar toda la librería de iconos extendidos). */
@Composable
fun CameraGlyph(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .drawBehind {
                val w = this.size.width
                val h = this.size.height
                val stroke = Stroke(width = w * 0.075f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.08f, h * 0.28f),
                    size = Size(w * 0.84f, h * 0.58f),
                    cornerRadius = CornerRadius(w * 0.1f),
                    style = stroke
                )
                drawCircle(color = color, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.57f), style = stroke)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.34f, h * 0.14f),
                    size = Size(w * 0.32f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.05f)
                )
            }
    )
}

/** Borde punteado para la tarjeta "Tomar foto". */
fun Modifier.dashedBorder(color: Color, radius: Dp = 20.dp, strokeWidth: Dp = 2.dp): Modifier =
    this.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
            )
        )
    }
