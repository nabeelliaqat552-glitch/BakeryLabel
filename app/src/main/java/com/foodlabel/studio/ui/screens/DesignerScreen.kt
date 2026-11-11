package com.foodlabel.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.core.Units
import com.foodlabel.studio.data.seed.TemplateLibrary
import com.foodlabel.studio.label.ElementType
import com.foodlabel.studio.label.FontOption
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.LabelElement
import com.foodlabel.studio.label.LabelRenderer
import com.foodlabel.studio.label.RenderResult
import com.foodlabel.studio.label.TextAlignOption
import com.foodlabel.studio.label.Tokens
import com.foodlabel.studio.printing.Output
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.Chip
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NumberField
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.RoundIconButton
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.SecondaryButton
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.Segmented
import com.foodlabel.studio.ui.components.ToggleRow
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SNAP_MM = 0.8

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false, danger: Boolean = false) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = when { active -> Fls.Chocolate; danger -> Fls.DangerBg; else -> Fls.Ivory },
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Fls.Chocolate else Fls.Line), modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = when { active -> Fls.GoldPale; danger -> Fls.Danger; else -> Fls.Ink }, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AddButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Fls.Sand), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Fls.Chocolate, modifier = Modifier.size(20.dp))
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * WYSIWYG label designer. The picture on screen is produced by the same renderer that prints,
 * at the printer's DPI. Quick mode offers simple switches; Advanced mode adds drag, resize and full styling.
 */
