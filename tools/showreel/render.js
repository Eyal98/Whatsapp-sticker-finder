#!/usr/bin/env node
// Renders the Peel-It showreel (showreel.html) with headless Chromium, frame by frame.
//
//   node tools/showreel/render.js                   # 16:9 and 9:16 WebM (VP9 + Opus) in tools/showreel/out
//   node tools/showreel/render.js --format land     # one cut only (land = 16:9, port = 9:16)
//   node tools/showreel/render.js --quality web     # 1280x720 / 720x1280, smaller files for web pages
//   node tools/showreel/render.js --mp4             # also H.264 + AAC MP4s (needs ffmpeg with libx264)
//   node tools/showreel/render.js --wav             # also the soundtrack on its own
//   node tools/showreel/render.js --serve           # live preview at http://127.0.0.1:8123/showreel.html
//
// Add `--reel whatsnew` to any of these for the 20-second "What's new" reel (whatsnew.html), plus:
//   --max-mb 9             # MP4s are encoded to a target size (two-pass), and fail above 10 MB
//   --check                # fails if any text that must be read is on screen too briefly
//   --frames 1.5,4,8.2     # PNG stills at those seconds, in out/frames (add --format land|port)
//
// Needs Playwright's Chromium: `npm i -g playwright && npx playwright install --with-deps chromium`
// (run with NODE_PATH=$(npm root -g) if Playwright is installed globally).
'use strict';

const { chromium } = require('playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const ROOT = __dirname;
const args = process.argv.slice(2);
const opt = name => args.includes(`--${name}`);
const val = (name, dflt) => { const i = args.indexOf(`--${name}`); return i >= 0 ? args[i + 1] : dflt; };
const OUT = path.resolve(val('out', path.join(ROOT, 'out')));
const formats = val('format', 'land,port').split(',');
const reel = val('reel', 'showreel');
const PAGE = reel === 'whatsnew' ? 'whatsnew.html' : 'showreel.html';
const PREFIX = reel === 'whatsnew' ? 'peel-it-whatsnew' : 'peel-it-showreel';
const quality = val('quality', 'high');
const suffix = { land: '16x9', port: '9x16' };

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.ttf': 'font/ttf' };
function serve(port) {
  return new Promise(resolve => {
    const srv = http.createServer((req, res) => {
      const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]));
      if (!file.startsWith(ROOT)) { res.statusCode = 403; res.end(); return; }
      fs.readFile(file, (err, data) => {
        if (err) { res.statusCode = 404; res.end(); return; }
        res.setHeader('content-type', TYPES[path.extname(file)] || 'application/octet-stream');
        res.end(data);
      });
    });
    srv.listen(port, '127.0.0.1', () => resolve(srv));
  });
}

