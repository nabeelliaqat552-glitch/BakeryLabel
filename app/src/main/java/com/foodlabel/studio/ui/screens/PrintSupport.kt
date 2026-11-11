package com.foodlabel.studio.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.AppContainer
import com.foodlabel.studio.data.db.PrintJobEntity
import com.foodlabel.studio.data.db.PrinterEntity
import com.foodlabel.studio.printing.BatchItem
import com.foodlabel.studio.printing.JobStatus
import com.foodlabel.studio.printing.Output
import com.foodlabel.studio.printing.driver.PrinterProtocol
import com.foodlabel.studio.printing.connection.ConnectionType
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.SecondaryButton
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PrintStart {
    data class Started(val jobId: Long) : PrintStart
    data class Error(val message: String) : PrintStart
}

fun isAndroidPrint(p: PrinterEntity) =
    ConnectionType.parse(p.connection) == ConnectionType.ANDROID_PRINT || PrinterProtocol.parse(p.protocol) == PrinterProtocol.ANDROID_PRINT

/** Starts a print on any kind of printer and returns the job id to follow. */
suspend fun startPrint(
    context: Context, c: AppContainer, businessId: Long, printer: PrinterEntity, items: List<BatchItem>, savedName: String? = null,
): PrintStart = try {
    if (items.isEmpty() || items.all { it.copies <= 0 }) PrintStart.Error("Nothing to print.")
    else if (isAndroidPrint(printer)) {
        val labels = c.printQueue.prepare(businessId, items)
        val total = labels.sumOf { it.copies }
        val jobId = c.repo.insertJob(
            PrintJobEntity(businessId = businessId, printerId = printer.id, printerName = printer.name, totalLabels = total,
                status = JobStatus.SENDING.name, payload = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(BatchItem.serializer()), items), savedName = savedName),
            labels.map { it.history },
        )
        withContext(Dispatchers.Main) {
            Output.printWithAndroid(context, "FoodLabel labels", labels) { ok, err ->
                c.appScope.launch {
                    c.repo.setJobStatus(jobId, if (ok) JobStatus.SENT.name else JobStatus.FAILED.name, if (ok) total else 0,
                        if (ok) null else (err ?: "Android Print could not prepare the document."))
                }
            }
        }
        PrintStart.Started(jobId)
    } else PrintStart.Started(c.printQueue.enqueue(businessId, printer, items, savedName))
} catch (e: Exception) {
    PrintStart.Error(e.message ?: "Could not start printing.")
}

/**
 * Live status for a print job. "Sent" is never shown as "Printed": the user confirms what came out.
 */
@Composable
fun PrintStatusCard(jobId: Long, onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val q by c.printQueue.state.collectAsStateWithLifecycle()
    val live = q.takeIf { it.jobId == jobId }
    // Android Print jobs are not run by the queue; read their status from the database.
    val dbJob by androidx.compose.runtime.produceState<PrintJobEntity?>(null, jobId) {
        while (true) { value = c.repo.getJob(jobId); kotlinx.coroutines.delay(700) }
    }
    val status = live?.status ?: dbJob?.let { JobStatus.parse(it.status) } ?: JobStatus.QUEUED
    val sent = live?.labelsSent ?: dbJob?.sentLabels ?: 0
    val total = live?.labelsTotal ?: dbJob?.totalLabels ?: 0
    val message = live?.message ?: dbJob?.error

    CardBlock {
        Text(status.label, fontWeight = FontWeight.Bold)
        if (status == JobStatus.QUEUED || status == JobStatus.SENDING) {
            LinearProgressIndicator(progress = { if (total == 0) 0f else sent / total.toFloat() }, modifier = Modifier.fillMaxWidth(),
                color = Fls.Chocolate, trackColor = Fls.Sand)
        }
        message?.let {
            Notice(it, when (status) {
                JobStatus.FAILED -> NoticeKind.Error
                JobStatus.SENT -> NoticeKind.Warning
                JobStatus.CONFIRMED -> NoticeKind.Success
                else -> NoticeKind.Info
            })
        }
        when (status) {
            JobStatus.QUEUED, JobStatus.SENDING ->
                SecondaryButton("Cancel printing", { c.printQueue.cancel(jobId) }, modifier = Modifier.fillMaxWidth(), danger = true)
            JobStatus.SENT -> {
                Text("Did the labels print correctly?", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("No, a problem", { scope.launch { c.printQueue.confirm(jobId, false) } }, modifier = Modifier.weight(1f))
                    PrimaryButton("Yes, printed", { scope.launch { c.printQueue.confirm(jobId, true); onDone() } }, modifier = Modifier.weight(1f))
                }
            }
            else -> SecondaryButton("Close", onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

