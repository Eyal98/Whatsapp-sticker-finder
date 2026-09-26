import com.android.build.api.variant.LibraryVariant
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.eyal98.stickerfinder.vision"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/**
 * Packages picture tagging as assets under `siglip/`: the SigLIP 2 image model, labels.tsv from
 * tools/siglip, and the matching label vectors. The model and vectors are downloaded at build
 * time and checked against pinned SHA-256s (siglip.properties); the app itself never needs
 * network access.
 */
abstract class FetchSiglipLabels : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val modelUrl: Property<String>
    @get:Input abstract val modelSha256: Property<String>
    @get:InputFile abstract val labelsTsv: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val dir = outputDir.get().asFile.resolve("siglip")
        if (sha256.get() == "UNPINNED") {
            // Not built yet: the app hides picture tags when the labels aren't packaged.
            logger.warn("SigLIP label vectors aren't pinned in siglip.properties; building without picture tags")
            dir.deleteRecursively()
            return
        }
        dir.mkdirs()
        labelsTsv.get().asFile.copyTo(dir.resolve("labels.tsv"), overwrite = true)
        val target = dir.resolve("labels.bin")
        if (!(target.isFile && sha256(target) == sha256.get())) download(url.get(), sha256.get(), target)
        checkMatchesTsv(target)
        // Bundled so picture tags work right after install, with nothing to import.
        val model = dir.resolve(MODEL_ASSET)
        if (!(model.isFile && sha256(model) == modelSha256.get())) download(modelUrl.get(), modelSha256.get(), model)
    }

    /**
     * The vectors carry a hash of the prompt list they were built from; a labels.tsv edited
     * without re-pinning new vectors would turn picture tags off on the phone, so fail here.
     */
    private fun checkMatchesTsv(bin: File) {
        val prompts = labelsTsv.get().asFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t').first().trim() }
        val expected = MessageDigest.getInstance("SHA-256").digest(prompts.joinToString("\n").toByteArray())
        val header = bin.readBytes().copyOfRange(20, 52)
        if (!header.contentEquals(expected)) {
            throw GradleException(
                "tools/siglip/labels.tsv changed but core/vision/siglip.properties still pins vectors for the " +
                    "old list: run the SigLIP labels workflow and pin the file it publishes",
            )
        }
    }

    private fun download(url: String, expectedSha: String, target: File) {
        val part = File(target.path + ".part")
        URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        val actual = sha256(part)
        if (actual != expectedSha) {
            part.delete()
            throw GradleException(
                "Checksum mismatch for ${target.name} (see core/vision/siglip.properties): " +
                    "expected $expectedSha, downloaded file has $actual",
            )
        }
        target.delete()
        check(part.renameTo(target)) { "Could not move $part to $target" }
    }

    private companion object {
        /** Keep in step with SiglipModel.ASSET. */
        const val MODEL_ASSET = "siglip2_base_224_fp16.tflite"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Packages the SFace face model as an asset under `faces/`, downloaded from the "face-model"
 * release (built by .github/workflows/face-model.yml) and checked against a pinned SHA-256
 * (faces.properties). Until it's pinned, the app is built without the People feature.
 */
abstract class FetchFaceModel : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val dir = outputDir.get().asFile.resolve("faces")
        if (sha256.get() == "UNPINNED") {
            logger.warn("Face model isn't pinned in faces.properties; building without People")
            dir.deleteRecursively()
            return
        }
        dir.mkdirs()
        val target = dir.resolve("sface_fp16.tflite")
        if (target.isFile && sha256(target) == sha256.get()) return
        val part = File(target.path + ".part")
        URI(url.get()).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        val actual = sha256(part)
        if (actual != sha256.get()) {
            part.delete()
            throw GradleException("Face model checksum mismatch (see core/vision/faces.properties): expected ${sha256.get()}, got $actual")
        }
        target.delete()
        check(part.renameTo(target)) { "Could not move $part to $target" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val facesConfig = Properties().apply {
    file("faces.properties").inputStream().use { load(it) }
}

val fetchFaceModel = tasks.register<FetchFaceModel>("fetchFaceModel") {
    url.set(facesConfig.getProperty("model.url").trim())
    sha256.set(facesConfig.getProperty("model.sha256").trim())
    outputDir.set(layout.buildDirectory.dir("generated/faces"))
}

val siglipConfig = Properties().apply {
    file("siglip.properties").inputStream().use { load(it) }
}

val fetchSiglipLabels = tasks.register<FetchSiglipLabels>("fetchSiglipLabels") {
    url.set(siglipConfig.getProperty("labels.url").trim())
    sha256.set(siglipConfig.getProperty("labels.sha256").trim())
    modelUrl.set(siglipConfig.getProperty("model.url").trim())
    modelSha256.set(siglipConfig.getProperty("model.sha256").trim())
    labelsTsv.set(rootProject.layout.projectDirectory.file("tools/siglip/labels.tsv"))
    outputDir.set(layout.buildDirectory.dir("generated/siglip"))
}

androidComponents {
    onVariants { variant: LibraryVariant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchSiglipLabels, FetchSiglipLabels::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(fetchFaceModel, FetchFaceModel::outputDir)
    }
}

dependencies {
    implementation(project(":core:ml"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.litert)
    // Face detection with landmarks, for grouping people. The bundled model: runs on the phone,
    // no Google Play services download.
    implementation(libs.mlkit.face.detection)

    testImplementation(libs.junit)
}
