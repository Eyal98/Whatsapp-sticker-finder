// Peel-It showreel: drawing core. Everything is a pure function of time, so any frame can be drawn
// in any order (scrubbing, frame-by-frame export). Times are in beats unless a name says otherwise.
'use strict';

const TAU = Math.PI * 2;
const BPM = 144;
const BEAT = 60 / BPM;          // 0.41667 s: exactly 25 frames at 60 fps
const BEATS = 88;               // 22 bars
const DURATION = BEATS * BEAT;  // 36.7 s

// Brand colours (app/src/main/java/.../ui/Theme.kt and tools/brand/flat.py), plus a few accents.
const C = {
  violet: '#5B4BDB', violet2: '#6C5CE7', violetMid: '#4234B8', violetDeep: '#1B1164', violetInk: '#2A1C8F',
  lav: '#C7BFFF', lavBg: '#E6E1FF', lavPale: '#F3F0FF',
  pink: '#FF6FA8', pinkD: '#E0407E', pinkDD: '#B8286A', pinkPale: '#FFD6E7',
  teal: '#00B894', tealD: '#00A383', tealDD: '#00735C', tealL: '#5CDBBE', mint: '#C8F3E6',
  amber: '#F2A900', amberL: '#FFD36E', amberD: '#C98A00', cream: '#FFEBC2',
  ink: '#2D2A4A', inkD: '#1C1B22', inkDD: '#15122E',
  white: '#FFFFFF', fold: '#DDD8FA', foldLine: '#C9C2F2',
  bg: '#FBFAFF', surf: '#F0EEF8', surfLow: '#F6F4FC', surfHigh: '#EAE7F4', outline: '#79768C', outlineV: '#CAC6DA',
  red: '#FF5A5F', redD: '#D93A44', sky: '#4FC3F7', skyD: '#1E9BD7', green: '#2BCB77', greenD: '#1FA35E',
  orange: '#FF8C42', orangeD: '#E0661C', brown: '#5B3A29', skin1: '#F6C7A1', skin2: '#FFD9B8',
};

const FONT_B = 'PeelDisplay';   // DejaVu Sans Bold, loaded by the page as a FontFace
const FONT_R = 'PeelText';      // DejaVu Sans
const EMOJI = '"Noto Color Emoji","Apple Color Emoji","Segoe UI Emoji","Twemoji Mozilla",sans-serif';
const fb = size => `${size}px ${FONT_B},"DejaVu Sans",Verdana,sans-serif`;
const fr = size => `${size}px ${FONT_R},"DejaVu Sans",Verdana,sans-serif`;

// Render state, set by the composer before each frame.
const RS = { W: 1920, H: 1080, land: true, px: 1 };
const LP = (landscape, portrait) => (RS.land ? landscape : portrait);

// ---------------------------------------------------------------- math

const clamp = (x, a = 0, b = 1) => (x < a ? a : x > b ? b : x);
const lerp = (a, b, t) => a + (b - a) * t;
const seg = (t, a, b) => clamp((t - a) / (b - a));
const mix2 = (p, q, t) => [lerp(p[0], q[0], t), lerp(p[1], q[1], t)];
const ease = {
  inQuad: t => t * t,
  outQuad: t => 1 - (1 - t) * (1 - t),
  inOutQuad: t => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2),
  inCubic: t => t * t * t,
  outCubic: t => 1 - Math.pow(1 - t, 3),
  inOutCubic: t => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
  outQuint: t => 1 - Math.pow(1 - t, 5),
  inExpo: t => (t === 0 ? 0 : Math.pow(2, 10 * t - 10)),
  outExpo: t => (t === 1 ? 1 : 1 - Math.pow(2, -10 * t)),
  inOutSine: t => -(Math.cos(Math.PI * t) - 1) / 2,
  outBack: (t, s = 1.70158) => 1 + (s + 1) * Math.pow(t - 1, 3) + s * Math.pow(t - 1, 2),
  inBack: (t, s = 1.70158) => (s + 1) * t * t * t - s * t * t,
};

// Damped spring step response (0 -> 1). tb in beats, f in oscillations per beat, z = damping.
function spring(tb, f = 1.2, z = 0.36) {
  if (tb <= 0) return 0;
  const w = TAU * f, wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tb) * (Math.cos(wd * tb) + (z * w / wd) * Math.sin(wd * tb));
}
// Scale for something popping in at beat t0 (0 before, overshoots, settles at 1).
const pop = (tb, t0, f = 1.35, z = 0.38) => spring(tb - t0, f, z);
// Decaying wobble after an impact at t0, for squash & stretch. Returns ~0 when settled.
function wobble(tb, t0, amp = 0.2, f = 2.2, decay = 5) {
  const d = tb - t0;
  if (d < 0) return 0;
  return amp * Math.exp(-decay * d) * Math.cos(TAU * f * d);
}
// Impact squash: [sx, sy] for a landing at t0 (volume-preserving).
function squash(tb, t0, amp = 0.22) {
  const w = wobble(tb, t0, amp);
  return [1 + w, 1 / (1 + w)];
}

function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hash1(n) {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}
function noise1(x) {
  const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f);
  return lerp(hash1(i), hash1(i + 1), u) * 2 - 1;
}

// ---------------------------------------------------------------- paths & plates

