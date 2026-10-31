package br.com.soe.campo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.Time
import br.com.soe.campo.data.VehicleCheckCatalog
import br.com.soe.campo.data.local.TripStopEntity
import br.com.soe.campo.data.local.VehicleEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Execucao da missao, na ordem em que ela acontece no veiculo: check-in do
 * motorista (4.2), pre-operacional (4.3), embarque (4.4), percurso (4.5) e
 * check-out (4.8).
 *
 * Cada etapa so libera a seguinte. Nao e burocracia de tela: e a sequencia
 * que o modulo define, e o bloqueio do pre-operacional depende dela.
 */
@Composable
fun TripScreen(
    viewModel: SoeViewModel,
    tripId: String,
    onOpenCheck: (String) -> Unit,
    onOpenBoarding: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as SoeApplication).container
    val mobility = container.mobilityRepository
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()

    val trip by remember(tripId) { mobility.observeTrip(tripId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val stops by remember(tripId) { mobility.observeStops(tripId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val passengers by remember(tripId) { mobility.observePassengers(tripId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val preOp by remember(tripId) {
        mobility.observeLatestCheck(tripId, VehicleCheckCatalog.KIND_PRE_OP)
    }.collectAsStateWithLifecycle(initialValue = null)
    val closeOut by remember(tripId) {
        mobility.observeLatestCheck(tripId, VehicleCheckCatalog.KIND_CLOSE_OUT)
    }.collectAsStateWithLifecycle(initialValue = null)

    var vehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    LaunchedEffect(trip?.vehicleId) {
        vehicle = trip?.vehicleId?.let { mobility.vehicle(it) }
    }

    var sosOpen by remember { mutableStateOf(false) }
    var checkOutOpen by remember { mutableStateOf(false) }
    var unplannedStopOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    // Foto do check-in: a politica do evento pode exigir.
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingPhoto
        pendingPhoto = null
        if (success && file != null) {
            scope.launch {
                val (latitude, longitude) = currentCoordinates(context)
                mobility.checkInDriver(tripId, latitude, longitude, file.absolutePath)
                SyncWorker.syncNow(context)
                message = "Check-in registrado com foto."
            }
        }
    }

    fun launchCheckInCamera() {
        val file = container.fieldRepository.newPhotoFile()
        pendingPhoto = file
        cameraLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        )
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCheckInCamera() }

    val current = trip
    val blocked = preOp?.let { it.blocked && it.overrideReason == null } ?: false
    val preOpDone = preOp?.status == "COMPLETED"
    val boarded = passengers.count { it.status == "BOARDED" } + (current?.aggregateBoarded ?: 0)

    if (sosOpen && current != null) {
        SosDialog(viewModel = viewModel, onDismiss = { sosOpen = false }, trip = current)
    }

    if (unplannedStopOpen && current != null) {
        UnplannedStopDialog(
            onDismiss = { unplannedStopOpen = false },
            onConfirm = { name ->
                scope.launch {
                    val (latitude, longitude) = currentCoordinates(context)
                    mobility.addUnplannedStop(tripId, name, latitude, longitude)
                    SyncWorker.syncNow(context)
                    unplannedStopOpen = false
                    message = "Parada nao programada registrada."
                }
            },
        )
    }

    if (checkOutOpen && current != null) {
        CheckOutDialog(
            odometerStart = current.odometerStart,
            closeOutDone = closeOut?.status == "COMPLETED",
            onOpenCloseOut = {
                scope.launch {
                    val (latitude, longitude) = currentCoordinates(context)
                    val id = mobility.startVehicleCheck(
                        eventId = current.eventId,
                        tripId = tripId,
                        vehicleId = current.vehicleId,
                        kind = VehicleCheckCatalog.KIND_CLOSE_OUT,
                        latitude = latitude,
                        longitude = longitude,
                    )
                    checkOutOpen = false
                    onOpenCheck(id)
                }
            },
            onDismiss = { checkOutOpen = false },
            onConfirm = { odometer, fuel, returned, notes ->
                scope.launch {
                    val (latitude, longitude) = currentCoordinates(context)
                    mobility.checkOutTrip(
                        tripId = tripId,
                        odometerEnd = odometer,
                        fuelEnd = fuel,
                        vehicleReturned = returned,
                        notes = notes,
                        latitude = latitude,
                        longitude = longitude,
                    )
                    SyncWorker.syncNow(context)
                    checkOutOpen = false
                    message = "Missao encerrada."
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SoeTopBar(
                title = current?.code ?: "Missao",
                subtitle = current?.let { labelOf(TRIP_STATUS, it.status) },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { sosOpen = true }) {
                        Icon(
                            Icons.Default.Sos,
                            contentDescription = "SOS",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) {
            EmptyMessage("Missao nao encontrada neste aparelho.", Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------ Cabecalho
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Servico", labelOf(TRIP_SERVICES, current.service))
                    InfoRow("Rota", current.routeName)
                    InfoRow(
                        "Trajeto",
                        listOfNotNull(current.originName, current.destinationName)
                            .joinToString(" → ")
                            .ifBlank { null },
                    )
                    InfoRow("Saida prevista", Time.dateTime(current.scheduledStartMs))
                    InfoRow(
                        "Veiculo",
                        vehicle?.let { v ->
                            listOfNotNull(v.code, v.model, v.plate).joinToString(" · ")
                        },
                    )
                    InfoRow("Capacidade", current.capacity?.let { "$it passageiro(s)" })
                }
            }

            if (blocked) {
                BlockBanner(
                    "Nao conformidade critica no pre-operacional. A partida esta bloqueada " +
                        "ate que haja excecao autorizada no proprio checklist.",
                )
            }

            // -------------------------------------- 4.2 Check-in do motorista
            StepCard(
                step = "1",
                title = "Check-in do motorista",
                done = current.checkedInAtMs != null,
                detail = current.checkedInAtMs?.let { "Confirmado em ${Time.full(it)}" }
                    ?: "Confirme identidade, veiculo e circuito.",
            ) {
                if (current.checkedInAtMs == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val (latitude, longitude) = currentCoordinates(context)
                                    mobility.checkInDriver(tripId, latitude, longitude, null)
                                    SyncWorker.syncNow(context)
                                    message = "Check-in registrado."
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Confirmar") }

                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    launchCheckInCamera()
                                } else {
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Com foto")
                        }
                    }
                }
            }

            // ------------------------------------ 4.3 Checklist pre-operacional
            StepCard(
                step = "2",
                title = VehicleCheckCatalog.title(VehicleCheckCatalog.KIND_PRE_OP),
                done = preOpDone && !blocked,
                detail = when {
                    preOp == null -> "Obrigatorio antes da partida."
                    blocked -> "Concluido com bloqueio critico em aberto."
                    preOpDone -> "Concluido em ${Time.full(preOp?.completedAtMs)}"
                    else -> "Em preenchimento."
                },
            ) {
                OutlinedButton(
                    enabled = current.checkedInAtMs != null,
                    onClick = {
                        scope.launch {
                            val (latitude, longitude) = currentCoordinates(context)
                            val id = mobility.startVehicleCheck(
                                eventId = current.eventId,
                                tripId = tripId,
                                vehicleId = current.vehicleId,
                                kind = VehicleCheckCatalog.KIND_PRE_OP,
                                latitude = latitude,
                                longitude = longitude,
                            )
                            onOpenCheck(id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (preOp == null) "Abrir checklist" else "Abrir / revisar checklist")
                }
            }

            // -------------------------------------- 4.4 Embarque e desembarque
            StepCard(
                step = "3",
                title = "Embarque e desembarque",
                done = boarded > 0,
                detail = buildString {
                    append("$boarded embarcado(s)")
                    current.capacity?.let { append(" de $it") }
                    if (passengers.isNotEmpty()) append(" · ${passengers.size} na lista")
                },
            ) {
                OutlinedButton(
                    onClick = onOpenBoarding,
                    enabled = current.checkedInAtMs != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Abrir embarque") }
            }

            // -------------------------------------------- 4.5 Execucao da viagem
            StepCard(
                step = "4",
                title = "Execucao da viagem",
                done = current.startedAtMs != null,
                detail = current.startedAtMs?.let { "Partida as ${Time.time(it)}" }
                    ?: "Registre a partida ao sair do ponto.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current.startedAtMs == null) {
                        Button(
                            enabled = preOpDone && !blocked,
                            onClick = {
                                scope.launch {
                                    val (latitude, longitude) = currentCoordinates(context)
                                    mobility.startTrip(tripId, latitude, longitude)
                                    SyncWorker.syncNow(context)
                                    message = "Partida registrada."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Registrar partida") }

                        if (!preOpDone) {
                            Text(
                                "Conclua o pre-operacional para liberar a partida.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    stops.forEach { stop ->
                        StopRow(
                            stop = stop,
                            enabled = current.startedAtMs != null && stop.arrivedAtMs == null,
                            onArrive = {
                                scope.launch {
                                    val (latitude, longitude) = currentCoordinates(context)
                                    mobility.registerStopArrival(stop.id, latitude, longitude)
                                    SyncWorker.syncNow(context)
                                    message = "Passagem por ${stop.name} registrada."
                                }
                            },
                        )
                    }

                    if (current.startedAtMs != null) {
                        OutlinedButton(
                            onClick = { unplannedStopOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Parada nao programada / desvio")
                        }
                    }
                }
            }

            // ------------------------------------------------- 4.8 Check-out
            StepCard(
                step = "5",
                title = "Check-out",
                done = current.status == "COMPLETED",
                detail = if (current.status == "COMPLETED") {
                    "Encerrada em ${Time.full(current.finishedAtMs)}"
                } else {
                    "Quilometragem, combustivel, encerramento e devolucao."
                },
            ) {
                if (current.status != "COMPLETED") {
                    Button(
                        enabled = current.startedAtMs != null,
                        onClick = { checkOutOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Encerrar missao") }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepCard(
    step: String,
    title: String,
    done: Boolean,
    detail: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = if (done) {
                        Color(0xFF059669).copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (done) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        Text(
                            step,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun StopRow(stop: TripStopEntity, enabled: Boolean, onArrive: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (stop.arrivedAtMs != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (stop.arrivedAtMs != null) {
                Color(0xFF059669)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.bodyMedium)
            val time = stop.arrivedAtMs?.let { "Passou as ${Time.time(it)}" }
                ?: stop.scheduledAtMs?.let { "Previsto ${Time.time(it)}" }
            if (time != null) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (stop.arrivedAtMs == null) {
            TextButton(onClick = onArrive, enabled = enabled) { Text("Cheguei") }
        }
    }
}

@Composable
private fun BlockBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Block, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UnplannedStopDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parada nao programada") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Motivo / local") },
                placeholder = { Text("Ex.: desvio por via bloqueada na Av. Central") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Registrar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CheckOutDialog(
    odometerStart: Int?,
    closeOutDone: Boolean,
    onOpenCloseOut: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (odometer: Int?, fuel: Int?, returned: Boolean, notes: String?) -> Unit,
) {
    var odometer by remember { mutableStateOf("") }
    var fuel by remember { mutableStateOf("") }
    var returned by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    val parsedOdometer = odometer.filter { it.isDigit() }.toIntOrNull()
    // Hodometro final menor que o inicial e erro de digitacao, nao um dado:
    // avisamos antes de gravar porque a quilometragem vira custo na web.
    val odometerInvalid = parsedOdometer != null && odometerStart != null &&
        parsedOdometer < odometerStart

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encerrar missao") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { c -> c.isDigit() } },
                    label = { Text("Quilometragem final") },
                    supportingText = odometerStart?.let { { Text("Inicial: $it km") } },
                    isError = odometerInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (odometerInvalid) {
                    Text(
                        "Valor menor que a quilometragem inicial.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedTextField(
                    value = fuel,
                    onValueChange = { fuel = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Combustivel final (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(onClick = onOpenCloseOut, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (closeOutDone) {
                            "Checklist de encerramento concluido · revisar"
                        } else {
                            "Preencher checklist de encerramento"
                        },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = returned, onCheckedChange = { returned = it })
                    Text("Veiculo devolvido / estacionado", Modifier.weight(1f))
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Pendencias e observacoes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !odometerInvalid,
                onClick = {
                    onConfirm(
                        parsedOdometer,
                        fuel.toIntOrNull()?.coerceIn(0, 100),
                        returned,
                        notes.trim().ifBlank { null },
                    )
                },
            ) { Text("Encerrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
