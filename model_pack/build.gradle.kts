/**
 * The install-time asset pack that carries the three big models in the Play bundle.
 *
 * Play measures the compressed download, and its limit for the base module is 500 MB while a single
 * asset pack may be 1.5 GB. The models are about 512 MiB of the app, so with them in the base module
 * the bundle has no chance; in an asset pack the base module is tens of MB and the pack is well
 * inside its own limit.
 *
 * It has to be an **install-time** pack. Those are delivered as split APKs with the install, read
 * through the ordinary AssetManager, and need no Play Asset Delivery library — so nothing pulls in
 * Play Core, and nothing merges INTERNET into the manifest. fast-follow and on-demand packs do need
 * that library, and would cost the app its "no network access at all" guarantee.
 *
 * The asset pack plugin packages whatever is in src/main/assets, so syncModelAssets below fills it
 * from the files the fetch tasks download and checksum. Nothing here is in git (the models are
 * gitignored by extension).
 */
plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("model_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}

/**
 * Copies the downloaded models into the pack. Sync, not Copy, so a model that is renamed or dropped
 * can't linger in src/main/assets from an earlier build and quietly ship.
 */
val syncModelAssets = tasks.register<Sync>("syncModelAssets") {
    description = "Fills model_pack/src/main/assets with the downloaded models."
    dependsOn(":core:embed:fetchGranite", ":core:vision:fetchSiglipLabels", ":core:vision:fetchFaceModel")
    // The fetch tasks' output directories, each holding one assets subdirectory (embedding/,
    // siglip/, faces/). Referred to by path rather than through the other projects' build layouts,
    // so configuring this module never reaches into theirs.
    from(rootProject.layout.projectDirectory.dir("core/embed/build/generated/granite"))
    from(rootProject.layout.projectDirectory.dir("core/vision/build/generated/siglip"))
    from(rootProject.layout.projectDirectory.dir("core/vision/build/generated/faces"))
    into(layout.projectDirectory.dir("src/main/assets"))
    preserve { include(".gitkeep") }
}

// This module holds nothing but the asset pack, so anything that assembles or packages it needs the
// models in place first.
tasks.configureEach {
    if (name != syncModelAssets.name &&
        (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package"))
    ) {
        dependsOn(syncModelAssets)
    }
}
