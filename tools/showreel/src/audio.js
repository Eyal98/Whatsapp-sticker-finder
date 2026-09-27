// Peel-It showreel: the soundtrack, synthesized with the Web Audio API (no samples).
// 144 BPM in F major: a bouncy chiptune-pop groove, plus sound effects cued from the same scene
// clocks and times the picture uses (scenes.js). renderSoundtrack() returns a deterministic stereo
// AudioBuffer.
'use strict';

const NOTE = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
// 'F#4' / 'Bb2' / 'C5' -> MIDI number
function midi(name) {
  const m = /^([A-G])([#b]?)(-?\d)$/.exec(name);
  return 12 * (+m[3] + 1) + NOTE[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0);
}
const hz = n => 440 * Math.pow(2, ((typeof n === 'string' ? midi(n) : n) - 69) / 12);

// The arrangement follows the scenes (AT in scenes.js): intro under the logo, a build under
// "10,000 stickers", the groove through search, sees, people and chat, a half-time breakdown for
// privacy, a chorus under "Under the hood", and the finale.
const TRI_F = ['F4', 'A4', 'C5'], TRI_C = ['E4', 'G4', 'C5'], TRI_DM = ['F4', 'A4', 'D5'], TRI_BB = ['F4', 'Bb4', 'D5'];
// Chords by beat range: [start, end, root (bass), triad (plucks)].
const CHORDS = [
  [0, 6, 'F2', TRI_F], [6, 8, 'C2', TRI_C],
  [8, 10, 'Bb1', TRI_BB], [10, 12, 'C2', TRI_C], [12, 14, 'D2', TRI_DM], [14, 16, 'C2', TRI_C],
  [16, 20, 'F2', TRI_F], [20, 24, 'C2', TRI_C], [24, 28, 'D2', TRI_DM], [28, 32, 'Bb1', TRI_BB], [32, 34, 'F2', TRI_F], [34, 36, 'C2', TRI_C],
  [36, 40, 'D2', TRI_DM], [40, 44, 'Bb1', TRI_BB], [44, 48, 'F2', TRI_F], [48, 52, 'C2', TRI_C],
  [52, 56, 'D2', TRI_DM], [56, 58, 'Bb1', TRI_BB], [58, 60, 'C2', TRI_C],
  [60, 64, 'D2', TRI_DM], [64, 68, 'Bb1', TRI_BB], [68, 72, 'F2', TRI_F], [72, 76, 'C2', TRI_C],
  [76, 77, 'Bb1', TRI_BB], [77, 78, 'C2', TRI_C], [78, 80, 'F2', TRI_F], [80, 82, 'D2', TRI_DM], [82, 84, 'Bb1', TRI_BB], [84, 86, 'C2', TRI_C], [86, 88, 'F2', TRI_F],
];
// Lead melody: [beat, note, length in beats]
const MELODY = [
  // groove A: search, sees
  [16, 'C5', 0.5], [16.5, 'F5', 0.5], [17, 'A5', 1], [18, 'G5', 0.5], [18.5, 'F5', 0.5], [19, 'G5', 0.5], [19.5, 'A5', 0.5],
  [20, 'G5', 1], [21, 'E5', 0.5], [21.5, 'C5', 0.5], [22, 'E5', 0.5], [22.5, 'G5', 0.5], [23, 'C6', 1],
  [24, 'A5', 0.5], [24.5, 'F5', 0.5], [25, 'D6', 1], [26, 'C6', 0.5], [26.5, 'A5', 0.5], [27, 'F5', 0.5], [27.5, 'A5', 0.5],
  [28, 'Bb5', 1.5], [29.5, 'A5', 0.5], [30, 'G5', 0.5], [30.5, 'F5', 0.5], [31, 'G5', 1],
  [32, 'C6', 0.5], [32.5, 'A5', 0.5], [33, 'F5', 0.5], [33.5, 'A5', 0.5], [34, 'G5', 0.5], [34.5, 'E5', 0.5], [35, 'C5', 0.5], [35.5, 'E5', 0.5],
  // groove B: people, chat
  [36, 'D6', 1], [37, 'C6', 0.5], [37.5, 'A5', 0.5], [38, 'F5', 1], [39, 'A5', 0.5], [39.5, 'C6', 0.5],
  [40, 'D6', 0.5], [40.5, 'C6', 0.5], [41, 'Bb5', 1], [42, 'A5', 0.5], [42.5, 'G5', 0.5], [43, 'F5', 1],
  [44, 'C6', 0.5], [44.5, 'A5', 0.5], [45, 'F5', 0.5], [45.5, 'A5', 0.5], [46, 'C6', 0.75], [46.75, 'D6', 0.25], [47, 'C6', 0.5], [47.5, 'A5', 0.5],
  [48, 'G5', 1], [49, 'E5', 0.5], [49.5, 'G5', 0.5], [50, 'C6', 1], [51, 'E5', 0.5], [51.5, 'G5', 0.5],
  // chorus (the sticker slaps play the melody over 60-64, the stat bells over 68-70)
  [64, 'Bb5', 1.5], [65.5, 'A5', 0.5], [66, 'G5', 0.5], [66.5, 'F5', 0.5], [67, 'G5', 1],
  [70, 'A5', 0.5], [70.5, 'C6', 0.5], [71, 'D6', 0.5], [71.5, 'C6', 0.5],
  [72, 'C6', 0.5], [72.5, 'D6', 0.5], [73, 'E6', 1], [74, 'G6', 1.5],
  // finale
  [76, 'D6', 0.5], [76.5, 'C6', 0.5], [77, 'E6', 0.5], [77.5, 'G6', 0.5], [78, 'F6', 1.5],
  [80, 'A5', 0.5], [80.5, 'C6', 0.5], [81, 'D6', 1], [82, 'D6', 0.5], [82.5, 'C6', 0.5], [83, 'Bb5', 1],
  [84, 'A5', 0.5], [84.5, 'C6', 0.5], [85, 'D6', 0.5], [85.5, 'E6', 0.5], [86, 'F6', 2],
];
const GROOVE = [[16, 52], [60, 75]]; // full drum groove ranges

async function renderSoundtrack(sampleRate = 48000) {
  const len = Math.ceil(DURATION * sampleRate);
  const ac = new OfflineAudioContext(2, len, sampleRate);
  const T = b => b * BEAT;
  const R = rng(144);

  // ------------------------------------------------ buses
  const comp = ac.createDynamicsCompressor();
  comp.threshold.value = -18;
  comp.knee.value = 10;
  comp.ratio.value = 3.5;
  comp.attack.value = 0.004;
  comp.release.value = 0.2;
  comp.connect(ac.destination);
  const master = ac.createGain();
  master.gain.value = 0.9;
  master.connect(comp);
  const revIn = ac.createGain();
  const conv = ac.createConvolver();
  conv.buffer = impulse(ac, 1.9, 3.4);
  const revOut = ac.createGain();
  revOut.gain.value = 0.32;
  revIn.connect(conv);
  conv.connect(revOut);
  revOut.connect(master);
  const echoIn = ac.createGain();
  const echo = ac.createDelay(2);
  echo.delayTime.value = T(0.75);
  const echoFb = ac.createGain();
  echoFb.gain.value = 0.3;
  const echoLp = ac.createBiquadFilter();
  echoLp.type = 'lowpass';
  echoLp.frequency.value = 2600;
  echoIn.connect(echo);
  echo.connect(echoLp);
  echoLp.connect(echoFb);
  echoFb.connect(echo);
  const echoOut = ac.createGain();
  echoOut.gain.value = 0.22;
  echoLp.connect(echoOut);
  echoOut.connect(master);
  const bus = gain => { const g = ac.createGain(); g.gain.value = gain; g.connect(master); return g; };
  const music = bus(0.5), drums = bus(0.62), sfx = bus(0.62);

  // ------------------------------------------------ building blocks
  const noiseBuf = ac.createBuffer(1, sampleRate * 2, sampleRate);
  { const d = noiseBuf.getChannelData(0); const Rn = rng(7); for (let i = 0; i < d.length; i++) d[i] = Rn() * 2 - 1; }
  const pulse = (() => {
    const n = 48, re = new Float32Array(n), im = new Float32Array(n), duty = 0.25;
    for (let k = 1; k < n; k++) { re[k] = (2 / (k * Math.PI)) * Math.sin(k * Math.PI * duty); }
    return ac.createPeriodicWave(re, im);
  })();
  function out(node, dest, { pan = 0, send = 0, echo: eSend = 0 } = {}) {
    let n = node;
    if (pan) { const p = ac.createStereoPanner(); p.pan.value = pan; n.connect(p); n = p; }
    n.connect(dest);
    if (send) { const s = ac.createGain(); s.gain.value = send; n.connect(s); s.connect(revIn); }
    if (eSend) { const s = ac.createGain(); s.gain.value = eSend; n.connect(s); s.connect(echoIn); }
  }
  // Percussive envelope: quick attack, exponential decay.
  function perc(param, t, peak, attack, decay) {
    param.setValueAtTime(0.0001, t);
    param.linearRampToValueAtTime(peak, t + attack);
    param.exponentialRampToValueAtTime(0.0001, t + attack + decay);
  }
  function tone(dest, o) {
    const t = o.t, dur = o.dur ?? 0.2;
    const osc = ac.createOscillator();
    if (o.wave) osc.setPeriodicWave(o.wave); else osc.type = o.type || 'sine';
    osc.frequency.setValueAtTime(o.f, t);
    if (o.f2) osc.frequency.exponentialRampToValueAtTime(o.f2, t + (o.glide ?? dur));
    if (o.detune) osc.detune.value = o.detune;
    const g = ac.createGain();
    if (o.decay !== undefined) perc(g.gain, t, o.gain, o.a ?? 0.003, o.decay);
    else {
      const a = o.a ?? 0.005, peak = o.gain, sus = peak * (o.s ?? 0.7), r = o.r ?? 0.08;
      g.gain.setValueAtTime(0.0001, t);
      g.gain.linearRampToValueAtTime(peak, t + a);
      g.gain.setTargetAtTime(sus, t + a, o.d ?? 0.08);
      g.gain.setValueAtTime(sus, t + Math.max(a, dur));
      g.gain.exponentialRampToValueAtTime(0.0001, t + Math.max(a, dur) + r);
    }
    let n = osc;
    if (o.vib) {
      const lfo = ac.createOscillator(), lg = ac.createGain();
      lfo.frequency.value = o.vib[0];
      lg.gain.setValueAtTime(0, t);
      lg.gain.linearRampToValueAtTime(o.vib[1], t + (o.vib[2] ?? 0.2));
      lfo.connect(lg);
      lg.connect(osc.detune);
      lfo.start(t);
      lfo.stop(t + dur + 1);
    }
    if (o.filter) {
      const f = ac.createBiquadFilter();
      f.type = o.filter.type || 'lowpass';
      f.Q.value = o.filter.q ?? 0.7;
      f.frequency.setValueAtTime(o.filter.f, t);
      if (o.filter.f2) f.frequency.setTargetAtTime(o.filter.f2, t, o.filter.tc ?? 0.08);
      n.connect(f);
      n = f;
    }
    n.connect(g);
    out(g, dest, o);
    osc.start(t);
    osc.stop(t + (o.decay !== undefined ? (o.a ?? 0.003) + o.decay : dur + (o.r ?? 0.08)) + 0.05);
  }
  function noise(dest, o) {
    const t = o.t, dur = o.dur ?? 0.1;
    const src = ac.createBufferSource();
    src.buffer = noiseBuf;
    src.loop = true;
    const f = ac.createBiquadFilter();
    f.type = o.type || 'bandpass';
    f.frequency.setValueAtTime(o.f ?? 1000, t);
    if (o.f2) f.frequency.exponentialRampToValueAtTime(o.f2, t + (o.sweep ?? dur));
    f.Q.value = o.q ?? 0.8;
    const g = ac.createGain();
    if (o.swell) {
      g.gain.setValueAtTime(0.0001, t);
      g.gain.linearRampToValueAtTime(o.gain, t + dur * o.swell);
      g.gain.linearRampToValueAtTime(0.0001, t + dur);
    } else perc(g.gain, t, o.gain, o.a ?? 0.002, o.decay ?? dur);
    src.connect(f);
    let n = f;
    if (o.am) {
      // amplitude "ratchet" for the peeling sound
      const amg = ac.createGain();
      amg.gain.value = 0.55;
      const lfo = ac.createOscillator();
      lfo.type = 'square';
      lfo.frequency.setValueAtTime(o.am, t);
      if (o.am2) lfo.frequency.linearRampToValueAtTime(o.am2, t + dur);
      const depth = ac.createGain();
      depth.gain.value = 0.45;
      lfo.connect(depth);
      depth.connect(amg.gain);
      lfo.start(t);
      lfo.stop(t + dur + 0.1);
      f.connect(amg);
      n = amg;
    }
    n.connect(g);
    if (o.panFrom !== undefined) {
      const p = ac.createStereoPanner();
      p.pan.setValueAtTime(o.panFrom, t);
      p.pan.linearRampToValueAtTime(o.panTo, t + dur);
      g.connect(p);
      out(p, dest, { send: o.send });
    } else out(g, dest, o);
    src.start(t, R() * 1.5);
    src.stop(t + (o.swell ? dur : (o.a ?? 0.002) + (o.decay ?? dur)) + 0.05);
  }

  // ------------------------------------------------ instruments
  const kick = (b, v = 1) => {
    tone(drums, { t: T(b), f: 165, f2: 46, glide: 0.12, gain: 0.95 * v, decay: 0.42 });
    noise(drums, { t: T(b), type: 'highpass', f: 2500, gain: 0.22 * v, decay: 0.012 });
  };
  const snare = (b, v = 1) => {
    noise(drums, { t: T(b), f: 1900, q: 0.7, gain: 0.55 * v, decay: 0.17, send: 0.15 });
    tone(drums, { t: T(b), type: 'triangle', f: 190, f2: 150, gain: 0.3 * v, decay: 0.09 });
  };
  const clap = (b, v = 1) => {
    for (let k = 0; k < 3; k++) noise(drums, { t: T(b) + k * 0.011, f: 1300, q: 1.1, gain: 0.42 * v, decay: 0.012 });
    noise(drums, { t: T(b) + 0.03, f: 1250, q: 1.0, gain: 0.4 * v, decay: 0.15, send: 0.25 });
  };
  const hat = (b, v = 1, open = false) => noise(drums, { t: T(b), type: 'highpass', f: open ? 7000 : 8000, q: 0.5, gain: 0.16 * v, decay: open ? 0.22 : 0.035, pan: 0.25 });
  const shaker = (b, v = 1) => noise(drums, { t: T(b), f: 6500, q: 1.4, gain: 0.1 * v, a: 0.015, decay: 0.045, pan: -0.3 });
  const crash = (b, v = 1) => {
    noise(drums, { t: T(b), type: 'highpass', f: 4200, gain: 0.3 * v, decay: 1.6, send: 0.3 });
    noise(drums, { t: T(b), f: 7200, q: 3, gain: 0.12 * v, decay: 0.9 });
  };
  const bass = (b, n, len = 0.5, v = 1) => {
    const f = hz(n);
    for (const type of ['square', 'triangle']) {
      tone(music, { t: T(b), type, f, dur: T(len) - 0.03, gain: (type === 'square' ? 0.12 : 0.2) * v, a: 0.004, d: 0.06, s: 0.6, r: 0.05, filter: { f: 2600, f2: 650, tc: 0.07, q: 2 } });
    }
  };
  const pluck = (b, notes, v = 1) => notes.forEach((n, i) => {
    const f = hz(n), pan = (i - 1) * 0.35;
    tone(music, { t: T(b), f, gain: 0.08 * v, decay: 0.26, pan, send: 0.2 });
    tone(music, { t: T(b), f: f * 4, gain: 0.018 * v, decay: 0.07, pan });
    tone(music, { t: T(b), type: 'triangle', f, gain: 0.05 * v, decay: 0.18, pan });
  });
  const lead = (b, n, len, v = 1) => {
    const f = hz(n), dur = T(len) - 0.02;
    tone(music, { t: T(b), wave: pulse, f, dur, gain: 0.125 * v, a: 0.006, d: 0.12, s: 0.65, r: 0.09, vib: [5.6, 16, 0.18], filter: { f: 5200 }, send: 0.18, echo: 0.5 });
    tone(music, { t: T(b), wave: pulse, f, detune: 9, dur, gain: 0.045 * v, a: 0.006, d: 0.12, s: 0.6, r: 0.09, filter: { f: 3000 }, pan: 0.3 });
  };
  const bell = (b, n, v = 1, decay = 1.1, pan = 0) => {
    const f = hz(n);
    [[1, 1, 1], [2.76, 0.4, 0.6], [5.4, 0.2, 0.35], [8.93, 0.08, 0.2]].forEach(([r, g, d]) => {
      if (f * r < sampleRate * 0.4) tone(sfx, { t: T(b), f: f * r, gain: 0.09 * v * g, decay: decay * d, pan, send: 0.35 });
    });
  };
  const xylo = (b, n, v = 1) => {
    const f = hz(n);
    tone(sfx, { t: T(b), f, gain: 0.16 * v, decay: 0.32, send: 0.18 });
    if (f * 3.93 < sampleRate * 0.4) tone(sfx, { t: T(b), f: f * 3.93, gain: 0.04 * v, decay: 0.08 });
    noise(sfx, { t: T(b), f: 3000, q: 1, gain: 0.05 * v, decay: 0.01 });
  };
  const brass = (b, notes, len, v = 1) => notes.forEach((n, i) => {
    for (const det of [-7, 7]) {
      tone(music, { t: T(b), type: 'sawtooth', f: hz(n), detune: det, dur: T(len), gain: 0.05 * v, a: 0.02, d: 0.15, s: 0.7, r: 0.45, filter: { f: 700, f2: 3200, tc: 0.05, q: 1.2 }, pan: (i / (notes.length - 1) - 0.5) * 0.6, send: 0.3 });
    }
  });
  const pad = (b, notes, len, v = 1) => notes.forEach((n, i) => {
    tone(music, { t: T(b), type: 'triangle', f: hz(n), detune: i % 2 ? 6 : -6, dur: T(len), gain: 0.05 * v, a: 0.25, d: 0.3, s: 0.8, r: 0.7, filter: { f: 1800 }, pan: (i - 1) * 0.4, send: 0.45 });
  });
  const arp = (b, n, v = 1) => tone(music, { t: T(b), type: 'square', f: hz(n), gain: 0.045 * v, decay: 0.09, filter: { f: 3200 }, pan: 0.2, echo: 0.3 });

  // ------------------------------------------------ sound effects
  const slap = (b, v = 1, pitch = 1) => {
    noise(sfx, { t: T(b), f: 1700 * pitch, q: 0.8, gain: 0.7 * v, decay: 0.035 });
    tone(sfx, { t: T(b), f: 150 * pitch, f2: 58 * pitch, glide: 0.1, gain: 0.7 * v, decay: 0.14 });
    noise(sfx, { t: T(b), type: 'lowpass', f: 520, gain: 0.35 * v, decay: 0.06 });
  };
  const click = (b, v = 1) => {
    tone(sfx, { t: T(b), f: 2600, f2: 1800, glide: 0.01, gain: 0.35 * v, decay: 0.018 });
    noise(sfx, { t: T(b), type: 'highpass', f: 5000, gain: 0.25 * v, decay: 0.006 });
    tone(sfx, { t: T(b) + 0.07, f: 1800, f2: 1300, glide: 0.01, gain: 0.25 * v, decay: 0.02 });
  };
  const peel = (b, len, v = 1) => {
    const dur = T(len);
    noise(sfx, { t: T(b), f: 900, f2: 4200, sweep: dur, q: 2.2, gain: 0.5 * v, dur, swell: 0.7, am: 34, am2: 60, panFrom: 0.5, panTo: -0.3 });
    noise(sfx, { t: T(b), type: 'highpass', f: 3000, f2: 7000, sweep: dur, gain: 0.12 * v, dur, swell: 0.8 });
    tone(sfx, { t: T(b) + dur, f: 520, f2: 900, glide: 0.03, gain: 0.25 * v, decay: 0.08 });
  };
  const popS = (b, f = 800, v = 1, pan = 0) => {
    tone(sfx, { t: T(b), f: f * 0.7, f2: f * 1.4, glide: 0.035, gain: 0.34 * v, decay: 0.1, pan });
  };
  const tick = (b, f = 3000, v = 1) => {
    tone(sfx, { t: T(b), f, gain: 0.13 * v, decay: 0.02 });
    noise(sfx, { t: T(b), type: 'highpass', f: 6000, gain: 0.1 * v, decay: 0.01 });
  };
  const key = (b, v = 1) => {
    noise(sfx, { t: T(b), f: 2800, q: 1.3, gain: 0.3 * v, decay: 0.02, pan: -0.1 });
    tone(sfx, { t: T(b), f: 240, gain: 0.18 * v, decay: 0.03 });
  };
  const whoosh = (b, len, v = 1, up = true, pan = [-0.7, 0.7]) => {
    const dur = T(len);
    noise(sfx, { t: T(b), f: up ? 380 : 2600, f2: up ? 3000 : 320, sweep: dur, q: 0.9, gain: 0.45 * v, dur, swell: 0.62, panFrom: pan[0], panTo: pan[1], send: 0.1 });
  };
  const sparkle = (b, v = 1, seed = 1) => {
    const Rs = rng(seed), scale = ['F6', 'G6', 'A6', 'C7', 'D7', 'F7'];
    for (let i = 0; i < 6; i++) bell(b + i * 0.075, scale[Math.floor(Rs() * scale.length)], 0.5 * v * (1 - i * 0.12), 0.5, (Rs() - 0.5) * 1.2);
  };
  const boing = (b, f0 = 220, v = 1) => {
    const t = T(b);
    const osc = ac.createOscillator();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(f0, t);
    osc.frequency.exponentialRampToValueAtTime(f0 * 1.8, t + 0.25);
    const lfo = ac.createOscillator(), lg = ac.createGain();
    lfo.frequency.value = 16;
    lg.gain.setValueAtTime(f0 * 0.4, t);
    lg.gain.exponentialRampToValueAtTime(1, t + 0.35);
    lfo.connect(lg);
    lg.connect(osc.frequency);
    const g = ac.createGain();
    perc(g.gain, t, 0.3 * v, 0.005, 0.38);
    osc.connect(g);
    out(g, sfx, { send: 0.1 });
    osc.start(t); lfo.start(t);
    osc.stop(t + 0.45); lfo.stop(t + 0.45);
  };
  const scan = (b, len, v = 1) => {
    const t = T(b), dur = T(len);
    const osc = ac.createOscillator();
    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(160, t);
    osc.frequency.exponentialRampToValueAtTime(760, t + dur);
    const f = ac.createBiquadFilter();
    f.type = 'bandpass';
    f.Q.value = 5;
    f.frequency.setValueAtTime(700, t);
    f.frequency.exponentialRampToValueAtTime(3200, t + dur);
    const trem = ac.createGain();
    trem.gain.value = 0.5;
    const lfo = ac.createOscillator(), lg = ac.createGain();
    lfo.type = 'square';
    lfo.frequency.value = 26;
    lg.gain.value = 0.5;
    lfo.connect(lg);
    lg.connect(trem.gain);
    const g = ac.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(0.1 * v, t + 0.05);
    g.gain.setValueAtTime(0.1 * v, t + dur - 0.05);
    g.gain.linearRampToValueAtTime(0.0001, t + dur);
    osc.connect(f); f.connect(trem); trem.connect(g);
    out(g, sfx, { pan: 0.2 });
    osc.start(t); lfo.start(t); osc.stop(t + dur + 0.05); lfo.stop(t + dur + 0.05);
  };
  const clack = (b, v = 1) => {
    noise(sfx, { t: T(b), f: 3200, q: 5, gain: 0.5 * v, decay: 0.025 });
    tone(sfx, { t: T(b), f: 900, f2: 620, glide: 0.02, gain: 0.3 * v, decay: 0.03 });
    noise(sfx, { t: T(b) + 0.05, f: 2000, q: 4, gain: 0.45 * v, decay: 0.035 });
  };
  const stamp = (b, v = 1) => {
    tone(sfx, { t: T(b), f: 95, f2: 38, glide: 0.2, gain: 0.9 * v, decay: 0.32 });
    noise(sfx, { t: T(b), type: 'lowpass', f: 800, gain: 0.5 * v, decay: 0.12 });
    slap(b, 0.7 * v, 0.8);
  };
  const blips = (b, notes, v = 1, step = 0.125) => notes.forEach((n, i) =>
    tone(sfx, { t: T(b + i * step), type: 'square', f: hz(n), gain: 0.08 * v, decay: 0.07, filter: { f: 4000 } }));
  const riser = (b, len, v = 1) => {
    const dur = T(len);
    noise(sfx, { t: T(b), type: 'highpass', f: 400, f2: 7000, sweep: dur, gain: 0.22 * v, dur, swell: 0.97 });
    tone(sfx, { t: T(b), type: 'triangle', f: 220, f2: 880, glide: dur, dur: dur - 0.03, gain: 0.05 * v, a: dur * 0.9, s: 1, r: 0.03 });
  };
  const thud = (b, v = 1) => {
    tone(sfx, { t: T(b), f: 110, f2: 55, glide: 0.08, gain: 0.5 * v, decay: 0.12 });
    noise(sfx, { t: T(b), type: 'lowpass', f: 900, gain: 0.25 * v, decay: 0.08 });
  };

  // ------------------------------------------------ music
  const chordAt = b => CHORDS.find(c => b >= c[0] && b < c[1]) || CHORDS[CHORDS.length - 1];
  const inGroove = b => GROOVE.some(([a, e]) => b >= a && b < e);
  const inBreak = b => b >= AT.privacy && b < AT.hood;
  const at = (scene, t) => AT[scene] + t;
  // Bass: long notes in the intro and the breakdown, an octave bounce on 8th notes elsewhere.
  bass(0.25, midi('F2'), 1.5, 1);
  for (const [b, n] of [[4, 'F2'], [5, 'F2'], [6, 'C2'], [7, 'C2']]) bass(b, midi(n), 0.9, 0.75);
  for (let b = 8; b < 86; b += 0.5) {
    const r = midi(chordAt(b)[2]);
    if (inBreak(b)) { if (b === 52 || b === 56 || b === 58) bass(b, r, b === 52 ? 4 : 2, 0.9); continue; }
    if (b >= 75 && b < 76) continue; // a beat of air before the finale
    if (b >= 76 && b < 78) { bass(b, r + (b % 1 ? 12 : 0), 0.5, 1); continue; }
    const step = Math.round(b * 2) % 8;
    const n = step % 2 ? r + 12 : step === 6 ? r + 7 : r;
    bass(b, n, 0.5, inGroove(b) || b >= 78 ? 1 : 0.8);
  }
  bass(86, midi('F2'), 2, 1.1);
  // Offbeat plucks.
  for (let b = 4; b < 7; b++) pluck(b + 0.5, chordAt(b)[3], 0.55);
  for (let b = 16; b < 86; b++) {
    if (inBreak(b) || (b >= 75 && b < 78)) continue;
    pluck(b + 0.5, chordAt(b)[3], b >= 60 && b < 64 ? 0.7 : b >= 78 ? 0.8 : 1);
  }
  // Pads under the intro, the breakdown and the end card.
  pad(0.25, ['F3', 'A3', 'C4'], 3.6, 0.8);
  pad(4, ['F3', 'A3', 'C4'], 2, 0.6); pad(6, ['E3', 'G3', 'C4'], 1.4, 0.6);
  pad(52, ['F3', 'A3', 'D4'], 4, 1.1); pad(56, ['F3', 'Bb3', 'D4'], 2, 1.1); pad(58, ['E3', 'G3', 'C4'], 2, 1.1);
  [[78, ['F3', 'A3', 'C4']], [80, ['F3', 'A3', 'D4']], [82, ['F3', 'Bb3', 'D4']], [84, ['E3', 'G3', 'C4']]].forEach(([b, n]) => pad(b, n, 2, 0.5));
  pad(86, ['F3', 'A3', 'C4'], 2, 0.7);
  // A glockenspiel tag under the logo, and a rising arpeggio in the "10,000 stickers" build.
  [[4, 'C6'], [4.5, 'A5'], [5, 'F5'], [5.5, 'A5'], [6, 'G5'], [6.5, 'E5'], [7, 'C6']].forEach(([b, n]) => bell(b, n, 0.45, 0.8));
  for (let b = 8; b < 16; b += 0.25) {
    const tri = chordAt(b)[3].map(midi);
    const k = Math.round(b * 4) % 4;
    arp(b, (k === 3 ? tri[0] + 12 : tri[k]) + 12, 0.45 + (b - 8) * 0.07);
  }
  MELODY.forEach(([b, n, l]) => lead(b, n, l));

  // ------------------------------------------------ drums
  for (let b = 16; b < 75; b += 0.25) {
    if (!inGroove(b)) continue;
    const s16 = Math.round(b * 4) % 16;
    if ([0, 8, 10].includes(s16) || (Math.floor(b / 4) % 2 === 1 && s16 === 6)) kick(b);
    if (s16 === 4 || s16 === 12) clap(b);
    const open = s16 % 4 === 2;
    hat(b, open ? 0.9 : s16 % 2 ? 0.45 : 0.75, open);
    if ((b >= 36 && b < 52) || b >= 60) shaker(b, s16 % 2 ? 0.6 : 1);
  }
  // intro
  kick(0.25, 1.1); crash(0.25, 0.8);
  for (let b = 1; b < 4; b += 0.5) hat(b, 0.4);
  [3.5, 3.625, 3.75, 3.875].forEach((b, i) => snare(b, 0.3 + i * 0.12));
  kick(4, 0.9); kick(5.5, 0.6); kick(6, 0.85); clap(5, 0.7); clap(7, 0.7);
  for (let b = 4; b < 7.25; b += 0.5) hat(b, 0.4, b % 1 === 0.5);
  // build
  crash(8, 0.9);
  for (let b = 8; b < 16; b++) kick(b, 0.95);
  for (const b of [9, 11, 13]) clap(b);
  for (let b = 8; b < 15; b += 0.25) hat(b, 0.3 + (b - 8) * 0.06, Math.round(b * 4) % 4 === 2);
  for (let i = 0; i < 16; i++) snare(15 + i * 0.0625, 0.2 + i * 0.05);
  crash(16, 1); crash(28, 0.6); crash(36, 0.7); crash(44, 0.7);
  // breakdown (half time)
  kick(52, 1); kick(53.5, 0.7); snare(54, 1); kick(54.75, 0.6); kick(55, 0.8); kick(56, 0.9); kick(57.5, 0.6); snare(58, 0.8);
  for (let b = 52; b < 59; b += 0.5) hat(b, 0.33);
  for (let i = 0; i < 16; i++) snare(59 + i * 0.0625, 0.18 + i * 0.05);
  // chorus
  crash(60, 1); crash(68, 0.7);
  for (let i = 0; i < 12; i++) snare(74.5 + i * 0.0625, 0.25 + i * 0.06);
  // finale: kicks on the landings, then a lighter groove under the end card
  for (const b of [76.0, 76.5, 77.0]) kick(b, 0.9);
  crash(78, 1.1); kick(78, 1.2);
  for (let b = 78; b < 86; b += 0.5) {
    const s8 = Math.round(b * 2) % 8;
    if ((s8 === 0 || s8 === 4) && b !== 78) kick(b, 0.85);
    if (s8 === 2 || s8 === 6) clap(b, 0.75);
    hat(b, s8 % 2 ? 0.35 : 0.55);
  }
  kick(86, 1.2); crash(86, 1);
  for (let b = 86.5; b < 87.5; b += 0.5) hat(b, 0.25);

  // ------------------------------------------------ cues (scene clocks and times from scenes.js)
  // S1 logo sting
  click(0.2, 0.8); slap(0.25, 1.2);
  bell(0.25, 'F5', 0.8); bell(0.25, 'A5', 0.7); bell(0.25, 'C6', 0.7);
  peel(1.0, 0.45, 0.5); sparkle(1.05, 0.6, 3);
  ['C5', 'F5', 'A5', 'C6', 'F6', 'A6', 'C7'].forEach((n, i) => xylo(1.5 + i * 0.125, n, 0.9));
  sparkle(2.25, 0.35, 5); popS(2.5, 900, 0.8); bell(2.5, 'C6', 0.6);
  sparkle(4.0, 0.4, 6);
  peel(6.0, 0.5, 0.35); sparkle(6.05, 0.4, 8);
  peel(at('many', -0.625), 0.875, 1);
  // S2 10,000 stickers
  const pent = ['C6', 'D6', 'F6', 'G6', 'A6', 'C7'];
  const Rp = rng(99);
  for (let i = 0; i < RAIN_N + RAIN_LATE.length; i++) popS(at('many', rainLand(i)), hz(pent[Math.floor(Rp() * pent.length)]) * 0.5, i < RAIN_N ? 0.42 : 0.55, (Rp() - 0.5) * 1.2);
  for (let k = 0; k < 12; k++) tick(at('many', 0.25 + k * 0.25), 1800 + k * 180, 0.9);
  slap(at('many', 3.25), 1.1); crash(at('many', 3.25), 0.7);
  ['C6', 'E6', 'G6'].forEach(n => bell(at('many', 3.25), n, 0.85));
  boing(at('many', 4.0), 180, 0.8);
  popS(at('many', 4.25), 700, 0.9); blips(at('many', 4.25), ['C5', 'G5'], 0.9, 0.125);
  whoosh(at('many', 4.75), 0.15, 0.25); whoosh(at('many', 5.5), 0.15, 0.25, true, [0.7, -0.7]);
  riser(at('many', 6.0), 2.0, 0.5);
  whoosh(at('many', 7.5), 0.6, 0.7); sparkle(at('many', 7.6), 0.4, 7);
  // S3 search
  whoosh(at('search', -0.25), 0.3, 0.4);
  for (let i = 0; i < QUERY.length; i++) key(at('search', typedAt(i)), 0.9);
  PARTIAL.forEach((_, i) => popS(at('search', RESULTS_PARTIAL + i * 0.05), 700 + i * 80, 0.5));
  FINAL.forEach((_, i) => popS(at('search', RESULTS_FINAL + i * 0.05), 600 + i * 45, 0.45));
  popS(at('search', 4.25), 850, 0.7); popS(at('search', 4.5), 1000, 0.6); popS(at('search', 4.75), 1150, 0.7);
  bell(at('search', 5.0), 'G6', 1); sparkle(at('search', 5.0), 0.5, 11); popS(at('search', 5.25), 900, 0.6);
  popS(at('search', 6.0), 1200, 0.35); popS(at('search', 8.0), 1200, 0.35);
  blips(at('search', 8.0), ['G6'], 0.5); blips(at('search', 8.5), ['A6'], 0.5);
  peel(at('search', HERO_LIFT), 0.375, 0.7); popS(at('search', HERO_LIFT + 0.375), 950, 0.6); sparkle(at('search', HERO_LIFT + 0.5), 0.5, 13);
  whoosh(at('search', 11.2), 0.8, 0.9); riser(at('search', 11.25), 0.75, 0.6);
  // S4 sees the picture, reads the text
  slap(at('sees', 0), 1.1);
  scan(at('sees', 0.5), 1.4, 1);
  SEES_TAGS.forEach((tag, i) => { popS(at('sees', tag.t), 800 + i * 150, 0.7); blips(at('sees', tag.t), [['A5', 'C6', 'D6'][i]], 0.7); });
  clack(at('sees', OCR_AT), 0.8); bell(at('sees', OCR_AT), 'A6', 0.8); popS(at('sees', OCR_AT + 0.25), 1000, 0.7);
  boing(at('sees', 3.0), 260, 0.35);
  [0, 1, 2].forEach(i => popS(at('sees', 4.0 + i * 0.125), 900 + i * 120, 0.3));
  popS(at('sees', 4.5), 1100, 0.3);
  boing(at('sees', 5.0), 280, 0.3);
  whoosh(at('people', -0.5), 0.65, 1, true, [0.8, -0.8]);
  // S5 People
  FACES.forEach((_, i) => { slap(at('people', i * 0.125), 0.45, 1.2 + i * 0.05); popS(at('people', i * 0.125), 700 + i * 60, 0.4); });
  for (let i = 0; i < 6; i++) tick(at('people', 1.0 + i * 0.0625), 3600, 0.8);
  scan(at('people', 1.25), 0.4, 0.5); popS(at('people', 1.5), 900, 0.5);
  whoosh(at('people', PEOPLE_T.group), 0.35, 0.6); boing(at('people', PEOPLE_T.group), 240, 0.5);
  bell(at('people', PEOPLE_T.names), 'D6', 0.8); popS(at('people', PEOPLE_T.names), 900, 0.6);
  bell(at('people', PEOPLE_T.names + 0.25), 'F6', 0.8); popS(at('people', PEOPLE_T.names + 0.25), 1100, 0.6);
  popS(at('people', PEOPLE_T.search), 850, 0.7);
  for (let k = 0; k < 4; k++) key(at('people', PEOPLE_T.search + k * 0.0625), 0.6);
  ['D6', 'F6', 'A6'].forEach((n, i) => bell(at('people', PEOPLE_T.found + i * 0.125), n, 0.7));
  sparkle(at('people', PEOPLE_T.found), 0.5, 57);
  [0, 1, 2].forEach(i => boing(at('people', PEOPLE_T.found + i * 0.125), 260 + i * 40, 0.35));
  peel(at('chat', -0.5), 0.875, 0.9);
  // S6 keyboard -> chat
  whoosh(at('chat', -0.1), 0.3, 0.35);
  blips(at('chat', 0.25), ['E6', 'C6'], 0.7, 0.06);
  for (let i = 0; i < 4; i++) key(at('chat', CHAT_T.typed + i * 0.125), 0.8);
  whoosh(at('chat', CHAT_T.kb), 0.3, 0.35, true, [0, 0]);
  for (let i = 0; i < 4; i++) popS(at('chat', CHAT_T.kb + 0.25 + i * 0.125), 800 + i * 100, 0.45);
  tick(at('chat', CHAT_T.tap), 2400, 1); popS(at('chat', CHAT_T.tap), 700, 0.6);
  whoosh(at('chat', CHAT_T.tap), 0.5, 0.6, true, [-0.5, 0.5]);
  slap(at('chat', CHAT_T.land), 0.9); sparkle(at('chat', CHAT_T.land), 0.4, 17);
  blips(at('chat', CHAT_T.ticks), ['C6', 'G6'], 0.7, 0.06);
  blips(at('chat', CHAT_T.reply), ['G6', 'E6'], 0.7, 0.06); popS(at('chat', CHAT_T.reply), 1100, 0.4);
  blips(at('chat', CHAT_T.turtle), ['G6', 'E6'], 0.7, 0.06); slap(at('chat', CHAT_T.turtle), 0.5, 1.3); popS(at('chat', CHAT_T.turtle), 600, 0.5);
  whoosh(at('privacy', -0.5), 0.5, 0.7, false);
  // S7 privacy
  popS(at('privacy', 0), 500, 0.9); boing(at('privacy', 0), 150, 0.35);
  clack(at('privacy', 0.5), 1); popS(at('privacy', 0.75), 800, 0.6);
  tone(sfx, { t: T(at('privacy', 1.0)), f: 120, f2: 240, glide: 0.25, gain: 0.35, decay: 0.5, send: 0.3 }); sparkle(at('privacy', 1.0), 0.4, 19);
  PACKETS.forEach(([t0], i) => { whoosh(at('privacy', t0), PACKET_OUT, 0.3, true, [0, (i % 2 ? 1 : -1) * 0.6]); boing(at('privacy', t0 + PACKET_OUT), 300 + (i % 4) * 40, 0.5); });
  stamp(at('privacy', 2.0), 1.1);
  popS(at('privacy', 2.5), 900, 0.6); popS(at('privacy', 3.25), 1000, 0.6);
  clack(at('privacy', 4.0), 0.4);
  riser(at('privacy', 6.0), 1.625, 0.7);
  popS(at('privacy', SHIELD_POP), 380, 1); sparkle(at('privacy', SHIELD_POP), 0.6, 23); whoosh(at('privacy', SHIELD_POP), 0.25, 0.4);
  // S8 under the hood
  slap(at('hood', 0), 1); popS(at('hood', 0.25), 800, 0.4);
  const bombNotes = ['D5', 'F5', 'A5', 'D6', 'C6', 'A5', 'F5', 'A5', 'C6', 'D6', 'F6', 'E6', 'D6', 'A6'];
  TECH.forEach((_, i) => { slap(at('hood', TECH_T(i)), 0.6, 0.9 + i * 0.03); xylo(at('hood', TECH_T(i)), bombNotes[i], 0.55); });
  sparkle(at('hood', 5.0), 0.5, 43); riser(at('hood', 5.0), 0.9, 0.25);
  whoosh(at('hood', HOOD_SHRINK), 0.5, 0.5);
  ['F5', 'A5', 'C6', 'F6'].forEach((n, i) => { const b = at('hood', STAT_T(i)); popS(b, 800, 0.6); bell(b, n, 1); for (let k = 1; k < 4; k++) tick(b + k * 0.125, 2500 + k * 300, 0.5); });
  boing(at('hood', 12.0), 280, 0.5);
  [0, 1, 2, 3].forEach(i => popS(at('hood', 12.0 + i * 0.125), 900 + i * 100, 0.35));
  peel(at('finale', -0.75), 0.875, 1);
  // S9 finale
  [-0.5, 0, 0.5].forEach((b, i) => { boing(at('finale', b), 200 + i * 60, 0.6); thud(at('finale', b + 0.5), 0.8); });
  sparkle(at('finale', 1.25), 0.6, 29);
  whoosh(at('finale', 1.5), 0.5, 0.7); riser(at('finale', 1.5), 0.5, 0.5);
  const tada = at('finale', FIN_T.tada);
  slap(tada, 1.3); brass(tada, ['F3', 'A3', 'C4', 'F4', 'A4', 'C5'], 1.5, 1.1); bell(tada, 'F6', 1); sparkle(tada + 0.1, 0.8, 31);
  const Rc = rng(5);
  for (let i = 0; i < 14; i++) popS(tada + i * 0.045, 900 + Rc() * 900, 0.3, (Rc() - 0.5) * 1.6);
  ['C5', 'F5', 'A5', 'C6', 'F6', 'A6', 'C7'].forEach((n, i) => xylo(tada + 0.25 + i * 0.125, n, 0.8));
  boing(at('finale', FIN_T.rest), 320, 0.3); thud(at('finale', FIN_T.rest + 0.5), 0.5);
  popS(at('finale', 3.25), 900, 0.7); bell(at('finale', 3.25), 'C6', 0.6);
  ['F5', 'A5', 'C6'].forEach((n, i) => { popS(at('finale', 3.75 + i * 0.25), 800 + i * 150, 0.55); bell(at('finale', 3.75 + i * 0.25), n, 0.5); });
  bell(at('finale', 4.75), 'A5', 0.4); bell(at('finale', 5.0), 'C6', 0.4);
  peel(at('finale', 6.0), 0.5, 0.3); sparkle(at('finale', 6.05), 0.4, 33);
  boing(at('finale', FIN_T.idleHop), 300, 0.4); thud(at('finale', FIN_T.idleHop + 0.4), 0.5);
  peel(at('finale', 8.0), 0.5, 0.3);
  [0, 1, 2, 3].forEach(i => popS(at('finale', 8.0 + i * 0.125), 1000 + i * 100, 0.3));
  const fin = at('finale', FIN_T.final);
  brass(fin, ['F3', 'C4', 'F4', 'A4', 'C5', 'F5'], 1.75, 1.2); click(fin, 1); sparkle(fin + 0.05, 0.9, 37); bell(fin, 'F6', 0.9); bell(fin, 'C6', 0.7);
  sparkle(fin + 1.0, 0.35, 41);

  const buf = await ac.startRendering();
  // Normalise to -0.6 dBFS and fade the last 200 ms.
  let peak = 0;
  for (let c = 0; c < buf.numberOfChannels; c++) { const d = buf.getChannelData(c); for (let i = 0; i < d.length; i++) peak = Math.max(peak, Math.abs(d[i])); }
  const gain = peak > 0 ? 0.93 / peak : 1, fade = Math.round(0.2 * sampleRate);
  for (let c = 0; c < buf.numberOfChannels; c++) {
    const d = buf.getChannelData(c);
    for (let i = 0; i < d.length; i++) d[i] *= gain * (i > d.length - fade ? (d.length - i) / fade : 1);
  }
  return buf;
}

// Stereo reverb impulse response: decaying noise, slightly darker over time.
function impulse(ac, seconds, decay) {
  const n = Math.round(ac.sampleRate * seconds), buf = ac.createBuffer(2, n, ac.sampleRate), Rn = rng(31);
  for (let c = 0; c < 2; c++) {
    const d = buf.getChannelData(c);
    let lp = 0;
    for (let i = 0; i < n; i++) {
      const t = i / n;
      lp += (Rn() * 2 - 1 - lp) * (0.9 - t * 0.6);
      d[i] = lp * Math.pow(1 - t, decay) * (i < ac.sampleRate * 0.012 ? i / (ac.sampleRate * 0.012) : 1);
    }
  }
  return buf;
}

// 16-bit PCM WAV bytes (for ffmpeg, or to listen to the track on its own).
function wavBytes(buf) {
  const ch = buf.numberOfChannels, n = buf.length, sr = buf.sampleRate;
  const out = new DataView(new ArrayBuffer(44 + n * ch * 2));
  const str = (o, s) => { for (let i = 0; i < s.length; i++) out.setUint8(o + i, s.charCodeAt(i)); };
  str(0, 'RIFF'); out.setUint32(4, 36 + n * ch * 2, true); str(8, 'WAVE'); str(12, 'fmt ');
  out.setUint32(16, 16, true); out.setUint16(20, 1, true); out.setUint16(22, ch, true); out.setUint32(24, sr, true);
  out.setUint32(28, sr * ch * 2, true); out.setUint16(32, ch * 2, true); out.setUint16(34, 16, true); str(36, 'data');
  out.setUint32(40, n * ch * 2, true);
  const data = [];
  for (let c = 0; c < ch; c++) data.push(buf.getChannelData(c));
  let o = 44;
  for (let i = 0; i < n; i++) for (let c = 0; c < ch; c++) { out.setInt16(o, Math.max(-1, Math.min(1, data[c][i])) * 32767, true); o += 2; }
  return new Uint8Array(out.buffer);
}
