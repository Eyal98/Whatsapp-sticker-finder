// Fitting animated stickers to WhatsApp's rules, which it checks when an animated sticker is
// inserted from a keyboard: 512 x 512, at most 500 KB, frames of at least 8 ms, 10 s in all.
// Stickers other people send don't have to follow them and are saved in the library as they are,
// so some can't be sent from a keyboard as they are ("Couldn't share" / "Can't send this file").
//
// Each attempt decodes the sticker frame by frame and encodes it again, lossy: on a 512 x 512
// canvas (scaled to fit and centered, if it's another size), with the timing kept but sped up to
// 10 s if longer and no frame under 8 ms, and the loop count and EXIF / XMP chunks (the sticker's
// pack) kept. Attempts go from lighter to heavier: lower quality first, then fewer frames (a
// dropped frame's time goes to the frame before it). Frames are streamed, never all held at once.

#include "sticker_shrink.h"

#include <stdlib.h>
#include <string.h>

#include "src/webp/demux.h"
#include "src/webp/encode.h"
#include "src/webp/mux.h"

typedef struct {
  float quality;
  int step;  // Keep every step-th frame.
} Attempt;

static const Attempt kAttempts[] = {
    {75.f, 1}, {60.f, 1}, {45.f, 1}, {60.f, 2}, {40.f, 2}, {30.f, 3}, {25.f, 4}, {20.f, 6}, {20.f, 10}, {20.f, 16},
};

int sticker_info(const uint8_t* in, size_t in_size, StickerInfo* info) {
  memset(info, 0, sizeof(*info));
  const WebPData data = {in, in_size};
  WebPDemuxer* demux = WebPDemux(&data);
  if (demux == NULL) return 0;
  info->width = (int)WebPDemuxGetI(demux, WEBP_FF_CANVAS_WIDTH);
  info->height = (int)WebPDemuxGetI(demux, WEBP_FF_CANVAS_HEIGHT);
  info->loop_count = (int)WebPDemuxGetI(demux, WEBP_FF_LOOP_COUNT);
  const uint32_t flags = WebPDemuxGetI(demux, WEBP_FF_FORMAT_FLAGS);
  info->animated = (flags & ANIMATION_FLAG) != 0;
  info->has_exif = (flags & EXIF_FLAG) != 0;
  info->has_xmp = (flags & XMP_FLAG) != 0;
  info->has_iccp = (flags & ICCP_FLAG) != 0;
  WebPIterator iter;
  if (WebPDemuxGetFrame(demux, 1, &iter)) {
    info->min_frame_ms = iter.duration;
    do {
      info->frames++;
      info->total_ms += iter.duration;
      if (iter.duration < info->min_frame_ms) info->min_frame_ms = iter.duration;
      if (iter.duration > info->max_frame_ms) info->max_frame_ms = iter.duration;
    } while (WebPDemuxNextFrame(&iter));
    WebPDemuxReleaseIterator(&iter);
  }
  WebPDemuxDelete(demux);
  return 1;
}

int sticker_needs_fitting(const StickerInfo* info, size_t size, size_t max_bytes) {
  return size > max_bytes || info->width != STICKER_SIZE || info->height != STICKER_SIZE ||
         info->total_ms > STICKER_MAX_TOTAL_MS || info->min_frame_ms < STICKER_MIN_FRAME_MS;
}

// Each frame's duration, from the file.
static int* Durations(const WebPData* in, int* count) {
  *count = 0;
  WebPDemuxer* demux = WebPDemux(in);
  if (demux == NULL) return NULL;
  const int frames = (int)WebPDemuxGetI(demux, WEBP_FF_FRAME_COUNT);
  int* durations = frames > 0 ? (int*)calloc((size_t)frames, sizeof(int)) : NULL;
  WebPIterator iter;
  if (durations != NULL && WebPDemuxGetFrame(demux, 1, &iter)) {
    do {
      if (*count < frames) durations[(*count)++] = iter.duration;
    } while (WebPDemuxNextFrame(&iter));
    WebPDemuxReleaseIterator(&iter);
  }
  WebPDemuxDelete(demux);
  if (*count == 0) {
    free(durations);
    return NULL;
  }
  return durations;
}

