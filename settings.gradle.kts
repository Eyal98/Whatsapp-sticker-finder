pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android is only published on JitPack. Restrict JitPack to that one group so
        // no other dependency can ever be resolved from it.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}

rootProject.name = "WhatsappStickerFinder"

include(":app")
include(":core:search")
include(":core:data")
include(":core:index")
include(":core:ocr")
include(":core:caption")
include(":core:ml")
include(":core:embed")
