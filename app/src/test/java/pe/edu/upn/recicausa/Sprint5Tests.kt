package pe.edu.upn.recicausa

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.edu.upn.recicausa.data.local.ActivityEntity
import pe.edu.upn.recicausa.data.remote.ActividadDto
import pe.edu.upn.recicausa.data.remote.ApiError
import pe.edu.upn.recicausa.data.remote.ApiErrors
import pe.edu.upn.recicausa.data.remote.ApiResult
import pe.edu.upn.recicausa.data.remote.Filters
import pe.edu.upn.recicausa.data.remote.PuntoDto
import pe.edu.upn.recicausa.data.remote.PuntoWriteDto
import pe.edu.upn.recicausa.data.remote.SupabaseApi
import pe.edu.upn.recicausa.data.remote.SupabaseClient
import pe.edu.upn.recicausa.data.remote.safeApiCall
import pe.edu.upn.recicausa.data.repository.AuthMessages
import pe.edu.upn.recicausa.data.repository.SessionRepository
import pe.edu.upn.recicausa.data.repository.StoredSession
import pe.edu.upn.recicausa.data.repository.isValid
import pe.edu.upn.recicausa.data.repository.toDomain
import pe.edu.upn.recicausa.data.repository.toDto
import pe.edu.upn.recicausa.data.repository.toEntity
import pe.edu.upn.recicausa.domain.Geo
import pe.edu.upn.recicausa.domain.PuntoEstado
import pe.edu.upn.recicausa.domain.PuntoFilter
import pe.edu.upn.recicausa.domain.PuntoReciclaje
import pe.edu.upn.recicausa.domain.PuntoValidator

// Pruebas de Sprint 5: puntos de reciclaje, errores de red y cliente HTTP (servidor simulado).

private fun punto(id: Long, nombre: String, lat: Double, lng: Double, vararg cats: String) = PuntoReciclaje(
    id = id, nombre = nombre, descripcion = "", direccion = "Av. Prueba 123", distrito = "Trujillo",
    latitud = lat, longitud = lng, categorias = cats.toList(), horario = "", telefono = ""
)

// ---------- Distancia y orden (HU10, HU11) ----------
class DistanciaTests {
    @Test fun `CP27 distancia Haversine entre dos puntos cercanos`() {
        // 0.01 grados de longitud a -8.11 de latitud son ~1.10 km.
        val d = Geo.distanceKm(-8.1116, -79.0288, -8.1116, -79.0188)
        assertEquals(1.10, d, 0.05)
    }

    @Test fun `CP28 distancia de un punto a si mismo es cero`() {
        assertEquals(0.0, Geo.distanceKm(-8.11, -79.03, -8.11, -79.03), 1e-9)
    }

    @Test fun `CP29 con ubicacion se ordena de menor a mayor distancia`() {
        val lejos = punto(1, "Lejos", -8.20, -79.03, "plastico")
        val cerca = punto(2, "Cerca", -8.1120, -79.0290, "plastico")
        val sorted = PuntoFilter.sorted(listOf(lejos, cerca), -8.1116, -79.0288)
        assertEquals(listOf("Cerca", "Lejos"), sorted.map { it.punto.nombre })
        assertNotNull(sorted[0].distanciaKm)
    }

    @Test fun `CP30 sin ubicacion se ordena por nombre y no hay distancia`() {
        val sorted = PuntoFilter.sorted(listOf(punto(1, "Zeta", 0.0, 0.0, "vidrio"), punto(2, "alfa", 0.0, 0.0, "vidrio")), null, null)
        assertEquals(listOf("alfa", "Zeta"), sorted.map { it.punto.nombre })
        assertNull(sorted[0].distanciaKm)
    }

    @Test fun `CP31 filtrar por tipo de residuo`() {
        val lista = listOf(punto(1, "A", 0.0, 0.0, "plastico", "vidrio"), punto(2, "B", 0.0, 0.0, "metales"))
        assertEquals(listOf(1L), PuntoFilter.byCategory(lista, "vidrio").map { it.id })
        assertEquals(2, PuntoFilter.byCategory(lista, null).size)
    }
}

// ---------- Validación de sugerencias (HU12) ----------
class PuntoValidatorTests {
    private fun ok() = PuntoValidator.validate("Centro de acopio", "Av. España 123", setOf("plastico"), -8.11, -79.03)

    @Test fun `CP32 datos correctos son validos`() = assertTrue(ok().isValid)

