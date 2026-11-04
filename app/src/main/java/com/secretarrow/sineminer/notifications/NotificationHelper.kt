package com.secretarrow.sineminer.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.secretarrow.sineminer.MainActivity
import com.secretarrow.sineminer.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages notification channels and notifications for SineMiner.
 *
 * Four channels:
 * - mining (foreground service, ongoing)
 * - shares (share accepted/rejected, brief)
 * - blocks (block found, high priority)
 * - errors (mining failures)
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Visible to same-module callers (e.g. MiningForegroundService). */
    internal val manager: NotificationManager = context.getSystemService<NotificationManager>()!!

    fun ensureChannels() {
        if (manager.getNotificationChannel(CHANNEL_MINING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MINING,
                    context.getString(R.string.notif_channel_mining),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notif_channel_mining_desc)
                    setShowBadge(false)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_SHARES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SHARES,
                    context.getString(R.string.notif_channel_shares),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notif_channel_shares_desc)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_BLOCKS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BLOCKS,
                    context.getString(R.string.notif_channel_blocks),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notif_channel_blocks_desc)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ERRORS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ERRORS,
                    context.getString(R.string.notif_channel_errors),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notif_channel_errors_desc)
                }
            )
        }
    }

    fun buildMiningNotification(contentText: String, progress: Int = 0): NotificationCompat.Builder {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_MINING)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(100, progress, progress == 0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
    }

    fun notifyShareAccepted() {
        manager.notify(
            NID_SHARE,
            NotificationCompat.Builder(context, CHANNEL_SHARES)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Share accepted")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()
        )
    }

    fun notifyShareRejected(reason: String) {
        manager.notify(
            NID_SHARE,
            NotificationCompat.Builder(context, CHANNEL_SHARES)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Share rejected: $reason")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()
        )
    }

    fun notifyBlockFound(hash: String) {
        manager.notify(
            NID_BLOCK,
            NotificationCompat.Builder(context, CHANNEL_BLOCKS)
                .setContentTitle("Block found!")
                .setContentText(hash.take(16) + "…")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    fun notifyError(message: String) {
        manager.notify(
            NID_ERROR,
            NotificationCompat.Builder(context, CHANNEL_ERRORS)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val CHANNEL_MINING = "sineminer.mining"
        const val CHANNEL_SHARES = "sineminer.shares"
        const val CHANNEL_BLOCKS = "sineminer.blocks"
        const val CHANNEL_ERRORS = "sineminer.errors"

        const val NID_FOREGROUND = 0x1100_0001
        const val NID_SHARE = 0x1100_0002
        const val NID_BLOCK = 0x1100_0003
        const val NID_ERROR = 0x1100_0004
    }
}
