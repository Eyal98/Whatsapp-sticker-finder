// Peel-It showreel: vector sticker illustrations (flat, like the brand elephant).
'use strict';

// ---------------------------------------------------------------- alarm clock

function alarmClock(ctx, x, y, r, { shake = 0, hands = true } = {}) {
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(shake);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  // bells + hammer
  ctx.fillStyle = C.redD;
  for (const s of [-1, 1]) {
    ctx.save();
    ctx.rotate(s * 0.62);
    ctx.beginPath();
    ctx.arc(0, -r * 1.02, r * 0.34, Math.PI, 0);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }
  ctx.strokeStyle = C.inkD;
  ctx.lineWidth = r * 0.1;
  ctx.beginPath();
  ctx.moveTo(0, -r * 0.95);
  ctx.lineTo(0, -r * 1.22);
  ctx.stroke();
  // legs
  ctx.lineWidth = r * 0.13;
  ctx.beginPath();
  ctx.moveTo(-r * 0.5, r * 0.78);
  ctx.lineTo(-r * 0.72, r * 1.08);
  ctx.moveTo(r * 0.5, r * 0.78);
  ctx.lineTo(r * 0.72, r * 1.08);
  ctx.stroke();
  // body + face
  ctx.fillStyle = C.red;
  ctx.fill(circleP(0, 0, r));
  ctx.fillStyle = '#fff';
  ctx.fill(circleP(0, 0, r * 0.76));
  ctx.fillStyle = C.ink;
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * TAU;
    ctx.fill(circleP(Math.cos(a) * r * 0.62, Math.sin(a) * r * 0.62, i % 3 ? r * 0.035 : r * 0.06));
  }
  if (hands) {
    ctx.strokeStyle = C.ink;
    ctx.lineWidth = r * 0.1;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(Math.cos(-Math.PI / 2 - 0.5) * r * 0.36, Math.sin(-Math.PI / 2 - 0.5) * r * 0.36);
    ctx.stroke();
    ctx.lineWidth = r * 0.07;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(Math.cos(-Math.PI / 2 - 0.12) * r * 0.55, Math.sin(-Math.PI / 2 - 0.12) * r * 0.55);
    ctx.stroke();
    ctx.fillStyle = C.red;
    ctx.fill(circleP(0, 0, r * 0.08));
  }
  ctx.restore();
}

// ---------------------------------------------------------------- the "late" cat sticker