@Composable
fun DesignerScreen(designId: Long?, templateId: Long?, productId: Long?, onBack: () -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sizes by remember(business.id) { c.repo.labelSizes(business.id) }.collectAsStateWithLifecycle(emptyList())

    var design by remember { mutableStateOf<LabelDesign?>(null) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var advanced by remember { mutableStateOf(true) }
    val undo = remember { mutableStateListOf<LabelDesign>() }
    val redo = remember { mutableStateListOf<LabelDesign>() }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var guideX by remember { mutableStateOf<Double?>(null) }
    var guideY by remember { mutableStateOf<Double?>(null) }
    var rawX by remember { mutableStateOf(0.0) }
    var rawY by remember { mutableStateOf(0.0) }
    var dragging by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, NoticeKind>?>(null) }
    var tokenMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(designId, templateId, business.id) {
        val loaded = when {
            designId != null -> c.repo.getDesign(designId)?.takeIf { it.businessId == business.id }
            templateId != null -> c.repo.getDesign(templateId)?.let {
                it.copy(id = 0, businessId = business.id, isTemplate = false, builtInKey = null, name = it.name, groupName = "Custom")
            }
            else -> null
        }
        design = loaded ?: run {
            val s = business.defaultLabelSizeId?.let { c.repo.getLabelSize(it) }
            TemplateLibrary.blankStarter(s?.widthMm ?: 50.0, s?.heightMm ?: 30.0).copy(businessId = business.id)
        }
    }

    val sample = remember(business.id) {
        Tokens.SAMPLE.copy(businessName = business.name.uppercase(), businessNameUr = business.nameUrdu.ifBlank { Tokens.SAMPLE.businessNameUr },
            currency = business.currencySymbol, logoPath = business.logoPath)
    }

    val d = design
    if (d == null) { Column { ScreenHeader("Label Designer", null, onBack) }; return }

    fun change(newDesign: LabelDesign, record: Boolean = true) {
        if (record) { undo.add(d); if (undo.size > 60) undo.removeAt(0); redo.clear() }
        design = newDesign
    }
    fun update(uid: Long, record: Boolean = true, f: (LabelElement) -> LabelElement) =
        change(d.copy(elements = d.elements.map { if (it.uid == uid) f(it) else it }), record)
    val sel = d.elements.firstOrNull { it.uid == selected }
    fun add(e: LabelElement) {
        val z = (d.elements.maxOfOrNull { it.z } ?: 0) + 1
        val placed = e.copy(z = z, xMm = ((d.widthMm - e.wMm) / 2).coerceAtLeast(0.0), yMm = ((d.heightMm - e.hMm) / 2).coerceAtLeast(0.0))
        change(d.copy(elements = d.elements + placed)); selected = placed.uid
    }

    val render by produceState<RenderResult?>(null, d, sample) {
        value = withContext(Dispatchers.Default) { runCatching { LabelRenderer.render(d, sample, 203, 2) }.getOrNull() }
    }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "images").apply { mkdirs() }
                val f = File(dir, "img_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
                f.absolutePath
            }
            val side = minOf(d.widthMm, d.heightMm) * 0.4
            add(LabelElement(type = ElementType.IMAGE, xMm = 0.0, yMm = 0.0, wMm = side, hMm = side, imagePath = path))
        }
    }
    val exportPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { Output.writePng(d, sample, it) } }
            message = "PNG saved at ${Output.EXPORT_DPI} DPI." to NoticeKind.Success
        }
    }

    fun save(asTemplate: Boolean, asCopy: Boolean = false) = scope.launch {
        val toSave = d.copy(id = if (asTemplate || asCopy) 0 else d.id, isTemplate = asTemplate, businessId = business.id,
            groupName = if (asTemplate) "My templates" else d.groupName, name = d.name.ifBlank { "My label" } + if (asCopy) " copy" else "")
        val id = c.repo.saveDesign(toSave, business.id)
        if (!asTemplate) {
            design = d.copy(id = id, businessId = business.id, name = toSave.name)
            if (productId != null) c.repo.setProductDesign(business.id, productId, id)
        }
        message = (when {
            asTemplate -> "Saved to My templates."
            productId != null -> "Saved. This product now prints with this design."
            else -> "Design saved."
        }) to NoticeKind.Success
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(d.name.ifBlank { "New label" }, "${Units.formatMm(d.widthMm)} × ${Units.formatMm(d.heightMm)} mm · 203 DPI preview", onBack) {
            RoundIconButton(Icons.AutoMirrored.Outlined.Undo, "Undo", { if (undo.isNotEmpty()) { redo.add(d); design = undo.removeAt(undo.lastIndex) } }, enabled = undo.isNotEmpty())
            RoundIconButton(Icons.AutoMirrored.Outlined.Redo, "Redo", { if (redo.isNotEmpty()) { undo.add(d); design = redo.removeAt(redo.lastIndex) } }, enabled = redo.isNotEmpty())
        }
        Segmented(listOf(false, true), advanced, { if (it) "Advanced" else "Quick" }, { advanced = it; if (!it) selected = null },
            Modifier.padding(horizontal = 16.dp).fillMaxWidth())

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---------------- canvas ----------------
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fls.Sand).padding(14.dp)) {
                val ratio = (d.widthMm / d.heightMm).toFloat().coerceIn(0.3f, 5f)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White).onSizeChanged { canvas = it }
                        .pointerInput(advanced) {
                            if (!advanced) return@pointerInput
                            detectTapGestures { pos ->
                                val cur = design ?: return@detectTapGestures
                                val k = size.width / cur.widthMm
                                val xm = pos.x / k; val ym = pos.y / k
                                selected = cur.elements.sortedByDescending { it.z }
                                    .firstOrNull { xm >= it.xMm - 0.5 && xm <= it.xMm + it.wMm + 0.5 && ym >= it.yMm - 0.5 && ym <= it.yMm + maxOf(it.hMm, 1.5) + 0.5 }?.uid
                            }
                        }
                        .pointerInput(advanced) {
                            if (!advanced) return@pointerInput
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val cur = design ?: return@detectDragGestures
                                    val k = size.width / cur.widthMm
                                    val xm = pos.x / k; val ym = pos.y / k
                                    val hit = cur.elements.sortedByDescending { it.z }
                                        .firstOrNull { xm >= it.xMm - 1 && xm <= it.xMm + it.wMm + 1 && ym >= it.yMm - 1 && ym <= it.yMm + maxOf(it.hMm, 1.5) + 1 }
                                    if (hit != null) {
                                        selected = hit.uid; rawX = hit.xMm; rawY = hit.yMm; dragging = true
                                        undo.add(cur); redo.clear()
                                    } else dragging = false
                                },
                                onDragEnd = { dragging = false; guideX = null; guideY = null },
                                onDragCancel = { dragging = false; guideX = null; guideY = null },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val cur = design
                                    val e = cur?.elements?.firstOrNull { it.uid == selected }
                                    if (dragging && cur != null && e != null) {
                                        val k = size.width / cur.widthMm
                                        rawX += amount.x / k; rawY += amount.y / k
                                        var nx = rawX; var ny = rawY
                                        guideX = null; guideY = null
                                        // Alignment guides: label centre and edges.
                                        val cx = (cur.widthMm - e.wMm) / 2
                                        val cy = (cur.heightMm - e.hMm) / 2
                                        if (abs(nx - cx) < SNAP_MM) { nx = cx; guideX = cur.widthMm / 2 }
                                        else if (abs(nx) < SNAP_MM) { nx = 0.0; guideX = 0.0 }
                                        else if (abs(nx + e.wMm - cur.widthMm) < SNAP_MM) { nx = cur.widthMm - e.wMm; guideX = cur.widthMm }
                                        if (abs(ny - cy) < SNAP_MM) { ny = cy; guideY = cur.heightMm / 2 }
                                        else if (abs(ny) < SNAP_MM) { ny = 0.0; guideY = 0.0 }
                                        else if (abs(ny + e.hMm - cur.heightMm) < SNAP_MM) { ny = cur.heightMm - e.hMm; guideY = cur.heightMm }
                                        nx = nx.coerceIn(-e.wMm + 1, cur.widthMm - 1); ny = ny.coerceIn(-e.hMm + 1, cur.heightMm - 1)
                                        design = cur.copy(elements = cur.elements.map { if (it.uid == e.uid) it.copy(xMm = nx, yMm = ny) else it })
                                    }
                                },
                            )
                        },
                ) {
                    render?.bitmap?.let {
                        Image(it.asImageBitmap(), "Label", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    }
                    if (advanced) Canvas(Modifier.fillMaxSize()) {
                        val k = size.width / d.widthMm.toFloat()
                        // 5 mm grid
                        var gx = 5f
                        while (gx < d.widthMm) { drawLine(Color(0x22B8923F), Offset(gx * k, 0f), Offset(gx * k, size.height)); gx += 5f }
                        var gy = 5f
                        while (gy < d.heightMm) { drawLine(Color(0x22B8923F), Offset(0f, gy * k), Offset(size.width, gy * k)); gy += 5f }
                        d.elements.forEach { e ->
                            val isSel = e.uid == selected
                            val bad = render?.problemElements?.contains(e.uid) == true
                            if (isSel || bad || !e.visible) drawRect(
                                color = when { bad -> Fls.Danger; isSel -> Fls.GoldRing; else -> Fls.Muted },
                                topLeft = Offset(e.xMm.toFloat() * k, e.yMm.toFloat() * k),
                                size = Size(e.wMm.toFloat() * k, maxOf(e.hMm, 0.6).toFloat() * k),
                                style = Stroke(width = if (isSel) 4f else 2f, pathEffect = if (isSel) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                            )
                        }
                        guideX?.let { drawLine(Fls.Danger, Offset(it.toFloat() * k, 0f), Offset(it.toFloat() * k, size.height), 2f) }
                        guideY?.let { drawLine(Fls.Danger, Offset(0f, it.toFloat() * k), Offset(size.width, it.toFloat() * k), 2f) }
                    }
                    // Resize handle on the selected element's bottom-right corner.
                    if (advanced && sel != null && canvas.width > 0) {
                        val k = canvas.width / d.widthMm
                        Box(
                            Modifier.offset { IntOffset(((sel.xMm + sel.wMm) * k).roundToInt() - 22, ((sel.yMm + sel.hMm) * k).roundToInt() - 22) }
                                .size(28.dp).clip(CircleShape).background(Fls.Chocolate)
                                .pointerInput(sel.uid) {
                                    detectDragGestures(
                                        onDragStart = { design?.let { undo.add(it); redo.clear() } },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val cur = design ?: return@detectDragGestures
                                            val kk = canvas.width / cur.widthMm
                                            design = cur.copy(elements = cur.elements.map {
                                                if (it.uid == sel.uid) it.copy(
                                                    wMm = (it.wMm + amount.x / kk).coerceIn(2.0, cur.widthMm),
                                                    hMm = if (it.type == ElementType.LINE) it.hMm else (it.hMm + amount.y / kk).coerceIn(1.5, cur.heightMm),
                                                ) else it
                                            })
                                        },
                                    )
                                },
                        )
                    }
                }
            }
            render?.warnings?.forEach { Notice(it, NoticeKind.Warning) }
            message?.let { Notice(it.first, it.second) }

            if (!advanced) QuickPanel(d, sizes.map { Triple(it.name, it.widthMm, it.heightMm) }, onChange = { change(it) })
            else {
                // ---------------- selected element ----------------
                if (sel != null) CardBlock {
                    SectionLabel(sel.displayName)
                    Text("X ${Units.formatMm(sel.xMm)} · Y ${Units.formatMm(sel.yMm)} · ${Units.formatMm(sel.wMm)} × ${Units.formatMm(sel.hMm)} mm" +
                        if (sel.type == ElementType.TEXT || sel.type == ElementType.BARCODE) " · ${sel.fontPt} pt" else "",
                        style = MaterialTheme.typography.bodySmall)
                    if (sel.type == ElementType.TEXT || sel.type == ElementType.QR) {
                        Field(sel.content, { v -> update(sel.uid) { it.copy(content = v) } }, if (sel.type == ElementType.QR) "QR content" else "Text", singleLine = false)
                        Box {
                            TextButton(onClick = { tokenMenu = true }) { Text("+ Insert a field (name, date, price…)") }
                            DropdownMenu(tokenMenu, { tokenMenu = false }) {
                                Tokens.FIELDS.forEach { f ->
                                    DropdownMenuItem(text = { Text(f.label) }, onClick = { tokenMenu = false; update(sel.uid) { it.copy(content = it.content + f.token) } })
                                }
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sel.type == ElementType.TEXT || sel.type == ElementType.BARCODE) {
                            Surface(onClick = { update(sel.uid) { it.copy(fontPt = (it.fontPt - 0.5).coerceAtLeast(4.0)) } }, shape = RoundedCornerShape(12.dp), color = Fls.Ivory,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Fls.Line), modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Text("A−", fontWeight = FontWeight.Bold) } }
                            Surface(onClick = { update(sel.uid) { it.copy(fontPt = (it.fontPt + 0.5).coerceAtMost(72.0)) } }, shape = RoundedCornerShape(12.dp), color = Fls.Ivory,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Fls.Line), modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Text("A+", fontWeight = FontWeight.Bold, fontSize = 17.sp) } }
                        }
                        if (sel.type == ElementType.TEXT) {
                            ToolButton(Icons.Outlined.FormatBold, "Bold", { update(sel.uid) { it.copy(bold = !it.bold) } }, sel.bold)
                            ToolButton(Icons.Outlined.FormatItalic, "Italic", { update(sel.uid) { it.copy(italic = !it.italic) } }, sel.italic)
                            ToolButton(Icons.AutoMirrored.Outlined.FormatAlignLeft, "Align start", { update(sel.uid) { it.copy(align = TextAlignOption.START) } }, sel.align == TextAlignOption.START)
                            ToolButton(Icons.Outlined.FormatAlignCenter, "Align centre", { update(sel.uid) { it.copy(align = TextAlignOption.CENTER) } }, sel.align == TextAlignOption.CENTER)
                            ToolButton(Icons.AutoMirrored.Outlined.FormatAlignRight, "Align end", { update(sel.uid) { it.copy(align = TextAlignOption.END) } }, sel.align == TextAlignOption.END)
                        }
                        if (sel.type == ElementType.TEXT || sel.type == ElementType.BOX)
                            ToolButton(Icons.Outlined.InvertColors, "White on black", { update(sel.uid) { it.copy(inverted = !it.inverted) } }, sel.inverted)
                        ToolButton(Icons.Outlined.RotateRight, "Rotate 90°", { update(sel.uid) { it.copy(rotation = (it.rotation + 90) % 360) } })
                        ToolButton(if (sel.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "Show or hide", { update(sel.uid) { it.copy(visible = !it.visible) } })
                        ToolButton(Icons.Outlined.FlipToFront, "Bring to front", { update(sel.uid) { it.copy(z = (d.elements.maxOf { e -> e.z }) + 1) } })
                        ToolButton(Icons.Outlined.FlipToBack, "Send to back", { update(sel.uid) { it.copy(z = (d.elements.minOf { e -> e.z }) - 1) } })
                        ToolButton(Icons.Outlined.ContentCopy, "Duplicate", {
                            val copy = sel.copy(uid = LabelElement.nextUid(), xMm = sel.xMm + 2, yMm = sel.yMm + 2, z = d.elements.maxOf { it.z } + 1)
                            change(d.copy(elements = d.elements + copy)); selected = copy.uid
                        })
                        ToolButton(Icons.Outlined.Delete, "Delete", { change(d.copy(elements = d.elements.filter { it.uid != sel.uid })); selected = null }, danger = true)
                    }
                    if (sel.type == ElementType.TEXT) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FontOption.entries.forEach { f -> Chip(f.label, sel.font == f, { update(sel.uid) { it.copy(font = f) } }) }
                            Chip("1 line", sel.maxLines == 1, { update(sel.uid) { it.copy(maxLines = 1) } })
                            Chip("2 lines", sel.maxLines == 2, { update(sel.uid) { it.copy(maxLines = 2) } })
                            Chip("3 lines", sel.maxLines == 3, { update(sel.uid) { it.copy(maxLines = 3) } })
                        }
                        Text("English and Urdu can be mixed in one text. Urdu runs right to left automatically.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (sel.type == ElementType.BARCODE) {
                        ToggleRow("Show the number under the bars", sel.humanReadable, { v -> update(sel.uid) { it.copy(humanReadable = v) } })
                        Text("The barcode type and number come from each product. The app keeps bars a whole number of printer dots wide so they scan.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else Text("Tap an item on the label to edit it. Drag to move. Drag the brown dot to resize.", style = MaterialTheme.typography.bodySmall)

                // ---------------- add ----------------
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    val w = d.widthMm; val h = d.heightMm
                    AddButton(Icons.Outlined.TextFields, "Text") { add(LabelElement(type = ElementType.TEXT, xMm = 0.0, yMm = 0.0, wMm = w * 0.6, hMm = h * 0.18, content = "Your text", fontPt = 8.0)) }
                    AddButton(Icons.Outlined.Translate, "Urdu") { add(LabelElement(type = ElementType.TEXT, xMm = 0.0, yMm = 0.0, wMm = w * 0.5, hMm = h * 0.25, content = "{name_ur}", fontPt = 10.0, align = TextAlignOption.END)) }
                    AddButton(Icons.Outlined.CalendarMonth, "Date") { add(LabelElement(type = ElementType.TEXT, xMm = 0.0, yMm = 0.0, wMm = w * 0.5, hMm = h * 0.14, content = "EXP {exp}", fontPt = 7.0, bold = true)) }
                    AddButton(Icons.Outlined.Sell, "Price") { add(LabelElement(type = ElementType.TEXT, xMm = 0.0, yMm = 0.0, wMm = w * 0.4, hMm = h * 0.18, content = "{price}", fontPt = 10.0, bold = true)) }
                    AddButton(Icons.Outlined.ViewWeek, "Barcode") { add(LabelElement(type = ElementType.BARCODE, xMm = 0.0, yMm = 0.0, wMm = w * 0.8, hMm = h * 0.3, content = "{barcode}", fontPt = 6.0)) }
                    AddButton(Icons.Outlined.QrCode2, "QR") { val s = minOf(w, h) * 0.45; add(LabelElement(type = ElementType.QR, xMm = 0.0, yMm = 0.0, wMm = s, hMm = s, content = "{name_en}|{batch}|{exp}")) }
                    AddButton(Icons.Outlined.Image, "Logo") {
                        if (business.logoPath != null) { val s = minOf(w, h) * 0.35; add(LabelElement(type = ElementType.IMAGE, xMm = 0.0, yMm = 0.0, wMm = s, hMm = s)) }
                        else pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    AddButton(Icons.Outlined.HorizontalRule, "Line") { add(LabelElement(type = ElementType.LINE, xMm = 0.0, yMm = 0.0, wMm = w * 0.8, hMm = 0.3, strokeMm = 0.3)) }
                    AddButton(Icons.Outlined.CropSquare, "Box") { add(LabelElement(type = ElementType.BOX, xMm = 0.0, yMm = 0.0, wMm = w * 0.5, hMm = h * 0.3)) }
                }
            }

            // ---------------- label settings ----------------
            TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Hide label settings" else "Label name, border, margins and background") }
            if (showSettings) CardBlock {
                Field(d.name, { v -> change(d.copy(name = v), record = false) }, "Design name")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(d.borderMm, { change(d.copy(borderMm = it.coerceIn(0.0, 3.0))) }, "Border (mm)", Modifier.weight(1f))
                    NumberField(d.insetMm, { change(d.copy(insetMm = it.coerceIn(0.0, 10.0))) }, "Border inset (mm)", Modifier.weight(1f))
                }
                NumberField(d.cornerMm, { change(d.copy(cornerMm = it.coerceIn(0.0, 20.0))) }, "Rounded corner (mm)")
                ToggleRow("Round label", d.round, { change(d.copy(round = it)) })
                ToggleRow("Black background, white print", d.backgroundBlack, { change(d.copy(backgroundBlack = it)) }, "Uses a lot of ink ribbon or heat. Test on your printer.")
                SecondaryButton("Save picture (PNG)", { exportPng.launch("${d.name.ifBlank { "label" }}.png") }, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Template", { save(asTemplate = true) }, modifier = Modifier.weight(1f))
            if (d.id != 0L) SecondaryButton("Copy", { save(asTemplate = false, asCopy = true) }, modifier = Modifier.weight(1f))
            PrimaryButton("Save", { save(asTemplate = false) }, modifier = Modifier.weight(1.3f))
        }
    }
}

