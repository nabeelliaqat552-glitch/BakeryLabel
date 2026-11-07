package com.bakery.labelapp.print

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.math.min

interface PrinterTransport {
    suspend fun send(data: ByteArray)
    suspend fun test()
}

fun transportFor(context: Context, cfg: PrinterConfig): PrinterTransport = when (cfg.type) {
    PrinterType.BLUETOOTH -> BluetoothTransport(context, cfg.address)
    PrinterType.WIFI -> WifiTransport(cfg.address, cfg.port)
    PrinterType.USB -> UsbTransport(context, cfg.address)
    PrinterType.SYSTEM -> error("Android print has no direct connection to test")
}

// ---------------- Bluetooth (classic SPP) ----------------

private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Paired devices as (name, MAC). Printers must be paired in phone settings first. */
@SuppressLint("MissingPermission")
fun pairedDevices(context: Context): List<Pair<String, String>> {
    if (!hasBluetoothPermission(context)) return emptyList()
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices.map { (it.name ?: "Unknown device") to it.address }.sortedBy { it.first }
    } catch (e: SecurityException) {
        emptyList()
    }
}

class BluetoothTransport(private val context: Context, private val address: String) : PrinterTransport {

    @SuppressLint("MissingPermission")
    private fun open(): BluetoothSocket {
        require(address.isNotBlank()) { "Choose a Bluetooth printer first" }
        if (!hasBluetoothPermission(context)) error("Allow Bluetooth permission in Printer setup")
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: error("This phone has no Bluetooth")
        if (!adapter.isEnabled) error("Turn on Bluetooth")
        try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
        val socket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(SPP)
        try {
            socket.connect()
        } catch (e: Exception) {
            runCatching { socket.close() }
            error("Could not connect to printer. Is it on and nearby?")
        }
        return socket
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        open().use { s ->
            val out = s.outputStream
            var i = 0
            while (i < data.size) {
                val n = min(2048, data.size - i)
                out.write(data, i, n)
                out.flush()
                i += n
                Thread.sleep(15)
            }
            Thread.sleep(800) // let the printer drain its buffer before closing
        }
    }

    override suspend fun test() = withContext(Dispatchers.IO) { open().close() }
}

// ---------------- Wi-Fi / LAN (raw port 9100) ----------------

class WifiTransport(private val host: String, private val port: Int) : PrinterTransport {
    private fun open(): Socket {
        require(host.isNotBlank()) { "Enter the printer IP address" }
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 5000)
        } catch (e: Exception) {
            runCatching { socket.close() }
            error("No printer answering at $host:$port")
        }
        return socket
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        open().use { s ->
            s.getOutputStream().apply { write(data); flush() }
            Thread.sleep(300)
        }
    }

    override suspend fun test() = withContext(Dispatchers.IO) { open().close() }
}

// ---------------- USB OTG ----------------

private const val ACTION_USB_PERMISSION = "com.bakery.labelapp.USB_PERMISSION"

fun usbId(d: UsbDevice) = "%04X:%04X".format(d.vendorId, d.productId)

/** Connected USB devices as (name, "VID:PID"). */
fun usbDevices(context: Context): List<Pair<String, String>> {
    val mgr = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
    return mgr.deviceList.values.map { (it.productName ?: "USB device") to usbId(it) }
}

class UsbTransport(private val context: Context, private val id: String) : PrinterTransport {

    private fun device(mgr: UsbManager): UsbDevice {
        val d = mgr.deviceList.values.firstOrNull { usbId(it).equals(id, ignoreCase = true) }
            ?: error("USB printer not connected. Check the OTG cable.")
        if (!mgr.hasPermission(d)) {
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
            )
            mgr.requestPermission(d, pi)
            error("Allow USB access on the screen, then print again")
        }
        return d
    }

    private fun bulkOut(d: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        val found = mutableListOf<Pair<UsbInterface, UsbEndpoint>>()
        for (i in 0 until d.interfaceCount) {
            val intf = d.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    found += intf to ep
                }
            }
        }
        return found.firstOrNull { it.first.interfaceClass == UsbConstants.USB_CLASS_PRINTER } ?: found.firstOrNull()
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val d = device(mgr)
        val (intf, ep) = bulkOut(d) ?: error("This USB device is not a printer")
        val conn = mgr.openDevice(d) ?: error("Could not open the USB printer")
        try {
            conn.claimInterface(intf, true)
            var i = 0
            while (i < data.size) {
                val n = min(16384, data.size - i)
                val written = conn.bulkTransfer(ep, data, i, n, 5000)
                if (written <= 0) error("USB printer stopped responding")
                i += written
            }
        } finally {
            conn.releaseInterface(intf)
            conn.close()
        }
    }

    override suspend fun test() = withContext(Dispatchers.IO) {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val d = device(mgr)
        bulkOut(d) ?: error("This USB device is not a printer")
        Unit
    }
}
