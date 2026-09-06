package br.com.soe.campo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.Time
import br.com.soe.campo.data.local.ChecklistExecutionEntity
import br.com.soe.campo.data.local.ChecklistTemplateEntity
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun ChecklistsScreen(
    viewModel: SoeViewModel,
    onOpenExecution: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as SoeApplication).container.fieldRepository
    val scope = rememberCoroutineScope()

    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    val templates by remember(eventId) {
        eventId?.let { repository.observeTemplates(it) }
            ?: MutableStateFlow(emptyList<ChecklistTemplateEntity>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val executions by remember(eventId) {
        eventId?.let { repository.observeExecutions(it) }
            ?: MutableStateFlow(emptyList<ChecklistExecutionEntity>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val templateNames = remember(templates) { templates.associate { it.id to it.name } }

    Scaffold(
        topBar = { SoeTopBar("Checklists", subtitle = session?.areaName, onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Disponiveis para aplicar", style = MaterialTheme.typography.titleMedium)
            }

            if (templates.isEmpty()) {
                item {
                    EmptyMessage("Nenhum checklist liberado para o seu perfil. Sincronize para receber os modelos.")
                }
            }

            items(templates, key = { it.id }) { template ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val currentEvent = eventId ?: return@clickable
                            scope.launch {
                                val executionId = repository.startExecution(
                                    eventId = currentEvent,
                                    templateId = template.id,
                                    latitude = null,
                                    longitude = null,
                                )
                                onOpenExecution(executionId)
                            }
                        },
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(labelOf(PHASES, template.phase))
                                    if (template.requiresPhoto) append(" · exige foto")
                                    if (template.requiresGeo) append(" · exige local")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "Aplicar")
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text("Aplicacoes recentes", style = MaterialTheme.typography.titleMedium)
            }

            if (executions.isEmpty()) {
                item { EmptyMessage("Nenhuma aplicacao neste dispositivo.") }
            }

            items(executions, key = { it.id }) { execution ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenExecution(execution.id) },
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                templateNames[execution.templateId] ?: "Checklist",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "${labelOf(CHECKLIST_STATUS, execution.status)} · " +
                                    Time.relative(
                                        execution.completedAtMs ?: execution.startedAtMs,
                                    ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (execution.pendingSync) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Aguardando sincronizacao",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }

                        execution.score?.let { score ->
                            SoePill(
                                "$score%",
                                if (score >= 80) {
                                    androidx.compose.ui.graphics.Color(0xFF059669)
                                } else {
                                    androidx.compose.ui.graphics.Color(0xFFD97706)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
