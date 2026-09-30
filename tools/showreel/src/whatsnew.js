// Peel-It "What's new" reel: 20 seconds (48 beats at 144 BPM = 12 bars) about the v0.3 update:
// folders, back up & restore, and getting started with Pili. It reuses the showreel's drawing core
// (core.js), art (art.js) and transitions (scenes.js) and draws its own scenes. Like the showreel,
// every frame is a pure function of time.
//
// Reading time is part of the design: every text that has to be read is registered with READS
// (when it lands, when it leaves, how many words) and `render.js --check` fails when one is on
// screen for less than 0.5 s + 0.28 s per word.
'use strict';

const WN = {
  // Scene starts, in beats.
  AT: { intro: 0, folders: 6, backup: 18, start: 30, end: 42 },
  // When each scene stops being readable: its transition out starts (global beats).
  LEAVE: { intro: 5.375, folders: 17.5, backup: 29.5, start: 41.5, end: 48 },
};
const wnScene = name => (g, tb) => WN_SCENES[name](g, tb - WN.AT[name]);

// ---------------------------------------------------------------- what has to be read

const READS = [];
const SETTLE = 1.0; // beats for a popped-in element to settle
// A text that lands (pops in) at local beat `pop` of `scene` and stays until the scene is left.
function read(scene, label, words, pop, { leave = null, extra = 0 } = {}) {
  READS.push({ scene, label, words, from: WN.AT[scene] + pop + SETTLE + extra, to: leave ?? WN.LEAVE[scene] });
}
const needSeconds = words => 0.5 + 0.28 * words;

// ---------------------------------------------------------------- shared bits

const FOLDER_PATH = new Path2D('M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z');
function folderIcon(g, x, y, s, color) {
  g.save();
  g.translate(x - s / 2, y - s / 2);
  g.scale(s / 24, s / 24);
  g.fillStyle = color;
  g.fill(FOLDER_PATH);
  g.restore();
}
const folderPart = color => ({ icon: (g, x, y, s) => folderIcon(g, x, y, s * 1.05, color) });

// Headline in sticker lettering: left-aligned on wide screens, centred on tall ones.
function headline(ctx, lines, t, { t0 = 0.25, size = LP(130, 118), y0 = LP(300, 250), lead = LP(140, 128), stagger = 0.06 } = {}) {
  const x = LP(110, RS.W / 2), align = LP('left', 'center');
  let delay = t0;
  lines.forEach(([text, fill, shade], i) => {
    stext(ctx, text, x, y0 + i * lead, { size, fill, shade, align, letter: letterPop(t, delay, stagger, { size }) });
    delay += 0.3 + text.length * stagger;
  });
}
// The lines' last letter has landed this many beats after t0 (for READS).
const headlineDone = (lines, t0 = 0.25, stagger = 0.06) => lines.reduce((d, [text]) => d + 0.3 + text.length * stagger, t0) - 0.3;

// A step chip (icon + a few words) under the headline; `i` places it in the column.
function stepChip(ctx, t, pop0, i, parts, { y0 = LP(560, 470), gap = LP(112, 92), size = LP(38, 33) } = {}) {
  const s = pop(t, pop0, 1.4, 0.4) * (1 + wobble(t, pop0 + 1.5, 0.03, 2, 6));
  if (s <= 0.002) return;
  chip(ctx, LP(110, RS.W / 2), y0 + i * gap, parts, { size, align: LP('left', 'center'), scale: s });
}

// Rounded pill inside a phone screen (UI units). Returns its width.
function uiChip(g, x, cy, text, { icon = false, selected = false, scale = 1, alpha = 1, measure = false } = {}) {
  g.save();
  g.font = fb(14);
  const tw = g.measureText(text).width;
  const w = 26 + (icon ? 20 : 0) + tw;
  if (measure) { g.restore(); return w; }
  if (scale > 0.002 && alpha > 0.002) {
    g.translate(x + w / 2, cy);
    g.scale(scale, scale);
    g.globalAlpha *= alpha;
    g.translate(-w / 2, 0);
    g.fillStyle = selected ? C.lavBg : C.surfHigh;
    g.fill(rrP(0, -17, w, 34, 17));
    if (selected) { g.strokeStyle = C.violet; g.lineWidth = 2; g.stroke(rrP(0, -17, w, 34, 17)); }
    if (icon) folderIcon(g, 24, 0, 15, selected ? C.violetInk : C.outline);
    txt(g, text, icon ? 39 : 13, 5, { size: 14, bold: true, color: selected ? C.violetInk : C.ink });
  }
  g.restore();
  return w;
}
function uiHeader(g, title) {
  statusBar(g, 400);
  logo(g, 42, 86, 36, { rot: -0.14, shadow: false });
  txt(g, title, 72, 96, { size: 25, bold: true, color: C.inkD });
}
function tapRipple(g, x, y, t, t0) {
  const p = seg(t, t0, t0 + 0.5);
  if (p <= 0 || p >= 1) return;
  g.save();
  g.globalAlpha = 0.45 * (1 - p);
  g.fillStyle = C.violet;
  g.fill(circleP(x, y, 8 + 34 * ease.outCubic(p)));
  g.restore();
}
function check(g, x, y, s, t, t0, color = C.green) {
  const k = pop(t, t0, 1.6, 0.4);
  if (k <= 0.002) return;
  g.save();
  g.translate(x, y);
  g.scale(k, k);
  g.fillStyle = color;
  g.fill(circleP(0, 0, s));
  checkMark(g, 0, 0, s * 1.05, '#fff', 0.2);
  g.restore();
}

