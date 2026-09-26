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
    namespace = "com.eyal98.stickerfinder.embed"
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
 * Packages the Granite embedding model as an asset under `embedding/`, downloaded at build time
 * and checked against the SHA-256 pinned in granite.properties. The app copies it into place on
 * first start (BundledEmbedding), so search by meaning works without importing a file.
 */
abstract class FetchGranite : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val dir = outputDir.get().asFile.resolve("embedding")
        if (sha256.get() == "UNPINNED") {
            logger.warn("Granite isn't pinned in granite.properties; building without the bundled embedding model")
            dir.deleteRecursively()
            return
        }
        dir.mkdirs()
        // Keep the name in step with ModelCatalog.GRANITE_EMBEDDING.fileName.
        val target = dir.resolve(url.get().substringAfterLast('/'))
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
        if (target.isFile && sha256(target) == sha256.get()) return
        val part = File(target.path + ".part")
        URI(url.get()).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        val actual = sha256(part)
        if (actual != sha256.get()) {
            part.delete()
            throw GradleException("Granite checksum mismatch (see core/embed/granite.properties): expected ${sha256.get()}, got $actual")
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

val graniteConfig = Properties().apply {
    file("granite.properties").inputStream().use { load(it) }
}

val fetchGranite = tasks.register<FetchGranite>("fetchGranite") {
    url.set(graniteConfig.getProperty("model.url").trim())
    sha256.set(graniteConfig.getProperty("model.sha256").trim())
    outputDir.set(layout.buildDirectory.dir("generated/granite"))
}

androidComponents {
    onVariants { variant: LibraryVariant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchGranite, FetchGranite::outputDir)
    }
}

dependencies {
    api(project(":core:data"))
    implementation(project(":core:ml"))
    implementation(libs.kotlinx.coroutines.core)
    // Single-file (.litertlm) embedding models, e.g. Granite multilingual.
    implementation(libs.litertlm.android)
}
