package ca.shadowfoxtv.taskkiller

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OptimizeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("shadowfox_optimizer", MODE_PRIVATE)
        if (prefs.getBoolean("in_progress", false)) return START_NOT_STICKY
        prefs.edit().putBoolean("in_progress", true).commit()
        scope.launch {
            try {
                val result = AppOptimizer(applicationContext).optimize()
                prefs.edit()
                    .putBoolean("has_run", true)
                    .putInt("closed_apps", result.closedApps)
                    .putLong("ram_freed", result.ramFreedBytes)
                    .putLong("storage_freed", result.storageFreedBytes)
                    .putBoolean("root_used", result.rootUsed)
                    .putBoolean("in_progress", false)
                    .commit()
            } finally {
                prefs.edit().putBoolean("in_progress", false).commit()
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
