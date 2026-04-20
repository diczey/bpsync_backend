package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.AppSettings
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.SettingsRepository
import com.example.finalapp.data.repository.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository = SettingsRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeLocalSettings()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.fetchSettings()) {
                is RepositoryResult.Success -> {
                    SettingsStore.replace(result.data)
                    _uiState.update {
                        it.copy(isLoading = false, message = "Settings synced.", errorMessage = null)
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

    fun setLanguage(language: String) {
        SettingsStore.setLanguage(language)
        saveCurrentSettings()
    }

    fun setPushNotifications(enabled: Boolean) {
        SettingsStore.setPushNotifications(enabled)
        saveCurrentSettings()
    }

    fun setWeeklyReports(enabled: Boolean) {
        SettingsStore.setWeeklyReports(enabled)
        saveCurrentSettings()
    }

    private fun observeLocalSettings() {
        viewModelScope.launch {
            SettingsStore.settings.collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun saveCurrentSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null, errorMessage = null) }
            when (val result = repository.updateSettings(SettingsStore.settings.value)) {
                is RepositoryResult.Success -> {
                    SettingsStore.replace(result.data)
                    _uiState.update {
                        it.copy(isSaving = false, message = "Settings saved.", errorMessage = null)
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = result.message)
                    }
                }
            }
        }
    }
}
