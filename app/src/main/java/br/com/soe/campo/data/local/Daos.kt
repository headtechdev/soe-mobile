package br.com.soe.campo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(rows: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAreas(rows: List<AreaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJobRoles(rows: List<JobRoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShifts(rows: List<ShiftEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeople(rows: List<PersonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(rows: List<ChecklistTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(rows: List<ChecklistItemEntity>)

    @Query("SELECT * FROM events WHERE id = :eventId")
    fun observeEvent(eventId: String): Flow<EventEntity?>

    @Query("SELECT * FROM events ORDER BY eventStartMs DESC")
    suspend fun allEvents(): List<EventEntity>

    @Query("SELECT * FROM areas WHERE eventId = :eventId AND deleted = 0 ORDER BY level, name")
    fun observeAreas(eventId: String): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas WHERE eventId = :eventId AND deleted = 0 ORDER BY level, name")
    suspend fun areas(eventId: String): List<AreaEntity>

    @Query("SELECT * FROM shifts WHERE eventId = :eventId AND deleted = 0 ORDER BY startsAtMs")
    suspend fun shifts(eventId: String): List<ShiftEntity>

    @Query("SELECT * FROM people WHERE userId = :userId AND deleted = 0 LIMIT 1")
    fun observeMyProfile(userId: String): Flow<PersonEntity?>

    @Query("SELECT * FROM job_roles WHERE id = :id")
    suspend fun jobRole(id: String): JobRoleEntity?

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun shift(id: String): ShiftEntity?

    @Query("SELECT * FROM areas WHERE id = :id")
    suspend fun area(id: String): AreaEntity?

    @Query("UPDATE people SET status = :status, checkInAtMs = :checkIn, checkOutAtMs = :checkOut, pendingSync = 1 WHERE id = :id")
    suspend fun setPresence(id: String, status: String, checkIn: Long?, checkOut: Long?)

    @Query("SELECT * FROM people WHERE pendingSync = 1")
    suspend fun pendingPeople(): List<PersonEntity>

    @Query("UPDATE people SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingPeople(ids: List<String>)
}

@Dao
interface ChecklistDao {

    @Query(
        """
        SELECT * FROM checklist_templates
        WHERE eventId = :eventId AND deleted = 0 AND active = 1
        ORDER BY phase, name
        """
    )
    fun observeTemplates(eventId: String): Flow<List<ChecklistTemplateEntity>>

    @Query("SELECT * FROM checklist_templates WHERE id = :id")
    suspend fun template(id: String): ChecklistTemplateEntity?

    @Query("SELECT * FROM checklist_items WHERE templateId = :templateId AND deleted = 0 ORDER BY sortOrder")
    suspend fun items(templateId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE templateId = :templateId AND deleted = 0 ORDER BY sortOrder")
    fun observeItems(templateId: String): Flow<List<ChecklistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExecutions(rows: List<ChecklistExecutionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExecution(row: ChecklistExecutionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswers(rows: List<ChecklistAnswerEntity>)

    @Query("SELECT * FROM checklist_executions WHERE id = :id")
    fun observeExecution(id: String): Flow<ChecklistExecutionEntity?>

    @Query("SELECT * FROM checklist_executions WHERE id = :id")
    suspend fun execution(id: String): ChecklistExecutionEntity?

    @Query(
        """
        SELECT * FROM checklist_executions
        WHERE eventId = :eventId AND deleted = 0
        ORDER BY COALESCE(completedAtMs, startedAtMs, updatedAtMs) DESC
        LIMIT 50
        """
    )
    fun observeExecutions(eventId: String): Flow<List<ChecklistExecutionEntity>>

    @Query("SELECT * FROM checklist_answers WHERE executionId = :executionId")
    fun observeAnswers(executionId: String): Flow<List<ChecklistAnswerEntity>>

    @Query("SELECT * FROM checklist_answers WHERE executionId = :executionId")
    suspend fun answers(executionId: String): List<ChecklistAnswerEntity>

    @Query("SELECT * FROM checklist_executions WHERE pendingSync = 1")
    suspend fun pendingExecutions(): List<ChecklistExecutionEntity>

    @Query("UPDATE checklist_executions SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingExecutions(ids: List<String>)

    @Query("UPDATE checklist_answers SET pendingSync = 0 WHERE executionId IN (:ids)")
    suspend fun clearPendingAnswers(ids: List<String>)

    @Transaction
    suspend fun saveDraft(
        execution: ChecklistExecutionEntity,
        answers: List<ChecklistAnswerEntity>,
    ) {
        upsertExecution(execution)
        upsertAnswers(answers)
    }
}

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<IncidentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: IncidentEntity)

    @Update
    suspend fun update(row: IncidentEntity)

    @Query(
        """
        SELECT * FROM incidents
        WHERE eventId = :eventId AND deleted = 0
        ORDER BY occurredAtMs DESC
        """
    )
    fun observeAll(eventId: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :id")
    fun observe(id: String): Flow<IncidentEntity?>

    @Query("SELECT * FROM incidents WHERE pendingSync = 1")
    suspend fun pending(): List<IncidentEntity>

    @Query("UPDATE incidents SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Query("SELECT COUNT(*) FROM incidents WHERE eventId = :eventId AND pendingSync = 1")
    fun countPending(eventId: String): Flow<Int>
}

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AlertEntity>)

    @Query(
        """
        SELECT * FROM alerts
        WHERE eventId = :eventId AND deleted = 0
        ORDER BY COALESCE(sentAtMs, updatedAtMs) DESC
        """
    )
    fun observeAll(eventId: String): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE eventId = :eventId AND deleted = 0 AND readAtMs IS NULL")
    fun countUnread(eventId: String): Flow<Int>

    @Query("UPDATE alerts SET readAtMs = :readAt, pendingSync = 1 WHERE id = :id AND readAtMs IS NULL")
    suspend fun markRead(id: String, readAt: Long)

    @Query("UPDATE alerts SET acknowledgedAtMs = :ackAt, readAtMs = COALESCE(readAtMs, :ackAt), pendingSync = 1 WHERE id = :id")
    suspend fun acknowledge(id: String, ackAt: Long)

    @Query("SELECT * FROM alerts WHERE pendingSync = 1")
    suspend fun pending(): List<AlertEntity>

    @Query("UPDATE alerts SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)
}

@Dao
interface AttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: PendingAttachmentEntity)

    @Query("SELECT * FROM pending_attachments WHERE uploaded = 0")
    suspend fun pending(): List<PendingAttachmentEntity>

    @Query("SELECT * FROM pending_attachments WHERE entityId = :entityId ORDER BY capturedAtMs DESC")
    fun observeFor(entityId: String): Flow<List<PendingAttachmentEntity>>

    @Query("UPDATE pending_attachments SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<String>)

    @Query("SELECT COUNT(*) FROM pending_attachments WHERE uploaded = 0")
    fun countPending(): Flow<Int>
}

@Dao
interface SyncStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE eventId = :eventId")
    suspend fun get(eventId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE eventId = :eventId")
    fun observe(eventId: String): Flow<SyncStateEntity?>
}
