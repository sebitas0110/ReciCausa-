package pe.edu.upn.recicausa.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import pe.edu.upn.recicausa.MainActivity
import pe.edu.upn.recicausa.R
import pe.edu.upn.recicausa.domain.ReminderTime

interface ReminderScheduler {
    /** Programa (o reprograma) el aviso diario a las [hour]:00. */
    fun schedule(hour: Int)
    fun cancel()
}

class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun schedule(hour: Int) = enqueue(context, hour)
    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "recicausa_daily_reminder"
        const val KEY_HOUR = "hour"

        fun enqueue(context: Context, hour: Int) {
            val delay = ReminderTime.delayUntilNext(System.currentTimeMillis(), hour)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_HOUR to hour))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val hour = inputData.getInt(WorkManagerReminderScheduler.KEY_HOUR, 18)
        Notifications.showReminder(applicationContext)
        // Reprograma el aviso del día siguiente.
        WorkManagerReminderScheduler.enqueue(applicationContext, hour)
        return Result.success()
    }
}

object Notifications {
    private const val CHANNEL_ID = "recordatorios"
    private const val NOTIFICATION_ID = 1

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context) {
        // Sin permiso (Android 13+) no se muestra nada; no se insiste.
        if (!hasPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // minSdk 26: los canales siempre existen.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Un aviso al día para separar tus residuos"
            }
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ReciCausa")
            .setContentText("¿Ya separaste tus residuos hoy? Regístralo y suma puntos.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
