package com.eyal98.stickerfinder.index

import android.content.Context
import android.os.BatteryManager

/** Battery state, to keep heavy model work for when the phone is plugged in. */
object Power {
    fun isCharging(context: Context): Boolean =
        context.getSystemService(BatteryManager::class.java)?.isCharging ?: false
}
