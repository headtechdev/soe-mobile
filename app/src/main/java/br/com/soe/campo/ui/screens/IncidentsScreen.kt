package br.com.soe.campo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.Time
import br.com.soe.campo.data.local.IncidentEntity
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun IncidentsScreen(
    viewModel: SoeViewModel,
    onReportIncident: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as SoeApplication).container.fieldRepository
    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    val incidents by remember(eventId) {
        eventId?.let { repository.observeIncidents(it) } ?: MutableStateFlow(emptyList<IncidentEntity>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            SoeTopBar(
                title = "Ocorrencias",
                subtitle = "${incidents.size} registro(s) neste dispositivo",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onReportIncident) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nova")
            }
        },
    ) { padding ->
        if (incidents.isEmpty()) {
            EmptyMessage(
                "Nenhuma ocorrencia registrada neste evento.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(incidents, key = { it.id }) { incident ->
                IncidentCard(incident)
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: IncidentEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    // Enquanto nao sincroniza o codigo definitivo nao existe,
                    // entao mostramos "na fila" em vez de um numero falso.
                    if (incident.pendingSync) "na fila" else incident.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (incident.pendingSync) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Aguardando sincronizacao",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.weight(1f))
                SoePill(
                    labelOf(SEVERITIES, incident.severity),
                    severityColor(incident.severity),
                )
            }

            Text(incident.title, style = MaterialTheme.typography.titleMedium)

            incident.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoePill(
                    labelOf(INCIDENT_STATUS, incident.status),
                    statusColor(incident.status),
                )
                Text(
                    "${labelOf(INCIDENT_TYPES, incident.type)} · ${Time.relative(incident.occurredAtMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