// Local geometry of the cat sticker (units), used for scan targets and leader lines.
const CAT = {
  face: [-12, -2], clock: [66, 40], sweat: [44, -66], text: [2, 104], textSize: 56,
  box: [-123, -132, 264, 302], // x, y, w, h: the die-cut outline plus some room
};
function catHeadP() {
  const p = new Path2D();
  p.ellipse(-12, -2, 70, 60, 0, 0, TAU);
  return p;
}
function catEarsP() {
  const p = new Path2D();
  p.moveTo(-74, -26); p.lineTo(-66, -92); p.lineTo(-26, -56); p.closePath();
  p.moveTo(2, -58); p.lineTo(40, -96); p.lineTo(52, -30); p.closePath();
  return p;
}
// The cat that is "running late" with an alarm clock, and the printed text "מאחר!" ("late!").
// Drawn centred on (x, y) at scale s. o.look: pupil offset (-1..1), o.shake: clock wobble.
function catSticker(ctx, x, y, s, o = {}) {
  ctx.save();
  ctx.translate(x, y);
  if (o.rot) ctx.rotate(o.rot);
  ctx.scale(s * (o.sx || 1), s * (o.sy || 1));
  const head = catHeadP(), ears = catEarsP();
  const paws = [ellipseP(-50, 52, 17, 12), ellipseP(24, 54, 17, 12)];
  const clockBody = circleP(CAT.clock[0], CAT.clock[1], 44);
  if (o.cut !== false) {
    dieCut(ctx, [head, ears, clockBody, circleP(CAT.clock[0], CAT.clock[1] - 36, 28), ...paws, rrP(-92, 66, 190, 72, 30)], { border: 11, shadow: 9 });
  }
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  // ears (rounded by a stroke of the same colour)
  ctx.fillStyle = ctx.strokeStyle = C.orange;
  ctx.lineWidth = 10;
  ctx.fill(ears);
  ctx.stroke(ears);
  ctx.fillStyle = C.pink;
  ctx.fill(polyP([[-64, -34], [-60, -74], [-36, -54]]));
  ctx.fill(polyP([[12, -56], [36, -80], [44, -38]]));
  // head
  ctx.fillStyle = C.orange;
  ctx.fill(head);
  ctx.strokeStyle = C.orangeD;
  ctx.lineWidth = 7;
  ctx.beginPath();
  ctx.moveTo(-26, -58); ctx.lineTo(-24, -44);
  ctx.moveTo(-12, -62); ctx.lineTo(-12, -46);
  ctx.moveTo(2, -58); ctx.lineTo(0, -44);
  ctx.stroke();
  // muzzle
  ctx.fillStyle = '#FFE3C9';
  ctx.fill(ellipseP(-12, 26, 30, 22));
  // eyes: wide, panicked, looking at the clock
  const look = o.look ?? 1;
  const blink = o.blink || 0;
  for (const ex of [-40, 16]) {
    ctx.save();
    ctx.translate(ex, -8);
    ctx.scale(1, 1 - blink * 0.9);
    ctx.fillStyle = '#fff';
    ctx.fill(ellipseP(0, 0, 17, 19));
    ctx.fillStyle = C.inkD;
    ctx.fill(circleP(5 * look, 2, 8));
    ctx.fillStyle = '#fff';
    ctx.fill(circleP(5 * look + 3, -1, 2.6));
    ctx.restore();
  }
  // worried brows
  ctx.strokeStyle = C.orangeD;
  ctx.lineWidth = 5;
  ctx.beginPath();
  ctx.moveTo(-54, -34); ctx.lineTo(-30, -40);
  ctx.moveTo(30, -34); ctx.lineTo(6, -40);
  ctx.stroke();
  // nose + open mouth
  ctx.fillStyle = C.pinkD;
  ctx.fill(polyP([[-18, 12], [-6, 12], [-12, 19]]));
  ctx.fillStyle = C.inkD;
  ctx.fill(ellipseP(-12, 34, 9, 11));
  ctx.fillStyle = C.pink;
  ctx.fill(ellipseP(-12, 40, 6, 4));
  // whiskers
  ctx.strokeStyle = 'rgba(45,42,74,.55)';
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  for (const s2 of [-1, 1]) {
    const bx = -12 + s2 * 26;
    ctx.moveTo(bx, 22); ctx.lineTo(bx + s2 * 40, 14);
    ctx.moveTo(bx, 28); ctx.lineTo(bx + s2 * 42, 30);
  }
  ctx.stroke();
  // blush
  ctx.fillStyle = 'rgba(255,111,168,.45)';
  ctx.fill(ellipseP(-52, 16, 10, 6));
  ctx.fill(ellipseP(28, 16, 10, 6));
  // sweat drop
  ctx.fillStyle = C.sky;
  const sw = new Path2D();
  sw.moveTo(44, -80);
  sw.bezierCurveTo(52, -66, 56, -60, 52, -54);
  sw.bezierCurveTo(48, -48, 38, -50, 38, -58);
  sw.bezierCurveTo(38, -64, 42, -70, 44, -80);
  ctx.fill(sw);
  ctx.fillStyle = 'rgba(255,255,255,.7)';
  ctx.fill(ellipseP(43, -60, 2.5, 4));
  // paws
  ctx.fillStyle = C.orange;
  paws.forEach(p => ctx.fill(p));
  ctx.strokeStyle = C.orangeD;
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.moveTo(-56, 48); ctx.lineTo(-56, 56); ctx.moveTo(-44, 48); ctx.lineTo(-44, 56);
  ctx.moveTo(18, 50); ctx.lineTo(18, 58); ctx.moveTo(30, 50); ctx.lineTo(30, 58);
  ctx.stroke();
  // alarm clock, ringing
  alarmClock(ctx, CAT.clock[0], CAT.clock[1], 36, { shake: o.shake || 0 });
  ctx.strokeStyle = C.redD;
  ctx.lineWidth = 4;
  for (const s2 of [-1, 1]) {
    ctx.beginPath();
    ctx.arc(CAT.clock[0], CAT.clock[1] - 6, 58, -Math.PI / 2 + s2 * 0.55 - 0.22, -Math.PI / 2 + s2 * 0.55 + 0.22);
    ctx.stroke();
  }
  // printed text
  stext(ctx, 'מאחר!', CAT.text[0], CAT.text[1] + 20, { size: CAT.textSize, fill: C.pinkD, shade: C.pinkDD, align: 'center', rtl: true, outline: 0.24, extrude: 0.07, shadow: false });
  ctx.restore();
}
// Map a point in the cat's local units to world coordinates.
function catPoint(x, y, s, rot, lx, ly) {
  const c = Math.cos(rot || 0), sn = Math.sin(rot || 0);
  return [x + (lx * c - ly * sn) * s, y + (lx * sn + ly * c) * s];
}
function catSprite() {
  return sprite('cat', 270, 290, g => catSticker(g, 0, -4, 1), 1.6);
}

