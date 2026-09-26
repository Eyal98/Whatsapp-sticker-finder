# Peel-It: Development Plan (formerly WhatsApp Sticker Finder)

Find your WhatsApp stickers by describing them in plain language, in Hebrew or English
("חתול עצוב", "sad cat", "something to say I'm running late", "מזל טוב"). Everything
runs on the phone. Nothing leaves the device.

---

## 1. Constraints that shape the design

Several facts about WhatsApp decide the architecture, so they come first.

| Constraint | Consequence |
|---|---|
| WhatsApp has **no plugin or extension API**. The only official integration point is the third‑party *sticker pack* API (you can add packs, but you can't read WhatsApp's data or add UI inside WhatsApp). | We build a **companion Android app** that runs next to WhatsApp. We don't touch WhatsApp itself. |
| Modded clients (GBWhatsApp and similar) or injecting code into WhatsApp **break the ToS, can get the account banned, and are a known malware channel.** | Out of scope. This is the main security decision in the project. |
| The **Favorites list** (which stickers you starred) is stored in WhatsApp's private database under `/data/data/com.whatsapp/`, and only root can read it. | We **can't read the favorites flag directly** on a normal phone. See §2 for how we handle this. |
| The sticker **image files** are stored in shared media storage: `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers/*.webp` (Android 11+). | We can read these files **read‑only** after the user grants access to that one folder through the system folder picker (Storage Access Framework). No broad storage permission is needed. |
| **iOS** keeps WhatsApp's files inside its sandbox, and no other app can reach them. | **Android is the primary target.** iOS gets a reduced version at most (see §9). |
| Google ML Kit's on‑device OCR **doesn't support Hebrew**. | Use Tesseract (`heb`+`eng`) or a vision‑language model to read text on stickers. |

### How a sticker gets from the app into the chat

We have three options. Phase 0 tests them on a real device:

1. **Custom keyboard (IME) with a sticker search panel.** This is the best experience: you type a
   query without leaving WhatsApp, tap a result, and it's sent. It uses Android's
   `commitContent` rich‑content API, which Gboard uses for its own stickers. Phase 0 must
   check that WhatsApp accepts our `.webp` as a sticker and doesn't convert it to a photo.
2. **Share sheet or floating bubble.** You search in our app or overlay, then share the result to WhatsApp.
   This is simple and works everywhere, but WhatsApp may send the sticker as an image.
3. **Generated "Search results" sticker pack.** This uses the official pack API. It always sends
   a real sticker, but it's clunky: packs need 3 to 30 stickers, and WhatsApp asks for confirmation
   whenever a pack is added.

Plan: start the MVP with option 2, move to option 1 for the main experience, and keep option 3 as a fallback.

---

## 2. The "favorites" problem

We can't read the favorites flag without root, so we combine these approaches:

- **A. Index every sticker in the `WhatsApp Stickers` folder.** Favorites are part of this
  folder, so they all show up in search. The folder also holds recently received stickers, which
  is usually useful.
- **B. In‑app "⭐ mine" marking.** On first launch, a quick grid lets the user tick
  the stickers they consider favorites. Search ranks these higher and can filter to only them.
- **C. Usage signal.** Stickers you pick from search results rise in the ranking over time.
- **D. (Optional, off by default) Root mode.** On rooted devices, read the favorites table
  read‑only. This isn't part of the core build.

A + B + C cover the goal for users without root.

---

## 3. Architecture

