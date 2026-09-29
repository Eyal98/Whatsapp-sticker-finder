package com.eyal98.stickerfinder.keyboard

import android.util.Log

/**
 * Makes animated stickers small enough for WhatsApp to take from a keyboard. WhatsApp refuses an
 * animated sticker over 500 KB inserted that way ("Couldn't share", an empty bubble on the
 * sender's phone), but stickers other people send can be bigger and are saved as they are. The
 * copy is re-encoded with libwebp (app/src/main/cpp): same 512 x 512 canvas, timing, looping and
 * sticker pack details; lower quality first, then fewer frames if needed.
 */
object StickerShrinker {

    /** WhatsApp's limit for animated stickers, with room to spare. */
    const val MAX_ANIMATED_BYTES = 490_000

    private val loaded: Boolean = try {
        System.loadLibrary("stickershrink")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Sticker shrinking isn't available", e)
        false
    }

    /** Whether an animated sticker of [bytes] must be made smaller to be sent. */
    fun needed(bytes: Long) = bytes > MAX_ANIMATED_BYTES

    /** [webp] re-encoded to at most [MAX_ANIMATED_BYTES], or null if that can't be done. Slow: seconds. */
    fun shrink(webp: ByteArray): ByteArray? {
        if (!loaded) return null
        return try {
            nativeShrink(webp, MAX_ANIMATED_BYTES)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory shrinking a sticker")
            null
        }
    }

    @JvmStatic
    private external fun nativeShrink(webp: ByteArray, maxBytes: Int): ByteArray?

    private const val TAG = "StickerShrinker"
}
