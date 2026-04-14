package com.example.finalapp.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.model.BLEStatusResponse
import com.example.finalapp.data.model.BleFrameUploadRequest
import com.example.finalapp.data.model.BleSessionDeviceRequest
import com.example.finalapp.data.model.PredictionModelInfoResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject

enum class BleDeviceRole {
    WRIST,
    CHEST,
    UNKNOWN
}

private enum class BleUploadTarget {
    MERGED,
    WRIST,
    CHEST
}

data class BleDevice(
    val name: String,
    val address: String,
    val role: BleDeviceRole = BleDeviceRole.UNKNOWN
)

private data class DeviceSession(
    val role: BleDeviceRole,
    val serviceUuid: UUID,
    val dataCharUuid: UUID,
    val cmdCharUuid: UUID,
    var gatt: BluetoothGatt? = null,
    var dataCharacteristic: BluetoothGattCharacteristic? = null,
    var commandCharacteristic: BluetoothGattCharacteristic? = null,
    var device: BleDevice? = null,
    var connectedAt: String? = null,
    var mtu: Int = 23
) {
    val isConnected: Boolean
        get() = gatt != null && device != null
}

private object LocalBleStateStore {
    var status: BLEStatusResponse = BLEStatusResponse(
        available = false,
        connected = false,
        deviceName = "",
        deviceAddress = null,
        connectedAt = null,
        dataStats = emptyMap(),
        wristConnected = false,
        chestConnected = false,
        wristDeviceName = null,
        wristDeviceAddress = null,
        chestDeviceName = null,
        chestDeviceAddress = null,
        streaming = false
    )
        private set

    fun update(status: BLEStatusResponse) {
        this.status = status
    }
}

private object AndroidBleManager {
    private const val SCAN_DURATION_MS = 6_000L
    private const val CONNECTION_TIMEOUT_MS = 10_000L
    private const val SOURCE_TAG = "android-dual-ble"
    private const val DESIRED_MTU = 247
    private const val WINDOW_SIZE = 25
    private const val MAX_BUFFERED_FRAMES = 80
    private const val CHEST_PACKET_SIZE = 24
    private const val CHEST_ECG_BATCH_SIZE = 10

    private val WRIST_SERVICE_UUID: UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
    private val WRIST_DATA_UUID: UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
    private val WRIST_CMD_UUID: UUID = UUID.fromString("19B10002-E8F2-537E-4F6C-D104768A1214")

    private val CHEST_SERVICE_UUID: UUID = UUID.fromString("29B10000-E8F2-537E-4F6C-D104768A1214")
    private val CHEST_DATA_UUID: UUID = UUID.fromString("29B10001-E8F2-537E-4F6C-D104768A1214")
    private val CHEST_CMD_UUID: UUID = UUID.fromString("29B10002-E8F2-537E-4F6C-D104768A1214")

    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessions = linkedMapOf(
        BleDeviceRole.WRIST to DeviceSession(
            role = BleDeviceRole.WRIST,
            serviceUuid = WRIST_SERVICE_UUID,
            dataCharUuid = WRIST_DATA_UUID,
            cmdCharUuid = WRIST_CMD_UUID
        ),
        BleDeviceRole.CHEST to DeviceSession(
            role = BleDeviceRole.CHEST,
            serviceUuid = CHEST_SERVICE_UUID,
            dataCharUuid = CHEST_DATA_UUID,
            cmdCharUuid = CHEST_CMD_UUID
        )
    )

    private val wristFrameBuffer = LinkedHashMap<Int, JSONObject>()
    private val chestFrameBuffer = LinkedHashMap<Int, JSONObject>()

    private var appContext: Context? = null
    private var activeScanCallback: ScanCallback? = null