    @Test fun `CP33 nombre muy corto es invalido`() {
        val r = PuntoValidator.validate("ab", "Av. España 123", setOf("plastico"), -8.11, -79.03)
        assertTrue(r.nombreError != null)
    }

    @Test fun `CP34 exige al menos un residuo y rechaza categorias desconocidas`() {
        assertEquals("Elige al menos un tipo de residuo.", PuntoValidator.validate("Centro", "Av. España 123", emptySet(), -8.11, -79.03).categoriasError)
        assertTrue(PuntoValidator.validate("Centro", "Av. España 123", setOf("radiactivo"), -8.11, -79.03).categoriasError != null)
    }

    @Test fun `CP35 la ubicacion debe estar dentro del Peru`() {
        val r = PuntoValidator.validate("Centro", "Av. España 123", setOf("vidrio"), 40.4, -3.7) // Madrid
        assertEquals("La ubicación debe estar dentro del Perú.", r.ubicacionError)
        assertTrue(Geo.isInPeru(-8.11, -79.03))
    }

    @Test fun `CP36 sin ubicacion marcada es invalido`() {
        assertEquals("Marca la ubicación en el mapa.", PuntoValidator.validate("Centro", "Av. España 123", setOf("vidrio"), null, null).ubicacionError)
    }
}

// ---------- Clasificación de errores y mensajes ----------
class ErroresTests {
    @Test fun `CP37 clasifica los codigos HTTP`() {
        assertEquals(ApiError.Unauthorized, ApiErrors.classify(401, null))
        assertEquals(ApiError.Forbidden, ApiErrors.classify(403, null))
        assertEquals(ApiError.RateLimited, ApiErrors.classify(429, null))
        assertTrue(ApiErrors.classify(400, null) is ApiError.Validation)
        assertTrue(ApiErrors.classify(409, null) is ApiError.Conflict)
        assertEquals(ApiError.Server(503), ApiErrors.classify(503, null))
        assertTrue(ApiErrors.classify(418, null) is ApiError.Unknown)
    }

    @Test fun `CP38 clasifica excepciones de red y tiempo de espera`() {
        assertEquals(ApiError.Timeout, ApiErrors.classify(SocketTimeoutException()))
        assertEquals(ApiError.Network, ApiErrors.classify(IOException("sin red")))
    }

    @Test fun `CP39 todos los errores tienen un mensaje legible para el usuario`() {
        val all = listOf(
            ApiError.Network, ApiError.Timeout, ApiError.Unauthorized, ApiError.Forbidden, ApiError.Conflict(null),
            ApiError.Validation(null), ApiError.RateLimited, ApiError.Server(500), ApiError.Unknown(1, null)
        )
        all.forEach { assertTrue(it.userMessage.isNotBlank()) }
        assertTrue(ApiError.Network.userMessage.contains("conexión"))
    }

    @Test fun `CP40 lee el codigo de error de Supabase Auth y de PostgREST`() {
        assertEquals("invalid_credentials", ApiErrors.errorCode("""{"code":400,"error_code":"invalid_credentials","msg":"x"}"""))
        assertEquals("42501", ApiErrors.errorCode("""{"code":"42501","message":"row-level security"}"""))
        assertNull(ApiErrors.errorCode("no es json"))
    }

    @Test fun `CP41 mensajes de inicio de sesion y registro`() {
        val bad = ApiResult.Failure(ApiError.Validation(null), "invalid_credentials")
        assertEquals("Correo o contraseña incorrectos.", AuthMessages.forLogin(bad))
        val dup = ApiResult.Failure(ApiError.Validation(null), "user_already_exists")
        assertEquals("Ya existe una cuenta con ese correo.", AuthMessages.forSignUp(dup))
        assertTrue(AuthMessages.forLogin(ApiResult.Failure(ApiError.Network)).contains("internet"))
    }
}

// ---------- DTO y conversiones ----------
class ConversionTests {
    private val entity = ActivityEntity(
        id = 7, userId = "u-1", categoryId = "plastico", quantity = 3, note = "botellas",
        points = 30, createdAt = 1_700_000_000_000, photoPath = "/data/foto.jpg"
    )

    @Test fun `CP42 actividad local a DTO conserva campos y usa local_id`() {
        val dto = entity.toDto("u-1")
        assertEquals(7L, dto.localId); assertEquals("u-1", dto.userId)
        assertEquals("plastico", dto.categoriaId); assertEquals(3, dto.cantidad); assertEquals(30, dto.puntos)
        assertEquals("2023-11-14T22:13:20Z", dto.creadoEn)
    }

