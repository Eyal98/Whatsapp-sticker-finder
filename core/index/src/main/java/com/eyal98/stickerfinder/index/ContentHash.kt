package com.eyal98.stickerfinder.index

import java.security.MessageDigest

/**
 * A file's exact identity: SHA-256 of its bytes, in hex. Two files with the same content hash are
 * the same sticker; two with the same perceptual hash may only look alike.
 */
object ContentHash {

    /** Stored for a file that couldn't be read, so the background pass doesn't retry it forever. */
    const val UNREADABLE = ""

    fun of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
