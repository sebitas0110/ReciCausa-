package pe.edu.upn.recicausa.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.edu.upn.recicausa.data.remote.ApiError
import pe.edu.upn.recicausa.data.remote.ApiResult
import pe.edu.upn.recicausa.data.remote.AuthResponse
import pe.edu.upn.recicausa.data.remote.SignInRequest
import pe.edu.upn.recicausa.data.remote.SignUpData
import pe.edu.upn.recicausa.data.remote.SignUpRequest
import pe.edu.upn.recicausa.data.remote.SupabaseApi
import pe.edu.upn.recicausa.data.remote.safeApiCall
import pe.edu.upn.recicausa.domain.AuthValidator
import pe.edu.upn.recicausa.domain.User

// Sprint 5 - Cuentas con Supabase Auth. La contraseña viaja por HTTPS a Supabase y no se guarda
// en el teléfono (RN08); en el teléfono solo queda la sesión (tokens) en DataStore.

class RemoteAuthRepository(
    private val api: SupabaseApi,
    private val session: SessionRepository
) : AuthRepository {

    override val currentUser: Flow<User?> = session.session.map { s ->
        s?.let { User(id = it.userId, name = it.name, email = it.email) }
    }

    override suspend fun register(name: String, email: String, password: String): AuthResult {
        val cleanName = name.trim()
        val cleanEmail = AuthValidator.normalizeEmail(email)
        return when (val r = safeApiCall { api.signUp(SignUpRequest(cleanEmail, password, SignUpData(cleanName))) }) {
            is ApiResult.Failure -> AuthResult.Failure(AuthMessages.forSignUp(r))
            is ApiResult.Success -> store(r.value, fallbackName = cleanName, fallbackEmail = cleanEmail)
                ?: AuthResult.Failure(
                    "Te enviamos un correo de confirmación. Confírmalo y luego inicia sesión."
                )
        }
    }

    override suspend fun login(email: String, password: String): AuthResult {
        val cleanEmail = AuthValidator.normalizeEmail(email)
        return when (val r = safeApiCall { api.signIn(SignInRequest(cleanEmail, password)) }) {
            is ApiResult.Failure -> AuthResult.Failure(AuthMessages.forLogin(r))
            is ApiResult.Success -> store(r.value, fallbackName = "", fallbackEmail = cleanEmail)
                ?: AuthResult.Failure("No se pudo iniciar sesión. Inténtalo de nuevo.")
        }
    }

    override suspend fun logout() {
        // Se cierra la sesión local siempre; avisar al servidor es "lo mejor posible".
        runCatching { safeApiCall { api.signOut() } }
        session.clear()
    }

    /** Guarda la sesión; devuelve null si la respuesta no trae tokens (cuenta por confirmar). */
    private suspend fun store(auth: AuthResponse, fallbackName: String, fallbackEmail: String): AuthResult? {
        val access = auth.accessToken ?: return null
        val refresh = auth.refreshToken ?: return null
        val user = auth.user ?: return null
        session.save(
            StoredSession(
                accessToken = access, refreshToken = refresh, userId = user.id,
                name = user.userMetadata?.nombre?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "Usuario" },
                email = user.email ?: fallbackEmail
            )
        )
        return AuthResult.Success
    }
}

/** Mensajes de autenticación. Un único mensaje genérico para credenciales erróneas (CA07.3). */
object AuthMessages {
    fun forLogin(f: ApiResult.Failure): String = when {
        f.code == "invalid_credentials" || f.error is ApiError.Validation || f.error == ApiError.Unauthorized ->
            "Correo o contraseña incorrectos."
        f.code == "email_not_confirmed" -> "Confirma tu correo antes de iniciar sesión."
        f.error == ApiError.Network -> "Para iniciar sesión necesitas conexión a internet."
        else -> f.error.userMessage
    }

    fun forSignUp(f: ApiResult.Failure): String = when {
        f.code == "user_already_exists" || f.code == "email_exists" || f.error is ApiError.Conflict ->
            "Ya existe una cuenta con ese correo." // CA07.2
        f.code == "weak_password" -> "La contraseña es muy débil. Usa al menos 8 caracteres."
        f.code == "over_email_send_rate_limit" || f.error == ApiError.RateLimited ->
            "Se enviaron demasiados correos. Espera unos minutos e inténtalo otra vez."
        f.error is ApiError.Validation -> "Revisa el correo y la contraseña."
        f.error == ApiError.Network -> "Para crear tu cuenta necesitas conexión a internet."
        else -> f.error.userMessage
    }
}
