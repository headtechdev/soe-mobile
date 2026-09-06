package br.com.soe.campo.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.MyLocation
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
import br.com.soe.campo.data.local.AreaEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Registro de ocorrencia em campo. Grava direto no SQLite e dispara um
 * sincronismo oportunista — se nao houver rede, o registro fica na fila e o
 * operador segue trabalhando.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIncidentScreen(
    viewModel: SoeViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as SoeApplication).container
    val repository = container.fieldRepository
    val scope = rememberCoroutineScope()

    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("SECURITY") }
    var severity by rememberSaveable { mutableStateOf("MEDIUM") }
    var areaId by rememberSaveable { mutableStateOf<String?>(null) }
    var locationText by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var longitude by rememberSaveable { mutableStateOf<Double?>(null) }
    // List<String> nao passa pelo autoSaver do Bundle; as fotos vivem
    // apenas enquanto a tela existe e ja sao gravadas no banco ao salvar.
    var photos by remember { mutableStateOf(listOf<String>()) }
    var saving by remember { mutableStateOf(false) }

    var areas by remember { mutableStateOf(listOf<AreaEntity>()) }
    LaunchedEffect(eventId) {
        eventId?.let { areas = repository.areas(it) }
    }

    // Camera: guardamos o arquivo de destino para so adicionar a lista quando
    // o app de camera confirmar a captura.
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingPhoto
        if (success && file != null) photos = photos + file.absolutePath
        pendingPhoto = null
    }

    fun launchCamera() {
        val file = repository.newPhotoFile()
        pendingPhoto = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraLauncher.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            lastKnownLocation(context)?.let {
                latitude = it.latitude
                longitude = it.longitude
            }
        }
    }

    Scaffold(
        topBar = { SoeTopBar("Reportar incidente", onBack = onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    // O app desenha sob as barras do sistema (enableEdgeToEdge, e
                    // obrigatorio no targetSdk 35). Sem reservar o inset de baixo,
                    // a barra de navegacao do aparelho cobre justamente o botao de
                    // salvar: o operador toca e nada acontece.
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Botao cinza sem explicacao e um beco sem saida em campo.
                    val impedimento = when {
                        eventId == null -> "Nenhum evento ativo. Sincronize na tela inicial."
                        title.isBlank() -> "Informe o titulo da ocorrencia para registrar."
                        else -> null
                    }

                    if (impedimento != null) {
                        Text(
                            impedimento,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = {
                            val currentEvent = eventId ?: return@Button
                            saving = true
                            scope.launch {
                                repository.reportIncident(
                                    eventId = currentEvent,
                                    title = title.trim(),
                                    description = description.trim().ifBlank { null },
                                    type = type,
                                    severity = severity,
                                    areaId = areaId,
                                    locationDescription = locationText.trim().ifBlank { null },
                                    latitude = latitude,
                                    longitude = longitude,
                                    photoPaths = photos,
                                )
                                SyncWorker.syncNow(context)
                                saving = false
                                onDone()
                            }
                        },
                        enabled = !saving && impedimento == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(if (saving) "Registrando..." else "Registrar ocorrencia")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("O que aconteceu") },
                placeholder = { Text("Ex.: queda de grade no portao B") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Tipo", style = MaterialTheme.typography.labelMedium)
            ChipGrid(
                options = INCIDENT_TYPES,
                selected = type,
                onSelect = { type = it },
            )

            Text("Severidade", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SEVERITIES.forEach { (value, label) ->
                    FilterChip(
                        selected = severity == value,
                        onClick = { severity = value },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = severityColor(value).copy(alpha = 0.2f),
                            selectedLabelColor = severityColor(value),
                        ),
                    )
                }
            }

            if (areas.isNotEmpty()) {
                Text("Area", style = MaterialTheme.typography.labelMedium)
                ChipGrid(
                    options = areas.map { it.id to it.name },
                    selected = areaId ?: "",
                    onSelect = { areaId = if (areaId == it) null else it },
                )
            }

            OutlinedTextField(
                value = locationText,
                onValueChange = { locationText = it },
                label = { Text("Local exato") },
                placeholder = { Text("Ex.: proximo ao ponto de hidratacao") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (hasLocationPermission(context)) {
                                lastKnownLocation(context)?.let {
                                    latitude = it.latitude
                                    longitude = it.longitude
                                }
                            } else {
                                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Usar minha localizacao")
                    }
                },
            )

            if (latitude != null && longitude != null) {
                Text(
                    "Coordenadas: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descricao e acao imediata") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // Evidencia fotografica
            Text("Evidencia fotografica", style = MaterialTheme.typography.labelMedium)
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos) { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }

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
                Text(if (photos.isEmpty()) "Adicionar foto" else "Adicionar outra foto")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGrid(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Usamos a ultima posicao conhecida em vez de pedir um fix novo: em campo o
 * operador nao pode esperar o GPS, e a precisao aproximada ja resolve para
 * localizar a ocorrencia dentro do perimetro do evento.
 */
@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
}