// ================================================================ scene 1: title (6 beats)

const I_ = { logo: 0.25, title: 1.0, chip: 1.5 };
function sIntro(ctx, t) {
  radialBg(ctx, '#7D6FF3', C.violet, RS.W * 0.4, RS.H * 0.45);
  patternFill(ctx, 'icons', '#FFFFFF', 0.08, t, { vx: -26, vy: -14 });
  const lx = LP(560, 540), ly = LP(520, 640), ls = LP(430, 500);
  sunburst(ctx, lx, ly, 2400, 18, '#FFFFFF', 0.07 * seg(t, 0.25, 0.9), t * 0.12);
  let s = 1, rot = -8;
  if (t < I_.logo) {
    const p = ease.inCubic(seg(t, -0.02, I_.logo));
    s = lerp(2.9, 1, p);
    rot = lerp(-34, -8, p);
  } else rot = -8 + wobble(t, I_.logo, 10, 1.3, 3.4);
  const [sqx, sqy] = t >= I_.logo ? squash(t, I_.logo, 0.22) : [1, 1];
  const k = 20 + 22 * Math.sin(Math.PI * ease.outQuad(seg(t, 1.0, 1.5))) + wobble(t, 1.5, 5, 2, 5);
  ring(ctx, t, I_.logo, lx, ly, { r0: ls * 0.45, r1: ls * 1.3, width: 32 });
  burstLines(ctx, t, I_.logo, lx, ly, { n: 12, r0: ls * 0.6, r1: ls * 1.15, width: 16, rot: 0.2 });
  logo(ctx, lx, ly, ls * s, { rot: rad(rot), k, sx: sqx, sy: sqy });
  const corner = logoPoint(lx, ly, ls, rad(rot), 80, 80);
  sparkles(ctx, t, 1.05, corner[0], corner[1], { n: 9, radius: 130, size: 34, seed: 11 });
  // "What's new" in sticker lettering, then the version.
  const ws = LP(190, 150);
  const line = [["What's", C.pink, C.pinkDD], ['new', C.amberL, C.amberD]];
  const x = LP(830, RS.W / 2), align = LP('left', 'center');
  const y1 = LP(500, 1080), y2 = LP(680, 1250);
  stext(ctx, line[0][0], x, y1, { size: ws, fill: line[0][1], shade: line[0][2], align, letter: letterPop(t, I_.title, 0.09, { size: ws, rise: 0.7, spin: 0.35 }) });
  stext(ctx, line[1][0], x, y2, { size: ws, fill: line[1][1], shade: line[1][2], align, letter: letterPop(t, I_.title + 0.6, 0.09, { size: ws, rise: 0.7, spin: 0.35 }) });
  chip(ctx, x, LP(795, 1370), [{ text: 'v0.3 alpha' }], { size: LP(44, 42), align, scale: pop(t, I_.chip, 1.3, 0.42) * (1 + wobble(t, 4.0, 0.05, 2, 6)) });
  sparkles(ctx, t, I_.title + 1.2, LP(1230, 800), LP(400, 980), { n: 7, radius: 110, size: 28, seed: 21 });
}
read('intro', "What's new", 2, I_.title + 0.6 + 0.09 * 2, { extra: 0 });
read('intro', 'v0.3 alpha', 2, I_.chip);

// ================================================================ scene 2: folders (12 beats)

const F_ = { title: 0.3, step1: 1.75, tap: 2.0, chip: 2.25, step2: 4.0, fly: [4.25, 4.75, 5.25], select: 6.0, kb: 6.5, step3: 6.25, sticker: 7.5, send: 9.0 };
const F_EMOJI = ['😂', '😍', '😭', '🥳', '🐸', '🤔', '😴', '🎂', '🙈', '🐶', '👍', '🔥'];
const F_MEMBERS = [0, 4, 8]; // the stickers that go into "Memes"
const F_TILE = 116, F_GAP = 10, F_Y0 = 262;
const fCell = i => [16 + (i % 3) * (F_TILE + F_GAP) + F_TILE / 2, F_Y0 + Math.floor(i / 3) * (F_TILE + F_GAP) + F_TILE / 2];

