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
    console.log(`Preview: http://127.0.0.1:${srv.address().port}/showreel.html`);
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
    await page.goto(`http://127.0.0.1:${srv.address().port}/showreel.html?headless`);
    await page.waitForFunction(() => window.Reel && window.Reel.ready, null, { timeout: 60000 });
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
      fs.writeFileSync(path.join(OUT, 'peel-it-showreel.wav'), Buffer.concat(parts));
      console.log('Wrote peel-it-showreel.wav');
    }
    for (const fmt of formats) {
      const name = `peel-it-showreel-${suffix[fmt]}${quality === 'web' ? '-web' : ''}`;
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
      if (opt('mp4')) toMp4(file, path.join(OUT, `${name}.mp4`));
    }
  } finally {
    await browser.close();
    srv.close();
  }
}

function toMp4(input, output) {
  const probe = spawnSync('ffmpeg', ['-hide_banner', '-encoders'], { encoding: 'utf8' });
  if (probe.status !== 0 || !/libx264/.test(probe.stdout)) {
    console.log('  skipping MP4: ffmpeg with libx264 not found');
    return;
  }
  // Chromium's VP9 encoder writes BT.601 (smpte170m) YUV; convert to the BT.709 that HD players
  // assume, and tag it, so the colours match the WebM everywhere.
  const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', input,
    '-vf', 'scale=in_color_matrix=bt601:out_color_matrix=bt709:in_range=tv:out_range=tv',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-pix_fmt', 'yuv420p', '-profile:v', 'high',
    '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709', '-color_range', 'tv',
    '-c:a', 'aac', '-b:a', '256k', '-movflags', '+faststart', output], { stdio: 'inherit' });
  if (r.status !== 0) throw new Error('ffmpeg failed');
  console.log(`  ${path.relative(process.cwd(), output)}: ${(fs.statSync(output).size / 1e6).toFixed(1)} MB (H.264 + AAC)`);
}

main().catch(e => { console.error(e); process.exit(1); });