    private var measurementStreaming = false
    private var framesReceived = 0
    private var wristFramesReceived = 0
    private var chestFramesReceived = 0
    private var framesUploaded = 0
    private var uploadFailures = 0
    private var measurementsReady = 0
    private var lastMeasurement: String? = null
    private var lastSeq = 0
    private var lastError: String? = null
    private var backendBufferFill = "0/$WINDOW_SIZE"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        publishStatus()
    }

    fun currentStatus(): BLEStatusResponse {
        publishStatus()
        return LocalBleStateStore.status
    }

    @SuppressLint("MissingPermission")
    suspend fun scanDevices(context: Context): RepositoryResult<List<BleDevice>> {
        initialize(context)

        val adapter = bluetoothAdapter()
            ?: return RepositoryResult.Error("This device does not support Bluetooth LE.")

        if (!adapter.isEnabled) {
            publishStatus()
            return RepositoryResult.Error("Bluetooth is turned off. Turn it on and try again.")
        }

        val scanner = adapter.bluetoothLeScanner
            ?: return RepositoryResult.Error("Bluetooth scanner is unavailable on this device.")

        activeScanCallback?.let { callback ->
            runCatching { scanner.stopScan(callback) }
            activeScanCallback = null
        }

        return suspendCancellableCoroutine { continuation ->
            val discovered = LinkedHashMap<String, BleDevice>()
            var completed = false
            lateinit var callback: ScanCallback

            fun finish(result: RepositoryResult<List<BleDevice>>) {
                if (completed) return
                completed = true
                mainHandler.removeCallbacksAndMessages(scanner)
                runCatching { scanner.stopScan(callback) }
                activeScanCallback = null
                continuation.resume(result)
            }

            callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val name = device.name ?: result.scanRecord?.deviceName ?: "Unnamed BLE Device"
                    discovered[device.address] = BleDevice(
                        name = name,
                        address = device.address,
                        role = classifyRole(name, result)
                    )
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { result ->
                        val device = result.device ?: return@forEach
                        val name = device.name ?: result.scanRecord?.deviceName ?: "Unnamed BLE Device"
                        discovered[device.address] = BleDevice(
                            name = name,
                            address = device.address,
                            role = classifyRole(name, result)
                        )
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    finish(RepositoryResult.Error(scanFailureMessage(errorCode)))
                }
            }

            activeScanCallback = callback
            scanner.startScan(callback)

            val timeoutRunnable = Runnable {
                val devices = discovered.values
                    .sortedWith(
                        compareBy<BleDevice>({ roleRank(it.role) }, { it.name.lowercase(Locale.US) }, { it.address })
                    )
                if (devices.isEmpty()) {
                    finish(RepositoryResult.Error("No Bluetooth LE devices found nearby."))
                } else {
                    finish(RepositoryResult.Success(devices))
                }
            }

            mainHandler.postAtTime(timeoutRunnable, scanner, SystemClock.uptimeMillis() + SCAN_DURATION_MS)

            continuation.invokeOnCancellation {
                mainHandler.removeCallbacksAndMessages(scanner)
                runCatching { scanner.stopScan(callback) }
                activeScanCallback = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, device: BleDevice): RepositoryResult<String> {
        initialize(context)

        if (device.role == BleDeviceRole.UNKNOWN) {
            return RepositoryResult.Error("Please choose a Wrist or Chest device.")
        }

        val adapter = bluetoothAdapter()
            ?: return RepositoryResult.Error("This device does not support Bluetooth LE.")

        if (!adapter.isEnabled) {
            publishStatus()
            return RepositoryResult.Error("Bluetooth is turned off. Turn it on and try again.")
        }

        val remoteDevice = runCatching { adapter.getRemoteDevice(device.address) }.getOrNull()
            ?: return RepositoryResult.Error("Couldn't resolve the selected Bluetooth device.")

        disconnectSession(device.role, clearMeasurementState = false)

        val session = sessions.getValue(device.role)

        return suspendCancellableCoroutine { continuation ->
            var completed = false
            lateinit var gatt: BluetoothGatt

            fun finish(result: RepositoryResult<String>) {
                if (completed) return
                completed = true
                mainHandler.removeCallbacksAndMessages(device.address)
                continuation.resume(result)
            }

            val timeoutRunnable = Runnable {
                runCatching {
                    gatt.disconnect()
                    gatt.close()
                }
                if (session.gatt == gatt) {
                    clearSession(session)
                }
                if (!bothDevicesConnected()) {
                    measurementStreaming = false
                }
                publishStatus()
                finish(RepositoryResult.Error("Connection timed out. Try again closer to the device."))
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gattInstance: BluetoothGatt, status: Int, newState: Int) {
                    when {
                        status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                            session.gatt = gattInstance
                            session.device = device
                            session.connectedAt = currentTimestamp()
                            session.mtu = 23
                            publishStatus()
                            scope.launch {
                                notifyMobileConnected(device)
                            }
                            finish(RepositoryResult.Success("Connected to ${device.name}"))

                            runCatching {
                                gattInstance.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            }

                            val mtuRequested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                runCatching { gattInstance.requestMtu(DESIRED_MTU) }.getOrDefault(false)
                            } else {
                                false
                            }

                            if (!mtuRequested) {
                                runCatching { gattInstance.discoverServices() }
                            }
                        }

                        newState == BluetoothProfile.STATE_DISCONNECTED -> {
                            runCatching { gattInstance.close() }
                            if (session.gatt == gattInstance) {
                                clearSession(session)
                            }
                            if (!bothDevicesConnected()) {
                                measurementStreaming = false
                            }
                            publishStatus()
                            scope.launch {
                                notifyMobileDisconnected(device)
                            }
                            if (!completed) {
                                val message = if (status == BluetoothGatt.GATT_SUCCESS) {
                                    "${roleLabel(device.role)} disconnected before measurement started."
                                } else {
                                    "Couldn't connect to ${device.name}. Make sure it's nearby and not already connected elsewhere."
                                }
                                finish(RepositoryResult.Error(message))
                            }
                        }
                    }
                }

                override fun onServicesDiscovered(gattInstance: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        updateLastError("Service discovery failed for ${roleLabel(device.role)}: $status")
                        return
                    }

                    val service = gattInstance.getService(session.serviceUuid)
                    val notifyChar = service?.getCharacteristic(session.dataCharUuid)
                    val cmdChar = service?.getCharacteristic(session.cmdCharUuid)

                    if (service == null || notifyChar == null || cmdChar == null) {
                        updateLastError("${roleLabel(device.role)} service or characteristics were not found.")
                        return
                    }

                    session.dataCharacteristic = notifyChar
                    session.commandCharacteristic = cmdChar

                    val notificationsEnabled = runCatching {
                        gattInstance.setCharacteristicNotification(notifyChar, true)
                    }.getOrDefault(false)

                    if (!notificationsEnabled) {
                        updateLastError("Couldn't enable ${roleLabel(device.role)} notifications.")
                        return
                    }

                    val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                    if (descriptor == null) {
                        publishStatus()
                        return
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gattInstance.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        )
                    } else {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gattInstance.writeDescriptor(descriptor)
                    }
                }

                override fun onMtuChanged(gattInstance: BluetoothGatt, mtu: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        session.mtu = mtu
                        publishStatus()
                    } else {
                        updateLastError("MTU negotiation failed for ${roleLabel(device.role)}: $status")
                    }

                    runCatching { gattInstance.discoverServices() }
                }

                override fun onDescriptorWrite(
                    gattInstance: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int
                ) {
                    if (descriptor.characteristic?.uuid != session.dataCharUuid) return
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        publishStatus()
                    } else {
                        updateLastError("Notification subscription failed for ${roleLabel(device.role)}: $status")
                    }
                }

                override fun onCharacteristicWrite(
                    gattInstance: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (characteristic.uuid != session.cmdCharUuid) return
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        updateLastError("${roleLabel(device.role)} command failed: $status")
                    }
                }

                override fun onCharacteristicChanged(
                    gattInstance: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    handleNotification(session.role, characteristic.value ?: ByteArray(0))
                }

                override fun onCharacteristicChanged(
                    gattInstance: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    handleNotification(session.role, value)
                }
            }

            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(context.applicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remoteDevice.connectGatt(context.applicationContext, false, callback)
            }

            session.gatt = gatt
            mainHandler.postAtTime(timeoutRunnable, device.address, SystemClock.uptimeMillis() + CONNECTION_TIMEOUT_MS)

            continuation.invokeOnCancellation {
                mainHandler.removeCallbacksAndMessages(device.address)
                runCatching {
                    gatt.disconnect()
                    gatt.close()
                }
                if (session.gatt == gatt) {
                    clearSession(session)
                }
                if (!bothDevicesConnected()) {
                    measurementStreaming = false
                }
                publishStatus()
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun startMeasurement(): RepositoryResult<String> {
        val wrist = sessions.getValue(BleDeviceRole.WRIST)
        val chest = sessions.getValue(BleDeviceRole.CHEST)

        if (!wrist.isConnected || !chest.isConnected) {
            return RepositoryResult.Error("Connect both Wrist and Chest before starting measurement.")
        }

        measurementStreaming = false
        resetMeasurementCounters()
        clearBuffers()
        lastError = null
        publishStatus()

        val (wristStarted, chestStarted) = coroutineScope {
            val wristCommand = async { writeCommand(wrist, "START") }
            val chestCommand = async { writeCommand(chest, "START") }
            wristCommand.await() to chestCommand.await()
        }

        if (!wristStarted || !chestStarted) {
            if (wristStarted) writeCommand(wrist, "STOP")
            if (chestStarted) writeCommand(chest, "STOP")
            measurementStreaming = false
            publishStatus()
            return RepositoryResult.Error("Couldn't send START to both BLE devices.")
        }

        when (val backendSession = startBackendMeasurementSession()) {
            is RepositoryResult.Success -> {
                lastError = null
            }

            is RepositoryResult.Error -> {
                writeCommand(wrist, "STOP")
                writeCommand(chest, "STOP")
                measurementStreaming = false
                updateLastError(backendSession.message)
                return RepositoryResult.Error(backendSession.message)
            }
        }

        measurementStreaming = true
        publishStatus()
        return RepositoryResult.Success("Measurement started on Wrist and Chest.")
    }

    @SuppressLint("MissingPermission")
    suspend fun stopMeasurement(): RepositoryResult<String> {
        val connectedSessions = sessions.values.filter { it.isConnected }
        if (connectedSessions.isEmpty()) {
            return RepositoryResult.Error("No connected BLE devices to stop.")
        }

        connectedSessions.forEach { writeCommand(it, "STOP") }
        measurementStreaming = false
        clearBuffers()
        when (val backendResult = stopBackendMeasurementSession()) {
            is RepositoryResult.Success -> {
                lastError = null
            }

            is RepositoryResult.Error -> {
                updateLastError(backendResult.message)
            }
        }
        publishStatus()
        return RepositoryResult.Success("Measurement stopped.")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        disconnectSession(BleDeviceRole.WRIST, clearMeasurementState = false)
        disconnectSession(BleDeviceRole.CHEST, clearMeasurementState = false)
        measurementStreaming = false
        clearBuffers()
        publishStatus()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        return appContext
            ?.getSystemService(BluetoothManager::class.java)
            ?.adapter
    }

    @SuppressLint("MissingPermission")
    private fun disconnectSession(role: BleDeviceRole, clearMeasurementState: Boolean) {
        val session = sessions[role] ?: return
        runCatching {
            session.gatt?.disconnect()
            session.gatt?.close()
        }
        clearSession(session)
        if (clearMeasurementState) {
            measurementStreaming = false
            clearBuffers()
        }
    }

    private fun clearSession(session: DeviceSession) {
        session.gatt = null
        session.dataCharacteristic = null
        session.commandCharacteristic = null
        session.device = null
        session.connectedAt = null
        session.mtu = 23
    }

    private fun clearBuffers() {
        wristFrameBuffer.clear()
        chestFrameBuffer.clear()
        backendBufferFill = "0/$WINDOW_SIZE"
    }

    private fun resetMeasurementCounters() {
        framesReceived = 0
        wristFramesReceived = 0
        chestFramesReceived = 0
        framesUploaded = 0
        uploadFailures = 0
        measurementsReady = 0
        lastMeasurement = null
        lastSeq = 0
        backendBufferFill = "0/$WINDOW_SIZE"
    }

    private fun publishStatus() {
        val adapter = bluetoothAdapter()
        val available = adapter?.isEnabled == true
        val wrist = sessions.getValue(BleDeviceRole.WRIST)
        val chest = sessions.getValue(BleDeviceRole.CHEST)

        val connected = wrist.isConnected || chest.isConnected
        val readyToStart = wrist.isConnected && chest.isConnected
        val summaryName = when {
            wrist.isConnected && chest.isConnected -> "${wrist.device?.name ?: "Wrist"} + ${chest.device?.name ?: "Chest"}"
            wrist.isConnected -> wrist.device?.name.orEmpty()
            chest.isConnected -> chest.device?.name.orEmpty()
            else -> ""
        }
        val summaryAddress = when {
            wrist.isConnected && chest.isConnected -> listOfNotNull(wrist.device?.address, chest.device?.address).joinToString(" | ")
            wrist.isConnected -> wrist.device?.address
            chest.isConnected -> chest.device?.address
            else -> null
        }

        val dataStats = linkedMapOf<String, Any>(
            "source" to SOURCE_TAG,
            "streaming" to measurementStreaming,
            "ready_to_start" to readyToStart,
            "frames_received" to framesReceived,
            "wrist_frames_received" to wristFramesReceived,
            "chest_frames_received" to chestFramesReceived,
            "frames_uploaded" to framesUploaded,
            "upload_failures" to uploadFailures,
            "measurements_ready" to measurementsReady,
            "buffer_fill" to backendBufferFill,
            "wrist_buffered" to 0,
            "chest_buffered" to 0,
            "last_seq" to lastSeq,
            "wrist_mtu" to wrist.mtu,
            "chest_mtu" to chest.mtu
        ).apply {
            lastMeasurement?.let { put("last_measurement", it) }
            lastError?.let { put("last_error", it) }
        }

        LocalBleStateStore.update(
            BLEStatusResponse(
                available = available,
                connected = connected,
                deviceName = summaryName,
                deviceAddress = summaryAddress,
                connectedAt = listOfNotNull(wrist.connectedAt, chest.connectedAt).minOrNull(),
                dataStats = dataStats,
                wristConnected = wrist.isConnected,
                chestConnected = chest.isConnected,
                wristDeviceName = wrist.device?.name,
                wristDeviceAddress = wrist.device?.address,
                chestDeviceName = chest.device?.name,
                chestDeviceAddress = chest.device?.address,
                streaming = measurementStreaming
            )
        )
    }

    private fun roleRank(role: BleDeviceRole): Int {
        return when (role) {
            BleDeviceRole.WRIST -> 0
            BleDeviceRole.CHEST -> 1
            BleDeviceRole.UNKNOWN -> 2
        }
    }

    private fun classifyRole(name: String, result: ScanResult): BleDeviceRole {
        val normalizedName = name.lowercase(Locale.US)
        return when {
            "wrist" in normalizedName -> BleDeviceRole.WRIST
            "chest" in normalizedName -> BleDeviceRole.CHEST
            result.scanRecord?.serviceUuids?.any { it.uuid == WRIST_SERVICE_UUID } == true -> BleDeviceRole.WRIST
            result.scanRecord?.serviceUuids?.any { it.uuid == CHEST_SERVICE_UUID } == true -> BleDeviceRole.CHEST
            else -> BleDeviceRole.UNKNOWN
        }
    }

    private fun roleLabel(role: BleDeviceRole): String {
        return when (role) {
            BleDeviceRole.WRIST -> "Wrist"
            BleDeviceRole.CHEST -> "Chest"
            BleDeviceRole.UNKNOWN -> "Unknown"
        }
    }

    private fun roleName(role: BleDeviceRole): String {
        return when (role) {
            BleDeviceRole.WRIST -> "wrist"
            BleDeviceRole.CHEST -> "chest"
            BleDeviceRole.UNKNOWN -> "unknown"
        }
    }

    private fun scanFailureMessage(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "A Bluetooth scan is already running."
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Bluetooth scan registration failed."
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Bluetooth LE scanning is not supported on this device."
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Bluetooth scan failed because of an internal error."
            else -> "Bluetooth scan failed. Error code: $errorCode"
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCommand(session: DeviceSession, command: String): Boolean {
        val gatt = session.gatt ?: return false
        val characteristic = session.commandCharacteristic ?: return false
        val payload = command.toByteArray(Charsets.UTF_8)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
                true
            }.getOrDefault(false)
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun handleNotification(role: BleDeviceRole, value: ByteArray) {
        if (value.isEmpty()) return

        framesReceived += 1
        when (role) {
            BleDeviceRole.WRIST -> {
                wristFramesReceived += 1
                val payload = runCatching { value.toString(Charsets.UTF_8) }.getOrNull()?.trim().orEmpty()
                if (payload.isBlank()) return
                handleWristPayload(payload)
            }

            BleDeviceRole.CHEST -> {
                chestFramesReceived += 1
                handleChestPayload(value)
            }

            BleDeviceRole.UNKNOWN -> Unit
        }
    }

    private fun handleWristPayload(rawFrame: String) {
        val normalized = normalizeWristFrame(rawFrame) ?: run {
            updateLastError("Wrist frame could not be parsed.")
            return
        }

        lastSeq = normalized.optInt("sq", lastSeq)

        if (!measurementStreaming) {
            publishStatus()
            return
        }

        val wristDevice = sessions.getValue(BleDeviceRole.WRIST).device
        scope.launch {
            uploadFrame(
                rawFrame = normalized.toString(),
                device = wristDevice,
                target = BleUploadTarget.WRIST
            )
        }
        publishStatus()
    }

    private fun handleChestPayload(rawFrame: ByteArray) {
        val normalized = normalizeChestFrame(rawFrame) ?: run {
            updateLastError("Chest frame could not be parsed.")
            return
        }

        lastSeq = normalized.optInt("sq", lastSeq)

        if (!measurementStreaming) {
            publishStatus()
            return
        }

        val seq = normalized.optInt("sq", -1)
        if (seq < 0) {
            updateLastError("Chest frame is missing seq.")
            return
        }

        val chestDevice = sessions.getValue(BleDeviceRole.CHEST).device
        scope.launch {
            uploadFrame(
                rawFrame = normalized.toString(),
                device = chestDevice,
                target = BleUploadTarget.CHEST
            )
        }
        publishStatus()
    }

    private fun normalizeWristFrame(rawFrame: String): JSONObject? {
        val source = runCatching { JSONObject(rawFrame) }.getOrNull() ?: return null

        if (source.has("ts") && source.has("sq") && source.has("pi") && source.has("pr")) {
            if (!source.has("tp")) source.put("tp", source.optDouble("temperature", 0.0))
            if (!source.has("bt")) source.put("bt", 100)
            if (!source.has("qi_w")) source.put("qi_w", source.optInt("qi", 0))
            if (!source.has("qi_c")) source.put("qi_c", 0)
            if (!source.has("qi")) {
                val qiW = source.optInt("qi_w", 0)
                val qiC = source.optInt("qi_c", 0)
                source.put("qi", if (qiC == 0) qiW else if (qiW == 1 && qiC == 1) 1 else 0)
            }
            return source
        }

        val seq = source.optInt("seq", source.optInt("sq", -1))
        if (seq < 0) return null

        val normalized = JSONObject()
        normalized.put("ts", source.optLong("timestamp", source.optLong("ts", System.currentTimeMillis())))
        normalized.put("sq", seq)
        normalized.put("pi", extractJsonValue(source, "ppg_ir", "pi") ?: return null)
        normalized.put("pr", extractJsonValue(source, "ppg_red", "pr") ?: return null)
        normalized.put("ax", source.optInt("ax", 0))
        normalized.put("ay", source.optInt("ay", 0))
        normalized.put("az", source.optInt("az", 0))
        normalized.put("gx", source.optInt("gx", 0))
        normalized.put("gy", source.optInt("gy", 0))
        normalized.put("gz", source.optInt("gz", 0))
        normalized.put("tp", source.optDouble("temperature", source.optDouble("tp", 0.0)))
        normalized.put("qi_w", source.optInt("qi_w", source.optInt("qi", 0)))
        normalized.put("qi_c", source.optInt("qi_c", 0))
        normalized.put("qi", source.optInt("qi", source.optInt("qi_w", 0)))
        normalized.put("bt", source.optInt("bt", 100))
        return normalized
    }

    private fun normalizeChestFrame(rawFrame: ByteArray): JSONObject? {
        if (rawFrame.size == CHEST_PACKET_SIZE) {
            return runCatching {
                val buffer = ByteBuffer.wrap(rawFrame).order(ByteOrder.LITTLE_ENDIAN)
                val ecgValues = JSONArray()

                val ep = buffer.get().toInt() and 0xFF
                val qiC = buffer.get().toInt() and 0xFF
                val seq = buffer.short.toInt() and 0xFFFF

                repeat(CHEST_ECG_BATCH_SIZE) {
                    ecgValues.put(buffer.short.toInt())
                }

                JSONObject().apply {
                    put("sq", seq)
                    put("ep", ep)
                    put("qi_c", qiC)
                    put("ecg", ecgValues)
                }
            }.getOrNull()
        }

        val rawText = runCatching { rawFrame.toString(Charsets.UTF_8) }.getOrNull()?.trim().orEmpty()
        if (rawText.isBlank()) return null

        val source = runCatching { JSONObject(rawText) }.getOrNull() ?: return null
        val seq = source.optInt("seq", source.optInt("sq", -1))
        if (seq < 0) return null

        val normalized = JSONObject()
        if (source.has("timestamp")) {
            normalized.put("ts", source.optLong("timestamp"))
        } else if (source.has("ts")) {
            normalized.put("ts", source.optLong("ts"))
        }
        normalized.put("sq", seq)
        normalized.put("ep", source.optInt("r_peak", source.optInt("ep", 0)))
        normalized.put("qi_c", source.optInt("qi_c", source.optInt("qi", 0)))
        normalized.put("cx", source.optInt("cx", 0))
        normalized.put("cy", source.optInt("cy", 0))
        normalized.put("cz", source.optInt("cz", 0))

        when {
            source.has("ecg") -> normalized.put("ecg", normalizeArrayValue(source.get("ecg")))
            source.has("ecg_value") -> normalized.put("ecg", normalizeArrayValue(source.get("ecg_value")))
        }
        return normalized
    }

    private fun extractJsonValue(source: JSONObject, primaryKey: String, fallbackKey: String): Any? {
        return when {
            source.has(primaryKey) -> source.get(primaryKey)
            source.has(fallbackKey) -> source.get(fallbackKey)
            else -> null
        }
    }

    private fun normalizeArrayValue(value: Any): Any {
        return when (value) {
            is JSONArray -> value
            is Number -> JSONArray().put(value.toInt())
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    runCatching { JSONArray(trimmed) }.getOrElse { JSONArray().put(trimmed.toIntOrNull() ?: 0) }
                } else {
                    JSONArray().put(trimmed.toIntOrNull() ?: 0)
                }
            }
            else -> JSONArray().put(value.toString())
        }
    }

    private suspend fun uploadFrame(
        rawFrame: String,
        device: BleDevice?,
        target: BleUploadTarget
    ) {
        val token = SessionStore.token.value
        if (token.isNullOrBlank()) {
            uploadFailures += 1
            updateLastError("BLE frame received but there is no active session token.")
            return
        }

        runCatching {
            val request = BleFrameUploadRequest(
                rawFrame = rawFrame,
                sourceDeviceName = device?.name,
                sourceDeviceAddress = device?.address
            )
            when (target) {
                BleUploadTarget.MERGED -> ApiClient.apiService.uploadBleMergedFrame(
                    token = "Bearer $token",
                    request = request
                )

                BleUploadTarget.WRIST -> ApiClient.apiService.uploadWristBleFrame(
                    token = "Bearer $token",
                    request = request
                )

                BleUploadTarget.CHEST -> ApiClient.apiService.uploadChestBleFrame(
                    token = "Bearer $token",
                    request = request
                )
            }
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    framesUploaded += 1
                    lastSeq = body.seqNum
                    backendBufferFill = body.bufferFill ?: backendBufferFill
                    lastError = null
                    body.reading?.let { reading ->
                        lastMeasurement = "${reading.systolic}/${reading.diastolic} - ${reading.heartRate} bpm"
                    }
                    if (body.readingCreated) {
                        measurementsReady += 1
                        ReadingRepository.syncFromApi()
                    }
                    publishStatus()
                } else {
                    uploadFailures += 1
                    updateLastError(body?.message ?: "BLE frame upload failed.")
                }
            },
            onFailure = {
                uploadFailures += 1
                updateLastError(it.message ?: "BLE frame upload failed.")
            }
        )
    }

    private suspend fun notifyMobileConnected(device: BleDevice) {
        val token = SessionStore.token.value ?: return
        runCatching {
            ApiClient.apiService.notifyMobileDeviceConnected(
                token = "Bearer $token",
                request = BleSessionDeviceRequest(
                    role = roleName(device.role),
                    deviceName = device.name,
                    deviceAddress = device.address
                )
            )
        }
    }

    private suspend fun notifyMobileDisconnected(device: BleDevice) {
        val token = SessionStore.token.value ?: return
        runCatching {
            ApiClient.apiService.notifyMobileDeviceDisconnected(
                token = "Bearer $token",
                request = BleSessionDeviceRequest(
                    role = roleName(device.role),
                    deviceName = device.name,
                    deviceAddress = device.address
                )
            )
        }
    }

    private suspend fun startBackendMeasurementSession(): RepositoryResult<String> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("BLE measurement cannot start without an active session token.")

        return runCatching {
            ApiClient.apiService.startMobileMeasurementSession("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    backendBufferFill = "0/$WINDOW_SIZE"
                    RepositoryResult.Success(body.message)
                } else {
                    RepositoryResult.Error(body?.message ?: "Backend BLE measurement session could not be reset.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Backend BLE measurement session could not be reset.")
            }
        )
    }

    private suspend fun stopBackendMeasurementSession(): RepositoryResult<String> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("BLE measurement stop could not be tracked without an active session token.")

        return runCatching {
            ApiClient.apiService.stopMobileMeasurementSession("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    backendBufferFill = "0/$WINDOW_SIZE"
                    RepositoryResult.Success(body.message)
                } else {
                    RepositoryResult.Error(body?.message ?: "Backend BLE measurement stop could not be recorded.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Backend BLE measurement stop could not be recorded.")
            }
        )
    }

    private fun bothDevicesConnected(): Boolean {
        return sessions.getValue(BleDeviceRole.WRIST).isConnected &&
            sessions.getValue(BleDeviceRole.CHEST).isConnected
    }

    private fun currentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    }

    private fun updateLastError(message: String) {
        lastError = message
        publishStatus()
    }
}

