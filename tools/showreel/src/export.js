// Peel-It showreel: frame-exact export with WebCodecs (VP9 video, Opus audio) into a WebM file.
// No realtime capture: every frame is drawn, encoded and timestamped in order, so the result is
// identical from run to run and never drops frames.
'use strict';

// ---------------------------------------------------------------- EBML / WebM writer

const utf8 = s => new TextEncoder().encode(s);
function concat(parts) {
  let n = 0;
  for (const p of parts) n += p.length;
  const out = new Uint8Array(n);
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}
function idBytes(id) {
  const out = [];
  while (id > 0) { out.unshift(id % 256); id = Math.floor(id / 256); }
  return Uint8Array.from(out);
}
function vint(n, width = 0) {
  let len = width;
  if (!len) { len = 1; while (n >= Math.pow(2, 7 * len) - 1) len++; }
  const out = new Uint8Array(len);
  let v = n;
  for (let i = len - 1; i >= 0; i--) { out[i] = v % 256; v = Math.floor(v / 256); }
  out[0] |= 1 << (8 - len);
  return out;
}
function uintBytes(n, width = 0) {
  const out = [];
  let v = n;
  do { out.unshift(v % 256); v = Math.floor(v / 256); } while (v > 0);
  while (out.length < width) out.unshift(0);
  return Uint8Array.from(out);
}
function floatBytes(x) {
  const b = new Uint8Array(8);
  new DataView(b.buffer).setFloat64(0, x);
  return b;
}
const E = (id, payload) => {
  const body = Array.isArray(payload) ? concat(payload) : payload;
  return concat([idBytes(id), vint(body.length), body]);
};
const EU = (id, n, width) => E(id, uintBytes(n, width));
const EF = (id, x) => E(id, floatBytes(x));
const ES = (id, s) => E(id, utf8(s));

const ID = {
  EBML: 0x1a45dfa3, EBMLVersion: 0x4286, EBMLReadVersion: 0x42f7, EBMLMaxIDLength: 0x42f2, EBMLMaxSizeLength: 0x42f3,
  DocType: 0x4282, DocTypeVersion: 0x4287, DocTypeReadVersion: 0x4285,
  Segment: 0x18538067, SeekHead: 0x114d9b74, Seek: 0x4dbb, SeekID: 0x53ab, SeekPosition: 0x53ac,
  Info: 0x1549a966, TimestampScale: 0x2ad7b1, Duration: 0x4489, MuxingApp: 0x4d80, WritingApp: 0x5741, Title: 0x7ba9,
  Tracks: 0x1654ae6b, TrackEntry: 0xae, TrackNumber: 0xd7, TrackUID: 0x73c5, TrackType: 0x83, FlagLacing: 0x9c,
  Language: 0x22b59c, CodecID: 0x86, CodecPrivate: 0x63a2, CodecDelay: 0x56aa, SeekPreRoll: 0x56bb, DefaultDuration: 0x23e383,
  Video: 0xe0, PixelWidth: 0xb0, PixelHeight: 0xba, Colour: 0x55b0, MatrixCoefficients: 0x55b1, Range: 0x55b9,
  TransferCharacteristics: 0x55ba, Primaries: 0x55bb,
  Audio: 0xe1, SamplingFrequency: 0xb5, Channels: 0x9f,
  Cluster: 0x1f43b675, Timestamp: 0xe7, SimpleBlock: 0xa3,
  Cues: 0x1c53bb6b, CuePoint: 0xbb, CueTime: 0xb3, CueTrackPositions: 0xb7, CueTrack: 0xf7, CueClusterPosition: 0xf1,
};

function colourElement(cs) {
  if (!cs) return null;
  const matrix = { rgb: 0, bt709: 1, bt470bg: 5, smpte170m: 6, 'bt2020-ncl': 9 }[cs.matrix];
  const transfer = { bt709: 1, smpte170m: 6, 'iec61966-2-1': 13, linear: 8, pq: 16, hlg: 18 }[cs.transfer];
  const prim = { bt709: 1, bt470bg: 5, smpte170m: 6, bt2020: 9, smpte432: 12 }[cs.primaries];
  const kids = [];
  if (matrix !== undefined) kids.push(EU(ID.MatrixCoefficients, matrix));
  if (cs.fullRange !== null && cs.fullRange !== undefined) kids.push(EU(ID.Range, cs.fullRange ? 2 : 1));
  if (transfer !== undefined) kids.push(EU(ID.TransferCharacteristics, transfer));
  if (prim !== undefined) kids.push(EU(ID.Primaries, prim));
  return kids.length ? E(ID.Colour, kids) : null;
}
function opusHead(channels, preSkip, rate) {
  const b = new Uint8Array(19), d = new DataView(b.buffer);
  b.set(utf8('OpusHead'), 0);
  d.setUint8(8, 1);
  d.setUint8(9, channels);
  d.setUint16(10, preSkip, true);
  d.setUint32(12, rate, true);
  d.setInt16(16, 0, true);
  d.setUint8(18, 0);
  return b;
}

