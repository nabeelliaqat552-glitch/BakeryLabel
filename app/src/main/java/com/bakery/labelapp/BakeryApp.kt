package com.bakery.labelapp

import android.app.Application
import com.bakery.labelapp.data.AppDatabase
import com.bakery.labelapp.data.CloudSync
import com.bakery.labelapp.data.Repository
import com.bakery.labelapp.print.PrinterStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BakeryApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var db: AppDatabase
    lateinit var cloud: CloudSync
    lateinit var repo: Repository
    lateinit var printerStore: PrinterStore

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        cloud = CloudSync(this, appScope).also { it.db = db }
        repo = Repository(db, cloud)
        printerStore = PrinterStore(this)
        appScope.launch {
            repo.ensureSeed()
            cloud.start()
        }
    }
}
