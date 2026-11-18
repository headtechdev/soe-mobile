package br.com.soe.campo.data

import br.com.soe.campo.data.remote.RemoteAssignment
import br.com.soe.campo.data.remote.RemoteEventSummary
import br.com.soe.campo.data.remote.RemoteShiftRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * A escolha do evento e regra de data, e data e onde se erra de graca. Os
 * casos abaixo sao os que aparecem em operacao: quem trabalha em dois eventos
 * na mesma semana, quem abre o app antes do turno comecar e quem entra num
 * servidor que ainda nao preenche as janelas.
 */
class AssignmentPickerTest {

    private val now = at(2026, Calendar.NOVEMBER, 18, 14, 0)

    @Test
    fun `escolhe o evento cujo turno esta em andamento`() {
        val hoje = assignment(
            personId = "p1",
            eventId = "evento-hoje",
            shiftStart = at(2026, Calendar.NOVEMBER, 18, 12, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 18, 20, 0),
        )
        val outro = assignment(
            personId = "p2",
            eventId = "evento-sabado",
            shiftStart = at(2026, Calendar.NOVEMBER, 21, 8, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 21, 18, 0),
        )

        // A ordem do servidor e a do azar: o evento errado vem primeiro.
        val choice = AssignmentPicker.pick(listOf(outro, hoje), emptyList(), now).getOrThrow()

        assertEquals("evento-hoje", choice.event.id)
        assertEquals("Turno em andamento", choice.reason)
    }

    @Test
    fun `turno que ainda vai comecar hoje vale`() {
        val maisTarde = assignment(
            personId = "p1",
            eventId = "evento-noite",
            shiftStart = at(2026, Calendar.NOVEMBER, 18, 22, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 19, 4, 0),
        )

        val choice = AssignmentPicker.pick(listOf(maisTarde), emptyList(), now).getOrThrow()

        assertEquals("evento-noite", choice.event.id)
        assertEquals("Turno de hoje", choice.reason)
    }

    @Test
    fun `entre dois turnos de hoje fica o mais proximo de agora`() {
        val manha = assignment(
            personId = "p1",
            eventId = "evento-manha",
            shiftStart = at(2026, Calendar.NOVEMBER, 18, 6, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 18, 10, 0),
        )
        val noite = assignment(
            personId = "p2",
            eventId = "evento-noite",
            shiftStart = at(2026, Calendar.NOVEMBER, 18, 16, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 18, 23, 0),
        )

        val choice = AssignmentPicker.pick(listOf(manha, noite), emptyList(), now).getOrThrow()

        assertEquals("evento-noite", choice.event.id)
    }

    @Test
    fun `sem turno cadastrado usa a janela do evento`() {
        val semTurno = assignment(
            personId = "p1",
            eventId = "evento-semana",
            shiftStart = null,
            shiftEnd = null,
            eventStart = at(2026, Calendar.NOVEMBER, 16, 8, 0),
            eventEnd = at(2026, Calendar.NOVEMBER, 22, 23, 0),
        )

        val choice = AssignmentPicker.pick(listOf(semTurno), emptyList(), now).getOrThrow()

        assertEquals("evento-semana", choice.event.id)
        assertEquals("Evento em andamento", choice.reason)
    }

    @Test
    fun `escala sem data nenhuma continua valendo`() {
        // Enquanto o Web SaaS nao preencher as janelas, barrar por data
        // deixaria todo mundo de fora.
        val semData = assignment(
            personId = "p1",
            eventId = "evento-sem-data",
            shiftStart = null,
            shiftEnd = null,
            eventStart = null,
            eventEnd = null,
        )

        val choice = AssignmentPicker.pick(listOf(semData), emptyList(), now).getOrThrow()

        assertEquals("evento-sem-data", choice.event.id)
    }

    @Test
    fun `turno de hoje ganha de evento que so esta em andamento`() {
        // O caso que motivou a regra: duas escalas, as duas "de hoje" se a
        // conta for so a janela do evento. O turno e o desempate.
        val eventoLongo = assignment(
            personId = "p1",
            eventId = "evento-do-mes",
            shiftStart = null,
            shiftEnd = null,
            eventStart = at(2026, Calendar.NOVEMBER, 1, 0, 0),
            eventEnd = at(2026, Calendar.NOVEMBER, 30, 23, 59),
        )
        val turnoHoje = assignment(
            personId = "p2",
            eventId = "evento-de-hoje",
            shiftStart = at(2026, Calendar.NOVEMBER, 18, 12, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 18, 20, 0),
            eventStart = at(2026, Calendar.NOVEMBER, 18, 6, 0),
            eventEnd = at(2026, Calendar.NOVEMBER, 18, 23, 59),
        )

        val choice = AssignmentPicker.pick(listOf(eventoLongo, turnoHoje), emptyList(), now)
            .getOrThrow()

        assertEquals("evento-de-hoje", choice.event.id)
    }

