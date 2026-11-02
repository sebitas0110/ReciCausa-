package pe.edu.upn.recicausa.domain

// Modelo de dominio (sin dependencias de Android, probable con JUnit puro).

/** Colores de contenedor según la NTP 900.058:2019 (ámbito municipal). */
enum class BinColor(val label: String) {
    VERDE("Verde · Aprovechables"),
    NEGRO("Negro · No aprovechables"),
    MARRON("Marrón · Orgánicos"),
    ROJO("Rojo · Peligrosos")
}

data class WasteCategory(
    val id: String,
    val name: String,
    val bin: BinColor,
    val examples: List<String>,
    val tip: String,
    val pointsPerUnit: Int
)

data class RecycleActivity(
    val id: Long = 0,
    val categoryId: String,
    val quantity: Int,
    val note: String,
    val points: Int,
    val createdAt: Long,
    /** Ruta de la foto guardada en el almacenamiento privado de la app; null si no se tomó foto. */
    val photoPath: String? = null
)

/** Usuario con sesión. Nunca incluye la contraseña ni su hash. */
data class User(val id: String, val name: String, val email: String)

enum class ChallengeType { TOTAL_ACTIVITIES, DISTINCT_CATEGORIES }

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val target: Int,
    val type: ChallengeType
)

data class ChallengeProgress(val challenge: Challenge, val current: Int) {
    val completed: Boolean get() = current >= challenge.target
    val fraction: Float get() = current.coerceAtMost(challenge.target).toFloat() / challenge.target
}

data class ProgressSummary(
    val totalPoints: Int,
    val totalActivities: Int,
    val activitiesByCategory: Map<String, Int>
)

data class CategoryCount(val category: WasteCategory, val count: Int)