// video: {chunks:[{key, ts(us), data}], width, height, fps, colorSpace}
// audio: {chunks:[{ts(us), data}], sampleRate, channels, head (OpusHead bytes)} or null
function muxWebM({ video, audio, durationMs, title }) {
  const header = E(ID.EBML, [
    EU(ID.EBMLVersion, 1), EU(ID.EBMLReadVersion, 1), EU(ID.EBMLMaxIDLength, 4), EU(ID.EBMLMaxSizeLength, 8),
    ES(ID.DocType, 'webm'), EU(ID.DocTypeVersion, 4), EU(ID.DocTypeReadVersion, 2),
  ]);
  const info = E(ID.Info, [
    EU(ID.TimestampScale, 1000000), EF(ID.Duration, durationMs), ES(ID.Title, title || ''),
    ES(ID.MuxingApp, 'peel-it-showreel'), ES(ID.WritingApp, 'peel-it-showreel (WebCodecs)'),
  ]);
  const videoKids = [EU(ID.PixelWidth, video.width), EU(ID.PixelHeight, video.height)];
  const colour = colourElement(video.colorSpace);
  if (colour) videoKids.push(colour);
  const tracks = [E(ID.TrackEntry, [
    EU(ID.TrackNumber, 1), EU(ID.TrackUID, 0x5e11), EU(ID.TrackType, 1), EU(ID.FlagLacing, 0), ES(ID.Language, 'und'),
    ES(ID.CodecID, 'V_VP9'), EU(ID.DefaultDuration, Math.round(1e9 / video.fps)), E(ID.Video, videoKids),
  ])];
  if (audio) {
    const preSkip = new DataView(audio.head.buffer, audio.head.byteOffset).getUint16(10, true);
    tracks.push(E(ID.TrackEntry, [
      EU(ID.TrackNumber, 2), EU(ID.TrackUID, 0xa11d), EU(ID.TrackType, 2), EU(ID.FlagLacing, 0), ES(ID.Language, 'und'),
      ES(ID.CodecID, 'A_OPUS'), E(ID.CodecPrivate, audio.head), EU(ID.CodecDelay, Math.round((preSkip / 48000) * 1e9)),
      EU(ID.SeekPreRoll, 80000000), E(ID.Audio, [EF(ID.SamplingFrequency, audio.sampleRate), EU(ID.Channels, audio.channels)]),
    ]));
  }
  const tracksEl = E(ID.Tracks, tracks);
  // Blocks in timestamp order; a new cluster at every video keyframe.
  const blocks = video.chunks.map(c => ({ track: 1, ts: Math.round(c.ts / 1000), key: c.key, data: c.data }));
  if (audio) for (const c of audio.chunks) blocks.push({ track: 2, ts: Math.max(0, Math.round(c.ts / 1000)), key: true, data: c.data });
  blocks.sort((a, b) => a.ts - b.ts || a.track - b.track);
  const clusters = [];
  let cur = null;
  for (const b of blocks) {
    if (!cur || (b.track === 1 && b.key && b.ts !== cur.ts) || b.ts - cur.ts > 30000) {
      cur = { ts: b.ts, blocks: [] };
      clusters.push(cur);
    }
    cur.blocks.push(b);
  }
  const clusterBytes = clusters.map(cl => E(ID.Cluster, [EU(ID.Timestamp, cl.ts), ...cl.blocks.map(b => {
    const head = new Uint8Array(4);
    head[0] = 0x80 | b.track;
    new DataView(head.buffer).setInt16(1, b.ts - cl.ts);
    head[3] = b.key ? 0x80 : 0;
    return E(ID.SimpleBlock, concat([head, b.data]));
  })]));
  // SeekHead with fixed-width positions, so its size doesn't depend on the positions.
  const seekHead = pos => E(ID.SeekHead, [
    E(ID.Seek, [E(ID.SeekID, idBytes(ID.Info)), EU(ID.SeekPosition, pos.info, 8)]),
    E(ID.Seek, [E(ID.SeekID, idBytes(ID.Tracks)), EU(ID.SeekPosition, pos.tracks, 8)]),
    E(ID.Seek, [E(ID.SeekID, idBytes(ID.Cues)), EU(ID.SeekPosition, pos.cues, 8)]),
  ]);
  const shLen = seekHead({ info: 0, tracks: 0, cues: 0 }).length;
  const pos = { info: shLen, tracks: shLen + info.length };
  let p = pos.tracks + tracksEl.length;
  const clusterPos = clusterBytes.map(c => { const at = p; p += c.length; return at; });
  pos.cues = p;
  const cues = E(ID.Cues, clusters.map((cl, i) => E(ID.CuePoint, [
    EU(ID.CueTime, cl.ts), E(ID.CueTrackPositions, [EU(ID.CueTrack, 1), EU(ID.CueClusterPosition, clusterPos[i])]),
  ])));
  const segment = E(ID.Segment, [seekHead(pos), info, tracksEl, ...clusterBytes, cues]);
  return concat([header, segment]);
}

