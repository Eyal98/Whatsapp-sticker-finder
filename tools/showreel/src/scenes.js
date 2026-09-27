// Peel-It showreel: the storyboard. 88 beats at 144 BPM = 36.7 s.
//
// Every scene draws itself as a pure function of its own clock `t`: beats since the scene starts
// (AT below). Scenes start on bar lines, so re-timing the reel only means editing AT, the
// transitions and the soundtrack's arrangement. Each scene holds for about two seconds after its
// last element lands, so there is time to read it.
'use strict';

// Scene start times, in beats.
const AT = { logo: 0, many: 8, search: 16, sees: 28, people: 36, chat: 44, privacy: 52, hood: 60, finale: 76 };

const rad = d => (d * Math.PI) / 180;
const CREDIT = 'Designed & built by Eyal98';
const REPO_URL = 'github.com/Eyal98/Whatsapp-sticker-finder';

function fmtInt(v) {
  return String(v).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}
function chipWidth(ctx, parts, size) {
  ctx.save();
  const w = parts.reduce((acc, p) => {
    if (p.emoji) return acc + size * 1.18;
    ctx.font = p.bold === false ? fr(size) : fb(size);
    return acc + ctx.measureText(p.text).width;
  }, 0);
  ctx.restore();
  return w + size * 0.62 * 2 + size * 0.34 * (parts.length - 1);
}
// World position of a point given in a logo's 108-unit space.
function logoPoint(lx, ly, size, rot, ux, uy) {
  const s = size / 64, c = Math.cos(rot), sn = Math.sin(rot);
  const x = (ux - 54) * s, y = (uy - 55) * s;
  return [lx + x * c - y * sn, ly + x * sn + y * c];
}
// Maps UI units (400 wide) inside a phone drawn by phone() to world coordinates.
function phoneXf(cx, cy, w, h, rot) {
  const bz = w * 0.032, u = (w - bz * 2) / 400, c = Math.cos(rot), s = Math.sin(rot);
  const f = (ux, uy) => {
    const lx = -w / 2 + bz + ux * u, ly = -h / 2 + bz + uy * u;
    return [cx + lx * c - ly * s, cy + lx * s + ly * c];
  };
  f.u = u;
  return f;
}
// Speech bubble with a tail pointing at (tx, ty).
function bubble(ctx, x, y, text, { size = 70, tx, ty, scale = 1, rot = 0, fill = '#fff', color = C.ink } = {}) {
  if (scale <= 0.002) return;
  ctx.save();
  ctx.font = fb(size);
  const w = ctx.measureText(text).width + size * 1.1, h = size * 1.75;
  ctx.translate(x, y);
  ctx.rotate(rot);
  ctx.scale(scale, scale);
  const body = rrP(-w / 2, -h / 2, w, h, h * 0.42);
  const ang = Math.atan2(ty - y, tx - x);
  const tail = polyP([[Math.cos(ang + 1.6) * h * 0.22, Math.sin(ang + 1.6) * h * 0.22], [Math.cos(ang) * (w * 0.2 + h * 0.75), Math.sin(ang) * (h * 0.95)], [Math.cos(ang - 1.6) * h * 0.22, Math.sin(ang - 1.6) * h * 0.22]]);
  dieCut(ctx, [body, tail], { border: size * 0.14, shadow: size * 0.16, shadowColor: 'rgba(24,14,70,.3)' });
  ctx.fillStyle = fill;
  ctx.fill(body);
  ctx.fill(tail);
  ctx.fillStyle = color;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, 0, size * 0.06);
  ctx.restore();
}
// A small hop on beat `t0`: [dy, sx, sy] offsets for something that jumps in place.
function hopAt(t, t0, h = 20, len = 0.4) {
  const p = (t - t0) / len;
  if (p <= 0 || p >= 1) return [0, 1, 1];
  const air = Math.sin(Math.PI * p);
  return [-air * h, 1 - 0.06 * air, 1 + 0.08 * air];
}

// ================================================================ S1: logo sting (8 beats)

function sLogo(ctx, t) {
  radialBg(ctx, '#7D6FF3', C.violet, RS.W * 0.42, RS.H * 0.45);
  patternFill(ctx, 'icons', '#FFFFFF', 0.08, t, { vx: -26, vy: -14 });
  const lx = LP(580, 540), ly = LP(505, 760), ls = LP(470, 540);
  sunburst(ctx, lx, ly, 2400, 18, '#FFFFFF', 0.07 * seg(t, 0.25, 0.9), t * 0.12);
  const LAND = 0.25;
  let s = 1, rot = -8;
  if (t < LAND) {
    const p = ease.inCubic(seg(t, -0.02, LAND));
    s = lerp(2.9, 1, p);
    rot = lerp(-34, -8, p);
  } else rot = -8 + wobble(t, LAND, 10, 1.3, 3.4);
  let [sqx, sqy] = t >= LAND ? squash(t, LAND, 0.22) : [1, 1];
  const [bx, by] = squash(t, 4.0, 0.1);
  sqx *= bx; sqy *= by;
  // The corner peels up on beat 1 and snaps back, and teases again before the page peels away.
  const k = 20 + 22 * Math.sin(Math.PI * ease.outQuad(seg(t, 1.0, 1.5))) + wobble(t, 1.5, 5, 2, 5)
    + 18 * Math.sin(Math.PI * seg(t, 6.0, 6.5)) + wobble(t, 6.5, 4, 2, 5);
  const fy = Math.sin((t - 1.2) * 2.2) * 7 * seg(t, 1.2, 2.0);
  ring(ctx, t, LAND, lx, ly, { r0: ls * 0.45, r1: ls * 1.3, width: 32 });
  burstLines(ctx, t, LAND, lx, ly, { n: 12, r0: ls * 0.6, r1: ls * 1.15, width: 16, rot: 0.2 });
  if (t < LAND) {
    ctx.save();
    ctx.globalAlpha = 0.3;
    logo(ctx, lx, ly, ls * s * 1.15, { rot: rad(rot - 6) });
    ctx.restore();
  }
  logo(ctx, lx, ly + fy, ls * s, { rot: rad(rot), k, sx: sqx, sy: sqy });
  const corner = logoPoint(lx, ly + fy, ls, rad(rot), 80, 80);
  sparkles(ctx, t, 1.05, corner[0], corner[1], { n: 9, radius: 130, size: 34, seed: 11 });
  sparkles(ctx, t, 6.05, corner[0], corner[1], { n: 7, radius: 110, size: 30, seed: 12 });
  // Wordmark: letters on 32nd notes, then the tagline.
  const ws = LP(205, 190);
  const wP = textWidth(ctx, 'Peel', ws), wI = textWidth(ctx, '-It', ws), gap = ws * 0.012;
  const wx = LP(875, 540 - (wP + gap + wI) / 2), wy = LP(585, 1255);
  const lp = letterPop(t, 1.5, 0.125, { size: ws, rise: 0.7, spin: 0.35 });
  stext(ctx, 'Peel', wx, wy, { size: ws, fill: C.pink, shade: C.pinkDD, letter: lp });
  stext(ctx, '-It', wx + wP + gap, wy, { size: ws, fill: C.amberL, shade: C.amberD, letter: i => lp(i + 4) });
  chip(ctx, LP(wx + 4, 540), LP(722, 1395), [{ text: 'Find the right sticker. Fast.' }], { size: LP(44, 44), align: LP('left', 'center'), scale: pop(t, 2.5, 1.3, 0.42) * (1 + wobble(t, 4.0, 0.05, 2, 6)) });
  sparkles(ctx, t, 2.25, LP(wx + wP + wI * 0.7, 540), wy - ws * 0.8, { n: 6, radius: 90, size: 26, seed: 21 });
  sparkles(ctx, t, 4.0, LP(wx + (wP + wI) / 2, 540), wy - ws * 0.5, { n: 8, radius: 260, size: 28, seed: 22, color: C.amberL });
}

// ================================================================ S2: 10,000 stickers (8 beats)

const RAIN_EMOJI = ['😂', '😍', '😭', '🥳', '😎', '🤔', '😴', '🙈', '🐱', '🐶', '🎂', '👍', '❤️', '🔥', '🍕', '😅', '🤯', '🥺', '😡', '🤩', '👀', '💃', '🐸', '🦄', '🙏', '💯', '😘', '🤣'];
const RAIN_WORDS = [
  ['LOL', C.amberL, C.amberD], ['חחח', C.teal, C.tealDD, true], ['OK!', C.sky, C.skyD], ['מאחר', C.pinkD, C.pinkDD, true],
  ['WOW', C.violet, C.violetInk], ['סבבה', C.orange, C.orangeD, true], ['nope', C.red, C.redD], ['יאללה', C.green, C.greenD, true],
  ['OMG', C.pink, C.pinkDD], ['BRB', C.tealL, C.tealDD],
];
const RAIN_N = 40;
// Landing beat of the i-th sticker: the pile grows while the counter runs, and a few stragglers
// keep dropping in while "Which one?" is on screen.
const RAIN_LATE = [5.0, 5.625, 6.25, 6.75];
const rainLand = i => (i < RAIN_N ? 0.15 + (i / RAIN_N) * 3.1 : RAIN_LATE[i - RAIN_N]);
const rainCache = {};
function rainItems() {
  const key = `${RS.W}x${RS.H}`;
  if (rainCache[key]) return rainCache[key];
  const R = rng(20240926);
  const cols = LP(8, 5), rows = LP(5, 8);
  const cw = RS.W / cols, rh = RS.H / rows;
  const cells = [];
  for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++) cells.push([c, r]);
  const deck = RAIN_EMOJI.slice().sort(() => R() - 0.5);
  let ei = 0, wi = 0;
  const make = (x, y) => {
    const word = R() < 0.26;
    const w = RAIN_WORDS[wi++ % RAIN_WORDS.length];
    const e = deck[ei++ % deck.length];
    return {
      x, y,
      rot: (R() - 0.5) * 0.8,
      scale: 0.95 + R() * 0.3,
      order: R(),
      sprite: word ? () => wordSticker(w[0], 58, w[1], w[2], { rtl: !!w[3] }) : () => emojiSticker(e, 104),
    };
  };
  const items = cells.map(([c, r]) => make((c + 0.5 + (R() - 0.5) * 0.55) * cw, (r + 0.5 + (R() - 0.5) * 0.5) * rh));
  items.sort((a, b) => a.order - b.order);
  // Stragglers land near the edges, clear of the counter and the bubble.
  const late = LP([[190, 170], [1740, 250], [300, 930], [1760, 640]], [[150, 330], [950, 300], [150, 1250], [930, 1250]]);
  late.forEach(([x, y]) => items.push(make(x, y)));
  items.forEach((it, i) => (it.t = rainLand(i)));
  rainCache[key] = items;
  return items;
}
const WHICH = () => ({ x: LP(1180, 540), y: LP(810, 1400) });

