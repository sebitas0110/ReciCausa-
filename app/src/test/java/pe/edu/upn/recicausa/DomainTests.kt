package pe.edu.upn.recicausa

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.edu.upn.recicausa.data.repository.InMemoryActivityRepository
import pe.edu.upn.recicausa.data.repository.InMemoryPreferencesRepository
import pe.edu.upn.recicausa.domain.ActivityValidator
import pe.edu.upn.recicausa.domain.Catalog
import pe.edu.upn.recicausa.domain.ProgressCalculator
import pe.edu.upn.recicausa.domain.RecycleActivity

class ActivityValidatorTest {
    @Test fun `CP01 sin categoria es invalido`() {
        val r = ActivityValidator.validate(null, "1", "")
        assertFalse(r.isValid)
        assertEquals("Selecciona una categoría de residuo.", r.categoryError)
    }

    @Test fun `CP02 cantidad vacia o no numerica es invalida`() {
        assertEquals("Ingresa un número entero.", ActivityValidator.validate("plastico", "", "").quantityError)
        assertEquals("Ingresa un número entero.", ActivityValidator.validate("plastico", "abc", "").quantityError)
    }

    @Test fun `CP03 cantidad fuera de rango es invalida`() {
        assertEquals("La cantidad mínima es 1.", ActivityValidator.validate("plastico", "0", "").quantityError)
        assertEquals("La cantidad máxima es 50.", ActivityValidator.validate("plastico", "51", "").quantityError)
    }

    @Test fun `CP04 nota mayor a 80 caracteres es invalida`() {
        assertEquals("Máximo 80 caracteres.", ActivityValidator.validate("plastico", "1", "x".repeat(81)).noteError)
    }

    @Test fun `CP05 datos correctos son validos`() {
        assertTrue(ActivityValidator.validate("plastico", " 3 ", "botellas").isValid)
        assertTrue(ActivityValidator.validate("vidrio", "50", "x".repeat(80)).isValid)
    }

    @Test fun `RN03 puntos = puntos por unidad por cantidad`() {
        val plastico = Catalog.categoryById("plastico")!!
        assertEquals(30, ActivityValidator.pointsFor(plastico, 3))
    }
}

class ProgressCalculatorTest {
    private fun activity(category: String, points: Int) =
        RecycleActivity(categoryId = category, quantity = 1, note = "", points = points, createdAt = 0)

    @Test fun `resumen suma puntos y cuenta actividades`() {
        val s = ProgressCalculator.summary(listOf(activity("plastico", 10), activity("vidrio", 10), activity("plastico", 10)))
        assertEquals(30, s.totalPoints)
        assertEquals(3, s.totalActivities)
        assertEquals(2, s.activitiesByCategory["plastico"])
    }

    @Test fun `retos sin actividades empiezan en cero`() {
        val p = ProgressCalculator.challengeProgress(Catalog.challenges, emptyList())
        assertTrue(p.all { it.current == 0 && !it.completed })
    }

    @Test fun `reto de variedad cuenta categorias distintas`() {
        val acts = listOf(activity("plastico", 10), activity("plastico", 10), activity("vidrio", 10), activity("organicos", 5))
        val p = ProgressCalculator.challengeProgress(Catalog.challenges, acts).associateBy { it.challenge.id }
        assertEquals(3, p.getValue("variedad").current)
        assertTrue(p.getValue("variedad").completed)
        assertEquals(4, p.getValue("constancia").current)
        assertFalse(p.getValue("constancia").completed)
    }

    @Test fun `fraccion de progreso nunca supera 1`() {
        val acts = List(8) { activity("plastico", 10) }
        val p = ProgressCalculator.challengeProgress(Catalog.challenges, acts).first { it.challenge.id == "constancia" }
        assertEquals(1f, p.fraction, 0.0001f)
    }
}

class CatalogTest {
    @Test fun `ids de categorias son unicos y existe al menos un reto`() {
        assertEquals(Catalog.categories.size, Catalog.categories.map { it.id }.distinct().size)
        assertTrue(Catalog.challenges.isNotEmpty())
        assertNull(Catalog.categoryById("no_existe"))
    }
}

class InMemoryRepositoryTest {
    @Test fun `actividades se devuelven de la mas reciente a la mas antigua`() = runTest {
        val repo = InMemoryActivityRepository()
        repo.add(RecycleActivity(categoryId = "vidrio", quantity = 1, note = "", points = 10, createdAt = 100))
        repo.add(RecycleActivity(categoryId = "plastico", quantity = 1, note = "", points = 10, createdAt = 200))
        val list = repo.observeAll().first()
        assertEquals(listOf("plastico", "vidrio"), list.map { it.categoryId })
    }

    @Test fun `preferencias guardan y devuelven recordatorios`() = runTest {
        val prefs = InMemoryPreferencesRepository()
        assertFalse(prefs.remindersEnabled.first())
        prefs.setRemindersEnabled(true)
        prefs.setReminderHour(20)
        assertTrue(prefs.remindersEnabled.first())
        assertEquals(20, prefs.reminderHour.first())
    }
}
