// Peel-It showreel: the soundtrack, synthesized with the Web Audio API (no samples).
// 144 BPM in F major: a bouncy chiptune-pop groove, plus sound effects cued to the same beat
// timings the scenes use. renderSoundtrack() returns a deterministic stereo AudioBuffer.
'use strict';

const NOTE = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
// 'F#4' / 'Bb2' / 'C5' -> MIDI number
function midi(name) {
  const m = /^([A-G])([#b]?)(-?\d)$/.exec(name);
  return 12 * (+m[3] + 1) + NOTE[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0);
}
const hz = n => 440 * Math.pow(2, ((typeof n === 'string' ? midi(n) : n) - 69) / 12);

// Chords by beat range: [start, end, root (bass), triad (plucks)].
const CHORDS = [
  [0, 4, 'F2', ['F4', 'A4', 'C5']],
  [4, 6, 'Bb1', ['Bb3', 'D4', 'F4']], [6, 8, 'C2', ['C4', 'E4', 'G4']],
  [8, 12, 'F2', ['F4', 'A4', 'C5']], [12, 16, 'C2', ['E4', 'G4', 'C5']], [16, 20, 'D2', ['F4', 'A4', 'D5']], [20, 24, 'Bb1', ['F4', 'Bb4', 'D5']],
  [24, 28, 'F2', ['F4', 'A4', 'C5']], [28, 32, 'C2', ['E4', 'G4', 'C5']],
  [32, 36, 'D2', ['F4', 'A4', 'D5']], [36, 38, 'Bb1', ['F4', 'Bb4', 'D5']], [38, 40, 'C2', ['E4', 'G4', 'C5']],
  [40, 41, 'Bb1', ['F4', 'Bb4', 'D5']], [41, 42, 'C2', ['E4', 'G4', 'C5']],
  [42, 44, 'F2', ['F4', 'A4', 'C5']], [44, 45, 'Bb1', ['F4', 'Bb4', 'D5']], [45, 46, 'C2', ['E4', 'G4', 'C5']], [46, 48, 'F2', ['F4', 'A4', 'C5']],
];
// Lead melody: [beat, note, length in beats]
const MELODY = [
  [8, 'C5', 0.5], [8.5, 'F5', 0.5], [9, 'A5', 1], [10, 'G5', 0.5], [10.5, 'F5', 0.5], [11, 'G5', 0.5], [11.5, 'A5', 0.5],
  [12, 'G5', 1], [13, 'E5', 0.5], [13.5, 'C5', 0.5], [14, 'E5', 0.5], [14.5, 'G5', 0.5], [15, 'C6', 1],
  [16, 'A5', 0.5], [16.5, 'F5', 0.5], [17, 'D6', 1], [18, 'C6', 0.5], [18.5, 'A5', 0.5], [19, 'F5', 0.5], [19.5, 'A5', 0.5],
  [20, 'Bb5', 1.5], [21.5, 'A5', 0.5], [22, 'G5', 0.5], [22.5, 'F5', 0.5], [23, 'G5', 1],
  [24, 'C6', 0.5], [24.5, 'A5', 0.5], [25, 'F5', 0.5], [25.5, 'A5', 0.5], [26, 'C6', 0.75], [26.75, 'D6', 0.25], [27, 'C6', 0.5], [27.5, 'A5', 0.5],
  [38, 'C6', 0.5], [38.5, 'D6', 0.5], [39, 'E6', 1],
  [40, 'D6', 0.5], [40.5, 'C6', 0.5], [41, 'E6', 0.5], [41.5, 'G6', 0.5], [42, 'F6', 1.5],
  [44, 'A5', 0.5], [44.5, 'C6', 0.5], [45, 'D6', 0.5], [45.5, 'E6', 0.5], [46, 'F6', 2],
];
const GROOVE = [[8, 28], [32, 40], [42, 46]]; // full drum groove ranges

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
  // Bass: octave bounce on 8th notes in the grooves, longer notes elsewhere.
  for (let b = 4; b < 46; b += 0.5) {
    const [, , root] = chordAt(b);
    const r = midi(root);
    if (b >= 28 && b < 32) { if (b % 2 === 0) bass(b, r, 2, 0.9); continue; }
    if (b >= 40 && b < 42) { bass(b, r + (b % 1 ? 12 : 0), 0.5, 1); continue; }
    const step = Math.round(b * 2) % 8;
    const n = step % 2 ? r + 12 : step === 6 ? r + 7 : r;
    bass(b, n, 0.5, inGroove(b) ? 1 : 0.8);
  }
  bass(0.25, midi('F1') + 12, 1.5, 1);
  bass(42, midi('F2'), 1, 1.1);
  bass(46, midi('F1') + 12, 2, 1.1);
  // Offbeat plucks.
  for (let b = 8; b < 46; b++) {
    if (b >= 28 && b < 32) continue;
    pluck(b + 0.5, chordAt(b)[3], b >= 32 && b < 36 ? 0.7 : 1);
  }
  // Pads under the intro and the breakdown.
  pad(0.25, ['F3', 'A3', 'C4'], 3.1, 0.8);
  pad(28, ['C4', 'E4', 'G4'], 3.8, 1.1);
  // Rising arpeggio in the "10,000 stickers" build.
  for (let b = 4; b < 8; b += 0.25) {
    const tri = chordAt(b)[3].map(midi);
    const k = Math.round(b * 4) % 4;
    arp(b, (k === 3 ? tri[0] + 12 : tri[k]) + 12, 0.6 + (b - 4) * 0.12);
  }
  MELODY.forEach(([b, n, l]) => lead(b, n, l));

  // ------------------------------------------------ drums
  for (let b = 8; b < 46; b += 0.25) {
    const s16 = Math.round(b * 4) % 16;
    if (b >= 28 && b < 32) continue;
    if (b >= 40 && b < 42) continue;
    if (!inGroove(b)) continue;
    if ([0, 8, 10].includes(s16) || (Math.floor(b / 4) % 2 === 1 && s16 === 6)) kick(b);
    if (s16 === 4 || s16 === 12) clap(b);
    const open = s16 % 4 === 2;
    hat(b, open ? 0.9 : s16 % 2 ? 0.45 : 0.75, open);
    if (b >= 32 && b < 40) shaker(b, s16 % 2 ? 0.6 : 1);
  }
  // intro + build
  kick(0.25, 1.1);
  crash(0.25, 0.8);
  for (let b = 1; b < 3.4; b += 0.5) hat(b, 0.4);
  [3.5, 3.625, 3.75, 3.875].forEach((b, i) => snare(b, 0.35 + i * 0.15));
  for (let b = 4; b < 8; b++) kick(b, 0.95);
  clap(5); clap(7);
  for (let b = 4; b < 7.5; b += 0.25) hat(b, 0.3 + (b - 4) * 0.12, Math.round(b * 4) % 4 === 2);
  for (let i = 0; i < 8; i++) snare(7.5 + i * 0.0625, 0.25 + i * 0.09);
  crash(8, 0.9);
  // breakdown (half time)
  kick(28, 1); kick(29.5, 0.7); snare(30, 1); kick(30.75, 0.6); kick(31, 0.8);
  for (let b = 28; b < 31.5; b += 0.5) hat(b, 0.35);
  crash(32, 1);
  for (let i = 0; i < 8; i++) snare(39.0 + i * 0.0625, 0.25 + i * 0.09);
  // finale
  crash(42, 1.1); kick(42, 1.2);
  for (const b of [40.0, 40.5, 41.0]) kick(b, 0.9);
  kick(46, 1.2); crash(46, 1);
  for (let b = 46.5; b < 47.5; b += 0.5) hat(b, 0.25);

  // ------------------------------------------------ cues (see scenes.js for the matching visuals)
  // S1 logo sting
  slap(0.25, 1.2); click(0.2, 0.8);
  bell(0.25, 'F5', 0.8); bell(0.25, 'A5', 0.7); bell(0.25, 'C6', 0.7);
  peel(1.0, 0.45, 0.5); sparkle(1.05, 0.6, 3);
  ['C5', 'F5', 'A5', 'C6', 'F6', 'A6', 'C7'].forEach((n, i) => xylo(1.5 + i * 0.125, n, 0.9));
  popS(2.5, 900, 0.8); bell(2.5, 'C6', 0.6); sparkle(2.25, 0.35, 5);
  peel(3.375, 0.85, 1);
  // S2 10,000 stickers
  const pent = ['C6', 'D6', 'F6', 'G6', 'A6', 'C7'];
  const Rp = rng(99);
  for (let i = 0; i < RAIN_N; i++) popS(rainLand(i), hz(pent[Math.floor(Rp() * pent.length)]) * 0.5, 0.42, (Rp() - 0.5) * 1.2);
  for (let k = 0; k < 8; k++) tick(4.25 + k * 0.25, 2000 + k * 260, 0.9);
  slap(6.25, 1.1); crash(6.25, 0.7); bell(6.25, 'C6', 0.9); bell(6.25, 'E6', 0.8); bell(6.25, 'G6', 0.8);
  boing(6.5, 180, 0.8);
  popS(6.75, 700, 0.9); blips(6.75, ['C5', 'G5'], 0.9, 0.125);
  whoosh(7.0, 0.15, 0.25); whoosh(7.25, 0.15, 0.25, true, [0.7, -0.7]);
  whoosh(7.5, 0.6, 0.7); sparkle(7.6, 0.4, 7); riser(7.0, 1.0, 0.5);
  // S3 search
  whoosh(7.75, 0.3, 0.4);
  for (let i = 0; i < QUERY.length; i++) key(typedAt(i), 0.9);
  PARTIAL.forEach((_, i) => popS(9.75 + i * 0.05, 700 + i * 80, 0.5));
  FINAL.forEach((_, i) => popS(12.0 + i * 0.05, 600 + i * 45, 0.45));
  popS(12.25, 850, 0.7); popS(12.5, 1000, 0.6); popS(12.75, 1150, 0.7);
  bell(13.0, 'G6', 1); sparkle(13.0, 0.5, 11); popS(13.25, 900, 0.6);
  peel(14.0, 0.375, 0.7); popS(14.375, 950, 0.6); sparkle(14.5, 0.5, 13);
  whoosh(15.2, 0.8, 0.9); riser(15.25, 0.75, 0.6);
  // S4 sees the picture
  slap(16, 1.1); crash(16, 0.6);
  scan(16.5, 1.4, 1);
  [17.0, 17.25, 17.5].forEach((b, i) => { popS(b, 800 + i * 150, 0.7); blips(b, [['A5', 'C6', 'D6'][i]], 0.7); });
  clack(18.0, 0.8); bell(18.0, 'A6', 0.8); popS(18.25, 1000, 0.7);
  boing(19.0, 260, 0.35);
  whoosh(19.5, 0.65, 1, true, [0.8, -0.8]);
  // S5 People
  FACES.forEach((_, i) => { slap(20 + i * 0.125, 0.45, 1.2 + i * 0.05); popS(20 + i * 0.125, 700 + i * 60, 0.4); });
  for (let i = 0; i < 6; i++) tick(21 + i * 0.0625, 3600, 0.8);
  scan(21.25, 0.4, 0.5); popS(21.5, 900, 0.5);
  whoosh(22.0, 0.35, 0.6); boing(22.0, 240, 0.5);
  bell(22.5, 'D6', 0.8); popS(22.5, 900, 0.6); bell(22.75, 'F6', 0.8); popS(22.75, 1100, 0.6);
  peel(23.5, 0.875, 0.9);
  // S6 keyboard -> chat
  whoosh(23.9, 0.3, 0.35);
  blips(24.25, ['E6', 'C6'], 0.7, 0.06);
  for (let i = 0; i < 4; i++) key(24.5 + i * 0.125, 0.8);
  whoosh(25.0, 0.3, 0.35, true, [0, 0]);
  for (let i = 0; i < 4; i++) popS(25.25 + i * 0.125, 800 + i * 100, 0.45);
  tick(26.0, 2400, 1); popS(26.0, 700, 0.6);
  whoosh(26.0, 0.5, 0.6, true, [-0.5, 0.5]);
  slap(26.5, 0.9); sparkle(26.5, 0.4, 17);
  blips(26.75, ['C6', 'G6'], 0.7, 0.06);
  blips(27.0, ['G6', 'E6'], 0.7, 0.06); popS(27.0, 1100, 0.4);
  whoosh(27.5, 0.5, 0.7, false); boing(28.0, 150, 0.4);
  // S7 privacy
  popS(28.0, 500, 0.9); clack(28.5, 1); popS(28.75, 800, 0.6);
  tone(sfx, { t: T(29), f: 120, f2: 240, glide: 0.25, gain: 0.35, decay: 0.5, send: 0.3 }); sparkle(29.0, 0.4, 19);
  [29.25, 29.75, 30.25, 30.75].forEach((b, i) => { whoosh(b, 0.375, 0.3, true, [0, (i % 2 ? 1 : -1) * 0.6]); boing(b + 0.375, 300 + i * 40, 0.55); });
  stamp(30.0, 1.1);
  popS(30.5, 900, 0.6);
  riser(31.0, 0.75, 0.7);
  popS(31.75, 380, 1); sparkle(31.75, 0.6, 23); whoosh(31.75, 0.25, 0.4);
  // S8 under the hood
  slap(32, 1); popS(32.25, 800, 0.4);
  const bombNotes = ['D5', 'F5', 'A5', 'D6', 'C6', 'A5', 'F5', 'A5', 'C6', 'D6', 'F6', 'E6', 'D6', 'A6'];
  TECH.forEach((_, i) => { slap(TECH_T(i), 0.6, 0.9 + i * 0.03); xylo(TECH_T(i), bombNotes[i], 0.55); });
  whoosh(36.0, 0.5, 0.5);
  ['F5', 'Bb5', 'D6', 'F6'].forEach((n, i) => { popS(STAT_T(i), 800, 0.6); bell(STAT_T(i), n, 1); for (let k = 1; k < 4; k++) tick(STAT_T(i) + k * 0.125, 2500 + k * 300, 0.5); });
  boing(38.5, 280, 0.5);
  peel(39.25, 0.875, 1);
  // S9 finale
  [39.5, 40.0, 40.5].forEach((b, i) => { boing(b, 200 + i * 60, 0.6); thud(b + 0.5, 0.8); });
  sparkle(41.25, 0.6, 29);
  whoosh(41.5, 0.5, 0.7); riser(41.5, 0.5, 0.5);
  slap(42, 1.3); brass(42, ['F3', 'A3', 'C4', 'F4', 'A4', 'C5'], 1.5, 1.1); bell(42, 'F6', 1); sparkle(42.1, 0.8, 31);
  const Rc = rng(5);
  for (let i = 0; i < 14; i++) popS(42 + i * 0.045, 900 + Rc() * 900, 0.3, (Rc() - 0.5) * 1.6);
  ['C5', 'F5', 'A5', 'C6', 'F6', 'A6', 'C7'].forEach((n, i) => xylo(42.25 + i * 0.125, n, 0.8));
  popS(43.25, 900, 0.7); bell(43.25, 'C6', 0.6);
  ['F5', 'A5', 'C6'].forEach((n, i) => { popS(43.75 + i * 0.25, 800 + i * 150, 0.55); bell(43.75 + i * 0.25, n, 0.5); });
  bell(44.75, 'A5', 0.4); bell(45.0, 'C6', 0.4);
  brass(46, ['F3', 'C4', 'F4', 'A4', 'C5', 'F5'], 1.75, 1.2); click(46, 1); sparkle(46.05, 0.9, 37); bell(46, 'F6', 0.9); bell(46, 'C6', 0.7);
  sparkle(47.0, 0.35, 41);

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
