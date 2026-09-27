package com.eyal98.stickerfinder.keyboard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Sends a sticker from WhatsApp's own sticker tray, so it stays linked to its sticker pack.
 *
 * A sticker a keyboard inserts is re-saved by WhatsApp as a new file, which loses the link to its
 * pack. Sent from the tray, it keeps it. When the user turns this on (Android's accessibility
 * settings, off by default) and taps a sticker from a pack in the Peel-It keyboard, this opens
 * WhatsApp's sticker tray, goes to that pack, recognizes the sticker on screen and taps it
 * ([TrayDriver]). If anything doesn't go as expected, the keyboard sends a copy as before.
 *
 * Privacy: Android only lets it see WhatsApp (packageNames in res/xml/tray_service.xml). It
 * ignores every event and only acts while the keyboard is sending a sticker; the screenshot it
 * takes then stays in memory and is dropped right away. It stores nothing but a short trace of
 * each attempt: steps, WhatsApp's view ids and match scores, never text ([TrayTrace]).
 */
class WhatsAppTrayService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    // Nothing happens on events: the service only acts when the keyboard asks.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Sends the sticker showing [picture] from pack [packName]; true once it's been tapped. */
    suspend fun send(picture: Bitmap, packName: String): Boolean = TrayDriver(this, picture, packName).run()

    companion object {
        /** WhatsApp and WhatsApp Business. */
        val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        @Volatile
        private var instance: WhatsAppTrayService? = null

        /** The running service, when the user has turned it on. */
        val connected: WhatsAppTrayService? get() = instance

        /** Whether it's turned on in Android's accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val ours = ComponentName(context, WhatsAppTrayService::class.java)
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { ComponentName.unflattenFromString(it.id) == ours }
        }
    }
}
