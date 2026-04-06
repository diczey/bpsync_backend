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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class BleDevice(
    val name: String,
    val address: String
)

private object LocalBleStateStore {
    var status: BLEStatusResponse = BLEStatusResponse(
        available = false,
        connected = false,
        deviceName = "",
        deviceAddress = null,
        connectedAt = null,
        dataStats = emptyMap()
    )
        private set

    fun update(status: BLEStatusResponse) {
        this.status = status
    }

    fun updateDataStats(transform: (Map<String, Any>) -> Map<String, Any>) {
        status = status.copy(dataStats = transform(status.dataStats))
    }
}

private object AndroidBleManager {
    private const val SCAN_DURATION_MS = 6_000L
    private const val CONNECTION_TIMEOUT_MS = 10_000L
    private const val SOURCE_TAG = "android-local-ble"

    private val WRIST_SERVICE_UUID: UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
    private val DATA_CHAR_UUID: UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
    private val CMD_CHAR_UUID: UUID = UUID.fromString("19B10002-E8F2-537E-4F6C-D104768A1214")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var appContext: Context? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var activeDevice: BleDevice? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        syncAvailability()
    }

    fun currentStatus(): BLEStatusResponse {
        syncAvailability()
        return LocalBleStateStore.status
    }

    @SuppressLint("MissingPermission")
    suspend fun scanDevices(context: Context): RepositoryResult<List<BleDevice>> {
        initialize(context)

        val adapter = bluetoothAdapter()
            ?: return RepositoryResult.Error("This device does not support Bluetooth LE.")

        if (!adapter.isEnabled) {
            markDisconnected(available = false)
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
                    discovered[device.address] = BleDevice(name = name, address = device.address)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { result ->
                        val device = result.device ?: return@forEach
                        val name = device.name ?: result.scanRecord?.deviceName ?: "Unnamed BLE Device"
                        discovered[device.address] = BleDevice(name = name, address = device.address)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    finish(RepositoryResult.Error(scanFailureMessage(errorCode)))
                }
            }

            activeScanCallback = callback
            scanner.startScan(callback)

            val timeoutRunnable = Runnable {
                val devices = discovered.values.toList()
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

        val adapter = bluetoothAdapter()
            ?: return RepositoryResult.Error("This device does not support Bluetooth LE.")

        if (!adapter.isEnabled) {
            markDisconnected(available = false)
            return RepositoryResult.Error("Bluetooth is turned off. Turn it on and try again.")
        }

        val remoteDevice = runCatching { adapter.getRemoteDevice(device.address) }.getOrNull()
            ?: return RepositoryResult.Error("Couldn't resolve the selected Bluetooth device.")

        disconnect()

        return suspendCancellableCoroutine { continuation ->
            var completed = false
            lateinit var gatt: BluetoothGatt

            fun finish(result: RepositoryResult<String>) {
                if (completed) return
                completed = true
                mainHandler.removeCallbacksAndMessages(remoteDevice.address)
                continuation.resume(result)
            }

            val timeoutRunnable = Runnable {
                runCatching {
                    gatt.disconnect()
                    gatt.close()
                }
                clearGattState()
                markDisconnected(available = true)
                finish(RepositoryResult.Error("Connection timed out. Try again closer to the device."))
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gattInstance: BluetoothGatt, status: Int, newState: Int) {
                    when {
                        status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                            bluetoothGatt = gattInstance
                            activeDevice = device
                            LocalBleStateStore.update(
                                BLEStatusResponse(
                                    available = true,
                                    connected = true,
                                    deviceName = device.name,
                                    deviceAddress = device.address,
                                    connectedAt = currentTimestamp(),
                                    dataStats = baseStats(device, streaming = false)
                                )
                            )
                            finish(RepositoryResult.Success("Connected to ${device.name}"))
                            runCatching { gattInstance.discoverServices() }
                        }

                        newState == BluetoothProfile.STATE_DISCONNECTED -> {
                            runCatching { gattInstance.close() }
                            if (bluetoothGatt == gattInstance) {
                                clearGattState()
                            }
                            markDisconnected(available = true)
                            if (!completed) {
                                val message = if (status == BluetoothGatt.GATT_SUCCESS) {
                                    "The device disconnected before streaming started."
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
                        updateLastError("Service discovery failed: $status")
                        return
                    }

                    val service = gattInstance.getService(WRIST_SERVICE_UUID)
                    val notifyChar = service?.getCharacteristic(DATA_CHAR_UUID)
                    val cmdChar = service?.getCharacteristic(CMD_CHAR_UUID)

                    if (service == null || notifyChar == null || cmdChar == null) {
                        updateLastError("BPSync wrist service or characteristics were not found.")
                        return
                    }

                    dataCharacteristic = notifyChar
                    commandCharacteristic = cmdChar

                    val notificationsEnabled = runCatching {
                        gattInstance.setCharacteristicNotification(notifyChar, true)
                    }.getOrDefault(false)

                    if (!notificationsEnabled) {
                        updateLastError("Couldn't enable BLE notifications.")
                        return
                    }

                    val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                    if (descriptor == null) {
                        sendStartCommand(gattInstance)
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

                override fun onDescriptorWrite(
                    gattInstance: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int
                ) {
                    if (descriptor.characteristic?.uuid != DATA_CHAR_UUID) return
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        sendStartCommand(gattInstance)
                    } else {
                        updateLastError("Notification subscription failed: $status")
                    }
                }

                override fun onCharacteristicWrite(
                    gattInstance: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (characteristic.uuid != CMD_CHAR_UUID) return
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        updateStreamingState(true)
                    } else {
                        updateLastError("START command failed: $status")
                    }
                }

                override fun onCharacteristicChanged(
                    gattInstance: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    handleNotification(characteristic.value ?: ByteArray(0))
                }

                override fun onCharacteristicChanged(
                    gattInstance: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    handleNotification(value)
                }
            }

            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(context.applicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remoteDevice.connectGatt(context.applicationContext, false, callback)
            }

            bluetoothGatt = gatt
            mainHandler.postAtTime(timeoutRunnable, remoteDevice.address, SystemClock.uptimeMillis() + CONNECTION_TIMEOUT_MS)

            continuation.invokeOnCancellation {
                mainHandler.removeCallbacksAndMessages(remoteDevice.address)
                runCatching {
                    gatt.disconnect()
                    gatt.close()
                }
                if (bluetoothGatt == gatt) {
                    clearGattState()
                }
                markDisconnected(available = adapter.isEnabled)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val adapter = bluetoothAdapter()
        runCatching {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
        clearGattState()
        markDisconnected(available = adapter?.isEnabled == true)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        return appContext
            ?.getSystemService(BluetoothManager::class.java)
            ?.adapter
    }

    private fun syncAvailability() {
        val adapter = bluetoothAdapter()
        val current = LocalBleStateStore.status
        LocalBleStateStore.update(
            current.copy(
                available = adapter?.isEnabled == true,
                connected = current.connected && bluetoothGatt != null
            )
        )
    }

    private fun clearGattState() {
        bluetoothGatt = null
        dataCharacteristic = null
        commandCharacteristic = null
        activeDevice = null
    }

    private fun markDisconnected(available: Boolean) {
        LocalBleStateStore.update(
            BLEStatusResponse(
                available = available,
                connected = false,
                deviceName = "",
                deviceAddress = null,
                connectedAt = null,
                dataStats = emptyMap()
            )
        )
    }

    private fun currentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
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

    private fun baseStats(device: BleDevice, streaming: Boolean): Map<String, Any> {
        return linkedMapOf(
            "source" to SOURCE_TAG,
            "device_name" to device.name,
            "device_address" to device.address,
            "streaming" to streaming,
            "frames_received" to 0,
            "frames_uploaded" to 0,
            "upload_failures" to 0,
            "measurements_ready" to 0,
            "buffer_fill" to "0/100",
            "last_seq" to 0
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendStartCommand(gatt: BluetoothGatt) {
        val characteristic = commandCharacteristic ?: return
        val payload = "START".toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun handleNotification(value: ByteArray) {
        if (value.isEmpty()) return

        val payload = runCatching { value.toString(Charsets.UTF_8) }.getOrNull()?.trim().orEmpty()
        if (payload.isBlank()) return

        incrementCounter("frames_received")
        val device = activeDevice
        scope.launch {
            uploadFrame(payload, device)
        }
    }

    private suspend fun uploadFrame(rawFrame: String, device: BleDevice?) {
        val token = SessionStore.token.value
        if (token.isNullOrBlank()) {
            incrementCounter("upload_failures")
            updateLastError("BLE frame received but there is no active session token.")
            return
        }

        runCatching {
            ApiClient.apiService.uploadBleFrame(
                token = "Bearer $token",
                request = BleFrameUploadRequest(
                    rawFrame = rawFrame,
                    sourceDeviceName = device?.name,
                    sourceDeviceAddress = device?.address
                )
            )
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    LocalBleStateStore.updateDataStats { current ->
                        current.toMutableMap().apply {
                            put("frames_uploaded", ((current["frames_uploaded"] as? Number)?.toInt() ?: 0) + 1)
                            put("buffer_fill", body.bufferFill ?: current["buffer_fill"] ?: "0/100")
                            put("last_seq", body.seqNum)
                            body.reading?.let { reading ->
                                put("last_measurement", "${reading.systolic}/${reading.diastolic} • ${reading.heartRate} bpm")
                            }
                            if (body.readingCreated) {
                                put("measurements_ready", ((current["measurements_ready"] as? Number)?.toInt() ?: 0) + 1)
                            }
                            remove("last_error")
                        }
                    }

                    if (body.readingCreated) {
                        ReadingRepository.syncFromApi()
                    }
                } else {
                    incrementCounter("upload_failures")
                    updateLastError(body?.message ?: "BLE frame upload failed.")
                }
            },
            onFailure = {
                incrementCounter("upload_failures")
                updateLastError(it.message ?: "BLE frame upload failed.")
            }
        )
    }

    private fun updateStreamingState(streaming: Boolean) {
        LocalBleStateStore.updateDataStats { current ->
            current.toMutableMap().apply {
                put("streaming", streaming)
                remove("last_error")
            }
        }
    }

    private fun incrementCounter(key: String) {
        LocalBleStateStore.updateDataStats { current ->
            current.toMutableMap().apply {
                put(key, ((current[key] as? Number)?.toInt() ?: 0) + 1)
            }
        }
    }

    private fun updateLastError(message: String) {
        LocalBleStateStore.updateDataStats { current ->
            current.toMutableMap().apply {
                put("last_error", message)
            }
        }
    }
}

class BleRepository(context: Context? = null) {
    init {
        context?.let(AndroidBleManager::initialize)
    }

    fun fetchBleStatus(): RepositoryResult<BLEStatusResponse> {
        return RepositoryResult.Success(AndroidBleManager.currentStatus())
    }

    suspend fun scanBleDevices(context: Context): RepositoryResult<List<BleDevice>> {
        return AndroidBleManager.scanDevices(context)
    }

    suspend fun connectToDevice(context: Context, device: BleDevice): RepositoryResult<String> {
        return AndroidBleManager.connect(context, device)
    }

    fun disconnect() {
        AndroidBleManager.disconnect()
    }
}
