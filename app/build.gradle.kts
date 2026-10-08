import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The Play bundle ships the big models in an install-time asset pack; see the root build.gradle.kts.
val modelsInAssetPack = providers.gradleProperty("modelsInAssetPack").map(String::toBoolean).getOrElse(false)

android {
    namespace = "com.eyal98.stickerfinder"
    compileSdk = 35

    if (modelsInAssetPack) {
        assetPacks += ":model_pack"
    }

    defaultConfig {
        applicationId = "com.eyal98.stickerfinder"
        // The on-device smoke test (src/androidTest), run on an emulator by .github/workflows/smoke.yml.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Rules for the smoke test's own APK when it tests the minified build.
        testProguardFiles("test-proguard-rules.pro")
        minSdk = 30
        targetSdk = 35
        // CI passes its run number so each sideload build installs over the previous one.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        // CI passes the tag's version for alpha releases (v0.1.0-alpha.1 -> 0.1.0-alpha.1).
        versionName = (findProperty("versionName") as String?) ?: "0.1.0-dev"

        // 64-bit ARM only: every phone that can run the on-device models (Android 11+, 6 GB+
        // RAM) is arm64. The ML runtimes ship native code for 4 CPU types, and the other three
        // made the APK about 250 MB, big enough that sideload downloads failed to install.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // The sideload signing key: a PKCS12 file whose path CI passes as -PsigningStoreFile, with
    // its password in the SIGNING_KEYSTORE_PASSWORD environment variable (never on the command
    // line or in the repository). Without both, sideload builds come out unsigned.
    val signingStore = (findProperty("signingStoreFile") as String?)?.let(::file)
    val signingPassword: String? = System.getenv("SIGNING_KEYSTORE_PASSWORD")
    signingConfigs {
        if (signingStore != null && signingPassword != null) {
            create("sideload") {
                storeFile = signingStore
                storeType = "pkcs12"
                storePassword = signingPassword
                keyAlias = "stickerfinder"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // A release-like build for installing on your own phone from CI: not debuggable (so
        // app data can't be read over USB), signed with one stable key so updates keep data, and
        // minified like release (checked on an emulator by the smoke test, which runs the
        // "minified" build type below). CI keeps R8's mapping.txt with each build.
        create("sideload") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfigs.findByName("sideload")?.let { signingConfig = it }
        }
        // The sideload build's exact R8 setup, signed with the debug key: what the on-device smoke
        // test runs (.github/workflows/smoke.yml), since the real key only exists in CI secrets.
        create("minified") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            proguardFile("minified-test-support.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // The bundled SigLIP model is memory-mapped straight from the APK, which needs it stored
        // uncompressed (it barely compresses anyway).
        noCompress += "tflite"
        // The bundled Granite model is copied out once on first start; it barely compresses, and
        // stored as is its size is known up front for the free-space check.
        noCompress += "litertlm"
    }

    // libwebp and the sticker shrinker (app/src/main/cpp): makes animated stickers over
    // WhatsApp's 500 KB limit small enough to send from the keyboard.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        // Compress native libraries in the APK (they're extracted once at install). Stored
        // uncompressed, they made the sideload download about 2.5x bigger.
        jniLibs.useLegacyPackaging = true
    }

    // The smoke test runs against the minified build by default, so it checks what ships; pass
    // -PtestBuildType=debug to run it against the debug build instead.
    testBuildType = (findProperty("testBuildType") as String?) ?: "minified"

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:index"))
    implementation(project(":core:ml"))
    implementation(project(":core:embed"))
    implementation(project(":core:vision"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.work.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(project(":core:ocr"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}

/**
 * The open-source notices the app shows (About screen): every library in the release build with
 * the license its Maven POM declares (or its parent POM's), one per line as
 * "group:name:version<TAB>license<TAB>url", in the asset licenses/dependencies.tsv.
 */
abstract class DependencyNotices : DefaultTask() {
    @get:Input abstract val lines: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val undeclared = lines.get().filter { it.split('\t')[1].isBlank() }.map { it.substringBefore('\t') }
        check(undeclared.isEmpty()) {
            "No license is declared in the POM for: ${undeclared.joinToString()}. Record a reviewed license before building."
        }
        val dir = outputDir.get().asFile.resolve("licenses")
        dir.deleteRecursively()
        dir.mkdirs()
        dir.resolve("dependencies.tsv").writeText(lines.get().joinToString("\n", postfix = "\n"))
    }
}

/** Fails the build when the app's assets don't carry the list [DependencyNotices] generated. */
abstract class VerifyNoticeAsset : DefaultTask() {
    @get:InputDirectory abstract val merged: DirectoryProperty
    @get:InputDirectory abstract val generated: DirectoryProperty

    @TaskAction
    fun verify() {
        val shipped = merged.get().asFile.resolve("licenses/dependencies.tsv")
        val expected = generated.get().asFile.resolve("licenses/dependencies.tsv")
        check(shipped.isFile) { "licenses/dependencies.tsv is missing from the app's assets" }
        check(shipped.readText() == expected.readText()) {
            "licenses/dependencies.tsv in the app's assets differs from the generated list"
        }
    }
}

fun pomFile(group: String, name: String, version: String): File? =
    dependencies.createArtifactResolutionQuery()
        .forModule(group, name, version)
        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
        .execute()
        .resolvedComponents
        .flatMap { it.getArtifacts(MavenPomArtifact::class.java) }
        .filterIsInstance<ResolvedArtifactResult>()
        .firstOrNull()
        ?.file

/** (name, url) of each license the POM declares, following parent POMs that declare none. */
fun pomLicenses(group: String, name: String, version: String, depth: Int = 0): List<Pair<String, String>> {
    val pom = pomFile(group, name, version) ?: return emptyList()
    val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom).documentElement
    fun org.w3c.dom.Element.child(tag: String): org.w3c.dom.Element? =
        (0 until childNodes.length).map { childNodes.item(it) }
            .firstOrNull { it is org.w3c.dom.Element && it.tagName == tag } as org.w3c.dom.Element?
    fun org.w3c.dom.Element.text(tag: String) = child(tag)?.textContent?.trim().orEmpty()
    val licenses = root.child("licenses")?.let { list ->
        (0 until list.childNodes.length).map { list.childNodes.item(it) }
            .filterIsInstance<org.w3c.dom.Element>()
            .map { it.text("name") to it.text("url") }
    }.orEmpty()
    if (licenses.isNotEmpty() || depth >= 4) return licenses
    val parent = root.child("parent") ?: return emptyList()
    return pomLicenses(parent.text("groupId"), parent.text("artifactId"), parent.text("version"), depth + 1)
}

// Licenses a POM doesn't declare, each reviewed by hand. Tesseract4Android's POM has no license;
// its upstream terms are Apache-2.0 (README.md, Third-party components).
val reviewedLicenses = mapOf(
    "cz.adaptech.tesseract4android:tesseract4android:4.9.0" to
        ("Apache-2.0" to "https://github.com/adaptech-cz/Tesseract4Android"),
)

val dependencyNotices = tasks.register<DependencyNotices>("dependencyNotices") {
    lines.set(
        provider {
            configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .sortedBy { "${it.group}:${it.module}" }
                .map { id ->
                    val coordinate = "${id.group}:${id.module}:${id.version}"
                    val licenses = pomLicenses(id.group, id.module, id.version)
                        .ifEmpty { listOfNotNull(reviewedLicenses[coordinate]) }
                    val license = licenses.joinToString(" / ") { it.first }
                    val url = licenses.firstOrNull()?.second.orEmpty()
                    listOf("${id.group}:${id.module}:${id.version}", license, url)
                        .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
                }
        },
    )
    outputDir.set(layout.buildDirectory.dir("generated/notices"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(dependencyNotices, DependencyNotices::outputDir)
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoticeAsset>("verify${cap}NoticeAsset") {
            merged.set(variant.artifacts.get(SingleArtifact.ASSETS))
            generated.set(dependencyNotices.flatMap { it.outputDir })
        }
        tasks.matching { it.name == "merge${cap}Assets" }.configureEach { finalizedBy(verify) }
    }
}