function foldersApp(g, H, t) {
  uiHeader(g, 'Peel-It');
  // search field
  g.fillStyle = C.surf;
  g.fill(rrP(16, 116, 368, 56, 28));
  magnifier(g, 46, 144, 24, C.outline);
  txt(g, 'Search stickers (עברית / English)', 68, 150, { size: 16, color: C.outline });
  // the folder chips
  const selected = t >= F_.select;
  const wAll = uiChip(g, 0, 0, 'All', { measure: true });
  const wMemes = uiChip(g, 0, 0, 'Memes · 3', { icon: true, measure: true });
  const memesX = 16 + wAll + 8;
  const chipS = pop(t, F_.chip, 1.5, 0.4);
  const arrived = F_.fly.filter(f => t >= f + 0.4).length;
  const bump = arrived ? wobble(t, F_.fly[arrived - 1] + 0.4, 0.1, 2, 6) : 0;
  const newX = memesX + (wMemes + 8) * clamp(chipS, 0, 1);
  uiChip(g, 16, 205, 'All', { selected: !selected });
  if (chipS > 0.002) uiChip(g, memesX, 205, `Memes · ${arrived}`, { icon: true, selected, scale: chipS * (1 + bump) });
  uiChip(g, newX, 205, '＋ New folder');
  tapRipple(g, newX + 50, 205, t, F_.tap);
  // the stickers
  const sel = ease.inOutCubic(seg(t, F_.select, F_.select + 0.6));
  txt(g, selected ? 'Memes' : 'Recent', 18, 246, { size: 14, bold: true, color: C.outline });
  for (let i = 0; i < 12; i++) {
    const m = F_MEMBERS.indexOf(i);
    let [cx, cy] = fCell(i), k = 1, a = 1;
    if (m >= 0) [cx, cy] = mix2(fCell(i), fCell(m), sel);
    else { k = 1 - sel; a = 1 - sel; }
    if (k <= 0.002) continue;
    g.save();
    g.globalAlpha *= a;
    g.fillStyle = C.surfLow;
    g.fill(rrP(cx - (F_TILE * k) / 2, cy - (F_TILE * k) / 2, F_TILE * k, F_TILE * k, 20 * k));
    drawSprite(g, emojiSticker(F_EMOJI[i], 104), cx, cy, 0.72 * k * pop(t, 0.2 + i * 0.05, 1.5, 0.4), i % 2 ? 0.06 : -0.06);
    g.restore();
  }
  // copies fly into the folder
  F_MEMBERS.forEach((idx, k) => {
    const p = seg(t, F_.fly[k], F_.fly[k] + 0.45);
    if (p <= 0 || p >= 1) return;
    const e = ease.inOutCubic(p);
    const [x0, y0] = fCell(idx), x1 = memesX + wMemes / 2, y1 = 205;
    const x = lerp(x0, x1, e), y = lerp(y0, y1, e) - Math.sin(Math.PI * p) * 70;
    drawSprite(g, emojiSticker(F_EMOJI[idx], 104), x, y, lerp(0.72, 0.22, e), (1 - p) * 0.4);
  });
}
function foldersChat(g, H, t) {
  g.fillStyle = C.lavPale;
  g.fillRect(0, 0, 400, H);
  g.fillStyle = C.violet;
  g.fillRect(0, 0, 400, 100);
  statusBar(g, 400, true);
  g.fillStyle = 'rgba(255,255,255,.25)';
  g.fill(circleP(38, 70, 18));
  txt(g, 'Chat', 66, 78, { size: 22, bold: true, color: '#fff' });
  // messages
  g.fillStyle = '#fff';
  g.fill(rrP(16, 122, 190, 44, 18));
  txt(g, 'Send me a meme!', 30, 150, { size: 16, color: C.ink });
  // sent sticker
  const sent = pop(t, F_.send + 0.4, 1.4, 0.4);
  if (sent > 0.002) {
    drawSprite(g, emojiSticker(F_EMOJI[0], 104), 300, 250, 1.2 * sent, 0.05);
    check(g, 352, 322, 9, t, F_.send + 1.2, C.skyD);
  }
  // the keyboard
  const kbH = 330, top = H - kbH;
  g.fillStyle = '#EEEBFA';
  g.fillRect(0, top, 400, kbH);
  g.fillStyle = '#fff';
  g.fill(rrP(16, top + 14, 368, 46, 23));
  magnifier(g, 42, top + 37, 20, C.outline);
  txt(g, 'Search stickers', 62, top + 43, { size: 15, color: C.outline });
  uiChip(g, 16, top + 90, 'All');
  const wAll = uiChip(g, 0, 0, 'All', { measure: true });
  uiChip(g, 16 + wAll + 8, top + 90, 'Memes · 3', { icon: true, selected: true, scale: pop(t, F_.kb + 0.4, 1.5, 0.4) });
  F_MEMBERS.forEach((idx, i) => {
    const s = pop(t, F_.sticker + i * 0.2, 1.5, 0.4);
    const cx = 72 + i * 128, cy = top + 200;
    g.fillStyle = '#fff';
    g.fill(rrP(cx - 54, cy - 54, 108, 108, 20));
    let lift = 0;
    if (i === 0 && t >= F_.send) lift = ease.outCubic(seg(t, F_.send, F_.send + 0.4));
    const fly = i === 0 ? seg(t, F_.send, F_.send + 0.4) : 0;
    if (i === 0 && fly >= 1) return;
    drawSprite(g, emojiSticker(F_EMOJI[idx], 104), lerp(cx, 300, ease.inOutCubic(fly)), lerp(cy, 250, ease.inOutCubic(fly)) - Math.sin(Math.PI * fly) * 40, lerp(0.72, 1.2, lift) * s, (i % 2 ? 0.06 : -0.06) * (1 - fly));
  });
  tapRipple(g, 72, top + 200, t, F_.send - 0.1);
}
function foldersUI(g, sw, sh, t) {
  const u = sw / 400;
  g.scale(u, u);
  const H = sh / u;
  g.fillStyle = C.bg;
  g.fillRect(0, 0, 400, H);
  foldersApp(g, H, t);
  const kb = seg(t, F_.kb, F_.kb + 0.6);
  if (kb > 0) {
    g.save();
    g.clip(circleP(200, H - 60, ease.inOutCubic(kb) * 1100));
    foldersChat(g, H, t);
    g.restore();
  }
}
function sFolders(ctx, t) {
  linearBg(ctx, C.amberL, C.orange, 1.0);
  patternFill(ctx, 'icons', '#FFFFFF', 0.13, t, { vx: 22, vy: -14 });
  const P = LP({ x: 1420, y: 560, w: 430, h: 900 }, { x: 540, y: 1270, w: 520, h: 1080 });
  const enter = spring(t + 0.25, 1.05, 0.42);
  const py = P.y + (1 - enter) * RS.H * 0.95;
  const lines = [['Folders', C.ink, C.inkDD]];
  headline(ctx, lines, t, { t0: F_.title, size: LP(170, 150), y0: LP(310, 260) });
  stepChip(ctx, t, F_.step1, 0, [folderPart(C.orangeD), { text: 'Name a folder' }], { y0: LP(480, 400) });
  stepChip(ctx, t, F_.step2, 1, [{ emoji: '👆' }, { text: 'Drop stickers in' }], { y0: LP(480, 400) });
  stepChip(ctx, t, F_.step3, 2, [{ emoji: '⌨️' }, { text: 'Also in the keyboard' }], { y0: LP(480, 400) });
  phone(ctx, P.x, py, P.w, P.h, (g, sw, sh) => foldersUI(g, sw, sh, t), { rot: LP(0.03, 0.015) * enter });
  const xf = phoneXf(P.x, py, P.w, P.h, LP(0.03, 0.015) * enter);
  const bump = (t0, [ux, uy]) => { const [x, y] = xf(ux, uy); sparkles(ctx, t, t0, x, y, { n: 6, radius: 90, size: 26, seed: Math.round(t0 * 7), color: '#fff' }); };
  bump(F_.fly[2] + 0.4, [120, 205]);
  bump(F_.send + 0.4, [300, 250]);
  confetti(ctx, t, F_.send + 0.4, xf(300, 250)[0], xf(300, 250)[1], { n: 26, seed: 8, dir: -Math.PI / 2, spread: 1.4, speed: [500, 900], size: 12 });
}
read('folders', 'Folders', 1, F_.title + 0.3 + 0.06 * 7);
read('folders', 'Name a folder', 3, F_.step1);
read('folders', 'Drop stickers in', 3, F_.step2);
read('folders', 'Also in the keyboard', 4, F_.step3);