```
┌──────────────────────── Companion app (no INTERNET permission) ────────────────────────┐
│                                                                                        │
│  Folder access (SAF, read-only)                                                         │
│        │                                                                               │
│        ▼                                                                               │
│  Indexer (WorkManager, runs while charging / idle, incremental)                        │
│   ├─ decode .webp (static + first/middle frame of animated)                            │
│   ├─ perceptual hash → dedupe                                                          │
│   ├─ OCR (Tesseract heb+eng) → text printed on the sticker                             │
│   ├─ VLM captioner (on-device, e.g. Gemma 3n via LiteRT-LM / MediaPipe)                │
│   │     → short description + emotions + tags, in BOTH Hebrew and English              │
│   └─ Text embedder (multilingual, e.g. EmbeddingGemma) → vector per sticker            │
│        │                                                                               │
│        ▼                                                                               │
│  Local index (SQLite: metadata + FTS table + vectors; encrypted at rest)               │
│        ▲                                                                               │
│        │                                                                               │
│  Query engine                                                                          │
│   ├─ normalize (Hebrew: strip niqqud, final letters, prefixes ו/ה/ב/ל/מ/ש/כ)            │
│   ├─ keyword search (FTS)  ─┐                                                           │
│   ├─ semantic search (cosine over vectors) ─┼─► hybrid rank (RRF) + ⭐ boost + usage     │
│   └─ optional LLM query rewrite for vague intents ("I'm late" → running, clock, sorry) │
│        │                                                                               │
│        ▼                                                                               │
│  UIs: Search screen · Keyboard (IME) panel · Share / bubble                            │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Why caption the stickers first instead of using only image–text embeddings

Multilingual CLIP‑style models (M‑CLIP, SigLIP 2 multilingual) can match a query to an image
directly, but their Hebrew quality is uneven. Sticker content is mostly *emotion + text +
characters*, and a generated caption handles that better. Captioning once while indexing and then
searching over text gives us:

- good Hebrew and English matching, including mixed‑language queries
- results you can explain (we can show *why* a sticker matched)
- tags the user can edit
- fast queries (only a short text gets embedded at query time, which takes milliseconds)

An image‑embedding model (SigLIP 2) is an optional extra ranking signal in Phase 3, used only if
the evaluation shows it helps.

### Device tiers

The heavy model runs **only while indexing**, when the phone is charging and idle.

| Tier | RAM | Captioning | Query |
|---|---|---|---|
| High | ≥ 8 GB | Gemma 3n E4B | EmbeddingGemma + optional LLM rewrite |
| Mid | 6 GB | Gemma 3n E2B | EmbeddingGemma |
| Low | < 6 GB | OCR + manual tags only | small multilingual embedder (e.g. multilingual‑e5‑small) + FTS |

Phase 0 checks the exact model choices against current releases and the user's phone.

---

## 4. Hebrew + English

- **UI**: full RTL support (Compose handles layout mirroring), strings in `values-iw`
  and `values`, and language taken from the system setting.
- **Captions** are generated in both languages and both are stored, so a Hebrew query can match
  an English caption and the other way around.
- **Hebrew normalization for keyword search**:
  - strip niqqud and cantillation marks (U+0591–U+05C7)
  - map final letters to regular forms (ך→כ, ם→מ, ן→נ, ף→פ, ץ→צ)
  - index each token both as written and with common one‑letter prefixes removed
    (ו, ה, ב, ל, מ, ש, כ, and combinations like וה, שה). This is a simple heuristic; full
    morphology isn't needed because the embeddings cover the rest.
  - treat geresh and gershayim (׳ ״ ' ") consistently, e.g. `מזל"ט`
- **Slang and transliteration**: a small editable synonym file
  (`סבבה ↔ ok/cool`, `יאללה ↔ let's go`, `חחח ↔ lol/laugh`), applied when the query is expanded.
- **Emoji in queries** ("😂") map to emotion tags.

---

## 5. Security and privacy design

