package br.com.soe.campo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ------------------------------------------------------------------ Login

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val scope: String = "mobile",
    val installationId: String,
    val model: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
)

@Serializable
data class LoginResponse(
    val token: String,
    val expiresIn: Long,
    val user: RemoteUser,
    val profile: RemoteProfile? = null,
)

@Serializable
data class RemoteUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val organizationId: String,
    val organizationName: String? = null,
)

@Serializable
data class RemoteProfile(
    val personId: String,
    val eventId: String,
    val eventName: String,
    val eventStatus: String,
    val area: RemoteNamed? = null,
    val jobRole: RemoteNamed? = null,
    val shift: RemoteShiftRef? = null,
    val badgeCode: String? = null,
)

@Serializable
data class RemoteNamed(val id: String, val name: String)

@Serializable
data class RemoteShiftRef(
    val id: String,
    val name: String,
    val startsAt: String? = null,
    val endsAt: String? = null,
)

// -------------------------------------------------------------- Bootstrap

@Serializable
data class BootstrapResponse(
    val serverTime: String,
    val user: RemoteUser,
    val assignments: List<RemoteAssignment> = emptyList(),
    val events: List<RemoteEventSummary> = emptyList(),
)

@Serializable
data class RemoteAssignment(
    val personId: String,
    val badgeCode: String? = null,
    val status: String,
    val area: RemoteNamed? = null,
    val jobRole: RemoteNamed? = null,
    val shift: RemoteShiftRef? = null,
    val event: RemoteEventSummary,
)

@Serializable
data class RemoteEventSummary(
    val id: String,
    val name: String,
    val code: String,
    val status: String,
    val venueName: String? = null,
    val city: String? = null,
    val eventStart: String? = null,
    val eventEnd: String? = null,
)

// ------------------------------------------------------------------- Pull

@Serializable
data class PullResponse(
    val serverTime: String,
    val since: String,
    val event: RemoteEvent? = null,
    val areas: List<RemoteArea> = emptyList(),
    val jobRoles: List<RemoteJobRole> = emptyList(),
    val shifts: List<RemoteShift> = emptyList(),
    val people: List<RemotePerson> = emptyList(),
    val checklistTemplates: List<RemoteTemplate> = emptyList(),
    val checklistTemplateItems: List<RemoteTemplateItem> = emptyList(),
    val checklistExecutions: List<RemoteExecution> = emptyList(),
    val checklistAnswers: List<RemoteAnswer> = emptyList(),
    val incidents: List<RemoteIncident> = emptyList(),
    val alerts: List<RemoteAlert> = emptyList(),
    val alertReceipts: List<RemoteAlertReceipt> = emptyList(),
    // Logistica & Mobilidade
    val vehicles: List<RemoteVehicle> = emptyList(),
    val trips: List<RemoteTrip> = emptyList(),
    val tripStops: List<RemoteTripStop> = emptyList(),
    val tripPassengers: List<RemoteTripPassenger> = emptyList(),
    val parkingSectors: List<RemoteParkingSector> = emptyList(),
    // Direitos Humanos & Sustentabilidade
    val sustainabilityTasks: List<RemoteSustainabilityTask> = emptyList(),
)

@Serializable
data class RemoteEvent(
    val id: String,
    val name: String,
    val code: String,
    val status: String,
    val venueName: String? = null,
    val city: String? = null,
    val eventStart: String? = null,
    val eventEnd: String? = null,
    val updatedAt: String,
)

