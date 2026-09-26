package com.eyal98.stickerfinder.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eyal98.stickerfinder.Diagnostics
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.StickerFinderApp
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.MessageDigest

/** A model the app ships, for the notices. Names and licenses stay in English, as published. */
private data class ModelNotice(val name: String, val by: String, val use: Int, val license: String, val source: String)

private val MODELS = listOf(
    ModelNotice(
        "SigLIP 2 (base, patch 16, 224)", "Google", R.string.about_use_picture_tags, "Apache License 2.0",
        "huggingface.co/litert-community/SigLIP2-base-patch16-224",
    ),
    ModelNotice(
        "Granite Embedding 311M Multilingual R2", "IBM", R.string.about_use_meaning, "Apache License 2.0",
        "huggingface.co/litert-community/granite-embedding-311m-multilingual-r2",
    ),
    ModelNotice(
        "SFace face recognition (converted to LiteRT)", "OpenCV Zoo", R.string.about_use_people, "Apache License 2.0",
        "github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface",
    ),
    ModelNotice(
        "ML Kit face detection", "Google", R.string.about_use_faces, "ML Kit Terms of Service",
        "developers.google.com/ml-kit/terms",
    ),
    ModelNotice(
        "Tesseract language data (tessdata_fast: heb, eng)", "Tesseract OCR", R.string.about_use_ocr, "Apache License 2.0",
        "github.com/tesseract-ocr/tessdata_fast",
    ),
)

/**
 * Native code bundled inside Tesseract4Android, which its Maven license doesn't list. Their full
 * license texts are in the asset licenses/native-libraries.txt.
 */
private val BUNDLED_NATIVE = listOf(
    "Tesseract OCR engine — Apache License 2.0 — github.com/tesseract-ocr/tesseract",
    "Leptonica — BSD 2-Clause — leptonica.org",
    "Independent JPEG Group's JPEG software (libjpeg 9f) — IJG License — ijg.org",
    "libpng — PNG Reference Library License v2 — libpng.org",
    "This software is based in part on the work of the Independent JPEG Group.",
)

private data class Library(val coordinates: String, val license: String, val url: String)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<String?>(null) }
    // Title and text of the license being read.
    var licenseText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLibraries by rememberSaveable { mutableStateOf(false) }
    val version = remember { versionText(context) }
    val fingerprint = remember { signingFingerprint(context) }
    val libraries = remember { readLibraries(context) }

    BackHandler(onBack = onBack)
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(stringResource(R.string.about_title), onBack)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(96.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(version, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Report a problem: the same redacted report as on the Smart search screen.
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.report_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        scope.launch { diagnostics = Diagnostics.build(context.applicationContext as StickerFinderApp) }
                    }) { Text(stringResource(R.string.report_button)) }
                }
            }

            Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider()
            Text(stringResource(R.string.about_models_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_models_body), style = MaterialTheme.typography.bodyMedium)
            for (model in MODELS) {
                Column {
                    Text(model.name, style = MaterialTheme.typography.titleSmall)
                    Text("${model.by} · ${stringResource(model.use)}", style = MaterialTheme.typography.bodyMedium)
                    Text("${model.license} · ${model.source}", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.about_libraries_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_libraries_body, libraries.size), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                licenseText = readAsset(context, "licenses/Apache-2.0.txt")?.let { "Apache License 2.0" to it }
            }) {
                Text(stringResource(R.string.about_apache_text))
            }
            val nativeTitle = stringResource(R.string.about_native_title)
            OutlinedButton(onClick = {
                licenseText = readAsset(context, "licenses/native-libraries.txt")?.let { nativeTitle to it }
            }) {
                Text(stringResource(R.string.about_native_text))
            }
            OutlinedButton(onClick = { showLibraries = !showLibraries }) {
                Text(stringResource(if (showLibraries) R.string.about_libraries_hide else R.string.about_libraries_show))
            }
            if (showLibraries) {
                for (line in BUNDLED_NATIVE) Text(line, style = MaterialTheme.typography.bodySmall)
                for (lib in libraries) {
                    Column {
                        Text(lib.coordinates, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text(
                            if (lib.url.isEmpty()) lib.license else "${lib.license} · ${lib.url}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.about_signing_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_signing_body), style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Text(fingerprint ?: "—", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }

    diagnostics?.let { DiagnosticsDialog(it, onDismiss = { diagnostics = null }) }
    licenseText?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { licenseText = null },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = { TextButton(onClick = { licenseText = null }) { Text(stringResource(R.string.diag_close)) } },
        )
    }
}

private fun versionText(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return "${info.versionName} (${info.longVersionCode})"
}

/**
 * SHA-256 of the certificate this copy is signed with, so testers can check an APK came from the
 * same place as the one listed on the release page.
 */
private fun signingFingerprint(context: Context): String? {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
    return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
        // Lowercase hex without separators, the way apksigner and the release notes print it.
        .joinToString("") { "%02x".format(it) }
}

private fun readAsset(context: Context, path: String): String? =
    try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        null
    }

private fun readLibraries(context: Context): List<Library> =
    readAsset(context, "licenses/dependencies.tsv").orEmpty().lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split('\t')
            Library(parts[0], parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        }
