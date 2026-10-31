package br.com.soe.campo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.data.Time
import br.com.soe.campo.ui.SoeViewModel

/**
 * Fila local e conflitos.
 *
 * Os dois modulos exigem que o app "indique claramente registros pendentes
 * de sincronizacao ou conflito" e nao perca dado em silencio. Pendencia ja
 * aparece como contador nas telas; o que faltava era o conflito — o registro
 * que o servidor recusou. Ele fica aqui, com o motivo, ate alguem resolver.
 */
@Composable
fun SyncQueueScreen(viewModel: SoeViewModel, onBack: () -> Unit) {
    val pending by viewModel.pendingUploads.collectAsStateWithLifecycle()
    val photos by viewModel.pendingPhotos.collectAsStateWithLifecycle()
    val conflicts by viewModel.syncConflicts.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SoeTopBar(
                title = "Fila e conflitos",
                subtitle = "Ultimo sincronismo: ${Time.relative(lastSync)}",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$pending registro(s) na fila local", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$photos evidencia(s) aguardando upload",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.syncNow() },
                    enabled = !state.syncing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.syncing) "Sincronizando..." else "Sincronizar agora") }
            }

            item {
                Text("Conflitos", style = MaterialTheme.typography.titleMedium)
            }

            if (conflicts.isEmpty()) {
                item {
                    Text(
                        "Nenhum registro recusado pelo servidor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(conflicts, key = { it.id }) { conflict ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            collectionLabel(conflict.collection),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(conflict.reason, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Recusado em ${Time.full(conflict.rejectedAtMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { viewModel.resolveConflict(conflict.id) }) {
                            Text("Marcar como resolvido")
                        }
                    }
                }
            }
        }
    }
}

private fun collectionLabel(collection: String): String = when (collection) {
    "incidents" -> "Ocorrencia"
    "checklistExecutions" -> "Checklist"
    "attachments" -> "Evidencia"
    "alertReceipts" -> "Ciencia de alerta"
    "personStatus" -> "Presenca"
    "trips" -> "Missao de logistica"
    "tripStops" -> "Parada"
    "tripPassengers" -> "Passageiro"
    "vehicleChecks" -> "Checklist de veiculo"
    "parkingAccesses" -> "Acesso de estacionamento"
    "sustainabilityUpdates" -> "Resposta de DH e Sustentabilidade"
    else -> collection
}
