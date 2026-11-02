package pe.edu.upn.recicausa.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.Response

// Sprint 5 - Manejo de respuestas y errores de los servicios web (semana 9: estados de carga, errores y conectividad).

/** Clasificación de los errores de red/HTTP. La UI muestra [userMessage]; nunca el error crudo. */
sealed interface ApiError {
    data object Network : ApiError                     // sin internet / servidor inalcanzable
    data object Timeout : ApiError                     // el servidor tardó demasiado
    data object Unauthorized : ApiError                // 401: sesión vencida o credenciales inválidas
    data object Forbidden : ApiError                   // 403: lo bloquea RLS
    data class Conflict(val detail: String?) : ApiError // 409: dato duplicado
    data class Validation(val detail: String?) : ApiError // 400/422: datos rechazados
    data object RateLimited : ApiError                 // 429
    data class Server(val code: Int) : ApiError        // 5xx
    data class Unknown(val code: Int, val detail: String?) : ApiError

    val userMessage: String
        get() = when (this) {
            Network -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
            Timeout -> "El servidor tardó demasiado en responder. Inténtalo de nuevo."
            Unauthorized -> "Tu sesión venció. Vuelve a iniciar sesión."
            Forbidden -> "No tienes permiso para hacer esta acción."
            is Conflict -> "Ese dato ya existe."
            is Validation -> detail?.takeIf { it.isNotBlank() } ?: "Los datos enviados no son válidos."
            RateLimited -> "Demasiados intentos. Espera un momento e inténtalo otra vez."
            is Server -> "El servidor tuvo un problema. Inténtalo más tarde."
            is Unknown -> "Ocurrió un error inesperado."
        }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError, val code: String? = null) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

object ApiErrors {
    private val json = Json { ignoreUnknownKeys = true }

    /** Código de error de Supabase Auth (`error_code`) o PostgREST (`code`), si viene en el cuerpo. */
    fun errorCode(body: String?): String? = parse(body)?.let {
        (it["error_code"] as? JsonPrimitive)?.contentOrNull ?: (it["code"] as? JsonPrimitive)?.contentOrNull
    }

    /** Mensaje técnico del servidor (solo para registros de depuración). */
    fun detail(body: String?): String? = parse(body)?.let {
        listOf("msg", "message", "error_description", "error").firstNotNullOfOrNull { k ->
            (it[k] as? JsonPrimitive)?.contentOrNull
        }
    }

    private fun parse(body: String?): JsonObject? =
        if (body.isNullOrBlank()) null else runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    /** CP39: clasifica una respuesta HTTP fallida. */
    fun classify(httpCode: Int, body: String?): ApiError {
        val detail = detail(body)
        return when (httpCode) {
            400, 422 -> ApiError.Validation(null)
            401 -> ApiError.Unauthorized
            403 -> ApiError.Forbidden
            409 -> ApiError.Conflict(detail)
            429 -> ApiError.RateLimited
            in 500..599 -> ApiError.Server(httpCode)
            else -> ApiError.Unknown(httpCode, detail)
        }
    }

    fun classify(e: Throwable): ApiError = when (e) {
        is SocketTimeoutException -> ApiError.Timeout
        is IOException -> ApiError.Network
        else -> ApiError.Unknown(-1, e.message)
    }
}

/** Ejecuta la llamada y devuelve siempre un [ApiResult]: nunca lanza por red o por HTTP. */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> = try {
    val response = call()
    if (response.isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        ApiResult.Success(response.body() as T) // Response<Unit> devuelve Unit; 204 sin cuerpo se trata abajo
    } else {
        val body = runCatching { response.errorBody()?.string() }.getOrNull()
        ApiResult.Failure(ApiErrors.classify(response.code(), body), ApiErrors.errorCode(body))
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    ApiResult.Failure(ApiErrors.classify(e))
}
