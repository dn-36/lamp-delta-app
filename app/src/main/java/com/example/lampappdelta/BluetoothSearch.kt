package com.example.lampappdelta

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.annotation.CheckResult
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("StaticFieldLeak")
object BluetoothSearch {

    @CheckResult
    suspend fun discoverDevices(
        durationMs: Long = 12_000L,
        includeBle: Boolean = true,
        onDevice: ((BluetoothDevice) -> Unit)? = null
    ): List<BluetoothDevice> = withContext(Dispatchers.Main) {

        Log.d("BT_DISCOVER", "discoverDevices() ENTER durationMs=$durationMs includeBle=$includeBle thread=${Thread.currentThread().name}")

        Log.d("BT_DISCOVER", "context is null? ${context == null}")

        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: run {
                Log.w("BT_DISCOVER", "BluetoothAdapter.getDefaultAdapter() == null -> return emptyList")
                return@withContext emptyList<BluetoothDevice>()
            }

        Log.d("BT_DISCOVER", "adapter.isEnabled=${adapter.isEnabled} adapter.isDiscovering=${adapter.isDiscovering}")

        if (!adapter.isEnabled) {
            Log.w("BT_DISCOVER", "Bluetooth disabled -> return emptyList")
            return@withContext emptyList<BluetoothDevice>()
        }

        // Проверяем доступные разрешения
        val hasScanPerm = runCatching { hasScanPermission(context!!) }
            .onFailure { Log.e("BT_DISCOVER", "hasScanPermission(context!!) crashed: ${it.message}", it) }
            .getOrDefault(false)

        val hasLocationOnLegacy = runCatching { hasLegacyLocationForScan(context!!) }
            .onFailure { Log.e("BT_DISCOVER", "hasLegacyLocationForScan(context!!) crashed: ${it.message}", it) }
            .getOrDefault(false)

        val canClassicScan = if (Build.VERSION.SDK_INT >= 31) hasScanPerm else hasLocationOnLegacy
        val canBleScan = includeBle && (if (Build.VERSION.SDK_INT >= 31) hasScanPerm else hasLocationOnLegacy)

        Log.d(
            "BT_DISCOVER",
            "permissions: sdk=${Build.VERSION.SDK_INT} hasScanPerm=$hasScanPerm hasLocationOnLegacy=$hasLocationOnLegacy canClassicScan=$canClassicScan canBleScan=$canBleScan"
        )

        if (!canClassicScan && !canBleScan) {
            Log.w("BT_DISCOVER", "No permissions for scan (classic&ble disabled) -> return emptyList")
            return@withContext emptyList<BluetoothDevice>()
        }

        val found = ConcurrentHashMap.newKeySet<String>() // адреса для дедупликации
        val devices = ConcurrentHashMap<String, BluetoothDevice>()
        val eventChan = Channel<Unit>(Channel.CONFLATED)

        Log.d("BT_DISCOVER", "init collections: found=${found.size} devices=${devices.size}")

        // --- CLASSIC DISCOVERY (ACTION_FOUND) ---
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        Log.d("BT_DISCOVER", "classic filter prepared")

        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                Log.d("BT_DISCOVER", "classic receiver onReceive: ctxNull=${ctx == null} action=${intent?.action}")

                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                        if (device == null) {
                            Log.w("BT_DISCOVER", "ACTION_FOUND but EXTRA_DEVICE is null")
                            return
                        }

                        val address = safeAddress(device)
                        Log.d(
                            "BT_DISCOVER",
                            "ACTION_FOUND device: name=${runCatching { device.name }.getOrNull()} address=$address bondState=${runCatching { device.bondState }.getOrNull()}"
                        )

                        if (address != null && found.add(address)) {

                            if (device.name != null && deviceList.none { it.address == device.address }) {
                                deviceList.add(device)
                                Log.d("BT_DISCOVER", "classic deviceList add: ${device.address} size=${deviceList.size}")
                            } else {
                                Log.d("BT_DISCOVER", "classic deviceList skip: nameNull=${device.name == null} alreadyInList=${deviceList.any { it.address == device.address }}")
                            }

                            devices[address] = device
                            Log.d("BT_DISCOVER", "devices map put: $address total=${devices.size}")

                            runCatching { onDevice?.invoke(device) }
                                .onFailure { Log.e("BT_DISCOVER", "onDevice callback crashed: ${it.message}", it) }

                            val sendRes = eventChan.trySend(Unit)
                            Log.d("BT_DISCOVER", "eventChan.trySend result=$sendRes")
                        } else {
                            Log.d("BT_DISCOVER", "classic dedup skip: addressNull=${address == null} alreadyFound=${address != null && found.contains(address)}")
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d("BT_DISCOVER", "ACTION_DISCOVERY_FINISHED received (classic)")
                    }
                }
            }
        }

        // Регистрируем ресивер с корректным флагом на API 33+
        if (canClassicScan) {
            Log.d("BT_DISCOVER", "register classic receiver... sdk=${Build.VERSION.SDK_INT}")
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    context!!.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context!!.registerReceiver(receiver, filter)
                }
            }.onSuccess {
                Log.d("BT_DISCOVER", "classic receiver registered OK")
            }.onFailure {
                Log.e("BT_DISCOVER", "classic receiver register FAILED: ${it.message}", it)
            }
        } else {
            Log.d("BT_DISCOVER", "classic scan disabled by permissions -> receiver not registered")
        }

        // --- BLE SCAN ---
        @SuppressLint("MissingPermission")
        val bleScanner = if (canBleScan) adapter.bluetoothLeScanner else null

        Log.d("BT_DISCOVER", "bleScanner is null? ${bleScanner == null}")

        val bleCallback = object : ScanCallback() {

            override fun onScanFailed(errorCode: Int) {
                Log.e("BT_DISCOVER", "BLE onScanFailed errorCode=$errorCode")
            }

            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (device == null) {
                    Log.w("BT_DISCOVER", "BLE onScanResult: result.device is null")
                    return
                }

                val address = safeAddress(device) ?: run {
                    Log.w("BT_DISCOVER", "BLE onScanResult: address null (safeAddress)")
                    return
                }

                Log.d(
                    "BT_DISCOVER",
                    "BLE onScanResult: type=$callbackType name=${runCatching { device.name }.getOrNull()} address=$address rssi=${result.rssi}"
                )

                if (found.add(address)) {

                    if (device.name != null && deviceList.none { it.address == device.address }) {
                        runCatching { _actionAddDevice(device.name!!) }
                            .onFailure { Log.e("BT_DISCOVER", "_actionAddDevice crashed: ${it.message}", it) }

                        deviceList.add(device)
                        Log.d("BT_DISCOVER", "ble deviceList add: ${device.address} size=${deviceList.size}")
                    } else {
                        Log.d("BT_DISCOVER", "ble deviceList skip: nameNull=${device.name == null} alreadyInList=${deviceList.any { it.address == device.address }}")
                    }

                    devices[address] = device
                    Log.d("BT_DISCOVER", "devices map put (ble): $address total=${devices.size}")

                    runCatching { onDevice?.invoke(device) }
                        .onFailure { Log.e("BT_DISCOVER", "onDevice callback crashed (ble): ${it.message}", it) }

                    val sendRes = eventChan.trySend(Unit)
                    Log.d("BT_DISCOVER", "eventChan.trySend (ble) result=$sendRes")
                } else {
                    Log.d("BT_DISCOVER", "BLE dedup skip: alreadyFound=$address")
                }
            }

            @SuppressLint("MissingPermission")
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Log.d("BT_DISCOVER", "BLE onBatchScanResults size=${results.size}")

                for (res in results) {
                    val device = res.device ?: continue
                    val address = safeAddress(device)

                    Log.d(
                        "BT_DISCOVER",
                        "BLE batch item: name=${runCatching { device.name }.getOrNull()} address=$address rssi=${res.rssi}"
                    )

                    if (address == null) continue

                    if (device.name != null && deviceList.none { it.address == device.address }) {
                        runCatching { _actionAddDevice(device.name!!) }
                            .onFailure { Log.e("BT_DISCOVER", "_actionAddDevice crashed (batch): ${it.message}", it) }
                        deviceList.add(device)
                        Log.d("BT_DISCOVER", "ble batch deviceList add: ${device.address} size=${deviceList.size}")
                    }

                    if (found.add(address)) {
                        devices[address] = device
                        Log.d("BT_DISCOVER", "devices map put (batch): $address total=${devices.size}")

                        runCatching { onDevice?.invoke(device) }
                            .onFailure { Log.e("BT_DISCOVER", "onDevice callback crashed (batch): ${it.message}", it) }
                    }
                }

                val sendRes = eventChan.trySend(Unit)
                Log.d("BT_DISCOVER", "eventChan.trySend (batch) result=$sendRes")
            }
        }

        try {
            // Стартуем сканирование(я)
            if (canClassicScan) {
                Log.d("BT_DISCOVER", "startDiscovery() call... isDiscoveringBefore=${adapter.isDiscovering}")
                @Suppress("DEPRECATION")
                val started = adapter.startDiscovery()
                Log.d("BT_DISCOVER", "startDiscovery() returned=$started isDiscoveringAfter=${adapter.isDiscovering}")
            } else {
                Log.d("BT_DISCOVER", "classic startDiscovery skipped (canClassicScan=false)")
            }

            if (canBleScan) {
                Log.d("BT_DISCOVER", "bleScanner.startScan() call...")
                runCatching { bleScanner?.startScan(bleCallback) }
                    .onSuccess { Log.d("BT_DISCOVER", "bleScanner.startScan() called OK") }
                    .onFailure { Log.e("BT_DISCOVER", "bleScanner.startScan() FAILED: ${it.message}", it) }
            } else {
                Log.d("BT_DISCOVER", "BLE startScan skipped (canBleScan=false includeBle=$includeBle)")
            }

            Log.d("BT_DISCOVER", "awaiting events up to timeout=${durationMs}ms ...")

            // Ждём либо таймаут, либо отмену корутины
            withTimeout(durationMs) {
                for (i in eventChan) {
                    Log.d("BT_DISCOVER", "eventChan tick: devices=${devices.size} found=${found.size}")
                    // no-op
                }
            }

            Log.d("BT_DISCOVER", "withTimeout finished without TimeoutCancellationException (unexpected)")

        } catch (_: TimeoutCancellationException) {
            Log.d("BT_DISCOVER", "TimeoutCancellationException -> normal end of scan")
        } catch (t: Throwable) {
            Log.e("BT_DISCOVER", "discoverDevices() unexpected error: ${t.message}", t)
        } finally {
            Log.d("BT_DISCOVER", "finally: stopping scans...")

            @Suppress("DEPRECATION")
            if (adapter.isDiscovering) {
                Log.d("BT_DISCOVER", "cancelDiscovery() call...")
                runCatching {
                    @Suppress("MissingPermission")
                    adapter.cancelDiscovery()
                }.onSuccess {
                    Log.d("BT_DISCOVER", "cancelDiscovery() OK")
                }.onFailure {
                    Log.e("BT_DISCOVER", "cancelDiscovery() FAILED: ${it.message}", it)
                }
            } else {
                Log.d("BT_DISCOVER", "adapter.isDiscovering=false -> cancelDiscovery skipped")
            }

            @SuppressLint("MissingPermission")
            runCatching {
                Log.d("BT_DISCOVER", "bleScanner.stopScan() call...")
                bleScanner?.stopScan(bleCallback)
            }.onSuccess {
                Log.d("BT_DISCOVER", "bleScanner.stopScan() OK")
            }.onFailure {
                Log.e("BT_DISCOVER", "bleScanner.stopScan() FAILED: ${it.message}", it)
            }

            try {
                if (canClassicScan) {
                    Log.d("BT_DISCOVER", "unregisterReceiver() call...")
                    context!!.unregisterReceiver(receiver)
                    Log.d("BT_DISCOVER", "unregisterReceiver() OK")
                } else {
                    Log.d("BT_DISCOVER", "unregisterReceiver skipped (canClassicScan=false)")
                }
            } catch (_: IllegalArgumentException) {
                Log.w("BT_DISCOVER", "unregisterReceiver() already unregistered")
            } catch (t: Throwable) {
                Log.e("BT_DISCOVER", "unregisterReceiver() FAILED: ${t.message}", t)
            }

            runCatching { eventChan.close() }
                .onSuccess { Log.d("BT_DISCOVER", "eventChan closed") }
                .onFailure { Log.e("BT_DISCOVER", "eventChan close failed: ${it.message}", it) }

            Log.d(
                "BT_DISCOVER",
                "finally done. totals: devices=${devices.size} found=${found.size} deviceList=${deviceList.size}"
            )
        }

        val result = devices.values.toList()
        Log.d("BT_DISCOVER", "discoverDevices() EXIT resultSize=${result.size}")
        result
    }

    private fun hasScanPermission(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        Log.d("BT_DISCOVER", "hasScanPermission(sdk=${Build.VERSION.SDK_INT}) -> $granted")
        return granted
    }

    private fun hasLegacyLocationForScan(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT in 23..30) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        Log.d("BT_DISCOVER", "hasLegacyLocationForScan(sdk=${Build.VERSION.SDK_INT}) -> $granted")
        return granted
    }

    @SuppressLint("MissingPermission")
    private fun safeAddress(device: BluetoothDevice): String? {
        return try {
            val addr = device.address
            Log.d("BT_DISCOVER", "safeAddress() -> $addr")
            addr
        } catch (_: SecurityException) {
            Log.w("BT_DISCOVER", "safeAddress() SecurityException -> null")
            null
        }
    }


    //  private val _statusFlow = MutableStateFlow(StausBluetoothConnection.DISCONNECTED)

 //   fun getStatusBleutooth(): StausBluetoothConnection = _statusFlow.value

 //   private val tscDll = TSCActivity()
    private var _device: BluetoothDevice? = null
    private val deviceList = mutableListOf<BluetoothDevice>()
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var _actionAddDevice: (String) -> Unit = {}
    private var context: Context? = null

    fun init(context: Context): BluetoothSearch {
        if (this.context == null) {
            this.context = context.applicationContext
            Log.d("BT_DISCOVER", "init(): context set = ${context != null}, pkg=${context?.packageName}")
        }
        return this
    }

  /*  private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 1) Обрабатываем уже спаренные устройства
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            pairedDevices?.forEach { device ->
                if (device.name != null && deviceList.none { it.address == device.address }) {
                    _actionAddDevice(device.name!!)
                    deviceList.add(device)
                }
            }

            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (it.name != null && deviceList.none { d -> d.address == it.address }) {
                            deviceList.add(it)
                            _actionAddDevice(it.name!!)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun searchForDevices(actionAddDevice: (String) -> Unit): List<BluetoothDevice> {
        _actionAddDevice = actionAddDevice

        // Проверяем разрешение локации (нужно для поиска Bluetooth)
        if (ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        deviceList.clear()

        // Регистрируем ресивер на ACTION_FOUND
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)

        context!!.registerReceiver(receiver, filter)

        bluetoothAdapter?.startDiscovery()

        Log.d("проверка списка блютуз устройств", "${deviceList}")

        // Останавливаем сразу, чтобы не держать поиск всегда включённым
        stopBluetoothDiscovery()

        return deviceList.distinctBy { it.address }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBluetoothDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
            Log.d("TSCprinter", "Bluetooth discovery stopped.")
        } else {
            Log.d("TSCprinter", "Bluetooth discovery was not active.")
        }
    } */

}