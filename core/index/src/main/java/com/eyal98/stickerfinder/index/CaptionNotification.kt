package com.eyal98.stickerfinder.index

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Shows that stickers are being described. A plain notification rather than a foreground one:
 * captioning usually starts in the background (on the charger), where Android doesn't allow
 * starting foreground work. Shown only if the user allowed notifications.
 */
internal object CaptionNotification {

    private const val CHANNEL = "captioning"
    private const val ID = 1002

    /** @param done null while the model is loading. */
    @SuppressLint("MissingPermission") // Checked by canNotify.
    fun show(context: Context, done: Int?, left: Int) {
        if (!canNotify(context)) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.caption_channel))
                .build(),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = if (done == null) {
            context.getString(R.string.caption_notification_loading)
        } else {
            context.getString(R.string.caption_notification_text, done, left)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_indexing)
            .setContentTitle(context.getString(R.string.caption_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setProgress(0, 0, done == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        manager.notify(ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}
