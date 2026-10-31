package br.com.soe.campo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.Time
import br.com.soe.campo.data.local.ParkingAccessEntity
import br.com.soe.campo.data.local.ParkingSectorEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Estacionamento e trafego (modulo 11, secao 4.6).
 *
 * Tudo em uma tela so porque e um agente so: valida credencial e placa,
 * registra entrada ou saida, consulta ocupacao e abre ocorrencia de trafego
 * (bloqueio, liberacao, desvio) sem trocar de contexto.
 *
 * A recusa tambem vira registro — saber o que foi barrado faz parte do
 * controle de acesso.
 */
@Composable
fun ParkingScreen(
    viewModel: SoeViewModel,
    onReportIncident: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as SoeApplication).container
    val mobility = container.mobilityRepository
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    val sectors by remember(eventId) {
        eventId?.let { mobility.observeSectors(it) } ?: MutableStateFlow(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val accesses by remember(eventId) {
        eventId?.let { mobility.observeAccesses(it) } ?: MutableStateFlow(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var credential by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var sectorId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var refusalOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingPhoto
        pendingPhoto = null
        if (success && file != null) photo = file.absolutePath
    }

    fun launchCamera() {
        val file = container.fieldRepository.newPhotoFile()
        pendingPhoto = file
        cameraLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        )
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }

    fun clearForm() {
        credential = ""
        plate = ""
        notes = ""
        photo = null
    }

    fun register(direction: String, authorized: Boolean, reason: String?) {
        val currentEvent = eventId ?: return
        saving = true
        scope.launch {
            val (latitude, longitude) = currentCoordinates(context)
            mobility.registerParkingAccess(
                eventId = currentEvent,
                sectorId = sectorId,
                sectorName = sectors.firstOrNull { it.id == sectorId }?.name,
                credential = credential,
                plate = plate,
                direction = direction,
                authorized = authorized,
                reason = reason,
                notes = notes,
                latitude = latitude,
                longitude = longitude,
                photoPath = photo,
            )
            SyncWorker.syncNow(context)
            saving = false
            clearForm()
            message = if (authorized) "Acesso registrado." else "Recusa registrada."
        }
    }

    if (refusalOpen) {
        RefusalDialog(
            onDismiss = { refusalOpen = false },
            onConfirm = { reason ->
                refusalOpen = false
                register("IN", authorized = false, reason = reason)
            },
        )
    }

    // Sem credencial nem placa nao ha o que validar: o registro ficaria
    // anonimo e inutil para a auditoria do acesso.
    val canRegister = eventId != null && !saving &&
        (credential.isNotBlank() || plate.isNotBlank())

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { SoeTopBar("Estacionamento e trafego", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sectors.isNotEmpty()) {
                item {
                    Text("Ocupacao dos setores", style = MaterialTheme.typography.titleMedium)
                }
                items(sectors, key = { it.id }) { sector -> SectorCard(sector) }
            }

            item {
                Text("Validar acesso", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = { Text("Credencial / autorizacao") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = plate,
                            onValueChange = { plate = it.uppercase() },
                            label = { Text("Placa") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (sectors.isNotEmpty()) {
                            Text("Setor", style = MaterialTheme.typography.labelMedium)
                            ChipGrid(
                                options = sectors.map { it.id to it.name },
                                selected = sectorId.orEmpty(),
                                onSelect = { sectorId = if (sectorId == it) null else it },
                            )
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Observacao") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    launchCamera()
                                } else {
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (photo == null) "Anexar foto" else "Foto anexada · trocar")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = canRegister,
                                onClick = { register("IN", authorized = true, reason = null) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Entrada") }

                            Button(
                                enabled = canRegister,
                                onClick = { register("OUT", authorized = true, reason = null) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Saida") }
                        }

                        OutlinedButton(
                            enabled = canRegister,
                            onClick = { refusalOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Registrar recusa") }

                        if (eventId == null) {
                            Text(
                                "Nenhum evento ativo. Sincronize na tela inicial.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                OutlinedCard(onClick = onReportIncident, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.ReportProblem,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Bloqueio, liberacao ou desvio", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Abre ocorrencia de trafego com foto e localizacao",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (accesses.isNotEmpty()) {
                item {
                    Text("Ultimos registros", style = MaterialTheme.typography.titleMedium)
                }
                items(accesses, key = { it.id }) { access -> AccessRow(access) }
            }
        }
    }
}

@Composable
private fun SectorCard(sector: ParkingSectorEntity) {
    val free = if (sector.capacity != null && sector.occupied != null) {
        (sector.capacity - sector.occupied).coerceAtLeast(0)
    } else {
        null
    }

    Surface(
        color = if (sector.blocked) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(sector.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        sector.blocked -> "Setor bloqueado"
                        free != null -> "$free vaga(s) livre(s) de ${sector.capacity}"
                        else -> sector.notes ?: "Ocupacao nao informada"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (free == 0 && !sector.blocked) {
                SoePill("Lotado", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AccessRow(access: ParkingAccessEntity) {
    val color = if (access.authorized) Color(0xFF059669) else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(access.plate, access.credential).joinToString(" · ")
                    .ifBlank { "Sem identificacao" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOfNotNull(
                    if (access.direction == "IN") "Entrada" else "Saida",
                    access.sectorName,
                    Time.time(access.occurredAtMs),
                    access.reason,
                    if (access.pendingSync) "na fila local" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SoePill(if (access.authorized) "Autorizado" else "Recusado", color)
    }
}

@Composable
private fun RefusalDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar recusa") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Motivo") },
                placeholder = { Text("Ex.: credencial vencida / setor nao autorizado") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(enabled = reason.isNotBlank(), onClick = { onConfirm(reason) }) {
                Text("Registrar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
