package pe.edu.upn.recicausa

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.edu.upn.recicausa.domain.AuthValidator
import pe.edu.upn.recicausa.domain.PasswordHasher
import pe.edu.upn.recicausa.domain.ProgressCalculator
import pe.edu.upn.recicausa.domain.RecycleActivity

// Pruebas de Sprint 3: cuentas locales, contraseñas y resumen de Inicio.

class AuthValidatorTest {
    @Test fun `CP14 correo sin formato es invalido`() {
        assertFalse(AuthValidator.validateLogin("ana@", "x").isValid)
        assertTrue(AuthValidator.validateLogin("ana@correo.com", "x").isValid)
    }

    @Test fun `CP15 login exige contrasena`() {
        assertEquals("Ingresa tu contraseña.", AuthValidator.validateLogin("ana@correo.com", "").passwordError)
    }

    @Test fun `CP16 registro valida minimo de contrasena y confirmacion`() {
        val short = AuthValidator.validateSignup("Ana", "ana@correo.com", "1234567", "1234567")
        assertEquals("Mínimo 8 caracteres.", short.passwordError)
        val mismatch = AuthValidator.validateSignup("Ana", "ana@correo.com", "12345678", "87654321")
        assertTrue(mismatch.confirmError != null)
        assertTrue(AuthValidator.validateSignup("Ana", "ana@correo.com", "12345678", "12345678").isValid)
    }

    @Test fun `CP17 nombre muy corto es invalido y el correo se normaliza`() {
        assertTrue(AuthValidator.validateSignup("A", "ana@correo.com", "12345678", "12345678").nameError != null)
        assertEquals("ana@correo.com", AuthValidator.normalizeEmail("  Ana@Correo.COM "))
    }
}

class PasswordHasherTest {
    @Test fun `CP18 verifica la contrasena correcta y rechaza otra`() {
        val h = PasswordHasher.hash("secreta123")
        assertTrue(PasswordHasher.verify("secreta123", h.hash, h.salt))
        assertFalse(PasswordHasher.verify("secreta124", h.hash, h.salt))
    }

    @Test fun `CP19 la misma contrasena genera hash distinto por sal y no se guarda en claro`() {
        val a = PasswordHasher.hash("secreta123")
        val b = PasswordHasher.hash("secreta123")
        assertNotEquals(a.hash, b.hash)
        assertNotEquals("secreta123", a.hash)
    }
}

class HomeSummaryTest {
    private fun activity(category: String) =
        RecycleActivity(categoryId = category, quantity = 1, note = "", points = 10, createdAt = 0)

    @Test fun `CP20 categorias principales ordenadas y limitadas`() {
        val list = listOf(activity("plastico"), activity("plastico"), activity("vidrio"))
        val top = ProgressCalculator.topCategories(list, limit = 1)
        assertEquals(1, top.size)
        assertEquals("plastico", top[0].category.id)
        assertEquals(2, top[0].count)
    }

    @Test fun `CP21 sin actividades no hay categorias ni foto`() {
        assertTrue(ProgressCalculator.topCategories(emptyList()).isEmpty())
        assertNull(activity("plastico").photoPath)
    }
}

class ReminderTimeTest {
    private val lima = java.util.TimeZone.getTimeZone("America/Lima")
    private fun at(hour: Int, minute: Int): Long = java.util.Calendar.getInstance(lima).apply {
        clear(); set(2026, java.util.Calendar.OCTOBER, 8, hour, minute, 0)
    }.timeInMillis

    @Test fun `CP22 antes de la hora el aviso es hoy`() {
        val delay = pe.edu.upn.recicausa.domain.ReminderTime.delayUntilNext(at(10, 0), 18, lima)
        assertEquals(8L * 60 * 60 * 1000, delay)
    }

    @Test fun `CP23 despues de la hora el aviso es manana`() {
        val delay = pe.edu.upn.recicausa.domain.ReminderTime.delayUntilNext(at(19, 0), 18, lima)
        assertEquals(23L * 60 * 60 * 1000, delay)
    }
}

class ActivityCrudTest {
    private fun sample(category: String = "plastico", qty: Int = 2) = pe.edu.upn.recicausa.domain.RecycleActivity(
        categoryId = category, quantity = qty, note = "", points = qty * 10, createdAt = 1
    )

    @Test fun `CP24 crear y leer una actividad por id`() = kotlinx.coroutines.test.runTest {
        val repo = pe.edu.upn.recicausa.data.repository.InMemoryActivityRepository()
        repo.add(sample())
        val saved = repo.observeAll().first().single()
        assertEquals("plastico", repo.getById(saved.id)?.categoryId)
        assertNull(repo.getById(999))
    }

    @Test fun `CP25 actualizar cambia los datos y respeta el resto`() = kotlinx.coroutines.test.runTest {
        val repo = pe.edu.upn.recicausa.data.repository.InMemoryActivityRepository()
        repo.add(sample())
        val saved = repo.observeAll().first().single()
        repo.update(saved.copy(categoryId = "vidrio", quantity = 5, points = 50))
        val updated = repo.getById(saved.id)!!
        assertEquals("vidrio", updated.categoryId)
        assertEquals(5, updated.quantity)
        assertEquals(saved.createdAt, updated.createdAt)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test fun `CP26 eliminar quita la actividad y los puntos se recalculan`() = kotlinx.coroutines.test.runTest {
        val repo = pe.edu.upn.recicausa.data.repository.InMemoryActivityRepository()
        repo.add(sample()); repo.add(sample("vidrio", 1))
        val first = repo.observeAll().first().last()
        repo.delete(first.id)
        val left = repo.observeAll().first()
        assertEquals(1, left.size)
        assertEquals(left.sumOf { it.points }, ProgressCalculator.summary(left).totalPoints)
    }
}
