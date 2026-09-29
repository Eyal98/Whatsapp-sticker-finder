package com.eyal98.stickerfinder.keyboard

import android.util.Log

/**
 * Makes animated stickers fit WhatsApp's rules, which it checks when one is inserted from a
 * keyboard: 512 x 512, at most 500 KB, frames of at least 8 ms and 10 s in all. Stickers other
 * people send don't have to follow them and are saved in the library as they are; sent from the
 * keyboard as they are, WhatsApp refused them ("Couldn't share", "Can't send this file"). The copy
 * is re-encoded with libwebp (app/src/main/cpp): scaled onto a 512 x 512 canvas if needed, timing
 * kept but sped up to 10 s and no frame under 8 ms, looping and sticker pack details kept; lower
 * quality first, then fewer frames if needed to fit the size.
 */
object StickerShrinker {

    /** WhatsApp's 500 KB limit for animated stickers, with room to spare. */
    const val MAX_ANIMATED_BYTES = 490_000

    /** What a WebP file holds, without decoding it; numbers only, for the problem report too. */
    data class Info(
        val width: Int,
        val height: Int,
        val frames: Int,
        val totalMs: Int,
        val shortestFrameMs: Int,
        val longestFrameMs: Int,
        val loopCount: Int,
        val exif: Boolean,
        val xmp: Boolean,
        val iccProfile: Boolean,
        val animated: Boolean,
        /** Breaks one of WhatsApp's rules for animated stickers. */
        val needsFitting: Boolean,
    ) {
        fun describe() =
            "${width}x$height, $frames frames, $totalMs ms ($shortestFrameMs..$longestFrameMs), loop $loopCount" +
                (if (exif) ", EXIF" else "") + (if (xmp) ", XMP" else "") + (if (iccProfile) ", ICC" else "") +
                (if (needsFitting) ", breaks WhatsApp's rules" else "")
    }

    private val loaded: Boolean = try {
        System.loadLibrary("stickershrink")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Sticker shrinking isn't available", e)
        false
    }

    /** What [webp] holds, or null if it isn't a WebP (or the native library didn't load). */
    fun info(webp: ByteArray): Info? {
        if (!loaded) return null
        val v = nativeInfo(webp, MAX_ANIMATED_BYTES) ?: return null
        return Info(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7] != 0, v[8] != 0, v[9] != 0, v[10] != 0, v[11] != 0)
    }

    /** [webp] re-encoded to fit WhatsApp's rules, or null if that can't be done. Slow: seconds. */
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

    @JvmStatic
    private external fun nativeInfo(webp: ByteArray, maxBytes: Int): IntArray?

    private const val TAG = "StickerShrinker"
}
