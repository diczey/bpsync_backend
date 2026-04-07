package com.example.finalapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.BleDevice
import com.example.finalapp.data.repository.BleRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.ui.localization.isTurkishSelected
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleUiState(
    val scanning: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val streaming: Boolean = false,
    val selectedDevice: BleDevice? = null,
    val devices: List<BleDevice> = emptyList(),
    val statusTitle: String = "",
    val statusSubtitle: String = "",
    val framesReceived: Int = 0,
    val framesUploaded: Int = 0,
    val bufferFill: String = "0/25",
    val measurementsReady: Int = 0,
    val lastMeasurement: String? = null,
    val activeModelLabel: String = "Checking...",
    val modelMessage: String? = null,
    val errorMessage: String? = null
)

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val bleRepository = BleRepository(application)

    private fun t(english: String, turkish: String): String {
        return if (isTurkishSelected()) turkish else english
    }

    private val _uiState = MutableStateFlow(
        BleUiState(
            statusTitle = t("Device Disconnected", "Cihaz Bağlı Değil"),
            statusSubtitle = t("Ensure your device is turned on", "Cihazınızın açık olduğundan emin olun")
        )
    )
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    init {
        refreshModelInfo()
        observeStatus()
    }

    fun refreshStatus() {
        when (val result = bleRepository.fetchBleStatus()) {
            is RepositoryResult.Success -> {
                val status = result.data
                val dataStats = status.dataStats
                val streaming = dataStats["streaming"] as? Boolean ?: false
                val framesReceived = (dataStats["frames_received"] as? Number)?.toInt() ?: 0
                val framesUploaded = (dataStats["frames_uploaded"] as? Number)?.toInt() ?: 0
                val bufferFill = dataStats["buffer_fill"]?.toString() ?: "0/100"
                val measurementsReady = (dataStats["measurements_ready"] as? Number)?.toInt() ?: 0
                val lastMeasurement = dataStats["last_measurement"]?.toString()
                val backendError = dataStats["last_error"]?.toString()

                _uiState.update {
                    it.copy(
                        scanning = false,
                        connecting = false,
                        connected = status.connected,
                        streaming = streaming,
                        selectedDevice = if (status.connected && status.deviceName.isNotBlank()) {
                            BleDevice(
                                name = status.deviceName,
                                address = status.deviceAddress.orEmpty()
                            )
                        } else {
                            null
                        },
                        statusTitle = when {
                            !status.available -> t("Bluetooth Unavailable", "Bluetooth Kullanılamıyor")
                            status.connected && streaming -> t(
                                "Streaming from ${status.deviceName}",
                                "${status.deviceName} cihazından veri alınıyor"
                            )
                            status.connected -> t(
                                "Connected to ${status.deviceName}",
                                "${status.deviceName} cihazına bağlandı"
                            )
                            else -> t("Device Disconnected", "Cihaz Bağlı Değil")
                        },
                        statusSubtitle = when {
                            !status.available -> t(
                                "Turn on Bluetooth to scan for devices",
                                "Cihazları taramak için Bluetooth'u aç"
                            )
                            status.connected && streaming -> t(
                                "Frames: $framesReceived | Uploaded: $framesUploaded",
                                "Alınan kare: $framesReceived | Gönderilen: $framesUploaded"
                            )
                            status.connected -> t(
                                "Configuring notifications and sync",
                                "Bildirim ve senkronizasyon ayarlanıyor"
                            )
                            else -> t(
                                "Ensure your device is turned on",
                                "Cihazınızın açık olduğundan emin olun"
                            )
                        },
                        framesReceived = framesReceived,
                        framesUploaded = framesUploaded,
                        bufferFill = bufferFill,
                        measurementsReady = measurementsReady,
                        lastMeasurement = lastMeasurement,
                        errorMessage = backendError
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
                    statusTitle = t("Scanning for Devices...", "Cihazlar Taranıyor..."),
                    statusSubtitle = t(
                        "Looking for nearby BLE devices",
                        "Yakın BLE cihazları aranıyor"
                    )
                )
            }

            when (val result = bleRepository.scanBleDevices(getApplication())) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            devices = result.data,
                            statusTitle = t("Device Disconnected", "Cihaz Bağlı Değil"),
                            statusSubtitle = t(
                                "Choose a device to connect",
                                "Bağlanmak için bir cihaz seç"
                            ),
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
                            statusTitle = t("Device Disconnected", "Cihaz Bağlı Değil"),
                            statusSubtitle = t(
                                "Ensure your device is turned on",
                                "Cihazınızın açık olduğundan emin olun"
                            )
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
                    statusTitle = t(
                        "Connecting to ${device.name}",
                        "${device.name} cihazına bağlanılıyor"
                    ),
                    statusSubtitle = device.address
                )
            }

            when (val result = bleRepository.connectToDevice(getApplication(), device)) {
                is RepositoryResult.Success -> {
                    refreshStatus()
                    refreshModelInfo()
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            connecting = false,
                            connected = false,
                            selectedDevice = null,
                            errorMessage = result.message,
                            statusTitle = t("Connection Failed", "Bağlantı Başarısız"),
                            statusSubtitle = t(
                                "Choose a device to connect",
                                "Bağlanmak için bir cihaz seç"
                            )
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
                errorMessage = t(
                    "Bluetooth permission is required to scan and connect.",
                    "Tarama ve bağlantı için Bluetooth izni gereklidir."
                ),
                statusTitle = t("Permission Required", "İzin Gerekli"),
                statusSubtitle = t(
                    "Allow Bluetooth access to continue",
                    "Devam etmek için Bluetooth erişimine izin ver"
                )
            )
        }
    }

    fun disconnect() {
        bleRepository.disconnect()
        refreshStatus()
    }

    private fun refreshModelInfo() {
        viewModelScope.launch {
            when (val result = bleRepository.fetchPredictionModelInfo()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            activeModelLabel = result.data.activeModelLabel,
                            modelMessage = result.data.message
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            activeModelLabel = t("Unavailable", "Kullanilamiyor"),
                            modelMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun observeStatus() {
        viewModelScope.launch {
            while (true) {
                refreshStatus()
                delay(1_000L)
            }
        }
    }
}
