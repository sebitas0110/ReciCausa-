package pe.edu.upn.recicausa.domain

// Cálculo derivado de progreso (HU04, HU05, Inicio).
// Los puntos y los retos NO se guardan: se derivan siempre de las actividades registradas,
// así hay una sola fuente de verdad (RN04: se conservan porque las actividades se conservan).

object ProgressCalculator {

    fun summary(activities: List<RecycleActivity>): ProgressSummary = ProgressSummary(
        totalPoints = activities.sumOf { it.points },
        totalActivities = activities.size,
        activitiesByCategory = activities.groupingBy { it.categoryId }.eachCount()
    )

    fun challengeProgress(
        challenges: List<Challenge>,
        activities: List<RecycleActivity>
    ): List<ChallengeProgress> = challenges.map { challenge ->
        val current = when (challenge.type) {
            ChallengeType.TOTAL_ACTIVITIES -> activities.size
            ChallengeType.DISTINCT_CATEGORIES -> activities.map { it.categoryId }.distinct().size
        }
        ChallengeProgress(challenge, current)
    }

    /** Primer reto aún no completado, o null si ya completó todos. */
    fun nextChallenge(progress: List<ChallengeProgress>): ChallengeProgress? =
        progress.firstOrNull { !it.completed }

    /** Categorías con más actividades, de mayor a menor (empates: orden del catálogo). */
    fun topCategories(activities: List<RecycleActivity>, limit: Int = 3): List<CategoryCount> =
        Catalog.categories
            .map { category -> CategoryCount(category, activities.count { it.categoryId == category.id }) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .take(limit)
}