function circleP(cx, cy, r) { const p = new Path2D(); p.arc(cx, cy, r, 0, TAU); return p; }
function ellipseP(cx, cy, rx, ry, rot = 0) { const p = new Path2D(); p.ellipse(cx, cy, rx, ry, rot, 0, TAU); return p; }
function rrP(x, y, w, h, r) { const p = new Path2D(); p.roundRect(x, y, w, h, r); return p; }
function polyP(pts, close = true) {
  const p = new Path2D();
  pts.forEach(([x, y], i) => (i ? p.lineTo(x, y) : p.moveTo(x, y)));
  if (close) p.closePath();
  return p;
}
function starP(cx, cy, r1, r2, n = 5, rot = -Math.PI / 2) {
  const pts = [];
  for (let i = 0; i < n * 2; i++) {
    const r = i % 2 ? r2 : r1, a = rot + (i * Math.PI) / n;
    pts.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]);
  }
  return polyP(pts);
}
// Rounded star (Nintendo-style) built from quadratic corners.
function roundStarP(cx, cy, r1, r2, n = 5, rot = -Math.PI / 2, round = 0.35) {
  const pts = [];
  for (let i = 0; i < n * 2; i++) {
    const r = i % 2 ? r2 : r1, a = rot + (i * Math.PI) / n;
    pts.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]);
  }
  const p = new Path2D();
  const m = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t)];
  const N = pts.length;
  for (let i = 0; i < N; i++) {
    const prev = pts[(i - 1 + N) % N], cur = pts[i], next = pts[(i + 1) % N];
    const a = m(cur, prev, round), b = m(cur, next, round);
    if (i === 0) p.moveTo(a[0], a[1]); else p.lineTo(a[0], a[1]);
    p.quadraticCurveTo(cur[0], cur[1], b[0], b[1]);
  }
  p.closePath();
  return p;
}
function sparkleP(cx, cy, r, thin = 0.28) {
  const p = new Path2D(), q = r * thin;
  p.moveTo(cx, cy - r);
  p.quadraticCurveTo(cx + q * 0.35, cy - q * 0.35, cx + r, cy);
  p.quadraticCurveTo(cx + q * 0.35, cy + q * 0.35, cx, cy + r);
  p.quadraticCurveTo(cx - q * 0.35, cy + q * 0.35, cx - r, cy);
  p.quadraticCurveTo(cx - q * 0.35, cy - q * 0.35, cx, cy - r);
  p.closePath();
  return p;
}
function heartP(cx, cy, s) {
  const p = new Path2D();
  p.moveTo(cx, cy + s * 0.9);
  p.bezierCurveTo(cx - s * 1.25, cy + s * 0.1, cx - s * 0.95, cy - s * 0.95, cx, cy - s * 0.35);
  p.bezierCurveTo(cx + s * 0.95, cy - s * 0.95, cx + s * 1.25, cy + s * 0.1, cx, cy + s * 0.9);
  p.closePath();
  return p;
}

// Die-cut sticker: a white border around `path` (a Path2D or array of them), with a hard shadow.
function dieCut(ctx, paths, { border = 10, shadow = 8, shadowColor = 'rgba(24,14,70,.26)', fill = '#fff', edge = null } = {}) {
  const list = Array.isArray(paths) ? paths : [paths];
  ctx.save();
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  if (shadow) {
    ctx.save();
    ctx.translate(0, shadow);
    ctx.fillStyle = ctx.strokeStyle = shadowColor;
    ctx.lineWidth = border * 2;
    for (const p of list) { ctx.fill(p); ctx.stroke(p); }
    ctx.restore();
  }
  ctx.fillStyle = ctx.strokeStyle = fill;
  ctx.lineWidth = border * 2;
  for (const p of list) { ctx.fill(p); ctx.stroke(p); }
  if (edge) {
    ctx.strokeStyle = edge;
    ctx.lineWidth = 1.5;
    for (const p of list) ctx.stroke(p);
  }
  ctx.restore();
}

// Hard (unblurred) shadow + fill for a Path2D.
function fillShadowed(ctx, path, fill, { dx = 0, dy = 8, color = 'rgba(24,14,70,.25)' } = {}) {
  ctx.save();
  ctx.translate(dx, dy);
  ctx.fillStyle = color;
  ctx.fill(path);
  ctx.restore();
  ctx.fillStyle = fill;
  ctx.fill(path);
}

function withT(ctx, x, y, s = 1, r = 0, fn, sx = 1, sy = 1) {
  ctx.save();
  ctx.translate(x, y);
  if (r) ctx.rotate(r);
  if (s !== 1 || sx !== 1 || sy !== 1) ctx.scale(s * sx, s * sy);
  fn(ctx);
  ctx.restore();
}

// Rectangle bigger than the frame, for backgrounds that must survive camera shake.
function bleedRect(ctx, fill) {
  ctx.fillStyle = fill;
  ctx.fillRect(-120, -120, RS.W + 240, RS.H + 240);
}

// ---------------------------------------------------------------- text

const layoutCache = new Map();
// Per-character layout that keeps the font's kerning (prefix widths) and adds tracking.
function layoutText(ctx, str, size, tracking, family, rtl) {
  const key = `${family}|${size}|${tracking}|${rtl ? 1 : 0}|${str}`;
  let L = layoutCache.get(key);
  if (L) return L;
  ctx.save();
  ctx.font = `${size}px ${family}`;
  const chars = Array.from(str);
  const out = [];
  let prefix = '';
  for (let i = 0; i < chars.length; i++) {
    const x0 = ctx.measureText(prefix).width + i * tracking * size;
    const w = ctx.measureText(chars[i]).width;
    out.push({ ch: chars[i], x: x0, w });
    prefix += chars[i];
  }
  const width = chars.length ? out[out.length - 1].x + out[out.length - 1].w : 0;
  if (rtl) for (const c of out) c.x = width - c.x - c.w;
  ctx.restore();
  L = { chars: out, width };
  layoutCache.set(key, L);
  return L;
}
function textWidth(ctx, str, size, family = FONT_B, tracking = 0.01) {
  return layoutText(ctx, str, size, tracking, family, false).width;
}

