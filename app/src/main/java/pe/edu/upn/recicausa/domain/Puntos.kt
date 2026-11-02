package pe.edu.upn.recicausa.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Sprint 5 - Puntos de reciclaje (HU10, HU11, HU12). Dominio puro: se prueba sin emulador.

enum class PuntoEstado(val wire: String, val label: String) {
    PENDIENTE("pendiente", "Pendiente de aprobación"),
    APROBADO("aprobado", "Aprobado"),
    RECHAZADO("rechazado", "Rechazado");

    companion object {
        fun fromWire(value: String?): PuntoEstado = entries.firstOrNull { it.wire == value } ?: PENDIENTE
    }
}

data class PuntoReciclaje(
    val id: Long,
    val nombre: String,
    val descripcion: String,
    val direccion: String,
    val distrito: String,
    val latitud: Double,
    val longitud: Double,
    /** Ids de [Catalog.categories] que recibe el punto. */
    val categorias: List<String>,
    val horario: String,
    val telefono: String,
    val estado: PuntoEstado = PuntoEstado.APROBADO
)

/** Un punto con su distancia al usuario (null si no se conoce la ubicación). */
data class PuntoConDistancia(val punto: PuntoReciclaje, val distanciaKm: Double?)

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Distancia entre dos coordenadas (fórmula de Haversine), en kilómetros. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(min(1.0, a)))
    }

    /** Recuadro aproximado del territorio peruano (HU12: la ubicación debe estar dentro del Perú). */
    fun isInPeru(lat: Double, lng: Double): Boolean =
        lat in -18.4..0.1 && lng in -81.5..-68.6

    fun formatDistance(km: Double): String =
        if (km < 1.0) "${(km * 1000).toInt().coerceAtLeast(10).div(10) * 10} m" else "%.1f km".format(java.util.Locale.US, km)
}

object PuntoFilter {
    /** Filtra por categoría (null = todas). */
    fun byCategory(puntos: List<PuntoReciclaje>, categoryId: String?): List<PuntoReciclaje> =
        if (categoryId == null) puntos else puntos.filter { categoryId in it.categorias }

    /**
     * Ordena por cercanía si hay ubicación (CA11.1); si no, por nombre (CA11.2).
     * La ubicación solo se usa aquí, en memoria: no se guarda ni se envía (CA11.3).
     */
    fun sorted(puntos: List<PuntoReciclaje>, lat: Double?, lng: Double?): List<PuntoConDistancia> =
        if (lat == null || lng == null) {
            puntos.sortedBy { it.nombre.lowercase() }.map { PuntoConDistancia(it, null) }
        } else {
            puntos.map { PuntoConDistancia(it, Geo.distanceKm(lat, lng, it.latitud, it.longitud)) }
                .sortedBy { it.distanciaKm }
        }
}

object PuntoValidator {
    const val MIN_NOMBRE = 3
    const val MAX_NOMBRE = 80
    const val MIN_DIRECCION = 5
    const val MAX_DIRECCION = 150
    const val MAX_DESCRIPCION = 300
    const val MAX_HORARIO = 120
    const val MAX_TELEFONO = 20

    data class Result(
        val nombreError: String? = null,
        val direccionError: String? = null,
        val categoriasError: String? = null,
        val ubicacionError: String? = null,
        val descripcionError: String? = null,
        val horarioError: String? = null,
        val telefonoError: String? = null
    ) {
        val isValid: Boolean
            get() = listOf(nombreError, direccionError, categoriasError, ubicacionError, descripcionError, horarioError, telefonoError)
                .all { it == null }
    }

    fun validate(
        nombre: String, direccion: String, categorias: Set<String>,
        latitud: Double?, longitud: Double?,
        descripcion: String = "", horario: String = "", telefono: String = ""
    ): Result {
        val n = nombre.trim().length
        val d = direccion.trim().length
        return Result(
            nombreError = if (n < MIN_NOMBRE || n > MAX_NOMBRE) "El nombre debe tener entre $MIN_NOMBRE y $MAX_NOMBRE caracteres." else null,
            direccionError = if (d < MIN_DIRECCION || d > MAX_DIRECCION) "La dirección debe tener entre $MIN_DIRECCION y $MAX_DIRECCION caracteres." else null,
            categoriasError = when {
                categorias.isEmpty() -> "Elige al menos un tipo de residuo."
                categorias.any { Catalog.categoryById(it) == null } -> "Hay un tipo de residuo no válido."
                else -> null
            },
            ubicacionError = when {
                latitud == null || longitud == null -> "Marca la ubicación en el mapa."
                !Geo.isInPeru(latitud, longitud) -> "La ubicación debe estar dentro del Perú."
                else -> null
            },
            descripcionError = if (descripcion.trim().length > MAX_DESCRIPCION) "Máximo $MAX_DESCRIPCION caracteres." else null,
            horarioError = if (horario.trim().length > MAX_HORARIO) "Máximo $MAX_HORARIO caracteres." else null,
            telefonoError = if (telefono.trim().length > MAX_TELEFONO) "Máximo $MAX_TELEFONO caracteres." else null
        )
    }
}