// ================================================================ scene 3: back up & restore (12 beats)

const B_ = { title: 0.25, step: [1.5, 3.25, 5.0, 6.5], tick: [1.5, 2.0, 2.5, 3.0], lock: 3.75, save: 4.25, fly: 4.5, land: 6.0, restore: 6.2, done: 8.6, rows: 8.9, folders: 9.6 };
const B_ROWS = [['🏷️', 'Tags'], ['⭐', 'Stars'], ['📁', 'Folders'], ['🔍', 'Search history']];
function bkOldUI(g, sw, sh, t) {
  const u = sw / 400;
  g.scale(u, u);
  const H = sh / u;
  g.fillStyle = C.bg;
  g.fillRect(0, 0, 400, H);
  statusBar(g, 400);
  txt(g, 'Back up', 24, 108, { size: 30, bold: true, color: C.inkD });
  txt(g, 'Choose what goes in the file', 24, 138, { size: 15, color: C.outline });
  B_ROWS.forEach(([e, label], i) => {
    const y = 170 + i * 66;
    g.fillStyle = C.surfLow;
    g.fill(rrP(16, y, 368, 56, 16));
    emoji(g, e, 46, y + 28, 26);
    txt(g, label, 76, y + 35, { size: 19, bold: true, color: C.ink });
    g.strokeStyle = C.outlineV;
    g.lineWidth = 3;
    g.stroke(circleP(350, y + 28, 13));
    check(g, 350, y + 28, 13, t, B_.tick[i], C.teal);
  });
  // People's names: off unless ticked
  const y = 170 + 4 * 66;
  g.fillStyle = C.surfLow;
  g.fill(rrP(16, y, 368, 56, 16));
  emoji(g, '🙂', 46, y + 28, 26);
  txt(g, "People's names", 76, y + 28, { size: 19, bold: true, color: C.ink });
  txt(g, 'off unless you tick it', 76, y + 47, { size: 12, color: C.outline });
  g.strokeStyle = C.outlineV;
  g.lineWidth = 3;
  g.stroke(circleP(350, y + 28, 13));
  // password
  const py = y + 84;
  g.fillStyle = C.surf;
  g.fill(rrP(16, py, 368, 54, 27));
  txt(g, 'Password (optional)', 40, py + 33, { size: 15, color: C.outline });
  const dots = clamp(Math.floor((t - B_.lock + 0.5) / 0.12), 0, 7);
  if (dots) { g.fillStyle = C.ink; for (let i = 0; i < dots; i++) g.fill(circleP(190 + i * 16, py + 27, 5)); }
  const shackle = 1 - ease.outBack(seg(t, B_.lock, B_.lock + 0.4));
  padlock(g, 346, py + 26, 26, { shackle: clamp(shackle, 0, 1) });
  // save button
  const press = 1 - 0.06 * Math.sin(Math.PI * seg(t, B_.save, B_.save + 0.35));
  g.save();
  g.translate(200, H - 92);
  g.scale(press, press);
  g.fillStyle = C.violet;
  g.fill(rrP(-150, -28, 300, 56, 28));
  txt(g, 'Save backup', 0, 7, { size: 20, bold: true, color: '#fff', align: 'center' });
  g.restore();
}
function bkNewUI(g, sw, sh, t) {
  const u = sw / 400;
  g.scale(u, u);
  const H = sh / u;
  g.fillStyle = C.bg;
  g.fillRect(0, 0, 400, H);
  statusBar(g, 400);
  txt(g, t < B_.done ? 'Restore' : 'Restored', 24, 108, { size: 30, bold: true, color: C.inkD });
  // waiting for the file
  if (t < B_.restore) {
    g.save();
    g.setLineDash([10, 8]);
    g.strokeStyle = C.outlineV;
    g.lineWidth = 3;
    g.stroke(rrP(30, 200, 340, 240, 26));
    g.restore();
    txt(g, 'Choose the backup file', 200, 330, { size: 19, color: C.outline, align: 'center' });
    return;
  }
  // progress
  const p = clamp(seg(t, B_.restore + 0.2, B_.done), 0, 1);
  g.fillStyle = C.surf;
  g.fill(rrP(30, 170, 340, 16, 8));
  g.fillStyle = C.teal;
  g.fill(rrP(30, 170, Math.max(16, 340 * p), 16, 8));
  txt(g, p < 1 ? `Restoring… ${Math.round(p * 100)}%` : 'Done', 30, 214, { size: 16, bold: true, color: C.ink });
  if (t >= B_.done) {
    check(g, 350, 205, 15, t, B_.done, C.green);
    B_ROWS.forEach(([e, label], i) => {
      const s = ease.outCubic(seg(t, B_.rows + i * 0.2, B_.rows + i * 0.2 + 0.45));
      if (s <= 0.002) return;
      const y = 250 + i * 60;
      g.save();
      g.globalAlpha *= s;
      g.translate(200 + (1 - s) * 70, y + 26);
      g.fillStyle = C.surfLow;
      g.fill(rrP(-184, -26, 368, 52, 16));
      emoji(g, e, -154, 0, 24);
      txt(g, label, -124, 7, { size: 18, bold: true, color: C.ink });
      g.fillStyle = C.teal;
      g.fill(circleP(150, 0, 11));
      checkMark(g, 150, 0, 12, '#fff', 0.2);
      g.restore();
    });
    uiChip(g, 16, 520, 'All', {});
    const wAll = uiChip(g, 0, 0, 'All', { measure: true });
    uiChip(g, 16 + wAll + 8, 520, 'Memes · 3', { icon: true, selected: true, scale: pop(t, B_.folders, 1.5, 0.4) });
  }
}
function fileCard(ctx, x, y, s, rot) {
  withT(ctx, x, y, s, rot, g => {
    const body = rrP(-120, -84, 240, 168, 22);
    dieCut(g, body, { border: 10, shadow: 10 });
    g.fillStyle = C.lavPale;
    g.fill(body);
    logo(g, -62, -8, 78, { rot: -0.1, k: 0, shadow: false });
    txt(g, 'peel-it', -12, -22, { size: 30, bold: true, color: C.violetInk });
    txt(g, '.backup', -12, 10, { size: 22, color: C.outline });
    padlock(g, 82, 44, 30);
  });
}
function sBackup(ctx, t) {
  linearBg(ctx, C.violet2, C.violetDeep, 1.0);
  patternFill(ctx, 'dots', '#FFFFFF', 0.07, t, { vx: -18, vy: 12 });
  const oldP = LP({ x: 1000, y: 570, w: 370, h: 780 }, { x: 290, y: 1310, w: 410, h: 850 });
  const newP = LP({ x: 1560, y: 570, w: 370, h: 780 }, { x: 790, y: 1310, w: 410, h: 850 });
  const e1 = spring(t + 0.25, 1.05, 0.42), e2 = pop(t, B_.land - 0.5, 1.1, 0.42);
  const lines = [['Back up', C.pink, C.pinkDD], ['& restore', C.amberL, C.amberD]];
  headline(ctx, lines, t, { t0: B_.title, size: LP(128, 112), y0: LP(280, 240), lead: LP(140, 122) });
  const cy0 = LP(560, 470), cg = LP(112, 92);
  stepChip(ctx, t, B_.step[0], 0, [{ emoji: '🏷️' }, { text: 'Tags, stars & folders' }], { y0: cy0, gap: cg });
  stepChip(ctx, t, B_.step[1], 1, [{ emoji: '🔒' }, { text: 'Optional password' }], { y0: cy0, gap: cg });
  stepChip(ctx, t, B_.step[2], 2, [{ emoji: '📱' }, { text: 'Restore on a new phone' }], { y0: cy0, gap: cg });
  stepChip(ctx, t, B_.step[3], 3, [{ emoji: '🙂' }, { text: "People's names: opt-in" }], { y0: cy0, gap: cg });
  phone(ctx, oldP.x, oldP.y + (1 - e1) * RS.H, oldP.w, oldP.h, (g, sw, sh) => bkOldUI(g, sw, sh, t), { rot: LP(-0.03, -0.02) });
  if (e2 > 0.002) phone(ctx, newP.x, newP.y + (1 - Math.min(1, e2)) * 200, newP.w, newP.h, (g, sw, sh) => bkNewUI(g, sw, sh, t), { rot: LP(0.03, 0.02), });
  // the file flies over
  const p = seg(t, B_.fly, B_.land);
  const from = [oldP.x, oldP.y + oldP.h * 0.28], to = [newP.x, newP.y - newP.h * 0.05];
  if (t >= B_.fly - 0.15 && t < B_.land + 0.4) {
    const e = ease.inOutCubic(p);
    const x = lerp(from[0], to[0], e), y = lerp(from[1], to[1], e) - Math.sin(Math.PI * e) * LP(170, 240);
    const s = t < B_.fly ? pop(t, B_.fly - 0.15, 1.6, 0.4) * 0.7 : p < 1 ? 0.7 : lerp(0.7, 0.2, seg(t, B_.land, B_.land + 0.4));
    const a = t > B_.land ? 1 - seg(t, B_.land + 0.15, B_.land + 0.4) : 1;
    ctx.save();
    ctx.globalAlpha *= a;
    fileCard(ctx, x, y, s * LP(1, 0.9), wobble(t, B_.fly + 0.4, 0.3, 1.4, 1.6));
    ctx.restore();
    if (p > 0 && p < 1) sparkles(ctx, t, B_.fly + p * (B_.land - B_.fly), x, y, { n: 4, radius: 60, size: 20, seed: 4, life: 0.5 });
  }
  sparkles(ctx, t, B_.land, to[0], to[1] + 60, { n: 8, radius: 110, size: 28, seed: 31 });
  ring(ctx, t, B_.land, to[0], to[1] + 60, { r0: 40, r1: 220, width: 16, life: 0.5 });
  sparkles(ctx, t, B_.done, newP.x, newP.y - newP.h * 0.28, { n: 8, radius: 130, size: 28, seed: 32 });
}
read('backup', 'Back up', 2, B_.title + 0.3 + 0.06 * 6);
read('backup', '& restore', 2, B_.title + 0.6 + 0.06 * 15);
B_.step.forEach((s, i) => read('backup', ['Tags, stars & folders', 'Optional password', 'Restore on a new phone', "People's names: opt-in"][i], [4, 2, 5, 3][i], s));