// Sticker lettering: white die-cut outline, hard shadow, extruded side and an inflated face.
// o.letter(i, n, ch) may return {s, sx, sy, dx, dy, r, a} per letter, or false to hide it.
function stext(ctx, str, x, y, o) {
  const size = o.size;
  const family = o.family || FONT_B;
  const L = layoutText(ctx, str, size, o.tracking ?? 0.012, family, !!o.rtl);
  const x0 = o.align === 'center' ? x - L.width / 2 : o.align === 'right' ? x - L.width : x;
  const outlineW = (o.outline ?? 0.2) * size;
  const inflate = (o.inflate ?? 0.045) * size;
  const ext = (o.extrude ?? 0.065) * size;
  const T = [];
  const n = L.chars.length;
  for (let i = 0; i < n; i++) {
    const c = L.chars[i];
    let st = o.letter ? o.letter(i, n, c.ch) : null;
    if (st === false) continue;
    st = st || {};
    const s = st.s ?? 1;
    if (s <= 0.002 || (st.a ?? 1) <= 0.002 || c.ch === ' ') continue;
    T.push({ ch: c.ch, cx: x0 + c.x + c.w / 2, s, sx: st.sx ?? 1, sy: st.sy ?? 1, dx: st.dx || 0, dy: st.dy || 0, r: st.r || 0, a: st.a ?? 1 });
  }
  if (!T.length) return L.width;
  const mid = size * 0.36;
  ctx.save();
  ctx.font = `${size}px ${family}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  const alpha = o.alpha ?? 1;
  const pass = fn => {
    for (const t of T) {
      ctx.save();
      ctx.globalAlpha = alpha * t.a;
      ctx.translate(t.cx + t.dx, y + t.dy - mid);
      if (t.r) ctx.rotate(t.r);
      ctx.scale(t.s * t.sx, t.s * t.sy);
      ctx.translate(0, mid);
      fn(t.ch);
      ctx.restore();
    }
  };
  if (o.shadow !== false) {
    const sh = ext + size * 0.05;
    ctx.fillStyle = ctx.strokeStyle = o.shadowColor || 'rgba(24,14,70,.3)';
    ctx.lineWidth = outlineW;
    pass(ch => { ctx.strokeText(ch, 0, sh); ctx.fillText(ch, 0, sh); });
  }
  if (outlineW > 0) {
    ctx.fillStyle = ctx.strokeStyle = o.outlineColor || '#fff';
    ctx.lineWidth = outlineW;
    pass(ch => {
      ctx.strokeText(ch, 0, 0);
      ctx.fillText(ch, 0, 0);
      if (ext > 0) { ctx.strokeText(ch, 0, ext); ctx.fillText(ch, 0, ext); }
    });
  }
  if (ext > 0 && o.shade) {
    ctx.fillStyle = ctx.strokeStyle = o.shade;
    ctx.lineWidth = inflate;
    const steps = Math.max(1, Math.ceil(ext / Math.max(1, inflate * 0.9)));
    pass(ch => {
      for (let k = steps; k >= 1; k--) {
        const oy = (ext * k) / steps;
        ctx.strokeText(ch, 0, oy);
        ctx.fillText(ch, 0, oy);
      }
    });
  }
  ctx.fillStyle = ctx.strokeStyle = o.fill;
  ctx.lineWidth = inflate;
  pass(ch => {
    if (inflate > 0) ctx.strokeText(ch, 0, 0);
    ctx.fillText(ch, 0, 0);
  });
  if (o.gloss) {
    // A thin highlight along the top of each letter.
    ctx.fillStyle = o.gloss;
    pass(ch => { ctx.save(); ctx.beginPath(); ctx.rect(-size, -size * 1.2, size * 2, size * 0.45); ctx.clip(); ctx.fillText(ch, 0, 0); ctx.restore(); });
  }
  ctx.restore();
  return L.width;
}

// Common per-letter entrance: letters pop in one after another from t0. Once settled, they give a
// small hop on every bar's first beat, rippling left to right (scenes start on bar lines, so a
// scene's own clock is in step with the music's bars).
function letterPop(tb, t0, stagger = 0.08, { f = 1.4, z = 0.36, rise = 0.5, spin = 0.25, size = 100 } = {}) {
  return i => {
    const tt = tb - t0 - i * stagger;
    if (tt <= 0) return false;
    const s = spring(tt, f, z);
    const q = tb - Math.floor(tb / 4) * 4 - i * 0.03;
    const hop = tt > 1.2 && q >= 0 && q < 0.4 ? Math.sin((Math.PI * q) / 0.4) : 0;
    return { s: s * (1 + hop * 0.02), dy: (1 - s) * size * rise - hop * size * 0.06, r: (1 - s) * spin * (i % 2 ? 1 : -1) };
  };
}

// Plain text helper.
function txt(ctx, str, x, y, { size = 24, bold = false, color = C.ink, align = 'left', base = 'alphabetic', rtl = false, alpha = 1, maxW } = {}) {
  ctx.save();
  ctx.font = bold ? fb(size) : fr(size);
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = base;
  ctx.globalAlpha *= alpha;
  if (rtl) ctx.direction = 'rtl';
  if (maxW) ctx.fillText(str, x, y, maxW); else ctx.fillText(str, x, y);
  ctx.restore();
}

function emoji(ctx, e, x, y, size) {
  ctx.save();
  ctx.font = `${size}px ${EMOJI}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(e, x, y + size * 0.06);
  ctx.restore();
}

