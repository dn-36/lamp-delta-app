package com.module.common.printer_barcode_tsc

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.module.common.printer_barcode_tsc.models.StausBluetoothConnection
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.util.UUID


@SuppressLint("StaticFieldLeak")
object TSCprinter {

    private val _statusFlow = MutableStateFlow(StausBluetoothConnection.DISCONNECTED)

    fun getStatusBleutooth(): StausBluetoothConnection = _statusFlow.value

    private var _device: BluetoothDevice? = null
    private val deviceList = mutableListOf<BluetoothDevice>()
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var _actionAddDevice: (String) -> Unit = {}
    private var context: Context? = null

    fun init(context: Context): TSCprinter {
        if (this.context == null) {
            this.context = context.applicationContext
        }
        return this
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 1) Обрабатываем уже спаренные устройства
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            pairedDevices?.forEach { device ->

                if (device.name != null && deviceList.none { it.address == device.address }) {

                    println("найденные устройства уже спаренные устройства ${device.name}")

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

                            println("найденные устройства ACTION_FOUND ${device.name}")
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
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connectToDevice(
        deviceName: String,
       // context: Context,
    ): BluetoothSocket? {

        _statusFlow.value = StausBluetoothConnection.LOADING

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var bluetoothSocket: BluetoothSocket? = null

        try {
            // Проверка разрешения BLUETOOTH_CONNECT (для Android 12+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e("TSCprinter", "BLUETOOTH_CONNECT permission not granted")
                    _statusFlow.value = StausBluetoothConnection.DISCONNECTED
                    return null
                }
            }

            val deviceListSnapshot = synchronized(deviceList) { deviceList.toList() }
            _device = deviceListSnapshot.find { it.name == deviceName }

            if (_device != null) {

                bluetoothSocket = _device!!.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket.connect()

                _statusFlow.value = StausBluetoothConnection.CONNECTED

            } else {
                // Если не нашли устройство с таким именем
                _statusFlow.value = StausBluetoothConnection.DISCONNECTED
              //  actionError()
            }
        } catch (e: IOException) {
            Log.e("TSCprinter", "Ошибка подключения: ${e.message}", e)
            _statusFlow.value = StausBluetoothConnection.DISCONNECTED
            try {
                bluetoothSocket?.close()
            } catch (closeEx: IOException) {
                closeEx.printStackTrace()
            }
        }

        return bluetoothSocket
    }

}