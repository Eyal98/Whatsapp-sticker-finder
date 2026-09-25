package com.eyal98.stickerfinder.index

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo

/** The ongoing notification a foreground indexing (or picture tagging) job must show. */
internal object IndexNotification {

    private const val CHANNEL = "indexing"
    const val INDEX_ID = 1001
    const val IMAGE_TAGS_ID = 1003

    fun foregroundInfo(
        context: Context,
        left: Int,
        id: Int = INDEX_ID,
        title: Int = R.string.index_notification_title,
    ): ForegroundInfo {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.index_channel))
                .build(),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_indexing)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(R.string.index_notification_text, left))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
