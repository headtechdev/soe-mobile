package br.com.soe.campo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
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
import br.com.soe.campo.data.local.TripPassengerEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.launch

/**
 * Embarque e desembarque (modulo 11, secao 4.4).
 *
 * Dois modos no mesmo lugar, porque o mesmo operador alterna entre eles ao
 * longo do dia: lista nominal quando o servico tem passageiro identificado,
 * contagem agregada quando e publico geral (RB-09).
 *
 * A lotacao trava no limite cadastrado (RB-01): o botao de embarcar some
 * quando o veiculo enche, com o motivo escrito na tela.
 */
@Composable
fun BoardingScreen(
    viewModel: SoeViewModel,
    tripId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mobility = (context.applicationContext as SoeApplication).container.mobilityRepository
    val scope = rememberCoroutineScope()

    val trip by remember(tripId) { mobility.observeTrip(tripId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val passengers by remember(tripId) { mobility.observePassengers(tripId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val stops by remember(tripId) { mobility.observeStops(tripId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var extraOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    val current = trip
    val nominalBoarded = passengers.count { it.status == "BOARDED" }
    val aggregate = current?.aggregateBoarded ?: 0
    val occupancy = nominalBoarded + aggregate
    val capacity = current?.capacity
    val full = capacity != null && occupancy >= capacity

    // Ponto atual: a ultima parada por onde ja passou; antes da partida, a
    // origem. E o que vai gravado no embarque.
    val currentStopName = stops.lastOrNull { it.arrivedAtMs != null }?.name
        ?: current?.originName

    if (extraOpen && current != null) {
        ExtraPassengerDialog(
            stopName = currentStopName,
            onDismiss = { extraOpen = false },
            onConfirm = { name, notes ->
                scope.launch {
                    mobility.addExtraPassenger(tripId, name, currentStopName, notes)
                    SyncWorker.syncNow(context)
                    extraOpen = false
                    message = "Passageiro adicional registrado."
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SoeTopBar(
                title = "Embarque",
                subtitle = current?.let { "${labelOf(TRIP_SERVICES, it.service)} · ${it.code}" },
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (current != null) {
                ExtendedFloatingActionButton(
                    onClick = { extraOpen = true },
                    text = { Text("Adicional") },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                )
            }
        },
    ) { padding ->
        if (current == null) {
            EmptyMessage("Missao nao encontrada neste aparelho.", Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OccupancyCard(
                    occupancy = occupancy,
                    capacity = capacity,
                    stopName = currentStopName,
                )
            }

            // ---------------------------------------- Contagem agregada
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Contagem agregada", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Para publico geral, quando nao e necessario identificar o passageiro.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                enabled = aggregate > 0,
                                onClick = {
                                    scope.launch {
                                        mobility.setAggregateBoarded(tripId, aggregate - 1)
                                    }
                                },
                            ) { Text("−") }

                            Text(
                                aggregate.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f),
                            )

                            Button(
                                enabled = !full,
                                onClick = {
                                    scope.launch {
                                        mobility.setAggregateBoarded(tripId, aggregate + 1)
                                        SyncWorker.syncNow(context)
                                    }
                                },
                            ) { Text("+ embarcou") }
                        }
                    }
                }
            }

            if (passengers.isNotEmpty()) {
                item {
                    Text(
                        "Lista de passageiros",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(passengers, key = { it.id }) { passenger ->
                PassengerCard(
                    passenger = passenger,
                    canBoard = !full,
                    onStatus = { status ->
                        scope.launch {
                            mobility.setPassengerStatus(passenger.id, status, currentStopName)
                            SyncWorker.syncNow(context)
                        }
                    },
                )
            }

            if (passengers.isEmpty()) {
                item {
                    EmptyMessage(
                        "Sem lista nominal nesta viagem. Use a contagem agregada ou registre " +
                            "passageiros adicionais.",
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun OccupancyCard(occupancy: Int, capacity: Int?, stopName: String?) {
    val full = capacity != null && occupancy >= capacity
    Surface(
        color = if (full) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (capacity != null) "$occupancy de $capacity a bordo" else "$occupancy a bordo",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (full) {
                    "Lotacao maxima atingida. Novo embarque bloqueado."
                } else {
                    "Ponto atual: ${stopName ?: "nao informado"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PassengerCard(
    passenger: TripPassengerEntity,
    canBoard: Boolean,
    onStatus: (String) -> Unit,
) {
    val statusColor = when (passenger.status) {
        "BOARDED" -> Color(0xFF059669)
        "DROPPED" -> Color(0xFF2563EB)
        "NO_SHOW", "REFUSED" -> MaterialTheme.colorScheme.error
        "EXTRA" -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(passenger.name, style = MaterialTheme.typography.bodyLarge)
                    val detail = listOfNotNull(
                        passenger.badgeCode,
                        passenger.boardingStopName,
                        passenger.boardedAtMs?.let { "Embarcou ${Time.time(it)}" },
                    ).joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SoePill(labelOf(PASSENGER_STATUS, passenger.status), statusColor)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (passenger.status != "BOARDED") {
                    FilledTonalButton(
                        enabled = canBoard,
                        onClick = { onStatus("BOARDED") },
                    ) { Text("Embarcou") }
                } else {
                    FilledTonalButton(onClick = { onStatus("DROPPED") }) { Text("Desembarcou") }
                }

                TextButton(onClick = { onStatus("NO_SHOW") }) { Text("Ausente") }
                TextButton(onClick = { onStatus("REFUSED") }) { Text("Recusou") }
            }

            if (!canBoard && passenger.status != "BOARDED") {
                Text(
                    "Veiculo na lotacao maxima.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ExtraPassengerDialog(
    stopName: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, notes: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passageiro adicional") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Quem embarcou sem estar na lista. Ponto: ${stopName ?: "nao informado"}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Divergencia / observacao") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, notes.trim().ifBlank { null }) },
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
