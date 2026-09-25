package com.eyal98.stickerfinder.ml

import android.app.ActivityManager
import android.content.Context

object DeviceCapability {

    fun totalRamBytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.totalMem
    }

    /** An unknown model is held to [fallback]'s requirement. */
    fun canRun(context: Context, model: ModelSpec?, fallback: ModelSpec): Boolean =
        totalRamBytes(context) >= (model ?: fallback).minRamBytes
}