@Serializable
data class RemoteArea(
    val id: String,
    val eventId: String,
    val parentId: String? = null,
    val name: String,
    val code: String,
    val color: String,
    val level: Int,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteJobRole(
    val id: String,
    val eventId: String,
    val areaId: String? = null,
    val name: String,
    val hierarchy: Int,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteShift(
    val id: String,
    val eventId: String,
    val name: String,
    val phase: String,
    val startsAt: String,
    val endsAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemotePerson(
    val id: String,
    val eventId: String,
    val userId: String? = null,
    val areaId: String? = null,
    val jobRoleId: String? = null,
    val shiftId: String? = null,
    val supervisorId: String? = null,
    val name: String,
    val badgeCode: String? = null,
    val status: String,
    val checkInAt: String? = null,
    val checkOutAt: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteTemplate(
    val id: String,
    val eventId: String,
    val areaId: String? = null,
    val shiftId: String? = null,
    val name: String,
    val description: String? = null,
    val phase: String,
    val requiresPhoto: Boolean,
    val requiresGeo: Boolean,
    val active: Boolean,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteTemplateItem(
    val id: String,
    val templateId: String,
    val label: String,
    val helpText: String? = null,
    val type: String,
    val required: Boolean,
    val requiresPhoto: Boolean,
    val sortOrder: Int,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteExecution(
    val id: String,
    val eventId: String,
    val templateId: String,
    val areaId: String? = null,
    val shiftId: String? = null,
    val status: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val score: Int? = null,
    val notes: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteAnswer(
    val id: String,
    val executionId: String,
    val itemId: String,
    val valueBool: Boolean? = null,
    val valueText: String? = null,
    val conform: Boolean? = null,
    val notes: String? = null,
    val answeredAt: String? = null,
)

@Serializable
data class RemoteIncident(
    val id: String,
    val eventId: String,
    val areaId: String? = null,
    val code: String,
    val title: String,
    val description: String? = null,
    val type: String,
    val severity: String,
    val status: String,
    val locationDescription: String? = null,
    val occurredAt: String,
    val resolvedAt: String? = null,
    val resolution: String? = null,
    val peopleAffected: Int? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteAlert(
    val id: String,
    val eventId: String,
    val areaId: String? = null,
    val title: String,
    val message: String,
    val level: String,
    val scope: String,
    val requiresAck: Boolean,
    val sentAt: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteAlertReceipt(
    val id: String,
    val alertId: String,
    /** Pessoas sem app que este destinatario precisa informar pessoalmente. */
    val relayCount: Int = 0,
    val readAt: String? = null,
    val acknowledgedAt: String? = null,
)

// -------------------------------------- Pull: Logistica & Mobilidade

@Serializable
data class RemoteVehicle(
    val id: String,
    val eventId: String,
    val code: String,
    val type: String,
    val plate: String? = null,
    val model: String? = null,
    val color: String? = null,
    val capacity: Int? = null,
    val accessible: Boolean = false,
    val armored: Boolean = false,
    val companyName: String? = null,
    val status: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteTrip(
    val id: String,
    val eventId: String,
    val code: String,
    val service: String,
    val vehicleId: String? = null,
    val driverPersonId: String? = null,
    val routeName: String? = null,
    val originName: String? = null,
    val destinationName: String? = null,
    val scheduledStart: String? = null,
    val scheduledEnd: String? = null,
    val status: String,
    val checkedInAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val odometerStart: Int? = null,
    val odometerEnd: Int? = null,
    val fuelStart: Int? = null,
    val fuelEnd: Int? = null,
    val capacity: Int? = null,
    val aggregateBoarded: Int? = null,
    val vehicleReturned: Boolean = false,
    val notes: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteTripStop(
    val id: String,
    val tripId: String,
    val sortOrder: Int,
    val name: String,
    val kind: String = "STOP",
    val scheduledAt: String? = null,
    val arrivedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val instructions: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteTripPassenger(
    val id: String,
    val tripId: String,
    val personId: String? = null,
    val name: String,
    val badgeCode: String? = null,
    val boardingStopName: String? = null,
    val status: String,
    val boardedAt: String? = null,
    val droppedAt: String? = null,
    val boardedStopName: String? = null,
    val notes: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class RemoteParkingSector(
    val id: String,
    val eventId: String,
    val name: String,
    val capacity: Int? = null,
    val occupied: Int? = null,
    val blocked: Boolean = false,
    val notes: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
)

// --------------------- Pull: Direitos Humanos & Sustentabilidade

@Serializable
data class RemoteSustainabilityTask(
    val id: String,
    val eventId: String,
    val theme: String,
    val subtheme: String? = null,
    val title: String,
    val description: String? = null,
    val phase: String,
    val criterion: String,
    val priority: String,
    val status: String,
    val dueAt: String? = null,
    val responsibleName: String? = null,
    val areaId: String? = null,
    val locationName: String? = null,
    val recurring: Boolean = false,
    val requiresEvidence: Boolean = false,
    val updatedAt: String,
    val deletedAt: String? = null,
)

// ------------------------------------------------------------------- Push

@Serializable
data class PushRequest(
    val eventId: String,
    val installationId: String? = null,
    val incidents: List<PushIncident> = emptyList(),
    val checklistExecutions: List<PushExecution> = emptyList(),
    val attachments: List<PushAttachment> = emptyList(),
    val alertReceipts: List<PushReceipt> = emptyList(),
    val personStatus: List<PushPersonStatus> = emptyList(),
    // Logistica & Mobilidade
    val trips: List<PushTrip> = emptyList(),
    val tripStops: List<PushTripStop> = emptyList(),
    val tripPassengers: List<PushTripPassenger> = emptyList(),
    val vehicleChecks: List<PushVehicleCheck> = emptyList(),
    val parkingAccesses: List<PushParkingAccess> = emptyList(),
    // Direitos Humanos & Sustentabilidade
    val sustainabilityUpdates: List<PushSustainabilityUpdate> = emptyList(),
)

@Serializable
data class PushTrip(
    val id: String,
    val status: String,
    val checkedInAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val odometerStart: Int? = null,
    val odometerEnd: Int? = null,
    val fuelStart: Int? = null,
    val fuelEnd: Int? = null,
    val aggregateBoarded: Int? = null,
    val vehicleReturned: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val clientCreatedAt: String? = null,
)

@Serializable
data class PushTripStop(
    val id: String,
    val tripId: String,
    val sortOrder: Int,
    val name: String,
    val kind: String,
    val scheduledAt: String? = null,
    val arrivedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val clientCreatedAt: String? = null,
)

@Serializable
data class PushTripPassenger(
    val id: String,
    val tripId: String,
    val personId: String? = null,
    val name: String,
    val status: String,
    val boardedAt: String? = null,
    val droppedAt: String? = null,
    val boardedStopName: String? = null,
    val notes: String? = null,
    val clientCreatedAt: String? = null,
)

@Serializable
data class PushVehicleCheck(
    val id: String,
    val tripId: String? = null,
    val vehicleId: String? = null,
    val kind: String,
    val status: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val odometer: Int? = null,
    val fuelLevel: Int? = null,
    val blocked: Boolean = false,
    val overrideReason: String? = null,
    val overrideAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val clientCreatedAt: String? = null,
    val answers: List<PushVehicleCheckAnswer> = emptyList(),
)

@Serializable
data class PushVehicleCheckAnswer(
    val id: String,
    val itemKey: String,
    val answer: String,
    val critical: Boolean = false,
    val notes: String? = null,
    val answeredAt: String? = null,
)

@Serializable
data class PushParkingAccess(
    val id: String,
    val sectorId: String? = null,
    val sectorName: String? = null,
    val credential: String? = null,
    val plate: String? = null,
    val direction: String,
    val authorized: Boolean,
    val reason: String? = null,
    val occurredAt: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val clientCreatedAt: String? = null,
)

@Serializable
data class PushSustainabilityUpdate(
    val id: String,
    val taskId: String,
    val status: String,
    val comment: String? = null,
    val justification: String? = null,
    val answeredAt: String,
    val authorName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val incidentId: String? = null,
    val clientCreatedAt: String? = null,
)

@Serializable
data class PushIncident(
    val id: String,
    val title: String,
    val description: String? = null,
    val type: String,
    val severity: String,
    val status: String,
    val areaId: String? = null,
    val locationDescription: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val occurredAt: String,
    val clientCreatedAt: String? = null,
    val peopleAffected: Int? = null,
    val resolution: String? = null,
)

@Serializable
data class PushExecution(
    val id: String,
    val templateId: String,
    val areaId: String? = null,
    val shiftId: String? = null,
    val status: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val score: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val clientCreatedAt: String? = null,
    val answers: List<PushAnswer> = emptyList(),
)

@Serializable
data class PushAnswer(
    val id: String,
    val itemId: String,
    val valueBool: Boolean? = null,
    val valueText: String? = null,
    val valueNumber: Double? = null,
    val conform: Boolean? = null,
    val notes: String? = null,
    val answeredAt: String? = null,
)

@Serializable
data class PushAttachment(
    val id: String,
    val entityType: String,
    val entityId: String,
    val fileName: String,
    val mimeType: String,
    val base64: String,
    val capturedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class PushReceipt(
    val alertId: String,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val acknowledgedAt: String? = null,
)

@Serializable
data class PushPersonStatus(
    val id: String,
    val status: String,
    val checkInAt: String? = null,
    val checkOutAt: String? = null,
)

@Serializable
data class PushResponse(
    val serverTime: String,
    val accepted: Map<String, List<String>> = emptyMap(),
    val rejected: List<PushRejection> = emptyList(),
)

@Serializable
data class PushRejection(
    val collection: String,
    val id: String,
    val reason: String,
)

@Serializable
data class ApiError(
    @SerialName("error") val message: String? = null,
)
