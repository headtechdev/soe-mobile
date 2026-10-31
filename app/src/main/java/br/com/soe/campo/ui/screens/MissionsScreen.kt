package br.com.soe.campo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.Time
import br.com.soe.campo.data.local.TripEntity
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Missoes de logistica atribuidas ao operador (modulo 11, secao 4).
 *
 * A ordem vem do banco: o que esta em viagem primeiro, depois o que tem
 * check-in feito, depois o programado. Quem abre a tela dentro do veiculo
 * quer ver no topo a missao que esta rodando agora.
 */
@Composable
fun MissionsScreen(
    viewModel: SoeViewModel,
    onOpenTrip: (String) -> Unit,
    onOpenParking: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mobility = (context.applicationContext as SoeApplication).container.mobilityRepository
    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    val trips by remember(eventId) {
        eventId?.let { mobility.observeTrips(it) } ?: MutableStateFlow(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val pending by viewModel.pendingUploads.collectAsStateWithLifecycle()

    var sosOpen by remember { mutableStateOf(false) }
    if (sosOpen) {
        SosDialog(viewModel = viewModel, onDismiss = { sosOpen = false })
    }

    Scaffold(
        topBar = {
            SoeTopBar(
                title = "Logistica e mobilidade",
                subtitle = "${trips.count { it.status != "COMPLETED" && it.status != "CANCELLED" }} missao(oes) em aberto",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            // O SOS acompanha o operador em toda a operacao logistica, nao
            // so dentro de uma missao: a emergencia pode ser do entorno.
            ExtendedFloatingActionButton(
                onClick = { sosOpen = true },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                text = { Text("SOS") },
                icon = { Icon(Icons.Default.Sos, contentDescription = null) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pending > 0) {
                item { OfflineBanner(pendingCount = pending) }
            }

            item {
                OutlinedCard(onClick = onOpenParking, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Estacionamento e trafego", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Validar credencial, registrar entrada/saida e bloqueios",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            if (trips.isEmpty()) {
                item {
                    EmptyMessage(
                        if (eventId == null) {
                            "Nenhum evento ativo. Sincronize na tela inicial."
                        } else {
                            "Nenhuma missao atribuida a voce neste evento."
                        },
                    )
                }
            }

            items(trips, key = { it.id }) { trip ->
                TripCard(trip = trip, onClick = { onOpenTrip(trip.id) })
            }
        }
    }
}

@Composable
private fun TripCard(trip: TripEntity, onClick: () -> Unit) {
    val statusColor = when (trip.status) {
        "IN_PROGRESS" -> Color(0xFF059669)
        "CHECKED_IN" -> Color(0xFF2563EB)
        "COMPLETED" -> Color(0xFF64748B)
        "CANCELLED" -> MaterialTheme.colorScheme.error
        else -> Color(0xFFD97706)
    }

    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    trip.code,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SoePill(labelOf(TRIP_SERVICES, trip.service), MaterialTheme.colorScheme.primary)
                SoePill(labelOf(TRIP_STATUS, trip.status), statusColor)
            }

            val route = listOfNotNull(trip.originName, trip.destinationName)
                .joinToString(" → ")
                .ifBlank { trip.routeName.orEmpty() }
            if (route.isNotBlank()) {
                Text(route, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Saida ${Time.time(trip.scheduledStartMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                trip.capacity?.let {
                    Text(
                        "Capacidade $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (trip.pendingSync) {
                    Text(
                        "na fila local",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
