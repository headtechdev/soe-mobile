package br.com.soe.campo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import br.com.soe.campo.data.local.AlertEntity
import br.com.soe.campo.sync.SyncWorker
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Alertas recebidos do centro de comando: geral, por area e criticos. A
 * leitura e a confirmacao ficam na fila local e sobem no proximo sincronismo.
 */
@Composable
fun AlertsScreen(
    viewModel: SoeViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as SoeApplication).container.fieldRepository
    val scope = rememberCoroutineScope()

    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    val alerts by remember(eventId) {
        eventId?.let { repository.observeAlerts(it) } ?: MutableStateFlow(emptyList<AlertEntity>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            SoeTopBar(
                title = "Alertas",
                subtitle = "${alerts.count { it.readAtMs == null }} nao lido(s)",
                onBack = onBack,
            )
        },
    ) { padding ->
        if (alerts.isEmpty()) {
            EmptyMessage("Nenhum alerta recebido.", Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(alerts, key = { it.id }) { alert ->
                val levelColor = when (alert.level) {
                    "CRITICAL" -> MaterialTheme.colorScheme.error
                    "WARNING" -> Color(0xFFD97706)
                    else -> MaterialTheme.colorScheme.primary
                }
                val unread = alert.readAtMs == null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (unread) scope.launch { repository.markAlertRead(alert.id) }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (unread) {
                            levelColor.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SoePill(labelOf(ALERT_LEVELS, alert.level), levelColor)
                            Spacer(Modifier.weight(1f))
                            Text(
                                Time.relative(alert.sentAtMs ?: alert.updatedAtMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(alert.title, style = MaterialTheme.typography.titleMedium)
                        Text(alert.message, style = MaterialTheme.typography.bodyMedium)

                        // Quem supervisiona gente sem app responde tambem por
                        // ela: o repasse precisa estar explicito, senao a
                        // confirmacao de ciencia vira uma mentira parcial.
                        if (alert.relayCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Repasse a sua equipe: ${alert.relayCount} pessoa(s) sem app dependem de voce para receber este alerta.",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }

                        if (alert.requiresAck) {
                            if (alert.acknowledgedAtMs == null) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            repository.acknowledgeAlert(alert.id)
                                            SyncWorker.syncNow(context)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (alert.relayCount > 0) {
                                            "Confirmar ciencia minha e da equipe"
                                        } else {
                                            "Confirmar ciencia"
                                        },
                                    )
                                }
                            } else {
                                Text(
                                    "Ciencia confirmada em ${Time.dateTime(alert.acknowledgedAtMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
