package br.com.soe.campo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "soe_session")

/**
 * Sessao persistente do app: o token sobrevive ao fechamento do aplicativo,
 * que e o que permite o "login persistente" e o uso em modo offline — o app
 * abre direto na tela inicial mesmo sem rede.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_ROLE = stringPreferencesKey("user_role")
        val EVENT_ID = stringPreferencesKey("event_id")
        val EVENT_NAME = stringPreferencesKey("event_name")
        val PERSON_ID = stringPreferencesKey("person_id")
        val AREA_NAME = stringPreferencesKey("area_name")
        val ROLE_NAME = stringPreferencesKey("role_name")
        val SHIFT_NAME = stringPreferencesKey("shift_name")
        val BADGE = stringPreferencesKey("badge")
        val BASE_URL = stringPreferencesKey("base_url")
        val INSTALLATION = stringPreferencesKey("installation_id")
        val OFFLINE = booleanPreferencesKey("offline_mode")
    }

    data class Session(
        val token: String?,
        val userId: String?,
        val userName: String?,
        val userEmail: String?,
        val userRole: String?,
        val eventId: String?,
        val eventName: String?,
        val personId: String?,
        val areaName: String?,
        val roleName: String?,
        val shiftName: String?,
        val badge: String?,
        val baseUrl: String?,
    ) {
        val isLoggedIn: Boolean get() = !token.isNullOrBlank()
    }

    val session: Flow<Session> = context.dataStore.data.map { it.toSession() }

    private fun Preferences.toSession() = Session(
        token = this[Keys.TOKEN],
        userId = this[Keys.USER_ID],
        userName = this[Keys.USER_NAME],
        userEmail = this[Keys.USER_EMAIL],
        userRole = this[Keys.USER_ROLE],
        eventId = this[Keys.EVENT_ID],
        eventName = this[Keys.EVENT_NAME],
        personId = this[Keys.PERSON_ID],
        areaName = this[Keys.AREA_NAME],
        roleName = this[Keys.ROLE_NAME],
        shiftName = this[Keys.SHIFT_NAME],
        badge = this[Keys.BADGE],
        baseUrl = this[Keys.BASE_URL],
    )

    suspend fun current(): Session = session.first()

    /**
     * Lido de forma sincrona pelo interceptor do OkHttp, que roda fora de
     * corrotina. O DataStore mantem o valor em memoria apos a primeira
     * leitura, entao o custo e desprezivel.
     */
    fun tokenBlocking(): String? = runBlocking { current().token }

    suspend fun baseUrl(fallback: String): String = current().baseUrl ?: fallback

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url.trim() }
    }

    /** Id estavel do dispositivo, gerado na primeira execucao. */
    suspend fun installationId(): String {
        val existing = context.dataStore.data.first()[Keys.INSTALLATION]
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.INSTALLATION] = generated }
        return generated
    }

    suspend fun saveLogin(
        token: String,
        userId: String,
        userName: String,
        userEmail: String,
        userRole: String,
    ) {
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.USER_ID] = userId
            it[Keys.USER_NAME] = userName
            it[Keys.USER_EMAIL] = userEmail
            it[Keys.USER_ROLE] = userRole
        }
    }

    suspend fun saveAssignment(
        eventId: String,
        eventName: String,
        personId: String?,
        areaName: String?,
        roleName: String?,
        shiftName: String?,
        badge: String?,
    ) {
        context.dataStore.edit {
            it[Keys.EVENT_ID] = eventId
            it[Keys.EVENT_NAME] = eventName
            personId?.let { value -> it[Keys.PERSON_ID] = value }
            areaName?.let { value -> it[Keys.AREA_NAME] = value }
            roleName?.let { value -> it[Keys.ROLE_NAME] = value }
            shiftName?.let { value -> it[Keys.SHIFT_NAME] = value }
            badge?.let { value -> it[Keys.BADGE] = value }
        }
    }

    suspend fun setOffline(offline: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE] = offline }
    }

    /** Encerra a sessao preservando baseUrl e installationId do aparelho. */
    suspend fun logout() {
        context.dataStore.edit { prefs ->
            val baseUrl = prefs[Keys.BASE_URL]
            val installation = prefs[Keys.INSTALLATION]
            prefs.clear()
            baseUrl?.let { prefs[Keys.BASE_URL] = it }
            installation?.let { prefs[Keys.INSTALLATION] = it }
        }
    }
}