| Area | Measure |
|---|---|
| **Network** | The app manifest declares **no `INTERNET` permission**. Android then blocks all network access, and anyone can check this by inspecting the APK. |
| **Getting models onto the phone** | Without network access, models come from **Play Asset Delivery** (Play downloads them, not the app) or from a file the user picks. Each model's SHA‑256 is pinned in the app and checked before loading. |
| **WhatsApp data** | Read‑only access to **one folder** through SAF. No `MANAGE_EXTERNAL_STORAGE`, no accessibility service, no notification listener, no root by default. |
| **Index at rest** | Stored in app‑private storage. The DB is encrypted with SQLCipher, with the key wrapped by Android Keystore. `allowBackup=false` and data‑extraction rules keep the index out of cloud and device‑transfer backups. |
| **Keyboard (IME)** | The only input it handles is the sticker search field. It doesn't record keystrokes, has no network access (same app), and has no clipboard history. It declares `supportsInlineSuggestions=false`. Queries aren't stored unless the user turns on history. |
| **Logging** | Release builds log nothing. Crash reports stay on the device and the user can export them manually. |
| **Supply chain** | Gradle dependency verification (`verification-metadata.xml`), pinned versions, a short list of dependencies, no analytics or ads SDKs, Dependabot, and a CodeQL + `lint` security scan in CI. |
| **Build/release** | Reproducible release builds, signing key kept offline, APK published with its checksum. |
| **Account safety** | WhatsApp itself is never modified, so the account isn't at risk of a ban. |

Threat model (short): the main risks are (1) leaking private sticker and chat content,
(2) a malicious dependency or model, and (3) the IME being abused as a keylogger. These are
covered by the network ban, checksums and dependency verification, and the minimal IME design.

---

## 6. Tech stack

- **Language/UI**: Kotlin, Jetpack Compose, Material 3 (RTL‑aware)
- **Background work**: WorkManager (charging + idle constraints), plus a `ContentObserver`/periodic
  rescan to pick up new stickers
- **Storage**: Room over SQLCipher; FTS4 virtual table; vectors stored as BLOBs
  (brute‑force cosine search is fine up to about 10k stickers, taking under 20 ms)
- **ML runtime**: LiteRT / MediaPipe LLM Inference (or LiteRT‑LM) for the VLM and embedder
- **OCR**: Tesseract4Android with `heb` + `eng` traineddata
- **Images**: Android `ImageDecoder` (animated WebP) and a dHash perceptual hash
- **Min SDK**: 30 (Android 11), matching the WhatsApp storage layout
- **CI**: GitHub Actions for build, unit tests, lint, detekt, CodeQL, and the evaluation run

### Proposed repo layout

```
app/                 Android app shell, navigation, DI
core/index/          Scanner, dedupe, indexing pipeline, WorkManager jobs
core/ml/             Model loading, checksum verification, captioner, embedder, OCR
core/search/         Normalization (he/en), FTS, vector search, hybrid ranking
core/data/           Room/SQLCipher schema, repositories
feature/search/      Search screen
feature/keyboard/    IME service + panel
feature/onboarding/  Folder grant, favorites picker, model setup
eval/                Golden query set + offline evaluation harness (JVM)
docs/                This plan, threat model, ADRs
```

---

## 7. Development phases

### Phase 0: Feasibility spikes (≈1 week)

Each spike answers one yes/no question, and its answer goes into an ADR in `docs/adr/`.

1. **Folder access**: can SAF grant a persistent read‑only URI to
   `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers` on the target phone and
   Android version? How many files are there, and how many are animated?
2. **Sending**: does WhatsApp accept `commitContent` from our IME as a *sticker*? What about
   `ACTION_SEND` with `image/webp`? Record the results for each approach.
3. **Models**: benchmark captioning time per sticker, peak RAM, and caption quality in Hebrew
   and English on the user's phone for 2 or 3 candidate VLMs. Benchmark embedding latency.
4. **OCR**: Tesseract `heb+eng` accuracy on about 30 real stickers with text.

**Exit criteria**: one working way to send stickers, and one model setup that captions about 1,000
stickers in under ~2 hours while the phone is charging.

### Phase 1: MVP, keyword search (≈2 weeks)

