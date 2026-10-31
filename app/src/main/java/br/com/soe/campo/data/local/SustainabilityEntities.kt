package br.com.soe.campo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Modulo Direitos Humanos & Sustentabilidade — a parte que roda no aparelho.
 *
 * A biblioteca de checklists, as regras de aplicabilidade e a validacao das
 * evidencias sao da WEB. No app ficam a tarefa atribuida ao usuario e o que
 * ele registra em campo: resposta, comentario, justificativa e evidencia.
 */

/**
 * Item do checklist do evento atribuido a este usuario.
 *
 * Espelha os campos da secao 7 do modulo que fazem sentido em campo. O que e
 * de gestao (aprovador, parecer, dependencias) nao desce para o aparelho.
 */
@Entity(tableName = "sustainability_tasks", indices = [Index("eventId"), Index("phase")])
data class SustainabilityTaskEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val theme: String,
    val subtheme: String?,
    val title: String,
    val description: String?,
    /** PRE_EVENT, LIVE ou POST_EVENT. */
    val phase: String,
    /** REQUIRED, RECOMMENDED ou NOT_APPLICABLE. */
    val criterion: String,
    /** CRITICAL, HIGH, MEDIUM ou LOW. */
    val priority: String,
    /**
     * NOT_STARTED, IN_PROGRESS, DONE, NOT_DONE, NOT_APPLICABLE ou
     * IN_VALIDATION. O app escreve os cinco primeiros; IN_VALIDATION so chega
     * do servidor.
     */
    val status: String,
    val dueAtMs: Long?,
    val responsibleName: String?,
    val areaId: String?,
    val locationName: String?,
    /** Verificacao recorrente: aceita varias execucoes, uma por turno/dia. */
    val recurring: Boolean,
    val requiresEvidence: Boolean,
    /** Ultima resposta registrada neste aparelho, para a lista mostrar. */
    val lastAnsweredAtMs: Long?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Uma execucao da verificacao.
 *
 * Itens recorrentes geram varios registros — por isso a resposta e uma
 * entidade propria, e nao colunas na tarefa: sobrescrever a resposta anterior
 * apagaria a ronda do turno passado.
 */
@Entity(tableName = "sustainability_updates", indices = [Index("taskId"), Index("eventId")])
data class SustainabilityUpdateEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val eventId: String,
    val status: String,
    val comment: String?,
    /** Obrigatoria quando a resposta e "nao aplicavel" ou "nao atendido". */
    val justification: String?,
    val answeredAtMs: Long,
    val authorName: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** Ocorrencia/acao corretiva aberta a partir deste item, quando houve. */
    val incidentId: String?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
)