// ================================================================ scene 4: getting started with Pili (12 beats)

const S_ = { title: 0.25, bubble1: 0.5, bubble2: 5.5, tick: [1.5, 2.25, 3.0, 3.75, 4.5, 5.25], done: 6.5 };
const S_ROWS = [['Search for a sticker', '🔎'], ['Send one to a chat', '📤'], ["Open a sticker's details", '🧾'], ['Star a favorite', '⭐'], ['Turn on the keyboard', '⌨️'], ['Try Smart search', '✨']];
function startUI(g, sw, sh, t) {
  const u = sw / 400;
  g.scale(u, u);
  const H = sh / u;
  g.fillStyle = C.bg;
  g.fillRect(0, 0, 400, H);
  uiHeader(g, 'Peel-It');
  const doneN = S_.tick.filter(x => t >= x + 0.2).length;
  const all = t >= S_.done;
  // the card
  g.save();
  g.fillStyle = C.lavBg;
  g.fill(rrP(16, 130, 368, 620, 26));
  const s = all ? 1 + wobble(t, S_.done, 0.04, 2, 6) : 1;
  txt(g, all ? "You're all set! 🎉" : 'Getting started', 36, 178, { size: 26 * s, bold: true, color: C.violetInk });
  txt(g, `${doneN} of 6`, 36, 206, { size: 16, color: C.outline });
  g.fillStyle = '#fff';
  g.fill(rrP(36, 222, 328, 12, 6));
  g.fillStyle = all ? C.green : C.violet;
  g.fill(rrP(36, 222, Math.max(12, 328 * (doneN / 6)), 12, 6));
  S_ROWS.forEach(([label, e], i) => {
    const y = 256 + i * 78;
    const done = t >= S_.tick[i] + 0.2;
    g.fillStyle = '#fff';
    g.fill(rrP(28, y, 344, 66, 18));
    g.strokeStyle = C.outlineV;
    g.lineWidth = 3;
    g.stroke(circleP(64, y + 33, 15));
    check(g, 64, y + 33, 15, t, S_.tick[i], C.teal);
    txt(g, label, 96, y + 40, { size: 18, bold: true, color: done ? C.outline : C.ink });
    if (done) { g.fillStyle = C.outline; g.fillRect(96, y + 34, textWidth(g, label, 18, FONT_B, 0.0) * 0.98, 2); }
    emoji(g, e, 344, y + 33, 24);
  });
  g.restore();
}
function sStart(ctx, t) {
  linearBg(ctx, C.pink, C.pinkD, 1.0);
  patternFill(ctx, 'hearts', '#FFFFFF', 0.12, t, { vx: 20, vy: -12 });
  const P = LP({ x: 1430, y: 560, w: 430, h: 900 }, { x: 540, y: 1330, w: 500, h: 1040 });
  const enter = spring(t + 0.25, 1.05, 0.42);
  const lines = [['Getting', '#fff', C.pinkDD], ['started', C.amberL, C.amberD]];
  headline(ctx, lines, t, { t0: S_.title, size: LP(126, 112), y0: LP(250, 230), lead: LP(140, 122) });
  chip(ctx, LP(110, RS.W / 2), LP(490, 470), [{ text: 'Meet Pili' }, { text: 'פילי', rtl: true, color: C.pinkD }], { size: LP(36, 34), align: LP('left', 'center'), scale: pop(t, 1.0, 1.4, 0.4) });
  phone(ctx, P.x, P.y + (1 - enter) * RS.H * 0.95, P.w, P.h, (g, sw, sh) => startUI(g, sw, sh, t), { rot: LP(0.03, 0.015) * enter });
  // Pili: hops in, blinks, flaps an ear on each tick
  const ex = LP(250, 200), ey = LP(985, 800), es = LP(3.1, 2.5);
  const hop = hopAt(t, 0.25, 90, 0.5);
  const ear = S_.tick.reduce((a, x) => a + (t > x ? Math.sin((t - x) * 14) * 0.2 * Math.exp(-(t - x) * 2.5) : 0), 0);
  const wave = S_.tick.filter(x => t >= x).length;
  dust(ctx, t, 0.75, ex, ey, { spread: 130, size: 30, seed: 41 });
  elephant(ctx, ex, ey + hop[0], es, { sx: hop[1], sy: hop[2], ear, blush: true, blink: Math.floor(t * 2) % 5 === 4 ? 1 : 0, head: Math.sin(t * 3) * 0.04, trunk: -0.15 * Math.sin(wave) });
  // her lines
  // The bubble's left edge sits just right of her trunk; its tail points at her head.
  const size = LP(38, 34), left = ex + 48 * es + LP(28, 60), by = ey - 62 * es, tip = [ex + 30 * es, ey - 50 * es];
  const say = (text, sz, scale) => {
    ctx.save(); ctx.font = fb(sz);
    const w = ctx.measureText(text).width + sz * 1.1;
    ctx.restore();
    bubble(ctx, left + w / 2, by, text, { size: sz, tx: tip[0], ty: tip[1], scale });
  };
  say("Hi, I'm Pili!", size, pop(t, S_.bubble1, 1.5, 0.4) * (1 - seg(t, S_.bubble2 - 0.3, S_.bubble2)));
  say('Long-press a sticker for details', size * 0.92, pop(t, S_.bubble2, 1.5, 0.4));
  if (t >= S_.done) {
    const xf = phoneXf(P.x, P.y, P.w, P.h, LP(0.03, 0.015));
    const [cx, cy] = xf(200, 180);
    confetti(ctx, t, S_.done, cx, cy, { n: 60, seed: 12, spread: 1.6, speed: [700, 1300], size: 15 });
    sparkles(ctx, t, S_.done, cx, cy, { n: 9, radius: 170, size: 30, seed: 13 });
  }
}
read('start', 'Getting started', 2, S_.title + 0.3 + 0.06 * 7);
read('start', 'Meet Pili', 2, 1.0);
read('start', "Hi, I'm Pili!", 3, S_.bubble1, { leave: WN.AT.start + S_.bubble2 - 0.3 });
read('start', 'Long-press a sticker for details', 5, S_.bubble2);
read('start', "You're all set!", 3, S_.done);

