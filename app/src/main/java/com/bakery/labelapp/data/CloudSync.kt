package com.bakery.labelapp.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Two-way sync between the local Room database (always the source the app reads)
 * and Cloud Firestore at bakeries/{bakeryCode}/...
 *
 * Offline first: writes go to Room immediately. Firestore keeps its own offline
 * queue, so changes handed to it while offline are uploaded when the phone is back online.
 * Conflicts: the newer updatedAt wins.
 */
class CloudSync(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    lateinit var db: AppDatabase

    private val prefs = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    private val pushLock = Mutex()
    private val listeners = mutableListOf<ListenerRegistration>()

    val available: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    private val _status = MutableStateFlow(
        if (available) "Not connected" else "Cloud not set up (add google-services.json). Working offline."
    )
    val status: StateFlow<String> = _status

    private val _bakeryCode = MutableStateFlow(prefs.getString("bakeryCode", "") ?: "")
    val bakeryCode: StateFlow<String> = _bakeryCode

    fun setBakeryCode(raw: String) {
        val code = raw.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        prefs.edit().putString("bakeryCode", code).apply()
        _bakeryCode.value = code
        start()
    }

    fun start() {
        listeners.forEach { it.remove() }
        listeners.clear()
        if (!available) return
        val code = _bakeryCode.value
        if (code.isBlank()) {
            _status.value = "Enter a bakery code to turn on cloud sync"
            return
        }
        scope.launch {
            try {
                _status.value = "Connecting…"
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) auth.signInAnonymously().await()
                val root = root(code)

                // A brand-new bakery space in the cloud: upload everything this phone has.
                val empty = root.collection("products").limit(1).get().await().isEmpty
                if (empty) {
                    db.products().markAllDirty()
                    db.batches().markAllDirty()
                    db.settings().markDirty()
                }
                listen(root)
                push()
                _status.value = "Cloud sync on · $code"
            } catch (e: Exception) {
                _status.value = "Sync paused: ${e.message ?: "no connection"}. Data is safe on this phone."
            }
        }
    }

    fun requestPush() {
        scope.launch {
            try {
                push()
            } catch (e: Exception) {
                _status.value = "Sync pending: ${e.message}"
            }
        }
    }

    private fun root(code: String): DocumentReference =
        FirebaseFirestore.getInstance().collection("bakeries").document(code)

    private suspend fun push(): Unit = pushLock.withLock {
        if (!available) return@withLock
        val code = _bakeryCode.value
        if (code.isBlank() || FirebaseAuth.getInstance().currentUser == null) return@withLock
        val root = root(code)
        // set() without await: Firestore queues the write offline and persists it.
        db.products().dirty().forEach {
            root.collection("products").document(it.id).set(it.toMap())
            db.products().markClean(it.id, it.updatedAt)
        }
        db.batches().dirty().forEach {
            root.collection("batches").document(it.id).set(it.toMap())
            db.batches().markClean(it.id, it.updatedAt)
        }
        db.settings().get()?.takeIf { it.dirty }?.let {
            root.collection("config").document("label").set(it.toMap())
            db.settings().markClean(it.updatedAt)
        }
        Unit
    }

    private fun listen(root: DocumentReference) {
        listeners += root.collection("products").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: return@addSnapshotListener
            scope.launch {
                docs.mapNotNull { productFrom(it) }.forEach { remote ->
                    val local = db.products().get(remote.id)
                    if (local == null || remote.updatedAt > local.updatedAt) db.products().upsert(remote)
                }
            }
        }
        listeners += root.collection("batches").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: return@addSnapshotListener
            scope.launch {
                docs.mapNotNull { batchFrom(it) }.forEach { remote ->
                    val local = db.batches().get(remote.id)
                    if (local == null || remote.updatedAt > local.updatedAt) db.batches().upsert(remote)
                }
            }
        }
        listeners += root.collection("config").document("label").addSnapshotListener { snap, _ ->
            val remote = snap?.let { settingsFrom(it) } ?: return@addSnapshotListener
            scope.launch {
                val local = db.settings().get()
                if (local == null || remote.updatedAt > local.updatedAt) db.settings().upsert(remote)
            }
        }
    }
}

