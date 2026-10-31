package br.com.soe.campo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SustainabilityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(rows: List<SustainabilityTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(row: SustainabilityTaskEntity)

    /**
     * Ordem de campo: primeiro o que esta em aberto, depois criticidade e
     * prazo. Quem abre a lista no meio da operacao precisa ver no topo o que
     * ainda cobra resposta.
     */
    @Query(
        """
        SELECT * FROM sustainability_tasks
        WHERE eventId = :eventId AND deleted = 0
        ORDER BY
            CASE WHEN status IN ('DONE', 'NOT_APPLICABLE') THEN 1 ELSE 0 END,
            CASE criterion WHEN 'REQUIRED' THEN 0 WHEN 'RECOMMENDED' THEN 1 ELSE 2 END,
            CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
            COALESCE(dueAtMs, 9223372036854775807),
            theme, title
        """
    )
    fun observeTasks(eventId: String): Flow<List<SustainabilityTaskEntity>>

    @Query("SELECT * FROM sustainability_tasks WHERE id = :id")
    fun observeTask(id: String): Flow<SustainabilityTaskEntity?>

    @Query("SELECT * FROM sustainability_tasks WHERE id = :id")
    suspend fun task(id: String): SustainabilityTaskEntity?

    /** Obrigatorios ainda em aberto — o numero que a Home mostra. */
    @Query(
        """
        SELECT COUNT(*) FROM sustainability_tasks
        WHERE eventId = :eventId AND deleted = 0
          AND criterion = 'REQUIRED'
          AND status NOT IN ('DONE', 'NOT_APPLICABLE')
        """
    )
    fun countOpenRequired(eventId: String): Flow<Int>

    @Query("SELECT * FROM sustainability_tasks WHERE eventId = :eventId AND deleted = 0")
    suspend fun allTasks(eventId: String): List<SustainabilityTaskEntity>

    @Query("SELECT * FROM sustainability_tasks WHERE pendingSync = 1")
    suspend fun pendingTasks(): List<SustainabilityTaskEntity>

    @Query("UPDATE sustainability_tasks SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingTasks(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUpdates(rows: List<SustainabilityUpdateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUpdate(row: SustainabilityUpdateEntity)

    @Query("SELECT * FROM sustainability_updates WHERE taskId = :taskId ORDER BY answeredAtMs DESC")
    fun observeUpdates(taskId: String): Flow<List<SustainabilityUpdateEntity>>

    @Query("SELECT * FROM sustainability_updates WHERE pendingSync = 1")
    suspend fun pendingUpdates(): List<SustainabilityUpdateEntity>

    @Query("UPDATE sustainability_updates SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingUpdates(ids: List<String>)

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM sustainability_tasks WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM sustainability_updates WHERE pendingSync = 1)
        """
    )
    fun countPending(): Flow<Int>

    /** A resposta e a tarefa mudam juntas: a lista precisa refletir na hora. */
    @Transaction
    suspend fun answer(task: SustainabilityTaskEntity, update: SustainabilityUpdateEntity) {
        upsertTask(task)
        upsertUpdate(update)
    }
}

@Dao
interface SyncRejectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SyncRejectionEntity>)

    @Query("SELECT * FROM sync_rejections WHERE resolved = 0 ORDER BY rejectedAtMs DESC")
    fun observeOpen(): Flow<List<SyncRejectionEntity>>

    @Query("SELECT COUNT(*) FROM sync_rejections WHERE resolved = 0")
    fun countOpen(): Flow<Int>

    @Query("UPDATE sync_rejections SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: String)

    /** Um registro reenviado e aceito deixa de ser conflito. */
    @Query("UPDATE sync_rejections SET resolved = 1 WHERE recordId IN (:recordIds)")
    suspend fun resolveByRecord(recordIds: List<String>)
}
