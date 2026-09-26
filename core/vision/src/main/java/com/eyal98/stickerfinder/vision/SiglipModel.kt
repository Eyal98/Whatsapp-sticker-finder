package com.eyal98.stickerfinder.vision

import android.content.Context
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * The SigLIP 2 image model, bundled in the app as an uncompressed asset (see
 * core/vision/build.gradle.kts), so picture tags work right after install.
 */
object SiglipModel {

    /** Keep in step with FetchSiglipLabels.MODEL_ASSET. */
    private const val ASSET = "siglip/siglip2_base_224_fp16.tflite"

    fun isBundled(context: Context): Boolean =
        context.assets.list("siglip")?.contains(ASSET.substringAfter('/')) == true

    /**
     * Maps the model straight from the APK: no copy on disk. Needs the asset stored uncompressed
     * (androidResources.noCompress in the app's build file).
     */
    @Throws(IOException::class)
    fun map(context: Context): MappedByteBuffer =
        context.assets.openFd(ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
}
