package br.com.soe.campo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Modulo Logistica & Mobilidade — a parte que roda no aparelho.
 *
 * O planejamento (frota, rotas, programacao, alocacao) e da WEB; aqui ficam
 * apenas as entidades que o operador de campo le ou escreve: a missao que lhe
 * foi atribuida, o veiculo, o checklist pre-operacional, os passageiros e os
 * registros de estacionamento.
 *
 * Valem as mesmas convencoes do resto do banco local: `id` e o UUID do
 * servidor (gerado no aparelho quando o registro nasce offline),
 * `pendingSync` marca o que ainda precisa subir.
 */

/** Veiculo da frota. Cadastro e da WEB; o app so le. */
@Entity(tableName = "vehicles", indices = [Index("eventId")])
data class VehicleEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val code: String,
    val type: String,
    val plate: String?,
    val model: String?,
    val color: String?,
    val capacity: Int?,
    val accessible: Boolean,
    val armored: Boolean,
    val companyName: String?,
    val status: String,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
)

/**
 * Missao atribuida ao operador: uma viagem de transfer, uma volta de shuttle
 * ou um deslocamento de frota.
 *
 * O ciclo de vida acompanha a secao 4 do modulo: SCHEDULED → CHECKED_IN (4.2)
 * → IN_PROGRESS (4.5) → COMPLETED (4.8). O bloqueio do pre-operacional (4.3)
 * impede a passagem para IN_PROGRESS.
 */
@Entity(tableName = "trips", indices = [Index("eventId"), Index("status")])
data class TripEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val code: String,
    /** SHUTTLE, TRANSFER ou FLEET — muda o que a tela de embarque pede. */
    val service: String,
    val vehicleId: String?,
    val driverPersonId: String?,
    val routeName: String?,
    val originName: String?,
    val destinationName: String?,
    val scheduledStartMs: Long?,
    val scheduledEndMs: Long?,
    val status: String,
    val checkedInAtMs: Long?,
    val startedAtMs: Long?,
    val finishedAtMs: Long?,
    val odometerStart: Int?,
    val odometerEnd: Int?,
    val fuelStart: Int?,
    val fuelEnd: Int?,
    val capacity: Int?,
    /** Contagem agregada, para servico de publico geral sem lista nominal. */
    val aggregateBoarded: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val vehicleReturned: Boolean = false,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

/** Parada do circuito, na ordem em que o motorista deve cumprir. */
@Entity(tableName = "trip_stops", indices = [Index("tripId")])
data class TripStopEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val sortOrder: Int,
    val name: String,
    val kind: String,
    val scheduledAtMs: Long?,
    val arrivedAtMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val instructions: String?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Passageiro esperado na viagem.
 *
 * `status` cobre o que a secao 4.4 manda registrar: ausencia, passageiro
 * adicional, divergencia e recusa — nao so o embarque feliz.
 */
@Entity(tableName = "trip_passengers", indices = [Index("tripId")])
data class TripPassengerEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val personId: String?,
    val name: String,
    val badgeCode: String?,
    val boardingStopName: String?,
    val status: String,
    val boardedAtMs: Long?,
    val droppedAtMs: Long?,
    val boardedStopName: String?,
    val notes: String?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Checklist do veiculo: pre-operacional (4.3) ou de encerramento (4.8).
 *
 * Os itens nao vem do servidor — sao os da secao 4.3, fixos no app
 * ([br.com.soe.campo.data.VehicleCheckCatalog]), porque o motorista precisa
 * conseguir rodar o pre-operacional no primeiro uso, antes de qualquer pull.
 */
@Entity(tableName = "vehicle_checks", indices = [Index("eventId"), Index("tripId")])
data class VehicleCheckEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val tripId: String?,
    val vehicleId: String?,
    /** PRE_OP ou CLOSE_OUT. */
    val kind: String,
    val status: String,
    val startedAtMs: Long?,
    val completedAtMs: Long?,
    val odometer: Int?,
    /** Nivel de combustivel em percentual. */
    val fuelLevel: Int?,
    /** Alguma nao conformidade critica foi marcada. */
    val blocked: Boolean,
    /** Justificativa da excecao autorizada que liberou a saida mesmo assim. */
    val overrideReason: String?,
    val overrideAtMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
)

@Entity(tableName = "vehicle_check_answers", indices = [Index("checkId")])
data class VehicleCheckAnswerEntity(
    @PrimaryKey val id: String,
    val checkId: String,
    val itemKey: String,
    /** CONFORM, NON_CONFORM ou NOT_APPLICABLE. */
    val answer: String,
    val critical: Boolean,
    val notes: String?,
    val answeredAtMs: Long,
    val pendingSync: Boolean = false,
)

/** Setor de estacionamento, com a ocupacao que veio do ultimo pull. */
@Entity(tableName = "parking_sectors", indices = [Index("eventId")])
data class ParkingSectorEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val name: String,
    val capacity: Int?,
    val occupied: Int?,
    val blocked: Boolean,
    val notes: String?,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
)

/**
 * Entrada ou saida validada no estacionamento (4.6).
 *
 * Fica gravado tambem quando a credencial e recusada: o registro do que foi
 * barrado e tao operacional quanto o do que entrou.
 */
@Entity(tableName = "parking_accesses", indices = [Index("eventId"), Index("sectorId")])
data class ParkingAccessEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val sectorId: String?,
    val sectorName: String?,
    val credential: String?,
    val plate: String?,
    val vehicleId: String?,
    /** IN ou OUT. */
    val direction: String,
    val authorized: Boolean,
    val reason: String?,
    val occurredAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
)