// ---------------------------------------------------------------- faces for People

const PEOPLE = {
  eyal: { skin: '#EDB98A', skinD: '#D99C6C', hair: '#3A2A22', style: 'short', name: 'Eyal', heb: 'אייל' },
  noa: { skin: '#FFD7B5', skinD: '#F0B98F', hair: '#E0661C', style: 'long', name: 'Noa', heb: 'נועה' },
};
// A round cartoon face sticker of radius r (units of 100 inside).
function faceSticker(ctx, x, y, r, who, expr, o = {}) {
  const P = PEOPLE[who];
  ctx.save();
  ctx.translate(x, y);
  if (o.rot) ctx.rotate(o.rot);
  ctx.scale((r / 100) * (o.sx || 1), (r / 100) * (o.sy || 1));
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  dieCut(ctx, circleP(0, 0, 112), { border: 11, shadow: 10 });
  ctx.save();
  ctx.clip(circleP(0, 0, 112));
  ctx.fillStyle = o.bg || (who === 'eyal' ? C.mint : C.pinkPale);
  ctx.fillRect(-120, -120, 240, 240);
  // hair behind the head
  if (P.style === 'long') {
    ctx.fillStyle = P.hair;
    ctx.fill(rrP(-86, -70, 172, 200, 70));
  }
  // neck + shirt
  ctx.fillStyle = P.skinD;
  ctx.fillRect(-24, 50, 48, 50);
  ctx.fillStyle = who === 'eyal' ? C.violet : C.teal;
  ctx.fill(ellipseP(0, 128, 82, 52));
  // head
  ctx.fillStyle = P.skin;
  ctx.fill(ellipseP(0, -4, 64, 70));
  ctx.fill(ellipseP(-62, 2, 10, 15));
  ctx.fill(ellipseP(62, 2, 10, 15));
  // hair on top
  ctx.fillStyle = P.hair;
  if (P.style === 'short') {
    const h = new Path2D();
    h.moveTo(-66, -8);
    h.bezierCurveTo(-72, -70, -30, -92, 8, -86);
    h.bezierCurveTo(52, -82, 74, -52, 64, -10);
    h.bezierCurveTo(56, -34, 40, -44, 22, -46);
    h.bezierCurveTo(4, -40, -30, -52, -40, -40);
    h.bezierCurveTo(-52, -30, -58, -20, -66, -8);
    h.closePath();
    ctx.fill(h);
    // stubble
    ctx.fillStyle = 'rgba(58,42,34,.16)';
    const b = new Path2D();
    b.moveTo(-56, 12);
    b.bezierCurveTo(-50, 64, 50, 64, 56, 12);
    b.bezierCurveTo(40, 44, -40, 44, -56, 12);
    ctx.fill(b);
  } else {
    const h = new Path2D();
    h.moveTo(-70, 20);
    h.bezierCurveTo(-80, -64, -34, -90, 4, -88);
    h.bezierCurveTo(52, -86, 82, -54, 70, 20);
    h.bezierCurveTo(62, -24, 50, -38, 34, -40);
    h.quadraticCurveTo(20, -26, 8, -40);
    h.quadraticCurveTo(-8, -26, -22, -40);
    h.quadraticCurveTo(-36, -28, -46, -38);
    h.bezierCurveTo(-60, -28, -66, -8, -70, 20);
    h.closePath();
    ctx.fill(h);
  }
  // blush
  ctx.fillStyle = 'rgba(255,111,168,.35)';
  ctx.fill(ellipseP(-38, 24, 13, 8));
  ctx.fill(ellipseP(38, 24, 13, 8));
  // eyes
  ctx.fillStyle = ctx.strokeStyle = C.inkD;
  ctx.lineWidth = 6;
  const blink = o.blink || 0;
  const dotEye = (ex, ey, rr) => { ctx.save(); ctx.translate(ex, ey); ctx.scale(1, 1 - blink * 0.9); ctx.fill(circleP(0, 0, rr)); ctx.fillStyle = '#fff'; ctx.fill(circleP(rr * 0.35, -rr * 0.35, rr * 0.32)); ctx.restore(); ctx.fillStyle = C.inkD; };
  const arcEye = (ex, ey) => { ctx.beginPath(); ctx.arc(ex, ey + 6, 11, Math.PI * 1.15, Math.PI * 1.85); ctx.stroke(); };
  if (expr === 'laugh' || expr === 'crylaugh') { arcEye(-24, 0); arcEye(24, 0); }
  else if (expr === 'wink') { dotEye(-24, 0, 8); arcEye(24, 0); }
  else if (expr === 'love') { ctx.fillStyle = C.red; ctx.fill(heartP(-24, 0, 12)); ctx.fill(heartP(24, 0, 12)); ctx.fillStyle = C.inkD; }
  else if (expr === 'shock') { ctx.fillStyle = '#fff'; ctx.fill(circleP(-24, -2, 13)); ctx.fill(circleP(24, -2, 13)); ctx.fillStyle = C.inkD; ctx.fill(circleP(-24, -2, 6)); ctx.fill(circleP(24, -2, 6)); }
  else if (expr === 'cool') {
    ctx.fillStyle = C.inkD;
    ctx.fill(rrP(-50, -14, 42, 26, 10));
    ctx.fill(rrP(8, -14, 42, 26, 10));
    ctx.fillRect(-10, -8, 20, 6);
    ctx.fillStyle = 'rgba(255,255,255,.5)';
    ctx.fill(rrP(-42, -9, 14, 6, 3));
    ctx.fill(rrP(16, -9, 14, 6, 3));
    ctx.fillStyle = C.inkD;
  } else { dotEye(-24, 0, 8); dotEye(24, 0, 8); }
  // brows (not under sunglasses)
  if (expr !== 'cool') {
    ctx.strokeStyle = P.style === 'short' ? P.hair : '#B8501A';
    ctx.lineWidth = 5;
    const up = expr === 'shock' ? -8 : 0;
    ctx.beginPath();
    ctx.moveTo(-36, -22 + up); ctx.quadraticCurveTo(-24, -28 + up, -12, -22 + up);
    ctx.moveTo(12, -22 + up); ctx.quadraticCurveTo(24, -28 + up, 36, -22 + up);
    ctx.stroke();
  }
  // mouth
  ctx.fillStyle = C.inkD;
  if (expr === 'laugh' || expr === 'crylaugh' || expr === 'love') {
    const m = new Path2D();
    m.moveTo(-24, 26);
    m.quadraticCurveTo(0, 30, 24, 26);
    m.quadraticCurveTo(22, 58, 0, 58);
    m.quadraticCurveTo(-22, 58, -24, 26);
    ctx.fill(m);
    ctx.fillStyle = '#fff';
    ctx.fill(rrP(-16, 27, 32, 8, 3));
    ctx.fillStyle = C.pink;
    ctx.fill(ellipseP(0, 50, 11, 6));
  } else if (expr === 'shock') {
    ctx.fill(ellipseP(0, 40, 11, 14));
  } else if (expr === 'wink') {
    ctx.strokeStyle = C.inkD;
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.arc(0, 26, 18, 0.15 * Math.PI, 0.85 * Math.PI);
    ctx.stroke();
    ctx.fillStyle = C.pink;
    ctx.fill(ellipseP(6, 45, 8, 9));
  } else {
    ctx.strokeStyle = C.inkD;
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.arc(0, 24, 20, 0.2 * Math.PI, 0.8 * Math.PI);
    ctx.stroke();
  }
  if (expr === 'crylaugh') {
    ctx.fillStyle = C.sky;
    for (const s2 of [-1, 1]) {
      const d = new Path2D();
      d.moveTo(s2 * 38, 4);
      d.bezierCurveTo(s2 * 46, 18, s2 * 50, 26, s2 * 44, 32);
      d.bezierCurveTo(s2 * 38, 36, s2 * 32, 30, s2 * 34, 22);
      d.closePath();
      ctx.fill(d);
    }
  }
  ctx.restore();
  ctx.restore();
}
// Face landmark positions (eyes, nose, mouth corners) in the face's 100-unit space.
const FACE_MARKS = [[-24, 0], [24, 0], [0, 18], [-20, 38], [20, 38]];
function faceSprite(who, expr) {
  return sprite(`face|${who}|${expr}`, 270, 270, g => faceSticker(g, 0, -4, 100, who, expr), 1.5);
}

