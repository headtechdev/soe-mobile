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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.ChecklistAnswerInput
import br.com.soe.campo.data.local.ChecklistItemEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Execucao rapida de checklist: um toque por item (Conforme / Nao conforme /
 * N/A), observacao opcional e foto quando o modelo exigir.
 */
@Composable
fun ChecklistRunScreen(
    viewModel: SoeViewModel,
    executionId: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as SoeApplication).container.fieldRepository
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()

    var templateId by remember { mutableStateOf<String?>(null) }
    var templateName by remember { mutableStateOf("Checklist") }
    var requiresPhoto by remember { mutableStateOf(false) }
    var readOnly by remember { mutableStateOf(false) }

    LaunchedEffect(executionId) {
        val execution = repository.execution(executionId) ?: return@LaunchedEffect
        templateId = execution.templateId
        readOnly = execution.status == "COMPLETED" || execution.status == "CANCELLED"

        repository.template(execution.templateId)?.let { template ->
            templateName = template.name
            requiresPhoto = template.requiresPhoto
        }
    }

    val items by remember(templateId) {
        templateId?.let { repository.observeItems(it) }
            ?: MutableStateFlow(emptyList<ChecklistItemEntity>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val savedAnswers by remember(executionId) {
        repository.observeAnswers(executionId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val photos by remember(executionId) {
        repository.observeAttachmentsOf(executionId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Respostas em memoria; so vao para o SQLite quando o operador salva.
    val answers = remember { mutableStateMapOf<String, ChecklistAnswerInput>() }
    LaunchedEffect(savedAnswers) {
        savedAnswers.forEach { saved ->
            if (!answers.containsKey(saved.itemId)) {
                answers[saved.itemId] = ChecklistAnswerInput(
                    valueBool = saved.valueBool,
                    valueText = saved.valueText,
                    valueNumber = saved.valueNumber,
                    conform = saved.conform,
                    notes = saved.notes,
                )
            }
        }
    }

    var generalNotes by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingPhoto
        val eventId = session?.eventId
        if (success && file != null && eventId != null) {
            scope.launch {
                repository.attachPhoto(
                    eventId = eventId,
                    entityType = "CHECKLIST_EXECUTION",
                    entityId = executionId,
                    localPath = file.absolutePath,
                )
            }
        }
        pendingPhoto = null
    }

    fun launchCamera() {
        val file = repository.newPhotoFile()
        pendingPhoto = file
        cameraLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        )
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }

    val answeredCount = answers.count { it.value.conform != null || it.value.valueText != null }
    val missingRequired = items.filter { it.required && answers[it.id]?.conform == null }
    val missingPhoto = requiresPhoto && photos.isEmpty()

    Scaffold(
        topBar = {
            SoeTopBar(
                title = templateName,
                subtitle = "$answeredCount de ${items.size} item(ns) respondido(s)",
                onBack = onBack,
            )
        },
        bottomBar = {
            if (!readOnly) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (missingRequired.isNotEmpty()) {
                            Text(
                                "${missingRequired.size} item(ns) obrigatorio(s) sem resposta",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (missingPhoto) {
                            Text(
                                "Este checklist exige ao menos uma foto de evidencia",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        saving = true
                                        repository.saveExecution(
                                            executionId,
                                            answers.toMap(),
                                            generalNotes.ifBlank { null },
                                            finish = false,
                                        )
                                        saving = false
                                        onDone()
                                    }
                                },
                                enabled = !saving,
                                modifier = Modifier.weight(1f),
                            ) { Text("Salvar rascunho") }

                            Button(
                                onClick = {
                                    scope.launch {
                                        saving = true
                                        repository.saveExecution(
                                            executionId,
                                            answers.toMap(),
                                            generalNotes.ifBlank { null },
                                            finish = true,
                                        )
                                        SyncWorker.syncNow(context)
                                        saving = false
                                        onDone()
                                    }
                                },
                                enabled = !saving && missingRequired.isEmpty() && !missingPhoto,
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
            items(items, key = { it.id }) { item ->
                ChecklistItemCard(
                    item = item,
                    input = answers[item.id],
                    readOnly = readOnly,
                    onChange = { answers[item.id] = it },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
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
                        Text("Anexar foto")
                    }
                }

                item {
                    OutlinedTextField(
                        value = generalNotes,
                        onValueChange = { generalNotes = it },
                        label = { Text("Observacoes gerais") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemCard(
    item: ChecklistItemEntity,
    input: ChecklistAnswerInput?,
    readOnly: Boolean,
    onChange: (ChecklistAnswerInput) -> Unit,
) {
    val current = input ?: ChecklistAnswerInput()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                item.label + if (item.required) " *" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            item.helpText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (item.type) {
                "TEXT", "PHOTO", "SELECT", "SIGNATURE" -> {
                    OutlinedTextField(
                        value = current.valueText.orEmpty(),
                        onValueChange = {
                            onChange(
                                current.copy(
                                    valueText = it,
                                    conform = if (it.isBlank()) null else true,
                                ),
                            )
                        },
                        enabled = !readOnly,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                "NUMBER" -> {
                    OutlinedTextField(
                        value = current.valueNumber?.toString().orEmpty(),
                        onValueChange = { text ->
                            val parsed = text.replace(",", ".").toDoubleOrNull()
                            onChange(
                                current.copy(
                                    valueNumber = parsed,
                                    conform = if (parsed == null) null else true,
                                ),
                            )
                        },
                        enabled = !readOnly,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConformChip(
                            label = "Conforme",
                            selected = current.conform == true,
                            color = androidx.compose.ui.graphics.Color(0xFF059669),
                            enabled = !readOnly,
                        ) { onChange(current.copy(valueBool = true, conform = true)) }

                        ConformChip(
                            label = "Nao conforme",
                            selected = current.conform == false,
                            color = MaterialTheme.colorScheme.error,
                            enabled = !readOnly,
                        ) { onChange(current.copy(valueBool = false, conform = false)) }

                        ConformChip(
                            label = "N/A",
                            selected = current.valueBool == null && current.notes != null,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            enabled = !readOnly,
                        ) { onChange(current.copy(valueBool = null, conform = null, notes = current.notes ?: "Nao aplicavel")) }
                    }
                }
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
private fun ConformChip(
    label: String,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
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
