package pe.edu.upn.recicausa.domain

// Sprint 1 - Contenido educativo (HU01) y retos (HU04).
// Los colores siguen la NTP 900.058:2019 para el ámbito municipal:
// verde = aprovechables, negro = no aprovechables, marrón = orgánicos, rojo = peligrosos.
// Es contenido curado y versionado con la app, por eso no se guarda en base de datos.

object Catalog {

    val categories: List<WasteCategory> = listOf(
        WasteCategory(
            id = "papel_carton", name = "Papel y cartón", bin = BinColor.VERDE,
            examples = listOf("Cajas de cartón", "Hojas y cuadernos", "Revistas y folletos"),
            tip = "Entrégalos secos y sin restos de comida.", pointsPerUnit = 10
        ),
        WasteCategory(
            id = "plastico", name = "Plástico", bin = BinColor.VERDE,
            examples = listOf("Botellas de bebidas", "Envases de productos", "Bolsas limpias"),
            tip = "Enjuaga los envases antes de depositarlos.", pointsPerUnit = 10
        ),
        WasteCategory(
            id = "vidrio", name = "Vidrio", bin = BinColor.VERDE,
            examples = listOf("Botellas", "Frascos", "Vasos"),
            tip = "Enjuágalos y manéjalos con cuidado para evitar cortes.", pointsPerUnit = 10
        ),
        WasteCategory(
            id = "metales", name = "Metales", bin = BinColor.VERDE,
            examples = listOf("Latas de conserva", "Latas de gaseosa", "Latas de leche"),
            tip = "Enjuaga las latas antes de depositarlas.", pointsPerUnit = 10
        ),
        WasteCategory(
            id = "organicos", name = "Orgánicos", bin = BinColor.MARRON,
            examples = listOf("Restos de comida", "Cáscaras de fruta", "Restos de poda y hojarasca"),
            tip = "Sepáralos de los residuos secos para evitar que los contaminen.", pointsPerUnit = 5
        ),
        WasteCategory(
            id = "peligrosos", name = "Peligrosos", bin = BinColor.ROJO,
            examples = listOf("Pilas", "Lámparas y luminarias", "Medicinas vencidas", "Envases de plaguicidas"),
            tip = "No los mezcles con otros residuos; guárdalos aparte.", pointsPerUnit = 15
        ),
        WasteCategory(
            id = "no_aprovechables", name = "No aprovechables", bin = BinColor.NEGRO,
            examples = listOf("Papel higiénico usado", "Pañales", "Colillas de cigarro", "Cerámicos", "Papel encerado"),
            tip = "Reducir su generación es la mejor opción.", pointsPerUnit = 2
        )
    )

    val challenges: List<Challenge> = listOf(
        Challenge(
            id = "primer_paso", title = "Primer paso",
            description = "Registra tu primera actividad de reciclaje.",
            target = 1, type = ChallengeType.TOTAL_ACTIVITIES
        ),
        Challenge(
            id = "constancia", title = "Constancia",
            description = "Registra 5 actividades de reciclaje.",
            target = 5, type = ChallengeType.TOTAL_ACTIVITIES
        ),
        Challenge(
            id = "variedad", title = "Separador variado",
            description = "Registra actividades en 3 categorías distintas.",
            target = 3, type = ChallengeType.DISTINCT_CATEGORIES
        )
    )

    fun categoryById(id: String): WasteCategory? = categories.firstOrNull { it.id == id }
}
