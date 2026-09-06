package br.com.soe.campo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Espelho local (SQLite) do subconjunto do modelo do Web SaaS que interessa a
 * operacao de campo.
 *
 * Convencoes:
 *  - `id` e sempre o mesmo UUID usado no servidor; registros criados offline
 *    geram o UUID aqui, o que torna o push idempotente.
 *  - `pendingSync` marca o que ainda precisa subir.
 *  - `updatedAtMs` guarda o updatedAt do servidor para o pull incremental.
 */

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val status: String,
    val venueName: String?,
    val city: String?,
    val eventStartMs: Long?,
    val eventEndMs: Long?,
    val updatedAtMs: Long,
)

@Entity(tableName = "areas", indices = [Index("eventId")])
data class AreaEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val parentId: String?,
    val name: String,
    val code: String,
    val color: String,
    val level: Int,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "job_roles", indices = [Index("eventId")])
data class JobRoleEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val areaId: String?,
    val name: String,
    val hierarchy: Int,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "shifts", indices = [Index("eventId")])
data class ShiftEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val name: String,
    val phase: String,
    val startsAtMs: Long,
    val endsAtMs: Long,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "people", indices = [Index("eventId"), Index("userId")])
data class PersonEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val userId: String?,
    val areaId: String?,
    val jobRoleId: String?,
    val shiftId: String?,
    val supervisorId: String?,
    val name: String,
    val badgeCode: String?,
    val status: String,
    val checkInAtMs: Long?,
    val checkOutAtMs: Long?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(tableName = "checklist_templates", indices = [Index("eventId")])
data class ChecklistTemplateEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val areaId: String?,
    val shiftId: String?,
    val name: String,
    val description: String?,
    val phase: String,
    val requiresPhoto: Boolean,
    val requiresGeo: Boolean,
    val active: Boolean,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "checklist_items", indices = [Index("templateId")])
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val label: String,
    val helpText: String?,
    val type: String,
    val required: Boolean,
    val requiresPhoto: Boolean,
    val sortOrder: Int,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "checklist_executions", indices = [Index("eventId"), Index("templateId")])
data class ChecklistExecutionEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val templateId: String,
    val areaId: String?,
    val shiftId: String?,
    val status: String,
    val startedAtMs: Long?,
    val completedAtMs: Long?,
    val score: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(tableName = "checklist_answers", indices = [Index("executionId"), Index("itemId")])
data class ChecklistAnswerEntity(
    @PrimaryKey val id: String,
    val executionId: String,
    val itemId: String,
    val valueBool: Boolean?,
    val valueText: String?,
    val valueNumber: Double?,
    val conform: Boolean?,
    val notes: String?,
    val answeredAtMs: Long?,
    val pendingSync: Boolean = false,
)

@Entity(tableName = "incidents", indices = [Index("eventId"), Index("status")])
data class IncidentEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val areaId: String?,
    val code: String,
    val title: String,
    val description: String?,
    val type: String,
    val severity: String,
    val status: String,
    val locationDescription: String?,
    val latitude: Double?,
    val longitude: Double?,
    val occurredAtMs: Long,
    val resolvedAtMs: Long?,
    val resolution: String?,
    val peopleAffected: Int?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(tableName = "alerts", indices = [Index("eventId")])
data class AlertEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val areaId: String?,
    val title: String,
    val message: String,
    val level: String,
    val scope: String,
    val requiresAck: Boolean,
    // Quando maior que zero, este alerta chegou ao operador tambem em nome de
    // pessoas da equipe dele que nao usam o app.
    val relayCount: Int,
    val sentAtMs: Long?,
    val readAtMs: Long?,
    val acknowledgedAtMs: Long?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

/** Foto capturada em campo, aguardando upload no proximo sincronismo. */
@Entity(tableName = "pending_attachments", indices = [Index("entityId")])
data class PendingAttachmentEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val entityType: String,
    val entityId: String,
    val localPath: String,
    val fileName: String,
    val mimeType: String,
    val capturedAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val uploaded: Boolean = false,
)

/** Marca d'agua do pull incremental por evento. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val eventId: String,
    val lastSyncIso: String?,
    val lastSyncAtMs: Long?,
    val lastError: String?,
)