function sMany(ctx, t) {
  radialBg(ctx, '#FF8FBD', '#FF5A98');
  patternFill(ctx, 'dots', '#FFFFFF', 0.16, t, { angle: 0.4, vx: 22, vy: -30 });
  for (const it of rainItems()) {
    const d = t - it.t;
    if (d < -0.34) continue;
    let y = it.y, rot = it.rot, sx = 1, sy = 1;
    if (d < 0) {
      const p = 1 + d / 0.34;
      y = lerp(-260, it.y, ease.inQuad(p));
      rot = it.rot - 0.7 * (1 - p);
      sx = 0.9; sy = 1.12;
    } else [sx, sy] = squash(t, it.t, 0.26);
    drawSprite(ctx, it.sprite(), it.x, y, it.scale, rot, 1, sx, sy);
  }
  // Push the pile back behind the counter.
  const cx = RS.W / 2, cy = LP(505, 840);
  const halo = ctx.createRadialGradient(cx, cy - 40, 0, cx, cy - 40, LP(700, 560));
  halo.addColorStop(0, 'rgba(255,90,152,.93)');
  halo.addColorStop(0.55, 'rgba(255,90,152,.7)');
  halo.addColorStop(1, 'rgba(255,90,152,0)');
  ctx.fillStyle = halo;
  ctx.fillRect(0, 0, RS.W, RS.H);
  // Counter: 0 -> 10,000 on 16th-note ticks, then a slam.
  const SLAM = 3.25;
  const v = Math.round(10000 * ease.outCubic(seg(t, 0.25, SLAM)) / 7) * 7;
  const str = t >= SLAM ? '10,000' : fmtInt(Math.min(9999, v));
  const phase = ((t - 0.25) % 0.25 + 0.25) % 0.25;
  const pulse = t < SLAM ? 1 + 0.06 * Math.exp(-14 * phase) : 1 + wobble(t, SLAM, 0.22, 1.4, 4);
  const appear = pop(t, 0.2, 1.25, 0.42);
  const ns = LP(250, 205);
  burstLines(ctx, t, SLAM, cx, cy - ns * 0.35, { n: 14, r0: ns * 1.6, r1: ns * 2.6, width: 14, color: '#FFF3A0' });
  stext(ctx, str, cx, cy, { size: ns, fill: C.ink, shade: C.inkDD, align: 'center', tracking: 0.02, letter: () => ({ s: appear * pulse }) });
  const ls = LP(112, 100);
  stext(ctx, 'stickers.', cx, cy + LP(150, 135), { size: ls, fill: '#fff', shade: '#FFC2DC', outlineColor: C.ink, outline: 0.17, align: 'center', letter: letterPop(t, SLAM + 0.125, 0.05, { size: ls }) });
  sparkles(ctx, t, SLAM + 0.05, cx, cy - ns * 0.4, { n: 10, radius: ns * 1.9, size: 36, seed: 31, color: '#FFF3A0' });
  // "Which one?": the elephant peeks up and looks both ways.
  const ex = LP(1575, 540 + 250), es = LP(4.4, 4.6);
  const rise = spring(t - 4.0, 1.1, 0.45);
  const ey = RS.H + LP(70, 60) + (1 - rise) * 520;
  const turn = t < 5.5 ? Math.cos(Math.PI * seg(t, 4.75, 4.9)) : -Math.cos(Math.PI * seg(t, 5.5, 5.65));
  if (rise > 0.01) elephant(ctx, ex, ey, es, { sx: turn, ear: Math.sin(t * 7) * 0.12, head: Math.sin(t * 4) * 0.05, shadow: false, blink: (t > 4.9 && t < 4.99) || (t > 6.6 && t < 6.7) ? 1 : 0 });
  const W = WHICH();
  bubble(ctx, W.x, W.y, 'Which one?', { size: LP(70, 66), tx: ex - LP(40, 0), ty: ey - es * 60, scale: pop(t, 4.25, 1.3, 0.34) * (1 + wobble(t, 6.0, 0.05, 2, 6)), rot: -0.04 + wobble(t, 4.25, 0.06, 2, 4) });
}

// ================================================================ S3: search by meaning (12 beats)

const QUERY = 'running late';
const typedAt = i => 1.0 + i * 0.25; // i-th character (0-based)
const typedCount = t => Math.max(0, Math.min(QUERY.length, Math.floor((t - 1.0) / 0.25) + 1));
const RESULTS_PARTIAL = 1.75, RESULTS_FINAL = 4.0, HERO_LIFT = 9.0;
const PARTIAL = [{ e: '🏃' }, { w: ['RUN!', C.red, C.redD] }, { e: '👟' }, { e: '💨' }];
const FINAL = [{ hero: true }, { e: '⏰' }, { w: ['LATE!', C.red, C.redD] }, { e: '😅' }, { e: '🏃‍♂️' }, { w: ['sorry!!', C.violet, C.violetInk] },
  { e: '🚌' }, { e: '🐢' }, { e: '⌛' }, { w: ['on my way', C.teal, C.tealDD] }, { e: '🚗' }, { e: '😬' }, { e: '🏃‍♀️' }, { w: ['soon!', C.orange, C.orangeD] }, { e: '🕐' }];
const HERO_TILE = [16 + 58, 198 + 58];

function searchUI(g, sw, sh, t) {
  const u = sw / 400;
  g.scale(u, u);
  const H = sh / u;
  g.fillStyle = C.bg;
  g.fillRect(0, 0, 400, H);
  statusBar(g, 400);
  logo(g, 42, 86, 36, { rot: -0.14, shadow: false });
  txt(g, 'Peel-It', 72, 96, { size: 25, bold: true, color: C.inkD });
  g.fillStyle = C.lavBg;
  g.fill(circleP(318, 88, 19));
  g.fill(circleP(362, 88, 19));
  g.fillStyle = C.violet;
  g.fill(sparkleP(318, 88, 10));
  g.fill(rrP(351, 81, 22, 14, 3));
  // search field
  const n = typedCount(t);
  const focus = seg(t, 0.8, 1.0);
  g.fillStyle = C.surf;
  g.fill(rrP(16, 116, 368, 60, 30));
  if (focus > 0) { g.strokeStyle = C.violet; g.globalAlpha = focus; g.lineWidth = 3; g.stroke(rrP(16, 116, 368, 60, 30)); g.globalAlpha = 1; }
  magnifier(g, 47, 146, 26, n ? C.violet : C.outline);
  if (n === 0) txt(g, 'Search stickers (עברית / English)', 70, 153, { size: 17, color: C.outline });
  else {
    const s = QUERY.slice(0, n);
    txt(g, s, 70, 155, { size: 23, bold: true, color: C.inkD });
    g.font = fb(23);
    const cw = g.measureText(s).width;
    const lastT = typedAt(n - 1);
    // the newest letter pops
    if (t - lastT < 0.2) {
      g.save();
      g.globalAlpha = 1 - (t - lastT) / 0.2;
      g.fillStyle = C.lav;
      g.fill(rrP(70 + cw - 16, 132, 18, 30, 5));
      g.restore();
    }
    if (Math.floor(t * 2) % 2 === 0 || t - lastT < 0.5) { g.fillStyle = C.violet; g.fillRect(72 + cw, 133, 3, 28); }
  }
  // results
  txt(g, n >= 12 && t > RESULTS_FINAL ? 'Best matches' : n >= 3 ? 'Matches' : 'Recent', 18, 195, { size: 14, bold: true, color: C.outline });
  const tile = 116, gapT = 10, x0 = 16, y0 = 206;
  const final = t >= RESULTS_FINAL, partial = !final && n >= 3 && t >= RESULTS_PARTIAL;
  const list = final ? FINAL : partial ? PARTIAL : [];
  const t0 = final ? RESULTS_FINAL : RESULTS_PARTIAL;
  for (let i = 0; i < 15; i++) {
    const cx = x0 + (i % 3) * (tile + gapT) + tile / 2, cy = y0 + Math.floor(i / 3) * (tile + gapT) + tile / 2;
    g.fillStyle = C.surfLow;
    g.fill(rrP(cx - tile / 2, cy - tile / 2, tile, tile, 20));
    const it = list[i];
    if (!it) continue;
    const s = pop(t, t0 + i * 0.05, 1.5, 0.4);
    if (it.hero) {
      // It wiggles on the two beats before it peels off the screen.
      if (t < HERO_LIFT) catSticker(g, cx, cy + 2, 0.36 * s * (1 + wobble(t, 8.0, 0.08, 2, 6) + wobble(t, 8.5, 0.08, 2, 6)), { look: 1, rot: 0.08 * (wobble(t, 8.0, 1, 2, 6) - wobble(t, 8.5, 1, 2, 6)) });
      // #1 highlight
      const hl = seg(t, 4.95, 5.1);
      if (hl > 0) {
        g.save();
        g.strokeStyle = C.teal;
        g.lineWidth = 5 + 2 * Math.sin(t * 10);
        g.globalAlpha = hl;
        g.stroke(rrP(cx - tile / 2 - 3, cy - tile / 2 - 3, tile + 6, tile + 6, 22));
        g.restore();
        const bs = pop(t, 5.0, 1.6, 0.35);
        if (bs > 0) {
          g.save();
          g.translate(cx + tile / 2 - 8, cy - tile / 2 + 8);
          g.scale(bs, bs);
          g.fillStyle = C.pinkD;
          g.fill(circleP(0, 0, 17));
          txt(g, '1', 0, 7, { size: 20, bold: true, color: '#fff', align: 'center' });
          g.restore();
        }
      }
    } else if (it.e) drawSprite(g, emojiSticker(it.e, 104), cx, cy, 0.72 * s, (i % 2 ? 0.06 : -0.06));
    else {
      const sp = wordSticker(it.w[0], 58, it.w[1], it.w[2]);
      drawSprite(g, sp, cx, cy, Math.min(0.62, 150 / sp.w) * s, -0.08);
    }
  }
}

