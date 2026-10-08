# Peel-It on iOS: feasibility go/no-go

Research only. No iOS code was written, no Apple account created, nothing bought.
Written October 2026, against iOS 27 / Xcode 26 as the current release.

This answers the question left open in [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) §9.

---

## 1. Recommendation: **no-go**

Do not build an iOS version now.

The reasoning in one breath: on iOS the app cannot see the user's sticker library at all, so the
user must feed it stickers by hand, one chat export at a time, forever. And no way out of the app
gives the user what Android gives them today — there are two possible send routes, and each breaks
a different half of the promise: the sticker-pack route sends a **real sticker but costs ~9
interactions and two app switches**, while the keyboard route **stays inside the chat but arrives
as a photo**, loses animation and transparency, and requires Full Access. That is roughly **a
quarter of the Android product's value for roughly four to six months of work.**

Three things are *not* the reason for the no-go, and are worth recording because they are the
opposite of what §9 assumed:

- **Size is not the problem on iOS.** Apple's limit is 4 GB uncompressed per app bundle; the
  bundled models are about 542 MB. They fit with ~3.4 GB of headroom and need no asset-delivery
  mechanism at all. The size work that dominates the Play release does not exist here. (§6)
- **Sending a real sticker is possible.** WhatsApp's third-party sticker-pack API is supported on
  iOS, so Peel-It could send actual stickers, not photos. It is just slow and ugly to use. (§4b)
- **A sticker keyboard is possible after all** — just not a good one. It cannot *insert* an image,
  but it can copy one to the clipboard for the user to paste, which is how shipped iOS sticker
  keyboards work. It needs Full Access and the result pastes as a photo. (§4a)

The honest no-go reason is **the manual, per-chat ingest, plus the fact that no send route is both
in-chat and a real sticker.** It is not bytes, and it is not a flat "iOS can't".

### What would change the answer

Revisit if any of these happen; otherwise the answer stays no.

1. WhatsApp ships a documented way to export or read the sticker library / Favorites on iOS.
   **This is the one that matters** — it is the load-bearing blocker, and the other two are
   comparatively cosmetic.
2. Apple opens the system Stickers drawer to third-party apps with a *dynamic* provider API
   (today, sticker packs are a Messages-only extension point and are static assets — §4, §5).
3. WhatsApp accepts a pasted image as a sticker, or Apple adds attachment insertion to
   `UITextDocumentProxy`. Either would make the keyboard route send a real sticker in-chat and
   would change this answer on its own, *if* trigger 1 also lands.

Note that the third trigger in the original draft — LiteRT-LM reaching iOS support — **has now
partially fired** and is no longer a reason to wait: iOS is in the LiteRT-LM support table, with
the Swift API at early-preview status (§6). That lowered the model risk from high to medium; it did
not change the recommendation, because the model runtime was never the binding constraint.

### The narrow thing that *is* worth doing instead

Peel-It already parses WhatsApp **iOS** chat exports — see
`core/search/src/main/kotlin/com/eyal98/stickerfinder/search/ChatExport.kt` and the iOS-format test
at `core/search/src/test/kotlin/com/eyal98/stickerfinder/search/ChatExportTest.kt:53`. An iPhone
user can already export a chat with media on iPhone and hand the `.zip` to the **Android** app on
another device. That costs zero new work. If the goal is "my iPhone friends get something", this is
it — not an iOS app.

---

## 2. What breaks, precisely

Peel-It rests on two Android mechanisms. Neither exists on iOS.

| Android mechanism | iOS status | Source |
|---|---|---|
| Read `Android/media/com.whatsapp/.../WhatsApp Stickers/*.webp` through a read-only SAF grant | **Impossible.** "Sandboxing is designed to prevent apps from gathering or modifying information stored by other apps." Each app gets "a unique home directory for its files, which is randomly assigned when the app is installed." Access outside the sandbox is only through OS-provided services, of which none exposes another app's files. | [Apple — Security of runtime process in iOS](https://support.apple.com/guide/security/security-of-runtime-process-sec15bfe098e/web) |
| Keyboard sends a real sticker with `commitContent(image/webp.wasticker)` | **No equivalent.** A custom keyboard can only "insert unattributed `NSString` objects at the text insertion point" via `textDocumentProxy`. There is no image-insertion API, and the keyboard "cannot select text" or reach the editing menu. The only way a keyboard can deliver an image is to put it on the clipboard and have the user paste it manually — which needs Full Access and arrives as a photo (§4a). | [Apple — App Extension Programming Guide: Custom Keyboard](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html) |

