package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.SettingsUpdateRequest

class SettingsRepository(
    private val apiService: ApiService = ApiClient.apiService
) {
    suspend fun fetchSettings(): RepositoryResult<AppSettings> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Success(SettingsStore.settings.value)

        return runCatching {
            apiService.getSettings("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                val remoteSettings = body?.settings
                if (response.isSuccessful && body?.success == true && remoteSettings != null) {
                    RepositoryResult.Success(
                        AppSettings(
                            language = remoteSettings.language,
                            notificationsEnabled = remoteSettings.pushNotificationsEnabled,
                            weeklyReportsEnabled = remoteSettings.weeklyReportsEnabled
                        )
                    )
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to load settings.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error while loading settings.")
            }
        )
    }

    suspend fun updateSettings(settings: AppSettings): RepositoryResult<AppSettings> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Success(settings)

        return runCatching {
            apiService.updateSettings(
                "Bearer $token",
                SettingsUpdateRequest(
                    language = settings.language,
                    pushNotificationsEnabled = settings.notificationsEnabled,
                    weeklyReportsEnabled = settings.weeklyReportsEnabled
                )
            )
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                val remoteSettings = body?.settings
                if (response.isSuccessful && body?.success == true && remoteSettings != null) {
                    RepositoryResult.Success(
                        AppSettings(
                            language = remoteSettings.language,
                            notificationsEnabled = remoteSettings.pushNotificationsEnabled,
                            weeklyReportsEnabled = remoteSettings.weeklyReportsEnabled
                        )
                    )
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to save settings.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error while saving settings.")
            }
        )
    }
}
