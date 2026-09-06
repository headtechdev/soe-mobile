package br.com.soe.campo.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Conversao entre o ISO-8601 UTC usado na API e o epoch em milissegundos
 * guardado no SQLite. Datas no banco local ficam sempre em UTC; a formatacao
 * para a tela usa o fuso do aparelho.
 */
object Time {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val isoNoMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val isoOffset = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    private val timeFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    private val dateTimeFmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
    private val fullFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    fun parse(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { iso.parse(value)?.time }.getOrNull()
            ?: runCatching { isoNoMillis.parse(value)?.time }.getOrNull()
            ?: runCatching { isoOffset.parse(value)?.time }.getOrNull()
    }

    fun parseOr(value: String?, fallback: Long): Long = parse(value) ?: fallback

    fun toIso(millis: Long): String = iso.format(Date(millis))

    fun toIsoOrNull(millis: Long?): String? = millis?.let { toIso(it) }

    fun time(millis: Long?): String = millis?.let { timeFmt.format(Date(it)) } ?: "--:--"

    fun dateTime(millis: Long?): String = millis?.let { dateTimeFmt.format(Date(it)) } ?: "-"

    fun full(millis: Long?): String = millis?.let { fullFmt.format(Date(it)) } ?: "-"

    /** "agora", "ha 12 min", "ha 3 h", "ha 2 d" — usado nas listas. */
    fun relative(millis: Long?): String {
        if (millis == null) return "-"
        val diff = System.currentTimeMillis() - millis
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "agora"
            minutes < 60 -> "ha $minutes min"
            minutes < 1440 -> "ha ${minutes / 60} h"
            else -> "ha ${minutes / 1440} d"
        }
    }
}