// ================================================================ scene 5: end card (6 beats)

const E_ = { logo: 0.25, word: 0.6, chip: 1.5, credit: 2.0 };
function sEnd(ctx, t) {
  linearBg(ctx, C.violet2, C.teal, 0.8);
  patternFill(ctx, 'icons', '#FFFFFF', 0.1, t, { vx: 26, vy: 16 });
  const lx = LP(600, 540), ly = LP(470, 640), ls = LP(420, 470);
  sunburst(ctx, lx, ly, 2600, 20, '#FFFFFF', 0.1 * seg(t, 0, 0.5), t * 0.1);
  const [sqx, sqy] = squash(t, E_.logo, 0.24);
  const k = 20 + 16 * Math.sin(Math.PI * seg(t, 4.0, 4.5)) + wobble(t, 4.5, 4, 2, 5);
  const p = spring(t - E_.logo + 0.3, 1.2, 0.4);
  ring(ctx, t, E_.logo, lx, ly, { r0: ls * 0.5, r1: ls * 1.5, width: 34 });
  burstLines(ctx, t, E_.logo, lx, ly, { n: 14, r0: ls * 0.7, r1: ls * 1.4, width: 16 });
  confetti(ctx, t, E_.logo, lx, ly, { n: 40, seed: 5, spread: 1.6, speed: [600, 1200], size: 14 });
  if (p > 0.002) logo(ctx, lx, ly, ls * p, { rot: rad(-8 + wobble(t, E_.logo, 9, 1.3, 3.4)), k, sx: sqx, sy: sqy });
  const ws = LP(190, 172);
  const wP = textWidth(ctx, 'Peel', ws), wI = textWidth(ctx, '-It', ws), gap = ws * 0.012;
  const wx = LP(870, 540 - (wP + gap + wI) / 2), wy = LP(520, 1140);
  const lp = letterPop(t, E_.word, 0.12, { size: ws, rise: 0.7, spin: 0.35 });
  stext(ctx, 'Peel', wx, wy, { size: ws, fill: C.pink, shade: C.pinkDD, letter: lp });
  stext(ctx, '-It', wx + wP + gap, wy, { size: ws, fill: C.amberL, shade: C.amberD, letter: i => lp(i + 4) });
  chip(ctx, LP(wx + 4, 540), LP(650, 1270), [{ text: 'v0.3.0-alpha' }, { text: 'out now', color: C.pinkD }], { size: LP(44, 42), align: LP('left', 'center'), scale: pop(t, E_.chip, 1.3, 0.42) });
  const a = seg(t, E_.credit, E_.credit + 0.4);
  txt(ctx, CREDIT, LP(wx + 6, 540), LP(745, 1370), { size: LP(30, 30), color: '#fff', align: LP('left', 'center'), alpha: a });
  txt(ctx, REPO_URL, LP(wx + 6, 540), LP(790, 1416), { size: LP(24, 24), color: 'rgba(255,255,255,.85)', align: LP('left', 'center'), alpha: a });
  sparkles(ctx, t, E_.chip, LP(1500, 800), LP(300, 1000), { n: 8, radius: 150, size: 28, seed: 61 });
}
read('end', 'Peel-It', 1, E_.word + 0.12 * 6);
read('end', 'v0.3.0-alpha out now', 2, E_.chip);

