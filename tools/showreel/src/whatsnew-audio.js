// Soundtrack of the "What's new" reel: the showreel's instruments (audio.js), a new arrangement
// (144 BPM, F major, 12 bars) and sound effects cued from the same event times the picture uses
// (whatsnew.js: WN, I_, F_, B_, S_, E_). Handed to renderSoundtrack() as `arrange`.
'use strict';

// Chords by beat range: [start, end, root (bass), triad (plucks)]. The melody is the showreel's
// "groove A" and "groove B" moved to start at beat 8, so every chord lines up.
const WN_CHORDS = [
  [0, 6, 'F2', ['F4', 'A4', 'C5']], [6, 8, 'C2', ['E4', 'G4', 'C5']],
  [8, 12, 'F2', ['F4', 'A4', 'C5']], [12, 16, 'C2', ['E4', 'G4', 'C5']], [16, 20, 'D2', ['F4', 'A4', 'D5']], [20, 24, 'Bb1', ['F4', 'Bb4', 'D5']],
  [24, 26, 'F2', ['F4', 'A4', 'C5']], [26, 28, 'C2', ['E4', 'G4', 'C5']],
  [28, 32, 'D2', ['F4', 'A4', 'D5']], [32, 36, 'Bb1', ['F4', 'Bb4', 'D5']], [36, 40, 'F2', ['F4', 'A4', 'C5']], [40, 44, 'C2', ['E4', 'G4', 'C5']],
  [44, 48, 'F2', ['F4', 'A4', 'C5']],
];
const WN_MELODY = MELODY.filter(m => m[0] >= 16 && m[0] < 52).map(m => [m[0] - 8, m[1], m[2]]).concat([
  [44, 'C6', 0.5], [44.5, 'E6', 0.5], [45, 'G6', 0.5], [45.5, 'A6', 0.5], [46, 'F6', 2],
]);

