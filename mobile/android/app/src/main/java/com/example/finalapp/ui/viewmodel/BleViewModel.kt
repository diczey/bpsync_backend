package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.BleRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleUiState(
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val selectedDevice: String? = null,
    val devices: List<String> = emptyList(),
    val statusTitle: String = "Device Disconnected",
    val statusSubtitle: String = "Ensure your device is turned on",
    val errorMessage: String? = null
)

class BleViewModel(
    private val bleRepository: BleRepository = BleRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            when (val result = bleRepository.fetchBleStatus()) {
                is RepositoryResult.Success -> {
                    val status = result.data
                    _uiState.update {
                        it.copy(
                            connected = status.connected,
                            selectedDevice = status.deviceName.ifBlank { null },
                            statusTitle = if (status.connected) {
                                "Connected to ${status.deviceName}"
                            } else {
                                "Device Disconnected"
                            },
                            statusSubtitle = if (status.connected) {
                                "Active and ready to sync"
                            } else {
                                "Ensure your device is turned on"
                            },
                            errorMessage = null
                        )
                    }
                }

                is RepositoryResult.Error -> Unit
            }
        }
    }

    fun scanDevices() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    scanning = true,
                    devices = emptyList(),
                    errorMessage = null,
                    statusTitle = "Scanning for Devices..."
                )
            }

            delay(1200)

            when (val result = bleRepository.scanBleDevices()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            devices = result.data,
                            statusTitle = "Device Disconnected",
                            statusSubtitle = "Choose a device to connect"
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            errorMessage = result.message,
                            statusTitle = "Device Disconnected",
                            statusSubtitle = "Ensure your device is turned on"
                        )
                    }
                }
            }
        }
    }

    fun connectToDevice(name: String) {
        _uiState.update {
            it.copy(
                connected = true,
                selectedDevice = name,
                devices = emptyList(),
                statusTitle = "Connected to $name",
                statusSubtitle = "Active and ready to sync",
                errorMessage = null
            )
        }
        // Automatically start streaming data when user selects a device
        startStreaming()
    }

    fun startStreaming() {
        viewModelScope.launch {
            when (val result = bleRepository.startStreaming()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            statusSubtitle = "Streaming live data…",
                            errorMessage = null
                        )
                    }
                }
                is RepositoryResult.Error -> {
                    // Not critical — streaming may not be supported on the demo server
                    _uiState.update { it.copy(statusSubtitle = "Active and ready to sync") }
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bleRepository.stopStreaming()
        }
        _uiState.update {
            it.copy(
                connected = false,
                selectedDevice = null,
                devices = emptyList(),
                statusTitle = "Device Disconnected",
                statusSubtitle = "Ensure your device is turned on",
                errorMessage = null
            )
        }
    }
}
