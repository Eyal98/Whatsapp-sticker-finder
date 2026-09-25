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
    namespace = "com.eyal98.stickerfinder.ocr"
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
 * Downloads the Tesseract language files listed in tessdata.properties and verifies each one's
 * SHA-256. The files are packaged as assets under `tessdata/`, so the app itself never needs
 * network access to get them.
 */
abstract class FetchTessdata : DefaultTask() {
    @get:Input abstract val baseUrl: Property<String>

    /** File name to expected SHA-256 (lowercase hex), or "UNPINNED". */
    @get:Input abstract val checksums: MapProperty<String, String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val dir = outputDir.get().asFile.resolve("tessdata").apply { mkdirs() }
        val problems = mutableListOf<String>()
        for ((name, expected) in checksums.get()) {
            val target = dir.resolve(name)
            if (target.isFile && sha256(target) == expected) continue
            val part = dir.resolve("$name.part")
            URI(baseUrl.get() + name).toURL().openStream().use { input ->
                part.outputStream().use { input.copyTo(it) }
            }
            val actual = sha256(part)
            if (actual == expected) {
                target.delete()
                check(part.renameTo(target)) { "Could not move $part to $target" }
            } else {
                part.delete()
                problems += "$name: expected $expected, downloaded file has $actual"
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Tesseract language file checksum mismatch (see core/ocr/tessdata.properties):\n" +
                    problems.joinToString("\n") { "  $it" },
            )
        }
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

val tessdataConfig = Properties().apply {
    file("tessdata.properties").inputStream().use { load(it) }
}

val fetchTessdata = tasks.register<FetchTessdata>("fetchTessdata") {
    baseUrl.set(tessdataConfig.getProperty("base.url"))
    checksums.set(
        tessdataConfig.stringPropertyNames()
            .filter { it.endsWith(".traineddata") }
            .associateWith { tessdataConfig.getProperty(it).trim() },
    )
    outputDir.set(layout.buildDirectory.dir("generated/tessdata"))
}

androidComponents {
    onVariants { variant: LibraryVariant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchTessdata, FetchTessdata::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.tesseract4android)

    testImplementation(libs.junit)
}
