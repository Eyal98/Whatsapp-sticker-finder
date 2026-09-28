package com.eyal98.stickerfinder.index.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The backup file's container: a short header, then the backup's JSON compressed, and encrypted
 * when the user sets a password (AES-256-GCM, key from the password with PBKDF2-HMAC-SHA256). The
 * header is authenticated too, so a file can't be switched to "no password" without failing.
 *
 *     "PEELIT" | version (1) | mode (0 plain, 1 encrypted) | [salt (16) | iv (12)] | payload
 */
object BackupFile {

    private val MAGIC = "PEELIT".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val PLAIN: Byte = 0
    private const val ENCRYPTED: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    const val ITERATIONS = 200_000

    class NotABackup : IOException("Not a Peel-It backup")
    class NeedsPassword : IOException("This backup needs its password")
    class WrongPassword : IOException("Wrong password")

    /** Packs [json]; encrypted when [password] isn't empty. */
    fun write(json: String, password: CharArray?): ByteArray {
        val compressed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION.toInt())
        if (password == null || password.isEmpty()) {
            out.write(PLAIN.toInt())
            out.write(compressed)
            return out.toByteArray()
        }
        out.write(ENCRYPTED.toInt())
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        out.write(salt)
        out.write(iv)
        val header = out.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(header)
        }
        out.write(cipher.doFinal(compressed))
        return out.toByteArray()
    }

    /** Whether [bytes] is a backup that needs a password. @throws NotABackup */
    fun isEncrypted(bytes: ByteArray): Boolean {
        checkHeader(bytes)
        return bytes[MAGIC.size + 1] == ENCRYPTED
    }

    /** Unpacks the backup's JSON. @throws NotABackup, NeedsPassword, WrongPassword */
    fun read(bytes: ByteArray, password: CharArray?): String {
        val encrypted = isEncrypted(bytes)
        val start = MAGIC.size + 2
        val compressed = if (!encrypted) {
            bytes.copyOfRange(start, bytes.size)
        } else {
            if (password == null || password.isEmpty()) throw NeedsPassword()
            if (bytes.size < start + SALT_BYTES + IV_BYTES) throw NotABackup()
            val salt = bytes.copyOfRange(start, start + SALT_BYTES)
            val iv = bytes.copyOfRange(start + SALT_BYTES, start + SALT_BYTES + IV_BYTES)
            val body = start + SALT_BYTES + IV_BYTES
            try {
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
                    updateAAD(bytes, 0, body)
                    doFinal(bytes, body, bytes.size - body)
                }
            } catch (e: AEADBadTagException) {
                throw WrongPassword()
            } catch (e: GeneralSecurityException) {
                throw NotABackup()
            }
        }
        return try {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: IOException) {
            throw NotABackup()
        }
    }

    private fun checkHeader(bytes: ByteArray) {
        if (bytes.size < MAGIC.size + 2) throw NotABackup()
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) throw NotABackup()
        if (bytes[MAGIC.size] != VERSION) throw NotABackup()
        val mode = bytes[MAGIC.size + 1]
        if (mode != PLAIN && mode != ENCRYPTED) throw NotABackup()
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
