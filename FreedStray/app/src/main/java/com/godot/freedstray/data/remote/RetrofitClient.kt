package com.godot.freedstray.data.remote

import com.godot.freedstray.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val DEFAULT_BASE_URL = "http://${BuildConfig.SERVER_HOST}:${BuildConfig.HTTP_PORT}/"

    private var currentBaseUrl = DEFAULT_BASE_URL
    var authToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    var apiService: ApiService = createApiService(DEFAULT_BASE_URL)
        private set

    fun updateBaseUrl(newHost: String) {
        var cleanHost = newHost.trim()
        if (!cleanHost.startsWith("http://") && !cleanHost.startsWith("https://")) {
            cleanHost = "http://$cleanHost"
        }
        if (!cleanHost.endsWith("/")) {
            cleanHost += "/"
        }
        currentBaseUrl = cleanHost
        apiService = createApiService(currentBaseUrl)
    }

    private fun createApiService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun getBearerToken(): String {
        val token = authToken ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }
}