// The kept frames' new durations when keeping every [step]-th frame: each takes the time of the
// frames dropped after it, sped up to fit 10 s, and none under 8 ms. Returns 0 when that can't
// fit in 10 s (then a bigger step is needed).
static int Timeline(const int* durations, int count, int step, int* out) {
  long total = 0;
  for (int i = 0; i < count; ++i) total += durations[i] > 0 ? durations[i] : 0;
  const double scale = total > STICKER_MAX_TOTAL_MS ? (double)STICKER_MAX_TOTAL_MS / (double)total : 1.0;
  long fitted = 0;
  for (int k = 0, i = 0; i < count; ++k, i += step) {
    long group = 0;
    for (int j = i; j < i + step && j < count; ++j) group += durations[j] > 0 ? durations[j] : 0;
    int ms = (int)(group * scale);
    if (ms < STICKER_MIN_FRAME_MS) ms = STICKER_MIN_FRAME_MS;
    out[k] = ms;
    fitted += ms;
  }
  return fitted <= STICKER_MAX_TOTAL_MS;
}

// Copies [rgba] (a [width] x [height] frame) into [target], a 512 x 512 picture, scaled to fit
// and centered on a transparent background.
static int PutFrame(const uint8_t* rgba, int width, int height, WebPPicture* target, WebPPicture* scratch) {
  if (width == STICKER_SIZE && height == STICKER_SIZE) {
    return WebPPictureImportRGBA(target, rgba, width * 4);
  }
  scratch->use_argb = 1;
  scratch->width = width;
  scratch->height = height;
  if (!WebPPictureImportRGBA(scratch, rgba, width * 4)) return 0;
  int w = STICKER_SIZE, h = STICKER_SIZE;
  if (width >= height) {
    h = (int)((long)height * STICKER_SIZE / width);
  } else {
    w = (int)((long)width * STICKER_SIZE / height);
  }
  if (w < 1) w = 1;
  if (h < 1) h = 1;
  if (!WebPPictureRescale(scratch, w, h)) return 0;
  const int x0 = (STICKER_SIZE - w) / 2, y0 = (STICKER_SIZE - h) / 2;
  for (int y = 0; y < STICKER_SIZE; ++y) {
    uint32_t* row = target->argb + (size_t)y * (size_t)target->argb_stride;
    memset(row, 0, STICKER_SIZE * sizeof(uint32_t));
    if (y >= y0 && y < y0 + h) {
      memcpy(row + x0, scratch->argb + (size_t)(y - y0) * (size_t)scratch->argb_stride, (size_t)w * sizeof(uint32_t));
    }
  }
  return 1;
}

// Encodes [in] once with [attempt] and the kept frames' [timeline]; returns 1 with [*result].
static int Encode(const WebPData* in, const Attempt* attempt, int step, const int* timeline, WebPData* result) {
  int ok = 0;
  WebPAnimDecoderOptions dec_options;
  if (!WebPAnimDecoderOptionsInit(&dec_options)) return 0;
  dec_options.color_mode = MODE_RGBA;
  dec_options.use_threads = 1;
  WebPAnimDecoder* dec = WebPAnimDecoderNew(in, &dec_options);
  if (dec == NULL) return 0;

  WebPAnimInfo info;
  WebPAnimEncoder* enc = NULL;
  WebPPicture picture, scratch;
  int picture_ready = 0, scratch_ready = 0;
  WebPConfig config;
  WebPAnimEncoderOptions enc_options;
  if (!WebPAnimDecoderGetInfo(dec, &info) || info.frame_count < 1) goto done;

  if (!WebPAnimEncoderOptionsInit(&enc_options)) goto done;
  enc_options.anim_params.loop_count = (int)info.loop_count;
  enc_options.anim_params.bgcolor = info.bgcolor;
  enc_options.allow_mixed = 0;
  enc_options.minimize_size = 0;
  enc = WebPAnimEncoderNew(STICKER_SIZE, STICKER_SIZE, &enc_options);
  if (enc == NULL) goto done;

  if (!WebPConfigInit(&config)) goto done;
  config.lossless = 0;
  config.quality = attempt->quality;
  config.alpha_quality = (int)attempt->quality;
  config.method = 4;
  config.thread_level = 1;
  if (!WebPValidateConfig(&config)) goto done;

  if (!WebPPictureInit(&picture) || !WebPPictureInit(&scratch)) goto done;
  scratch_ready = 1;
  picture.use_argb = 1;
  picture.width = STICKER_SIZE;
  picture.height = STICKER_SIZE;
  if (!WebPPictureAlloc(&picture)) goto done;
  picture_ready = 1;

  int index = 0, kept = 0, timestamp = 0;
  while (WebPAnimDecoderHasMoreFrames(dec)) {
    uint8_t* rgba;
    int ignored;
    if (!WebPAnimDecoderGetNext(dec, &rgba, &ignored)) goto done;
    if (index++ % step != 0) continue;
    if (!PutFrame(rgba, (int)info.canvas_width, (int)info.canvas_height, &picture, &scratch)) goto done;
    if (!WebPAnimEncoderAdd(enc, &picture, timestamp, &config)) goto done;
    timestamp += timeline[kept++];
  }
  // The end of the last frame.
  if (!WebPAnimEncoderAdd(enc, NULL, timestamp, NULL)) goto done;
  WebPDataInit(result);
  ok = WebPAnimEncoderAssemble(enc, result);

done:
  if (picture_ready) WebPPictureFree(&picture);
  if (scratch_ready) WebPPictureFree(&scratch);
  WebPAnimEncoderDelete(enc);
  WebPAnimDecoderDelete(dec);
  return ok;
}

