package br.com.soe.campo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.data.Time
import br.com.soe.campo.ui.SoeViewModel

/**
 * Tela inicial: as tres acoes de campo em destaque (reportar incidente,
 * checklists, alertas) e o estado do sincronismo sempre visivel.
 */
@Composable
fun HomeScreen(
    viewModel: SoeViewModel,
    onReportIncident: () -> Unit,
    onOpenIncidents: () -> Unit,
    onOpenChecklists: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unread by viewModel.unreadAlerts.collectAsStateWithLifecycle()
    val pending by viewModel.pendingUploads.collectAsStateWithLifecycle()
    val pendingPhotos by viewModel.pendingPhotos.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSyncAt.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.lastSyncMessage) {
        val message = state.error ?: state.lastSyncMessage
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SoeTopBar(
                title = session?.eventName ?: "SOE Campo",
                subtitle = listOfNotNull(session?.areaName, session?.shiftName)
                    .joinToString(" · ")
                    .ifBlank { session?.userName },
                actions = {
                    IconButton(onClick = { viewModel.syncNow() }, enabled = !state.syncing) {
                        if (state.syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sincronizar")
                        }
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Perfil")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Estado do sincronismo — o operador precisa saber se o que ele
            // registrou ja subiu.
            SyncStatusCard(
                lastSyncMs = lastSync,
                pendingRecords = pending,
                pendingPhotos = pendingPhotos,
                syncing = state.syncing,
                onSync = { viewModel.syncNow() },
            )

            BigActionCard(
                title = "Reportar incidente",
                subtitle = "Registro imediato, com foto e local. Funciona sem rede.",
                icon = Icons.Default.ReportProblem,
                color = MaterialTheme.colorScheme.error,
                onClick = onReportIncident,
            )

            BigActionCard(
                title = "Checklists",
                subtitle = "Executar as verificacoes do seu turno e area.",
                icon = Icons.Default.ChecklistRtl,
                color = MaterialTheme.colorScheme.primary,
                onClick = onOpenChecklists,
            )

            BigActionCard(
                title = "Alertas",
                subtitle = if (unread > 0) "$unread alerta(s) nao lido(s)" else "Comunicados do comando",
                icon = Icons.Default.NotificationsActive,
                color = if (unread > 0) Color(0xFFD97706) else MaterialTheme.colorScheme.secondary,
                badge = unread,
                onClick = onOpenAlerts,
            )

            OutlinedCard(
                onClick = onOpenIncidents,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("Minhas ocorrencias", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Acompanhar o que foi registrado neste evento",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    lastSyncMs: Long?,
    pendingRecords: Int,
    pendingPhotos: Int,
    syncing: Boolean,
    onSync: () -> Unit,
) {
    val totalPending = pendingRecords + pendingPhotos
    val container = if (totalPending > 0) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (totalPending > 0) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                contentDescription = null,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (totalPending > 0) {
                        "$totalPending item(ns) na fila local"
                    } else {
                        "Tudo sincronizado"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Ultimo sincronismo: ${Time.relative(lastSyncMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSync, enabled = !syncing) {
                Text(if (syncing) "Enviando..." else "Sincronizar")
            }
        }
    }
}

@Composable
private fun BigActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    badge: Int = 0,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                color = color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(28.dp),
                )
            }

            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (badge > 0) {
                Badge { Text(badge.toString()) }
            }
        }
    }
}
