package br.com.soe.campo

import android.app.Application
import android.content.Context
import br.com.soe.campo.data.FieldRepository
import br.com.soe.campo.data.SessionStore
import br.com.soe.campo.data.SyncRepository
import br.com.soe.campo.data.local.SoeDatabase
import br.com.soe.campo.data.remote.ApiFactory
import br.com.soe.campo.data.remote.SoeApi

class SoeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Injecao de dependencia manual. O app tem poucas dependencias e um unico
 * grafo, entao um container explicito e mais simples de ler do que um
 * framework de DI.
 */
class AppContainer(private val context: Context) {

    val database: SoeDatabase by lazy { SoeDatabase.get(context) }

    val sessionStore: SessionStore by lazy { SessionStore(context) }

    /**
     * A URL da API pode ser trocada na tela de login (cada evento costuma ter
     * um servidor diferente), entao o Retrofit e reconstruido quando ela muda.
     */
    private var cachedBaseUrl: String? = null
    private var cachedApi: SoeApi? = null

    suspend fun api(): SoeApi {
        val baseUrl = sessionStore.baseUrl(BuildConfig.DEFAULT_API_URL)
        val current = cachedApi
        if (current != null && cachedBaseUrl == baseUrl) return current

        val created = ApiFactory.create(
            baseUrl = baseUrl,
            tokenProvider = { sessionStore.tokenBlocking() },
            debug = BuildConfig.DEBUG,
        )
        cachedBaseUrl = baseUrl
        cachedApi = created
        return created
    }

    val fieldRepository: FieldRepository by lazy {
        FieldRepository(context, database, sessionStore) { api() }
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(database, sessionStore) { api() }
    }
}
