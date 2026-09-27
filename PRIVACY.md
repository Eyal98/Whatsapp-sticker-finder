# Privacy

Peel-It is built so that nothing you do in it can leave your phone.

- **No internet access.** The app doesn't request Android's internet permission, so the system
  doesn't let it connect to anything. Every build is checked for this in CI
  (`scripts/check-apk-permissions.sh`).
- **What it reads.** Only the stickers folder you pick (usually WhatsApp's), through Android's
  folder picker. Nothing else on the phone.
- **What it stores.** An index of your stickers (printed text, picture tags, pack names), your own
  tags, stars and edits, meaning-search vectors and, if you turn on People, face data. All of it
  stays in the app's private storage and is left out of cloud backups and phone-to-phone
  transfers. Uninstalling the app deletes it.
- **People (faces).** Off until you turn it on. Faces found on stickers are turned into face
  fingerprints, which count as biometric data. They never leave the phone, aren't backed up, and
  **Delete all face data** on the People screen removes them at any time. Only use it on stickers
  of people who are OK with it.
- **Search history.** When you send a sticker after a search, the app remembers that search and
  sticker so similar searches rank it higher. It stays on the phone, is left out of backups and
  problem reports, and **About → Clear search history** deletes it. The keyboard only learns from
  searches typed on its own keys, never from text it read from the chat box. If you send the same
  sticker twice for the same short search, that search becomes one of the sticker's tags, which you
  can see and remove in the sticker's details.
- **Keyboard.** The sticker keyboard reads at most 100 characters already in the chat box, only
  when you open it, never in password fields, and never saves them.
- **Pack link (optional, off by default).** WhatsApp saves its own copy of a sticker sent from any
  keyboard, and that copy isn't linked to its sticker pack. If you turn on "Peel-It: send from
  WhatsApp's stickers" in Android's accessibility settings, Peel-It sends stickers from packs
  through WhatsApp's own sticker tray instead. Android lets it see only WhatsApp. It does nothing
  until you tap a sticker in the Peel-It keyboard; then it opens WhatsApp's sticker tray, takes one
  screenshot to recognize the sticker, taps it, and discards the screenshot. It keeps only a short
  record of each attempt (steps, WhatsApp's screen element ids, match scores; no text), which
  appears in problem reports. Turn it off in the same settings at any time.
- **Problem reports.** "Build report" makes a text report of counts, versions and error messages,
  with no stickers, file names, sticker text, tags, names or searches. It's only shared if you
  share it yourself, and you see all of it first.
- **Models.** All models run on the phone and are part of the app (see
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). No data is sent to their authors.

Questions: open an issue on this repository.