// Pill-shaped label. Returns its width. Parts: [{text, color, rtl, emoji, bold}]
function chip(ctx, x, y, parts, o = {}) {
  const size = o.size || 34;
  const pad = o.pad ?? size * 0.62;
  const gap = size * 0.34;
  const h = o.h || size * 1.9;
  ctx.save();
  const widths = parts.map(p => {
    if (p.emoji) return size * 1.18;
    ctx.font = p.bold === false ? fr(size) : fb(size);
    if (p.rtl) ctx.direction = 'rtl'; else ctx.direction = 'ltr';
    return ctx.measureText(p.text).width;
  });
  const w = pad * 2 + widths.reduce((a, b) => a + b, 0) + gap * (parts.length - 1);
  let left = o.align === 'center' ? x - w / 2 : o.align === 'right' ? x - w : x;
  const top = y - h / 2;
  if (o.scale !== undefined || o.rot) {
    const s = o.scale ?? 1;
    if (s <= 0.002) { ctx.restore(); return w; }
    // Scale around the chip's own anchor (its left edge, centre or right edge).
    const ax = o.align === 'center' ? x : o.align === 'right' ? x : left;
    ctx.translate(ax, y);
    if (o.rot) ctx.rotate(o.rot);
    ctx.scale(s, s);
    ctx.translate(-ax, -y);
  }
  if (o.alpha !== undefined) ctx.globalAlpha *= o.alpha;
  const path = rrP(left, top, w, h, h / 2);
  if (o.border !== 0) dieCut(ctx, path, { border: o.border ?? size * 0.16, shadow: o.shadow ?? size * 0.2, fill: o.borderColor || '#fff', shadowColor: o.shadowColor });
  ctx.fillStyle = o.fill || '#fff';
  ctx.fill(path);
  if (o.stroke) { ctx.strokeStyle = o.stroke; ctx.lineWidth = 3; ctx.stroke(path); }
  let cx = left + pad;
  parts.forEach((p, i) => {
    const pw = widths[i];
    if (p.emoji) emoji(ctx, p.emoji, cx + pw / 2, y, size * 1.05);
    else if (p.icon) p.icon(ctx, cx + pw / 2, y, size);
    else {
      ctx.font = p.bold === false ? fr(size) : fb(size);
      ctx.fillStyle = p.color || o.color || C.ink;
      ctx.textBaseline = 'middle';
      if (p.rtl) { ctx.direction = 'rtl'; ctx.textAlign = 'right'; ctx.fillText(p.text, cx + pw, y + size * 0.05); }
      else { ctx.direction = 'ltr'; ctx.textAlign = 'left'; ctx.fillText(p.text, cx, y + size * 0.05); }
    }
    cx += pw + gap;
  });
  ctx.restore();
  return w;
}

// ---------------------------------------------------------------- sprite cache

const sprites = new Map();
let supportsFilter = null;
function canvasFilterOK() {
  if (supportsFilter !== null) return supportsFilter;
  try {
    const c = document.createElement('canvas').getContext('2d');
    c.filter = 'brightness(0)';
    supportsFilter = c.filter === 'brightness(0)';
  } catch (e) { supportsFilter = false; }
  return supportsFilter;
}
// A cached offscreen drawing, centred on (0,0), drawn at pixel density `RS.px * maxScale`.
function sprite(key, w, h, draw, maxScale = 1.25) {
  const dens = Math.min(4, RS.px * maxScale);
  const k = `${key}@${dens.toFixed(2)}`;
  let s = sprites.get(k);
  if (!s) {
    const c = document.createElement('canvas');
    c.width = Math.ceil(w * dens);
    c.height = Math.ceil(h * dens);
    const g = c.getContext('2d');
    g.scale(dens, dens);
    g.translate(w / 2, h / 2);
    draw(g);
    s = { c, w, h };
    sprites.set(k, s);
  }
  return s;
}
function drawSprite(ctx, s, x, y, scale = 1, rot = 0, alpha = 1, sx = 1, sy = 1) {
  if (scale <= 0.002 || alpha <= 0.002) return;
  ctx.save();
  ctx.translate(x, y);
  if (rot) ctx.rotate(rot);
  ctx.scale(scale * sx, scale * sy);
  if (alpha < 1) ctx.globalAlpha *= alpha;
  ctx.drawImage(s.c, -s.w / 2, -s.h / 2, s.w, s.h);
  ctx.restore();
}

// An emoji cut out like a sticker: white outline that follows its silhouette, hard shadow.
function emojiSticker(e, size) {
  const b = size * 0.085, pad = b * 2 + size * 0.12;
  return sprite(`emo|${e}|${size}`, size + pad * 2, size + pad * 2, g => {
    g.font = `${size}px ${EMOJI}`;
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    const y0 = size * 0.06;
    if (canvasFilterOK()) {
      const ring = (r, n) => { for (let i = 0; i < n; i++) { const a = (i / n) * TAU; g.fillText(e, Math.cos(a) * r, y0 + Math.sin(a) * r); } };
      g.save(); g.filter = 'brightness(0)'; g.globalAlpha = 0.24; g.translate(0, size * 0.07); ring(b, 16); g.fillText(e, 0, y0); g.restore();
      g.save(); g.filter = 'brightness(0) invert(1)'; ring(b, 20); ring(b * 0.5, 12); g.restore();
    } else {
      dieCut(g, circleP(0, 0, size * 0.5), { border: b, shadow: size * 0.07 });
    }
    g.fillText(e, 0, y0);
  });
}

// Lettering sticker (e.g. "LOL", "חחח"): die-cut lettering, cached.
function wordSticker(word, size, fill, shade, { rtl = false, rot = 0 } = {}) {
  const probe = document.createElement('canvas').getContext('2d');
  const w = textWidth(probe, word, size, FONT_B, 0.01) + size * 0.9;
  const h = size * 1.9;
  return sprite(`word|${word}|${size}|${fill}`, w, h, g => {
    g.rotate(rot);
    stext(g, word, 0, size * 0.32, { size, fill, shade, align: 'center', rtl, outline: 0.26, extrude: 0.07 });
  });
}

// ---------------------------------------------------------------- icons

