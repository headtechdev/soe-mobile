package br.com.soe.campo.data

import br.com.soe.campo.data.local.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Logistica & Mobilidade em campo (secao 4 do modulo).
 *
 * Mesma regra do resto do app: tudo cai primeiro no SQLite com
 * `pendingSync = true` e sobe no proximo sincronismo. A missao continua
 * andando com o aparelho sem rede — e e assim que ela costuma rodar, dentro
 * de um veiculo em deslocamento.
 */
class MobilityRepository(
    private val db: SoeDatabase,
    private val attachments: FieldRepository,
) {

    private val dao get() = db.mobilityDao()

    // ------------------------------------------------------- Observadores

    fun observeTrips(eventId: String): Flow<List<TripEntity>> = dao.observeTrips(eventId)

    fun observeTrip(tripId: String): Flow<TripEntity?> = dao.observeTrip(tripId)

    fun observeStops(tripId: String): Flow<List<TripStopEntity>> = dao.observeStops(tripId)

    fun observePassengers(tripId: String): Flow<List<TripPassengerEntity>> =
        dao.observePassengers(tripId)

    fun observeVehicles(eventId: String): Flow<List<VehicleEntity>> = dao.observeVehicles(eventId)

    fun observeSectors(eventId: String): Flow<List<ParkingSectorEntity>> =
        dao.observeSectors(eventId)

    fun observeAccesses(eventId: String): Flow<List<ParkingAccessEntity>> =
        dao.observeAccesses(eventId)

    fun observeCheck(checkId: String): Flow<VehicleCheckEntity?> = dao.observeCheck(checkId)

    fun observeCheckAnswers(checkId: String): Flow<List<VehicleCheckAnswerEntity>> =
        dao.observeCheckAnswers(checkId)

    fun observeLatestCheck(tripId: String, kind: String): Flow<VehicleCheckEntity?> =
        dao.observeLatestCheck(tripId, kind)

    fun observeOpenTrips(eventId: String): Flow<Int> = dao.countOpenTrips(eventId)

    fun observePending(): Flow<Int> = dao.countPending()

    suspend fun trip(tripId: String): TripEntity? = dao.trip(tripId)

    suspend fun vehicle(id: String): VehicleEntity? = dao.vehicle(id)

    suspend fun latestCheck(tripId: String, kind: String): VehicleCheckEntity? =
        dao.latestCheck(tripId, kind)

    // -------------------------------------------- 4.2 Check-in do motorista

    /**
     * Confirma identidade, veiculo e circuito. A foto e a localizacao sao
     * opcionais — a politica do evento decide se exige, e o aparelho pode nao
     * ter permissao concedida.
     */
    suspend fun checkInDriver(
        tripId: String,
        latitude: Double?,
        longitude: Double?,
        photoPath: String?,
    ) {
        val trip = dao.trip(tripId) ?: return
        val now = System.currentTimeMillis()

        dao.upsertTrip(
            trip.copy(
                status = "CHECKED_IN",
                checkedInAtMs = now,
                latitude = latitude ?: trip.latitude,
                longitude = longitude ?: trip.longitude,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )

        if (photoPath != null) {
            attachments.attachPhoto(
                eventId = trip.eventId,
                entityType = "TRIP_CHECK_IN",
                entityId = tripId,
                localPath = photoPath,
                latitude = latitude,
                longitude = longitude,
            )
        }
    }

    // --------------------------------------------- 4.3 / 4.8 Checklist

    /** Abre (ou reabre) o checklist do veiculo para esta missao. */
    suspend fun startVehicleCheck(
        eventId: String,
        tripId: String?,
        vehicleId: String?,
        kind: String,
        latitude: Double?,
        longitude: Double?,
    ): String {
        val existing = tripId?.let { dao.latestCheck(it, kind) }
        if (existing != null && existing.status != "COMPLETED") return existing.id

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        dao.upsertCheck(
            VehicleCheckEntity(
                id = id,
                eventId = eventId,
                tripId = tripId,
                vehicleId = vehicleId,
                kind = kind,
                status = "IN_PROGRESS",
                startedAtMs = now,
                completedAtMs = null,
                odometer = null,
                fuelLevel = null,
                blocked = false,
                overrideReason = null,
                overrideAtMs = null,
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
     * Grava o checklist.
     *
     * O bloqueio nasce aqui, nao na tela: qualquer item critico marcado como
     * nao conforme trava a partida ate que haja excecao autorizada. A conta e
     * do repositorio para valer igual em qualquer caminho que salve o
     * checklist.
     */
    suspend fun saveVehicleCheck(
        checkId: String,
        answers: Map<String, VehicleCheckAnswerInput>,
        odometer: Int?,
        fuelLevel: Int?,
        notes: String?,
        finish: Boolean,
    ) {
        val check = dao.check(checkId) ?: return
        val now = System.currentTimeMillis()

        val rows = answers.map { (key, input) ->
            val critical = VehicleCheckCatalog.item(check.kind, key)?.critical == true
            VehicleCheckAnswerEntity(
                id = UUID.randomUUID().toString(),
                checkId = checkId,
                itemKey = key,
                answer = input.answer,
                critical = critical,
                notes = input.notes,
                answeredAtMs = now,
                pendingSync = true,
            )
        }

        val blocked = rows.any {
            it.critical && it.answer == VehicleCheckCatalog.NON_CONFORM
        }

        dao.saveCheck(
            check.copy(
                status = if (finish) "COMPLETED" else "IN_PROGRESS",
                completedAtMs = if (finish) now else null,
                odometer = odometer,
                fuelLevel = fuelLevel,
                blocked = blocked,
                // A excecao vale para o bloqueio que existia quando foi dada:
                // se o checklist voltou a ficar limpo, nao ha o que liberar.
                overrideReason = if (blocked) check.overrideReason else null,
                overrideAtMs = if (blocked) check.overrideAtMs else null,
                notes = notes,
                updatedAtMs = now,
                pendingSync = true,
            ),
            rows,
        )
    }

    /** Excecao autorizada: libera a partida com bloqueio critico em aberto. */
    suspend fun authorizeCheckOverride(checkId: String, reason: String, authorizedBy: String?) {
        val check = dao.check(checkId) ?: return
        val now = System.currentTimeMillis()
        val trail = listOfNotNull(reason.trim().ifBlank { null }, authorizedBy?.trim()?.ifBlank { null })
            .joinToString(" · Autorizado por ")

        dao.upsertCheck(
            check.copy(
                overrideReason = trail,
                overrideAtMs = now,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    suspend fun attachCheckPhoto(checkId: String, localPath: String) {
        val check = dao.check(checkId) ?: return
        attachments.attachPhoto(
            eventId = check.eventId,
            entityType = "VEHICLE_CHECK",
            entityId = checkId,
            localPath = localPath,
            latitude = check.latitude,
            longitude = check.longitude,
        )
    }

    // ---------------------------------------- 4.4 Embarque e desembarque

    suspend fun setPassengerStatus(
        passengerId: String,
        status: String,
        stopName: String?,
        notes: String? = null,
    ) {
        val passenger = dao.passenger(passengerId) ?: return
        val now = System.currentTimeMillis()

        dao.upsertPassenger(
            passenger.copy(
                status = status,
                boardedAtMs = if (status == "BOARDED") now else passenger.boardedAtMs,
                droppedAtMs = if (status == "DROPPED") now else passenger.droppedAtMs,
                boardedStopName = if (status == "BOARDED") stopName else passenger.boardedStopName,
                notes = notes ?: passenger.notes,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    /** Passageiro que apareceu sem estar na lista (4.4: "passageiro adicional"). */
    suspend fun addExtraPassenger(tripId: String, name: String, stopName: String?, notes: String?) {
        val now = System.currentTimeMillis()
        dao.upsertPassenger(
            TripPassengerEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                personId = null,
                name = name.trim(),
                badgeCode = null,
                boardingStopName = stopName,
                status = "EXTRA",
                boardedAtMs = now,
                droppedAtMs = null,
                boardedStopName = stopName,
                notes = notes,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    /** Contagem agregada, para servico sem lista nominal (RB-09). */
    suspend fun setAggregateBoarded(tripId: String, count: Int) {
        val trip = dao.trip(tripId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertTrip(
            trip.copy(
                aggregateBoarded = count.coerceAtLeast(0),
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    /**
     * Lotacao atual: nominal + agregada. A tela usa para travar embarque
     * acima da capacidade cadastrada (RB-01).
     */
    suspend fun occupancy(tripId: String): Int {
        val trip = dao.trip(tripId) ?: return 0
        return dao.boardedCount(tripId) + (trip.aggregateBoarded ?: 0)
    }

    // -------------------------------------------- 4.5 Execucao da viagem

    suspend fun startTrip(tripId: String, latitude: Double?, longitude: Double?) {
        val trip = dao.trip(tripId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertTrip(
            trip.copy(
                status = "IN_PROGRESS",
                startedAtMs = trip.startedAtMs ?: now,
                latitude = latitude ?: trip.latitude,
                longitude = longitude ?: trip.longitude,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    suspend fun registerStopArrival(stopId: String, latitude: Double?, longitude: Double?) {
        val stop = dao.stop(stopId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertStop(
            stop.copy(
                arrivedAtMs = now,
                latitude = latitude ?: stop.latitude,
                longitude = longitude ?: stop.longitude,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    /** Parada nao programada registrada durante o percurso. */
    suspend fun addUnplannedStop(tripId: String, name: String, latitude: Double?, longitude: Double?) {
        val now = System.currentTimeMillis()
        val order = (dao.stops(tripId).maxOfOrNull { it.sortOrder } ?: 0) + 1
        dao.upsertStop(
            TripStopEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                sortOrder = order,
                name = name.trim(),
                kind = "UNPLANNED",
                scheduledAtMs = null,
                arrivedAtMs = now,
                latitude = latitude,
                longitude = longitude,
                instructions = null,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    // ------------------------------------------------------ 4.8 Check-out

    /**
     * Encerra a missao. Quilometragem e combustivel finais sao do check-out;
     * a devolucao do veiculo e confirmada explicitamente para liberar o
     * recurso para a proxima escala.
     */
    suspend fun checkOutTrip(
        tripId: String,
        odometerEnd: Int?,
        fuelEnd: Int?,
        vehicleReturned: Boolean,
        notes: String?,
        latitude: Double?,
        longitude: Double?,
    ) {
        val trip = dao.trip(tripId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertTrip(
            trip.copy(
                status = "COMPLETED",
                finishedAtMs = now,
                odometerEnd = odometerEnd,
                fuelEnd = fuelEnd,
                vehicleReturned = vehicleReturned,
                notes = notes ?: trip.notes,
                latitude = latitude ?: trip.latitude,
                longitude = longitude ?: trip.longitude,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    suspend fun setOdometerStart(tripId: String, odometer: Int?, fuel: Int?) {
        val trip = dao.trip(tripId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertTrip(
            trip.copy(
                odometerStart = odometer ?: trip.odometerStart,
                fuelStart = fuel ?: trip.fuelStart,
                updatedAtMs = now,
                pendingSync = true,
            ),
        )
    }

    // ------------------------------------- 4.6 Estacionamento e trafego

    /**
     * Valida credencial/placa e registra a entrada ou a saida.
     *
     * A recusa tambem vira registro: saber o que foi barrado, quando e por
     * que e parte do controle de acesso, nao ruido.
     */
    suspend fun registerParkingAccess(
        eventId: String,
        sectorId: String?,
        sectorName: String?,
        credential: String?,
        plate: String?,
        direction: String,
        authorized: Boolean,
        reason: String?,
        notes: String?,
        latitude: Double?,
        longitude: Double?,
        photoPath: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        dao.upsertAccess(
            ParkingAccessEntity(
                id = id,
                eventId = eventId,
                sectorId = sectorId,
                sectorName = sectorName,
                credential = credential?.trim()?.ifBlank { null },
                plate = plate?.trim()?.uppercase()?.ifBlank { null },
                vehicleId = null,
                direction = direction,
                authorized = authorized,
                reason = reason?.trim()?.ifBlank { null },
                occurredAtMs = now,
                latitude = latitude,
                longitude = longitude,
                notes = notes?.trim()?.ifBlank { null },
                updatedAtMs = now,
                pendingSync = true,
            ),
        )

        if (photoPath != null) {
            attachments.attachPhoto(
                eventId = eventId,
                entityType = "PARKING_ACCESS",
                entityId = id,
                localPath = photoPath,
                latitude = latitude,
                longitude = longitude,
            )
        }

        return id
    }
}

data class VehicleCheckAnswerInput(
    val answer: String,
    val notes: String? = null,
)