// ================================================================ composer

const WN_SCENES = { intro: sIntro, folders: sFolders, backup: sBackup, start: sStart, end: sEnd };
const WN_ORDER = ['intro', 'folders', 'backup', 'start', 'end'];
const WN_TRANSITIONS = [
  { a: WN.LEAVE.intro, b: WN.AT.folders + 0.25, from: 'intro', to: 'folders', kind: 'peel', corner: 'br' },
  { a: WN.LEAVE.folders, b: WN.AT.backup + 0.25, from: 'folders', to: 'backup', kind: 'whip' },
  { a: WN.LEAVE.backup, b: WN.AT.start + 0.375, from: 'backup', to: 'start', kind: 'peel', corner: 'tl' },
  { a: WN.LEAVE.start, b: WN.AT.end + 0.25, from: 'start', to: 'end', kind: 'iris' },
];
const WN_IMPACTS = [[0.25, 18], [WN.AT.folders + F_.chip, 5], [WN.AT.folders + F_.send + 0.4, 6], [WN.AT.backup + B_.land, 9], [WN.AT.backup + B_.done, 6], [WN.AT.start + S_.done, 11], [WN.AT.end + E_.logo, 13]];
function wnShake(tb) {
  let x = 0, y = 0, r = 0;
  for (const [t0, amp] of WN_IMPACTS) {
    const d = tb - t0;
    if (d < 0 || d > 1.2) continue;
    const e = amp * Math.exp(-d * 6);
    x += e * noise1(t0 * 13 + d * 40);
    y += e * noise1(t0 * 7 + d * 40 + 50);
    r += e * 0.0008 * noise1(t0 * 3 + d * 30 + 99);
  }
  return [x, y, r];
}
function wnSceneAt(tb) {
  let name = WN_ORDER[0];
  for (const n of WN_ORDER) if (tb >= WN.AT[n]) name = n;
  return name;
}

