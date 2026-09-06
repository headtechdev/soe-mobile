package br.com.soe.campo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import br.com.soe.campo.SoeApplication
import br.com.soe.campo.data.SessionStore
import br.com.soe.campo.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado de sessao e sincronismo compartilhado por todas as telas.
 */
class SoeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as SoeApplication).container
    private val repository = container.fieldRepository

    val session: StateFlow<SessionStore.Session?> = container.sessionStore.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val syncing: Boolean = false,
        val lastSyncMessage: String? = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadAlerts: StateFlow<Int> = session
        .flatMapLatest { current ->
            val eventId = current?.eventId ?: return@flatMapLatest flowOf(0)
            repository.observeUnreadAlerts(eventId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingUploads: StateFlow<Int> = session
        .flatMapLatest { current ->
            val eventId = current?.eventId ?: return@flatMapLatest flowOf(0)
            repository.observePendingIncidents(eventId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pendingPhotos: StateFlow<Int> = repository.observePendingAttachments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lastSyncAt: StateFlow<Long?> = session
        .flatMapLatest { current ->
            val eventId = current?.eventId ?: return@flatMapLatest flowOf(null)
            repository.observeSyncState(eventId).map { it?.lastSyncAtMs }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun login(email: String, password: String, baseUrl: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = UiState(loading = true)
            try {
                if (!baseUrl.isNullOrBlank()) {
                    container.sessionStore.setBaseUrl(baseUrl)
                }
                repository.login(email, password)
                SyncWorker.schedulePeriodic(getApplication<Application>())
                SyncWorker.syncNow(getApplication<Application>())
                _uiState.value = UiState()
                onSuccess()
            } catch (error: Exception) {
                _uiState.value = UiState(error = friendlyMessage(error))
            }
        }
    }

    /** Sincroniza na hora e devolve o resultado para a tela. */
    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncing = true, error = null)
            try {
                val result = container.syncRepository.sync()
                val rejected = result.rejected.size
                _uiState.value = _uiState.value.copy(
                    syncing = false,
                    lastSyncMessage = buildString {
                        append("${result.pushed} enviado(s), ${result.pulled} recebido(s)")
                        if (rejected > 0) append(" · $rejected recusado(s)")
                    },
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    syncing = false,
                    error = friendlyMessage(error),
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, lastSyncMessage = null)
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            SyncWorker.cancelAll(getApplication<Application>())
            repository.logout()
            onDone()
        }
    }

    private fun friendlyMessage(error: Exception): String = when (error) {
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException,
        -> "Sem conexao com o servidor. Os registros ficam salvos no aparelho e sobem no proximo sincronismo."

        is retrofit2.HttpException -> when (error.code()) {
            401 -> "Credenciais invalidas ou sessao expirada."
            404 -> "Recurso nao encontrado no servidor."
            else -> "Erro do servidor (${error.code()})."
        }

        else -> error.message ?: "Falha inesperada."
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                return SoeViewModel(application) as T
            }
        }
    }
}