async function main() {
  if (opt('serve')) {
    const srv = await serve(+val('port', 8123));
    console.log(`Preview: http://127.0.0.1:${srv.address().port}/${PAGE}`);
    return;
  }
  fs.mkdirSync(OUT, { recursive: true });
  const srv = await serve(0);
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM || undefined });
  try {
    const page = await browser.newPage();
    page.on('pageerror', e => console.error('[page]', e.message));
    let parts = [];
    await page.exposeFunction('reelChunk', b64 => { parts.push(Buffer.from(b64, 'base64')); });
    await page.exposeFunction('reelProgress', p => process.stdout.write(`\r  ${Math.round(p * 100)}%   `));
    await page.goto(`http://127.0.0.1:${srv.address().port}/${PAGE}?headless`);
    await page.waitForFunction(() => window.Reel && window.Reel.ready, null, { timeout: 60000 });
    if (opt('check')) {
      if (reel !== 'whatsnew') throw new Error('--check is for --reel whatsnew');
      const reads = await page.evaluate(() => readReport());
      let bad = 0;
      console.log('Reading time of every text that has to be read (needs 0.5 s + 0.28 s per word):');
      for (const r of reads) {
        const ok = r.seconds >= r.need;
        if (!ok) bad++;
        console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${r.scene.padEnd(8)} ${r.label.padEnd(34)} ${r.seconds.toFixed(2)} s on screen, needs ${r.need.toFixed(2)} s`);
      }
      if (bad) { console.error(`${bad} text(s) on screen too briefly`); process.exitCode = 1; }
      else console.log('All texts stay on screen long enough.');
      return;
    }
    if (val('frames', '')) {
      const dir = path.join(OUT, 'frames');
      fs.mkdirSync(dir, { recursive: true });
      for (const fmt of formats) for (const sec of val('frames', '').split(',').map(Number)) {
        const url = await page.evaluate(({ fmt, sec }) => Reel.frame(fmt, sec, 0.5), { fmt, sec });
        const file = path.join(dir, `${suffix[fmt]}-${sec.toFixed(2)}.png`);
        fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
        console.log('Wrote', path.relative(process.cwd(), file));
      }
      return;
    }
    // Streams page bytes to Node in 1 MB pieces.
    await page.evaluate(() => {
      window.sendBytes = async bytes => {
        for (let o = 0; o < bytes.length; o += 1 << 20) {
          const piece = bytes.subarray(o, o + (1 << 20));
          let bin = '';
          for (let i = 0; i < piece.length; i += 0x8000) bin += String.fromCharCode.apply(null, piece.subarray(i, i + 0x8000));
          await window.reelChunk(btoa(bin));
        }
      };
    });
    if (opt('wav')) {
      parts = [];
      await page.evaluate(async () => window.sendBytes(Reel.wavBytes(await Reel.getSoundtrack())));
      fs.writeFileSync(path.join(OUT, `${PREFIX}.wav`), Buffer.concat(parts));
      console.log(`Wrote ${PREFIX}.wav`);
    }
    for (const fmt of formats) {
      const name = `${PREFIX}-${suffix[fmt]}${quality === 'web' ? '-web' : ''}`;
      console.log(`Rendering ${name} (${quality})…`);
      parts = [];
      const info = await page.evaluate(async ({ fmt, quality }) => {
        const r = await Reel.exportWebM({ format: fmt, quality, onProgress: p => window.reelProgress(p) });
        window.lastWebM = r.bytes;
        await window.sendBytes(r.bytes);
        return r.info;
      }, { fmt, quality });
      const file = path.join(OUT, `${name}.webm`);
      fs.writeFileSync(file, Buffer.concat(parts));
      console.log(`\r  ${path.relative(process.cwd(), file)}: ${(fs.statSync(file).size / 1e6).toFixed(1)} MB, ${info.width}x${info.height}, ${info.frames} frames, ${info.mode}, ${info.seconds} s`);
      // Check that Chromium plays the file back: duration, a seek, and a decoded frame.
      const check = await page.evaluate(async () => {
        const v = document.createElement('video');
        v.muted = true;
        v.src = URL.createObjectURL(new Blob([window.lastWebM], { type: 'video/webm' }));
        await new Promise((ok, bad) => { v.onloadedmetadata = ok; v.onerror = () => bad(new Error('not playable')); });
        const duration = v.duration;
        v.currentTime = 10;
        await new Promise(ok => { v.onseeked = ok; });
        return { duration, expected: DURATION, width: v.videoWidth, height: v.videoHeight, seeked: v.currentTime };
      });
      if (Math.abs(check.duration - check.expected) > 0.1) throw new Error(`unexpected duration ${check.duration}, expected ${check.expected}`);
      console.log(`  plays back: ${check.width}x${check.height}, ${check.duration.toFixed(2)} s`);
      if (opt('mp4')) toMp4(file, path.join(OUT, `${name}.mp4`), +val('max-mb', 0), check.duration);
    }
  } finally {
    await browser.close();
    srv.close();
  }
}

// With maxMb, two-pass at the bitrate that fits (and lower until it does): the size is checked.
function toMp4(input, output, maxMb = 0, seconds = 0) {
  const probe = spawnSync('ffmpeg', ['-hide_banner', '-encoders'], { encoding: 'utf8' });
  if (probe.status !== 0 || !/libx264/.test(probe.stdout)) {
    console.log('  skipping MP4: ffmpeg with libx264 not found');
    return;
  }
  // Chromium's VP9 encoder writes BT.601 (smpte170m) YUV; convert to the BT.709 that HD players
  // assume, and tag it, so the colours match the WebM everywhere.
  const colour = ['-vf', 'scale=in_color_matrix=bt601:out_color_matrix=bt709:in_range=tv:out_range=tv'];
  const tags = ['-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709', '-color_range', 'tv'];
  if (maxMb > 0) {
    if (maxMb >= 10) throw new Error('--max-mb must stay below 10');
    const audioBits = 128000;
    let videoBits = Math.floor((maxMb * 1e6 * 8 * 0.94) / seconds - audioBits);
    for (let attempt = 0; attempt < 4; attempt++) {
      const log = path.join(OUT, 'ffmpeg2pass');
      const common = ['-y', '-loglevel', 'error', '-i', input, ...colour, '-c:v', 'libx264', '-preset', 'slow', '-b:v', String(videoBits),
        '-maxrate', String(Math.round(videoBits * 1.5)), '-bufsize', String(videoBits * 2), '-pix_fmt', 'yuv420p', '-profile:v', 'high', ...tags, '-passlogfile', log];
      let r = spawnSync('ffmpeg', [...common, '-pass', '1', '-an', '-f', 'mp4', '/dev/null'], { stdio: 'inherit' });
      if (r.status === 0) r = spawnSync('ffmpeg', [...common, '-pass', '2', '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart', output], { stdio: 'inherit' });
      for (const f of fs.readdirSync(OUT)) if (f.startsWith('ffmpeg2pass')) fs.rmSync(path.join(OUT, f));
      if (r.status !== 0) throw new Error('ffmpeg failed');
      const size = fs.statSync(output).size;
      if (size < 9.9e6) { console.log(`  ${path.relative(process.cwd(), output)}: ${(size / 1e6).toFixed(2)} MB (H.264 + AAC, ${Math.round(videoBits / 1000)} kbit/s video)`); return; }
      videoBits = Math.floor(videoBits * 0.85);
    }
    throw new Error(`${output} is still 10 MB or more`);
  }
  const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', input, ...colour,
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-pix_fmt', 'yuv420p', '-profile:v', 'high', ...tags,
    '-c:a', 'aac', '-b:a', '256k', '-movflags', '+faststart', output], { stdio: 'inherit' });
  if (r.status !== 0) throw new Error('ffmpeg failed');
  console.log(`  ${path.relative(process.cwd(), output)}: ${(fs.statSync(output).size / 1e6).toFixed(1)} MB (H.264 + AAC)`);
}

main().catch(e => { console.error(e); process.exit(1); });
