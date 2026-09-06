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

        return try {
            container.syncRepository.sync()
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

    companion object {
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
