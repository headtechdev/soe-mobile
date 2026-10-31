package br.com.soe.campo.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.soe.campo.SoeApplication
import java.util.concurrent.TimeUnit

/**
 * Sincronismo em background. Roda periodicamente e tambem sob demanda, sempre
 * exigindo rede — sem conexao o WorkManager simplesmente adia, que e o
 * comportamento esperado do modo offline.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SoeApplication).container
        val session = container.sessionStore.current()

        if (!session.isLoggedIn || session.eventId == null) return Result.success()

        val eventId = session.eventId
        val database = container.database

        FieldNotifications.ensureChannels(applicationContext)

        // Fotografia do que ja existia, para saber depois o que chegou agora.
        val previousSyncAtMs = database.syncStateDao().get(eventId)?.lastSyncAtMs
        val knownAlerts = database.alertDao().all(eventId).map { it.id }.toSet()
        val knownTasks = database.sustainabilityDao().allTasks(eventId).map { it.id }.toSet()
        val knownTrips = database.mobilityDao().allTrips(eventId).map { it.id }.toSet()

        return try {
            container.syncRepository.sync()
            notifyNewWork(eventId, previousSyncAtMs, knownAlerts, knownTasks, knownTrips)
            Result.success()
        } catch (error: Exception) {
            container.syncRepository.recordError(
                session.eventId,
                error.message ?: error::class.java.simpleName,
            )
            // Falha de rede e transitoria: o WorkManager reagenda com backoff.
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    /**
     * Avisa o operador do que chegou neste ciclo.
     *
     * Duas travas contra barulho: nada e notificado no primeiro sincronismo
     * (senao o aparelho dispara uma notificacao por item do evento inteiro) e
     * o prazo so avisa no ciclo em que ele vira — depois disso o item ja esta
     * destacado na lista, nao precisa tocar de novo a cada 15 minutos.
     */
    private suspend fun notifyNewWork(
        eventId: String,
        previousSyncAtMs: Long?,
        knownAlerts: Set<String>,
        knownTasks: Set<String>,
        knownTrips: Set<String>,
    ) {
        if (previousSyncAtMs == null) return

        val database = (applicationContext as SoeApplication).container.database
        val now = System.currentTimeMillis()

        database.alertDao().all(eventId)
            .filter { it.id !in knownAlerts && it.acknowledgedAtMs == null }
            .take(MAX_NOTIFICATIONS)
            .forEach { FieldNotifications.notifyAlert(applicationContext, it) }

        val tasks = database.sustainabilityDao().allTasks(eventId)
        val open = { status: String -> status != "DONE" && status != "NOT_APPLICABLE" }

        tasks
            .filter { it.id !in knownTasks && open(it.status) }
            .take(MAX_NOTIFICATIONS)
            .forEach { FieldNotifications.notifyTask(applicationContext, it, overdue = false) }

        tasks
            .filter { task ->
                val due = task.dueAtMs ?: return@filter false
                open(task.status) && due in previousSyncAtMs..now
            }
            .take(MAX_NOTIFICATIONS)
            .forEach { FieldNotifications.notifyTask(applicationContext, it, overdue = true) }

        database.mobilityDao().allTrips(eventId)
            .filter { it.id !in knownTrips && it.status == "SCHEDULED" }
            .take(MAX_NOTIFICATIONS)
            .forEach { FieldNotifications.notifyTrip(applicationContext, it) }
    }

    companion object {
        /** Teto por ciclo: uma avalanche de notificacoes nao se le em campo. */
        private const val MAX_NOTIFICATIONS = 5

        private const val PERIODIC = "soe-sync-periodic"
        private const val ONE_SHOT = "soe-sync-now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Agendado no login; mantem o dispositivo em dia sozinho. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Disparado ao puxar para atualizar ou apos gravar algo em campo. */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_SHOT)
        }
    }
}
