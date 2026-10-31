package br.com.soe.campo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.soe.campo.ui.theme.SeverityColors
import br.com.soe.campo.ui.theme.StatusColors

// Rotulos PT-BR espelhando os enums do Web SaaS.

val INCIDENT_TYPES = listOf(
    "MEDICAL" to "Medico",
    "SECURITY" to "Seguranca",
    "FIRE" to "Incendio",
    "STRUCTURAL" to "Estrutural",
    "ELECTRICAL" to "Eletrica",
    "CLIMATE" to "Clima",
    "CROWD" to "Publico / Multidao",
    "TECHNICAL" to "Tecnico (A/V/L)",
    "LOGISTICS" to "Logistica",
    "CLEANING" to "Limpeza",
    "OTHER" to "Outro",
)

val SEVERITIES = listOf(
    "LOW" to "Baixa",
    "MEDIUM" to "Media",
    "HIGH" to "Alta",
    "CRITICAL" to "Critica",
)

val INCIDENT_STATUS = mapOf(
    "OPEN" to "Aberto",
    "ASSIGNED" to "Atribuido",
    "IN_PROGRESS" to "Em atendimento",
    "RESOLVED" to "Resolvido",
    "CLOSED" to "Encerrado",
    "CANCELLED" to "Cancelado",
)

val CHECKLIST_STATUS = mapOf(
    "PENDING" to "Pendente",
    "IN_PROGRESS" to "Em execucao",
    "COMPLETED" to "Concluido",
    "CANCELLED" to "Cancelado",
)

val PHASES = mapOf(
    "LOAD_IN" to "Montagem",
    "LIVE" to "Evento",
    "LOAD_OUT" to "Desmontagem",
)

val ALERT_LEVELS = mapOf(
    "INFO" to "Informativo",
    "WARNING" to "Atencao",
    "CRITICAL" to "Critico",
)

// -------------------------------------------- Logistica & Mobilidade

val TRIP_SERVICES = mapOf(
    "SHUTTLE" to "Shuttle",
    "TRANSFER" to "Transfer",
    "FLEET" to "Frota",
)

val TRIP_STATUS = mapOf(
    "SCHEDULED" to "Programada",
    "CHECKED_IN" to "Check-in feito",
    "IN_PROGRESS" to "Em viagem",
    "COMPLETED" to "Encerrada",
    "CANCELLED" to "Cancelada",
)

val PASSENGER_STATUS = mapOf(
    "EXPECTED" to "Esperado",
    "BOARDED" to "Embarcado",
    "DROPPED" to "Desembarcado",
    "NO_SHOW" to "Ausente",
    "EXTRA" to "Adicional",
    "REFUSED" to "Recusou embarque",
)

/** Categorias de ocorrencia da secao 4.7 (trafego, veiculo, passageiro...). */
val MOBILITY_INCIDENT_TYPES = listOf(
    "LOGISTICS" to "Transito / via",
    "TECHNICAL" to "Veiculo (pane, avaria)",
    "CROWD" to "Passageiro",
    "SECURITY" to "Seguranca",
    "MEDICAL" to "Emergencia medica",
    "OTHER" to "Operacao / outro",
)

// ------------------------------ Direitos Humanos & Sustentabilidade

val SUSTAINABILITY_PHASES = mapOf(
    "PRE_EVENT" to "Pre-evento",
    "LIVE" to "Durante o evento",
    "POST_EVENT" to "Pos-evento",
)

val SUSTAINABILITY_CRITERIA = mapOf(
    "REQUIRED" to "Obrigatorio",
    "RECOMMENDED" to "Recomendado",
    "NOT_APPLICABLE" to "Nao aplicavel",
)

val SUSTAINABILITY_PRIORITIES = mapOf(
    "CRITICAL" to "Critica",
    "HIGH" to "Alta",
    "MEDIUM" to "Media",
    "LOW" to "Baixa",
)

val SUSTAINABILITY_STATUS = mapOf(
    "NOT_STARTED" to "Nao iniciado",
    "IN_PROGRESS" to "Em andamento",
    "DONE" to "Atendido",
    "NOT_DONE" to "Nao atendido",
    "NOT_APPLICABLE" to "Nao aplicavel",
    "IN_VALIDATION" to "Em validacao",
)

/** Respostas que o app pode registrar; "em validacao" so vem do servidor. */
val SUSTAINABILITY_ANSWERS = listOf(
    "DONE" to "Atendido",
    "IN_PROGRESS" to "Em andamento",
    "NOT_DONE" to "Nao atendido",
    "NOT_APPLICABLE" to "Nao aplicavel",
)

fun labelOf(map: Map<String, String>, key: String?): String =
    key?.let { map[it] ?: it } ?: "-"

fun labelOf(pairs: List<Pair<String, String>>, key: String?): String =
    key?.let { value -> pairs.firstOrNull { it.first == value }?.second ?: value } ?: "-"

fun severityColor(severity: String?): Color =
    SeverityColors[severity] ?: Color(0xFF64748B)

fun statusColor(status: String?): Color =
    StatusColors[status] ?: Color(0xFF64748B)

@Composable
fun SoePill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoeTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = actions,
    )
}

/** Grade de escolha unica. Nasceu no registro de ocorrencia e virou padrao. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGrid(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
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
                enabled = enabled,
                label = { Text(label) },
            )
        }
    }
}

/**
 * Linha rotulo/valor dos cabecalhos de missao e tarefa. Em campo o operador
 * le de relance, entao o rotulo fica pequeno e o valor em destaque.
 */
@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 92.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Banner que avisa o operador de que existem registros na fila local. */
@Composable
fun OfflineBanner(pendingCount: Int, modifier: Modifier = Modifier) {
    if (pendingCount <= 0) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "$pendingCount registro(s) aguardando sincronizacao",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
