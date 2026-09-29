# Privacy

Peel-It is built so that nothing you do in it can leave your phone.

- **No internet access.** The app doesn't request Android's internet permission, so the system
  doesn't let it connect to anything. Every build is checked for this in CI
  (`scripts/check-apk-permissions.sh`).
- **What it reads.** Only the stickers folder you pick (usually WhatsApp's), through Android's
  folder picker, and chat exports you choose to import (below). Nothing else on the phone.
- **What it stores.** An index of your stickers (printed text, picture tags, pack names), your own
  tags, stars and edits, meaning-search vectors and, if you turn on People, face data. All of it
  stays in the app's private storage and is left out of Android's automatic cloud backups and
  phone-to-phone transfers (your own backup files, below, are the only way it leaves). It isn't
  encrypted by the app beyond what Android does for app storage. Uninstalling the app deletes it.
- **People (faces).** Off until you turn it on. Faces found on stickers are turned into face
  fingerprints, which count as biometric data. They never leave the phone on their own and aren't
  in Android's backups; only a backup file you make with "Include people" ticked holds one per
  named person (below). **Delete all face data** on the People screen removes them at any time. Only use it on stickers
  of people who are OK with it.
- **Search history.** When you send a sticker after a search, the app remembers that search and
  sticker so similar searches rank it higher. It stays on the phone, is left out of Android's
  backups and of problem reports (a backup file you make does include it, see below), and **About → Clear search history** deletes it. The keyboard only learns from
  searches typed on its own keys, never from text it read from the chat box. If you send the same
  sticker twice for the same short search, that search becomes one of the sticker's tags, which you
  can see and remove in the sticker's details.
- **Learning from your chats (optional).** If you import a WhatsApp chat export (Smart search →
  Learn from your chats, or share it from WhatsApp's Export chat), the app reads the chat's text
  and its sticker files, on the phone, to learn what you use each sticker for from the messages
  written just before it was sent. It keeps only a set of numbers per sticker (an average of what
  those messages meant) and how many sends it's based on, plus a fingerprint (hash) of each
  imported chat so the same export isn't counted twice. It never keeps the messages, names, phone
  numbers or the export itself, doesn't log them, and leaves what it learned out of backups and
  problem reports (which show only how many chats and stickers). An export holds the other
  person's messages too, so only import your own chats. **Forget imported chats** on the same
  screen deletes everything learned from chats.
- **Your own backups.** About → Back up saves what you made (tags, descriptions, stars, hidden
  picture tags, folders, how often you used each sticker, search history, test searches and the
  search setting) to a file you choose, to restore on another phone. It's never made by itself and never
  sent anywhere: you pick where it goes. With a password it's encrypted (AES-256-GCM, key from the
  password with PBKDF2); without one, anyone with the file can read it. People's names are only
  included if you tick the box, and then with one face fingerprint per named person (biometric
  data) so the new phone can match names to faces. What a restore can't match yet waits in the
  app's private storage until the stickers or faces are found; "Delete all face data" also deletes
  waiting face fingerprints. A backup file is checked before anything in it is used, and one too
  large to be a real backup is refused.
- **Keyboard.** The sticker keyboard reads at most 100 characters already in the chat box, only
  when you open it, never in password fields, and never saves them.
- **Problem reports.** "Build report" makes a text report of counts, versions and error messages,
  with no stickers, file names, sticker text, tags, names or searches. It's only shared if you
  share it yourself, and you see all of it first.
- **Models.** All models run on the phone and are part of the app (see
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). No data is sent to their authors.

Questions: open an issue on this repository.
