package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.NotificationDto

data class NotificationsPayload(
    val notifications: List<NotificationDto>,
    val unreadCount: Int,
)

class NotificationsRepository(
    private val apiService: ApiService = ApiClient.apiService
) {
    suspend fun fetchNotifications(unreadOnly: Boolean = false): RepositoryResult<NotificationsPayload> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.getNotifications("Bearer $token", unreadOnly = unreadOnly)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    RepositoryResult.Success(
                        NotificationsPayload(
                            notifications = body.notifications,
                            unreadCount = body.unreadCount
                        )
                    )
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to load notifications.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error while loading notifications.")
            }
        )
    }

    suspend fun markAsRead(notificationId: String): RepositoryResult<Unit> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return command { apiService.markNotificationAsRead("Bearer $token", notificationId) }
    }

    suspend fun markAllAsRead(): RepositoryResult<Unit> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return command { apiService.markAllNotificationsAsRead("Bearer $token") }
    }

    suspend fun deleteNotification(notificationId: String): RepositoryResult<Unit> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return command { apiService.deleteNotification("Bearer $token", notificationId) }
    }

    private suspend fun command(
        block: suspend () -> retrofit2.Response<com.example.finalapp.data.model.CommandResponse>
    ): RepositoryResult<Unit> {
        return runCatching { block() }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    RepositoryResult.Success(Unit)
                } else {
                    RepositoryResult.Error(body?.message ?: "Request failed.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error while updating notifications.")
            }
        )
    }
}
