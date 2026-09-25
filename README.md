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

Phase 2 (in progress): **Smart search**. An on-device Gemma 3n model (MediaPipe LLM Inference)
describes each sticker in Hebrew and English with search keywords, while the phone is charging
and idle. The app can't download, so you import the model file yourself; its SHA-256 is shown
for you to compare with the download page before it's used.

**Search by meaning**: an on-device multilingual embedding model (EmbeddingGemma 300M, via the
Google AI Edge RAG SDK) turns each sticker's description, printed text and tags into a vector.
Queries are embedded the same way, and results merge keyword and meaning matches with
Reciprocal Rank Fusion, so "running late" can find a sticker described as "מאחר".

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
| `core/caption` | Caption prompt/parser and MediaPipe captioner |
| `core/embed` | EmbeddingGemma text embedder (AI Edge RAG SDK), shared per process |

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
- [MediaPipe](https://github.com/google-ai-edge/mediapipe) LLM Inference: Apache-2.0
- Gemma models (downloaded by the user, not bundled): [Gemma Terms of Use](https://ai.google.dev/gemma/terms)
- [Google AI Edge RAG SDK](https://ai.google.dev/edge/mediapipe/solutions/genai/rag/android): Apache-2.0
- [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android): Apache-2.0. It is only
  published on JitPack, so the build allows JitPack for that one package group only.
