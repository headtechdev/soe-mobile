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
