package com.bakery.labelapp.print

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PrinterType(val label: String) {
    BLUETOOTH("Bluetooth"),
    WIFI("Wi-Fi"),
    USB("USB OTG"),
    SYSTEM("Android print / PDF"),
}

/** TSPL = label printers (Xprinter, TSC, Gprinter in label mode). ESC/POS = receipt-style printers. */
enum class PrinterLanguage { TSPL, ESCPOS }

data class PrinterConfig(
    val type: PrinterType = PrinterType.BLUETOOTH,
    /** Bluetooth MAC, Wi-Fi IP address, or USB "VID:PID". */
    val address: String = "",
    val name: String = "",
    val port: Int = 9100,
    val language: PrinterLanguage = PrinterLanguage.TSPL,
    val dpi: Int = 203,
    val invert: Boolean = false,
) {
    val isConfigured: Boolean get() = type == PrinterType.SYSTEM || address.isNotBlank()
    val displayName: String get() = when (type) {
        PrinterType.SYSTEM -> "Android print"
        else -> name.ifBlank { address }
    }
}

/** Printer choice is saved per phone (not synced), since each counter has its own printer. */
class PrinterStore(context: Context) {
    private val prefs = context.getSharedPreferences("printer", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<PrinterConfig> = _config

    private fun load(): PrinterConfig {
        val d = PrinterConfig()
        return PrinterConfig(
            type = runCatching { PrinterType.valueOf(prefs.getString("type", d.type.name)!!) }.getOrDefault(d.type),
            address = prefs.getString("address", "") ?: "",
            name = prefs.getString("name", "") ?: "",
            port = prefs.getInt("port", d.port),
            language = runCatching { PrinterLanguage.valueOf(prefs.getString("language", d.language.name)!!) }.getOrDefault(d.language),
            dpi = prefs.getInt("dpi", d.dpi),
            invert = prefs.getBoolean("invert", false),
        )
    }

    fun save(c: PrinterConfig) {
        prefs.edit()
            .putString("type", c.type.name)
            .putString("address", c.address)
            .putString("name", c.name)
            .putInt("port", c.port)
            .putString("language", c.language.name)
            .putInt("dpi", c.dpi)
            .putBoolean("invert", c.invert)
            .apply()
        _config.value = c
    }
}
