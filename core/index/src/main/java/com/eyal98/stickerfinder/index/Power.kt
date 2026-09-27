package com.eyal98.stickerfinder.index

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Battery state, to keep heavy model work for when the phone is plugged in. */
object Power {
    fun isCharging(context: Context): Boolean =
        context.getSystemService(BatteryManager::class.java)?.isCharging ?: false

    /** Plugged in or not, and whether the battery is low (as WorkManager's "battery not low" sees it). */
    data class State(val charging: Boolean, val batteryLow: Boolean)

    /** Battery level at or below which Android reports the battery as low. */
    private const val LOW_PERCENT = 15

    /** The battery state now and whenever it changes. */
    fun observe(context: Context): Flow<State> = callbackFlow {
        fun stateOf(intent: Intent): State {
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val percent = if (level < 0) 100 else level * 100 / scale
            return State(charging = plugged, batteryLow = !plugged && percent <= LOW_PERCENT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(stateOf(intent))
            }
        }
        val current = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        current?.let { trySend(stateOf(it)) }
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
