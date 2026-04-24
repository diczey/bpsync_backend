package com.example.finalapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.BleDevice
import com.example.finalapp.data.repository.BleDeviceRole
import com.example.finalapp.data.repository.BleRepository
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.ui.localization.isTurkishSelected
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleUiState(
    val scanning: Boolean = false,
    val connecting: Boolean = false,
    val startingMeasurement: Boolean = false,
    val stoppingMeasurement: Boolean = false,
    val connected: Boolean = false,
    val streaming: Boolean = false,
    val readyToStart: Boolean = false,
    val wristConnected: Boolean = false,
    val chestConnected: Boolean = false,
    val wristDevice: BleDevice? = null,
    val chestDevice: BleDevice? = null,
    val devices: List<BleDevice> = emptyList(),
    val statusTitle: String = "",
    val statusSubtitle: String = "",
    val activeModelLabel: String = "Checking...",
    val modelMessage: String? = null,
    val errorMessage: String? = null,
    val framesReceived: Int = 0,
    val wristFramesReceived: Int = 0,
    val chestFramesReceived: Int = 0,
    val framesUploaded: Int = 0,
    val uploadFailures: Int = 0,
    val measurementsReady: Int = 0,
    val bufferFill: String = "0/25",
    val lastSeq: Int = 0,
    val lastBleError: String? = null,
    val lastMeasurement: String? = null
)

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val bleRepository = BleRepository(application)

    private fun t(english: String, turkish: String): String {
        return if (isTurkishSelected()) turkish else english
    }

    private val _uiState = MutableStateFlow(
        BleUiState(
            statusTitle = t("Devices Disconnected", "Cihazlar Bagli Degil"),
            statusSubtitle = t("Connect Wrist and Chest modules", "Wrist ve Chest modullerini bagla")
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
                applyBleStatus(result.data)
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
                    errorMessage = null,
                    statusTitle = t("Scanning for Devices...", "Cihazlar Taraniyor..."),
                    statusSubtitle = t(
                        "Looking for Wrist and Chest modules",
                        "Wrist ve Chest modulleri araniyor"
                    )
                )
            }

            when (val result = bleRepository.scanBleDevices(getApplication())) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            devices = result.data,
                            errorMessage = null
                        )
                    }
                    refreshStatus()
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            devices = emptyList(),
                            errorMessage = result.message,
                            statusTitle = t("Scan Failed", "Tarama Basarisiz"),
                            statusSubtitle = t(
                                "Make sure Bluetooth and both devices are on",
                                "Bluetooth ve iki cihazin da acik oldugundan emin ol"
                            )
                        )
                    }
                }
            }
        }
    }

    fun connectWrist() {
        connectByRole(BleDeviceRole.WRIST)
    }

    fun connectChest() {
        connectByRole(BleDeviceRole.CHEST)
    }

    fun stopMeasurement() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    stoppingMeasurement = true,
                    startingMeasurement = false,
                    errorMessage = null
                )
            }
            when (val result = bleRepository.stopMeasurement()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            stoppingMeasurement = false,
                            startingMeasurement = false,
                            errorMessage = null
                        )
                    }
                    refreshStatus()
                    // Wait for backend CNN inference to complete, then fetch results
                    launch {
                        delay(3500L)
                        ReadingRepository.syncFromApi()
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            stoppingMeasurement = false,
                            startingMeasurement = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun startMeasurement() {
        startMeasurementInternal()
    }

    fun onPermissionsDenied() {
        _uiState.update {
            it.copy(
                scanning = false,
                connecting = false,
                errorMessage = t(
                    "Bluetooth permission is required to scan and connect.",
                    "Tarama ve baglanti icin Bluetooth izni gereklidir."
                ),
                statusTitle = t("Permission Required", "Izin Gerekli"),
                statusSubtitle = t(
                    "Allow Bluetooth access to continue",
                    "Devam etmek icin Bluetooth erisimine izin ver"
                )
            )
        }
    }

    fun disconnect() {
        bleRepository.disconnect()
        refreshStatus()
    }

    private fun connectByRole(role: BleDeviceRole) {
        viewModelScope.launch {
            val existing = _uiState.value.devices.firstOrNull { it.role == role }
            val device = existing ?: scanForRole(role)

            if (device == null) {
                _uiState.update {
                    it.copy(
                        errorMessage = when (role) {
                            BleDeviceRole.WRIST -> t("No Wrist device was found.", "Wrist cihazi bulunamadi.")
                            BleDeviceRole.CHEST -> t("No Chest device was found.", "Chest cihazi bulunamadi.")
                            BleDeviceRole.UNKNOWN -> t("No device was found.", "Cihaz bulunamadi.")
                        }
                    )
                }
                return@launch
            }

            connectToResolvedDevice(device)
        }
    }

    private suspend fun scanForRole(role: BleDeviceRole): BleDevice? {
        _uiState.update {
            it.copy(
                scanning = true,
                connecting = false,
                errorMessage = null,
                statusTitle = when (role) {
                    BleDeviceRole.WRIST -> t("Scanning for Wrist...", "Wrist Araniyor...")
                    BleDeviceRole.CHEST -> t("Scanning for Chest...", "Chest Araniyor...")
                    BleDeviceRole.UNKNOWN -> t("Scanning for Devices...", "Cihazlar Taraniyor...")
                },
                statusSubtitle = t("Searching nearby BLE devices", "Yakindaki BLE cihazlari aranıyor")
            )
        }

        return when (val result = bleRepository.scanBleDevices(getApplication())) {
            is RepositoryResult.Success -> {
                _uiState.update {
                    it.copy(
                        scanning = false,
                        devices = result.data,
                        errorMessage = null
                    )
                }
                result.data.firstOrNull { it.role == role }
            }

            is RepositoryResult.Error -> {
                _uiState.update {
                    it.copy(
                        scanning = false,
                        devices = emptyList(),
                        errorMessage = result.message
                    )
                }
                null
            }
        }
    }

    private suspend fun connectToResolvedDevice(device: BleDevice) {
        _uiState.update {
            it.copy(
                connecting = true,
                errorMessage = null,
                statusTitle = t(
                    "Connecting to ${device.name}",
                    "${device.name} cihazina baglaniliyor"
                ),
                statusSubtitle = device.address
            )
        }

        when (val result = bleRepository.connectToDevice(getApplication(), device)) {
            is RepositoryResult.Success -> {
                _uiState.update { it.copy(errorMessage = null) }
                refreshStatus()
                refreshModelInfo()
            }

            is RepositoryResult.Error -> {
                _uiState.update {
                    it.copy(
                        connecting = false,
                        errorMessage = result.message,
                        statusTitle = t("Connection Failed", "Baglanti Basarisiz"),
                        statusSubtitle = t(
                            "Make sure the device is nearby and powered on",
                            "Cihazin yakin ve acik oldugundan emin ol"
                        )
                    )
                }
            }
        }
    }

    private fun startMeasurementInternal() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    startingMeasurement = true,
                    stoppingMeasurement = false,
                    errorMessage = null
                )
            }
            when (val result = bleRepository.startMeasurement()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            startingMeasurement = false,
                            stoppingMeasurement = false,
                            errorMessage = null
                        )
                    }
                    refreshStatus()
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            startingMeasurement = false,
                            stoppingMeasurement = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun refreshModelInfo() {
        viewModelScope.launch {
            try {
                val result = withTimeout(5000L) {
                    bleRepository.fetchPredictionModelInfo()
                }
                when (result) {
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
                                activeModelLabel = "CNN-LSTM",
                                modelMessage = null
                            )
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(activeModelLabel = "CNN-LSTM", modelMessage = null)
                }
            }
        }
    }

    private fun observeStatus() {
        viewModelScope.launch {
            bleRepository.observeBleStatus().collectLatest { status ->
                applyBleStatus(status)
            }
        }
    }

    private fun applyBleStatus(status: com.example.finalapp.data.model.BLEStatusResponse) {
        val dataStats = status.dataStats
        val readyToStart = status.wristConnected && status.chestConnected
        val streaming = status.streaming || dataStats.booleanValue("streaming")
        val lastBleError = dataStats.stringValueOrNull("last_error")

        _uiState.update {
            it.copy(
                scanning = false,
                connecting = false,
                connected = status.connected,
                streaming = streaming,
                readyToStart = readyToStart,
                startingMeasurement = if (streaming) false else it.startingMeasurement,
                stoppingMeasurement = if (!streaming) false else it.stoppingMeasurement,
                wristConnected = status.wristConnected,
                chestConnected = status.chestConnected,
                wristDevice = status.wristDeviceName?.let { name ->
                    BleDevice(name = name, address = status.wristDeviceAddress.orEmpty(), role = BleDeviceRole.WRIST)
                },
                chestDevice = status.chestDeviceName?.let { name ->
                    BleDevice(name = name, address = status.chestDeviceAddress.orEmpty(), role = BleDeviceRole.CHEST)
                },
                statusTitle = when {
                    !status.available -> t("Bluetooth Unavailable", "Bluetooth Kullanilamiyor")
                    streaming -> t("Measurement Started", "Olcum Basladi")
                    readyToStart -> t("Both Devices Connected", "Iki Cihaz Bagli")
                    status.wristConnected || status.chestConnected -> t("Connect the Other Device", "Diger Cihazi Bagla")
                    else -> t("Devices Disconnected", "Cihazlar Bagli Degil")
                },
                statusSubtitle = when {
                    !status.available -> t(
                        "Turn on Bluetooth to continue",
                        "Devam etmek icin Bluetooth'u ac"
                    )
                    streaming -> t(
                        "Wrist and Chest are streaming together",
                        "Wrist ve Chest birlikte veri gonderiyor"
                    )
                    readyToStart -> t(
                        "Both devices are ready. Press start to begin measurement",
                        "Iki cihaz hazir. Olcume baslamak icin baslat tusuna bas"
                    )
                    status.wristConnected || status.chestConnected -> t(
                        "One device is ready, now connect the other one",
                        "Bir cihaz hazir, simdi digerini bagla"
                    )
                    else -> t(
                        "Use the Wrist and Chest buttons below",
                        "Asagidaki Wrist ve Chest tuslarini kullan"
                    )
                },
                errorMessage = lastBleError,
                framesReceived = dataStats.intValue("frames_received"),
                wristFramesReceived = dataStats.intValue("wrist_frames_received"),
                chestFramesReceived = dataStats.intValue("chest_frames_received"),
                framesUploaded = dataStats.intValue("frames_uploaded"),
                uploadFailures = dataStats.intValue("upload_failures"),
                measurementsReady = dataStats.intValue("measurements_ready"),
                bufferFill = dataStats.stringValue("buffer_fill", "0/25"),
                lastSeq = dataStats.intValue("last_seq"),
                lastBleError = lastBleError,
                lastMeasurement = dataStats.stringValueOrNull("last_measurement")
            )
        }
    }
}

private fun Map<String, Any>.booleanValue(key: String): Boolean {
    return when (val value = this[key]) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true)
        else -> false
    }
}

private fun Map<String, Any>.intValue(key: String): Int {
    return when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }
}

private fun Map<String, Any>.stringValue(key: String, fallback: String): String {
    return stringValueOrNull(key) ?: fallback
}

private fun Map<String, Any>.stringValueOrNull(key: String): String? {
    return this[key]?.toString()?.takeIf { it.isNotBlank() }
}
