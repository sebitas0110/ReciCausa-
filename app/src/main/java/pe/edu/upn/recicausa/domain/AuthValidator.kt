package pe.edu.upn.recicausa.domain

// Validaciones de inicio de sesión y registro de cuenta. Dominio puro: se prueba sin emulador.

object AuthValidator {
    const val MIN_PASSWORD = 8
    const val MIN_NAME = 2
    const val MAX_NAME = 40

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    data class LoginResult(
        val emailError: String? = null,
        val passwordError: String? = null
    ) {
        val isValid: Boolean get() = emailError == null && passwordError == null
    }

    data class SignupResult(
        val nameError: String? = null,
        val emailError: String? = null,
        val passwordError: String? = null,
        val confirmError: String? = null
    ) {
        val isValid: Boolean
            get() = nameError == null && emailError == null && passwordError == null && confirmError == null
    }

    /** Los correos se comparan siempre en minúsculas y sin espacios en los extremos. */
    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun validateLogin(email: String, password: String): LoginResult = LoginResult(
        emailError = emailError(email),
        passwordError = if (password.isEmpty()) "Ingresa tu contraseña." else null
    )

    fun validateSignup(name: String, email: String, password: String, confirm: String): SignupResult {
        val trimmed = name.trim()
        val nameError = when {
            trimmed.length < MIN_NAME -> "Escribe tu nombre (mínimo $MIN_NAME letras)."
            trimmed.length > MAX_NAME -> "Máximo $MAX_NAME caracteres."
            else -> null
        }
        val passwordError =
            if (password.length < MIN_PASSWORD) "Mínimo $MIN_PASSWORD caracteres." else null
        val confirmError = when {
            confirm.isEmpty() -> "Repite la contraseña."
            confirm != password -> "Las contraseñas no coinciden."
            else -> null
        }
        return SignupResult(nameError, emailError(email), passwordError, confirmError)
    }

    private fun emailError(email: String): String? = when {
        email.isBlank() -> "Ingresa tu correo."
        !EMAIL.matches(email.trim()) -> "Escribe un correo válido."
        else -> null
    }
}