function magnifier(ctx, x, y, s, color = C.ink, lw = 0.16) {
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = s * lw;
  ctx.lineCap = 'round';
  ctx.beginPath();
  ctx.arc(x - s * 0.08, y - s * 0.08, s * 0.3, 0, TAU);
  ctx.moveTo(x + s * 0.14, y + s * 0.14);
  ctx.lineTo(x + s * 0.38, y + s * 0.38);
  ctx.stroke();
  ctx.restore();
}
function checkMark(ctx, x, y, s, color = '#fff', lw = 0.18) {
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = s * lw;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.beginPath();
  ctx.moveTo(x - s * 0.32, y + s * 0.02);
  ctx.lineTo(x - s * 0.08, y + s * 0.26);
  ctx.lineTo(x + s * 0.36, y - s * 0.24);
  ctx.stroke();
  ctx.restore();
}
function padlock(ctx, x, y, s, { shackle = 0, body = C.amber, bodyD = C.amberD, metal = '#E9E6F5' } = {}) {
  // s = body width. shackle: 0 closed, 1 open (lifted).
  ctx.save();
  ctx.translate(x, y);
  ctx.lineCap = 'round';
  const lift = shackle * s * 0.28;
  ctx.strokeStyle = '#9C98B8';
  ctx.lineWidth = s * 0.17;
  ctx.beginPath();
  ctx.moveTo(-s * 0.27, -s * 0.12 - lift);
  ctx.lineTo(-s * 0.27, -s * 0.42 - lift);
  ctx.arc(0, -s * 0.42 - lift, s * 0.27, Math.PI, 0);
  ctx.lineTo(s * 0.27, -s * 0.12 - lift * (1 - shackle * 0.9));
  ctx.stroke();
  ctx.strokeStyle = metal;
  ctx.lineWidth = s * 0.08;
  ctx.stroke();
  const bp = rrP(-s * 0.5, -s * 0.2, s, s * 0.8, s * 0.16);
  fillShadowed(ctx, bp, body, { dy: s * 0.06, color: 'rgba(24,14,70,.25)' });
  ctx.fillStyle = bodyD;
  ctx.fill(rrP(-s * 0.5, s * 0.42, s, s * 0.18, [0, 0, s * 0.16, s * 0.16]));
  ctx.fillStyle = C.ink;
  ctx.beginPath();
  ctx.arc(0, s * 0.12, s * 0.1, 0, TAU);
  ctx.fill();
  ctx.fillRect(-s * 0.045, s * 0.12, s * 0.09, s * 0.2);
  ctx.restore();
}
function shieldP(cx, cy, s) {
  const p = new Path2D();
  p.moveTo(cx, cy - s * 0.5);
  p.bezierCurveTo(cx + s * 0.22, cy - s * 0.38, cx + s * 0.4, cy - s * 0.38, cx + s * 0.44, cy - s * 0.36);
  p.bezierCurveTo(cx + s * 0.46, cy + s * 0.05, cx + s * 0.3, cy + s * 0.34, cx, cy + s * 0.52);
  p.bezierCurveTo(cx - s * 0.3, cy + s * 0.34, cx - s * 0.46, cy + s * 0.05, cx - s * 0.44, cy - s * 0.36);
  p.bezierCurveTo(cx - s * 0.4, cy - s * 0.38, cx - s * 0.22, cy - s * 0.38, cx, cy - s * 0.5);
  p.closePath();
  return p;
}
function cloudP(cx, cy, s) {
  const p = new Path2D();
  p.moveTo(cx - s * 0.45, cy + s * 0.22);
  p.arc(cx - s * 0.3, cy + s * 0.02, s * 0.2, Math.PI * 0.5, Math.PI * 1.5);
  p.arc(cx - s * 0.05, cy - s * 0.12, s * 0.27, Math.PI * 1.05, Math.PI * 1.85);
  p.arc(cx + s * 0.27, cy + s * 0.02, s * 0.2, Math.PI * 1.35, Math.PI * 0.5);
  p.closePath();
  return p;
}

// ---------------------------------------------------------------- the elephant (tools/brand/flat.py)

const ELE = {
  tail: new Path2D('M20,50q-6,4 -5,12'),
  legBack: new Path2D('M24,58h13v24a3,3 0,0 1,-3,3h-7a3,3 0,0 1,-3,-3z'),
  legFront: new Path2D('M55,58h13v24a3,3 0,0 1,-3,3h-7a3,3 0,0 1,-3,-3z'),
  body: ellipseP(46, 52, 28, 21),
  head: circleP(72, 40, 16),
  trunk: new Path2D('M80,48C90,48 95,40 94,30C93.5,24 88,22 86,26'),
  ear: new Path2D('M63,31c-8,2 -12,12 -8,20c3,6 10,8 15,4c2,-6 1,-18 -7,-24z'),
  eyeW: circleP(77, 36, 3),
  eyeP: circleP(77.8, 36.2, 1.6),
  bowL: new Path2D('M71,24.5l-7,-5q-2,5 1,9.5z'),
  bowR: new Path2D('M71,24.5l7,-5q2,5 -1,9.5z'),
  knot: circleP(71, 24.5, 2.3),
};

// Draws the elephant in her own 100x100 box (feet at y=85). Pose fields are angles in radians.
function elephantRaw(ctx, p = {}) {
  const body = p.color || C.violet;
  ctx.save();
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  const rotAt = (px, py, a, fn) => { ctx.save(); ctx.translate(px, py); ctx.rotate(a || 0); ctx.translate(-px, -py); fn(); ctx.restore(); };
  rotAt(20, 50, p.tail, () => { ctx.strokeStyle = body; ctx.lineWidth = 3.5; ctx.stroke(ELE.tail); });
  ctx.fillStyle = body;
  rotAt(30.5, 60, p.legBack, () => ctx.fill(ELE.legBack));
  rotAt(61.5, 60, p.legFront, () => ctx.fill(ELE.legFront));
  ctx.fill(ELE.body);
  rotAt(66, 50, p.head, () => {
    ctx.fillStyle = body;
    ctx.fill(ELE.head);
    rotAt(80, 47, p.trunk, () => { ctx.strokeStyle = body; ctx.lineWidth = 9; ctx.stroke(ELE.trunk); });
    if (p.mono) return;
    rotAt(64, 32, p.ear, () => { ctx.fillStyle = p.earColor || C.pink; ctx.fill(ELE.ear); });
    const blink = p.blink || 0;
    if (blink < 0.85) {
      ctx.save();
      ctx.translate(77, 36);
      ctx.scale(1, 1 - blink);
      ctx.translate(-77, -36);
      ctx.fillStyle = '#fff';
      ctx.fill(ELE.eyeW);
      ctx.fillStyle = C.ink;
      ctx.fill(ELE.eyeP);
      ctx.restore();
    } else {
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 1.6;
      ctx.beginPath();
      ctx.moveTo(74.2, 36.6);
      ctx.quadraticCurveTo(77, 34, 79.8, 36.6);
      ctx.stroke();
    }
    if (p.blush) { ctx.fillStyle = 'rgba(255,111,168,.55)'; ctx.fill(ellipseP(80.5, 43.5, 3.2, 2)); }
    ctx.fillStyle = C.pink;
    ctx.fill(ELE.bowL);
    ctx.fill(ELE.bowR);
    ctx.fillStyle = C.pinkD;
    ctx.fill(ELE.knot);
  });
  ctx.restore();
}
// Elephant anchored at her feet (x, y). s = scale of the 100-unit box.
function elephant(ctx, x, y, s, p = {}) {
  ctx.save();
  ctx.translate(x, y);
  if (p.rot) ctx.rotate(p.rot);
  ctx.scale(s * (p.sx || 1) * (p.flip ? -1 : 1), s * (p.sy || 1));
  ctx.translate(-48, -85);
  if (p.shadow !== false) {
    ctx.fillStyle = 'rgba(20,10,60,.16)';
    ctx.fill(ellipseP(47, 86, 30 * (p.shadowScale || 1), 3.4));
  }
  elephantRaw(ctx, p);
  ctx.restore();
}