- Onboarding: folder grant, then the initial scan
- Indexer: decode, dedupe, OCR, and manual tags
- FTS search with Hebrew normalization, and a results grid
- Send by share intent
- ⭐ "mine" marking
- `INTERNET` permission absent from day 1, with a CI check that fails if it's added

**Done when**: you can find a sticker by the text printed on it, or by a tag, in either language.

### Phase 2: Natural‑language search (≈2–3 weeks)

- VLM captioning job (bilingual descriptions + emotion tags), resumable and incremental
- Multilingual text embeddings, vector search, and hybrid ranking with Reciprocal Rank Fusion
- "Why this matched" chips, and editable tags
- Evaluation harness plus a golden set (see §8)

**Done when**: evaluation targets are met.

### Phase 3: In‑WhatsApp experience (≈2 weeks)

- Keyboard (IME) with a search panel and tap to send (or a floating bubble if the IME spike failed)
- Ranking boosted by usage
- Optional LLM query rewrite on high‑tier devices
- Optional SigLIP image‑embedding signal, kept only if it improves evaluation scores

### Phase 4: Hardening and release (≈1–2 weeks)

- SQLCipher encryption, backup exclusion, model checksum pinning, release logging off
- Run `/security-review` and a manual threat‑model review; confirm no network access with
  `aapt dump permissions` plus a runtime test
- Performance: cold start under 1 s, query p95 under 300 ms, and indexing never runs in the foreground
- Accessibility (TalkBack labels in both languages) and RTL screenshot tests
- Distribution: signed APK on GitHub Releases, with Play Store later if wanted

---

## 8. Quality and evaluation

- **Golden set**: about 100 queries (50 Hebrew, 50 English, including mixed‑language, slang, and
  emoji queries), each labeled with the sticker(s) it should return. It's built and run **on the
  phone** (Smart search → Search quality test), because that's where the stickers and models are.
  Stickers are identified by image hash, so labels survive renames and rescans. The set lives in
  app‑private storage; it can be exported as JSON (query text + hashes, no images) and imported
  again, but isn't committed to git, since the queries describe personal stickers.
- **Metrics**: Recall@5 (share of the right stickers in the top 5, out of as many as fit),
  MRR@10, and search latency (p50/p95), for keyword, meaning and combined ranking, reported per
  language (Hebrew, English, mixed). The run also sweeps the semantic similarity cut‑off and
  offers to apply the best one.
- **Targets for Phase 2**: Recall@5 ≥ 0.80 overall, with Hebrew no more than 0.05 below English.
- **Unit tests**: Hebrew normalizer (niqqud, final letters, prefixes), ranking fusion,
  dedupe, and the incremental scanner.
- **Instrumented tests**: SAF flow, IME `commitContent`, and RTL layouts.

---

## 9. iOS (later, limited)

iOS apps can't read WhatsApp's stickers. At most we could build:
a Share Extension that saves stickers the user shares into it manually, the same
local indexing and search, and sending back by share or copy. This needs a separate
feasibility spike, and it isn't planned before the Android version is complete.

---

## 10. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| WhatsApp changes its folder layout or stops accepting stickers from keyboards | Medium | Keep the send method behind an interface with three fallbacks, and detect the folder path at startup |
| On‑device VLM is too slow or weak in Hebrew on the user's phone | Medium | Device tiers, overnight indexing, OCR + manual tags as a floor, and the model can be swapped |
| Favorites can't be identified exactly | Certain (without root) | Index all stickers + ⭐ marking + usage boost (§2) |
| Large model download size | High | Play Asset Delivery on‑demand packs, and a low tier with no VLM |
| Play Store policy on IMEs | Low | Minimal permissions and a clear privacy policy; sideloading is an option |

---

## 11. Immediate next steps

1. Confirm the target phone model and Android version (this decides the model tier).
2. Create the Android project skeleton (Phase 1 layout) with CI and the no‑INTERNET check.
3. Run the Phase 0 spikes and record the results as ADRs.
