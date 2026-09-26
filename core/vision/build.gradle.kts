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
 * Packages the picture-tag labels as assets under `siglip/`: labels.tsv from tools/siglip, and
 * the matching label vectors, downloaded from the release and checked against a pinned SHA-256
 * (siglip.properties). The app itself never needs network access.
 */
abstract class FetchSiglipLabels : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
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
        if (!(target.isFile && sha256(target) == sha256.get())) download(dir, target)
        checkMatchesTsv(target)
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

    private fun download(dir: File, target: File) {
        val part = dir.resolve("labels.bin.part")
        URI(url.get()).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        val actual = sha256(part)
        if (actual != sha256.get()) {
            part.delete()
            throw GradleException(
                "SigLIP label vectors checksum mismatch (see core/vision/siglip.properties): " +
                    "expected ${sha256.get()}, downloaded file has $actual",
            )
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

val siglipConfig = Properties().apply {
    file("siglip.properties").inputStream().use { load(it) }
}

val fetchSiglipLabels = tasks.register<FetchSiglipLabels>("fetchSiglipLabels") {
    url.set(siglipConfig.getProperty("labels.url").trim())
    sha256.set(siglipConfig.getProperty("labels.sha256").trim())
    labelsTsv.set(rootProject.layout.projectDirectory.file("tools/siglip/labels.tsv"))
    outputDir.set(layout.buildDirectory.dir("generated/siglip"))
}

androidComponents {
    onVariants { variant: LibraryVariant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchSiglipLabels, FetchSiglipLabels::outputDir)
    }
}

dependencies {
    implementation(project(":core:ml"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.litert)

    testImplementation(libs.junit)
}
