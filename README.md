# WhatsApp Sticker Finder

A privacy-first Android companion app that lets you search your WhatsApp stickers
using natural language, in Hebrew or English. All processing runs on the device, and
the app has no network permission.

See [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) for the architecture, security
design, and phased roadmap.

## Status

Phase 1: pick the WhatsApp Stickers folder, index it in the background, search by the text
printed on stickers (on-device Tesseract OCR, Hebrew + English) and by tags you add, star
favorites, and share a sticker to WhatsApp. Search handles Hebrew prefixes, niqqud and common
slang.

Phase 2: **Smart search**. **Picture tags**: a SigLIP 2 image model, bundled in the app, tags
what each sticker shows (a cat, a laughing face, Kermit) in Hebrew and English against a fixed
label list, with no setup. Stickers' own pack names and emojis are searchable too. (Gemma sticker
descriptions were tried and dropped: slow, and too often generic or wrong.)

**Search by meaning**: an on-device multilingual embedding model (IBM Granite multilingual R2,
via LiteRT-LM) turns each sticker's printed text, tags and pack name into a vector. It's the one optional
file: the app can't download, so you import it yourself; its SHA-256 is pinned.
Queries are embedded the same way, and results merge keyword and meaning matches with
Reciprocal Rank Fusion, so "running late" can find a sticker described as "מאחר".

**Sticker keyboard** (Phase 3): stickers shared through the share sheet arrive in WhatsApp as
photos, so the app also includes a sticker-only keyboard. Type a search in the chat box with your
usual keyboard, switch to Sticker Finder, and tap a result: it's inserted with the keyboard
content API as `image/webp.wasticker` (falling back to WebP/PNG where that's what the field
accepts), and the search text is removed. It reads at most 100 characters before the cursor, only
when opened, never in password fields.

**Search quality test** (Smart search screen): write test searches in Hebrew and English, mark the
stickers each should find, and run them through the real pipeline on the phone. Reports Recall@5
and MRR@10 per language for keyword, meaning and combined ranking, search latency, and the best
similarity cut-off, which can be applied with one tap.

## Modules

| Module | What it does |
|---|---|
| `app` | Compose UI: onboarding (folder grant), search grid, tags, sending to WhatsApp |
| `core/search` | Pure Kotlin: Hebrew/English normalization, prefix variants, synonyms, stop words, FTS query building, vectors, rank fusion |
| `core/data` | Room database: stickers, FTS4 index, vectors; semantic and hybrid search |
| `core/index` | Folder access (SAF), scanner, indexer, WorkManager job |
| `core/ocr` | Tesseract OCR (`heb+eng`), text cleanup, bundled language files |
| `core/ml` | Model files: import into private storage, SHA-256 verification, catalog, RAM check |
| `core/vision` | Bundled SigLIP 2 image model and picture-tag labels |
| `core/embed` | Granite text embedder (LiteRT-LM), shared per process |

## Install on your phone (no computer needed)

Every push to this branch (or `main`) builds an APK signed with one stable key and publishes it
as the **Sideload build** pre-release under Releases. Open that page on the phone, download the
`.apk`, and open it (allow installing from your browser when asked). Later builds install over
the previous one and keep your data. Android 11 or newer.

This needs two repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | The PKCS12 keystore, base64-encoded (alias `stickerfinder`) |
| `SIGNING_KEYSTORE_PASSWORD` | Its password (also the key password) |

Without them CI still builds and tests everything, but doesn't publish an APK. Keep the key: an
APK signed with a different key can't be installed over the current one without uninstalling
(which deletes the app's data). To make one yourself:

```sh
keytool -genkeypair -storetype PKCS12 -keystore signing.p12 -alias stickerfinder \
  -keyalg EC -groupname secp256r1 -validity 10000 -dname "CN=Sticker Finder sideload"
base64 -w0 signing.p12   # paste the output into SIGNING_KEYSTORE_BASE64
```

## Build

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:search:test testDebugUnitTest   # unit tests
./gradlew lintDebug
./gradlew assembleDebug
scripts/check-apk-permissions.sh app/build/outputs/apk/debug/app-debug.apk
```

The build downloads the Tesseract language files once and fails unless each matches the
SHA-256 pinned in `core/ocr/tessdata.properties`. The app gets them from its own APK and never
downloads anything itself.

The last command fails if the APK asks for `INTERNET` or any permission not on its allowlist.
CI runs all of these on every push.

## Third-party components

- [Tesseract](https://github.com/tesseract-ocr/tesseract) and its
  [`tessdata_fast`](https://github.com/tesseract-ocr/tessdata_fast) language models: Apache-2.0
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) and [LiteRT](https://github.com/google-ai-edge/LiteRT): Apache-2.0
- [SigLIP 2](https://huggingface.co/google/siglip2-base-patch16-224) (bundled, LiteRT build from litert-community): Apache-2.0
- [Granite embedding multilingual R2](https://huggingface.co/ibm-granite) (imported by the user): Apache-2.0
- [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android): Apache-2.0. It is only
  published on JitPack, so the build allows JitPack for that one package group only.