/** Quick mode: no dragging, only plain choices. */
@Composable
private fun QuickPanel(d: LabelDesign, sizes: List<Triple<String, Double, Double>>, onChange: (LabelDesign) -> Unit) {
    CardBlock {
        SectionLabel("Label size")
        Text("Choosing another size re-fits the layout. The printed label is always the real size you choose.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEach { (name, w, h) ->
                Chip(name, d.widthMm == w && d.heightMm == h, { if (d.widthMm != w || d.heightMm != h) onChange(TemplateLibrary.adaptTo(d, w, h)) })
            }
        }
    }
    CardBlock {
        SectionLabel("Show on this label")
        val groups = listOf("Business name" to "business", "Product name (English)" to "name_en", "Product name (Urdu)" to "name_ur",
            "Manufacturing date" to "mfg", "Expiry date" to "exp", "Price" to "price", "Weight" to "weight", "Batch number" to "batch")
        groups.forEach { (label, key) ->
            val els = d.elements.filter { it.type == ElementType.TEXT && key in Tokens.keysIn(it.content) }
            if (els.isNotEmpty()) ToggleRow(label, els.all { it.visible }, { on ->
                onChange(d.copy(elements = d.elements.map { if (it in els) it.copy(visible = on) else it }))
            })
        }
        val codes = d.elements.filter { it.type == ElementType.BARCODE || it.type == ElementType.QR }
        if (codes.isNotEmpty()) ToggleRow("Barcode / QR", codes.all { it.visible }, { on ->
            onChange(d.copy(elements = d.elements.map { if (it in codes) it.copy(visible = on) else it }))
        })
        val barcodes = d.elements.filter { it.type == ElementType.BARCODE }
        if (barcodes.isNotEmpty()) ToggleRow("Number under barcode", barcodes.all { it.humanReadable }, { on ->
            onChange(d.copy(elements = d.elements.map { if (it in barcodes) it.copy(humanReadable = on) else it }))
        })
    }
}