function arrangeWhatsNew(k) {
  const { T, R, midi } = k;
  const chordAt = b => WN_CHORDS.find(c => b >= c[0] && b < c[1]) || WN_CHORDS[WN_CHORDS.length - 1];
  const at = (scene, t) => WN.AT[scene] + t;
  const groove = b => b >= 8 && b < 44;

  // ---- music
  k.bass(0.25, midi('F2'), 1.5, 1);
  for (const [b, n] of [[4, 'F2'], [5, 'F2'], [6, 'C2'], [7, 'C2']]) k.bass(b, midi(n), 0.9, 0.75);
  for (let b = 8; b < 47; b += 0.5) {
    const r = midi(chordAt(b)[2]);
    const step = Math.round(b * 2) % 8;
    k.bass(b, step % 2 ? r + 12 : step === 6 ? r + 7 : r, 0.5, groove(b) ? 1 : 0.8);
  }
  k.bass(47, midi('F2'), 1, 1.1);
  for (let b = 4; b < 47; b++) k.pluck(b + 0.5, chordAt(b)[3], b < 8 ? 0.55 : 1);
  k.pad(0.25, ['F3', 'A3', 'C4'], 3.6, 0.8);
  k.pad(44, ['F3', 'A3', 'C4'], 4, 0.7);
  // a rising arpeggio under the first folders scene, into the groove
  for (let b = 6; b < 8; b += 0.25) {
    const tri = chordAt(b)[3].map(midi), i = Math.round(b * 4) % 4;
    k.arp(b, (i === 3 ? tri[0] + 12 : tri[i]) + 12, 0.45 + (b - 6) * 0.2);
  }
  WN_MELODY.forEach(([b, n, l]) => k.lead(b, n, l));

  // ---- drums
  for (let b = 8; b < 44; b += 0.25) {
    const s16 = Math.round(b * 4) % 16;
    if ([0, 8, 10].includes(s16) || (Math.floor(b / 4) % 2 === 1 && s16 === 6)) k.kick(b);
    if (s16 === 4 || s16 === 12) k.clap(b);
    const open = s16 % 4 === 2;
    k.hat(b, open ? 0.9 : s16 % 2 ? 0.45 : 0.75, open);
    if (b >= 24) k.shaker(b, s16 % 2 ? 0.6 : 1);
  }
  for (let b = 4; b < 8; b += 0.5) k.hat(b, 0.4 + (b - 4) * 0.1);
  k.kick(0.25, 1.1); k.crash(0.25, 0.8);
  k.kick(44, 1.1); k.crash(44, 0.9);

  // ---- 1. title
  k.slap(at('intro', I_.logo), 1.3);
  k.peel(at('intro', 1.0), 0.5, 0.5);
  'What\'s new'.replace(' ', '').split('').forEach((_, i) => k.key(at('intro', I_.title + i * 0.09), 0.7));
  k.popS(at('intro', I_.chip), 900, 0.7); k.bell(at('intro', I_.chip), 'C6', 0.6);
  k.sparkle(at('intro', 1.05), 0.5, 3);
  [['C6', 4.0], ['A5', 4.5], ['F5', 5.0]].forEach(([n, b]) => k.bell(at('intro', b), n, 0.4, 0.8));
  k.peel(WN.LEAVE.intro, 0.875, 0.8);
  k.riser(WN.LEAVE.intro - 0.5, 1, 0.5);

  // ---- 2. folders
  const F = b => at('folders', b);
  k.whoosh(F(-0.25), 0.5, 0.6);
  k.slap(F(F_.title + 0.3), 0.8); 'Folders'.split('').forEach((_, i) => k.key(F(F_.title + i * 0.06), 0.5));
  k.popS(F(F_.step1), 800, 0.7);
  k.click(F(F_.tap), 0.9);
  k.popS(F(F_.chip), 1000, 0.8); k.bell(F(F_.chip), 'F6', 0.7);
  F_.fly.forEach((b, i) => { k.whoosh(F(b), 0.45, 0.4, true, [-0.2, 0.3]); k.bell(F(b + 0.4), ['C6', 'E6', 'G6'][i], 0.8, 0.9); k.thud(F(b + 0.4), 0.35); });
  k.popS(F(F_.step2), 850, 0.7);
  k.click(F(F_.select), 0.9); k.sparkle(F(F_.select + 0.1), 0.4, 7);
  k.whoosh(F(F_.kb), 0.6, 0.6, true, [-0.5, 0.5]); k.popS(F(F_.step3), 900, 0.7);
  F_MEMBERS_SFX(k, F);
  k.click(F(F_.send), 1); k.whoosh(F(F_.send), 0.4, 0.5, true, [0, 0.4]);
  k.slap(F(F_.send + 0.4), 0.8); k.sparkle(F(F_.send + 0.45), 0.7, 9); k.bell(F(F_.send + 1.2), 'C7', 0.4);
  k.whoosh(WN.LEAVE.folders - 0.25, 0.75, 0.6);

  // ---- 3. back up & restore
  const B = b => at('backup', b);
  k.slap(B(B_.title + 0.3), 0.9); 'Back up'.replace(' ', '').split('').forEach((_, i) => k.key(B(B_.title + i * 0.06), 0.5));
  B_.step.forEach(b => k.popS(B(b), 850, 0.7));
  B_.tick.forEach((b, i) => { k.tick(B(b), 3400, 1); k.xylo(B(b + 0.05), ['C5', 'E5', 'G5', 'C6'][i], 0.8); });
  k.click(B(B_.lock), 1); k.thud(B(B_.lock + 0.3), 0.7);
  for (let i = 0; i < 7; i++) k.tick(B(B_.lock - 0.5 + i * 0.12 + 0.5), 2600, 0.5);
  k.click(B(B_.save), 1);
  k.whoosh(B(B_.fly), B_.land - B_.fly, 0.8, true, [-0.7, 0.7]);
  k.thud(B(B_.land), 1); k.stamp(B(B_.land), 0.6); k.sparkle(B(B_.land + 0.05), 0.7, 11);
  for (let i = 0; i < 9; i++) k.blips(B(B_.restore + 0.3 + i * 0.25), [['C5', 'E5', 'G5'][i % 3]], 0.7);
  k.brass(B(B_.done), ['F3', 'A3', 'C4', 'F4', 'A4', 'C5'], 1.5, 1.0); k.bell(B(B_.done), 'F6', 0.9); k.sparkle(B(B_.done + 0.05), 0.8, 13);
  for (let i = 0; i < 4; i++) k.xylo(B(B_.rows + i * 0.2), ['C5', 'E5', 'G5', 'C6'][i], 0.7);
  k.popS(B(B_.folders), 1000, 0.7);
  k.peel(WN.LEAVE.backup, 0.875, 0.8);

  // ---- 4. getting started
  const S = b => at('start', b);
  k.slap(S(S_.title + 0.3), 0.9); 'Getting'.split('').forEach((_, i) => k.key(S(S_.title + i * 0.06), 0.5));
  k.boing(S(0.25), 260, 0.6); k.thud(S(0.75), 0.7);
  k.popS(S(S_.bubble1), 900, 0.8); k.popS(S(1.0), 1000, 0.6); k.popS(S(S_.bubble2), 800, 0.8); k.bell(S(S_.bubble2), 'A5', 0.5);
  S_.tick.forEach((b, i) => { k.xylo(S(b + 0.1), ['C5', 'D5', 'E5', 'G5', 'A5', 'C6'][i], 0.9); k.tick(S(b), 3200, 0.9); });
  k.slap(S(S_.done), 1.2); k.brass(S(S_.done), ['F3', 'A3', 'C4', 'F4', 'A4', 'C5'], 1.5, 1.1); k.bell(S(S_.done), 'F6', 1); k.sparkle(S(S_.done + 0.1), 0.8, 15);
  const Rc = R;
  for (let i = 0; i < 12; i++) k.popS(S(S_.done + i * 0.045), 900 + Rc() * 900, 0.3, (Rc() - 0.5) * 1.6);
  ['C5', 'F5', 'A5', 'C6', 'F6', 'A6'].forEach((n, i) => k.xylo(S(S_.done + 0.25 + i * 0.125), n, 0.7));
  k.whoosh(WN.LEAVE.start - 0.25, 0.75, 0.6); k.riser(WN.LEAVE.start - 0.5, 0.75, 0.5);

  // ---- 5. end card
  const E = b => at('end', b);
  const fin = E(E_.logo);
  k.stamp(fin, 1.2); k.brass(fin, ['F3', 'C4', 'F4', 'A4', 'C5', 'F5'], 1.75, 1.2); k.bell(fin, 'F6', 0.9); k.sparkle(fin + 0.1, 0.9, 17);
  for (let i = 0; i < 12; i++) k.popS(fin + i * 0.045, 900 + R() * 900, 0.3, (R() - 0.5) * 1.6);
  'Peel-It'.replace('-', '').split('').forEach((_, i) => k.key(E(E_.word + i * 0.12), 0.7));
  k.popS(E(E_.chip), 900, 0.8); k.bell(E(E_.chip), 'C6', 0.7); k.bell(E(E_.chip + 0.25), 'F6', 0.6);
  k.sparkle(E(E_.chip + 0.05), 0.5, 19);
  k.peel(E(4.0), 0.5, 0.3);
  k.bell(E(4.75), 'A5', 0.4); k.bell(E(5.0), 'C6', 0.4);
}

// The folders' keyboard scene: the stickers pop in one by one.
function F_MEMBERS_SFX(k, F) {
  [0, 1, 2].forEach(i => k.popS(F(F_.sticker + i * 0.2), 900 + i * 120, 0.5));
}