// ---- Firestore mapping ----

private fun Product.toMap() = mapOf(
    "name" to name, "shelfLifeDays" to shelfLifeDays, "active" to active,
    "sortOrder" to sortOrder, "updatedAt" to updatedAt,
)

private fun productFrom(d: DocumentSnapshot): Product? {
    val name = d.getString("name") ?: return null
    return Product(
        id = d.id, name = name,
        shelfLifeDays = (d.getLong("shelfLifeDays") ?: 1L).toInt(),
        active = d.getBoolean("active") ?: true,
        sortOrder = (d.getLong("sortOrder") ?: 0L).toInt(),
        updatedAt = d.getLong("updatedAt") ?: 0L,
        dirty = false,
    )
}

private fun Batch.toMap() = mapOf(
    "code" to code, "productId" to productId, "productName" to productName,
    "mfgEpochDay" to mfgEpochDay, "expEpochDay" to expEpochDay, "expManual" to expManual,
    "copiesPrinted" to copiesPrinted, "createdAt" to createdAt, "updatedAt" to updatedAt,
    "deleted" to deleted,
)

private fun batchFrom(d: DocumentSnapshot): Batch? {
    val code = d.getString("code") ?: return null
    return Batch(
        id = d.id, code = code,
        productId = d.getString("productId") ?: "",
        productName = d.getString("productName") ?: "",
        mfgEpochDay = d.getLong("mfgEpochDay") ?: return null,
        expEpochDay = d.getLong("expEpochDay") ?: return null,
        expManual = d.getBoolean("expManual") ?: false,
        copiesPrinted = (d.getLong("copiesPrinted") ?: 0L).toInt(),
        createdAt = d.getLong("createdAt") ?: 0L,
        updatedAt = d.getLong("updatedAt") ?: 0L,
        deleted = d.getBoolean("deleted") ?: false,
        dirty = false,
    )
}

private fun LabelSettings.toMap() = mapOf(
    "bakeryName" to bakeryName, "subtitle" to subtitle, "showSubtitle" to showSubtitle,
    "widthMm" to widthMm, "heightMm" to heightMm, "gapMm" to gapMm, "fontScale" to fontScale,
    "borderMm" to borderMm, "cornerMm" to cornerMm,
    "backgroundColor" to backgroundColor, "textColor" to textColor,
    "borderColor" to borderColor, "expiryColor" to expiryColor,
    "dateFormat" to dateFormat, "defaultCopies" to defaultCopies, "adminPin" to adminPin,
    "updatedAt" to updatedAt,
)

private fun settingsFrom(d: DocumentSnapshot): LabelSettings? {
    if (!d.exists()) return null
    val def = LabelSettings()
    fun f(key: String, fallback: Float) = d.getDouble(key)?.toFloat() ?: fallback
    fun l(key: String, fallback: Long) = d.getLong(key) ?: fallback
    return LabelSettings(
        bakeryName = d.getString("bakeryName") ?: def.bakeryName,
        subtitle = d.getString("subtitle") ?: def.subtitle,
        showSubtitle = d.getBoolean("showSubtitle") ?: true,
        widthMm = f("widthMm", def.widthMm),
        heightMm = f("heightMm", def.heightMm),
        gapMm = f("gapMm", def.gapMm),
        fontScale = f("fontScale", def.fontScale),
        borderMm = f("borderMm", def.borderMm),
        cornerMm = f("cornerMm", def.cornerMm),
        backgroundColor = l("backgroundColor", def.backgroundColor),
        textColor = l("textColor", def.textColor),
        borderColor = l("borderColor", def.borderColor),
        expiryColor = l("expiryColor", def.expiryColor),
        dateFormat = d.getString("dateFormat") ?: def.dateFormat,
        defaultCopies = l("defaultCopies", 1L).toInt(),
        adminPin = d.getString("adminPin") ?: def.adminPin,
        updatedAt = l("updatedAt", 0L),
        dirty = false,
    )
}
