package br.com.soe.campo.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Localizacao para registro operacional.
 *
 * Os dois modulos pedem a mesma coisa: coordenada quando o aparelho tiver
 * permissao e nada quando nao tiver — nunca bloquear o registro por causa do
 * GPS. A coleta e pontual, no momento do registro; o app nao rastreia
 * continuamente (modulo 11, secao 5.3).
 */
fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Usamos a ultima posicao conhecida em vez de pedir um fix novo: em campo o
 * operador nao pode esperar o GPS, e a precisao aproximada ja resolve para
 * localizar o registro dentro do perimetro do evento.
 */
@SuppressLint("MissingPermission")
fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
}

/** Par (latitude, longitude) do aparelho, ou (null, null) sem permissao. */
fun currentCoordinates(context: Context): Pair<Double?, Double?> {
    val location = lastKnownLocation(context) ?: return null to null
    return location.latitude to location.longitude
}
