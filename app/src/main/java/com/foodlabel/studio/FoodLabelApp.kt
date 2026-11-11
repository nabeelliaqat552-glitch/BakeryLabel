package com.foodlabel.studio

import android.app.Application
import android.util.Log
import com.foodlabel.studio.backup.BackupManager
import com.foodlabel.studio.data.db.AppDatabase
import com.foodlabel.studio.data.repo.Repository
import com.foodlabel.studio.printing.PrintQueue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container: one database, repository, print queue and backup manager per process. */
class AppContainer(app: Application) {
    val db: AppDatabase = AppDatabase.create(app)
    val repo = Repository(db)
    val printQueue = PrintQueue(app, repo)
    val backup = BackupManager(app, repo)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        Log.e("FoodLabel", "Background task failed", e)
    })
}

class FoodLabelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.repo.ensureBuiltIns() }
    }
}