// ---------------------------------------------------------------- tech stickers

// Laptop-style stickers for "Under the hood". shape: pill | circle | hex | tag | bubble | shield
function techSticker(ctx, st, scale = 1) {
  ctx.save();
  ctx.scale(scale, scale);
  const size = st.size || 34;
  ctx.font = fb(size);
  const lines = st.label.split('\n');
  const tw = Math.max(...lines.map(l => ctx.measureText(l).width)) + (st.emoji ? size * 1.3 : 0);
  const lh = size * 1.18;
  const th = lines.length * lh;
  let path, w, h;
  if (st.shape === 'circle') {
    const r = Math.max(tw, th) / 2 + size * 0.7;
    w = h = r * 2;
    path = circleP(0, 0, r);
  } else if (st.shape === 'hex') {
    const r = Math.max(tw / 1.6, th) / 2 + size * 1.1;
    w = h = r * 2;
    const pts = [];
    for (let i = 0; i < 6; i++) pts.push([Math.cos((i / 6) * TAU) * r * 1.12, Math.sin((i / 6) * TAU) * r]);
    path = polyP(pts);
  } else if (st.shape === 'bubble') {
    w = tw + size * 1.6; h = th + size * 1.2;
    path = rrP(-w / 2, -h / 2, w, h, size * 0.7);
    path.addPath(polyP([[-w * 0.22, h / 2 - 2], [-w * 0.34, h / 2 + size * 0.8], [-w * 0.06, h / 2 - 2]]));
  } else if (st.shape === 'shield') {
    w = tw + size * 2.2; h = th + size * 2.6;
    path = shieldP(0, 0, Math.max(w, h * 0.95));
  } else if (st.shape === 'tag') {
    w = tw + size * 1.8; h = th + size * 1.0;
    path = polyP([[-w / 2, -h / 2], [w / 2 - h / 2, -h / 2], [w / 2, 0], [w / 2 - h / 2, h / 2], [-w / 2, h / 2]]);
  } else {
    w = tw + size * 1.5; h = th + size * 0.9;
    path = rrP(-w / 2, -h / 2, w, h, Math.min(h / 2, size * (st.round ?? 1)));
  }
  dieCut(ctx, path, { border: size * 0.2, shadow: size * 0.22 });
  if (st.grad) {
    const g = ctx.createLinearGradient(-w / 2, -h / 2, w / 2, h / 2);
    g.addColorStop(0, st.grad[0]);
    g.addColorStop(1, st.grad[1]);
    ctx.fillStyle = g;
  } else ctx.fillStyle = st.fill;
  ctx.fill(path);
  if (st.ring) { ctx.strokeStyle = st.ring; ctx.lineWidth = size * 0.12; ctx.stroke(path); }
  ctx.fillStyle = st.color || '#fff';
  ctx.textBaseline = 'middle';
  ctx.textAlign = 'center';
  const ox = st.emoji ? size * 0.65 : 0;
  lines.forEach((l, i) => ctx.fillText(l, ox, (i - (lines.length - 1) / 2) * lh + size * 0.06));
  if (st.emoji) emoji(ctx, st.emoji, ox - tw / 2 + size * 0.1, 0, size * 1.05);
  ctx.restore();
}
function techSprite(st) {
  return sprite(`tech|${st.label}|${st.shape}|${st.fill || st.grad}`, 620, 360, g => techSticker(g, st), 1.6);
}
