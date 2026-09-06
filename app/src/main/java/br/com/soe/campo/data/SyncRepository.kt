package br.com.soe.campo.data

import android.util.Base64
import br.com.soe.campo.data.local.*
import br.com.soe.campo.data.remote.*
import java.io.File

/**
 * Motor de sincronismo.
 *
 * O ciclo e sempre push-depois-pull:
 *   1. sobe o que foi produzido offline (ocorrencias, checklists, fotos,
 *      leituras de alerta, presenca);
 *   2. baixa tudo o que mudou no servidor desde o ultimo `serverTime`.
 *
 * Nada e apagado da fila local antes de o servidor confirmar o id, e o
 * `serverTime` so avanca quando o pull chega inteiro — se a conexao cair no
 * meio, a proxima tentativa refaz o mesmo intervalo sem perder registro.
 */
class SyncRepository(
    private val db: SoeDatabase,
    private val session: SessionStore,
    private val apiProvider: suspend () -> SoeApi,
) {

    data class Result(
        val pushed: Int,
        val pulled: Int,
        val rejected: List<PushRejection>,
    )

    suspend fun sync(): Result {
        val current = session.current()
        val eventId = current.eventId ?: error("Nenhum evento selecionado")
        val api = apiProvider()
        val installationId = session.installationId()

        val pushed = push(api, eventId, installationId)
        val pulled = pull(api, eventId, installationId)

        return Result(pushed = pushed.first, pulled = pulled, rejected = pushed.second)
    }

    // ------------------------------------------------------------- PUSH

    private suspend fun push(
        api: SoeApi,
        eventId: String,
        installationId: String,
    ): Pair<Int, List<PushRejection>> {
        val incidents = db.incidentDao().pending()
        val executions = db.checklistDao().pendingExecutions()
        val attachments = db.attachmentDao().pending()
        val alerts = db.alertDao().pending()
        val people = db.catalogDao().pendingPeople()

        val totalPending =
            incidents.size + executions.size + attachments.size + alerts.size + people.size
        if (totalPending == 0) return 0 to emptyList()

        val request = PushRequest(
            eventId = eventId,
            installationId = installationId,
            incidents = incidents.map { it.toPush() },
            checklistExecutions = executions.map { execution ->
                execution.toPush(db.checklistDao().answers(execution.id))
            },
            attachments = attachments.mapNotNull { it.toPush() },
            alertReceipts = alerts.map {
                PushReceipt(
                    alertId = it.id,
                    readAt = Time.toIsoOrNull(it.readAtMs),
                    acknowledgedAt = Time.toIsoOrNull(it.acknowledgedAtMs),
                )
            },
            personStatus = people.map {
                PushPersonStatus(
                    id = it.id,
                    status = it.status,
                    checkInAt = Time.toIsoOrNull(it.checkInAtMs),
                    checkOutAt = Time.toIsoOrNull(it.checkOutAtMs),
                )
            },
        )

        val response = api.push(request)

        // Limpa da fila apenas os ids que o servidor confirmou.
        response.accepted["incidents"]?.let { db.incidentDao().clearPending(it) }
        response.accepted["checklistExecutions"]?.let {
            db.checklistDao().clearPendingExecutions(it)
            db.checklistDao().clearPendingAnswers(it)
        }
        response.accepted["attachments"]?.let { db.attachmentDao().markUploaded(it) }
        response.accepted["alertReceipts"]?.let { db.alertDao().clearPending(it) }
        response.accepted["personStatus"]?.let { db.catalogDao().clearPendingPeople(it) }

        val accepted = response.accepted.values.sumOf { it.size }
        return accepted to response.rejected
    }

    private fun IncidentEntity.toPush() = PushIncident(
        id = id,
        title = title,
        description = description,
        type = type,
        severity = severity,
        status = status,
        areaId = areaId,
        locationDescription = locationDescription,
        latitude = latitude,
        longitude = longitude,
        occurredAt = Time.toIso(occurredAtMs),
        clientCreatedAt = Time.toIso(updatedAtMs),
        peopleAffected = peopleAffected,
        resolution = resolution,
    )

    private fun ChecklistExecutionEntity.toPush(answers: List<ChecklistAnswerEntity>) =
        PushExecution(
            id = id,
            templateId = templateId,
            areaId = areaId,
            shiftId = shiftId,
            status = status,
            startedAt = Time.toIsoOrNull(startedAtMs),
            completedAt = Time.toIsoOrNull(completedAtMs),
            score = score,
            latitude = latitude,
            longitude = longitude,
            notes = notes,
            clientCreatedAt = Time.toIso(updatedAtMs),
            answers = answers.map { answer ->
                PushAnswer(
                    id = answer.id,
                    itemId = answer.itemId,
                    valueBool = answer.valueBool,
                    valueText = answer.valueText,
                    valueNumber = answer.valueNumber,
                    conform = answer.conform,
                    notes = answer.notes,
                    answeredAt = Time.toIsoOrNull(answer.answeredAtMs),
                )
            },
        )

    private fun PendingAttachmentEntity.toPush(): PushAttachment? {
        val file = File(localPath)
        if (!file.exists()) return null

        return PushAttachment(
            id = id,
            entityType = entityType,
            entityId = entityId,
            fileName = fileName,
            mimeType = mimeType,
            base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
            capturedAt = Time.toIso(capturedAtMs),
            latitude = latitude,
            longitude = longitude,
        )
    }

    // ------------------------------------------------------------- PULL

    private suspend fun pull(api: SoeApi, eventId: String, installationId: String): Int {
        val state = db.syncStateDao().get(eventId)
        val response = api.pull(eventId, state?.lastSyncIso, installationId)

        val catalog = db.catalogDao()

        response.event?.let { event ->
            catalog.upsertEvents(
                listOf(
                    EventEntity(
                        id = event.id,
                        name = event.name,
                        code = event.code,
                        status = event.status,
                        venueName = event.venueName,
                        city = event.city,
                        eventStartMs = Time.parse(event.eventStart),
                        eventEndMs = Time.parse(event.eventEnd),
                        updatedAtMs = Time.parseOr(event.updatedAt, System.currentTimeMillis()),
                    ),
                ),
            )
        }

        catalog.upsertAreas(
            response.areas.map {
                AreaEntity(
                    id = it.id,
                    eventId = it.eventId,
                    parentId = it.parentId,
                    name = it.name,
                    code = it.code,
                    color = it.color,
                    level = it.level,
                    updatedAtMs = Time.parseOr(it.updatedAt, 0),
                    deleted = it.deletedAt != null,
                )
            },
        )

        catalog.upsertJobRoles(
            response.jobRoles.map {
                JobRoleEntity(
                    id = it.id,
                    eventId = it.eventId,
                    areaId = it.areaId,
                    name = it.name,
                    hierarchy = it.hierarchy,
                    updatedAtMs = Time.parseOr(it.updatedAt, 0),
                    deleted = it.deletedAt != null,
                )
            },
        )

        catalog.upsertShifts(
            response.shifts.map {
                ShiftEntity(
                    id = it.id,
                    eventId = it.eventId,
                    name = it.name,
                    phase = it.phase,
                    startsAtMs = Time.parseOr(it.startsAt, 0),
                    endsAtMs = Time.parseOr(it.endsAt, 0),
                    updatedAtMs = Time.parseOr(it.updatedAt, 0),
                    deleted = it.deletedAt != null,
                )
            },
        )

        // Presenca alterada localmente e ainda nao confirmada tem prioridade
        // sobre o que veio do servidor.
        val pendingPeopleIds = catalog.pendingPeople().map { it.id }.toSet()
        catalog.upsertPeople(
            response.people
                .filterNot { it.id in pendingPeopleIds }
                .map {
                    PersonEntity(
                        id = it.id,
                        eventId = it.eventId,
                        userId = it.userId,
                        areaId = it.areaId,
                        jobRoleId = it.jobRoleId,
                        shiftId = it.shiftId,
                        supervisorId = it.supervisorId,
                        name = it.name,
                        badgeCode = it.badgeCode,
                        status = it.status,
                        checkInAtMs = Time.parse(it.checkInAt),
                        checkOutAtMs = Time.parse(it.checkOutAt),
                        updatedAtMs = Time.parseOr(it.updatedAt, 0),
                        deleted = it.deletedAt != null,
                    )
                },
        )

        catalog.upsertTemplates(
            response.checklistTemplates.map {
                ChecklistTemplateEntity(
                    id = it.id,
                    eventId = it.eventId,
                    areaId = it.areaId,
                    shiftId = it.shiftId,
                    name = it.name,
                    description = it.description,
                    phase = it.phase,
                    requiresPhoto = it.requiresPhoto,
                    requiresGeo = it.requiresGeo,
                    active = it.active,
                    updatedAtMs = Time.parseOr(it.updatedAt, 0),
                    deleted = it.deletedAt != null,
                )
            },
        )

        catalog.upsertItems(
            response.checklistTemplateItems.map {
                ChecklistItemEntity(
                    id = it.id,
                    templateId = it.templateId,
                    label = it.label,
                    helpText = it.helpText,
                    type = it.type,
                    required = it.required,
                    requiresPhoto = it.requiresPhoto,
                    sortOrder = it.sortOrder,
                    updatedAtMs = Time.parseOr(it.updatedAt, 0),
                    deleted = it.deletedAt != null,
                )
            },
        )

        // Execucoes e ocorrencias com fila local pendente nao sao sobrescritas.
        val pendingExecutionIds = db.checklistDao().pendingExecutions().map { it.id }.toSet()
        db.checklistDao().upsertExecutions(
            response.checklistExecutions
                .filterNot { it.id in pendingExecutionIds }
                .map {
                    ChecklistExecutionEntity(
                        id = it.id,
                        eventId = it.eventId,
                        templateId = it.templateId,
                        areaId = it.areaId,
                        shiftId = it.shiftId,
                        status = it.status,
                        startedAtMs = Time.parse(it.startedAt),
                        completedAtMs = Time.parse(it.completedAt),
                        score = it.score,
                        latitude = null,
                        longitude = null,
                        notes = it.notes,
                        updatedAtMs = Time.parseOr(it.updatedAt, 0),
                        deleted = it.deletedAt != null,
                    )
                },
        )

        db.checklistDao().upsertAnswers(
            response.checklistAnswers
                .filterNot { it.executionId in pendingExecutionIds }
                .map {
                    ChecklistAnswerEntity(
                        id = it.id,
                        executionId = it.executionId,
                        itemId = it.itemId,
                        valueBool = it.valueBool,
                        valueText = it.valueText,
                        valueNumber = null,
                        conform = it.conform,
                        notes = it.notes,
                        answeredAtMs = Time.parse(it.answeredAt),
                    )
                },
        )

        val pendingIncidentIds = db.incidentDao().pending().map { it.id }.toSet()
        db.incidentDao().upsertAll(
            response.incidents
                .filterNot { it.id in pendingIncidentIds }
                .map {
                    IncidentEntity(
                        id = it.id,
                        eventId = it.eventId,
                        areaId = it.areaId,
                        code = it.code,
                        title = it.title,
                        description = it.description,
                        type = it.type,
                        severity = it.severity,
                        status = it.status,
                        locationDescription = it.locationDescription,
                        latitude = null,
                        longitude = null,
                        occurredAtMs = Time.parseOr(it.occurredAt, 0),
                        resolvedAtMs = Time.parse(it.resolvedAt),
                        resolution = it.resolution,
                        peopleAffected = it.peopleAffected,
                        updatedAtMs = Time.parseOr(it.updatedAt, 0),
                        deleted = it.deletedAt != null,
                    )
                },
        )

        val receiptByAlert = response.alertReceipts.associateBy { it.alertId }
        val pendingAlertIds = db.alertDao().pending().map { it.id }.toSet()
        db.alertDao().upsertAll(
            response.alerts
                .filterNot { it.id in pendingAlertIds }
                .map {
                    val receipt = receiptByAlert[it.id]
                    AlertEntity(
                        id = it.id,
                        eventId = it.eventId,
                        areaId = it.areaId,
                        title = it.title,
                        message = it.message,
                        level = it.level,
                        scope = it.scope,
                        requiresAck = it.requiresAck,
                        relayCount = receipt?.relayCount ?: 0,
                        sentAtMs = Time.parse(it.sentAt),
                        readAtMs = Time.parse(receipt?.readAt),
                        acknowledgedAtMs = Time.parse(receipt?.acknowledgedAt),
                        updatedAtMs = Time.parseOr(it.updatedAt, 0),
                        deleted = it.deletedAt != null,
                    )
                },
        )

        db.syncStateDao().put(
            SyncStateEntity(
                eventId = eventId,
                lastSyncIso = response.serverTime,
                lastSyncAtMs = System.currentTimeMillis(),
                lastError = null,
            ),
        )

        return response.areas.size + response.people.size + response.incidents.size +
            response.alerts.size + response.checklistTemplates.size +
            response.checklistExecutions.size
    }

    suspend fun recordError(eventId: String, message: String) {
        val state = db.syncStateDao().get(eventId)
        db.syncStateDao().put(
            SyncStateEntity(
                eventId = eventId,
                lastSyncIso = state?.lastSyncIso,
                lastSyncAtMs = state?.lastSyncAtMs,
                lastError = message,
            ),
        )
    }
}
