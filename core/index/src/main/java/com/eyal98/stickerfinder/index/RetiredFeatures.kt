package com.eyal98.stickerfinder.index

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.eyal98.stickerfinder.ml.ModelStore

/**
 * Cleans up after features earlier versions had: Gemma sticker descriptions (dropped as slow
 * and inaccurate) and the imported picture model (now bundled). Their background jobs,
 * notification channel and model files (the Gemma file alone is 2.6-3.7 GB) are removed.
 * Cheap once there's nothing left.
 */
object RetiredFeatures {

    fun cleanUp(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("sticker-caption-now")
            cancelUniqueWork("sticker-caption-periodic")
        }
        NotificationManagerCompat.from(context).deleteNotificationChannel("captioning")
        ModelStore.removeRetired(context)
    }
}
