package br.com.soe.campo.data

import android.content.Context
import br.com.soe.campo.data.local.*
import br.com.soe.campo.data.remote.LoginRequest
import br.com.soe.campo.data.remote.SoeApi
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

/**
 * Fonte unica de verdade das telas. Toda escrita cai primeiro no SQLite com
 * `pendingSync = true` — a tela nunca espera a rede — e o [SyncRepository]
 * cuida de subir quando houver conexao.
 */
class FieldRepository(
    private val context: Context,
    private val db: SoeDatabase,
    private val session: SessionStore,
    private val apiProvider: suspend () -> SoeApi,
) {

    // ------------------------------------------------------------ Login

    suspend fun login(email: String, password: String) {
        val api = apiProvider()
        val installationId = session.installationId()

        val response = api.login(
            LoginRequest(
                email = email.trim(),
                password = password,
                installationId = installationId,
                model = android.os.Build.MODEL,
                osVersion = android.os.Build.VERSION.RELEASE,
                appVersion = "1.0.0",
            ),
        )

        session.saveLogin(
            token = response.token,
            userId = response.user.id,
            userName = response.user.name,
            userEmail = response.user.email,
            userRole = response.user.role,
        )

        // O perfil vem no proprio login quando o usuario esta vinculado a uma
        // pessoa do evento; senao caimos no bootstrap para escolher o evento.
        response.profile?.let { profile ->
            session.saveAssignment(
                eventId = profile.eventId,
                eventName = profile.eventName,
                personId = profile.personId,
                areaName = profile.area?.name,
                roleName = profile.jobRole?.name,
                shiftName = profile.shift?.name,
                badge = profile.badgeCode,
            )
            return
        }

        val bootstrap = api.bootstrap()
        val assignment = bootstrap.assignments.firstOrNull()
        if (assignment != null) {
            session.saveAssignment(
                eventId = assignment.event.id,
                eventName = assignment.event.name,
                personId = assignment.personId,
                areaName = assignment.area?.name,
                roleName = assignment.jobRole?.name,
                shiftName = assignment.shift?.name,
                badge = assignment.badgeCode,
            )
            return
        }

        val fallback = bootstrap.events.firstOrNull()
            ?: error("Nenhum evento atribuido a este usuario.")

        session.saveAssignment(
            eventId = fallback.id,
            eventName = fallback.name,
            personId = null,
            areaName = null,
            roleName = null,
            shiftName = null,
            badge = null,
        )
    }

    suspend fun logout() {
        session.logout()
    }

    // ------------------------------------------------------- Observadores

    fun observeIncidents(eventId: String): Flow<List<IncidentEntity>> =
        db.incidentDao().observeAll(eventId)

    fun observeIncident(id: String): Flow<IncidentEntity?> = db.incidentDao().observe(id)

    fun observeTemplates(eventId: String): Flow<List<ChecklistTemplateEntity>> =
        db.checklistDao().observeTemplates(eventId)

    fun observeExecutions(eventId: String): Flow<List<ChecklistExecutionEntity>> =
        db.checklistDao().observeExecutions(eventId)

    fun observeItems(templateId: String): Flow<List<ChecklistItemEntity>> =
        db.checklistDao().observeItems(templateId)

    fun observeAnswers(executionId: String): Flow<List<ChecklistAnswerEntity>> =
        db.checklistDao().observeAnswers(executionId)

    fun observeAlerts(eventId: String): Flow<List<AlertEntity>> =
        db.alertDao().observeAll(eventId)

    fun observeUnreadAlerts(eventId: String): Flow<Int> = db.alertDao().countUnread(eventId)

    fun observePendingIncidents(eventId: String): Flow<Int> =
        db.incidentDao().countPending(eventId)

    fun observePendingAttachments(): Flow<Int> = db.attachmentDao().countPending()

    fun observeAreas(eventId: String): Flow<List<AreaEntity>> =
        db.catalogDao().observeAreas(eventId)

    fun observeSyncState(eventId: String): Flow<SyncStateEntity?> =
        db.syncStateDao().observe(eventId)

    fun observeMyProfile(userId: String): Flow<PersonEntity?> =
        db.catalogDao().observeMyProfile(userId)

    fun observeAttachmentsOf(entityId: String): Flow<List<PendingAttachmentEntity>> =
        db.attachmentDao().observeFor(entityId)

    suspend fun areas(eventId: String) = db.catalogDao().areas(eventId)

    suspend fun template(id: String) = db.checklistDao().template(id)

    suspend fun execution(id: String) = db.checklistDao().execution(id)

    // ------------------------------------------------------- Ocorrencias

    /**
     * Grava a ocorrencia localmente com um UUID gerado aqui. Enquanto nao
     * sincroniza, o codigo exibido e provisorio (LOCAL); o codigo definitivo
     * OC-0001 vem do servidor no proximo pull.
     */
    suspend fun reportIncident(
        eventId: String,
        title: String,
        description: String?,
        type: String,
        severity: String,
        areaId: String?,
        locationDescription: String?,
        latitude: Double?,
        longitude: Double?,
        occurredAtMs: Long = System.currentTimeMillis(),
        photoPaths: List<String> = emptyList(),
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        db.incidentDao().upsert(
            IncidentEntity(
                id = id,
                eventId = eventId,
                areaId = areaId,
                code = "LOCAL",
                title = title,
                description = description,
                type = type,
                severity = severity,
                status = "OPEN",
                locationDescription = locationDescription,
                latitude = latitude,
                longitude = longitude,
                occurredAtMs = occurredAtMs,
                resolvedAtMs = null,
                resolution = null,
                peopleAffected = null,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )

        for (path in photoPaths) {
            attachPhoto(eventId, "INCIDENT", id, path, latitude, longitude)
        }

        return id
    }

    suspend fun attachPhoto(
        eventId: String,
        entityType: String,
        entityId: String,
        localPath: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        val file = File(localPath)
        if (!file.exists()) return

        db.attachmentDao().insert(
            PendingAttachmentEntity(
                id = UUID.randomUUID().toString(),
                eventId = eventId,
                entityType = entityType,
                entityId = entityId,
                localPath = localPath,
                fileName = file.name,
                mimeType = "image/jpeg",
                capturedAtMs = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    /** Arquivo de destino da proxima foto, dentro do diretorio privado. */
    fun newPhotoFile(): File {
        val dir = File(context.filesDir, "evidencias").apply { mkdirs() }
        return File(dir, "${System.currentTimeMillis()}.jpg")
    }

    // -------------------------------------------------------- Checklists

    suspend fun startExecution(
        eventId: String,
        templateId: String,
        latitude: Double?,
        longitude: Double?,
    ): String {
        val template = db.checklistDao().template(templateId)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        db.checklistDao().upsertExecution(
            ChecklistExecutionEntity(
                id = id,
                eventId = eventId,
                templateId = templateId,
                areaId = template?.areaId,
                shiftId = template?.shiftId,
                status = "IN_PROGRESS",
                startedAtMs = now,
                completedAtMs = null,
                score = null,
                latitude = latitude,
                longitude = longitude,
                notes = null,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )

        return id
    }

    /**
     * Salva as respostas. Ao concluir, calcula a conformidade considerando
     * apenas os itens efetivamente respondidos (nao aplicavel nao entra na
     * conta) e marca a execucao para sincronizacao.
     */
    suspend fun saveExecution(
        executionId: String,
        answers: Map<String, ChecklistAnswerInput>,
        notes: String?,
        finish: Boolean,
    ) {
        val execution = db.checklistDao().execution(executionId) ?: return
        val now = System.currentTimeMillis()

        val rows = answers.map { (itemId, input) ->
            ChecklistAnswerEntity(
                id = UUID.randomUUID().toString(),
                executionId = executionId,
                itemId = itemId,
                valueBool = input.valueBool,
                valueText = input.valueText,
                valueNumber = input.valueNumber,
                conform = input.conform,
                notes = input.notes,
                answeredAtMs = now,
                pendingSync = true,
            )
        }

        val evaluated = rows.mapNotNull { it.conform }
        val score = if (evaluated.isEmpty()) {
            null
        } else {
            (evaluated.count { it } * 100) / evaluated.size
        }

        db.checklistDao().saveDraft(
            execution.copy(
                status = if (finish) "COMPLETED" else "IN_PROGRESS",
                completedAtMs = if (finish) now else null,
                score = if (finish) score else null,
                notes = notes,
                updatedAtMs = now,
                pendingSync = true,
            ),
            rows,
        )
    }

    // ------------------------------------------------------------ Alertas

    suspend fun markAlertRead(id: String) {
        db.alertDao().markRead(id, System.currentTimeMillis())
    }

    suspend fun acknowledgeAlert(id: String) {
        db.alertDao().acknowledge(id, System.currentTimeMillis())
    }

    // ----------------------------------------------------------- Presenca

    suspend fun togglePresence(personId: String, checkingIn: Boolean) {
        val now = System.currentTimeMillis()
        db.catalogDao().setPresence(
            id = personId,
            status = if (checkingIn) "CHECKED_IN" else "CHECKED_OUT",
            checkIn = if (checkingIn) now else null,
            checkOut = if (checkingIn) null else now,
        )
    }
}

data class ChecklistAnswerInput(
    val valueBool: Boolean? = null,
    val valueText: String? = null,
    val valueNumber: Double? = null,
    val conform: Boolean? = null,
    val notes: String? = null,
)