// ---------------------------------------------------------------- encoders

async function encodeVideo({ canvas, frames, fps, drawFrameAt, quality = 'high', onProgress }) {
  const width = canvas.width, height = canvas.height;
  const chunks = [];
  let config = null;
  let failure = null;
  const enc = new VideoEncoder({
    output: (chunk, meta) => {
      const data = new Uint8Array(chunk.byteLength);
      chunk.copyTo(data);
      chunks.push({ key: chunk.type === 'key', ts: chunk.timestamp, data });
      if (meta && meta.decoderConfig) config = meta.decoderConfig;
    },
    error: e => { failure = e; },
  });
  const base = { codec: 'vp09.00.41.08', width, height, framerate: fps, latencyMode: 'quality' };
  // Constant quality where the browser supports it; otherwise a generous variable bitrate.
  const q = quality === 'web' ? 34 : 24;
  const qcfg = { ...base, bitrateMode: 'quantizer' };
  let useQ = false;
  try { useQ = (await VideoEncoder.isConfigSupported(qcfg)).supported; } catch (e) { useQ = false; }
  enc.configure(useQ ? qcfg : { ...base, bitrate: quality === 'web' ? 5e6 : 14e6, bitrateMode: 'variable' });
  for (let i = 0; i < frames; i++) {
    if (failure) throw failure;
    drawFrameAt(i);
    const vf = new VideoFrame(canvas, { timestamp: Math.round((i * 1e6) / fps), duration: Math.round(1e6 / fps) });
    const opts = { keyFrame: i % fps === 0 };
    if (useQ) opts.vp9 = { quantizer: q };
    enc.encode(vf, opts);
    vf.close();
    while (enc.encodeQueueSize > 6) await new Promise(r => setTimeout(r, 1));
    if (onProgress && i % 30 === 0) onProgress(i / frames);
  }
  await enc.flush();
  if (failure) throw failure;
  enc.close();
  chunks.sort((a, b) => a.ts - b.ts);
  return { chunks, width, height, fps, colorSpace: config && config.colorSpace, mode: useQ ? `vp9 q${q}` : 'vp9 vbr' };
}

async function encodeAudio(buffer, bitrate = 192000) {
  const chunks = [];
  let config = null;
  let failure = null;
  const enc = new AudioEncoder({
    output: (chunk, meta) => {
      const data = new Uint8Array(chunk.byteLength);
      chunk.copyTo(data);
      chunks.push({ ts: chunk.timestamp, data });
      if (meta && meta.decoderConfig) config = meta.decoderConfig;
    },
    error: e => { failure = e; },
  });
  const sr = buffer.sampleRate, ch = buffer.numberOfChannels;
  enc.configure({ codec: 'opus', sampleRate: sr, numberOfChannels: ch, bitrate });
  const block = 4800;
  for (let off = 0; off < buffer.length; off += block) {
    const n = Math.min(block, buffer.length - off);
    const data = new Float32Array(n * ch);
    for (let c = 0; c < ch; c++) data.set(buffer.getChannelData(c).subarray(off, off + n), c * n);
    const ad = new AudioData({ format: 'f32-planar', sampleRate: sr, numberOfFrames: n, numberOfChannels: ch, timestamp: Math.round((off * 1e6) / sr), data });
    enc.encode(ad);
    ad.close();
  }
  await enc.flush();
  if (failure) throw failure;
  enc.close();
  let head = config && config.description ? new Uint8Array(config.description.buffer || config.description) : null;
  if (!head || head.length < 19 || new TextDecoder().decode(head.subarray(0, 8)) !== 'OpusHead') head = opusHead(ch, 312, sr);
  chunks.sort((a, b) => a.ts - b.ts);
  return { chunks, sampleRate: sr, channels: ch, head };
}
