package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.NotificationDto
import com.example.finalapp.data.repository.NotificationsRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0,
    val unreadOnly: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
)

class NotificationsViewModel(
    private val repository: NotificationsRepository = NotificationsRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.fetchNotifications(_uiState.value.unreadOnly)) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            notifications = result.data.notifications,
                            unreadCount = result.data.unreadCount,
                            isLoading = false,
                            message = if (result.data.notifications.isEmpty()) "No notifications yet." else null,
                            errorMessage = null
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun toggleUnreadOnly(enabled: Boolean) {
        _uiState.update { it.copy(unreadOnly = enabled) }
        refresh()
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            when (val result = repository.markAsRead(notificationId)) {
                is RepositoryResult.Success -> refresh()
                is RepositoryResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            when (val result = repository.markAllAsRead()) {
                is RepositoryResult.Success -> refresh()
                is RepositoryResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteNotification(notificationId)) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        val updated = it.notifications.filterNot { notification -> notification.id == notificationId }
                        it.copy(notifications = updated)
                    }
                    refresh()
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }
}
