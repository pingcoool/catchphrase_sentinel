package com.audiokit.catchphrase.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.audiokit.catchphrase.MainActivity
import com.audiokit.catchphrase.R
import com.audiokit.catchphrase.data.AppSettings

object NotificationHelper {

    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALERT = "alert"
    const val NOTIF_MONITOR = 1001
    const val NOTIF_ALERT = 2001
    const val NOTIF_RESUME = 3001
    const val NOTIF_ERROR = 4001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.channel_monitor),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "常驻显示监听状态与今日次数"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        // 声音与震动由 App 按设置自行控制，避免与通知渠道重复
        val alert = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.channel_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "检测到口头禅时的提醒"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannels(listOf(monitor, alert))
    }

    fun monitorNotification(context: Context, count: Int): Notification {
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            context, 2,
            Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("口头禅哨兵正在监听")
            .setContentText("今日已捕获 $count 次 · 点击查看")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "停止监听", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showAlert(context: Context, phrase: String, settings: AppSettings, screenOff: Boolean) {
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("又说了「$phrase」")
            .setContentText("刚刚 · 已记入时间线")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(0)
            .setContentIntent(open)

        if (screenOff && settings.wakeScreen) {
            val card = PendingIntent.getActivity(
                context, 4,
                Intent(context, WakeCardActivity::class.java)
                    .putExtra(WakeCardActivity.EXTRA_PHRASE, phrase)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(card, true)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ALERT, builder.build())
        }
    }

    fun showResumeNotification(context: Context) {
        val open = PendingIntent.getActivity(
            context, 5,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("口头禅哨兵已暂停")
            .setContentText("点击打开 App 恢复监听")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_RESUME, notification)
        }
    }

    fun showError(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("监听启动失败")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ERROR, notification)
        }
    }
}