function sSearch(ctx, t) {
  linearBg(ctx, '#1CD6A9', C.tealD, 1.1);
  patternFill(ctx, 'search', '#FFFFFF', 0.12, t, { vx: 24, vy: -16 });
  const P = LP({ x: 1330, y: 560, w: 430, h: 900 }, { x: 540, y: 1215, w: 500, h: 1040 });
  const enter = spring(t + 0.25, 1.05, 0.42);
  const py = P.y + (1 - enter) * RS.H * 0.95;
  const prot = LP(0.035, 0.018) - (1 - enter) * 0.14;
  // headline
  const hs = LP(128, 112);
  const hx = LP(110, RS.W / 2), ha = LP('left', 'center');
  const y1 = LP(348, 232), y2 = LP(492, 362);
  stext(ctx, 'Search by', hx, y1, { size: hs, fill: C.ink, shade: C.inkDD, align: ha, letter: letterPop(t, 0.05, 0.055, { size: hs }) });
  stext(ctx, 'meaning', hx, y2, { size: hs, fill: C.pink, shade: C.pinkDD, align: ha, letter: letterPop(t, 0.5, 0.055, { size: hs }) });
  // "running late" ≈ "מאחר"
  const cs = LP(38, 36), cy = LP(640, 492);
  const a = [{ emoji: '🔎' }, { text: 'running late' }];
  const b = [{ text: 'מאחר', rtl: true, color: C.pinkD }];
  const wa = chipWidth(ctx, a, cs), wb = chipWidth(ctx, b, cs), eq = cs * 2.3;
  const rowW = wa + eq + wb;
  const ax = LP(110, RS.W / 2 - rowW / 2);
  chip(ctx, ax, cy, a, { size: cs, scale: pop(t, 4.25, 1.4, 0.4) });
  const es = pop(t, 4.5, 1.6, 0.35) * (1 + wobble(t, 6.0, 0.18, 2, 5) + wobble(t, 8.0, 0.18, 2, 5));
  if (es > 0) {
    withT(ctx, ax + wa + eq / 2, cy, es, 0, g => {
      dieCut(g, circleP(0, 0, cs * 0.72), { border: cs * 0.14, shadow: cs * 0.16 });
      g.fillStyle = C.ink;
      g.fill(circleP(0, 0, cs * 0.72));
      txt(g, '≈', 0, cs * 0.36, { size: cs * 1.1, bold: true, color: '#fff', align: 'center' });
    });
  }
  chip(ctx, ax + wa + eq, cy, b, { size: cs, scale: pop(t, 4.75, 1.4, 0.4) });
  chip(ctx, LP(110, RS.W / 2), cy + LP(110, 100), [{ text: 'Hebrew' }, { text: '⇄', color: C.pinkD }, { text: 'English' }, { text: '· same meaning', bold: false, color: C.outline }], { size: LP(30, 28), align: LP('left', 'center'), scale: pop(t, 5.25, 1.4, 0.42) });
  phone(ctx, P.x, py, P.w, P.h, (g, sw, sh) => searchUI(g, sw, sh, t), { rot: prot });
  // The top result peels off the phone, floats beside it, then flies at the camera.
  if (t >= HERO_LIFT) {
    const xf = phoneXf(P.x, py, P.w, P.h, prot);
    const p0 = xf(HERO_TILE[0], HERO_TILE[1] + 2);
    const s0 = 0.36 * xf.u;
    const p1 = LP([925, 830], [540, 1130]);
    const s1 = LP(1.35, 1.45);
    const lift = ease.outBack(seg(t, HERO_LIFT, HERO_LIFT + 0.5), 2.2);
    const fly = spring(t - HERO_LIFT - 0.375, 0.9, 0.5);
    let x = lerp(p0[0], p1[0], fly), y = lerp(p0[1] - 50 * lift, p1[1], fly) + Math.sin((t - HERO_LIFT) * 2.4) * 8 * seg(t, 10, 10.5);
    let s = lerp(s0 * (1 + 0.3 * lift), s1, fly);
    let r = -0.14 * lift + wobble(t, HERO_LIFT + 0.375, 0.1, 1.2, 3);
    const zoom = ease.inExpo(seg(t, 11.2, 11.95));
    s *= 1 + zoom * 14;
    x = lerp(x, RS.W / 2, zoom);
    y = lerp(y, RS.H / 2 + s * 30, zoom);
    r *= 1 - zoom;
    sparkles(ctx, t, HERO_LIFT + 0.5, p1[0], p1[1], { n: 10, radius: 200, size: 34, seed: 41 });
    catSticker(ctx, x, y, s, { rot: r, look: Math.sin(t * 3), shake: Math.sin(t * TAU * 3) * 0.08 });
    const flash = ease.inQuad(seg(t, 11.5, 11.95));
    if (flash > 0) { ctx.save(); ctx.globalAlpha = flash; bleedRect(ctx, '#FFFFFF'); ctx.restore(); }
  }
}

// ================================================================ S4: sees the picture, reads the text (8 beats)

// Ordered top to bottom by the feature they point at, so the leader lines never cross.
const SEES_TAGS = [
  { t: 1.0, parts: [{ emoji: '😰' }, { text: 'stressed' }, { text: '·', color: C.outline }, { text: 'לחוץ', rtl: true, color: C.violet }], at: CAT.sweat },
  { t: 1.25, parts: [{ emoji: '🐱' }, { text: 'cat' }, { text: '·', color: C.outline }, { text: 'חתול', rtl: true, color: C.violet }], at: CAT.face },
  { t: 1.5, parts: [{ emoji: '⏰' }, { text: 'alarm clock' }, { text: '·', color: C.outline }, { text: 'שעון', rtl: true, color: C.violet }], at: CAT.clock },
];
const OCR_AT = 2.0;
function sSees(ctx, t) {
  linearBg(ctx, '#FFE39A', '#F8B425', 1.2);
  patternFill(ctx, 'stripes', '#FFFFFF', 0.15, t, { angle: -0.6, vx: 60, vy: 0 });
  const K = LP({ x: 500, y: 585, s: 2.35 }, { x: 540, y: 800, s: 2.4 });
  const ent = spring(t, 1.25, 0.4);
  const s = K.s * lerp(1.4, 1, ent) * (1 + wobble(t, 3.0, 0.03, 2, 5) + wobble(t, 5.0, 0.03, 2, 5));
  const rot = lerp(0.14, -0.04, ent);
  const scanOn = seg(t, 0.35, 0.55) * (1 - seg(t, 2.1, 2.4));
  // scanner frame
  const bx = K.x + CAT.box[0] * K.s, by = K.y + CAT.box[1] * K.s, bw = CAT.box[2] * K.s, bh = CAT.box[3] * K.s;
  if (scanOn > 0) {
    ctx.save();
    ctx.globalAlpha = scanOn;
    ctx.strokeStyle = C.ink;
    ctx.lineWidth = 10;
    ctx.lineCap = 'round';
    const L = 60;
    for (const [cx, cy, dx, dy] of [[bx, by, 1, 1], [bx + bw, by, -1, 1], [bx, by + bh, 1, -1], [bx + bw, by + bh, -1, -1]]) {
      ctx.beginPath();
      ctx.moveTo(cx, cy + dy * L); ctx.lineTo(cx, cy); ctx.lineTo(cx + dx * L, cy);
      ctx.stroke();
    }
    ctx.restore();
  }
  const blink = (t > 2.6 && t < 2.72) || (t > 5.4 && t < 5.52) ? 1 : 0;
  catSticker(ctx, K.x, K.y, s, { rot, look: t < 1 ? 1 : Math.cos((t - 1) * 2), shake: Math.sin(t * TAU * 3) * 0.07, blink });
  // scan beam
  const sp = seg(t, 0.5, 1.9);
  if (sp > 0 && sp < 1) {
    const yb = lerp(by + 10, by + bh - 10, ease.inOutSine(sp));
    ctx.save();
    ctx.beginPath();
    ctx.rect(bx - 30, by - 10, bw + 60, bh + 20);
    ctx.clip();
    const tr = ctx.createLinearGradient(0, yb - 160, 0, yb);
    tr.addColorStop(0, 'rgba(92,219,190,0)');
    tr.addColorStop(1, 'rgba(92,219,190,.45)');
    ctx.fillStyle = tr;
    ctx.fillRect(bx - 30, yb - 160, bw + 60, 160);
    ctx.fillStyle = '#E9FFF8';
    ctx.fillRect(bx - 30, yb - 4, bw + 60, 8);
    ctx.fillStyle = C.teal;
    ctx.fillRect(bx - 30, yb + 4, bw + 60, 4);
    ctx.restore();
    chip(ctx, bx + bw + 10, yb, [{ text: 'SigLIP 2' }], { size: 22, fill: C.ink, color: '#fff', border: 5, shadow: 4, align: 'left' });
  }
  // headline
  const hs = LP(88, 84), hx = LP(900, RS.W / 2), ha = LP('left', 'center');
  stext(ctx, 'Sees the picture.', hx, LP(235, 190), { size: hs, fill: C.ink, shade: C.inkDD, align: ha, letter: letterPop(t, 0.3, 0.04, { size: hs }) });
  stext(ctx, 'Reads the text.', hx, LP(355, 310), { size: hs, fill: C.violet, shade: C.violetInk, align: ha, letter: letterPop(t, OCR_AT, 0.04, { size: hs }) });
  // picture tags with leader lines
  const cs = LP(40, 40);
  const chipAt = i => LP([900, 510 + i * 112], [RS.W / 2, 1290 + i * 112]);
  SEES_TAGS.forEach((T, i) => {
    const sc = pop(t, T.t, 1.4, 0.4) * (1 + wobble(t, 4.0 + i * 0.125, 0.06, 2, 6));
    if (sc <= 0) return;
    const [cx, cy] = chipAt(i);
    const [fx, fy] = catPoint(K.x, K.y, s, rot, T.at[0], T.at[1]);
    const ex = LP(cx - 18, cx), ey = LP(cy, cy - cs);
    const lp = ease.outCubic(seg(t, T.t, T.t + 0.35));
    ctx.save();
    ctx.strokeStyle = C.ink;
    ctx.lineWidth = 4;
    ctx.setLineDash([2, 12]);
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(fx, fy);
    ctx.lineTo(lerp(fx, ex, lp), lerp(fy, ey, lp));
    ctx.stroke();
    ctx.restore();
    ctx.fillStyle = C.ink;
    ctx.fill(circleP(fx, fy, 9 * Math.min(1, sc)));
    ctx.fillStyle = '#fff';
    ctx.fill(circleP(fx, fy, 4 * Math.min(1, sc)));
    chip(ctx, cx, cy, T.parts, { size: cs, align: LP('left', 'center'), scale: sc });
  });
  // printed text: box + chip
  const ob = seg(t, OCR_AT, OCR_AT + 0.12);
  if (ob > 0) {
    const [tx, ty] = catPoint(K.x, K.y, s, rot, CAT.text[0], CAT.text[1]);
    const bw2 = 170 * s, bh2 = 64 * s, bs = lerp(1.4, 1, ease.outBack(ob));
    ctx.save();
    ctx.translate(tx, ty);
    ctx.rotate(rot);
    ctx.scale(bs, bs);
    ctx.strokeStyle = C.pinkD;
    ctx.lineWidth = 7;
    ctx.setLineDash([22, 12]);
    ctx.lineDashOffset = -t * 60;
    ctx.stroke(rrP(-bw2 / 2, -bh2 / 2, bw2, bh2, 16));
    ctx.restore();
    const [cx, cy] = LP([900, 510 + 3 * 112 + 34], [RS.W / 2, 1290 + 3 * 112 + 30]);
    const sc = pop(t, OCR_AT + 0.25, 1.4, 0.4) * (1 + wobble(t, 4.5, 0.06, 2, 6));
    chip(ctx, cx, cy, [{ text: 'Aa', color: C.pinkD }, { text: 'printed text:', bold: false, color: C.outline }, { text: 'מאחר', rtl: true, color: C.pinkD }, { text: '= late' }], { size: cs, align: LP('left', 'center'), scale: sc });
    sparkles(ctx, t, OCR_AT + 0.25, tx, ty, { n: 7, radius: 150, size: 30, seed: 51 });
  }
  const flash = 1 - seg(t, 0, 0.35);
  if (flash > 0) { ctx.save(); ctx.globalAlpha = flash; bleedRect(ctx, '#FFFFFF'); ctx.restore(); }
}

// ================================================================ S5: People (8 beats)

