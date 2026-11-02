package pe.edu.upn.recicausa.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pe.edu.upn.recicausa.ui.components.LeafGlyph
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.viewmodel.LoginViewModel
import pe.edu.upn.recicausa.ui.viewmodel.SignupViewModel

// Iniciar sesión y crear cuenta. Los errores aparecen junto a cada campo (como en el diseño).

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    isPassword: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                TextButton(onClick = { visible = !visible }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (visible) "Ocultar" else "Mostrar")
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun FormError(message: String?) {
    if (message == null) return
    Text(
        text = message,
        color = ReciColors.Danger,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite } // los lectores de pantalla lo anuncian
    )
}

@Composable
private fun PrivacyNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ReciColors.GreenSoft, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ReciColors.Ink)
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel, onGoSignup: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier.size(56.dp).background(ReciColors.Green, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) { LeafGlyph(color = androidx.compose.ui.graphics.Color.White, size = 30.dp) }
        Text("ReciCausa", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Aprende, separa y suma puntos.", style = MaterialTheme.typography.bodyLarge, color = ReciColors.Muted)
        Spacer(Modifier.size(12.dp))

        AuthField(
            label = "Correo", value = state.email, onValueChange = viewModel::onEmailChange,
            error = state.errors.emailError, placeholder = "tucorreo@ejemplo.com",
            keyboardType = KeyboardType.Email
        )
        AuthField(
            label = "Contraseña", value = state.password, onValueChange = viewModel::onPasswordChange,
            error = state.errors.passwordError, keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done, onDone = viewModel::submit, isPassword = true
        )
        FormError(state.formError)

        Button(
            onClick = viewModel::submit,
            enabled = !state.loading,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) { Text(if (state.loading) "Ingresando…" else "Ingresar", fontWeight = FontWeight.Bold) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("¿Aún no tienes cuenta?", color = ReciColors.Muted)
            TextButton(onClick = onGoSignup, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Crear cuenta", fontWeight = FontWeight.Bold)
            }
        }
        PrivacyNote("Tu cuenta se gestiona con Supabase y tus actividades se respaldan en la nube. Para iniciar sesión necesitas internet; después la app funciona sin conexión.")
    }
}

@Composable
fun SignupScreen(viewModel: SignupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Volver") }
        Text("Crea tu cuenta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Solo te pedimos lo necesario para guardar tu progreso.",
            style = MaterialTheme.typography.bodyLarge, color = ReciColors.Muted
        )

        AuthField(
            label = "Nombre", value = state.name, onValueChange = viewModel::onNameChange,
            error = state.errors.nameError, placeholder = "¿Cómo quieres que te llamemos?"
        )
        AuthField(
            label = "Correo", value = state.email, onValueChange = viewModel::onEmailChange,
            error = state.errors.emailError, placeholder = "tucorreo@ejemplo.com",
            keyboardType = KeyboardType.Email
        )
        AuthField(
            label = "Contraseña", value = state.password, onValueChange = viewModel::onPasswordChange,
            error = state.errors.passwordError, placeholder = "Mínimo 8 caracteres",
            keyboardType = KeyboardType.Password, isPassword = true
        )
        AuthField(
            label = "Repite la contraseña", value = state.confirm, onValueChange = viewModel::onConfirmChange,
            error = state.errors.confirmError, keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done, onDone = viewModel::submit, isPassword = true
        )
        FormError(state.formError)

        Button(
            onClick = viewModel::submit,
            enabled = !state.loading,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) { Text(if (state.loading) "Creando cuenta…" else "Crear cuenta", fontWeight = FontWeight.Bold) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("¿Ya tienes cuenta?", color = ReciColors.Muted)
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Iniciar sesión", fontWeight = FontWeight.Bold)
            }
        }
        PrivacyNote("Enviamos solo tu nombre, correo y actividades. Tu contraseña la protege Supabase y no se guarda en el teléfono.")
    }
}
