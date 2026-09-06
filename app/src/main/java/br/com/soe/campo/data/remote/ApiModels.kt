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
