package com.example.finalapp.data.api

import android.os.Build
import com.example.finalapp.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
private fun isProbablyEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk", ignoreCase = true)
}

private fun resolveBaseUrl(): String {
    return if (BuildConfig.DEBUG && !isProbablyEmulator()) {
        BuildConfig.PHONE_DEBUG_API_BASE_URL.ensureTrailingSlash()
    } else {
        BuildConfig.API_BASE_URL.ensureTrailingSlash()
    }
}

object ApiClient {
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(resolveBaseUrl())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
