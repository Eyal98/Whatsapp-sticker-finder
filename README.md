# WhatsApp Sticker Finder

A privacy-first Android companion app that lets you search your WhatsApp stickers
using natural language, in Hebrew or English. All processing runs on the device, and
the app has no network permission.

See [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) for the architecture, security
design, and phased roadmap.

## Status

Phase 1 scaffold: pick the WhatsApp Stickers folder, index it in the background, search by
tags you add (Hebrew or English, with prefix and slang handling), star favorites, and share a
sticker to WhatsApp. OCR (Phase 1b) and natural-language captions (Phase 2) plug into
`StickerIndexer`.

## Modules

| Module | What it does |
|---|---|
| `app` | Compose UI: onboarding (folder grant), search grid, tags, sending to WhatsApp |
| `core/search` | Pure Kotlin: Hebrew/English normalization, prefix variants, synonyms, FTS query building, rank fusion |
| `core/data` | Room database: stickers table + FTS4 index, repository |
| `core/index` | Folder access (SAF), scanner, indexer, WorkManager job |

## Build

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:search:test testDebugUnitTest   # unit tests
./gradlew lintDebug
./gradlew assembleDebug
scripts/check-apk-permissions.sh app/build/outputs/apk/debug/app-debug.apk
```

The last command fails if the APK asks for `INTERNET` or any permission not on its allowlist.
CI runs all of these on every push.
