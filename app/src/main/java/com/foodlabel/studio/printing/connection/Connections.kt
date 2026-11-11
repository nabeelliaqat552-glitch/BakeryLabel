package com.foodlabel.studio.printing.connection

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class ConnectionType(val label: String) {
    BLUETOOTH("Bluetooth"), BLE("Bluetooth LE"), USB("USB cable"), NETWORK("Wi-Fi / LAN"), ANDROID_PRINT("Android Print");

    companion object { fun parse(v: String?) = entries.firstOrNull { it.name == v } ?: BLUETOOTH }
}

class PrinterException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * A raw byte pipe to a printer. A successful [write] means the bytes were handed to the link,
 * NOT that labels came out. The app never reports "printed" on that basis alone.
 */
interface PrinterConnection {
    suspend fun open()
    suspend fun write(bytes: ByteArray, onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> })
    suspend fun close()
}

private const val CHUNK = 1024

fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun adapter(context: Context): BluetoothAdapter =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        ?: throw PrinterException("This phone has no Bluetooth.")

/** Gives printers time to take data off the link before the socket closes; closing early can cut labels short. */
private suspend fun drainDelay(bytes: Int) = delay((300L + bytes / 40L).coerceAtMost(4000L))

/** Bluetooth Classic, Serial Port Profile. Most Bluetooth thermal printers use this. */
@SuppressLint("MissingPermission")
class BluetoothClassicConnection(private val context: Context, private val address: String) : PrinterConnection {
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission(context)) throw PrinterException("Allow Nearby devices (Bluetooth) permission to print.")
        val ad = adapter(context)
        if (!ad.isEnabled) throw PrinterException("Bluetooth is off. Turn it on and try again.")
        val device: BluetoothDevice = try { ad.getRemoteDevice(address) } catch (e: IllegalArgumentException) {
            throw PrinterException("Saved Bluetooth address is not valid.")
        }
        runCatching { ad.cancelDiscovery() }
        val attempts = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(SPP) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP) },
        )
        var last: Exception? = null
        for (make in attempts) {
            val s = try { make() } catch (e: IOException) { last = e; continue }
            try {
                s.connect()
                socket = s; out = s.outputStream
                return@withContext
            } catch (e: IOException) {
                last = e
                runCatching { s.close() }
            }
        }
        throw PrinterException("Could not connect to the printer. Check it is on, paired and not connected to another phone.", last)
    }

    override suspend fun write(bytes: ByteArray, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val o = out ?: throw PrinterException("Printer is not connected.")
        try {
            var i = 0
            while (i < bytes.size) {
                val n = minOf(CHUNK, bytes.size - i)
                o.write(bytes, i, n)
                o.flush()
                i += n
                onProgress(i, bytes.size)
                delay(8)
            }
            drainDelay(bytes.size)
        } catch (e: IOException) {
            throw PrinterException("Connection to the printer was lost while sending.", e)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) { runCatching { socket?.close() }; socket = null; out = null }

    companion object { val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }
}

