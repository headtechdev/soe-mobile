package br.com.soe.campo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta alinhada ao Web SaaS: azul de comando, vermelho de criticidade,
// verde de conformidade. Contraste alto porque o app e usado ao ar livre.
private val CommandBlue = Color(0xFF2563EB)
private val CommandNavy = Color(0xFF0B1220)
private val Critical = Color(0xFFDC2626)
private val Conform = Color(0xFF059669)
private val Warning = Color(0xFFD97706)

val SeverityColors = mapOf(
    "LOW" to Conform,
    "MEDIUM" to Warning,
    "HIGH" to Color(0xFFEA580C),
    "CRITICAL" to Critical,
)

val StatusColors = mapOf(
    "OPEN" to Critical,
    "ASSIGNED" to Warning,
    "IN_PROGRESS" to CommandBlue,
    "RESOLVED" to Conform,
    "CLOSED" to Color(0xFF64748B),
    "CANCELLED" to Color(0xFF94A3B8),
)

private val LightColors = lightColorScheme(
    primary = CommandBlue,
    onPrimary = Color.White,
    secondary = CommandNavy,
    onSecondary = Color.White,
    error = Critical,
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
    background = CommandNavy,
    surface = Color(0xFF16203A),
    onSurface = Color(0xFFE2E8F0),
)

private val SoeTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SoeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SoeTypography,
        content = content,
    )
}
