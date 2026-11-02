package pe.edu.upn.recicausa.domain

// Sprint 1 - Validaciones del formulario de registro (HU02, RN02).

object ActivityValidator {
    const val MAX_QUANTITY = 50
    const val MAX_NOTE = 80

    data class Result(
        val categoryError: String? = null,
        val quantityError: String? = null,
        val noteError: String? = null
    ) {
        val isValid: Boolean get() = categoryError == null && quantityError == null && noteError == null
    }

    fun validate(categoryId: String?, quantityText: String, note: String): Result {
        val categoryError = when {
            categoryId == null -> "Selecciona una categoría de residuo."
            Catalog.categoryById(categoryId) == null -> "La categoría seleccionada no es válida."
            else -> null
        }
        val quantity = quantityText.trim().toIntOrNull()
        val quantityError = when {
            quantity == null -> "Ingresa un número entero."
            quantity < 1 -> "La cantidad mínima es 1."
            quantity > MAX_QUANTITY -> "La cantidad máxima es $MAX_QUANTITY."
            else -> null
        }
        val noteError = if (note.length > MAX_NOTE) "Máximo $MAX_NOTE caracteres." else null
        return Result(categoryError, quantityError, noteError)
    }

    /** RN03: los puntos se asignan al registrar correctamente la actividad. */
    fun pointsFor(category: WasteCategory, quantity: Int): Int = category.pointsPerUnit * quantity
}