---

## 3. Getting stickers **in**: every path, rated

| Path | Supported by WhatsApp? | Allowed by Apple? | What the user has to do | Verdict |
|---|---|---|---|---|
| **Chat export with media** → share the `.zip` to Peel-It's Share Extension | **Yes.** Open the chat → tap the contact/group name → **Export Chat** → **Attach Media**. Produces a `.zip` containing `_chat.txt` plus the media, with stickers as `.webp` (e.g. `00000012-STICKER-2024-12-31-21-05-33.webp`). | Yes — a standard Share Extension receiving `public.zip-archive`; read in memory, never written out. | Per chat: ~6 taps, repeated for every chat they care about, and repeated again as new stickers arrive. Exports are capped by WhatsApp (widely reported as ~10,000 messages when media is attached; WhatsApp's own Help Center documents the steps but not a number), so a long chat only yields its recent media. | **The only real path.** Partial, manual, per-chat. |
| Read the sticker tray / **Favorites** | No. There is no export. Favorites live in WhatsApp's sandbox. | N/A | — | **Impossible.** |
| Long-press a sticker → share it out, one at a time | No. The sticker long-press menu on iPhone offers **Add to Favorites** and adding it to a sticker pack — there is no Share or Save action. | N/A | — | **Not available.** |
| **iCloud backup** of WhatsApp | Backup exists, but it is WhatsApp's own encrypted backup. There is no read API, and CloudKit gives an app access only to its own container. | No third-party read path exists. | — | **Impossible.** |
| **Files app** / document browser | WhatsApp on iPhone does not expose its media directory as a Files provider, and WhatsApp documents no such path. Peel-It would see nothing to pick. | A document picker is allowed; there would just be nothing there. | — | **Nothing to read.** |
| Third-party sticker packs the user installed | Those images live in the *pack app's* bundle, which is another sandbox. | N/A | — | **Impossible.** |
| **Photos** (`PHPickerViewController`) | Only works for stickers already in Photos. WhatsApp offers no "save sticker to Photos" on iPhone. | Yes, and it is Apple's preferred out-of-process picker (guideline 5.1.1(iii)). | Would require the user to get stickers into Photos first, which WhatsApp does not offer. | **Dead end in practice.** |

