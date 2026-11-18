package br.com.soe.campo.data

import br.com.soe.campo.data.remote.RemoteAssignment
import br.com.soe.campo.data.remote.RemoteEventSummary
import java.util.Calendar
import java.util.TimeZone

/**
 * Escolhe em qual evento o operador entra.
 *
 * A regra e a escala do dia: quem opera em mais de um evento entra naquele
 * para o qual esta escalado hoje, e nao no primeiro que o servidor devolver.
 * Entrar no evento errado nao e so inconveniente — a ocorrencia registrada
 * vai parar na operacao de outro cliente.
 *
 * Preferencia, do mais forte para o mais fraco:
 *  1. turno acontecendo agora;
 *  2. turno em algum momento de hoje;
 *  3. evento em andamento agora;
 *  4. evento que ocupa algum pedaco de hoje;
 *  5. escala sem data nenhuma informada.
 *
 * O passo 5 existe por prudencia: enquanto o Web SaaS nao preencher as
 * janelas, toda escala chegaria "fora de hoje" e ninguem conseguiria entrar.
 * Sem data, a escala continua valendo.
 *
 * Empate resolve pelo que comeca mais perto de agora.
 */
object AssignmentPicker {

    private const val NO_WINDOW = 4
    private const val NOT_TODAY = Int.MAX_VALUE

    data class Choice(
        val assignment: RemoteAssignment?,
        val event: RemoteEventSummary,
        /** Por que esta escala foi escolhida, para a trilha e para a tela. */
        val reason: String,
    )

    /**
     * @param assignments escalas do usuario, como vieram do bootstrap.
     * @param events eventos visiveis ao usuario, usados so quando ele nao tem
     *   escala nenhuma (perfil de gestao, que acompanha sem estar escalado).
     * @param nowMs instante do login.
     */
    fun pick(
        assignments: List<RemoteAssignment>,
        events: List<RemoteEventSummary>,
        nowMs: Long,
    ): Result<Choice> {
        val day = dayWindow(nowMs)

        val ranked = assignments
            .map { it to rank(it, day, nowMs) }
            .filter { it.second != NOT_TODAY }
            .sortedWith(
                compareBy(
                    { it.second },
                    { distanceToNow(it.first, nowMs) },
                ),
            )

        ranked.firstOrNull()?.let { (assignment, rank) ->
            return Result.success(
                Choice(
                    assignment = assignment,
                    event = assignment.event,
                    reason = reasonFor(rank),
                ),
            )
        }

        // Sem escala hoje: um evento rodando hoje ainda serve para quem
        // acompanha a operacao sem estar escalado.
        val eventToday = events
            .filter { overlapsDay(Time.parse(it.eventStart), Time.parse(it.eventEnd), day) }
            .minByOrNull { Time.parse(it.eventStart) ?: Long.MAX_VALUE }

        if (eventToday != null) {
            return Result.success(
                Choice(
                    assignment = null,
                    event = eventToday,
                    reason = "Evento em andamento hoje",
                ),
            )
        }

        return Result.failure(NoAssignmentTodayException(nextScaleMessage(assignments, events, nowMs)))
    }

    private fun rank(assignment: RemoteAssignment, day: LongRange, nowMs: Long): Int {
        val shiftStart = Time.parse(assignment.shift?.startsAt)
        val shiftEnd = Time.parse(assignment.shift?.endsAt)
        val eventStart = Time.parse(assignment.event.eventStart)
        val eventEnd = Time.parse(assignment.event.eventEnd)

        val hasWindow = shiftStart != null || shiftEnd != null ||
            eventStart != null || eventEnd != null

        return when {
            contains(shiftStart, shiftEnd, nowMs) -> 0
            overlapsDay(shiftStart, shiftEnd, day) -> 1
            contains(eventStart, eventEnd, nowMs) -> 2
            overlapsDay(eventStart, eventEnd, day) -> 3
            !hasWindow -> NO_WINDOW
            else -> NOT_TODAY
        }
    }

    private fun reasonFor(rank: Int): String = when (rank) {
        0 -> "Turno em andamento"
        1 -> "Turno de hoje"
        2 -> "Evento em andamento"
        3 -> "Evento acontece hoje"
        else -> "Escala sem data informada"
    }

    /** Dia local do aparelho — o operador pensa no dia dele, nao em UTC. */
    private fun dayWindow(nowMs: Long): LongRange {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return start until calendar.timeInMillis
    }

    /**
     * Janela aberta de um lado conta: turno sem fim cadastrado que ja comecou
     * esta em andamento, nao encerrado.
     */
    private fun contains(start: Long?, end: Long?, instant: Long): Boolean {
        if (start == null && end == null) return false
        val afterStart = start == null || instant >= start
        val beforeEnd = end == null || instant <= end
        return afterStart && beforeEnd
    }

    private fun overlapsDay(start: Long?, end: Long?, day: LongRange): Boolean {
        if (start == null && end == null) return false
        val from = start ?: Long.MIN_VALUE
        val to = end ?: Long.MAX_VALUE
        return from <= day.last && to >= day.first
    }

    /** Quanto a escala esta longe de agora, para desempatar. */
    private fun distanceToNow(assignment: RemoteAssignment, nowMs: Long): Long {
        val reference = Time.parse(assignment.shift?.startsAt)
            ?: Time.parse(assignment.event.eventStart)
            ?: return Long.MAX_VALUE
        return kotlin.math.abs(reference - nowMs)
    }

    /**
     * Mensagem do erro: dizer apenas "sem escala hoje" deixa o operador sem
     * saber se errou de conta, de dia ou se a escala ainda nao foi publicada.
     */
    private fun nextScaleMessage(
        assignments: List<RemoteAssignment>,
        events: List<RemoteEventSummary>,
        nowMs: Long,
    ): String {
        val next = assignments
            .mapNotNull { assignment ->
                val start = Time.parse(assignment.shift?.startsAt)
                    ?: Time.parse(assignment.event.eventStart)
                    ?: return@mapNotNull null
                if (start <= nowMs) null else assignment to start
            }
            .minByOrNull { it.second }

        if (next != null) {
            return "Voce nao esta escalado para hoje. Sua proxima escala e em " +
                "${next.first.event.name}, a partir de ${Time.full(next.second)}."
        }

        if (assignments.isEmpty() && events.isEmpty()) {
            return "Nenhum evento atribuido a este usuario."
        }

        return "Voce nao esta escalado para hoje. Procure a coordenacao do evento."
    }
}

/** Login recusado porque nao ha escala para o dia. */
class NoAssignmentTodayException(message: String) : Exception(message)
