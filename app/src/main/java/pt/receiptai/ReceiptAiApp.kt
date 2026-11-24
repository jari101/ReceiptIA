package pt.receiptai

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pt.receiptai.work.ExpiryCheckWorker

class ReceiptAiApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.recipes.seedIfEmpty() }
        ExpiryCheckWorker.createChannel(this)
        ExpiryCheckWorker.schedule(this)
    }
}