const FACES = [
  { who: 'eyal', expr: 'laugh' }, { who: 'noa', expr: 'wink' }, { who: 'eyal', expr: 'cool' },
  { who: 'noa', expr: 'love' }, { who: 'eyal', expr: 'shock' }, { who: 'noa', expr: 'crylaugh' },
];
const PEOPLE_T = { group: 2.0, names: 2.5, search: 4.0, found: 4.25 };
function peopleLayout() {
  if (RS.land) {
    return {
      r: 112,
      scatter: [[330, 560], [650, 820], [960, 540], [1270, 820], [1590, 560], [960, 900]],
      cluster: [[440, 560], [1270, 560], [690, 610], [1520, 610], [555, 820], [1395, 820]],
      groups: [[565, 670, 300], [1395, 670, 300]],
      tags: [[565, 1000], [1395, 1000]],
      optin: [960, 300], search: [565, 318],
    };
  }
  return {
    r: 118,
    scatter: [[300, 690], [780, 760], [330, 1080], [760, 1130], [300, 1470], [780, 1500]],
    cluster: [[400, 700], [410, 1330], [670, 740], [680, 1370], [540, 930], [545, 1560]],
    groups: [[540, 800, 310], [545, 1430, 310]],
    tags: [[540, 1105], [545, 1735]],
    optin: [540, 1845], search: [540, 470],
  };
}
function sPeople(ctx, t) {
  radialBg(ctx, C.lavPale, '#D4CCFF');
  patternFill(ctx, 'hearts', C.violet, 0.07, t, { vx: 20, vy: 26 });
  const L = peopleLayout();
  const hs = LP(110, 104);
  if (RS.land) stext(ctx, 'Finds your friends', RS.W / 2, 205, { size: hs, fill: C.violet, shade: C.violetInk, align: 'center', letter: letterPop(t, 0.3, 0.04, { size: hs }) });
  else {
    stext(ctx, 'Finds your', RS.W / 2, 250, { size: hs, fill: C.violet, shade: C.violetInk, align: 'center', letter: letterPop(t, 0.3, 0.04, { size: hs }) });
    stext(ctx, 'friends', RS.W / 2, 375, { size: hs, fill: C.pink, shade: C.pinkDD, align: 'center', letter: letterPop(t, 0.55, 0.04, { size: hs }) });
  }
  const group = spring(t - PEOPLE_T.group, 1.0, 0.45);
  // Searching "Eyal" lights up his group and dims the other one.
  const found = ease.inOutCubic(seg(t, PEOPLE_T.found, PEOPLE_T.found + 0.3));
  const dimOf = gi => (gi === 1 ? 1 - 0.62 * found : 1);
  // group outlines
  L.groups.forEach(([gx, gy, gr], gi) => {
    const a = seg(t, 2.25 + gi * 0.125, 2.5 + gi * 0.125);
    if (a <= 0) return;
    ctx.save();
    ctx.strokeStyle = gi ? C.pink : C.teal;
    ctx.lineWidth = gi ? 8 : 8 + 6 * found;
    ctx.setLineDash([26, 16]);
    ctx.lineDashOffset = -t * 40;
    ctx.globalAlpha = a * dimOf(gi);
    ctx.beginPath();
    ctx.arc(gx, gy, gr * lerp(0.8, 1, ease.outBack(a)) * (gi ? 1 : 1 + 0.04 * found), 0, TAU);
    ctx.stroke();
    if (!gi && found > 0) {
      ctx.globalAlpha = 0.16 * found;
      ctx.fillStyle = C.teal;
      ctx.fill(circleP(gx, gy, gr));
    }
    ctx.restore();
  });
  FACES.forEach((F, i) => {
    const arrive = i * 0.125;
    const [sx0, sy0] = L.scatter[i], [cx1, cy1] = L.cluster[i];
    const from = [sx0 + (i % 2 ? 1 : -1) * RS.W * 0.7, sy0 - RS.H * 0.4];
    if (t < arrive - 0.35) return;
    let rot = (i % 2 ? 0.14 : -0.12);
    const p = ease.outCubic(seg(t, arrive - 0.35, arrive));
    let x = lerp(from[0], sx0, p);
    let y = lerp(from[1], sy0, p);
    rot = lerp(rot * 6, rot, p);
    x = lerp(x, cx1, group);
    y = lerp(y, cy1, group);
    rot = lerp(rot, (i % 2 ? -0.05 : 0.05), group);
    let [sqx, sqy] = squash(t, arrive, 0.22);
    // Eyal's stickers hop in turn when his name is searched.
    const gi = F.who === 'eyal' ? 0 : 1;
    if (!gi) {
      const [hy, hx2, hy2] = hopAt(t, PEOPLE_T.found + Math.floor(i / 2) * 0.125, 34, 0.45);
      y += hy; sqx *= hx2; sqy *= hy2;
    }
    const r = L.r;
    ctx.save();
    ctx.globalAlpha = dimOf(gi);
    drawSprite(ctx, faceSprite(F.who, F.expr), x, y, (r / 100) * (1 + 0.04 * Math.sin(t * 5 + i)), rot, 1, sqx, sqy);
    ctx.restore();
    // face detection brackets + landmarks
    const det = seg(t, 0.95 + i * 0.0625, 1.1 + i * 0.0625) * (1 - seg(t, 1.95, 2.25));
    if (det > 0) {
      const bs = lerp(1.5, 1, ease.outBack(det)) * r * 0.8;
      ctx.save();
      ctx.translate(x, y - r * 0.05);
      ctx.rotate(rot);
      ctx.globalAlpha = det;
      ctx.strokeStyle = C.teal;
      ctx.lineWidth = 7;
      ctx.lineCap = 'round';
      const Lb = bs * 0.4;
      for (const [dx, dy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
        ctx.beginPath();
        ctx.moveTo(dx * bs, dy * bs + -dy * Lb); ctx.lineTo(dx * bs, dy * bs); ctx.lineTo(dx * bs - dx * Lb, dy * bs);
        ctx.stroke();
      }
      const lm = seg(t, 1.25, 1.35) * (1 - seg(t, 1.8, 1.95));
      if (lm > 0) {
        ctx.globalAlpha = lm;
        ctx.fillStyle = C.teal;
        for (const [mx, my] of FACE_MARKS) ctx.fill(circleP((mx * r) / 100, (my * r) / 100 - 4, 7));
        ctx.fillStyle = '#fff';
        for (const [mx, my] of FACE_MARKS) ctx.fill(circleP((mx * r) / 100, (my * r) / 100 - 4, 3));
      }
      ctx.restore();
    }
  });
  // name tags
  [['Eyal', 'אייל', C.teal], ['Noa', 'נועה', C.pinkD]].forEach(([en, he, col], gi) => {
    const sc = pop(t, PEOPLE_T.names + gi * 0.25, 1.4, 0.38) * (gi ? 1 : 1 + wobble(t, PEOPLE_T.found, 0.1, 2, 5));
    const [tx, ty] = L.tags[gi];
    ctx.save();
    ctx.globalAlpha = dimOf(gi);
    chip(ctx, tx, ty, [{ emoji: '🏷️' }, { text: en, color: col }, { text: '·', color: C.outline }, { text: he, rtl: true, color: col }], { size: LP(44, 42), align: 'center', scale: sc, rot: gi ? 0.04 : -0.04 });
    ctx.restore();
  });
  chip(ctx, L.optin[0], L.optin[1], [{ emoji: '🔒' }, { text: 'opt-in, stays on the phone', bold: false }], { size: LP(28, 28), align: 'center', scale: pop(t, 1.5, 1.4, 0.42) });
  // search by name
  const ss = pop(t, PEOPLE_T.search, 1.4, 0.4);
  if (ss > 0) {
    const typed = 'Eyal'.slice(0, clamp(Math.floor((t - PEOPLE_T.search) / 0.0625) + 1, 0, 4));
    chip(ctx, L.search[0], L.search[1], [{ emoji: '🔎' }, { text: typed.padEnd(4, ' ') }], { size: LP(38, 38), align: 'center', scale: ss, stroke: C.teal });
    sparkles(ctx, t, PEOPLE_T.found, L.groups[0][0], L.groups[0][1], { n: 10, radius: 300, size: 32, seed: 57, color: C.teal });
  }
}

// ================================================================ S6: sticker keyboard -> chat (8 beats)

const CHAT_T = { typed: 0.5, kb: 1.0, tap: 2.0, land: 2.5, ticks: 2.75, reply: 3.0, typing2: 4.0, turtle: 4.5 };
const KB_H = 330;
function chatUI(g, sw, sh, t, hideSent) {
  const u = sw / 400;
  g.scale(u, u);
  const H = sh / u;
  g.fillStyle = '#EFE8DF';
  g.fillRect(0, 0, 400, H);
  g.save();
  g.globalAlpha = 0.06;
  g.fillStyle = C.ink;
  for (let yy = 140; yy < H; yy += 56) for (let xx = (yy / 56) % 2 ? 20 : 48; xx < 400; xx += 56) g.fill(sparkleP(xx, yy, 7));
  g.restore();
  const kb = spring(t - CHAT_T.kb, 1.1, 0.5);
  const kbTop = H - KB_H * kb;
  const inY = kbTop - 66;
  // messages (above the input bar); the chat scrolls up as new ones arrive
  g.save();
  g.beginPath();
  g.rect(0, 114, 400, inY - 114);
  g.clip();
  const shift = -40 * seg(t, CHAT_T.reply - 0.1, CHAT_T.reply + 0.2) - 150 * ease.inOutCubic(seg(t, CHAT_T.turtle - 0.15, CHAT_T.turtle + 0.2));
  g.translate(0, shift);
  const b1 = pop(t, 0.25, 1.5, 0.45);
  if (b1 > 0) {
    g.save();
    g.translate(18, 132);
    g.scale(b1, b1);
    g.fillStyle = '#fff';
    g.fill(rrP(0, 0, 232, 50, 16));
    txt(g, 'where are you?? 😤', 14, 32, { size: 19, color: C.inkD });
    txt(g, '9:41', 220, 44, { size: 11, color: C.outline, align: 'right' });
    g.restore();
  }
  if (t >= CHAT_T.land && !hideSent) {
    const [sqx, sqy] = squash(t, CHAT_T.land, 0.24);
    catSticker(g, 300, 268, 0.52, { sx: sqx, sy: sqy, look: 1, shake: Math.sin(t * TAU * 3) * 0.06 });
    const tk = seg(t, CHAT_T.ticks, CHAT_T.ticks + 0.1);
    if (tk > 0) {
      g.save();
      g.globalAlpha = tk;
      txt(g, '9:42', 330, 356, { size: 12, color: C.outline, align: 'right' });
      checkMark(g, 346, 351, 16, C.violet, 0.2);
      checkMark(g, 355, 351, 16, C.violet, 0.2);
      g.restore();
    }
  }
  const b2 = pop(t, CHAT_T.reply, 1.5, 0.4);
  if (b2 > 0) {
    g.save();
    g.translate(18, 382);
    g.scale(b2, b2);
    g.fillStyle = '#fff';
    g.fill(rrP(0, 0, 140, 54, 16));
    emoji(g, '😂😂😂', 64, 27, 26);
    g.restore();
  }
  // Noa answers with a sticker of her own: a turtle.
  const tt = pop(t, CHAT_T.turtle, 1.5, 0.4);
  if (tt > 0) {
    const [sqx, sqy] = squash(t, CHAT_T.turtle, 0.2);
    drawSprite(g, emojiSticker('🐢', 104), 96, 528, 1.15 * tt, -0.08, 1, sqx, sqy);
  }
  g.restore();
  // header (drawn over the messages that scroll under it)
  g.fillStyle = C.tealDD;
  g.fillRect(0, 0, 400, 114);
  statusBar(g, 400, true);
  g.strokeStyle = '#fff';
  g.lineWidth = 3.5;
  g.lineCap = 'round';
  g.beginPath(); g.moveTo(28, 80); g.lineTo(18, 88); g.lineTo(28, 96); g.stroke();
  drawSprite(g, faceSprite('noa', 'wink'), 62, 88, 0.2);
  txt(g, 'Noa', 94, 84, { size: 21, bold: true, color: '#fff' });
  const typing = (t > 2.8 && t < CHAT_T.reply) || (t > CHAT_T.typing2 && t < CHAT_T.turtle);
  txt(g, typing ? 'typing…' : 'online', 94, 105, { size: 14, color: 'rgba(255,255,255,.85)' });
  // input bar
  g.fillStyle = '#fff';
  g.fill(rrP(12, inY + 6, 318, 52, 26));
  const nt = t < CHAT_T.land - 0.05 ? Math.max(0, Math.min(4, Math.floor((t - CHAT_T.typed) / 0.125) + 1)) : 0;
  if (nt > 0) txt(g, 'late'.slice(0, nt), 32, inY + 40, { size: 20, color: C.inkD });
  else txt(g, 'Message', 32, inY + 40, { size: 19, color: C.outline });
  if (nt > 0 && Math.floor(t * 2) % 2 === 0) { g.font = fr(20); g.fillStyle = C.teal; g.fillRect(34 + g.measureText('late'.slice(0, nt)).width, inY + 20, 2.5, 26); }
  g.fillStyle = C.tealD;
  g.fill(circleP(364, inY + 32, 26));
  g.fillStyle = '#fff';
  g.fill(rrP(358, inY + 18, 12, 20, 6));
  g.fillRect(363, inY + 38, 2.5, 7);
  // Peel-It keyboard
  if (kb > 0.001) {
    g.fillStyle = '#EEEBF8';
    g.fillRect(0, kbTop, 400, KB_H + 40);
    g.fillStyle = C.violet;
    g.fillRect(0, kbTop, 400, 4);
    g.fillStyle = '#fff';
    g.fill(rrP(12, kbTop + 14, 290, 44, 22));
    magnifier(g, 36, kbTop + 36, 20, C.violet);
    txt(g, 'late', 56, kbTop + 43, { size: 19, bold: true, color: C.inkD });
    g.fillStyle = C.violet;
    g.fill(rrP(312, kbTop + 14, 76, 44, 22));
    txt(g, 'עב/EN', 350, kbTop + 42, { size: 16, bold: true, color: '#fff', align: 'center' });
    const thumbs = [{ hero: true }, { e: '⏰' }, { e: '🏃' }, { e: '😅' }];
    thumbs.forEach((th, i) => {
      const tx = 58 + i * 94, ty = kbTop + 118;
      g.fillStyle = '#fff';
      g.fill(rrP(tx - 42, ty - 42, 84, 84, 16));
      const s = pop(t, CHAT_T.kb + 0.25 + i * 0.125, 1.5, 0.4);
      if (th.hero) catSticker(g, tx, ty + 2, 0.27 * s, { look: 1 });
      else drawSprite(g, emojiSticker(th.e, 104), tx, ty, 0.52 * s);
    });
    if (t >= CHAT_T.tap && t < CHAT_T.tap + 0.5) {
      const rp = seg(t, CHAT_T.tap, CHAT_T.tap + 0.5);
      g.save();
      g.globalAlpha = 1 - rp;
      g.fillStyle = C.violet;
      g.fill(circleP(58, kbTop + 118, 20 + 50 * ease.outCubic(rp)));
      g.restore();
    }
    const rows = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'];
    rows.forEach((row, r) => {
      const kw = 34, gap = 4.6;
      const total = row.length * kw + (row.length - 1) * gap;
      const x0 = (400 - total) / 2;
      for (let i = 0; i < row.length; i++) {
        const kx = x0 + i * (kw + gap), ky = kbTop + 178 + r * 48;
        g.fillStyle = '#fff';
        g.fill(rrP(kx, ky, kw, 40, 8));
        txt(g, row[i], kx + kw / 2, ky + 27, { size: 17, color: C.inkD, align: 'center' });
      }
    });
  }
}
function chatPhone() { return LP({ x: 1320, y: 560, w: 430, h: 900 }, { x: 540, y: 1220, w: 500, h: 1040 }); }
function sChat(ctx, t) {
  radialBg(ctx, '#3B3766', C.inkDD);
  patternFill(ctx, 'icons', C.lav, 0.08, t, { vx: -22, vy: 18 });
  const P = chatPhone();
  const enter = spring(t + 0.1, 1.0, 0.45);
  const px = P.x + (1 - enter) * RS.W * 0.6;
  const prot = LP(-0.03, -0.015) + (1 - enter) * 0.2;
  const hs = LP(124, 108);
  const hx = LP(110, RS.W / 2), ha = LP('left', 'center');
  stext(ctx, 'Sends real', hx, LP(350, 230), { size: hs, fill: '#fff', shade: C.lav, outlineColor: C.violet, align: ha, letter: letterPop(t, 0.25, 0.05, { size: hs }) });
  stext(ctx, 'stickers', hx, LP(490, 358), { size: hs, fill: C.amberL, shade: C.amberD, align: ha, letter: letterPop(t, 0.625, 0.05, { size: hs }) });
  chip(ctx, hx, LP(630, 485), [{ emoji: '⌨️' }, { text: 'with its own sticker keyboard' }], { size: LP(32, 30), align: ha, scale: pop(t, 1.0, 1.4, 0.42) });
  const hideSent = t < CHAT_T.land;
  phone(ctx, px, P.y, P.w, P.h, (g, sw, sh) => chatUI(g, sw, sh, t, hideSent), { rot: prot });
  // the tapped sticker flies from the keyboard into the chat
  if (t >= CHAT_T.tap && t < CHAT_T.land) {
    const xf = phoneXf(px, P.y, P.w, P.h, prot);
    const Hs = (P.h - P.w * 0.064) / xf.u;
    const kbTop = Hs - KB_H;
    const a = xf(58, kbTop + 120), b = xf(300, 268);
    const p = ease.inOutCubic(seg(t, CHAT_T.tap, CHAT_T.land));
    const ctrl = [lerp(a[0], b[0], 0.5) - LP(260, 200), Math.min(a[1], b[1]) - LP(260, 300)];
    const x = (1 - p) * (1 - p) * a[0] + 2 * (1 - p) * p * ctrl[0] + p * p * b[0];
    const y = (1 - p) * (1 - p) * a[1] + 2 * (1 - p) * p * ctrl[1] + p * p * b[1];
    const s = lerp(0.27, 0.52, p) * xf.u * (1 + Math.sin(p * Math.PI) * 0.5);
    catSticker(ctx, x, y, s, { rot: -TAU * ease.inOutCubic(p), look: 1 });
  }
  if (t >= CHAT_T.land) {
    const xf = phoneXf(px, P.y, P.w, P.h, prot);
    const [lx, ly] = xf(300, 268);
    ring(ctx, t, CHAT_T.land, lx, ly, { r0: 40, r1: 190, width: 14, color: C.amberL });
    sparkles(ctx, t, CHAT_T.land, lx, ly, { n: 8, radius: 150, size: 28, seed: 61, color: C.amberL });
  }
}

// ================================================================ S7: nothing leaves the phone (8 beats)

function privacyCenter() { return LP([1360, 560], [540, 660]); }
// Things that try to leave the phone and bounce off the shield: [beat, angle, emoji]
const PACKETS = [[1.25, -2.4, '🖼️'], [1.75, -0.7, '💬'], [2.25, 0.5, '🏷️'], [2.75, 2.3, '🙂'], [3.75, -1.6, '📷'], [4.75, 1.3, '🎂'], [5.75, -2.9, '😂'], [6.5, 0.1, '🐱']];
const PACKET_OUT = 0.375;
const SHIELD_POP = 7.75;
function sPrivacy(ctx, t) {
  radialBg(ctx, '#3A2BB0', C.violetDeep, ...privacyCenter());
  patternFill(ctx, 'grid', C.lav, 0.06, t, { angle: 0, vx: 0, vy: 20 });
  const [cx, cy] = privacyCenter();
  const R = LP(330, 350);
  // shield bubble
  const sh = spring(t - 1.0, 1.0, 0.4);
  const popOut = seg(t, SHIELD_POP - 0.125, SHIELD_POP + 0.125);
  if (sh > 0 && popOut < 1) {
    const r = R * sh * (1 + 0.12 * ease.inQuad(popOut)) * (1 + 0.012 * Math.sin(t * 3.2));
    ctx.save();
    ctx.globalAlpha = 1 - popOut;
    const g = ctx.createRadialGradient(cx, cy, r * 0.2, cx, cy, r);
    g.addColorStop(0, 'rgba(92,219,190,.05)');
    g.addColorStop(0.8, 'rgba(92,219,190,.16)');
    g.addColorStop(1, 'rgba(92,219,190,.34)');
    ctx.fillStyle = g;
    ctx.fill(circleP(cx, cy, r));
    ctx.strokeStyle = C.tealL;
    ctx.lineWidth = 10;
    ctx.stroke(circleP(cx, cy, r));
    ctx.strokeStyle = 'rgba(255,255,255,.55)';
    ctx.lineWidth = 12;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.arc(cx, cy, r * 0.84, Math.PI * 1.1, Math.PI * 1.38);
    ctx.stroke();
    ctx.restore();
  }
  if (popOut > 0) {
    ring(ctx, t, SHIELD_POP, cx, cy, { r0: R, r1: R * 1.8, width: 24, color: C.tealL, life: 0.5 });
    sparkles(ctx, t, SHIELD_POP, cx, cy, { n: 16, radius: R * 1.2, size: 40, seed: 71, color: C.mint });
  }
  // packets that try to leave and bounce back off the shield
  PACKETS.forEach(([t0, ang, e]) => {
    const d = t - t0;
    if (d < 0 || d > 0.9) return;
    const rr0 = LP(80, 90), rr1 = R - 36;
    let rr;
    if (d < PACKET_OUT) rr = lerp(rr0, rr1, ease.inQuad(d / PACKET_OUT));
    else rr = lerp(rr1, rr0 + 40, ease.outCubic((d - PACKET_OUT) / (0.9 - PACKET_OUT)));
    const x = cx + Math.cos(ang) * rr, y = cy + Math.sin(ang) * rr;
    const a = d < 0.75 ? 1 : 1 - (d - 0.75) / 0.15;
    drawSprite(ctx, emojiSticker(e, 104), x, y, 0.6, d * 3, a);
    if (d >= PACKET_OUT) {
      const hx = cx + Math.cos(ang) * R, hy = cy + Math.sin(ang) * R;
      ring(ctx, t, t0 + PACKET_OUT, hx, hy, { r0: 10, r1: 90, width: 12, color: '#fff', life: 0.45 });
    }
  });
  // phone + lock
  const ps = spring(t - 0.75, 1.2, 0.42) * (1 - ease.inBack(popOut) * 0.2);
  if (ps > 0) {
    withT(ctx, cx, cy, ps, -0.05, g2 => {
      const pw = LP(225, 235), ph = pw * 2;
      phone(g2, 0, 0, pw, ph, (g, sw, sh2) => {
        const gr = g.createLinearGradient(0, 0, 0, sh2);
        gr.addColorStop(0, C.violet2);
        gr.addColorStop(1, C.teal);
        g.fillStyle = gr;
        g.fillRect(0, 0, sw, sh2);
        logo(g, sw / 2, sh2 * 0.42, sw * 0.62, { rot: -0.14 });
      });
    });
  }
  const lockIn = spring(t, 1.2, 0.38);
  const lockMove = ease.inOutCubic(seg(t, 0.65, 1.1));
  const lx = lerp(cx, cx + LP(118, 122), lockMove), ly = lerp(cy, cy + LP(185, 192), lockMove);
  const lsz = lerp(LP(230, 250), LP(128, 134), lockMove) * lockIn * (1 + wobble(t, 4.0, 0.08, 2, 6));
  if (lockIn > 0) {
    padlock(ctx, lx, ly, lsz, { shackle: 1 - ease.outBack(seg(t, 0.35, 0.5)) });
    burstLines(ctx, t, 0.5, lx, ly, { n: 10, r0: 150, r1: 280, width: 12, color: C.amberL });
  }
  // headline
  const hs = LP(116, 104), hx = LP(110, RS.W / 2), ha = LP('left', 'center');
  const lines = RS.land ? ['Nothing', 'leaves your', 'phone.'] : ['Nothing leaves', 'your phone.'];
  const ys = RS.land ? [300, 435, 570] : [1180, 1305];
  lines.forEach((l, i) => stext(ctx, l, hx, ys[i], { size: hs, fill: i === lines.length - 1 ? C.tealL : '#fff', shade: i === lines.length - 1 ? C.tealDD : C.lav, outlineColor: C.ink, outline: 0.16, align: ha, letter: letterPop(t, 0.9 + i * 0.25, 0.04, { size: hs }) }));
  // stamp
  const st = seg(t, 1.85, 2.0);
  if (st > 0) {
    const sx = LP(560, 540), sy = LP(760, 1500);
    const sc = lerp(2.2, 1, ease.inQuad(st)) * (1 + wobble(t, 2.0, 0.08, 2, 6));
    withT(ctx, sx, sy, sc, -0.08, g => {
      g.globalAlpha = lerp(0.2, 1, st);
      const w = LP(760, 700), h = 150;
      g.strokeStyle = C.red;
      g.lineWidth = 12;
      g.stroke(rrP(-w / 2, -h / 2, w, h, 24));
      g.lineWidth = 4;
      g.stroke(rrP(-w / 2 + 16, -h / 2 + 16, w - 32, h - 32, 14));
      g.fillStyle = C.red;
      g.font = fb(60);
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.fillText('NO INTERNET', 0, -14);
      g.font = fb(30);
      g.fillText('PERMISSION  ·  CHECKED IN CI', 0, 42);
    });
  }
  chip(ctx, hx, LP(895, 1665), [{ emoji: '🧠' }, { text: 'Every AI model runs on the phone' }], { size: LP(32, 30), align: ha, scale: pop(t, 2.5, 1.4, 0.42) });
  chip(ctx, hx, LP(985, 1765), [{ emoji: '✈️' }, { text: 'Works in airplane mode' }], { size: LP(32, 30), align: ha, scale: pop(t, 3.25, 1.4, 0.42) });
  const flash = ease.inQuad(seg(t, SHIELD_POP, 8.0));
  if (flash > 0) { ctx.save(); ctx.globalAlpha = flash; bleedRect(ctx, '#FFFFFF'); ctx.restore(); }
}

// ================================================================ S8: under the hood (16 beats)

const TECH = [
  { label: 'Kotlin', grad: ['#7F52FF', '#E44857'], shape: 'pill', size: 40 },
  { label: 'Jetpack\nCompose', fill: C.green, shape: 'hex', size: 30 },
  { label: 'SigLIP 2', emoji: '👁️', fill: C.pink, shape: 'pill', size: 36 },
  { label: 'Room + FTS4', fill: C.amber, color: C.inkD, shape: 'tag', size: 34 },
  { label: 'Granite R2\nembeddings', fill: C.ink, shape: 'bubble', size: 30 },
  { label: 'LiteRT', fill: C.orange, shape: 'circle', size: 34 },
  { label: 'Tesseract\nOCR', fill: C.sky, color: C.inkD, shape: 'circle', size: 28 },
  { label: 'ML Kit + SFace', fill: C.violet, shape: 'pill', size: 32 },
  { label: 'Rank\nFusion', fill: C.tealDD, shape: 'hex', size: 30 },
  { label: 'WorkManager', fill: '#fff', color: C.ink, ring: C.teal, shape: 'pill', size: 32 },
  { label: 'R8', fill: C.inkD, shape: 'circle', size: 40 },
  { label: 'GitHub Actions', fill: '#24292F', shape: 'tag', size: 30 },
  { label: 'Emulator\nsmoke test', fill: C.red, shape: 'shield', size: 26 },
  { label: 'SHA-256\npinned', fill: C.lav, color: C.inkD, shape: 'pill', round: 0.5, size: 30 },
];
const TECH_T = i => 0.5 + i * 0.25;
const HOOD_SHRINK = 7.25;
const STAT_T = i => 8.0 + i * 0.5;
function techLayout() {
  if (RS.land) {
    return {
      lid: { x: 960, y: 610, w: 1200, h: 720 },
      pos: [[-420, -225, -0.12], [-150, -235, 0.08], [160, -228, -0.06], [425, -205, 0.1],
        [-395, -10, 0.06], [-205, 45, -0.1], [230, 10, 0.12], [430, 20, -0.05],
        [-420, 215, -0.08], [-155, 225, 0.05], [90, 215, -0.1], [300, 225, 0.07], [470, 195, -0.12], [20, 205, 0.04]],
    };
  }
  return {
    lid: { x: 540, y: 1030, w: 940, h: 1300 },
    pos: [[-230, -470, -0.12], [210, -450, 0.08], [-220, -290, 0.1], [220, -270, -0.06],
      [-200, -100, -0.08], [230, -90, 0.12], [-230, 90, 0.06], [200, 110, -0.1],
      [-220, 280, 0.1], [210, 300, -0.07], [-250, 470, -0.05], [170, 460, 0.08], [-20, 540, -0.1], [0, -10, 0.05]],
  };
}
const STATS = [
  { n: 5, suffix: '', label: ['on-device', 'AI models'], fill: C.pink, shade: C.pinkDD },
  { n: 8, suffix: '', label: ['Gradle', 'modules'], fill: C.teal, shade: C.tealDD },
  { n: 80, suffix: '+', label: ['unit', 'tests'], fill: C.amber, shade: C.amberD },
  { n: 0, suffix: '', label: ['network', 'permissions'], fill: C.violet, shade: C.violetInk },
];
function sHood(ctx, t) {
  linearBg(ctx, C.violet2, C.violetMid, 1.0);
  patternFill(ctx, 'code', '#FFFFFF', 0.08, t, { vx: -18, vy: -24 });
  const T = techLayout();
  const shrink = ease.inOutCubic(seg(t, HOOD_SHRINK, HOOD_SHRINK + 0.5));
  const lidS = lerp(1, LP(0.56, 0.46), shrink);
  const lidX = lerp(T.lid.x, LP(960, 540), shrink), lidY = lerp(T.lid.y, LP(815, 1520), shrink);
  const lidIn = spring(t, 1.0, 0.45);
  const shakeLid = TECH.reduce((acc, _, i) => acc + wobble(t, TECH_T(i), 5, 3, 9), 0);
  ctx.save();
  ctx.translate(lidX, lidY + (1 - lidIn) * RS.H + shakeLid);
  ctx.scale(lidS, lidS);
  ctx.rotate(LP(-0.02, -0.015) + 0.012 * Math.sin(t * 0.9) * seg(t, 4, 5));
  const { w, h } = T.lid;
  const lid = rrP(-w / 2, -h / 2, w, h, 56);
  ctx.save();
  ctx.translate(18, 22);
  ctx.fillStyle = 'rgba(12,6,40,.32)';
  ctx.fill(lid);
  ctx.restore();
  const lg = ctx.createLinearGradient(-w / 2, -h / 2, w / 2, h / 2);
  lg.addColorStop(0, '#3A3848');
  lg.addColorStop(1, '#23212D');
  ctx.fillStyle = lg;
  ctx.fill(lid);
  ctx.strokeStyle = 'rgba(255,255,255,.1)';
  ctx.lineWidth = 4;
  ctx.stroke(rrP(-w / 2 + 8, -h / 2 + 8, w - 16, h - 16, 50));
  ctx.save();
  ctx.globalAlpha = 0.9;
  ctx.scale(2.3, 2.3);
  ctx.translate(-48, -52);
  elephantRaw(ctx, { mono: true, color: '#F3F0FF' });
  ctx.restore();
  TECH.forEach((st, i) => {
    const t0 = TECH_T(i);
    const d = t - t0;
    if (d < -0.12) return;
    const [dx, dy, r] = T.pos[i];
    let s = 1;
    if (d < 0) s = lerp(1.9, 1, ease.inQuad(1 + d / 0.12));
    const [sqx, sqy] = squash(t, t0, 0.2);
    drawSprite(ctx, techSprite(st), dx, dy, s * 1.18, r, d < 0 ? 0.6 : 1, sqx, sqy);
  });
  // A glossy shine sweeps across the lid while you read the stack.
  const sh = seg(t, 5.0, 5.9);
  if (sh > 0 && sh < 1) {
    ctx.save();
    ctx.clip(lid);
    const x = lerp(-w * 0.9, w * 0.9, ease.inOutSine(sh));
    const gr = ctx.createLinearGradient(x - 160, 0, x + 160, 0);
    gr.addColorStop(0, 'rgba(255,255,255,0)');
    gr.addColorStop(0.5, 'rgba(255,255,255,.32)');
    gr.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = gr;
    ctx.transform(1, 0, -0.35, 1, 0, 0);
    ctx.fillRect(-w, -h, w * 2 + 400, h * 2);
    ctx.restore();
  }
  ctx.restore();
  TECH.forEach((_, i) => {
    const [dx, dy] = T.pos[i];
    const t0 = TECH_T(i);
    if (t > t0 && t < t0 + 0.5 && shrink === 0) ring(ctx, t, t0, T.lid.x + dx, T.lid.y + dy, { r0: 50, r1: 160, width: 10, color: '#fff', life: 0.4 });
  });
  const hs = LP(96, 100);
  stext(ctx, 'Under the hood', RS.W / 2, LP(150, 215), { size: hs, fill: C.amberL, shade: C.amberD, align: 'center', letter: letterPop(t, 0.25, 0.04, { size: hs }) });
  // stats
  STATS.forEach((S, i) => {
    const t0 = STAT_T(i);
    const sc = pop(t, t0, 1.3, 0.4) * (1 + wobble(t, 12.0 + i * 0.125, 0.07, 2, 5));
    if (sc <= 0) return;
    const tw = LP(380, 440), th = LP(290, 330);
    const [x, y] = RS.land ? [960 + (i - 1.5) * 420, 430] : [290 + (i % 2) * 500, 620 + Math.floor(i / 2) * 390];
    const rot = (i % 2 ? 0.03 : -0.03);
    withT(ctx, x, y, sc, rot, g => {
      const p = rrP(-tw / 2, -th / 2, tw, th, 44);
      dieCut(g, p, { border: 12, shadow: 14 });
      g.fillStyle = '#fff';
      g.fill(p);
      const v = Math.round(S.n * ease.outCubic(seg(t, t0, t0 + 0.6)));
      const numS = LP(150, 170);
      stext(g, `${v}${S.suffix}`, 0, -th * 0.02, { size: numS, fill: S.fill, shade: S.shade, align: 'center', outline: 0.12 });
      txt(g, S.label[0], 0, th * 0.25, { size: LP(32, 36), bold: true, color: C.inkD, align: 'center' });
      txt(g, S.label[1], 0, th * 0.25 + LP(38, 42), { size: LP(32, 36), bold: true, color: C.inkD, align: 'center' });
    });
    sparkles(ctx, t, t0 + 0.1, x, y - LP(60, 70), { n: 6, radius: 160, size: 28, seed: 80 + i, color: '#FFF3A0' });
    sparkles(ctx, t, 12.0 + i * 0.125, x, y - LP(60, 70), { n: 5, radius: 150, size: 24, seed: 90 + i, color: '#FFF3A0' });
  });
  const flash = 1 - seg(t, 0, 0.3);
  if (flash > 0) { ctx.save(); ctx.globalAlpha = flash; bleedRect(ctx, '#FFFFFF'); ctx.restore(); }
}

// ================================================================ S9: finale (12 beats)

const FIN_T = { tada: 2.0, rest: 2.25, idleHop: 7.0, final: 10.0 };
function finaleLayout() {
  if (RS.land) {
    return { logo: [960, 300, 270], word: [960, 612, 190], tag: [960, 725], feat: 842, credit: [960, 950], url: [960, 1000], ground: 1010, big: 5.2, small: 3.5, hops: [-320, 280, 600, 820], rest: 200 };
  }
  return { logo: [540, 470, 340], word: [540, 868, 176], tag: [540, 995], feat: 1115, credit: [540, 1445], url: [540, 1495], ground: 1800, big: 5.4, small: 3.8, hops: [-320, 120, 330, 470], rest: 300 };
}
function sFinale(ctx, t) {
  linearBg(ctx, C.violet2, C.teal, 0.8);
  patternFill(ctx, 'icons', '#FFFFFF', 0.1, t, { vx: 26, vy: 16 });
  const F = finaleLayout();
  const [lx, ly, ls] = F.logo;
  const TADA = FIN_T.tada, FINAL = FIN_T.final;
  sunburst(ctx, lx, ly, 2600, 20, '#FFFFFF', 0.1 * seg(t, TADA, TADA + 0.4), t * 0.1);
  // The elephant hops in big (take-offs at -0.5, 0 and 0.5), tosses the sticker that becomes the
  // logo, then turns round and hops back to the corner so the end card has room.
  const gy = F.ground;
  let ex = F.hops[3], ey = gy, sx = 1, sy = 1, legA = 0, legB = 0, es = F.big, flip = false;
  const hop = (x0, x1, t0, len, h) => {
    const p = clamp((t - t0) / len);
    const air = Math.sin(Math.PI * p);
    ex = lerp(x0, x1, p);
    ey = gy - air * h;
    sx = 1 - 0.08 * air; sy = 1 + 0.12 * air;
    legA = 0.45 * air; legB = -0.45 * air;
  };
  if (t < 1.0) {
    const k = clamp(Math.floor((t + 0.5) / 0.5), 0, 2);
    hop(F.hops[k], F.hops[k + 1], -0.5 + k * 0.5, 0.5, LP(200, 240));
    if (t < -0.5) ex = F.hops[0];
  } else if (t >= FIN_T.rest) {
    const p = seg(t, FIN_T.rest, FIN_T.rest + 0.5);
    hop(F.hops[3], F.rest, FIN_T.rest, 0.5, LP(70, 90));
    es = lerp(F.big, F.small, ease.inOutCubic(p));
    flip = t < FIN_T.rest + 0.85;
    if (t >= FIN_T.idleHop) hop(F.rest, F.rest, FIN_T.idleHop, 0.4, 60);
  }
  const landT = [0.0, 0.5, 1.0, TADA, FIN_T.rest + 0.5, FIN_T.idleHop + 0.4, FINAL];
  for (const lt of landT) {
    const [a, b] = squash(t, lt, lt === FINAL ? 0.12 : 0.2);
    if (t >= lt && t < lt + 1.2) { sx *= a; sy *= b; }
  }
  landT.slice(0, 3).forEach((lt, k) => dust(ctx, t, lt, F.hops[k + 1], gy, { spread: 150, size: 34, seed: 10 + k }));
  dust(ctx, t, FIN_T.rest + 0.5, F.rest, gy, { spread: 110, size: 26, seed: 14 });
  dust(ctx, t, FIN_T.idleHop + 0.4, F.rest, gy, { spread: 90, size: 22, seed: 15 });
  const earFlap = t0 => (t > t0 ? Math.sin((t - t0) * 14) * 0.2 * Math.exp(-(t - t0) * 2) : 0);
  const ear = -0.25 * Math.max(0, Math.sin((t - 1.0) * 9)) * seg(t, 1.0, 1.5) * (1 - seg(t, 3, 3.5)) + earFlap(FIN_T.idleHop) + earFlap(FINAL);
  const trunkUp = -0.3 * ease.outBack(seg(t, 1.0, 1.3)) * (1 - ease.inOutCubic(seg(t, 2.1, 2.6)) * 0.6);
  const wink = t > FINAL + 0.05 && t < FINAL + 0.45 ? 1 : 0;
  elephant(ctx, ex, ey, es, { sx, sy, flip, legBack: legA, legFront: legB, ear, trunk: trunkUp, blink: wink, blush: t > TADA, head: Math.sin(t * 3) * 0.04 });
  // the mini sticker: appears on the trunk, tossed onto the logo spot
  const tip = [ex + (88 - 48) * es, ey + (22 - 85) * es];
  if (t >= 1.25 && t < TADA) {
    const appear = pop(t, 1.25, 1.6, 0.4);
    const p = ease.inOutQuad(seg(t, 1.5, TADA));
    const x = lerp(tip[0], lx, p), y = lerp(tip[1] - 34 * appear, ly, p) - Math.sin(Math.PI * p) * LP(260, 380);
    const s = lerp(0.9, 2.2, p) * appear;
    withT(ctx, x, y, s * LP(1.1, 1.2), 0.2 + p * TAU * 1.5, g => {
      const body = new Path2D('M-15,-17h30a4,4 0,0 1,4,4v18l-12,12h-22a4,4 0,0 1,-4,-4v-26a4,4 0,0 1,4,-4z');
      g.save(); g.translate(0, 1.8); g.fillStyle = 'rgba(0,0,0,.14)'; g.fill(body); g.restore();
      g.fillStyle = '#fff'; g.fill(body);
      g.strokeStyle = '#D6D0F5'; g.lineWidth = 1.2; g.stroke(body);
      g.fillStyle = C.pink; g.fill(heartP(0, -2, 8));
      g.fillStyle = C.fold; g.fill(new Path2D('M19,5l-12,12v-8a4,4 0,0 1,4,-4z'));
    });
    sparkles(ctx, t, 1.25, tip[0], tip[1] - 30, { n: 6, radius: 70, size: 22, seed: 91 });
  }
  // TA-DA: the logo lands, confetti. Its corner teases a peel while the end card holds.
  if (t >= TADA) {
    const [sqx, sqy] = squash(t, TADA, 0.24);
    const beat = 1 + wobble(t, FINAL, 0.07, 2, 5) + wobble(t, 6.0, 0.03, 2, 5);
    const fl = Math.sin((t - TADA) * 2) * 6 * seg(t, TADA + 0.5, TADA + 1.5);
    const k = 20 + 16 * Math.sin(Math.PI * seg(t, 6.0, 6.5)) + 16 * Math.sin(Math.PI * seg(t, 8.0, 8.5)) + wobble(t, 8.5, 4, 2, 5);
    ring(ctx, t, TADA, lx, ly, { r0: ls * 0.5, r1: ls * 1.5, width: 34 });
    burstLines(ctx, t, TADA, lx, ly, { n: 14, r0: ls * 0.7, r1: ls * 1.4, width: 16 });
    logo(ctx, lx, ly + fl, ls * beat * lerp(0.4, 1, ease.outBack(seg(t, TADA, TADA + 0.12), 3)), { rot: rad(-8 + wobble(t, TADA, 12, 1.2, 3)), sx: sqx, sy: sqy, k });
    const corner = logoPoint(lx, ly + fl, ls, rad(-8), 80, 80);
    sparkles(ctx, t, 6.05, corner[0], corner[1], { n: 6, radius: 90, size: 26, seed: 102, color: C.amberL });
    sparkles(ctx, t, FINAL, lx, ly, { n: 12, radius: ls * 1.1, size: 40, seed: 101, color: C.amberL });
  }
  // wordmark
  const [wxc, wy, ws] = F.word;
  const wP = textWidth(ctx, 'Peel', ws), wI = textWidth(ctx, '-It', ws), gap = ws * 0.012;
  const wx = wxc - (wP + gap + wI) / 2;
  const lp0 = letterPop(t, TADA + 0.25, 0.125, { size: ws, rise: 0.8, spin: 0.4 });
  const wave = i => { const b = lp0(i); if (!b) return b; const q = t > FINAL ? Math.sin((t - FINAL) * 12 - i * 0.9) * 14 * Math.exp(-(t - FINAL) * 2.2) : 0; return { ...b, dy: b.dy - Math.max(0, q) }; };
  stext(ctx, 'Peel', wx, wy, { size: ws, fill: C.pink, shade: C.pinkDD, letter: wave });
  stext(ctx, '-It', wx + wP + gap, wy, { size: ws, fill: C.amberL, shade: C.amberD, letter: i => wave(i + 4) });
  chip(ctx, F.tag[0], F.tag[1], [{ text: 'Find the right sticker. Fast.' }], { size: LP(46, 44), align: 'center', scale: pop(t, 3.25, 1.3, 0.42) * (1 + wobble(t, 8.0, 0.04, 2, 6)) });
  // feature chips
  const feats = [[{ emoji: '🔎' }, { text: 'Search by meaning' }], [{ text: 'עברית', rtl: true, color: C.violet }, { text: '+ English' }], [{ emoji: '🔒' }, { text: '100% on-device' }]];
  const fs = LP(34, 38);
  const fscale = i => pop(t, 3.75 + i * 0.25, 1.4, 0.4) * (1 + wobble(t, 8.25 + i * 0.125, 0.06, 2, 6));
  if (RS.land) {
    const ws2 = feats.map(f => chipWidth(ctx, f, fs)), gap2 = 34;
    let x = 960 - (ws2.reduce((a, b) => a + b, 0) + gap2 * 2) / 2 + 85;
    feats.forEach((f, i) => { chip(ctx, x + ws2[i] / 2, F.feat, f, { size: fs, align: 'center', scale: fscale(i), fill: C.lavPale }); x += ws2[i] + gap2; });
  } else feats.forEach((f, i) => chip(ctx, 540, F.feat + i * 100, f, { size: fs, align: 'center', scale: fscale(i), fill: C.lavPale }));
  // credits
  const c1 = ease.outCubic(seg(t, 4.75, 5.15)), c2 = ease.outCubic(seg(t, 5.0, 5.4));
  if (c1 > 0) txt(ctx, CREDIT, F.credit[0] + LP(60, 0), F.credit[1] + (1 - c1) * 30, { size: LP(34, 38), bold: true, color: '#fff', align: 'center', alpha: c1 });
  if (c2 > 0) txt(ctx, REPO_URL, F.url[0] + LP(60, 0), F.url[1] + (1 - c2) * 30, { size: LP(26, 29), color: C.mint, align: 'center', alpha: c2 });
  // confetti
  confetti(ctx, t, TADA, -40, RS.H + 40, { n: 70, seed: 7, dir: -1.05, spread: 0.6, speed: [1500, 2600], g: 1400, life: 4 });
  confetti(ctx, t, TADA, RS.W + 40, RS.H + 40, { n: 70, seed: 8, dir: -Math.PI + 1.05, spread: 0.6, speed: [1500, 2600], g: 1400, life: 4 });
  confetti(ctx, t, FINAL, RS.W / 2, -60, { n: 60, seed: 12, dir: Math.PI / 2, spread: 2.6, speed: [200, 700], g: 380, life: 3 });
}

// ================================================================ transitions

function clipPoly(pts, f) {
  const out = [];
  for (let i = 0; i < pts.length; i++) {
    const A = pts[i], B = pts[(i + 1) % pts.length], fa = f(A), fb2 = f(B);
    if (fa >= 0) out.push(A);
    if ((fa >= 0) !== (fb2 >= 0)) {
      const k = fa / (fa - fb2);
      out.push([A[0] + (B[0] - A[0]) * k, A[1] + (B[1] - A[1]) * k]);
    }
  }
  return out;
}

// The outgoing frame is a sticker peeled off from `corner`, showing the next scene underneath.
function tPeel(ctx, tb, a, b, drawA, drawB, corner, buf) {
  const p = ease.inOutCubic(seg(tb, a, b));
  drawB(ctx);
  const W = RS.W, H = RS.H;
  const Cx = corner.includes('r') ? W : 0, Cy = corner.includes('b') ? H : 0;
  let dx = W / 2 - Cx, dy = H / 2 - Cy + (corner.includes('b') ? -H * 0.15 : H * 0.15);
  const L = Math.hypot(dx, dy);
  dx /= L; dy /= L;
  const diag = Math.hypot(W, H);
  const m = p * diag * 1.2;
  const f = X => (X[0] - Cx) * dx + (X[1] - Cy) * dy - m;
  const rect = [[-150, -150], [W + 150, -150], [W + 150, H + 150], [-150, H + 150]];
  const kept = clipPoly(rect, f);
  const peeled = clipPoly(rect, X => -f(X));
  const flap = peeled.map(X => { const d = f(X); return [X[0] - 2 * d * dx, X[1] - 2 * d * dy]; });
  // shadow cast by the flap
  if (flap.length > 2) {
    ctx.save();
    for (const [o, al] of [[34, 0.1], [20, 0.12], [8, 0.16]]) {
      ctx.fillStyle = `rgba(12,6,40,${al})`;
      ctx.fill(polyP(flap.map(([x, y]) => [x + dx * o, y + dy * o])));
    }
    ctx.restore();
  }
  if (kept.length > 2) {
    ctx.save();
    ctx.clip(polyP(kept));
    buf.draw(drawA);
    ctx.drawImage(buf.canvas, 0, 0, W, H);
    // shading near the fold on the page that's still stuck down
    const fx = Cx + dx * m, fy = Cy + dy * m;
    const g = ctx.createLinearGradient(fx, fy, fx + dx * 80, fy + dy * 80);
    g.addColorStop(0, 'rgba(12,6,40,.25)');
    g.addColorStop(1, 'rgba(12,6,40,0)');
    ctx.fillStyle = g;
    ctx.fillRect(-150, -150, W + 300, H + 300);
    ctx.restore();
  }
  if (flap.length > 2) {
    const fx = Cx + dx * m, fy = Cy + dy * m;
    const g = ctx.createLinearGradient(fx, fy, fx - dx * m * 1.2, fy - dy * m * 1.2);
    g.addColorStop(0, '#E4DFFB');
    g.addColorStop(0.12, '#FFFFFF');
    g.addColorStop(0.55, '#F1EEFD');
    g.addColorStop(1, C.fold);
    ctx.fillStyle = g;
    const fp = polyP(flap);
    ctx.fill(fp);
    ctx.strokeStyle = C.foldLine;
    ctx.lineWidth = 3;
    ctx.stroke(fp);
  }
}
function tIris(ctx, tb, a, b, drawA, drawB, cx, cy) {
  const p = ease.inCubic(seg(tb, a, b));
  drawA(ctx);
  const R = Math.hypot(RS.W, RS.H) * 2.2 * p + 1;
  const path = roundStarP(cx, cy, R, R * 0.52, 5, -Math.PI / 2 + p * 1.2, 0.3);
  ctx.save();
  ctx.clip(path);
  drawB(ctx);
  ctx.restore();
  ctx.save();
  ctx.strokeStyle = '#fff';
  ctx.lineWidth = 22;
  ctx.lineJoin = 'round';
  ctx.stroke(path);
  ctx.restore();
}
function tWhip(ctx, tb, a, b, drawA, drawB, bufA, bufB) {
  const p = ease.inOutCubic(seg(tb, a, b));
  const v = Math.sin(Math.PI * seg(tb, a, b));
  const off = p * RS.W;
  bufA.draw(drawA);
  bufB.draw(drawB);
  const blit = (buf, x) => {
    ctx.drawImage(buf.canvas, x, 0, RS.W, RS.H);
    if (v > 0.2) {
      ctx.save();
      for (let k = 1; k <= 3; k++) {
        ctx.globalAlpha = 0.18 * v;
        ctx.drawImage(buf.canvas, x + k * 26 * v, 0, RS.W, RS.H);
      }
      ctx.restore();
    }
  };
  blit(bufA, -off);
  blit(bufB, RS.W - off);
  speedLines(ctx, v, 7, 'rgba(255,255,255,.55)', 1);
}
function keyholeP(cx, cy, s) {
  const p = new Path2D();
  p.arc(cx, cy - s * 0.3, s * 0.5, 0, TAU);
  p.moveTo(cx - s * 0.22, cy - s * 0.05);
  p.lineTo(cx + s * 0.22, cy - s * 0.05);
  p.lineTo(cx + s * 0.4, cy + s * 0.95);
  p.lineTo(cx - s * 0.4, cy + s * 0.95);
  p.closePath();
  return p;
}
function tKeyhole(ctx, tb, a, b, drawA, drawB, fromXY, toXY) {
  const mid = (a + b) / 2;
  const big = Math.hypot(RS.W, RS.H) * 2.4;
  bleedRect(ctx, C.inkDD);
  const closing = tb < mid;
  const p = closing ? seg(tb, a, mid) : 1 - seg(tb, mid, b);
  // Snap to a keyhole you can recognise, hold it, then shut it (reversed when opening).
  const hold = LP(430, 470);
  const s = p < 0.4 ? lerp(big, hold, ease.outCubic(p / 0.4)) : p < 0.65 ? hold * (1 - 0.08 * (p - 0.4) / 0.25) : lerp(hold * 0.92, 0.5, ease.inCubic((p - 0.65) / 0.35));
  const [x, y] = closing ? fromXY : toXY;
  const path = keyholeP(x, y, s);
  ctx.save(); ctx.clip(path); (closing ? drawA : drawB)(ctx); ctx.restore();
  if (s < hold * 1.6) { ctx.save(); ctx.strokeStyle = '#fff'; ctx.lineWidth = 14; ctx.lineJoin = 'round'; ctx.stroke(path); ctx.restore(); }
}

// ================================================================ composer

const SCENES = { logo: sLogo, many: sMany, search: sSearch, sees: sSees, people: sPeople, chat: sChat, privacy: sPrivacy, hood: sHood, finale: sFinale };
// In global beats. search -> sees and privacy -> hood are cuts through a white flash drawn by the
// scenes themselves.
const TRANSITIONS = [
  { a: AT.many - 0.625, b: AT.many + 0.25, from: 'logo', to: 'many', kind: 'peel', corner: 'br' },
  { a: AT.search - 0.5, b: AT.search + 0.25, from: 'many', to: 'search', kind: 'iris' },
  { a: AT.people - 0.5, b: AT.people + 0.25, from: 'sees', to: 'people', kind: 'whip' },
  { a: AT.chat - 0.5, b: AT.chat + 0.375, from: 'people', to: 'chat', kind: 'peel', corner: 'tl' },
  { a: AT.privacy - 0.5, b: AT.privacy + 0.5, from: 'chat', to: 'privacy', kind: 'keyhole' },
  { a: AT.finale - 0.75, b: AT.finale + 0.125, from: 'hood', to: 'finale', kind: 'peel', corner: 'bl' },
];
const ORDER = ['logo', 'many', 'search', 'sees', 'people', 'chat', 'privacy', 'hood', 'finale'];
function sceneAt(tb) {
  let name = ORDER[0];
  for (const n of ORDER) if (tb >= AT[n]) name = n;
  return name;
}
// Camera shake impulses: [global beat, strength in px].
const IMPACTS = [
  [AT.logo + 0.25, 18], [AT.many + 3.25, 7], [AT.sees, 9], [AT.chat + CHAT_T.land, 5], [AT.privacy + 0.5, 8], [AT.privacy + 2.0, 14],
  [AT.finale + FIN_T.tada, 13], [AT.finale + FIN_T.final, 6], ...TECH.map((_, i) => [AT.hood + TECH_T(i), 3]),
];
function shakeAt(tb) {
  let x = 0, y = 0, r = 0;
  for (const [t0, amp] of IMPACTS) {
    const d = tb - t0;
    if (d < 0 || d > 1.2) continue;
    const e = amp * Math.exp(-d * 6);
    x += e * noise1(t0 * 13 + d * 40);
    y += e * noise1(t0 * 7 + d * 40 + 50);
    r += e * 0.0008 * noise1(t0 * 3 + d * 30 + 99);
  }
  return [x, y, r];
}

class FrameBuffer {
  constructor() { this.canvas = document.createElement('canvas'); this.ctx = this.canvas.getContext('2d'); }
  fit() {
    const w = Math.round(RS.W * RS.px), h = Math.round(RS.H * RS.px);
    if (this.canvas.width !== w || this.canvas.height !== h) { this.canvas.width = w; this.canvas.height = h; }
  }
  draw(fn) {
    this.fit();
    const g = this.ctx;
    g.setTransform(RS.px, 0, 0, RS.px, 0, 0);
    g.clearRect(0, 0, RS.W, RS.H);
    fn(g);
  }
}
const BUFS = [new FrameBuffer(), new FrameBuffer()];

// Draws the frame at global beat `tb`; each scene gets its own clock.
function drawFrame(ctx, tb) {
  ctx.save();
  const [sx, sy, sr] = shakeAt(tb);
  ctx.translate(RS.W / 2 + sx, RS.H / 2 + sy);
  ctx.rotate(sr);
  ctx.translate(-RS.W / 2, -RS.H / 2);
  const scene = name => g => SCENES[name](g, tb - AT[name]);
  const tr = TRANSITIONS.find(T => tb >= T.a && tb < T.b);
  if (tr) {
    const A = scene(tr.from), B = scene(tr.to);
    if (tr.kind === 'peel') tPeel(ctx, tb, tr.a, tr.b, A, B, tr.corner, BUFS[0]);
    else if (tr.kind === 'iris') { const W = WHICH(); tIris(ctx, tb, tr.a, tr.b, A, B, W.x, W.y); }
    else if (tr.kind === 'whip') tWhip(ctx, tb, tr.a, tr.b, A, B, BUFS[0], BUFS[1]);
    else if (tr.kind === 'keyhole') {
      const P = chatPhone();
      tKeyhole(ctx, tb, tr.a, tr.b, A, B, [P.x, P.y], privacyCenter());
    }
  } else scene(sceneAt(tb))(ctx);
  ctx.restore();
}
