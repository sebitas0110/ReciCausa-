package pe.edu.upn.recicausa.domain

import java.util.Calendar
import java.util.TimeZone

// Cálculo puro (sin Android) del tiempo hasta el próximo recordatorio diario.

object ReminderTime {
    /** Milisegundos desde [nowMillis] hasta la próxima ocurrencia de [hour]:00. Siempre > 0 (máx. 24 h). */
    fun delayUntilNext(nowMillis: Long, hour: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val target = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - nowMillis
    }
}
