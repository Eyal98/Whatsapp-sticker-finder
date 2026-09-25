package com.eyal98.stickerfinder

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sends a sticker file to WhatsApp through the share sheet. This is the Phase 1 method; WhatsApp
 * may deliver it as an image rather than a sticker. The keyboard (Phase 3) is meant to replace it.
 */
object WhatsAppSender {

    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun send(context: Context, stickerUri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, stickerUri)
            clipData = ClipData.newRawUri(null, stickerUri)
            // Lets WhatsApp read this one file; our folder grant itself is never shared.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        for (pkg in WHATSAPP_PACKAGES) {
            try {
                context.startActivity(Intent(share).setPackage(pkg))
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next package.
            }
        }
        context.startActivity(Intent.createChooser(share, null))
    }
}