// The launcher logo: a white sticker with the elephant, bottom-right corner peeled back by `k`
// (20 in the static logo; 0 = flat; bigger = peeled further). Centred on (x, y); size = width.
function logo(ctx, x, y, size, { rot = (-8 * Math.PI) / 180, k = 20, sx = 1, sy = 1, pose = {}, shadow = true } = {}) {
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(rot);
  ctx.scale((size / 64) * sx, (size / 64) * sy);
  ctx.translate(-54, -55);
  const body = rrP(22, 24, 64, 62, 6);
  const c = 172 - k;
  // Half-planes either side of the fold line x + y = c.
  const keep = polyP([[-400, -400], [c + 400, -400], [-400, c + 400]]);
  const peeled = polyP([[c + 400, -400], [3000, -400], [3000, 3000], [-400, 3000], [-400, c + 400]]);
  if (shadow) {
    ctx.save();
    ctx.translate(0, 2.4);
    ctx.clip(keep);
    ctx.fillStyle = 'rgba(12,6,40,.24)';
    ctx.fill(body);
    ctx.restore();
  }
  ctx.save();
  ctx.clip(keep);
  ctx.fillStyle = '#fff';
  ctx.fill(body);
  ctx.save();
  ctx.translate(18, 21);
  ctx.scale(0.64, 0.64);
  elephantRaw(ctx, pose);
  ctx.restore();
  ctx.restore();
  if (k > 0.4) {
    // The flap: the peeled corner mirrored across the fold line x + y = c.
    ctx.save();
    ctx.transform(0, -1, -1, 0, c, c);
    ctx.clip(peeled);
    ctx.fillStyle = 'rgba(12,6,40,.12)';
    ctx.save(); ctx.translate(-1.2, -1.2); ctx.fill(body); ctx.restore();
    ctx.fillStyle = C.fold;
    ctx.fill(body);
    ctx.restore();
    ctx.strokeStyle = C.foldLine;
    ctx.lineWidth = 0.8;
    ctx.beginPath();
    ctx.moveTo(86, c - 86);
    ctx.lineTo(c - 86, 86);
    ctx.stroke();
  }
  ctx.restore();
}

// ---------------------------------------------------------------- phone

// Draws a phone centred on (cx, cy) and calls screen(ctx, w, h) with the origin at the screen's
// top-left corner. The screen is clipped to its rounded rectangle.
function phone(ctx, cx, cy, w, h, screen, { rot = 0, body = '#1E1C29', shadow = true } = {}) {
  ctx.save();
  ctx.translate(cx, cy);
  if (rot) ctx.rotate(rot);
  const r = w * 0.14, bz = w * 0.032;
  const outer = rrP(-w / 2, -h / 2, w, h, r);
  if (shadow) {
    ctx.save();
    ctx.translate(w * 0.03, h * 0.028);
    ctx.fillStyle = 'rgba(12,6,40,.3)';
    ctx.fill(outer);
    ctx.restore();
  }
  ctx.fillStyle = body;
  ctx.fill(outer);
  ctx.strokeStyle = 'rgba(255,255,255,.12)';
  ctx.lineWidth = 3;
  ctx.stroke(rrP(-w / 2 + 3, -h / 2 + 3, w - 6, h - 6, r - 3));
  const sw = w - bz * 2, sh = h - bz * 2;
  ctx.save();
  ctx.translate(-w / 2 + bz, -h / 2 + bz);
  ctx.clip(rrP(0, 0, sw, sh, r - bz));
  screen(ctx, sw, sh);
  ctx.restore();
  ctx.fillStyle = '#0B0A10';
  ctx.beginPath();
  ctx.arc(0, -h / 2 + bz + sw * 0.045, sw * 0.02, 0, TAU);
  ctx.fill();
  ctx.restore();
}

function statusBar(ctx, w, dark = false) {
  const col = dark ? '#fff' : C.inkD;
  txt(ctx, '9:41', 30, 34, { size: 17, bold: true, color: col });
  ctx.save();
  ctx.fillStyle = col;
  for (let i = 0; i < 4; i++) ctx.fillRect(w - 96 + i * 7, 30 - i * 3.5, 4.5, 4 + i * 3.5);
  ctx.globalAlpha = 0.9;
  ctx.strokeStyle = col;
  ctx.lineWidth = 1.6;
  ctx.stroke(rrP(w - 58, 20, 30, 14, 4));
  ctx.fillRect(w - 55, 23, 21, 8);
  ctx.fillRect(w - 27, 24.5, 2.5, 5);
  ctx.restore();
}

// ---------------------------------------------------------------- backgrounds

