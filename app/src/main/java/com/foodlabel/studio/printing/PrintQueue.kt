package com.foodlabel.studio.printing

import android.content.Context
import android.util.Log
import com.foodlabel.studio.data.db.LabelSizeEntity
import com.foodlabel.studio.data.db.PrintHistoryEntity
import com.foodlabel.studio.data.db.PrintJobEntity
import com.foodlabel.studio.data.db.PrinterEntity
import com.foodlabel.studio.data.repo.Repository
import com.foodlabel.studio.data.seed.TemplateLibrary
import com.foodlabel.studio.dates.DateEngine
import com.foodlabel.studio.dates.DateFormatOption
import com.foodlabel.studio.dates.ShelfLifeUnit
import com.foodlabel.studio.label.LabelData
import com.foodlabel.studio.label.LabelDataFactory
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.LabelRenderer
import com.foodlabel.studio.printing.connection.ConnectionType
import com.foodlabel.studio.printing.connection.PrinterException
import com.foodlabel.studio.printing.connection.connectionFor
import com.foodlabel.studio.printing.driver.Drivers
import com.foodlabel.studio.printing.driver.PrinterProtocol
import com.foodlabel.studio.printing.driver.RasterJob
import com.foodlabel.studio.core.Units
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** One line of a print job: a product, how many labels, and its dates and batch. */
@Serializable
data class BatchItem(
    val productId: Long,
    val copies: Int,
    /** ISO date (yyyy-MM-dd). */
    val mfgDate: String,
    /** ISO date picked by hand, or null to calculate from shelf life. */
    val manualExpiry: String? = null,
    val batch: String? = null,
    val designId: Long? = null,
    val labelSizeId: Long? = null,
    val language: String? = null,
)

enum class JobStatus(val label: String) {
    QUEUED("Waiting"), SENDING("Sending"), SENT("Sent – please check the labels"),
    CONFIRMED("Printed (confirmed)"), FAILED("Failed"), CANCELLED("Cancelled"), SAVED("Saved batch");

    companion object { fun parse(v: String) = entries.firstOrNull { it.name == v } ?: FAILED }
}

data class QueueState(
    val jobId: Long? = null,
    val status: JobStatus? = null,
    val labelsSent: Int = 0,
    val labelsTotal: Int = 0,
    val message: String? = null,
)

/** A rendered label ready for any output: raw printer, PDF or PNG. */
data class PreparedLabel(
    val design: LabelDesign, val data: LabelData, val copies: Int, val history: PrintHistoryEntity,
    val gapMm: Double = 2.0, val continuous: Boolean = false,
)

/**
 * Runs print jobs one at a time in the app process. Status is honest:
 * SENT means the printer link accepted every byte; only the user's confirmation marks a job CONFIRMED.
 */
