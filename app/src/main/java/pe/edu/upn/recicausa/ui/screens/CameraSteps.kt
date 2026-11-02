package pe.edu.upn.recicausa.ui.screens

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import pe.edu.upn.recicausa.ui.components.CameraGlyph
import pe.edu.upn.recicausa.ui.theme.ReciColors

// Pasos de la foto: 1) explicar para qué se pide la cámara, 2) visor con CameraX.
// El permiso se pide solo cuando el usuario toca "Tomar foto", nunca al abrir la app.

@Composable
fun CameraPermissionStep(
    denied: Boolean,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(112.dp).background(ReciColors.GreenSoft, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).background(ReciColors.Green, CircleShape), contentAlignment = Alignment.Center) {
                CameraGlyph(color = Color.White, size = 40.dp)
            }
        }
        Text(
            "Para tomar la foto necesitamos tu cámara",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            "Así puedes dejar constancia de lo que depositas. Es totalmente opcional.",
            style = MaterialTheme.typography.bodyLarge, color = ReciColors.Muted, textAlign = TextAlign.Center
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Bullet("La cámara se usa solo mientras tomas la foto.")
            Bullet("La foto se guarda solo en este teléfono; no se envía a ningún servidor.")
            Bullet("Puedes registrar tus actividades sin foto y cambiar el permiso cuando quieras en los ajustes del teléfono.")
        }
        if (denied) {
            Text(
                "El permiso fue rechazado, así que no podemos abrir la cámara. Puedes registrar sin foto o activarlo en los ajustes del teléfono.",
                color = ReciColors.Danger, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.size(4.dp))
        if (denied) {
            Button(
                onClick = onOpenSettings, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) { Text("Abrir ajustes", fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = onAllow, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) { Text("Permitir cámara", fontWeight = FontWeight.Bold) }
        }
        OutlinedButton(
            onClick = onSkip, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) { Text(if (denied) "Registrar sin foto" else "Ahora no", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✓", color = ReciColors.Green, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ continuation.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }

@Composable
fun CameraStep(
    newPhotoFile: () -> File,
    onCaptured: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var lens by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Enlaza la cámara al ciclo de vida; se vuelve a ejecutar solo al cambiar de lente.
    LaunchedEffect(lens) {
        try {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.Builder().requireLensFacing(lens).build(), preview, imageCapture
            )
            error = null
        } catch (e: Exception) {
            error = "No se pudo abrir la cámara de este teléfono."
        }
    }
    // Al salir del visor se libera la cámara (RNF06: sin uso en segundo plano).
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }

    Column(
        modifier = modifier.fillMaxSize().background(ReciColors.CameraBg).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1B2A21))
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    error ?: "Encuadra los residuos que vas a depositar",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(ReciColors.CameraBg.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            Box(
                Modifier
                    .size(80.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .background(if (capturing) Color.Gray else Color.White, CircleShape)
                    .semantics { contentDescription = "Tomar foto" }
                    .clickable(enabled = !capturing && error == null) {
                        val file = newPhotoFile()
                        capturing = true
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    capturing = false
                                    onCaptured(file.absolutePath)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                    error = "No se pudo guardar la foto. Inténtalo de nuevo."
                                }
                            }
                        )
                    }
            )
            TextButton(
                onClick = {
                    lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                    else CameraSelector.LENS_FACING_BACK
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Girar", color = Color.White, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
