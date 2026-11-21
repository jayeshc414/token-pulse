package dev.jayesh.tokenpulse.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.jayesh.tokenpulse.MainActivity
import dev.jayesh.tokenpulse.R
import dev.jayesh.tokenpulse.data.Fmt
import dev.jayesh.tokenpulse.data.LimitWindow
import dev.jayesh.tokenpulse.data.Prefs
import dev.jayesh.tokenpulse.data.Repository
import dev.jayesh.tokenpulse.data.Snapshot
import dev.jayesh.tokenpulse.widget.Widgets
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = Repository(applicationContext).refresh()
        Widgets.updateAll(applicationContext)
        r.getOrNull()?.let { Alerts.check(applicationContext, it) }
        return if (r.isSuccess || runAttemptCount >= 2) Result.success() else Result.retry()
    }
}

object Sync {
    private const val PERIODIC = "tp-periodic"
    private const val NOW = "tp-now"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val minutes = Prefs(context).refreshMinutes.coerceAtLeast(15).toLong()
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun now(context: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }
}

/** Heads-up when the session or weekly limit crosses a threshold — once per window per level. */
object Alerts {
    private const val CHANNEL = "limits"

    fun ensureChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Usage limits", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When your Claude Code session or weekly limit is running low"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun check(context: Context, s: Snapshot) {
        val prefs = Prefs(context)
        if (!prefs.alertsEnabled) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val plan = s.plan ?: return
        maybe(context, "session", plan.fiveHour, intArrayOf(75, 90), 101) { w, lvl ->
            "Session at ${Fmt.pct(w.pct)}" to "Crossed $lvl% of your 5-hour limit · resets in ${Fmt.until(w.resetsAt)}"
        }
        maybe(context, "weekly", plan.sevenDay, intArrayOf(80, 95), 102) { w, lvl ->
            "Weekly limit at ${Fmt.pct(w.pct)}" to "Crossed $lvl% · resets ${Fmt.weekdayAt(w.resetsAt)}"
        }
    }

    private fun maybe(
        context: Context,
        name: String,
        w: LimitWindow?,
        levels: IntArray,
        notifId: Int,
        text: (LimitWindow, Int) -> Pair<String, String>,
    ) {
        if (w == null) return
        val level = levels.lastOrNull { w.pct >= it } ?: return
        val sp = context.getSharedPreferences("token_pulse_alerts", Context.MODE_PRIVATE)
        val window = w.resetsAt?.toString() ?: "none"
        val sent = sp.getString(name, "") ?: ""
        val sentWindow = sent.substringBefore('|')
        val sentLevel = sent.substringAfter('|', "0").toIntOrNull() ?: 0
        if (sentWindow == window && sentLevel >= level) return
        sp.edit().putString(name, "$window|$level").apply()

        val (title, body) = text(w, level)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(0xFFE07A52.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId, n)
        } catch (_: SecurityException) {
        }
    }
}
