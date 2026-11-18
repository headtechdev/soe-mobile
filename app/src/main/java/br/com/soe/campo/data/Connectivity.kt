package br.com.soe.campo.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Estado da rede do aparelho.
 *
 * O app inteiro trabalha offline por design — menos o login, que precisa
 * falar com o servidor para autenticar e descobrir a escala do dia. Checar
 * antes de tentar nos deixa dizer isso com todas as letras, em vez de
 * esperar o timeout e devolver uma falha de rede generica.
 */
object Connectivity {

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false

        // NET_CAPABILITY_VALIDATED e o que distingue "conectado ao wi-fi do
        // evento" de "conectado e com internet". Em rede local de evento, sem
        // saida para a internet, o servidor pode ser justamente o da rede —
        // por isso basta a conexao, nao a validacao.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Acompanha a rede enquanto a tela de login estiver aberta. */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline(context))
            }

            override fun onLost(network: Network) {
                trySend(isOnline(context))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(isOnline(context))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        trySend(isOnline(context))
        manager.registerNetworkCallback(request, callback)

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.conflate()
}

/** Login tentado sem rede. Tratada a parte para render uma mensagem propria. */
class OfflineLoginException : Exception(
    "Entrar exige conexao. Conecte o aparelho a rede do evento ou aos dados moveis e tente de novo.",
)
