package com.eyal98.stickerfinder.index.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {

    private val json = """{"format":"peel-it-backup","tags":["חתול","Kermit"]}"""

    @Test
    fun `plain backup round trip`() {
        val bytes = BackupFile.write(json, password = null)
        assertFalse(BackupFile.isEncrypted(bytes))
        assertEquals(json, BackupFile.read(bytes, password = null))
    }

    @Test
    fun `encrypted backup round trip, and the text isn't readable in the file`() {
        val bytes = BackupFile.write(json, "correct horse".toCharArray())
        assertTrue(BackupFile.isEncrypted(bytes))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Kermit"))
        assertEquals(json, BackupFile.read(bytes, "correct horse".toCharArray()))
    }

    @Test(expected = BackupFile.WrongPassword::class)
    fun `wrong password is refused`() {
        BackupFile.read(BackupFile.write(json, "a".toCharArray()), "b".toCharArray())
    }

    @Test(expected = BackupFile.NeedsPassword::class)
    fun `an encrypted backup asks for its password`() {
        BackupFile.read(BackupFile.write(json, "a".toCharArray()), null)
    }

    @Test(expected = BackupFile.WrongPassword::class)
    fun `tampering is detected`() {
        val bytes = BackupFile.write(json, "a".toCharArray())
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        BackupFile.read(bytes, "a".toCharArray())
    }

    @Test(expected = BackupFile.NotABackup::class)
    fun `other files are refused`() {
        BackupFile.read("hello, not a backup".toByteArray(), null)
    }

    @Test(expected = BackupFile.TooLarge::class)
    fun `a small file that unpacks to too much is refused`() {
        // Zeros compress about a thousand to one.
        val huge = ByteArray(1_000_001)
        val packed = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.GZIPOutputStream(out).use { it.write(huge) }
        }.toByteArray()
        BackupFile.read("PEELIT".toByteArray() + byteArrayOf(1, 0) + packed, null, maxJsonBytes = 1_000_000)
    }

    @Test(expected = BackupFile.TooLarge::class)
    fun `a file larger than any backup is refused while reading`() {
        BackupFile.readFile(java.io.ByteArrayInputStream(ByteArray(1_001)), maxBytes = 1_000)
    }
}
