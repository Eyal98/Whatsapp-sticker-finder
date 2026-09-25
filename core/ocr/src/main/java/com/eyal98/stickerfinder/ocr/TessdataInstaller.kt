package com.eyal98.stickerfinder.ocr

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Tesseract reads its language files from disk, so the copies bundled in the APK's assets are
 * copied into app-private storage once per app install or update. The assets are covered by the
 * APK signature, and the copies live where only this app can write.
 */
object TessdataInstaller {

    private const val TAG = "TessdataInstaller"
    private const val ASSET_DIR = "tessdata"

    /**
     * Returns the directory to pass to Tesseract (it must contain a `tessdata` folder), or null
     * if the files could not be installed.
     */
    fun install(context: Context): File? {
        val root = File(context.noBackupFilesDir, "ocr")
        val tessdata = File(root, ASSET_DIR)
        val marker = File(root, "installed-${installStamp(context)}")
        if (marker.isFile) return root

        return try {
            root.deleteRecursively()
            tessdata.mkdirs()
            val names = context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".traineddata") }
            if (names.isEmpty()) throw IOException("No language files in assets/$ASSET_DIR")
            for (name in names) {
                val part = File(tessdata, "$name.part")
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    part.outputStream().use { input.copyTo(it) }
                }
                if (!part.renameTo(File(tessdata, name))) throw IOException("Could not install $name")
            }
            marker.createNewFile()
            root
        } catch (e: IOException) {
            Log.w(TAG, "Could not install OCR language files", e)
            null
        }
    }

    /** Changes whenever the app is installed or updated, which is when the assets can change. */
    private fun installStamp(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
}
