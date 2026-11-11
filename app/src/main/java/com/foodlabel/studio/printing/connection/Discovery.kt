package com.foodlabel.studio.printing.connection

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class FoundPrinter(
    val name: String,
    val address: String,
    val type: ConnectionType,
    val paired: Boolean = false,
    val detail: String = "",
)

object Discovery {

    fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasScanPermission(context: Context) = bluetoothPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothOn(context: Context): Boolean =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<FoundPrinter> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val ad = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return emptyList()
        return runCatching {
            ad.bondedDevices.map {
                FoundPrinter(it.name ?: "Bluetooth device", it.address,
                    if (it.type == BluetoothDevice.DEVICE_TYPE_LE) ConnectionType.BLE else ConnectionType.BLUETOOTH, paired = true, detail = "Paired")
            }
        }.getOrDefault(emptyList())
    }

    /** Classic discovery plus a BLE scan, emitted as devices appear. Stops when the collector is cancelled. */
    @SuppressLint("MissingPermission")
    fun scanBluetooth(context: Context): Flow<FoundPrinter> = callbackFlow {
        val ad = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (ad == null || !hasScanPermission(context)) { close(); return@callbackFlow }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    @Suppress("DEPRECATION")
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null) trySend(FoundPrinter(d.name ?: "Unknown device", d.address, ConnectionType.BLUETOOTH,
                        paired = d.bondState == BluetoothDevice.BOND_BONDED, detail = "Bluetooth"))
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        runCatching { ad.startDiscovery() }
        val le = ad.bluetoothLeScanner
        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                val name = result.scanRecord?.deviceName ?: d.name ?: return
                trySend(FoundPrinter(name, d.address, ConnectionType.BLE, detail = "Bluetooth LE · signal ${result.rssi} dBm"))
            }
        }
        runCatching { le?.startScan(leCallback) }
        awaitClose {
            runCatching { ad.cancelDiscovery() }
            runCatching { le?.stopScan(leCallback) }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    fun pair(context: Context, address: String): Boolean = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.getRemoteDevice(address).createBond()
    }.getOrDefault(false)

    fun usbDevices(context: Context): List<Pair<FoundPrinter, UsbDevice>> {
        val mgr = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return mgr.deviceList.values.map { dev ->
            val isPrinter = (0 until dev.interfaceCount).any { dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            val label = dev.productName ?: "USB device"
            FoundPrinter(label, dev.deviceName, ConnectionType.USB, paired = mgr.hasPermission(dev),
                detail = (if (isPrinter) "USB printer" else "USB device") + " · VID ${"%04X".format(dev.vendorId)} PID ${"%04X".format(dev.productId)}" +
                    if (mgr.hasPermission(dev)) " · allowed" else " · needs permission") to dev
        }
    }

    const val ACTION_USB_PERMISSION = "com.foodlabel.studio.USB_PERMISSION"

    fun requestUsbPermission(context: Context, device: UsbDevice) {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        mgr.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
    }

    /** Looks for devices answering on [port] in the phone's own /24 Wi-Fi subnet. */
    suspend fun scanNetwork(port: Int = 9100, onFound: (FoundPrinter) -> Unit) = withContext(Dispatchers.IO) {
        val local = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress } ?: return@withContext
        val parts = local.hostAddress!!.split('.')
        val prefix = parts.take(3).joinToString(".")
        val gate = Semaphore(32)
        coroutineScope {
            (1..254).map { host ->
                async {
                    gate.withPermit {
                        val ip = "$prefix.$host"
                        val open = runCatching { Socket().use { it.connect(InetSocketAddress(ip, port), 250); true } }.getOrDefault(false)
                        if (open) onFound(FoundPrinter("Network printer", ip, ConnectionType.NETWORK, detail = "Answers on port $port"))
                    }
                }
            }.awaitAll()
        }
    }
}
