package br.com.soe.campo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.local.TripEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.launch

/**
 * Botao SOS (modulo 11, secao 4.7).
 *
 * Abre uma ocorrencia critica em dois toques, com a localizacao disponivel e
 * o vinculo da missao, veiculo e passageiros embarcados quando o acionamento
 * parte de uma viagem. O registro vai para a fila local como qualquer outro:
 * acionar o SOS sem rede nao pode falhar.
 *
 * O app nao aciona servico publico — ele registra e sobe para o Centro de
 * Comando, que dispara o protocolo do evento.
 */
@Composable
fun SosDialog(
    viewModel: SoeViewModel,
    onDismiss: () -> Unit,
    trip: TripEntity? = null,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as SoeApplication).container
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    var type by remember { mutableStateOf("MEDICAL") }
    var description by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = {
            Text(if (sent) "SOS registrado" else "Acionar SOS")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sent) {
                    Text(
                        "A ocorrencia critica foi gravada e sobe para o Centro de Comando no " +
                            "proximo sincronismo. Siga o protocolo do evento para acionamento " +
                            "de servicos de emergencia.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Cria uma ocorrencia critica com sua localizacao" +
                            (trip?.let { ", vinculada a missao ${it.code}" } ?: "") + ".",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Text("Categoria", style = MaterialTheme.typography.labelMedium)
                    ChipGrid(
                        options = MOBILITY_INCIDENT_TYPES,
                        selected = type,
                        onSelect = { type = it },
                        enabled = !sending,
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("O que esta acontecendo") },
                        minLines = 2,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("Fechar") }
            } else {
                Button(
                    enabled = !sending && eventId != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        val currentEvent = eventId ?: return@Button
                        sending = true
                        scope.launch {
                            val (latitude, longitude) = currentCoordinates(context)

                            // O corpo carrega o contexto operacional porque o
                            // modelo de ocorrencia do SOE nao tem campo de
                            // missao: sem isso o comando recebe um SOS solto.
                            val summary = buildString {
                                append("SOS acionado pelo app.")
                                trip?.let {
                                    append(" Missao ${it.code}")
                                    it.routeName?.let { route -> append(" · $route") }
                                    it.vehicleId?.let { append(" · veiculo vinculado") }
                                }
                                session?.userName?.let { append(" · Operador: $it") }
                                if (description.isNotBlank()) append("\n${description.trim()}")
                            }

                            container.fieldRepository.reportIncident(
                                eventId = currentEvent,
                                title = "SOS · ${labelOf(MOBILITY_INCIDENT_TYPES, type)}",
                                description = summary,
                                type = type,
                                severity = "CRITICAL",
                                areaId = null,
                                locationDescription = trip?.routeName,
                                latitude = latitude,
                                longitude = longitude,
                            )
                            SyncWorker.syncNow(context)
                            sending = false
                            sent = true
                        }
                    },
                ) {
                    Text(if (sending) "Registrando..." else "Acionar SOS")
                }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancelar") }
            }
        },
    )
}
