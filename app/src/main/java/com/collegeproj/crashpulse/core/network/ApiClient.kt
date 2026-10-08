package com.collegeproj.crashpulse.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.collegeproj.crashpulse.core.model.RefreshRequest
import com.collegeproj.crashpulse.core.storage.AppPreferences
import com.collegeproj.crashpulse.core.storage.SessionStore

class ApiClient(private val session: SessionStore, private val preferences: AppPreferences) {
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder().header("Accept", "application/json").header("X-Client", "CrashPulse-Android/0.1")
        session.accessToken?.let { request.header("Authorization", "Bearer $it") }
        chain.proceed(request.build())
    }
    private val refreshClient = OkHttpClient.Builder().build()
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator { _, response -> authenticate(response) }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private fun authenticate(response: okhttp3.Response): okhttp3.Request? {
            if (responseCount(response) > 1) return null
            val oldToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = session.accessToken
            if (oldToken != null && current != null && oldToken != current) return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            val refresh = session.refreshToken ?: return null
            val tokens = synchronized(session) {
                runCatching {
                    runBlocking { rawService().refresh(RefreshRequest(refresh)) }.body()
                }.getOrNull()
            } ?: run { session.clear(); return null }
            session.save(tokens.access_token, tokens.refresh_token)
            return response.request.newBuilder().header("Authorization", "Bearer ${tokens.access_token}").build()
    }

    fun service(): ApiService = Retrofit.Builder()
        .baseUrl(preferences.normalize(preferences.apiBaseUrl))
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(ApiService::class.java)

    private fun rawService(): ApiService = Retrofit.Builder()
        .baseUrl(preferences.normalize(preferences.apiBaseUrl))
        .client(refreshClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(ApiService::class.java)

    private fun responseCount(response: okhttp3.Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) { result++; prior = prior.priorResponse }
        return result
    }
}