// Replaces the showreel's drawFrame (scenes.js) on this page.
function drawFrame(ctx, tb) {
  ctx.save();
  const [sx, sy, sr] = wnShake(tb);
  ctx.translate(RS.W / 2 + sx, RS.H / 2 + sy);
  ctx.rotate(sr);
  ctx.translate(-RS.W / 2, -RS.H / 2);
  const tr = WN_TRANSITIONS.find(T => tb >= T.a && tb < T.b);
  if (tr) {
    const A = wnScene(tr.from), B = wnScene(tr.to);
    const draw = fn => g => fn(g, tb);
    if (tr.kind === 'peel') tPeel(ctx, tb, tr.a, tr.b, draw(A), draw(B), tr.corner, BUFS[0]);
    else if (tr.kind === 'whip') tWhip(ctx, tb, tr.a, tr.b, draw(A), draw(B), BUFS[0], BUFS[1]);
    else if (tr.kind === 'iris') tIris(ctx, tb, tr.a, tr.b, draw(A), draw(B), LP(330, 250), LP(830, 720));
  } else wnScene(wnSceneAt(tb))(ctx, tb);
  ctx.restore();
}
// The reads, for render.js --check.
function readReport() {
  return READS.map(r => ({ ...r, seconds: (r.to - r.from) * BEAT, need: needSeconds(r.words) }));
}