class BleRepository(context: Context? = null) {
    init {
        context?.let(AndroidBleManager::initialize)
    }

    fun resetForColdStart() {
        AndroidBleManager.disconnect()
    }

    fun fetchBleStatus(): RepositoryResult<BLEStatusResponse> {
        return RepositoryResult.Success(AndroidBleManager.currentStatus())
    }

    suspend fun fetchPredictionModelInfo(): RepositoryResult<PredictionModelInfoResponse> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session token.")

        return runCatching {
            ApiClient.apiService.getPredictionModelInfo("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    RepositoryResult.Success(body)
                } else {
                    RepositoryResult.Error(body?.message ?: "Model info couldn't be loaded.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Model info couldn't be loaded.")
            }
        )
    }

    suspend fun scanBleDevices(context: Context): RepositoryResult<List<BleDevice>> {
        return AndroidBleManager.scanDevices(context)
    }

    suspend fun connectToDevice(context: Context, device: BleDevice): RepositoryResult<String> {
        return AndroidBleManager.connect(context, device)
    }

    suspend fun startMeasurement(): RepositoryResult<String> {
        return AndroidBleManager.startMeasurement()
    }

    suspend fun stopMeasurement(): RepositoryResult<String> {
        return AndroidBleManager.stopMeasurement()
    }

    fun disconnect() {
        AndroidBleManager.disconnect()
    }
}
