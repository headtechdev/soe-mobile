package br.com.soe.campo.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.soe.campo.R
import br.com.soe.campo.data.local.AlertEntity
import br.com.soe.campo.data.local.SustainabilityTaskEntity
import br.com.soe.campo.data.local.TripEntity

/**
 * Notificacoes locais do app.
 *
 * Nao e push: o aparelho nao tem FCM configurado. O que existe e o
 * sincronismo em background, que a cada 15 minutos ja sabe o que chegou de
 * novo — avisar a partir dali cobre o essencial ("tarefas atribuidas, prazos
 * e itens criticos") sem depender de servico externo. Push real continua
 * sendo uma decisao de produto em aberto nos dois modulos.
 *
 * Canais separados porque o operador precisa poder silenciar o informativo
 * sem perder o critico.
 */
object FieldNotifications {

    private const val CHANNEL_CRITICAL = "soe_critico"
    private const val CHANNEL_TASKS = "soe_tarefas"
    private const val CHANNEL_MISSIONS = "soe_missoes"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CRITICAL,
                "Alertas criticos",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Alertas do comando que exigem ciencia imediata." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASKS,
                "Tarefas e prazos",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Itens de checklist atribuidos, devolucoes e prazos." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MISSIONS,
                "Missoes de logistica",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Viagens, transfers e shuttles atribuidos a voce." },
        )
    }

    fun notifyAlert(context: Context, alert: AlertEntity) {
        val critical = alert.level == "CRITICAL"
        post(
            context = context,
            channel = if (critical) CHANNEL_CRITICAL else CHANNEL_TASKS,
            id = alert.id.hashCode(),
            title = alert.title,
            body = alert.message,
            priority = if (critical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
        )
    }

    fun notifyTask(context: Context, task: SustainabilityTaskEntity, overdue: Boolean) {
        val prefix = when {
            overdue -> "Prazo vencido"
            task.criterion == "REQUIRED" -> "Item obrigatorio"
            else -> "Nova tarefa"
        }
        post(
            context = context,
            channel = CHANNEL_TASKS,
            id = task.id.hashCode(),
            title = "$prefix · ${task.theme}",
            body = task.title,
            priority = if (task.priority == "CRITICAL" || overdue) {
                NotificationCompat.PRIORITY_HIGH
            } else {
                NotificationCompat.PRIORITY_DEFAULT
            },
        )
    }

    fun notifyTrip(context: Context, trip: TripEntity) {
        val route = listOfNotNull(trip.originName, trip.destinationName).joinToString(" → ")
        post(
            context = context,
            channel = CHANNEL_MISSIONS,
            id = trip.id.hashCode(),
            title = "Missao ${trip.code}",
            body = route.ifBlank { trip.routeName ?: "Nova missao atribuida" },
            priority = NotificationCompat.PRIORITY_DEFAULT,
        )
    }

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        body: String,
        priority: Int,
    ) {
        val manager = NotificationManagerCompat.from(context)
        // Em Android 13+ sem a permissao concedida o post e descartado em
        // silencio; checar aqui evita a excecao e deixa claro o motivo.
        if (!manager.areNotificationsEnabled()) return

        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val contentIntent = intent?.let {
            PendingIntent.getActivity(
                context,
                id,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification: Notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()

        runCatching { manager.notify(id, notification) }
    }
}
