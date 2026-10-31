package br.com.soe.campo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MobilityDao {

    // ------------------------------------------------------------ Veiculos

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicles(rows: List<VehicleEntity>)

    @Query("SELECT * FROM vehicles WHERE eventId = :eventId AND deleted = 0 ORDER BY code")
    fun observeVehicles(eventId: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun vehicle(id: String): VehicleEntity?

    // -------------------------------------------------------------- Viagens

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrips(rows: List<TripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(row: TripEntity)

    @Query(
        """
        SELECT * FROM trips
        WHERE eventId = :eventId AND deleted = 0
        ORDER BY
            CASE status
                WHEN 'IN_PROGRESS' THEN 0
                WHEN 'CHECKED_IN' THEN 1
                WHEN 'SCHEDULED' THEN 2
                ELSE 3
            END,
            COALESCE(scheduledStartMs, updatedAtMs)
        """
    )
    fun observeTrips(eventId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: String): TripEntity?

    @Query(
        """
        SELECT COUNT(*) FROM trips
        WHERE eventId = :eventId AND deleted = 0 AND status IN ('SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS')
        """
    )
    fun countOpenTrips(eventId: String): Flow<Int>

    @Query("SELECT * FROM trips WHERE eventId = :eventId AND deleted = 0")
    suspend fun allTrips(eventId: String): List<TripEntity>

    @Query("SELECT * FROM trips WHERE pendingSync = 1")
    suspend fun pendingTrips(): List<TripEntity>

    @Query("UPDATE trips SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingTrips(ids: List<String>)

    // -------------------------------------------------------------- Paradas

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStops(rows: List<TripStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStop(row: TripStopEntity)

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId AND deleted = 0 ORDER BY sortOrder")
    fun observeStops(tripId: String): Flow<List<TripStopEntity>>

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId AND deleted = 0 ORDER BY sortOrder")
    suspend fun stops(tripId: String): List<TripStopEntity>

    @Query("SELECT * FROM trip_stops WHERE id = :id")
    suspend fun stop(id: String): TripStopEntity?

    @Query("SELECT * FROM trip_stops WHERE pendingSync = 1")
    suspend fun pendingStops(): List<TripStopEntity>

    @Query("UPDATE trip_stops SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingStops(ids: List<String>)

    // ---------------------------------------------------------- Passageiros

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPassengers(rows: List<TripPassengerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPassenger(row: TripPassengerEntity)

    @Query("SELECT * FROM trip_passengers WHERE tripId = :tripId AND deleted = 0 ORDER BY name")
    fun observePassengers(tripId: String): Flow<List<TripPassengerEntity>>

    @Query("SELECT * FROM trip_passengers WHERE id = :id")
    suspend fun passenger(id: String): TripPassengerEntity?

    @Query("SELECT COUNT(*) FROM trip_passengers WHERE tripId = :tripId AND deleted = 0 AND status = 'BOARDED'")
    suspend fun boardedCount(tripId: String): Int

    @Query("SELECT * FROM trip_passengers WHERE pendingSync = 1")
    suspend fun pendingPassengers(): List<TripPassengerEntity>

    @Query("UPDATE trip_passengers SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingPassengers(ids: List<String>)

    // ------------------------------------------------- Checklist do veiculo

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheck(row: VehicleCheckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckAnswers(rows: List<VehicleCheckAnswerEntity>)

    @Query("SELECT * FROM vehicle_checks WHERE id = :id")
    fun observeCheck(id: String): Flow<VehicleCheckEntity?>

    @Query("SELECT * FROM vehicle_checks WHERE id = :id")
    suspend fun check(id: String): VehicleCheckEntity?

    @Query(
        """
        SELECT * FROM vehicle_checks
        WHERE tripId = :tripId AND kind = :kind
        ORDER BY COALESCE(completedAtMs, startedAtMs, updatedAtMs) DESC
        LIMIT 1
        """
    )
    suspend fun latestCheck(tripId: String, kind: String): VehicleCheckEntity?

    @Query(
        """
        SELECT * FROM vehicle_checks
        WHERE tripId = :tripId AND kind = :kind
        ORDER BY COALESCE(completedAtMs, startedAtMs, updatedAtMs) DESC
        LIMIT 1
        """
    )
    fun observeLatestCheck(tripId: String, kind: String): Flow<VehicleCheckEntity?>

    @Query("SELECT * FROM vehicle_check_answers WHERE checkId = :checkId")
    fun observeCheckAnswers(checkId: String): Flow<List<VehicleCheckAnswerEntity>>

    @Query("SELECT * FROM vehicle_check_answers WHERE checkId = :checkId")
    suspend fun checkAnswers(checkId: String): List<VehicleCheckAnswerEntity>

    @Query("DELETE FROM vehicle_check_answers WHERE checkId = :checkId")
    suspend fun deleteCheckAnswers(checkId: String)

    @Query("SELECT * FROM vehicle_checks WHERE pendingSync = 1")
    suspend fun pendingChecks(): List<VehicleCheckEntity>

    @Query("UPDATE vehicle_checks SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingChecks(ids: List<String>)

    @Query("UPDATE vehicle_check_answers SET pendingSync = 0 WHERE checkId IN (:ids)")
    suspend fun clearPendingCheckAnswers(ids: List<String>)

    /** As respostas substituem as anteriores: o checklist e sempre reescrito inteiro. */
    @Transaction
    suspend fun saveCheck(check: VehicleCheckEntity, answers: List<VehicleCheckAnswerEntity>) {
        upsertCheck(check)
        deleteCheckAnswers(check.id)
        upsertCheckAnswers(answers)
    }

    // -------------------------------------------------------- Estacionamento

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSectors(rows: List<ParkingSectorEntity>)

    @Query("SELECT * FROM parking_sectors WHERE eventId = :eventId AND deleted = 0 ORDER BY name")
    fun observeSectors(eventId: String): Flow<List<ParkingSectorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccess(row: ParkingAccessEntity)

    @Query(
        """
        SELECT * FROM parking_accesses
        WHERE eventId = :eventId
        ORDER BY occurredAtMs DESC
        LIMIT 60
        """
    )
    fun observeAccesses(eventId: String): Flow<List<ParkingAccessEntity>>

    @Query("SELECT * FROM parking_accesses WHERE pendingSync = 1")
    suspend fun pendingAccesses(): List<ParkingAccessEntity>

    @Query("UPDATE parking_accesses SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPendingAccesses(ids: List<String>)

    // ------------------------------------------------------------ Pendencias

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM trips WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM trip_stops WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM trip_passengers WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM vehicle_checks WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM parking_accesses WHERE pendingSync = 1)
        """
    )
    fun countPending(): Flow<Int>
}
