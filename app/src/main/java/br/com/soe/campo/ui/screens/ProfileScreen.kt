package br.com.soe.campo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.Time
import br.com.soe.campo.data.local.PersonEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    viewModel: SoeViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as SoeApplication).container.fieldRepository
    val scope = rememberCoroutineScope()

    val session by viewModel.session.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val pending by viewModel.pendingUploads.collectAsStateWithLifecycle()
    val pendingPhotos by viewModel.pendingPhotos.collectAsStateWithLifecycle()

    val profile by remember(session?.userId) {
        session?.userId?.let { repository.observeMyProfile(it) } ?: MutableStateFlow<PersonEntity?>(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    var confirmLogout by remember { mutableStateOf(false) }
    val checkedIn = profile?.status == "CHECKED_IN"

    Scaffold(topBar = { SoeTopBar("Perfil", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        session?.userName ?: "-",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        session?.userEmail ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()

                    InfoRow("Evento", session?.eventName)
                    InfoRow("Area", session?.areaName)
                    InfoRow("Funcao", session?.roleName)
                    InfoRow("Turno", session?.shiftName)
                    InfoRow("Cracha", session?.badge)
                    InfoRow(
                        "Presenca",
                        when (profile?.status) {
                            "CHECKED_IN" -> "Em operacao desde ${Time.time(profile?.checkInAtMs)}"
                            "CHECKED_OUT" -> "Turno encerrado as ${Time.time(profile?.checkOutAtMs)}"
                            else -> profile?.status ?: "-"
                        },
                    )
                }
            }

            profile?.let { person ->
                Button(
                    onClick = {
                        scope.launch {
                            repository.togglePresence(person.id, !checkedIn)
                            SyncWorker.syncNow(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(if (checkedIn) "Registrar saida do turno" else "Registrar entrada no turno")
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sincronismo", style = MaterialTheme.typography.titleMedium)
                    InfoRow("Ultimo sincronismo", Time.full(lastSync))
                    InfoRow("Registros na fila", pending.toString())
                    InfoRow("Fotos na fila", pendingPhotos.toString())
                    InfoRow("Servidor", session?.baseUrl)

                    OutlinedButton(
                        onClick = { viewModel.syncNow() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sincronizar agora") }
                }
            }

            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sair da conta")
            }

            Text(
                "Sair apaga a sessao deste aparelho. Registros ainda nao sincronizados permanecem no banco local, mas so sobem apos novo login.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair da conta?") },
            text = {
                Text(
                    if (pending + pendingPhotos > 0) {
                        "Existem ${pending + pendingPhotos} item(ns) aguardando sincronizacao. Sincronize antes de sair para nao atrasar o comando."
                    } else {
                        "Voce precisara digitar e-mail e senha novamente para voltar a operar."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout(onLoggedOut)
                }) { Text("Sair") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