    @Test fun `CP43 DTO a entidad restaura id y fecha y no trae foto`() {
        val back = entity.toDto("u-1").toEntity("u-1")
        assertEquals(entity.copy(photoPath = null), back)
    }

    @Test fun `CP44 DTO con categoria o cantidad invalida se descarta`() {
        assertTrue(ActividadDto(localId = 1, categoriaId = "plastico", cantidad = 2, puntos = 20).isValid())
        assertFalse(ActividadDto(localId = 1, categoriaId = "radiactivo", cantidad = 2, puntos = 20).isValid())
        assertFalse(ActividadDto(localId = 1, categoriaId = "vidrio", cantidad = 99, puntos = 20).isValid())
    }

    @Test fun `CP45 punto remoto a dominio mapea categorias y estado`() {
        val p = PuntoDto(1, "Acopio", null, "Av. 1", "Trujillo", -8.1, -79.0, listOf("vidrio", "metales"), null, null, "pendiente").toDomain()
        assertEquals(listOf("vidrio", "metales"), p.categorias)
        assertEquals(PuntoEstado.PENDIENTE, p.estado)
        assertEquals("", p.horario)
    }
}

// ---------- Cliente HTTP con servidor simulado ----------
private class FakeSession(initial: StoredSession? = null) : SessionRepository {
    private val state = MutableStateFlow(initial)
    override val session = state
    override val userId = state.map { it?.userId }
    override fun peek() = state.value
    override suspend fun save(session: StoredSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

class ClienteHttpTests {
    private lateinit var server: MockWebServer
    private val stored = StoredSession("tok-viejo", "ref-1", "u-1", "Ana", "ana@correo.com")

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun api(session: SessionRepository): SupabaseApi =
        SupabaseClient.create(server.url("/").toString(), "sb_publishable_test", session)

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val puntoJson = """{"id":5,"nombre":"Acopio","direccion":"Av. 1","latitud":-8.1,"longitud":-79.0,"categorias":["plastico"],"estado":"aprobado"}"""
    private val write = PuntoWriteDto("Acopio", "", "Av. 1", "Trujillo", -8.1, -79.0, listOf("plastico"), "", "")

    @Test fun `CP46 sin sesion envia apikey y no envia Authorization`() = runBlocking {
        server.enqueue(json("[]"))
        api(FakeSession()).getPuntosAprobados()
        val req = server.takeRequest()
        assertEquals("sb_publishable_test", req.getHeader("apikey"))
        assertNull(req.getHeader("Authorization"))
    }

    @Test fun `CP47 con sesion agrega el token Bearer`() = runBlocking {
        server.enqueue(json("[]"))
        api(FakeSession(stored)).getMisPuntos(Filters.eq("u-1"))
        val req = server.takeRequest()
        assertEquals("Bearer tok-viejo", req.getHeader("Authorization"))
        assertTrue(req.path!!.contains("creado_por=eq.u-1"))
    }

    @Test fun `CP48 GET de puntos aprobados filtra por estado y parsea el JSON`() = runBlocking {
        server.enqueue(json("[$puntoJson]"))
        val r = safeApiCall { api(FakeSession()).getPuntosAprobados() }
        assertTrue(server.takeRequest().path!!.contains("estado=eq.aprobado"))
        val lista = (r as ApiResult.Success).value
        assertEquals("Acopio", lista.single().nombre)
        assertEquals("Trujillo", lista.single().distrito) // valor por defecto cuando el JSON no lo trae
    }

    @Test fun `CP49 ante un 401 renueva la sesion y reintenta una vez`() = runBlocking {
        val session = FakeSession(stored)
        server.enqueue(json("""{"message":"JWT expired"}""", 401))
        server.enqueue(json("""{"access_token":"tok-nuevo","refresh_token":"ref-2","expires_in":3600,"user":{"id":"u-1","email":"ana@correo.com"}}"""))
        server.enqueue(json("[$puntoJson]"))

        val r = safeApiCall { api(session).getMisPuntos(Filters.eq("u-1")) }

        assertTrue(r is ApiResult.Success)
        assertEquals(3, server.requestCount)
        server.takeRequest()
        val refresh = server.takeRequest()
        assertTrue(refresh.path!!.contains("grant_type=refresh_token"))
        assertTrue(refresh.body.readUtf8().contains("ref-1"))
        assertEquals("Bearer tok-nuevo", server.takeRequest().getHeader("Authorization"))
        assertEquals("tok-nuevo", session.peek()?.accessToken)
        assertEquals("Ana", session.peek()?.name) // conserva el nombre
    }

    @Test fun `CP50 si la renovacion falla se cierra la sesion local`() = runBlocking {
        val session = FakeSession(stored)
        server.enqueue(json("""{"message":"JWT expired"}""", 401))
        server.enqueue(json("""{"error_code":"refresh_token_not_found","msg":"x"}""", 400))

        val r = safeApiCall { api(session).getMisPuntos(Filters.eq("u-1")) }

        assertEquals(ApiError.Unauthorized, (r as ApiResult.Failure).error)
        assertNull(session.peek())
    }

    @Test fun `CP51 un 403 por RLS se clasifica como Forbidden`() = runBlocking {
        server.enqueue(json("""{"code":"42501","message":"new row violates row-level security policy"}""", 403))
        val r = safeApiCall { api(FakeSession(stored)).crearPunto(write) } as ApiResult.Failure
        assertEquals(ApiError.Forbidden, r.error)
        assertEquals("42501", r.code)
    }

    @Test fun `CP52 POST de una sugerencia envia el JSON y pide la fila creada`() = runBlocking {
        server.enqueue(json("[$puntoJson]", 201))
        val r = safeApiCall { api(FakeSession(stored)).crearPunto(write) }
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("return=representation", req.getHeader("Prefer"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"nombre\":\"Acopio\"") && body.contains("\"categorias\":[\"plastico\"]"))
        assertFalse(body.contains("estado")) // el estado lo fija la base (RN11)
        assertEquals(5L, (r as ApiResult.Success).value.single().id)
    }

    @Test fun `CP53 PATCH y DELETE usan el filtro id`() = runBlocking {
        server.enqueue(json("[$puntoJson]"))
        server.enqueue(MockResponse().setResponseCode(204))
        val a = api(FakeSession(stored))
        safeApiCall { a.editarPunto(Filters.eq(5), write) }
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method); assertTrue(patch.path!!.contains("id=eq.5"))
        val del = safeApiCall { a.borrarPunto(Filters.eq(5)) }
        val delReq = server.takeRequest()
        assertEquals("DELETE", delReq.method); assertTrue(delReq.path!!.contains("id=eq.5"))
        assertTrue(del is ApiResult.Success)
    }

    @Test fun `CP54 upsert de actividades usa on_conflict y merge-duplicates`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val dto = ActividadDto(userId = "u-1", localId = 3, categoriaId = "vidrio", cantidad = 1, puntos = 10)
        val r = safeApiCall { api(FakeSession(stored)).upsertActividades(listOf(dto)) }
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("on_conflict=user_id") && req.path!!.contains("local_id"))
        assertEquals("resolution=merge-duplicates,return=minimal", req.getHeader("Prefer"))
        assertTrue(req.body.readUtf8().startsWith("[{"))
        assertTrue(r is ApiResult.Success)
    }

    @Test fun `CP55 borrar actividades por lista de local_id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        safeApiCall { api(FakeSession(stored)).borrarActividades(Filters.inList(listOf(1L, 2L))) }
        val path = server.takeRequest().path!!
        assertTrue(path.contains("local_id=in."))
        assertTrue(java.net.URLDecoder.decode(path, "UTF-8").contains("in.(1,2)"))
    }

    @Test fun `CP56 inicio de sesion enviado a token con grant_type password`() = runBlocking {
        server.enqueue(json("""{"access_token":"a","refresh_token":"r","user":{"id":"u-9","email":"x@y.com","user_metadata":{"nombre":"Luz"}}}"""))
        val r = safeApiCall { api(FakeSession()).signIn(pe.edu.upn.recicausa.data.remote.SignInRequest("x@y.com", "12345678")) }
        assertTrue(server.takeRequest().path!!.contains("auth/v1/token?grant_type=password"))
        assertEquals("Luz", (r as ApiResult.Success).value.user?.userMetadata?.nombre)
    }

    @Test fun `CP57 sin red devuelve Network y no lanza excepcion`() = runBlocking {
        val a = api(FakeSession())
        server.shutdown()
        val r = safeApiCall { a.getPuntosAprobados() } as ApiResult.Failure
        assertTrue(r.error == ApiError.Network || r.error == ApiError.Timeout)
    }
}
