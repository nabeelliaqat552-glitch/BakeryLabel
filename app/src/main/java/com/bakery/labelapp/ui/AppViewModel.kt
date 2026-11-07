package com.bakery.labelapp.ui

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.labelapp.BakeryApp
import com.bakery.labelapp.data.Batch
import com.bakery.labelapp.data.LabelSettings
import com.bakery.labelapp.data.Product
import com.bakery.labelapp.label.Dates
import com.bakery.labelapp.label.LabelContent
import com.bakery.labelapp.print.LabelPrintService
import com.bakery.labelapp.print.PdfLabels
import com.bakery.labelapp.print.PrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the person is about to print. Dates live here until a batch is created. */
data class PrintForm(
    val productId: String? = null,
    val mfgDay: Long = Dates.today(),
    /** null = expiry is calculated automatically from the shelf life */
    val manualExp: Long? = null,
    /** null = use the default copies from settings */
    val copies: Int? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val bakery = app as BakeryApp
    private val repo = bakery.repo
    val printerStore = bakery.printerStore
    val cloud = bakery.cloud

    val settings: StateFlow<LabelSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LabelSettings())
    val products: StateFlow<List<Product>> =
        repo.activeProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allProducts: StateFlow<List<Product>> =
        repo.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val batches: StateFlow<List<Batch>> =
        repo.batches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _form = MutableStateFlow(PrintForm())
    val form: StateFlow<PrintForm> = _form.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    var adminUnlocked by mutableStateOf(false)

    // ---------- form ----------

    fun selectedProduct(list: List<Product>, f: PrintForm): Product? =
        list.firstOrNull { it.id == f.productId } ?: list.firstOrNull()

    fun autoExp(p: Product?, mfgDay: Long): Long = mfgDay + (p?.shelfLifeDays ?: 0)

    fun selectProduct(id: String) = _form.update { it.copy(productId = id, manualExp = null) }
    fun setMfg(day: Long) = _form.update { it.copy(mfgDay = day) }
    fun setExp(day: Long) = _form.update { it.copy(manualExp = day) }
    fun resetExp() = _form.update { it.copy(manualExp = null) }
    fun setCopies(n: Int) = _form.update { it.copy(copies = n.coerceIn(1, 999)) }

    fun setSize(w: Float, h: Float) = viewModelScope.launch {
        repo.saveSettings(settings.value.copy(widthMm = w, heightMm = h))
    }

    fun content(s: LabelSettings, productName: String, mfgDay: Long, expDay: Long) = LabelContent(
        bakeryName = s.bakeryName,
        subtitle = if (s.showSubtitle) s.subtitle else null,
        productName = productName.uppercase(),
        mfgText = Dates.format(mfgDay, s.dateFormat),
        expText = Dates.format(expDay, s.dateFormat),
    )

    // ---------- printing ----------

    /** Prints a NEW batch and saves it with fixed dates. */
    fun printNew(activity: Activity) = launchTask {
        val p = selectedProduct(products.value, form.value) ?: error("Add a product first")
        val f = form.value
        val exp = f.manualExp ?: autoExp(p, f.mfgDay)
        require(exp >= f.mfgDay) { "Expiry date is before the manufacturing date" }
        val copies = f.copies ?: settings.value.defaultCopies
        val s = settings.value
        LabelPrintService.print(activity, printerStore.config.value, s, content(s, p.name, f.mfgDay, exp), copies)
        val batch = repo.createBatch(p, f.mfgDay, exp, f.manualExp != null, copies)
        "Sent $copies label${if (copies == 1) "" else "s"} · batch ${batch.code}"
    }

    /** Reprints an existing batch with its original dates. */
    fun reprint(activity: Activity, b: Batch, copies: Int) = launchTask {
        val s = settings.value
        LabelPrintService.print(activity, printerStore.config.value, s, content(s, b.productName, b.mfgEpochDay, b.expEpochDay), copies)
        repo.addPrints(b, copies)
        "Reprinted $copies × ${b.productName}"
    }

    fun exportPdf(activity: Activity) = launchTask {
        val p = selectedProduct(products.value, form.value) ?: error("Add a product first")
        val f = form.value
        val exp = f.manualExp ?: autoExp(p, f.mfgDay)
        val s = settings.value
        val copies = f.copies ?: s.defaultCopies
        val file = withContext(Dispatchers.IO) { PdfLabels.create(activity, s, content(s, p.name, f.mfgDay, exp), copies) }
        withContext(Dispatchers.Main) { PdfLabels.share(activity, file) }
        "PDF ready ($copies page${if (copies == 1) "" else "s"})"
    }

    fun testConnection(cfg: PrinterConfig) = launchTask {
        LabelPrintService.test(getApplication<Application>(), cfg)
        "Printer connected"
    }

    fun testPrint(activity: Activity, cfg: PrinterConfig) = launchTask {
        val s = settings.value
        val today = Dates.today()
        LabelPrintService.print(activity, cfg, s, content(s, "CHOCOLATE CAKE", today, today + 2), 1)
        "Test label sent"
    }

    // ---------- admin ----------

    fun savePrinter(cfg: PrinterConfig) {
        printerStore.save(cfg)
        _messages.tryEmit("Printer saved")
    }

    fun saveProduct(p: Product) = launchTask { repo.saveProduct(p); "Product saved" }
    fun saveSettings(s: LabelSettings) = launchTask { repo.saveSettings(s); "Label design saved" }
    fun deleteBatch(b: Batch) = launchTask { repo.deleteBatch(b); "Batch removed" }

    fun changePin(newPin: String) = launchTask {
        require(newPin.length in 4..8) { "PIN must be 4 to 8 digits" }
        repo.saveSettings(settings.value.copy(adminPin = newPin))
        "PIN changed"
    }

    private fun launchTask(block: suspend () -> String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                _messages.emit(block())
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Something went wrong")
            } finally {
                _busy.value = false
            }
        }
    }
}