const patterns = new Map();
function iconTile(kind, color) {
  const size = 220;
  const dens = Math.min(3, RS.px);
  const key = `${kind}|${color}|${dens}`;
  let pat = patterns.get(key);
  if (pat) return pat;
  const c = document.createElement('canvas');
  c.width = c.height = Math.round(size * dens);
  const g = c.getContext('2d');
  g.scale(dens, dens);
  g.fillStyle = g.strokeStyle = color;
  g.lineCap = g.lineJoin = 'round';
  const at = (x, y, r, fn) => { g.save(); g.translate(x, y); g.rotate(r); fn(); g.restore(); };
  if (kind === 'icons') {
    at(40, 45, -0.2, () => g.fill(roundStarP(0, 0, 22, 10, 5)));
    at(150, 60, 0.25, () => g.fill(heartP(0, 0, 17)));
    at(95, 150, 0.1, () => g.fill(sparkleP(0, 0, 20)));
    at(190, 175, -0.15, () => { g.save(); g.scale(0.42, 0.42); g.translate(-48, -55); elephantRaw(g, { mono: true, color }); g.restore(); });
    at(30, 170, 0.3, () => g.fill(circleP(0, 0, 6)));
  } else if (kind === 'search') {
    at(55, 55, 0, () => magnifier(g, 0, 0, 44, color, 0.18));
    at(165, 165, 0, () => g.fill(sparkleP(0, 0, 16)));
    at(170, 50, 0, () => g.fill(circleP(0, 0, 6)));
    at(50, 170, 0.3, () => g.fill(roundStarP(0, 0, 14, 6, 5)));
  } else if (kind === 'dots') {
    for (let y = 0; y < 2; y++) for (let x = 0; x < 2; x++) g.fill(circleP(55 + x * 110 + (y % 2) * 55, 55 + y * 110, 9));
  } else if (kind === 'stripes') {
    g.lineWidth = 34;
    for (let i = -2; i < 4; i++) { g.beginPath(); g.moveTo(i * 110 - 20, -20); g.lineTo(i * 110 + 240, 240); g.stroke(); }
  } else if (kind === 'grid') {
    g.lineWidth = 2;
    g.strokeRect(0, 0, size, size);
    g.beginPath(); g.moveTo(size / 2, 0); g.lineTo(size / 2, size); g.moveTo(0, size / 2); g.lineTo(size, size / 2); g.globalAlpha = 0.5; g.stroke(); g.globalAlpha = 1;
    g.lineWidth = 4; g.beginPath(); g.moveTo(size / 2 - 9, size / 2); g.lineTo(size / 2 + 9, size / 2); g.moveTo(size / 2, size / 2 - 9); g.lineTo(size / 2, size / 2 + 9); g.stroke();
  } else if (kind === 'code') {
    g.font = fb(34);
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    at(55, 55, -0.15, () => g.fillText('{ }', 0, 0));
    at(165, 160, 0.12, () => g.fillText('</>', 0, 0));
    at(165, 50, 0, () => g.fill(circleP(0, 0, 6)));
    at(50, 170, 0.2, () => g.fill(sparkleP(0, 0, 15)));
  } else if (kind === 'hearts') {
    at(55, 60, -0.2, () => g.fill(heartP(0, 0, 16)));
    at(165, 165, 0.25, () => g.fill(heartP(0, 0, 12)));
    at(160, 55, 0, () => g.fill(sparkleP(0, 0, 14)));
    at(50, 170, 0, () => g.fill(circleP(0, 0, 6)));
  }
  pat = { canvas: c, size, dens };
  patterns.set(key, pat);
  return pat;
}
// Scrolling tiled pattern over the whole frame.
function patternFill(ctx, kind, color, alpha, tb, { angle = -0.35, vx = 30, vy = 18, scale = 1 } = {}) {
  const tile = iconTile(kind, color);
  const pat = ctx.createPattern(tile.canvas, 'repeat');
  const t = tb * BEAT;
  const m = new DOMMatrix()
    .rotateSelf((angle * 180) / Math.PI)
    .translateSelf((t * vx) % (tile.size * scale), (t * vy) % (tile.size * scale))
    .scaleSelf(scale / tile.dens, scale / tile.dens);
  pat.setTransform(m);
  ctx.save();
  ctx.globalAlpha *= alpha;
  ctx.fillStyle = pat;
  ctx.fillRect(-120, -120, RS.W + 240, RS.H + 240);
  ctx.restore();
}
function radialBg(ctx, inner, outer, cx = RS.W / 2, cy = RS.H / 2, r = Math.max(RS.W, RS.H) * 0.75) {
  const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
  g.addColorStop(0, inner);
  g.addColorStop(1, outer);
  bleedRect(ctx, g);
}
function linearBg(ctx, a, b, angle = 0.8) {
  const cx = RS.W / 2, cy = RS.H / 2, L = Math.hypot(RS.W, RS.H) / 2;
  const g = ctx.createLinearGradient(cx - Math.cos(angle) * L, cy - Math.sin(angle) * L, cx + Math.cos(angle) * L, cy + Math.sin(angle) * L);
  g.addColorStop(0, a);
  g.addColorStop(1, b);
  bleedRect(ctx, g);
}
// Rotating sunburst rays behind a hero element.
function sunburst(ctx, cx, cy, r, n, color, alpha, rot) {
  ctx.save();
  ctx.globalAlpha *= alpha;
  ctx.fillStyle = color;
  ctx.beginPath();
  for (let i = 0; i < n; i++) {
    const a0 = rot + (i / n) * TAU, a1 = a0 + (TAU / n) * 0.5;
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, r, a0, a1);
    ctx.closePath();
  }
  ctx.fill();
  ctx.restore();
}

// ---------------------------------------------------------------- effects

const CONFETTI = [C.pink, C.amberL, C.tealL, C.lav, '#fff', C.sky, C.orange];

