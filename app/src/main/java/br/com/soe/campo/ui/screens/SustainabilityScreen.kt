package br.com.soe.campo.ui.screens

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
import br.com.soe.campo.data.local.SustainabilityTaskEntity
import br.com.soe.campo.ui.SoeViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tarefas de Direitos Humanos & Sustentabilidade (modulo 12, secao 10).
 *
 * A lista e por fase e tema, com o que e obrigatorio, vencido ou critico em
 * destaque — a orientacao de UX do modulo e justamente nao transformar isso
 * numa parede de indicadores.
 */
@Composable
fun SustainabilityScreen(
    viewModel: SoeViewModel,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository =
        (context.applicationContext as SoeApplication).container.sustainabilityRepository
    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventId = session?.eventId

    val tasks by remember(eventId) {
        eventId?.let { repository.observeTasks(it) } ?: MutableStateFlow(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val pending by viewModel.pendingUploads.collectAsStateWithLifecycle()

    var phase by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("") }
    var onlyOpen by remember { mutableStateOf(true) }

    val themes = remember(tasks) { tasks.map { it.theme }.distinct().sorted() }
    val now = System.currentTimeMillis()

    val visible = tasks.filter { task ->
        (phase.isBlank() || task.phase == phase) &&
            (theme.isBlank() || task.theme == theme) &&
            (!onlyOpen || (task.status != "DONE" && task.status != "NOT_APPLICABLE"))
    }

    Scaffold(
        topBar = {
            SoeTopBar(
                title = "DH e Sustentabilidade",
                subtitle = "${visible.size} de ${tasks.size} item(ns)",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pending > 0) {
                item { OfflineBanner(pendingCount = pending) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fase", style = MaterialTheme.typography.labelMedium)
                    ChipGrid(
                        options = listOf("" to "Todas") + SUSTAINABILITY_PHASES.toList(),
                        selected = phase,
                        onSelect = { phase = it },
                    )

                    if (themes.size > 1) {
                        Text("Tema", style = MaterialTheme.typography.labelMedium)
                        ChipGrid(
                            options = listOf("" to "Todos") + themes.map { it to it },
                            selected = theme,
                            onSelect = { theme = it },
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = onlyOpen, onCheckedChange = { onlyOpen = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Somente em aberto", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyMessage(
                        when {
                            eventId == null -> "Nenhum evento ativo. Sincronize na tela inicial."
                            tasks.isEmpty() -> "Nenhuma tarefa atribuida a voce neste evento."
                            else -> "Nada em aberto com os filtros atuais."
                        },
                    )
                }
            }

            items(visible, key = { it.id }) { task ->
                TaskCard(task = task, now = now, onClick = { onOpenTask(task.id) })
            }
        }
    }
}

@Composable
private fun TaskCard(task: SustainabilityTaskEntity, now: Long, onClick: () -> Unit) {
    val overdue = task.dueAtMs != null && task.dueAtMs < now &&
        task.status != "DONE" && task.status != "NOT_APPLICABLE"

    val statusColor = when (task.status) {
        "DONE" -> Color(0xFF059669)
        "NOT_DONE" -> MaterialTheme.colorScheme.error
        "IN_PROGRESS" -> Color(0xFF2563EB)
        "IN_VALIDATION" -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                listOfNotNull(task.theme, task.subtheme).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(task.title, style = MaterialTheme.typography.bodyLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (task.criterion == "REQUIRED") {
                    SoePill("Obrigatorio", MaterialTheme.colorScheme.error)
                } else {
                    SoePill(labelOf(SUSTAINABILITY_CRITERIA, task.criterion), MaterialTheme.colorScheme.primary)
                }
                if (task.priority == "CRITICAL" || task.priority == "HIGH") {
                    SoePill(labelOf(SUSTAINABILITY_PRIORITIES, task.priority), Color(0xFFD97706))
                }
                SoePill(labelOf(SUSTAINABILITY_STATUS, task.status), statusColor)
            }

            val footer = listOfNotNull(
                labelOf(SUSTAINABILITY_PHASES, task.phase),
                task.locationName,
                task.dueAtMs?.let { if (overdue) "Vencido em ${Time.dateTime(it)}" else "Prazo ${Time.dateTime(it)}" },
                if (task.recurring) "recorrente" else null,
                if (task.pendingSync) "na fila local" else null,
            ).joinToString(" · ")

            Text(
                footer,
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
