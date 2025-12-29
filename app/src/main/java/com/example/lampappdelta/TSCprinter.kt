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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        scope: CoroutineScope

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

                Log.d("TSCprinter", "uuids устройства: ${_device!!.uuids}")

                bluetoothSocket = _device!!.createInsecureRfcommSocketToServiceRecord(uuid)
                //_device!!.createRfcommSocketToServiceRecord(uuid)

                bluetoothSocket.connect()

                val job = scope.launch {

                    readLoop(bluetoothSocket) { frame ->

                        val data = extractMsgData(frame)

                        Log.d("TSCprinter", "Got raw frame len=${frame.size} value=${frame}")
                        Log.d("TSCprinter", "Got converted frame value hex=${data.toHex()}")
                        Log.d("TSCprinter", "Got converted frame value Ascii=${data.toAscii()}")

                    }
                }

                val out = bluetoothSocket.outputStream

                val queryStatusPacket = byteArrayOf(
                    0x43, 0x4D, 0x44,             // 'C' 'M' 'D'
                    0x30, 0x30, 0x31, 0x36,       // "0016"
                    0x49,                         // 'I'
                    0x3F,                         // '?'  (QUERY_STATUS)
                    0x00, 0x00,                   // tx_number = 0
                    0xFD.toByte(), 0xDD.toByte(), // CRC16 (как в примере коллеги)
                    0x45, 0x4E, 0x44              // 'E' 'N' 'D'
                )

                out.write(queryStatusPacket)
                out.flush()

                Log.d("TSCprinter", "проверка подключения к сокету: ${bluetoothSocket.isConnected}")

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

    suspend fun readLoop(socket: BluetoothSocket, onFrame: (ByteArray) -> Unit) =

        withContext(Dispatchers.IO) {

        val input = socket.inputStream
        val framer = MsgFramer()
        val tmp = ByteArray(1024)

        while (true) {
            val n = input.read(tmp) // блокирует поток
            if (n <= 0) break
            framer.push(tmp, n)

            while (true) {
                val frame = framer.nextFrameOrNull() ?: break
                onFrame(frame)
            }
        }
    }

    fun extractMsgData(frame: ByteArray): ByteArray {
        val headerSize = 3 + 4 + 1 + 1 + 2
        val tailSize = 2 + 3
        val dataStart = headerSize
        val dataEnd = frame.size - tailSize
        if (dataEnd < dataStart) return byteArrayOf()
        return frame.copyOfRange(dataStart, dataEnd)
    }

    fun ByteArray.toHex(): String =
        joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }

    fun ByteArray.toAscii(): String =
        buildString {
            for (b in this@toAscii) {
                val v = b.toInt() and 0xFF
                append(
                    if (v in 32..126) v.toChar() else '.'   // печатные символы, остальное точкой
                )
            }
        }

    class MsgFramer {
        private val buf = ArrayList<Byte>() // простой буфер

        fun push(chunk: ByteArray, count: Int) {
            for (i in 0 until count) buf.add(chunk[i])
        }

        fun nextFrameOrNull(): ByteArray? {
            // 1) найти "CMD"
            fun indexOfCMD(): Int {
                for (i in 0..buf.size - 3) {
                    if (buf[i] == 'C'.code.toByte() &&
                        buf[i+1] == 'M'.code.toByte() &&
                        buf[i+2] == 'D'.code.toByte()
                    ) return i
                }
                return -1
            }

            val start = indexOfCMD()
            if (start < 0) {
                // мусор — можно чистить буфер, но аккуратно
                if (buf.size > 2048) buf.clear()
                return null
            }

            // отбросить всё до CMD
            if (start > 0) repeat(start) { buf.removeAt(0) }

            // нужно минимум 7 байт: CMD + len(4)
            if (buf.size < 7) return null

            val lenStr = byteArrayOf(buf[3], buf[4], buf[5], buf[6]).toString(Charsets.US_ASCII)
            val totalLen = lenStr.toIntOrNull() ?: run {
                // если длина битая — сдвигаемся на 1 байт и ищем снова
                buf.removeAt(0)
                return null
            }

            if (buf.size < totalLen) return null // ждём ещё

            val frame = ByteArray(totalLen)
            for (i in 0 until totalLen) frame[i] = buf[i]

            // удалить из буфера
            repeat(totalLen) { buf.removeAt(0) }

            return frame
        }

    }

}