package com.eyal98.stickerfinder.caption

import android.app.ActivityManager
import android.content.Context

object DeviceCapability {

    fun totalRamBytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.totalMem
    }

    /** Unknown models are held to the recommended model's requirement. */
    fun canRun(context: Context, model: CaptionModel?): Boolean =
        totalRamBytes(context) >= (model ?: ModelCatalog.RECOMMENDED).minRamBytes
}