/**
 * Bluetooth Low Energy. There is no standard printing service on BLE, so this writes to the first
 * writable characteristic the printer exposes. It works with many BLE-only label printers that accept
 * raw commands this way, but not with printers that need a vendor app protocol.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class BleConnection(private val context: Context, private val address: String) : PrinterConnection {
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var mtuPayload = 20
    private var connected = CompletableDeferred<Boolean>()
    private var servicesReady = CompletableDeferred<Boolean>()
    private var mtuReady = CompletableDeferred<Int>()
    @Volatile private var writeDone: CompletableDeferred<Boolean>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) connected.complete(true)
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected.complete(false); servicesReady.complete(false); writeDone?.complete(false)
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { servicesReady.complete(status == BluetoothGatt.GATT_SUCCESS) }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { mtuReady.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23) }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeDone?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    override suspend fun open() {
        if (!hasBluetoothPermission(context)) throw PrinterException("Allow Nearby devices (Bluetooth) permission to print.")
        val device = adapter(context).getRemoteDevice(address)
        gatt = withContext(Dispatchers.Main) { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }
        val g = gatt ?: throw PrinterException("Could not start Bluetooth LE.")
        try {
            if (!withTimeout(10_000) { connected.await() }) throw PrinterException("Printer refused the Bluetooth LE connection.")
            g.requestMtu(247)
            val mtu = runCatching { withTimeout(3_000) { mtuReady.await() } }.getOrDefault(23)
            mtuPayload = (mtu - 3).coerceAtLeast(20)
            g.discoverServices()
            if (!withTimeout(10_000) { servicesReady.await() }) throw PrinterException("Could not read the printer's Bluetooth LE services.")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            close(); throw PrinterException("Bluetooth LE printer did not respond in time.")
        }
        characteristic = g.services.flatMap { it.characteristics }.firstOrNull {
            it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        } ?: run { close(); throw PrinterException("This Bluetooth LE device has no writable channel, so it cannot print from this app.") }
    }

    override suspend fun write(bytes: ByteArray, onProgress: (Int, Int) -> Unit) {
        val g = gatt ?: throw PrinterException("Printer is not connected.")
        val ch = characteristic ?: throw PrinterException("Printer is not connected.")
        val noResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        ch.writeType = if (noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var i = 0
        while (i < bytes.size) {
            val n = minOf(mtuPayload, bytes.size - i)
            val part = bytes.copyOfRange(i, i + n)
            val done = CompletableDeferred<Boolean>().also { writeDone = it }
            ch.value = part
            if (!g.writeCharacteristic(ch)) {
                delay(30)
                ch.value = part
                if (!g.writeCharacteristic(ch)) throw PrinterException("Bluetooth LE write was refused by the printer.")
            }
            val ok = runCatching { withTimeout(2_000) { done.await() } }.getOrDefault(noResponse)
            if (!ok) throw PrinterException("Bluetooth LE printer stopped accepting data.")
            if (noResponse) delay(6)
            i += n
            onProgress(i, bytes.size)
        }
        drainDelay(bytes.size)
    }

    override suspend fun close() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
        connected = CompletableDeferred(); servicesReady = CompletableDeferred(); mtuReady = CompletableDeferred()
    }
}

/** USB OTG. Uses the printer-class interface when present, otherwise the first bulk OUT endpoint. */
class UsbConnection(private val context: Context, private val deviceName: String) : PrinterConnection {
    private var conn: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var endpoint: UsbEndpoint? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val dev = mgr.deviceList[deviceName] ?: throw PrinterException("USB printer is not plugged in. Connect it with an OTG cable.")
        if (!mgr.hasPermission(dev)) throw PrinterException("USB permission was not given. Open Printer setup and allow access.")
        val interfaces = (0 until dev.interfaceCount).map { dev.getInterface(it) }
        val chosen = interfaces.sortedByDescending { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }.firstNotNullOfOrNull { i ->
            (0 until i.endpointCount).map { i.getEndpoint(it) }
                .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
                ?.let { i to it }
        } ?: throw PrinterException("This USB device does not accept print data.")
        val c = mgr.openDevice(dev) ?: throw PrinterException("Could not open the USB printer.")
        if (!c.claimInterface(chosen.first, true)) { c.close(); throw PrinterException("USB printer is busy.") }
        conn = c; iface = chosen.first; endpoint = chosen.second
    }

    override suspend fun write(bytes: ByteArray, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val c = conn ?: throw PrinterException("Printer is not connected.")
        val ep = endpoint!!
        var i = 0
        while (i < bytes.size) {
            val n = minOf(16 * 1024, bytes.size - i)
            val sent = c.bulkTransfer(ep, bytes, i, n, 5_000)
            if (sent <= 0) throw PrinterException("USB printer stopped accepting data.")
            i += sent
            onProgress(i, bytes.size)
        }
        drainDelay(bytes.size / 4)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { iface?.let { conn?.releaseInterface(it) }; conn?.close() }
        conn = null; iface = null; endpoint = null
    }
}

/** Raw TCP (JetDirect / port 9100), used by most network label printers. */
class NetworkConnection(private val host: String, private val port: Int) : PrinterConnection {
    private var socket: Socket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        try {
            socket = Socket().apply { connect(InetSocketAddress(host, port), 5_000); soTimeout = 10_000; tcpNoDelay = true }
        } catch (e: IOException) {
            throw PrinterException("Could not reach $host:$port. Check the IP address and that the phone is on the same Wi-Fi.", e)
        }
    }

    override suspend fun write(bytes: ByteArray, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val o = socket?.getOutputStream() ?: throw PrinterException("Printer is not connected.")
        try {
            var i = 0
            while (i < bytes.size) {
                val n = minOf(8 * 1024, bytes.size - i)
                o.write(bytes, i, n); i += n
                onProgress(i, bytes.size)
            }
            o.flush()
            drainDelay(bytes.size / 8)
        } catch (e: IOException) {
            throw PrinterException("Network connection to the printer was lost.", e)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) { runCatching { socket?.close() }; socket = null }
}

fun connectionFor(context: Context, type: ConnectionType, address: String, port: Int): PrinterConnection = when (type) {
    ConnectionType.BLUETOOTH -> BluetoothClassicConnection(context, address)
    ConnectionType.BLE -> BleConnection(context, address)
    ConnectionType.USB -> UsbConnection(context, address)
    ConnectionType.NETWORK -> NetworkConnection(address, port)
    ConnectionType.ANDROID_PRINT -> throw PrinterException("Android Print is handled by the system print dialog.")
}
