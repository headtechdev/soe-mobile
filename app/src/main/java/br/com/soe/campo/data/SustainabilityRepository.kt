package br.com.soe.campo.data

import br.com.soe.campo.data.local.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

/**
 * Direitos Humanos & Sustentabilidade em campo (secao 10 do modulo).
 *
 * A verificacao e sempre um registro novo, nunca uma sobrescrita: item
 * recorrente guarda a ronda de cada turno, e evidencia validada nao e
 * substituida em silencio.
 */
class SustainabilityRepository(
    private val db: SoeDatabase,
    private val fieldRepository: FieldRepository,
) {

    private val dao get() = db.sustainabilityDao()

    fun observeTasks(eventId: String): Flow<List<SustainabilityTaskEntity>> =
        dao.observeTasks(eventId)

    fun observeTask(taskId: String): Flow<SustainabilityTaskEntity?> = dao.observeTask(taskId)

    fun observeUpdates(taskId: String): Flow<List<SustainabilityUpdateEntity>> =
        dao.observeUpdates(taskId)

    fun observeOpenRequired(eventId: String): Flow<Int> = dao.countOpenRequired(eventId)

    fun observePending(): Flow<Int> = dao.countPending()

    fun observeEvidence(taskId: String): Flow<List<PendingAttachmentEntity>> =
        db.attachmentDao().observeFor(taskId)

    suspend fun task(taskId: String): SustainabilityTaskEntity? = dao.task(taskId)

    /**
     * Responde o item.
     *
     * "Nao aplicavel" e "nao atendido" exigem justificativa — a regra vale
     * aqui, e nao so na tela, para que nenhum caminho de gravacao escape
     * dela. A tela so precisa avisar antes.
     */
    suspend fun answer(
        taskId: String,
        status: String,
        comment: String?,
        justification: String?,
        authorName: String?,
        latitude: Double?,
        longitude: Double?,
        incidentId: String? = null,
    ): Result<String> {
        val task = dao.task(taskId) ?: return Result.failure(IllegalStateException("Tarefa nao encontrada"))

        val needsJustification = status == "NOT_APPLICABLE" || status == "NOT_DONE"
        if (needsJustification && justification.isNullOrBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "Justificativa obrigatoria para \"nao aplicavel\" e \"nao atendido\".",
                ),
            )
        }

        val now = System.currentTimeMillis()
        val updateId = UUID.randomUUID().toString()

        dao.answer(
            task.copy(
                status = status,
                lastAnsweredAtMs = now,
                updatedAtMs = now,
                pendingSync = true,
            ),
            SustainabilityUpdateEntity(
                id = updateId,
                taskId = taskId,
                eventId = task.eventId,
                status = status,
                comment = comment?.trim()?.ifBlank { null },
                justification = justification?.trim()?.ifBlank { null },
                answeredAtMs = now,
                authorName = authorName,
                latitude = latitude,
                longitude = longitude,
                incidentId = incidentId,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )

        return Result.success(updateId)
    }

    /**
     * Anexa evidencia ao item. Aceita foto, video e arquivo — a secao 8 do
     * modulo trata os tres como evidencia de primeira classe, e limitar a
     * foto deixaria de fora justamente o registro de ronda e o comprovante
     * de destinacao.
     */
    suspend fun attachEvidence(
        taskId: String,
        localPath: String,
        mimeType: String,
        latitude: Double?,
        longitude: Double?,
    ) {
        val task = dao.task(taskId) ?: return
        val file = File(localPath)
        if (!file.exists()) return

        db.attachmentDao().insert(
            PendingAttachmentEntity(
                id = UUID.randomUUID().toString(),
                eventId = task.eventId,
                entityType = "SUSTAINABILITY_TASK",
                entityId = taskId,
                localPath = localPath,
                fileName = file.name,
                mimeType = mimeType,
                capturedAtMs = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    /**
     * Abre ocorrencia/acao corretiva a partir do item e deixa o vinculo
     * gravado nos dois lados, para a web conseguir fechar o circuito.
     */
    suspend fun openCorrectiveAction(
        taskId: String,
        title: String,
        description: String?,
        severity: String,
        latitude: Double?,
        longitude: Double?,
        authorName: String?,
    ): String {
        val task = dao.task(taskId) ?: error("Tarefa nao encontrada")

        val incidentId = fieldRepository.reportIncident(
            eventId = task.eventId,
            title = title.trim(),
            description = description?.trim()?.ifBlank { null },
            // A matriz de tipos do SOE nao tem um socioambiental proprio;
            // OTHER com a referencia do item no texto mantem a ocorrencia
            // rastreavel ate a web ganhar um tipo dedicado.
            type = "OTHER",
            severity = severity,
            areaId = task.areaId,
            locationDescription = task.locationName,
            latitude = latitude,
            longitude = longitude,
        )

        answer(
            taskId = taskId,
            status = "NOT_DONE",
            comment = "Acao corretiva aberta: ${title.trim()}",
            justification = description?.trim()?.ifBlank { null }
                ?: "Nao conformidade registrada em campo.",
            authorName = authorName,
            latitude = latitude,
            longitude = longitude,
            incidentId = incidentId,
        )

        return incidentId
    }
}
