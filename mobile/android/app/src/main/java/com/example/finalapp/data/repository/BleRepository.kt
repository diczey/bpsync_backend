package com.example.finalapp.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.finalapp.data.model.BLEStatusResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.coroutines.resume
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
}

private object AndroidBleManager {
    private const val SCAN_DURATION_MS = 6_000L
    private const val CONNECTION_TIMEOUT_MS = 10_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null

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
                bluetoothGatt = null
                markDisconnected(available = true)
                finish(RepositoryResult.Error("Connection timed out. Try again closer to the device."))
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gattInstance: BluetoothGatt, status: Int, newState: Int) {
                    when {
                        status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                            bluetoothGatt = gattInstance
                            LocalBleStateStore.update(
                                BLEStatusResponse(
                                    available = true,
                                    connected = true,
                                    deviceName = device.name,
                                    deviceAddress = device.address,
                                    connectedAt = currentTimestamp(),
                                    dataStats = mapOf("source" to "android-local-ble")
                                )
                            )
                            finish(RepositoryResult.Success("Connected to ${device.name}"))
                            runCatching { gattInstance.discoverServices() }
                        }

                        newState == BluetoothProfile.STATE_DISCONNECTED -> {
                            runCatching { gattInstance.close() }
                            if (bluetoothGatt == gattInstance) {
                                bluetoothGatt = null
                            }
                            markDisconnected(available = true)
                            if (!completed) {
                                val message = if (status == BluetoothGatt.GATT_SUCCESS) {
                                    "The device disconnected before pairing completed."
                                } else {
                                    "Couldn't connect to ${device.name}. Make sure it's nearby and not already connected elsewhere."
                                }
                                finish(RepositoryResult.Error(message))
                            }
                        }
                    }
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
                    bluetoothGatt = null
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
        bluetoothGatt = null
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

    private fun markDisconnected(available: Boolean) {
        val current = LocalBleStateStore.status
        LocalBleStateStore.update(
            current.copy(
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