    @Test
    fun `escala encerrada antes de hoje nao entra`() {
        // Turno de ontem, num evento que tambem acabou ontem: nao ha nada
        // acontecendo hoje para este usuario.
        val ontem = assignment(
            personId = "p1",
            eventId = "evento-ontem",
            shiftStart = at(2026, Calendar.NOVEMBER, 17, 8, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 17, 18, 0),
            eventStart = at(2026, Calendar.NOVEMBER, 17, 6, 0),
            eventEnd = at(2026, Calendar.NOVEMBER, 17, 23, 0),
        )

        val result = AssignmentPicker.pick(listOf(ontem), emptyList(), now)

        assertTrue(result.isFailure)
    }

    @Test
    fun `turno de ontem em evento que segue hoje ainda entra`() {
        // Decisao deliberada: trancar do lado de fora quem e equipe de um
        // evento que esta rodando agora e pior do que deixar entrar. O turno
        // do dia continua tendo precedencia quando existe.
        val eventoSegue = assignment(
            personId = "p1",
            eventId = "evento-em-andamento",
            shiftStart = at(2026, Calendar.NOVEMBER, 17, 8, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 17, 18, 0),
            eventStart = at(2026, Calendar.NOVEMBER, 16, 6, 0),
            eventEnd = at(2026, Calendar.NOVEMBER, 22, 23, 0),
        )

        val choice = AssignmentPicker.pick(listOf(eventoSegue), emptyList(), now).getOrThrow()

        assertEquals("Evento em andamento", choice.reason)
    }

    @Test
    fun `erro diz qual e a proxima escala`() {
        val sabado = assignment(
            personId = "p1",
            eventId = "evento-sabado",
            shiftStart = at(2026, Calendar.NOVEMBER, 21, 8, 0),
            shiftEnd = at(2026, Calendar.NOVEMBER, 21, 18, 0),
            eventStart = at(2026, Calendar.NOVEMBER, 21, 6, 0),
            eventEnd = at(2026, Calendar.NOVEMBER, 21, 23, 0),
        )

        val error = AssignmentPicker.pick(listOf(sabado), emptyList(), now).exceptionOrNull()

        assertTrue(error is NoAssignmentTodayException)
        assertTrue(error!!.message!!.contains("21/11/2026"))
    }

    @Test
    fun `sem escala cai no evento que acontece hoje`() {
        // Perfil de gestao: acompanha a operacao sem estar escalado.
        val evento = event(
            id = "evento-hoje",
            start = at(2026, Calendar.NOVEMBER, 18, 8, 0),
            end = at(2026, Calendar.NOVEMBER, 18, 23, 0),
        )

        val choice = AssignmentPicker.pick(emptyList(), listOf(evento), now).getOrThrow()

        assertEquals("evento-hoje", choice.event.id)
        assertEquals(null, choice.assignment)
    }

    @Test
    fun `sem escala e sem evento hoje nao entra`() {
        val outroMes = event(
            id = "evento-dezembro",
            start = at(2026, Calendar.DECEMBER, 10, 8, 0),
            end = at(2026, Calendar.DECEMBER, 12, 23, 0),
        )

        val result = AssignmentPicker.pick(emptyList(), listOf(outroMes), now)

        assertTrue(result.isFailure)
    }

    // ------------------------------------------------------------ helpers

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun assignment(
        personId: String,
        eventId: String,
        shiftStart: Long?,
        shiftEnd: Long?,
        // Por padrao o evento acompanha o turno: uma janela larga demais aqui
        // faz o teste passar pelo motivo errado.
        eventStart: Long? = shiftStart,
        eventEnd: Long? = shiftEnd,
    ) = RemoteAssignment(
        personId = personId,
        status = "CONFIRMED",
        shift = if (shiftStart == null && shiftEnd == null) {
            null
        } else {
            RemoteShiftRef(
                id = "turno-$personId",
                name = "Turno",
                startsAt = shiftStart?.let { Time.toIso(it) },
                endsAt = shiftEnd?.let { Time.toIso(it) },
            )
        },
        event = event(eventId, eventStart, eventEnd),
    )

    private fun event(id: String, start: Long?, end: Long?) = RemoteEventSummary(
        id = id,
        name = id,
        code = id.uppercase(),
        status = "ACTIVE",
        eventStart = start?.let { Time.toIso(it) },
        eventEnd = end?.let { Time.toIso(it) },
    )
}
