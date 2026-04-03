package com.example.finalapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.BleDevice
import com.example.finalapp.data.repository.BleRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleUiState(
    val scanning: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val selectedDevice: BleDevice? = null,
    val devices: List<BleDevice> = emptyList(),
    val statusTitle: String = "Device Disconnected",
    val statusSubtitle: String = "Ensure your device is turned on",
    val errorMessage: String? = null
)

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val bleRepository = BleRepository(application)

    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        when (val result = bleRepository.fetchBleStatus()) {
            is RepositoryResult.Success -> {
                val status = result.data
                _uiState.update {
                    it.copy(
                        scanning = false,
                        connecting = false,
                        connected = status.connected,
                        selectedDevice = if (status.connected && status.deviceName.isNotBlank()) {
                            BleDevice(
                                name = status.deviceName,
                                address = status.deviceAddress.orEmpty()
                            )
                        } else {
                            null
                        },
                        statusTitle = when {
                            !status.available -> "Bluetooth Unavailable"
                            status.connected -> "Connected to ${status.deviceName}"
                            else -> "Device Disconnected"
                        },
                        statusSubtitle = when {
                            !status.available -> "Turn on Bluetooth to scan for devices"
                            status.connected -> "Active and ready to sync"
                            else -> "Ensure your device is turned on"
                        },
                        errorMessage = null
                    )
                }
            }

            is RepositoryResult.Error -> {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun scanDevices() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    scanning = true,
                    connecting = false,
                    devices = emptyList(),
                    errorMessage = null,
                    statusTitle = "Scanning for Devices...",
                    statusSubtitle = "Looking for nearby BLE devices"
                )
            }

            when (val result = bleRepository.scanBleDevices(getApplication())) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            devices = result.data,
                            statusTitle = "Device Disconnected",
                            statusSubtitle = "Choose a device to connect",
                            errorMessage = null
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            devices = emptyList(),
                            errorMessage = result.message,
                            statusTitle = "Device Disconnected",
                            statusSubtitle = "Ensure your device is turned on"
                        )
                    }
                }
            }
        }
    }

    fun connectToDevice(device: BleDevice) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connecting = true,
                    errorMessage = null,
                    statusTitle = "Connecting to ${device.name}",
                    statusSubtitle = device.address
                )
            }

            when (val result = bleRepository.connectToDevice(getApplication(), device)) {
                is RepositoryResult.Success -> {
                    refreshStatus()
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            connecting = false,
                            connected = false,
                            selectedDevice = null,
                            errorMessage = result.message,
                            statusTitle = "Connection Failed",
                            statusSubtitle = "Choose a device to connect"
                        )
                    }
                }
            }
        }
    }

    fun onPermissionsDenied() {
        _uiState.update {
            it.copy(
                scanning = false,
                connecting = false,
                errorMessage = "Bluetooth permission is required to scan and connect.",
                statusTitle = "Permission Required",
                statusSubtitle = "Allow Bluetooth access to continue"
            )
        }
    }

    fun disconnect() {
        bleRepository.disconnect()
        refreshStatus()
    }
}