**Sources:** [WhatsApp — How to export your chat history](https://faq.whatsapp.com/1180414079177245?locale=en_US) ·
[WhatsApp — How to use stickers (iPhone)](https://faq.whatsapp.com/iphone/chats/how-to-use-stickers) ·
[Apple — iOS app sandbox](https://support.apple.com/guide/security/security-of-runtime-process-sec15bfe098e/web)

### What the Share Extension can actually do

A Share Extension is a receiver, not a reader: it appears in *other apps'* share sheets and takes
what they hand it ([Apple — App Extensions Increase Your
Impact](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/index.html),
Table 1-1: Share = "Post to a sharing website or share content with others"). It cannot go looking
for files. So the Share Extension's whole job on iOS would be: accept the chat-export `.zip`, hand
it to the containing app through the shared app group, and let the app index it. That is the same
shape as the existing Android `ChatImporter` path (`core/index/.../ChatImporter.kt`), so the logic
ports; the *coverage* does not.

---

## 4. Getting a sticker back **out**: four paths, measured in taps

Two of these work. Neither works well, and they fail in opposite directions — read 4a and 4b
together, then the comparison table at the end of 4b.

### 4a. Custom keyboard — possible, but it sends a **photo**, not a sticker

This is the one place the original draft was too pessimistic, so it is worth being precise about
what is and is not true.

**True:** a keyboard cannot *insert* an image into the host app. Apple's guide lists what a keyboard
may put into the host app: `insertText:` with an "unattributed `NSString`", `deleteBackward`, and
cursor moves. There is no image or attachment API on `UITextDocumentProxy`, and nothing like
Android's `commitContent`.

**Also true:** a keyboard can still *deliver* an image, by writing it to `UIPasteboard` and letting
the user long-press → **Paste** in the WhatsApp chat box. This is not theoretical — it is how
shipped iOS sticker keyboards work. [Stickerboard](https://github.com/apsun/Stickerboard) states the
constraint and the workaround plainly: "Third party keyboards are only able to send text; the only
way to 'send' images is by copying them to the clipboard so that you can easily access them," and it
requires Full Access "to copy the stickers to your clipboard."

So the keyboard route exists. Its costs, all of which are real:

| Cost | Detail |
|---|---|
| **Arrives as a photo** | WhatsApp receives a pasted image, not a `.wasticker`. Transparency and animation are lost — the exact outcome ARCHITECTURE.md §7 already records as the worse Android share path. |
| **Requires Full Access** | Both for the clipboard write and for the shared app-group container. Without `RequestsOpenAccess` a keyboard extension has **no shared container with its containing app**, so it could not read Peel-It's index at all. |
| **Full Access is also what grants network** | Turning it on hands the keyboard the container *and* lifts the network sandbox, so the user is asked to trust precisely the component that most needs to look trustworthy (§5). |
| **4.4.1 tension** | The guideline requires a keyboard to "Provide keyboard input functionality (e.g. typed characters)" and to "Remain functional without full network access and without requiring full access". A sticker-only keyboard whose single feature stops working without Full Access is arguably non-compliant; it would need to also be a working text keyboard. |
| **Manual paste** | The user taps the chat field, long-presses, and taps Paste. iOS may also show a paste-permission prompt for a cross-app pasteboard read. |

**Verdict:** the keyboard is the only route that keeps search *inside* the chat — about 4
interactions, **0 app switches** — but it downgrades every sticker to a photo and costs the privacy
posture. The sticker-pack route (§4b) is the mirror image: a real sticker, 9 interactions, 2 app
switches. **Neither is Android's 2 taps for a real sticker in-chat, and no third option combines
them.** That pair, not a single hard "no", is what makes the product not worth building.

**Sources:** [Apple — Custom
Keyboard](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html) ·
[App Review Guidelines 4.4.1](https://developer.apple.com/app-store/review/guidelines/) (quoted
verbatim above; re-verified October 2026) ·
[Apple — Supporting extensions in iOS](https://support.apple.com/en-gu/guide/security/secabd3504cd/web)
("custom keyboards run by default in a very restrictive sandbox that blocks access to the network") ·
[Stickerboard](https://github.com/apsun/Stickerboard) (shipped precedent).

### 4b. WhatsApp's third-party sticker-pack API — **works, with real friction**

This is the only way to send a *real sticker* from an iOS app. WhatsApp's own repo documents it:
serialise the pack to the pasteboard, then open `whatsapp://stickerPack`.

Hard constraints from the API:

| Constraint | Value | Consequence for Peel-It |
|---|---|---|
| Stickers per pack | **minimum 3**, maximum 30 | A single best match cannot be sent. Every send ships a padded pack. |
| Static / animated | **A pack must be all-static or all-animated, never mixed** | Nastier than it looks: search results mix the two freely, so "send my top 3 matches" can be an invalid pack. Peel-It would have to segregate results by type and pad each kind separately — i.e. the pack it sends is not the result set the user picked. |
| Dimensions | **exactly 512 × 512** | Received stickers that are not 512² must be re-canvassed. |
| Static sticker size | **≤ 100 KB** | *Stricter than the Android keyboard path needs.* Many 512² lossless WebP stickers exceed this, so most static stickers would need lossy re-encoding — a visible quality cost the Android build does not pay. |
| Animated sticker size | ≤ 500 KB | Same limit the Android `StickerShrinker` already handles; libwebp builds for iOS, so that logic ports. |
| Tray image | 96 × 96, ≤ 50 KB | Trivial, generate it. |
| Handoff | one pack at a time, via pasteboard + URL scheme | Each send is an app switch plus WhatsApp's add-pack confirmation. |

**Source:** [WhatsApp/stickers — iOS README](https://github.com/WhatsApp/stickers/blob/main/iOS/README.md)

**Tap count, head to head — all three routes:**

| | Android today | iOS via sticker pack (§4b) | iOS via keyboard (§4a) |
|---|---|---|---|
| App switches per search | **0** | **2** (to Peel-It and back) | **0** |
| Interactions | switch keyboard (1) → type → tap sticker (1) = **2** | open Peel-It (1) → type → select ≥3 results (3) → "Send to WhatsApp" (1) → confirm "Add stickers?" (1) → open sticker tray (2) → find the pack (scroll) → tap the sticker (1) = **~9** | switch keyboard (1) → type → tap sticker (1) → long-press chat field (1) → Paste (1) = **~4** |
| Arrives as | **a real sticker** | **a real sticker** | a photo — no transparency, no animation |
| Where you type | inside the WhatsApp chat | in another app | inside the WhatsApp chat |
| Needs Full Access | n/a | no | **yes** |

Read the two iOS columns together: each one is acceptable on its own axis and unacceptable on the
other. Nine interactions and two app switches is worse than just scrolling WhatsApp's own tray for
most libraries; four interactions that turn every sticker into a photo defeat the point of a sticker
app. **The absence of a route that is both in-chat and a real sticker is the finding that kills the
product** — not any single API limit.

Two further costs: WhatsApp's tray accumulates the generated packs, and WhatsApp removes
third-party packs when the providing app is uninstalled.

### 4c. Share sheet / pasteboard — **works, arrives as a photo**

Peel-It → share sheet → WhatsApp → pick chat → send. About 5 taps and 2 app switches, and the
sticker arrives as an **image**, losing transparency and animation — the same known-worse outcome as
the Android share path (ARCHITECTURE.md §7). Writing to `UIPasteboard` and letting the user paste is
equivalent — and when done from the keyboard extension rather than the app, it is exactly §4a.
Keep as a fallback, never as the main route.

### 4d. The iOS Stickers drawer — closed to us

iOS has a system Stickers drawer, but third-party sticker packs are a **Messages** extension point:
Apple's extension-point table lists "iMessage — iOS — Interact with the Messages app", and Apple's
guide is titled ["Adding your sticker packs to
**Messages**"](https://developer.apple.com/documentation/messages/adding-your-sticker-packs-to-messages).
Packs are also static assets dragged into a Stickers asset catalog at build time, so even inside
Messages they could not be search results. There is no public API for an app to feed the system
drawer dynamically, and therefore no route into WhatsApp through it.

---

## 5. Apple's rules for this shape of app

| Area | Rule | Does Peel-It pass? |
|---|---|---|
| **Keyboard extensions** | 4.4.1: must "Provide keyboard input functionality (e.g. typed characters)", must "Provide a method for progressing to the next keyboard", must "Remain functional without full network access and without requiring full access", must "Follow Sticker guidelines if the keyboard includes images or emoji", and must not "Launch other apps besides Settings". | **Marginal — the one real review risk.** The clipboard keyboard (§4a) is a shipped pattern, but its only feature stops working without Full Access, which sits badly against "without requiring full access". Mitigation would be to ship a genuine text keyboard with sticker search bolted on — more work, and still a judgement call at review. Also note "must not launch other apps" independently forbids a keyboard that hands off to WhatsApp, so the keyboard and sticker-pack routes cannot be combined into one component. |
| **Sticker-only apps** | WhatsApp's own iOS README: "With Apple's strict App Store review policy, we recommend iOS developers to submit apps that contain more functionality than to simply export stickers", and "significantly modify the UI before submitting". | Passes comfortably — Peel-It is a search app, not a pack wrapper. This is the one review risk that is *not* a problem. |
| **Reading user media** | 5.1.1(iii) data minimisation: "Where possible, use the out-of-process picker or a share sheet rather than requesting full access to protected resources like Photos or Contacts." | Passes — the design is share-sheet-only and never asks for Photos. |
| **Face recognition** | 2.5.13 requires `LocalAuthentication` for face-based *account authentication* — not applicable; Peel-It groups faces to tell people apart, it does not authenticate. 5.1.2(vi): data from "depth and/or facial mapping tools (e.g. ARKit, Camera APIs, or Photo APIs) may not be used for marketing, advertising or use-based data mining". | Passes — face grouping is opt-in, on-device, deletable, and used for nothing else. Apple has no rule that forbids on-device face grouping in this shape. |
| **Privacy policy** | 5.1.1(i): a privacy policy link is required in App Store Connect metadata **and** inside the app. | Needs a hosted privacy policy URL — already required for Play, so no new work. |
| **Privacy labels** | App privacy details are "required to submit new apps and app updates". But: "Data that is processed only on device is not 'collected' and does not need to be disclosed." | Passes, and well: Peel-It would declare **Data Not Collected**. |

**Sources:** [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) ·
[Apple — App privacy details](https://developer.apple.com/app-store/app-privacy-details/) ·
[WhatsApp/stickers — iOS README](https://github.com/WhatsApp/stickers/blob/main/iOS/README.md)

### The trust claim cannot be made on iOS

This matters more than any single API. Peel-It's main promise is *provable* zero network: the
manifest has no `INTERNET` permission, Android enforces it, `scripts/check-apk-permissions.sh` fails
CI on the built artifact, and anyone can verify it from the published APK.

iOS has no equivalent. There is no network permission to omit and nothing in the `.ipa` to assert
against, so the strongest claim available is a self-declared **Data Not Collected** privacy label
plus whatever the user later observes in Settings → Privacy & Security → App Privacy Report. That is
a promise, not a proof, and it is not something CI can gate. The one place iOS *does* enforce it is
a keyboard extension without Open Access — which is precisely the component that cannot exist here.

An iOS Peel-It would therefore ship the same privacy design with a materially weaker claim. Flagging
this to Chief of staff as a product-positioning consequence, not just an engineering one.

---

## 6. Model size and the App Store limits

### The limits

| Limit | Value | Source |
|---|---|---|
| Max uncompressed app bundle (min deployment iOS 9.0+) | **4 GB** | [Apple — Maximum build file sizes](https://developer.apple.com/help/app-store-connect/reference/app-uploads/maximum-build-file-sizes) |
| Max executable `__TEXT` across the binary (iOS 9.0+) | **80 MB** | same |
| Cellular download cap | None since iOS 13. The user is warned over 200 MB and can allow it; Settings → App Store → App Downloads offers Always Allow / Ask If Over 200 MB / Ask First. | [9to5Mac — iOS 13 removes the 200 MB limit](https://9to5mac.com/2019/06/03/ios-13-removes-200-mb-file-size-limit-for-app-downloads-over-cellular/) |
| On-Demand Resources | **Deprecated as of iOS 27**; migrate to Background Assets. (Historic limits: 512 MB per pack pre-iOS 18, 8 GB on iOS 18+.) | [Apple — ODR size limits](https://developer.apple.com/help/app-store-connect/reference/on-demand-resources-size-limits/) |
| Background Assets, Apple-hosted | Up to 200 GB per app, included in the membership; essential / prefetch / on-demand policies. | [Apple — Overview of Apple-hosted asset packs](https://developer.apple.com/help/app-store-connect/manage-asset-packs/overview-of-apple-hosted-asset-packs) |

### Do the models fit? Yes, with room to spare

From [ARCHITECTURE.md](ARCHITECTURE.md) §8:

| Model | Size |
|---|---|
| Granite Embedding 311M multilingual R2 (int8) | 332 MB |
| SigLIP 2 base/16 224 px (fp16) | 185 MB |
| SFace (fp16) | 19 MB |
| Tesseract `tessdata_fast` heb + eng | ~3 MB |
| ML Kit face detection (bundled library) | ~3 MB |
| **Total** | **~542 MB** |

542 MB against a 4 GB bundle limit. **No shrinking is required, and no asset-delivery mechanism is
required** — neither the deprecated ODR nor Background Assets. The ~560 MB artifact that is a hard
problem for Play is simply an acceptable App Store app. The user-facing cost is a ~500 MB download
(fp16/int8 weights compress by only a few percent) with a cellular warning the user can dismiss.

The 80 MB `__TEXT` limit is not a concern either: it applies to executable code, and all 542 MB here
is bundled resource data. The only native code is libwebp, Tesseract and the LiteRT runtime — tens
of MB at most. Worth a one-line check at first build rather than an assumption, since exceeding it
is an upload-time rejection.

So the "would they need to shrink, and by how much" answer is: **no, 0 MB.** Note the asymmetry
worth telling the user: the size work that blocks Play distribution is work iOS would not need.

### The real model problem is the runtime, not the size

| Component | iOS runtime | Risk |
|---|---|---|
| SigLIP 2 (`.tflite`) | LiteRT has iOS support; or convert to Core ML | Low. Conversion + re-check that picture tags still match the pinned label vectors. |
| SFace (`.tflite`) | LiteRT iOS or Core ML | Low. |
| Face detection + landmarks | ML Kit has an iOS SDK; Apple's Vision does this natively and better | Low, and could drop the ML Kit dependency. |
| Tesseract heb + eng | libtesseract builds for iOS; the `tessdata_fast` files are platform-neutral | Low. Keep Tesseract — Apple's Vision OCR language list is version-dependent and its Hebrew support is **unverified**; it would have to be checked on-device before relying on it. |
| **Granite via LiteRT-LM (`.litertlm`)** | **iOS now appears in the support table with both CPU and GPU**, and the Swift API is marked **"Early Preview"** — actively developed, not production-ready like the Python and Kotlin paths. | **Medium — downgraded from high.** Meaning search is the product, so this matters, but it is no longer a wall. Two options: ship on the early-preview Swift API and accept its churn, or re-convert Granite to Core ML / ONNX including its tokenizer. Either way, per the **quantisation cost** rule the model is not accepted until Recall@5 is re-measured against the DEVELOPMENT_PLAN §8 targets (≥ 0.80 overall, Hebrew within 0.05 of English). An early-preview dependency on the single most important feature is still a real schedule risk. |

**Source:** [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview) (re-checked
October 2026 — iOS CPU/GPU listed, Swift API at early preview; this is a change from the status
assumed when this spike opened).

Also note RAM: meaning search needs about 3 GB (ARCHITECTURE.md §12). iPhone 15/16-class devices
have 6–8 GB; 4 GB iPhones would fall back to keyword-only, the same tiering as Android.

---

## 7. What the iOS app would honestly be

> Peel-It for iPhone would be a searchable album of the stickers you hand it. Because iPhone keeps
> WhatsApp's files private, it cannot see your sticker tray or your Favorites — so you would export
> a chat from WhatsApp with its media attached and share the file to Peel-It, once per chat, and
> again whenever you want newer stickers. Peel-It would then read the text on those stickers, tag
> what is in the picture, group the people in them, and let you search all of it in Hebrew or
> English, exactly as the Android app does. Sending one would mean picking your poison. Either you
> search in Peel-It and tap Send to WhatsApp, which asks you to add your picks as a sticker pack and
> then leaves you to find the one you wanted in WhatsApp's own tray — slow, but it arrives as a real
> sticker. Or you install Peel-It's keyboard, search right there in the chat, and paste the sticker
> in — quick, but it arrives as a flat photo with no transparency or animation, and the keyboard
> needs the "Full Access" permission to work at all. There is no option that is both fast and a real
> sticker, because iPhone does not let one app put an image into another app's chat box.

**Share of the Android product's value: roughly 25%.**

| Android value | On iOS |
|---|---|
| Your whole sticker library indexed automatically, no setup past one folder grant | **Gone.** Only what you manually export, per chat, repeatedly. This is the biggest single loss. |
| Search inside WhatsApp, in the chat — 2 taps, 0 app switches, real sticker | **Split and degraded.** Either in-chat but a photo (~4 taps, Full Access), or a real sticker but ~9 interactions and 2 app switches in another app. Never both. |
| Sends a real sticker | **Kept only on the slow route**, with lossy re-encoding for static stickers over 100 KB and no mixing of static and animated in one pack. |
| Hebrew/English hybrid search, tags, folders, people, learning from picks | **Kept** — but over a smaller, staler library, and contingent on the Granite runtime (early preview) or a re-conversion holding quality. |
| Learning from chat exports | **Kept**, and becomes the *primary* ingest instead of a bonus. |
| Provable zero-network | **Gone.** A declaration, not a proof (§5) — and the keyboard route would actively require Full Access, which lifts the network sandbox. |

---

## 8. Effort estimate and what the user must own

### Effort

Reference size of the current codebase: **13,422 lines of Kotlin across 131 files** in 8 Gradle
modules, plus a vendored libwebp (~64k lines of C that ports as-is).

Native Swift rebuild, one developer, focused:

| Work | Estimate |
|---|---|
| Port `:core:search` (1,252 lines + 755 lines of tests: Hebrew normalisation, prefixes, synonyms, FTS query building, vector math, RRF, face grouping, chat parsing, eval metrics) to Swift | 2–3 weeks |
| Storage layer: SQLite + FTS + vector BLOBs, replacing Room/KSP | 1–2 weeks |
| Share Extension + app group + zip read + chat parse + hashing (mirrors `ChatImporter`) | 1–2 weeks |
| SigLIP 2 + SFace conversion and validation against the pinned label vectors | 2–3 weeks |
| **Granite on iOS: early-preview Swift API, or Core ML/ONNX re-conversion incl. tokenizer, + on-device search quality re-test** | **2–4 weeks** |
| Tesseract heb + eng on iOS | ~1 week |
| UI: search, sticker details, Smart search, People, tag suggestions, folders, quality test, About, RTL | 4–6 weeks |
| Sticker-pack send: libwebp for iOS, 512²/100 KB/500 KB re-encode, static/animated segregation, pasteboard + URL-scheme handoff | 1–2 weeks |
| Keyboard extension: text keyboard for 4.4.1 compliance + sticker search UI + clipboard delivery + Full Access onboarding | 1–2 weeks |
| Backup/restore format parity, diagnostics | 1 week |
| App Store submission: privacy labels, privacy policy, screenshots, review cycles | 1–2 weeks |
| **Total** | **~17–28 weeks (4–6 months)** |

A Kotlin Multiplatform route (sharing `:core:search`) saves perhaps 3–4 weeks on the search core but
adds KMP build setup and does nothing about the Granite risk, so it lands in the same range.

Two things make this a floor rather than a midpoint. It assumes the Granite path keeps Hebrew
recall — if it does not, iOS ships keyword-only search and the value share drops well below 25%. And
because neither send route is good enough alone (§4), a serious attempt builds **both**, which is
why the keyboard line is in the table at all.

### What the user must buy or own

| Item | Cost | Required for |
|---|---|---|
| **A Mac** | from ~$599 (Mac mini) | Xcode is macOS-only. There is no way to build an iOS app without one. |
| **Apple Developer Program** | **$99 / year** ([Apple](https://developer.apple.com/programs/enroll/)) | TestFlight and App Store distribution. *Not* needed to build and run on your own device. |
| An iPhone to test on | owned or ~$600+ | Needs ~6 GB RAM for meaning search; a 4 GB device falls back to keyword-only. |
| A hosted privacy policy URL | ~free | Guideline 5.1.1(i). Already needed for Play. |

New spend if a Mac is already owned: **$99/year.**

---

## 9. Bottom line for the decision

- **iOS cannot read WhatsApp's stickers.** Sourced, not inferred. No workaround exists. The user
  must hand-feed the app a chat export per chat, forever. This is the blocker that does not bend.
- **No send route is both in-chat and a real sticker.** The keyboard stays in the chat but pastes a
  photo and needs Full Access; the sticker-pack API sends a real sticker but costs ~9 interactions
  and 2 app switches. Both are sourced and both ship today in other apps. Neither is Android's
  2 taps.
- **Three findings went the other way and are worth keeping:** the models fit inside Apple's 4 GB
  limit with no asset delivery at all (the work that dominates the Play release simply does not
  exist on iOS), real stickers *can* be sent, and LiteRT-LM now lists iOS — so the model runtime is
  a medium risk, not a wall. None of them rescues the product.
- **The provable no-network claim cannot be reproduced on iOS**, and the keyboard route would
  require Full Access, which lifts the network sandbox outright.
- 4–6 months of work, $99/year plus a Mac, for about a quarter of the value.

**No-go.** The recommendation is unchanged from the first draft, but the reasoning is narrower and
more honest: not "iOS can't do this", but "iOS can only do this badly, in two different directions,
and the ingest is manual either way." Revisit on trigger 1 in §1 — a WhatsApp sticker export on
iOS. The other triggers do not matter without it.
