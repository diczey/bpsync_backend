package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.AuthRepository
import com.example.finalapp.data.repository.BleRepository
import com.example.finalapp.data.repository.DashboardRepository
import com.example.finalapp.data.repository.ProfileRepository
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val userName: String = "John Doe",
    val healthId: String = "BP2024",
    val systolic: String = "--",
    val diastolic: String = "--",
    val pulse: String = "--",
    val spo2: String = "--",
    val bleDeviceName: String = "BP Monitor Pro",
    val bleConnectedLabel: String = "Disconnected",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastCheckupDate: String? = null
)

class DashboardViewModel(
    private val dashboardRepository: DashboardRepository = DashboardRepository(),
    private val bleRepository: BleRepository = BleRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        hydrateFromSession()
        observeReadings()
        refreshDashboard()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            hydrateFromSession()
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (ReadingRepository.syncFromApi()) {
                is RepositoryResult.Success -> hydrateFromLocalReading()
                is RepositoryResult.Error -> Unit
            }

            when (val dashboardResult = dashboardRepository.fetchDashboardSummary()) {
                is RepositoryResult.Success -> {
                    val summary = dashboardResult.data
                    _uiState.update {
                        it.copy(
                            systolic = summary.latestSystolic.toString(),
                            diastolic = summary.latestDiastolic.toString(),
                            pulse = summary.latestHeartRate.toString(),
                            spo2 = summary.latestSpo2.toString(),
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (ReadingRepository.latestReading() == null) {
                                dashboardResult.message
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            when (val bleResult = bleRepository.fetchBleStatus()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            bleDeviceName = bleResult.data.deviceName.ifBlank { "BP Monitor Pro" },
                            bleConnectedLabel = if (bleResult.data.connected) {
                                "Connected"
                            } else {
                                "Disconnected"
                            }
                        )
                    }
                }

                is RepositoryResult.Error -> Unit
            }
        }
    }

    fun showLatestMeasurement() {
        refreshDashboard()
    }

    fun logout() {
        authRepository.logout()
    }

    fun updateLastCheckupDate(date: String) {
        val user = SessionStore.user.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = profileRepository.saveProfile(
                name = user.name,
                email = user.email,
                dateOfBirth = user.dateOfBirth ?: "",
                gender = user.gender ?: "",
                weight = user.weight ?: "",
                height = user.height ?: "",
                lastCheckupDate = date
            )
            if (result is RepositoryResult.Success) {
                _uiState.update { it.copy(lastCheckupDate = date, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to save date") }
            }
        }
    }

    private fun hydrateFromSession() {
        val user = SessionStore.user.value ?: return
        _uiState.update {
            it.copy(
                userName = user.name.ifBlank { "John Doe" },
                healthId = user.id.ifBlank { "BP2024" },
                lastCheckupDate = user.lastCheckupDate
            )
        }
    }

    private fun observeReadings() {
        viewModelScope.launch {
            ReadingRepository.readings.collect { readings ->
                val latest = readings.firstOrNull() ?: return@collect
                _uiState.update {
                    it.copy(
                        systolic = latest.systolic,
                        diastolic = latest.diastolic,
                        pulse = latest.pulse,
                        spo2 = latest.spo2
                    )
                }
            }
        }
    }
}
