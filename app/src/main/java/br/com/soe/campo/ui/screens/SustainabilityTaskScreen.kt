package br.com.soe.campo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.Time
import br.com.soe.campo.data.local.PendingAttachmentEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Execucao de um item do checklist de DH & Sustentabilidade.
 *
 * O fluxo e o que o modulo pede em um so lugar: entender o requisito,
 * responder, comprovar e concluir. Evidencia aceita foto, video e arquivo —
 * limitar a foto deixaria de fora comprovante de destinacao e lista de
 * presenca, que sao o grosso das evidencias deste modulo.
 */
@Composable
fun SustainabilityTaskScreen(
    viewModel: SoeViewModel,
    taskId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as SoeApplication).container
    val repository = container.sustainabilityRepository
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()

    val task by remember(taskId) { repository.observeTask(taskId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val updates by remember(taskId) { repository.observeUpdates(taskId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val evidence by remember(taskId) { repository.observeEvidence(taskId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var status by remember { mutableStateOf("DONE") }
    var comment by remember { mutableStateOf("") }
    var justification by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var correctiveOpen by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    suspend fun attach(path: String, mime: String) {
        val (latitude, longitude) = currentCoordinates(context)
        repository.attachEvidence(taskId, path, mime, latitude, longitude)
    }

    // Foto
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingPhoto
        pendingPhoto = null
        if (success && file != null) {
            scope.launch {
                attach(file.absolutePath, "image/jpeg")
                message = "Foto anexada."
            }
        }
    }

    // Video
    var pendingVideo by remember { mutableStateOf<File?>(null) }
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { success ->
        val file = pendingVideo
        pendingVideo = null
        if (success && file != null) {
            scope.launch {
                attach(file.absolutePath, "video/mp4")
                message = "Video anexado."
            }
        }
    }

    // Arquivo ja existente no aparelho. Copiamos para o diretorio privado
    // porque o Uri do seletor nao sobrevive a reinicializacao do app, e o
    // upload pode acontecer horas depois, no proximo sincronismo.
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val copied = container.fieldRepository.copyToEvidence(uri)
                if (copied != null) {
                    attach(copied.first, copied.second)
                    message = "Arquivo anexado."
                } else {
                    message = "Nao foi possivel ler o arquivo escolhido."
                }
            }
        }
    }

    fun launchPhoto() {
        val file = container.fieldRepository.newPhotoFile()
        pendingPhoto = file
        photoLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        )
    }

    fun launchVideo() {
        val file = container.fieldRepository.newVideoFile()
        pendingVideo = file
        videoLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        )
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchPhoto() }

    val current = task
    val needsJustification = status == "NOT_APPLICABLE" || status == "NOT_DONE"
    val missingEvidence = current?.requiresEvidence == true && evidence.isEmpty() && status == "DONE"
    val blockingReason = when {
        current == null -> "Tarefa nao encontrada neste aparelho."
        needsJustification && justification.isBlank() ->
            "Justificativa obrigatoria para \"${labelOf(SUSTAINABILITY_ANSWERS, status)}\"."
        missingEvidence -> "Este item exige ao menos uma evidencia anexada."
        else -> null
    }

    if (correctiveOpen && current != null) {
        CorrectiveActionDialog(
            taskTitle = current.title,
            onDismiss = { correctiveOpen = false },
            onConfirm = { title, description, severity ->
                scope.launch {
                    val (latitude, longitude) = currentCoordinates(context)
                    repository.openCorrectiveAction(
                        taskId = taskId,
                        title = title,
                        description = description,
                        severity = severity,
                        latitude = latitude,
                        longitude = longitude,
                        authorName = session?.userName,
                    )
                    SyncWorker.syncNow(context)
                    correctiveOpen = false
                    message = "Acao corretiva aberta como ocorrencia."
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SoeTopBar(
                title = current?.theme ?: "Tarefa",
                subtitle = current?.let { labelOf(SUSTAINABILITY_PHASES, it.phase) },
                onBack = onBack,
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    blockingReason?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        enabled = !saving && blockingReason == null,
                        onClick = {
                            saving = true
                            scope.launch {
                                val (latitude, longitude) = currentCoordinates(context)
                                val result = repository.answer(
                                    taskId = taskId,
                                    status = status,
                                    comment = comment,
                                    justification = justification,
                                    authorName = session?.userName,
                                    latitude = latitude,
                                    longitude = longitude,
                                )
                                saving = false
                                result
                                    .onSuccess {
                                        SyncWorker.syncNow(context)
                                        comment = ""
                                        justification = ""
                                        message = "Resposta registrada."
                                    }
                                    .onFailure { message = it.message }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(if (saving) "Registrando..." else "Registrar resposta")
                    }
                }
            }
        },
    ) { padding ->
        if (current == null) {
            EmptyMessage("Tarefa nao encontrada neste aparelho.", Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                    current.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoePill(
                            labelOf(SUSTAINABILITY_CRITERIA, current.criterion),
                            if (current.criterion == "REQUIRED") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        SoePill(
                            labelOf(SUSTAINABILITY_PRIORITIES, current.priority),
                            Color(0xFFD97706),
                        )
                        SoePill(
                            labelOf(SUSTAINABILITY_STATUS, current.status),
                            MaterialTheme.colorScheme.secondary,
                        )
                    }

                    InfoRow("Responsavel", current.responsibleName)
                    InfoRow("Local", current.locationName)
                    InfoRow("Prazo", current.dueAtMs?.let { Time.full(it) })
                    if (current.recurring) {
                        InfoRow("Recorrencia", "Aceita varias execucoes (uma por ronda/turno)")
                    }
                }
            }

            item { HorizontalDivider() }

            // ---------------------------------------------------- Resposta
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resposta", style = MaterialTheme.typography.titleMedium)
                    ChipGrid(
                        options = SUSTAINABILITY_ANSWERS,
                        selected = status,
                        onSelect = { status = it },
                    )

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Comentario") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (needsJustification) {
                        OutlinedTextField(
                            value = justification,
                            onValueChange = { justification = it },
                            label = { Text("Justificativa (obrigatoria)") },
                            minLines = 2,
                            isError = justification.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // --------------------------------------------------- Evidencias
            item {
                Text(
                    if (current.requiresEvidence) "Evidencias (obrigatorias)" else "Evidencias",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (evidence.isEmpty()) {
                item {
                    Text(
                        "Nenhuma evidencia anexada neste aparelho.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(evidence, key = { it.id }) { item -> EvidenceRow(item) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                launchPhoto()
                            } else {
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Foto")
                    }

                    OutlinedButton(onClick = { launchVideo() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Video")
                    }

                    OutlinedButton(
                        onClick = { fileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Arquivo")
                    }
                }
            }

            // --------------------------------------------- Acao corretiva
            item {
                OutlinedCard(
                    onClick = { correctiveOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Abrir ocorrencia / acao corretiva", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Registra a nao conformidade como ocorrencia vinculada a este item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ------------------------------------------------- Historico
            if (updates.isNotEmpty()) {
                item {
                    Text("Historico neste aparelho", style = MaterialTheme.typography.titleMedium)
                }
                items(updates, key = { it.id }) { update ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "${labelOf(SUSTAINABILITY_STATUS, update.status)} · ${Time.full(update.answeredAtMs)}" +
                                if (update.pendingSync) " · na fila local" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        update.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        update.justification?.let {
                            Text(
                                "Justificativa: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceRow(evidence: PendingAttachmentEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (evidence.mimeType.startsWith("image/")) {
            AsyncImage(
                model = File(evidence.localPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(
                    if (evidence.mimeType.startsWith("video/")) {
                        Icons.Default.Videocam
                    } else {
                        Icons.Default.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(evidence.fileName, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    Time.dateTime(evidence.capturedAtMs),
                    if (evidence.latitude != null) "com localizacao" else null,
                    if (evidence.uploaded) "enviada" else "na fila local",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CorrectiveActionDialog(
    taskTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, severity: String) -> Unit,
) {
    var title by remember { mutableStateOf(taskTitle) }
    var description by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("MEDIUM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acao corretiva") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("O que foi encontrado") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descricao e providencia") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Gravidade", style = MaterialTheme.typography.labelMedium)
                ChipGrid(
                    options = SEVERITIES,
                    selected = severity,
                    onSelect = { severity = it },
                )
                Text(
                    "O item fica marcado como \"nao atendido\" e a ocorrencia entra na fila " +
                        "local como qualquer outra.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title, description.trim().ifBlank { null }, severity) },
            ) { Text("Abrir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
