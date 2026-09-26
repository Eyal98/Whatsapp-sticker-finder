# Peel-It showreel

A 20-second motion-graphics reel for Peel-It, in 16:9 (1920×1080) and 9:16 (1080×1920), 60 fps.
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

## Render

Needs Playwright's Chromium (and ffmpeg with libx264 for MP4):

```sh
npm i -g playwright && npx playwright install --with-deps chromium
NODE_PATH=$(npm root -g) node tools/showreel/render.js           # out/peel-it-showreel-16x9.webm and -9x16.webm
NODE_PATH=$(npm root -g) node tools/showreel/render.js --mp4     # also H.264 + AAC MP4s
NODE_PATH=$(npm root -g) node tools/showreel/render.js --serve   # live preview with sound, scrubbing and export
```

Other options: `--format land|port` for one cut, `--quality web` for smaller files, `--wav` for the
soundtrack on its own.

## Timeline

144 BPM, so a beat is exactly 25 frames at 60 fps and 12 bars last 20 s. Times in the code are in
beats; every hit in the picture sits on the 16th-note grid the music uses.

| Beats | Starts | Scene |
|---|---|---|
| 0–4 | 0:00.0 | The logo sticker slaps down and its corner peels; the wordmark pops in |
| 4–8 | 0:01.7 | 10,000 stickers rain down. "Which one?" |
| 8–16 | 0:03.3 | Search by meaning: "running late" finds a sticker that says "מאחר" |
| 16–20 | 0:06.7 | Sees the picture (SigLIP 2 picture tags), reads the text (OCR) |
| 20–24 | 0:08.3 | People: faces found, grouped and named |
| 24–28 | 0:10.0 | The sticker keyboard sends a real sticker into a chat |
| 28–32 | 0:11.7 | Nothing leaves your phone: no internet permission |
| 32–40 | 0:13.3 | Under the hood: the stack as laptop stickers, then the numbers |
| 40–48 | 0:16.7 | The elephant hops in and tosses the logo into place |

The credit line on the end card is `CREDIT` / `REPO_URL` at the top of `src/scenes.js`.

Fonts: DejaVu Sans, in `fonts/` (license in `fonts/LICENSE-DejaVu.txt`). Emoji stickers use the
system's color emoji font (Noto Color Emoji on Linux, installed by Playwright's `--with-deps`).
