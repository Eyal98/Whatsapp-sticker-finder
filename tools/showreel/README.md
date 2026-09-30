# Peel-It showreel

A 37-second motion-graphics reel for Peel-It, in 16:9 (1920×1080) and 9:16 (1080×1920), 60 fps.
It is all code:

- **Picture**: Canvas 2D scenes in `src/scenes.js`, drawn with the brand's own shapes (the
  elephant and the peeling-sticker logo from `tools/brand`), sticker-style lettering, and vector
  stickers in `src/art.js`. Every frame is a pure function of time, so any frame can be drawn on
  its own.
- **Sound**: the music and every sound effect are synthesized with the Web Audio API
  (`src/audio.js`) in an `OfflineAudioContext`, cued to the same beat times as the picture.
- **File**: each frame is encoded with WebCodecs (VP9, Opus) and muxed into WebM by
  `src/export.js`. Nothing is captured in real time, so no frame is ever dropped and every render
  is identical.

The **Showreel** workflow renders both cuts whenever this folder changes and publishes them to the
`showreel` pre-release as MP4 (H.264 + AAC, plays everywhere) and WebM (VP9 + Opus).

## What's new (20 seconds)

A second, shorter reel for an update: `--reel whatsnew` renders `whatsnew.html`, which reuses this
engine (`src/whatsnew.js` for the scenes, `src/whatsnew-audio.js` for the music and cues). The v0.3
cut is 12 bars, 20.0 s, about folders, back up & restore and getting started with Pili:

| Beats | Starts | Scene |
|---|---|---|
| 0–6 | 0:00.0 | Title: "What's new", v0.3 alpha |
| 6–18 | 0:02.5 | Folders: name one, drop stickers in, use it from the keyboard |
| 18–30 | 0:07.5 | Back up & restore: what goes in, password, the file flies to a new phone |
| 30–42 | 0:12.5 | Getting started: the checklist ticks off, Pili's tips |
| 42–48 | 0:17.5 | End card |

Reading time is checked, not guessed: every text that has to be read is registered in `READS` with
when it lands, and `node tools/showreel/render.js --reel whatsnew --check` fails when one is on
screen for less than 0.5 s plus 0.28 s per word (the workflow runs it). `--frames 1.5,4.6` writes
stills to look at. `--max-mb 9` encodes the MP4s two-pass to a target size and fails at 10 MB,
which is what GitHub plays inline; the Showreel workflow publishes them to the `showreel` release.

## Render

Needs Playwright's Chromium (and ffmpeg with libx264 for MP4):

```sh
npm i -g playwright && npx playwright install --with-deps chromium
NODE_PATH=$(npm root -g) node tools/showreel/render.js           # out/peel-it-showreel-16x9.webm and -9x16.webm
NODE_PATH=$(npm root -g) node tools/showreel/render.js --mp4     # also H.264 + AAC MP4s
NODE_PATH=$(npm root -g) node tools/showreel/render.js --serve   # live preview with sound, scrubbing and export
```

Other options: `--format land|port` for one cut, `--quality web` for smaller 1280×720 files to embed
in web pages, `--wav` for the soundtrack on its own.

## Timeline

144 BPM, so a beat is exactly 25 frames at 60 fps and 22 bars last 36.7 s. Times in the code are
in beats; every hit in the picture sits on the 16th-note grid the music uses.

Each scene runs on its own clock: `AT` at the top of `src/scenes.js` says when it starts, and all
the times inside a scene (and its sound cues in `src/audio.js`) count from there. Scenes start on
bar lines and hold for about two seconds after their last element lands, so there is time to read
them. To re-time the reel, change `AT`, then the chords and melody in `src/audio.js` follow the
new bar layout.

| Beats | Starts | Scene |
|---|---|---|
| 0–8 | 0:00.0 | The logo sticker slaps down and its corner peels; the wordmark pops in |
| 8–16 | 0:03.3 | 10,000 stickers rain down. "Which one?" |
| 16–28 | 0:06.7 | Search by meaning: "running late" finds a sticker that says "מאחר" |
| 28–36 | 0:11.7 | Sees the picture (SigLIP 2 picture tags), reads the text (OCR) |
| 36–44 | 0:15.0 | People: faces found, grouped, named, then searched by name |
| 44–52 | 0:18.3 | The sticker keyboard sends a real sticker into a chat |
| 52–60 | 0:21.7 | Nothing leaves your phone: no internet permission, works in airplane mode |
| 60–76 | 0:25.0 | Under the hood: the stack as laptop stickers, then the numbers |
| 76–88 | 0:31.7 | The elephant hops in and tosses the logo into place |

The credit line on the end card is `CREDIT` / `REPO_URL` at the top of `src/scenes.js`.

Fonts: DejaVu Sans, in `fonts/` (license in `fonts/LICENSE-DejaVu.txt`). Emoji stickers use the
system's color emoji font (Noto Color Emoji on Linux, installed by Playwright's `--with-deps`).