// Deterministic confetti burst from (x, y) at beat t0.
function confetti(ctx, tb, t0, x, y, { n = 60, seed = 1, dir = -Math.PI / 2, spread = 1.2, speed = [700, 1400], g = 900, life = 3.2, size = 16, colors = CONFETTI } = {}) {
  const dt = tb - t0;
  if (dt <= 0 || dt > life) return;
  const R = rng(seed);
  ctx.save();
  for (let i = 0; i < n; i++) {
    const a = dir + (R() - 0.5) * spread;
    const v = lerp(speed[0], speed[1], R());
    const drag = 1.8 + R();
    const k = (1 - Math.exp(-drag * dt)) / drag;
    const px = x + Math.cos(a) * v * k + Math.sin(dt * 3 + i) * 14 * dt;
    const py = y + Math.sin(a) * v * k + g * dt * dt * 0.5;
    const fade = 1 - seg(dt, life * 0.65, life);
    const col = colors[Math.floor(R() * colors.length)];
    const shape = Math.floor(R() * 4);
    const spin = (R() - 0.5) * 18;
    const sz = size * (0.6 + R() * 0.8);
    ctx.save();
    ctx.translate(px, py);
    ctx.rotate(spin * dt + R() * TAU);
    ctx.scale(Math.cos(dt * (6 + R() * 6) + i), 1);
    ctx.globalAlpha = fade;
    ctx.fillStyle = col;
    if (shape === 0) ctx.fillRect(-sz / 2, -sz * 0.3, sz, sz * 0.6);
    else if (shape === 1) ctx.fill(circleP(0, 0, sz * 0.38));
    else if (shape === 2) ctx.fill(roundStarP(0, 0, sz * 0.6, sz * 0.28, 5));
    else ctx.fill(polyP([[0, -sz * 0.5], [sz * 0.45, sz * 0.35], [-sz * 0.45, sz * 0.35]]));
    ctx.restore();
  }
  ctx.restore();
}

// Twinkling 4-point sparkles around (x, y), starting at t0.
function sparkles(ctx, tb, t0, x, y, { n = 7, seed = 3, radius = 120, size = 26, life = 1.2, color = '#fff' } = {}) {
  const dt = tb - t0;
  if (dt <= 0 || dt > life + 0.6) return;
  const R = rng(seed);
  ctx.save();
  ctx.fillStyle = color;
  for (let i = 0; i < n; i++) {
    const a = R() * TAU, d = radius * (0.35 + R() * 0.75);
    const delay = R() * 0.35;
    const lt = seg(dt - delay, 0, life * (0.6 + R() * 0.4));
    if (lt <= 0 || lt >= 1) continue;
    const s = Math.sin(lt * Math.PI) * size * (0.6 + R() * 0.7);
    const out = 1 + lt * 0.35;
    ctx.save();
    ctx.translate(x + Math.cos(a) * d * out, y + Math.sin(a) * d * out);
    ctx.rotate(lt * 1.5);
    ctx.fill(sparkleP(0, 0, s));
    ctx.restore();
  }
  ctx.restore();
}

// Expanding ring for an impact at t0.
function ring(ctx, tb, t0, x, y, { r0 = 60, r1 = 360, life = 0.7, width = 18, color = '#fff' } = {}) {
  const dt = tb - t0;
  if (dt <= 0 || dt >= life) return;
  const p = ease.outCubic(dt / life);
  ctx.save();
  ctx.strokeStyle = color;
  ctx.globalAlpha = 1 - p;
  ctx.lineWidth = width * (1 - p) + 1;
  ctx.beginPath();
  ctx.arc(x, y, lerp(r0, r1, p), 0, TAU);
  ctx.stroke();
  ctx.restore();
}

// Radiating burst lines ("impact star") at t0.
function burstLines(ctx, tb, t0, x, y, { n = 10, r0 = 80, r1 = 240, life = 0.5, width = 10, color = '#fff', rot = 0 } = {}) {
  const dt = tb - t0;
  if (dt <= 0 || dt >= life) return;
  const p = ease.outCubic(dt / life);
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineCap = 'round';
  ctx.lineWidth = width * (1 - p * 0.7);
  ctx.globalAlpha = 1 - p * p;
  ctx.beginPath();
  for (let i = 0; i < n; i++) {
    const a = rot + (i / n) * TAU;
    const a0 = lerp(r0, r1, p * 0.6), a1 = lerp(r0, r1, Math.min(1, p * 1.2 + 0.25));
    ctx.moveTo(x + Math.cos(a) * a0, y + Math.sin(a) * a0);
    ctx.lineTo(x + Math.cos(a) * a1, y + Math.sin(a) * a1);
  }
  ctx.stroke();
  ctx.restore();
}

// Dust puffs where something lands.
function dust(ctx, tb, t0, x, y, { n = 6, spread = 90, size = 22, life = 0.8, color = 'rgba(255,255,255,.85)', seed = 9 } = {}) {
  const dt = tb - t0;
  if (dt <= 0 || dt >= life) return;
  const p = dt / life;
  const R = rng(seed);
  ctx.save();
  ctx.fillStyle = color;
  for (let i = 0; i < n; i++) {
    const side = i % 2 ? 1 : -1;
    const d = spread * (0.3 + R() * 0.7) * ease.outCubic(p);
    const r = size * (0.5 + R() * 0.6) * (1 - p) * (0.6 + p);
    ctx.globalAlpha = 1 - p;
    ctx.beginPath();
    ctx.arc(x + side * d, y - d * 0.25 * R(), Math.max(0.1, r), 0, TAU);
    ctx.fill();
  }
  ctx.restore();
}

// Speed lines across the frame (for whip pans).
function speedLines(ctx, amount, seed = 5, color = 'rgba(255,255,255,.6)', dir = 1) {
  if (amount <= 0.01) return;
  const R = rng(seed);
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineCap = 'round';
  for (let i = 0; i < 26; i++) {
    const y = R() * RS.H, len = (200 + R() * 500) * amount, x = R() * RS.W;
    ctx.globalAlpha = amount * (0.4 + R() * 0.6);
    ctx.lineWidth = 3 + R() * 8;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x - dir * len, y);
    ctx.stroke();
  }
  ctx.restore();
}
