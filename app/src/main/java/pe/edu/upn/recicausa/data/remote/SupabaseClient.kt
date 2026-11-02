package pe.edu.upn.recicausa.data.remote

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import pe.edu.upn.recicausa.data.repository.SessionRepository
import pe.edu.upn.recicausa.data.repository.StoredSession

// Sprint 5 - Cliente HTTP: cabeceras, renovación de sesión y construcción de Retrofit.

val SupabaseJson: Json = Json {
    ignoreUnknownKeys = true   // el servidor puede agregar campos sin romper la app
    explicitNulls = false      // no enviar null: la base aplica sus valores por defecto
    encodeDefaults = true
}

/**
 * Agrega a cada petición la cabecera `apikey` (clave publishable) y, si hay sesión,
 * `Authorization: Bearer <access_token>` para que las políticas RLS identifiquen al usuario.
 */
class HeadersInterceptor(
    private val apiKey: String,
    private val session: SessionRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("apikey", apiKey)
            .header("Accept", "application/json")
        session.peek()?.let { builder.header("Authorization", "Bearer ${it.accessToken}") }
        return chain.proceed(builder.build())
    }
}

/**
 * Ante un 401 intenta renovar el token con el refresh_token y reintenta la petición UNA vez.
 * Si la renovación falla, cierra la sesión local (el usuario debe volver a iniciar sesión).
 * Usa [plainApi], un cliente sin este Authenticator, para no entrar en un ciclo.
 */
class TokenAuthenticator(
    private val session: SessionRepository,
    private val plainApi: SupabaseApi
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Ya se reintentó con un token nuevo y volvió a fallar: no insistir.
        if (response.priorResponse != null) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val current = session.peek() ?: return null

        return synchronized(this) {
            // Otro hilo pudo renovar mientras esperábamos el candado.
            val latest = session.peek()
            if (latest != null && latest.accessToken != failedToken) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer ${latest.accessToken}").build()
            }
            val renewed = runBlocking { safeApiCall { plainApi.refresh(RefreshRequest(current.refreshToken)) } }
            val auth = (renewed as? ApiResult.Success)?.value
            val newAccess = auth?.accessToken
            val newRefresh = auth?.refreshToken
            if (newAccess == null || newRefresh == null) {
                // Solo se cierra la sesión si el servidor rechazó el refresh; un fallo de red no la borra.
                if (renewed is ApiResult.Failure && renewed.error != ApiError.Network && renewed.error != ApiError.Timeout) {
                    runBlocking { session.clear() }
                }
                null
            } else {
                runBlocking { session.save(current.copy(accessToken = newAccess, refreshToken = newRefresh)) }
                response.request.newBuilder().header("Authorization", "Bearer $newAccess").build()
            }
        }
    }
}

object SupabaseClient {
    /** Cliente sin Authenticator (lo usa el propio Authenticator para renovar). */
    fun createPlain(baseUrl: String, apiKey: String, session: SessionRepository, okHttp: OkHttpClient? = null): SupabaseApi =
        retrofit(baseUrl, (okHttp?.newBuilder() ?: baseClient())
            .addInterceptor(HeadersInterceptor(apiKey, session)).build())

    fun create(baseUrl: String, apiKey: String, session: SessionRepository, okHttp: OkHttpClient? = null): SupabaseApi {
        val plain = createPlain(baseUrl, apiKey, session, okHttp)
        val client = (okHttp?.newBuilder() ?: baseClient())
            .addInterceptor(HeadersInterceptor(apiKey, session))
            .authenticator(TokenAuthenticator(session, plain))
            .build()
        return retrofit(baseUrl, client)
    }

    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)

    private fun retrofit(baseUrl: String, client: OkHttpClient): SupabaseApi =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(SupabaseJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseApi::class.java)
}
