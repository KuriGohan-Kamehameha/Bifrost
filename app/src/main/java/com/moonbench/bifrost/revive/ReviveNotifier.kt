package com.moonbench.bifrost.revive

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.MainActivity
import com.moonbench.bifrost.R

/** Tells the user when Bifrost could not, or would not, come back by itself. */
object ReviveNotifier {

    private const val CHANNEL_ID = "bifrost_revive"
    private const val NOTIFICATION_ID = 4246
    private const val REQUEST_OPEN = 4247
    private const val REQUEST_RESTART = 4248

    fun showGaveUp(context: Context, cause: String, snapshot: ReviveSnapshot?) {
        show(
            context,
            context.getString(R.string.revive_gave_up_title),
            context.getString(R.string.revive_gave_up_text, cause),
            snapshot
        )
    }

    fun showBlocked(context: Context, cause: String, snapshot: ReviveSnapshot?) {
        show(
            context,
            context.getString(R.string.revive_blocked_title),
            context.getString(R.string.revive_blocked_text, cause),
            snapshot
        )
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun show(context: Context, title: String, text: String, snapshot: ReviveSnapshot?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.revive_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)

        if (snapshot != null) {
            // Tapping a notification action is a user interaction, which Android
            // always allows to start a foreground service. The request carries
            // no revive marker, so it counts as a manual start and resets the
            // crash counter.
            val restart = PendingIntent.getForegroundService(
                context,
                REQUEST_RESTART,
                ServiceRevive.snapshotIntent(context, snapshot),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, context.getString(R.string.revive_action_restart), restart)
        }

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }
}
