package br.com.soe.campo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.VehicleCheckAnswerInput
import br.com.soe.campo.data.VehicleCheckCatalog
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Checklist do veiculo — pre-operacional (4.3) e encerramento (4.8).
 *
 * Os itens vem do catalogo fixo do app. Nao conformidade em item critico
 * bloqueia a partida; a excecao autorizada exige justificativa e fica
 * registrada junto do checklist, nunca desfaz o bloqueio em silencio.
 */
@Composable
fun VehicleCheckScreen(
    viewModel: SoeViewModel,
    checkId: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as SoeApplication).container
    val mobility = container.mobilityRepository
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()

    val check by remember(checkId) { mobility.observeCheck(checkId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val savedAnswers by remember(checkId) { mobility.observeCheckAnswers(checkId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val photos by remember(checkId) {
        container.fieldRepository.observeAttachmentsOf(checkId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val answers = remember { mutableStateMapOf<String, VehicleCheckAnswerInput>() }
    LaunchedEffect(savedAnswers) {
        savedAnswers.forEach { saved ->
            if (!answers.containsKey(saved.itemKey)) {
                answers[saved.itemKey] = VehicleCheckAnswerInput(saved.answer, saved.notes)
            }
        }
    }

    var odometer by remember { mutableStateOf("") }
    var fuel by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var overrideOpen by remember { mutableStateOf(false) }

    LaunchedEffect(check?.id) {
        check?.let {
            if (odometer.isBlank()) odometer = it.odometer?.toString().orEmpty()
            if (fuel.isBlank()) fuel = it.fuelLevel?.toString().orEmpty()
            if (notes.isBlank()) notes = it.notes.orEmpty()
        }
    }

    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingPhoto
        pendingPhoto = null
        if (success && file != null) {
            scope.launch { mobility.attachCheckPhoto(checkId, file.absolutePath) }
        }
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

    val current = check
    val kind = current?.kind ?: VehicleCheckCatalog.KIND_PRE_OP
    val items = VehicleCheckCatalog.items(kind)
    val missing = items.filter { answers[it.key] == null }
    val criticalIssues = items.filter {
        it.critical && answers[it.key]?.answer == VehicleCheckCatalog.NON_CONFORM
    }
    val readOnly = current?.status == "COMPLETED"

    if (overrideOpen) {
        OverrideDialog(
            issues = criticalIssues.map { it.label },
            onDismiss = { overrideOpen = false },
            onConfirm = { reason, authorizedBy ->
                scope.launch {
                    mobility.authorizeCheckOverride(checkId, reason, authorizedBy)
                    SyncWorker.syncNow(context)
                    overrideOpen = false
                }
            },
        )
    }

    Scaffold(
        topBar = {
            SoeTopBar(
                title = VehicleCheckCatalog.title(kind),
                subtitle = "${items.size - missing.size} de ${items.size} item(ns) respondido(s)",
                onBack = onBack,
            )
        },
        bottomBar = {
            if (!readOnly) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (missing.isNotEmpty()) {
                            Text(
                                "${missing.size} item(ns) sem resposta",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !saving,
                                onClick = {
                                    scope.launch {
                                        saving = true
                                        mobility.saveVehicleCheck(
                                            checkId = checkId,
                                            answers = answers.toMap(),
                                            odometer = odometer.toIntOrNull(),
                                            fuelLevel = fuel.toIntOrNull()?.coerceIn(0, 100),
                                            notes = notes.ifBlank { null },
                                            finish = false,
                                        )
                                        saving = false
                                        onDone()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Salvar rascunho") }

                            Button(
                                enabled = !saving && missing.isEmpty(),
                                onClick = {
                                    scope.launch {
                                        saving = true
                                        mobility.saveVehicleCheck(
                                            checkId = checkId,
                                            answers = answers.toMap(),
                                            odometer = odometer.toIntOrNull(),
                                            fuelLevel = fuel.toIntOrNull()?.coerceIn(0, 100),
                                            notes = notes.ifBlank { null },
                                            finish = true,
                                        )
                                        SyncWorker.syncNow(context)
                                        saving = false
                                        onDone()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Concluir") }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (criticalIssues.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Bloqueio critico: ${criticalIssues.joinToString { it.label }}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (current?.overrideReason != null) {
                                Text(
                                    "Excecao autorizada: ${current.overrideReason}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            } else if (!readOnly) {
                                TextButton(onClick = { overrideOpen = true }) {
                                    Text("Registrar excecao autorizada")
                                }
                            }
                        }
                    }
                }
            }

            items(items, key = { it.key }) { item ->
                CheckItemCard(
                    item = item,
                    input = answers[item.key],
                    readOnly = readOnly,
                    onChange = { answers[item.key] = it },
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = odometer,
                        onValueChange = { odometer = it.filter { c -> c.isDigit() } },
                        label = { Text("Quilometragem") },
                        enabled = !readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fuel,
                        onValueChange = { fuel = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Combustivel (%)") },
                        enabled = !readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Text("Evidencia fotografica", style = MaterialTheme.typography.titleMedium)
            }

            if (photos.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos, key = { it.id }) { photo ->
                            AsyncImage(
                                model = File(photo.localPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }

            if (!readOnly) {
                item {
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
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
                        Text("Fotografar avaria")
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observacoes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckItemCard(
    item: VehicleCheckCatalog.Item,
    input: VehicleCheckAnswerInput?,
    readOnly: Boolean,
    onChange: (VehicleCheckAnswerInput) -> Unit,
) {
    val current = input ?: VehicleCheckAnswerInput(answer = "")

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (item.critical) {
                    SoePill("Critico", MaterialTheme.colorScheme.error)
                }
            }
            item.help?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnswerChip(
                    "Conforme",
                    current.answer == VehicleCheckCatalog.CONFORM,
                    Color(0xFF059669),
                    !readOnly,
                ) { onChange(current.copy(answer = VehicleCheckCatalog.CONFORM)) }

                AnswerChip(
                    "Nao conforme",
                    current.answer == VehicleCheckCatalog.NON_CONFORM,
                    MaterialTheme.colorScheme.error,
                    !readOnly,
                ) { onChange(current.copy(answer = VehicleCheckCatalog.NON_CONFORM)) }

                if (item.allowsNotApplicable) {
                    AnswerChip(
                        "N/A",
                        current.answer == VehicleCheckCatalog.NOT_APPLICABLE,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        !readOnly,
                    ) { onChange(current.copy(answer = VehicleCheckCatalog.NOT_APPLICABLE)) }
                }
            }

            if (current.answer == VehicleCheckCatalog.NON_CONFORM && item.wantsPhoto) {
                Text(
                    "Fotografe a avaria e descreva a gravidade e a localizacao.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!readOnly) {
                OutlinedTextField(
                    value = current.notes.orEmpty(),
                    onValueChange = { onChange(current.copy(notes = it.ifBlank { null })) },
                    placeholder = { Text("Observacao") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                current.notes?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerChip(
    label: String,
    selected: Boolean,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.18f),
            selectedLabelColor = color,
        ),
    )
}

@Composable
private fun OverrideDialog(
    issues: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, authorizedBy: String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var authorizedBy by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excecao autorizada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Bloqueio em aberto: ${issues.joinToString()}. A liberacao fica registrada " +
                        "com responsavel e justificativa, e sobe junto com o checklist.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Justificativa") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = authorizedBy,
                    onValueChange = { authorizedBy = it },
                    label = { Text("Quem autorizou") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = reason.isNotBlank() && authorizedBy.isNotBlank(),
                onClick = { onConfirm(reason, authorizedBy) },
            ) { Text("Liberar partida") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