// Puts [in]'s EXIF and XMP chunks (the sticker pack's details) into [encoded], in place.
static int CopyMetadata(const WebPData* in, WebPData* encoded) {
  WebPMux* source = WebPMuxCreate(in, 0);
  if (source == NULL) return 0;
  WebPData exif, xmp;
  const int has_exif = WebPMuxGetChunk(source, "EXIF", &exif) == WEBP_MUX_OK;
  const int has_xmp = WebPMuxGetChunk(source, "XMP ", &xmp) == WEBP_MUX_OK;
  if (!has_exif && !has_xmp) {
    WebPMuxDelete(source);
    return 1;
  }
  int ok = 0;
  WebPMux* target = WebPMuxCreate(encoded, 1);
  if (target != NULL &&
      (!has_exif || WebPMuxSetChunk(target, "EXIF", &exif, 1) == WEBP_MUX_OK) &&
      (!has_xmp || WebPMuxSetChunk(target, "XMP ", &xmp, 1) == WEBP_MUX_OK)) {
    WebPData assembled;
    WebPDataInit(&assembled);
    if (WebPMuxAssemble(target, &assembled) == WEBP_MUX_OK) {
      WebPDataClear(encoded);
      *encoded = assembled;
      ok = 1;
    }
  }
  WebPMuxDelete(target);
  WebPMuxDelete(source);
  return ok;
}

int sticker_shrink(const uint8_t* in, size_t in_size, size_t max_bytes, uint8_t** out, size_t* out_size) {
  *out = NULL;
  *out_size = 0;
  const WebPData input = {in, in_size};
  // What the metadata will add back.
  size_t metadata = 0;
  WebPMux* source = WebPMuxCreate(&input, 0);
  if (source == NULL) return 0;
  WebPData chunk;
  if (WebPMuxGetChunk(source, "EXIF", &chunk) == WEBP_MUX_OK) metadata += chunk.size + 8;
  if (WebPMuxGetChunk(source, "XMP ", &chunk) == WEBP_MUX_OK) metadata += chunk.size + 8;
  WebPMuxDelete(source);
  if (metadata >= max_bytes) return 0;

  int count;
  int* durations = Durations(&input, &count);
  if (durations == NULL) return 0;
  int* timeline = (int*)calloc((size_t)count, sizeof(int));
  if (timeline == NULL) {
    free(durations);
    return 0;
  }

  int ok = 0;
  const size_t attempts = sizeof(kAttempts) / sizeof(kAttempts[0]);
  for (size_t i = 0; i < attempts && !ok; ++i) {
    // More frames dropped if even at 8 ms each they don't fit in 10 s.
    int step = kAttempts[i].step;
    while (step < count && !Timeline(durations, count, step, timeline)) ++step;
    if (!Timeline(durations, count, step, timeline)) break;

    WebPData encoded;
    if (!Encode(&input, &kAttempts[i], step, timeline, &encoded)) break;
    const size_t size = encoded.size;
    if (size + metadata > max_bytes) {
      WebPDataClear(&encoded);
      // Far too big: skip the attempts that can't get there by quality alone.
      if (size > 2 * max_bytes) {
        while (i + 1 < attempts && kAttempts[i + 1].step == kAttempts[i].step) ++i;
      }
      continue;
    }
    if (!CopyMetadata(&input, &encoded) || encoded.size > max_bytes) {
      WebPDataClear(&encoded);
      break;
    }
    *out = (uint8_t*)encoded.bytes;
    *out_size = encoded.size;
    ok = 1;
  }
  free(timeline);
  free(durations);
  return ok;
}

void sticker_shrink_free(uint8_t* data) { WebPFree(data); }
