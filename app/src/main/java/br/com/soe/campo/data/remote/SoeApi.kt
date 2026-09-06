package br.com.soe.campo.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.serialization.json.Json
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SoeApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/sync/bootstrap")
    suspend fun bootstrap(): BootstrapResponse

    @GET("api/sync/pull")
    suspend fun pull(
        @Query("eventId") eventId: String,
        @Query("since") since: String?,
        @Query("installationId") installationId: String?,
    ): PullResponse

    @POST("api/sync/push")
    suspend fun push(@Body body: PushRequest): PushResponse
}

/**
 * Constroi o cliente HTTP. O token do login persistente e injetado por um
 * interceptor, entao nenhuma tela precisa se preocupar com autenticacao.
 */
object ApiFactory {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun create(
        baseUrl: String,
        tokenProvider: () -> String?,
        debug: Boolean,
    ): SoeApi {
        val auth = Interceptor { chain ->
            val token = tokenProvider()
            val request = if (token.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            }
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .apply {
                if (debug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            // Uploads de evidencia em rede de evento sao lentos; o timeout
            // generoso evita perder o lote por variacao de sinal.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SoeApi::class.java)
    }
}