class PrintQueue(private val context: Context, private val repo: Repository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channel = Channel<Long>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()
    @Volatile private var cancelRequested: Long? = null
    private var worker: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    init {
        worker = scope.launch { for (id in channel) runJob(id) }
    }

    suspend fun enqueue(businessId: Long, printer: PrinterEntity, items: List<BatchItem>, savedName: String? = null): Long {
        val prepared = prepare(businessId, items)
        val total = prepared.sumOf { it.copies }
        val jobId = repo.insertJob(
            PrintJobEntity(businessId = businessId, printerId = printer.id, printerName = printer.name, totalLabels = total,
                payload = json.encodeToString(items), savedName = savedName),
            prepared.map { it.history },
        )
        _state.value = QueueState(jobId, JobStatus.QUEUED, 0, total)
        channel.send(jobId)
        return jobId
    }

    fun cancel(jobId: Long) { cancelRequested = jobId }

    suspend fun confirm(jobId: Long, printedOk: Boolean) {
        val j = repo.getJob(jobId) ?: return
        repo.setJobStatus(jobId, if (printedOk) JobStatus.CONFIRMED.name else JobStatus.FAILED.name, j.sentLabels,
            if (printedOk) null else "You reported the labels did not print correctly.")
        if (_state.value.jobId == jobId) _state.update { it.copy(status = if (printedOk) JobStatus.CONFIRMED else JobStatus.FAILED) }
    }

    fun decodeItems(payload: String): List<BatchItem> = runCatching { json.decodeFromString<List<BatchItem>>(payload) }.getOrDefault(emptyList())

    /** Resolves products, dates and designs for a batch. Shared by raw printing, PDF and PNG export. */
    suspend fun prepare(businessId: Long, items: List<BatchItem>): List<PreparedLabel> {
        val business = repo.getBusiness(businessId) ?: throw PrinterException("Business not found.")
        val fmt = DateFormatOption.parse(business.dateFormat)
        return items.filter { it.copies > 0 }.map { item ->
            val pw = repo.getProduct(businessId, item.productId) ?: throw PrinterException("A product in this job was deleted.")
            val p = pw.product
            val mfg = LocalDate.parse(item.mfgDate)
            val dates = DateEngine.resolve(
                mfg, if (business.showMfgTime) java.time.LocalTime.now().withSecond(0).withNano(0) else null,
                p.shelfLife, ShelfLifeUnit.parse(p.shelfUnit), item.manualExpiry?.let { LocalDate.parse(it) },
            )
            val sizeId = item.labelSizeId ?: business.defaultLabelSizeId
            val size = sizeId?.let { repo.getLabelSize(it) }
            val design = resolveDesign(businessId, item.designId ?: p.defaultDesignId, sizeId)
            val data = LabelDataFactory.build(business, p, pw.barcode, dates, item.batch, item.language ?: business.labelLanguage)
            PreparedLabel(design, data, item.copies, PrintHistoryEntity(
                jobId = 0, businessId = businessId, productId = p.id, productName = p.nameEn, copies = item.copies,
                mfgDate = DateEngine.format(dates.manufactured, fmt), expDate = DateEngine.format(dates.expiry, fmt),
                batchNumber = data.batch, designName = design.name,
            ), gapMm = size?.gapMm ?: 2.0, continuous = size?.continuous ?: false)
        }
    }

    private suspend fun resolveDesign(businessId: Long, designId: Long?, sizeId: Long?): LabelDesign {
        designId?.let { id -> repo.getDesign(id)?.let { d -> if (d.businessId == null || d.businessId == businessId) return d } }
        val size: LabelSizeEntity? = sizeId?.let { repo.getLabelSize(it) }
        return TemplateLibrary.blankStarter(size?.widthMm ?: 50.0, size?.heightMm ?: 30.0)
    }

    private suspend fun runJob(jobId: Long) {
        val job = repo.getJob(jobId) ?: return
        val printer = job.printerId?.let { repo.getPrinter(job.businessId, it) }
        var sent = 0
        fun publish(status: JobStatus, msg: String? = null) {
            _state.value = QueueState(jobId, status, sent, job.totalLabels, msg)
        }
        if (printer == null) {
            repo.setJobStatus(jobId, JobStatus.FAILED.name, 0, "Printer was removed."); publish(JobStatus.FAILED, "Printer was removed."); return
        }
        val protocol = PrinterProtocol.parse(printer.protocol)
        val type = ConnectionType.parse(printer.connection)
        if (!protocol.isRaw || type == ConnectionType.ANDROID_PRINT) {
            val m = "This printer uses Android Print. Use Print with Android or Save as PDF."
            repo.setJobStatus(jobId, JobStatus.FAILED.name, 0, m); publish(JobStatus.FAILED, m); return
        }
        val conn = connectionFor(context, type, printer.address, printer.port)
        try {
            repo.setJobStatus(jobId, JobStatus.SENDING.name, 0, null); publish(JobStatus.SENDING, "Connecting to ${printer.name}…")
            val labels = prepare(job.businessId, decodeItems(job.payload))
            conn.open()
            publish(JobStatus.SENDING, "Sending to ${printer.name}…")
            for (label in labels) {
                if (cancelRequested == jobId) throw CancellationException("Cancelled by user")
                val bytes = buildBytes(label, printer, protocol)
                conn.write(bytes)
                sent += label.copies
                repo.setJobStatus(jobId, JobStatus.SENDING.name, sent, null)
                publish(JobStatus.SENDING, "Sent $sent of ${job.totalLabels} labels…")
            }
            repo.setJobStatus(jobId, JobStatus.SENT.name, sent, null)
            publish(JobStatus.SENT, "All $sent labels were sent to ${printer.name}. Check they printed correctly.")
        } catch (e: CancellationException) {
            repo.setJobStatus(jobId, JobStatus.CANCELLED.name, sent, "Cancelled after $sent labels were sent.")
            publish(JobStatus.CANCELLED, "Cancelled. $sent labels had already been sent.")
        } catch (e: Exception) {
            Log.w("PrintQueue", "Job $jobId failed", e)
            val msg = e.message ?: "Printing failed."
            repo.setJobStatus(jobId, JobStatus.FAILED.name, sent, msg)
            publish(JobStatus.FAILED, msg)
        } finally {
            runCatching { conn.close() }
            if (cancelRequested == jobId) cancelRequested = null
        }
    }

    companion object {
        /** Renders one label at the printer's real DPI and wraps it in that printer's command language. */
        fun buildBytes(label: PreparedLabel, printer: PrinterEntity, protocol: PrinterProtocol): ByteArray {
            val d = label.design
            if (d.widthMm > printer.headWidthMm + 0.5) {
                throw PrinterException(
                    "This label is ${Units.formatMm(d.widthMm)} mm wide but ${printer.name} prints only ${Units.formatMm(printer.headWidthMm)} mm. " +
                        "Choose a narrower label size. The app does not shrink labels to fit."
                )
            }
            val r = LabelRenderer.render(d, label.data, printer.dpi, 1, forPrint = true)
            val mono = LabelRenderer.toMono(r.bitmap, thresholdFor(printer.density))
            r.bitmap.recycle()
            val centre = if (protocol == PrinterProtocol.ESC_POS) ((printer.headWidthMm - d.widthMm) / 2).coerceAtLeast(0.0) else 0.0
            val offset = Units.mmToDots(centre + printer.offsetXmm, printer.dpi).coerceAtLeast(0)
            return Drivers.forProtocol(protocol).build(
                RasterJob(mono, printer.dpi, d.widthMm, d.heightMm, label.gapMm, label.copies, printer.density, printer.speed,
                    continuousPaper = label.continuous, offsetXDots = offset)
            )
        }

        /** Higher density darkens the image slightly in software too, which helps faint ESC/POS heads. */
        fun thresholdFor(density: Int) = (130 + density.coerceIn(0, 15) * 4).coerceIn(120, 200)
    }
}
