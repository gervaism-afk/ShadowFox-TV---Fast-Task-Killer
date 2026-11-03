package ca.shadowfoxtv.taskkiller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger

object HealthAlerts {
    private const val JOB_ID = 7201
    private const val STORAGE_ID = 7202
    private const val HEAT_ID = 7203
    private const val CHANNEL = "shadowfox_health"
    private var foregroundActivities = 0

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (!UserToolsManager(context).healthSettings().lowStorage) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
        runCatching {
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, HealthCheckJob::class.java))
                .setPeriodic(6 * 60 * 60 * 1000L).setPersisted(true).build())
        }
    }

    fun foregroundStarted(context: Context) {
        foregroundActivities++
        if (foregroundActivities == 1) {
            Thread({ checkStorage(context.applicationContext) }, "ShadowFoxStorageCheck").start()
            if (Build.VERSION.SDK_INT >= 29) ThermalMonitor.refresh(context.applicationContext, true)
        }
    }

    fun foregroundStopped(context: Context) {
        foregroundActivities = (foregroundActivities - 1).coerceAtLeast(0)
        if (foregroundActivities == 0 && Build.VERSION.SDK_INT >= 29) ThermalMonitor.refresh(context.applicationContext, false)
    }

    fun settingsChanged(context: Context) {
        schedule(context)
        val settings = UserToolsManager(context).healthSettings()
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!settings.lowStorage) notifications.cancel(STORAGE_ID)
        if (!settings.heat) notifications.cancel(HEAT_ID)
        Thread({ checkStorage(context.applicationContext) }, "ShadowFoxStorageCheck").start()
        if (Build.VERSION.SDK_INT >= 29) ThermalMonitor.refresh(context.applicationContext, foregroundActivities > 0)
    }

    @Synchronized fun checkStorage(context: Context) {
        runCatching {
            val settings = UserToolsManager(context).healthSettings()
            if (!settings.lowStorage) return
            val prefs = context.getSharedPreferences("shadowfox_user_tools", Context.MODE_PRIVATE)
            val previous = prefs.getBoolean("storage_active", false)
            val free = StatFs(Environment.getDataDirectory().absolutePath).availableBytes
            val active = HealthAlertPolicy.lowStorage(free, settings.thresholdMb * 1048576L, previous)
            update(context, "storage", STORAGE_ID, active, previous, "Storage is running low",
                "${free / 1048576} MB free. Open ShadowFox for storage settings.")
        }
    }

    @Synchronized private fun checkThermal(context: Context, status: Int) {
        if (!UserToolsManager(context).healthSettings().heat) return
        val prefs = context.getSharedPreferences("shadowfox_user_tools", Context.MODE_PRIVATE)
        val previous = prefs.getBoolean("heat_active", false)
        val active = HealthAlertPolicy.hot(status, previous)
        update(context, "heat", HEAT_ID, active, previous, "Device heat warning",
            "Android reports high thermal stress. Check ventilation and let the device cool.")
    }

    private fun update(context: Context, key: String, id: Int, active: Boolean, previous: Boolean, title: String, text: String) {
        val prefs = context.getSharedPreferences("shadowfox_user_tools", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val notify = HealthAlertPolicy.notify(active, previous, now, prefs.getLong("${key}_sent", 0))
        // Do not mark an undelivered warning as sent: enabling notifications should allow delivery.
        val delivered = notify && post(context, id, title, text)
        prefs.edit().putBoolean("${key}_active", if (active && notify && !delivered) false else active)
            .apply()
        if (delivered) prefs.edit().putLong("${key}_sent", now).apply()
        if (!active) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }

    private fun post(context: Context, id: Int, title: String, text: String): Boolean = runCatching {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Device health alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, UltimateCenterActivity::class.java).putExtra("shadowfox_tab", "SYSTEM")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shadowfox_cache).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).build()
        manager.notify(id, notification)
        true
    }.getOrDefault(false)

    fun thermalLabel(context: Context): String {
        if (Build.VERSION.SDK_INT < 29) return "UNAVAILABLE"
        val status = runCatching { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus }.getOrNull()
        return when (status) {
            0 -> "NORMAL"; 1 -> "WARM"; 2 -> "MODERATE"; 3 -> "HOT"
            4, 5, 6 -> "CRITICAL"; else -> "UNAVAILABLE"
        }
    }

    @RequiresApi(29)
    private object ThermalMonitor {
        private var listener: PowerManager.OnThermalStatusChangedListener? = null

        fun refresh(context: Context, foreground: Boolean) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            listener?.let { runCatching { power.removeThermalStatusListener(it) } }
            listener = null
            if (!foreground || !UserToolsManager(context).healthSettings().heat) return
            val callback = PowerManager.OnThermalStatusChangedListener { checkThermal(context, it) }
            runCatching {
                power.addThermalStatusListener(callback)
                listener = callback
                checkThermal(context, power.currentThermalStatus)
            }
        }
    }
}

class HealthCheckJob : JobService() {
    private val generation = AtomicInteger()
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val token = generation.incrementAndGet()
        worker = Thread({
            try { HealthAlerts.checkStorage(applicationContext) }
            finally { Handler(Looper.getMainLooper()).post { if (generation.get() == token) jobFinished(params, false) } }
        }, "ShadowFoxHealthJob").also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        generation.incrementAndGet()
        worker?.interrupt()
        return UserToolsManager(this).healthSettings().lowStorage
    }

    override fun onDestroy() {
        generation.incrementAndGet()
        worker?.interrupt()
        super.onDestroy()
    }
}
