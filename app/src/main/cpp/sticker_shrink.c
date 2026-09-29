// Shrinking animated stickers for WhatsApp, which rejects animated stickers over 500 KB when
// they're inserted from a keyboard (stickers other people sent can be bigger, and end up in the
// library as they are).
//
// Each attempt decodes the sticker frame by frame and encodes it again, lossy, keeping the canvas
// size (WhatsApp wants 512 x 512), the timing, the loop count and the EXIF chunk that links the
// sticker to its pack. Attempts go from lighter to heavier: lower quality first, then fewer
// frames (a dropped frame's time goes to the frame before it, so the animation keeps its speed).
// Frames are streamed, never all held at once.

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
    {75.f, 1}, {60.f, 1}, {45.f, 1}, {60.f, 2}, {40.f, 2}, {30.f, 3}, {25.f, 4}, {20.f, 6},
};

// Encodes [in] once with [attempt]; returns 1 with the result in [*result].
static int Encode(const WebPData* in, const Attempt* attempt, WebPData* result) {
  int ok = 0;
  WebPAnimDecoderOptions dec_options;
  if (!WebPAnimDecoderOptionsInit(&dec_options)) return 0;
  dec_options.color_mode = MODE_RGBA;
  dec_options.use_threads = 1;
  WebPAnimDecoder* dec = WebPAnimDecoderNew(in, &dec_options);
  if (dec == NULL) return 0;

  WebPAnimInfo info;
  WebPAnimEncoder* enc = NULL;
  WebPPicture picture;
  int picture_ready = 0;
  WebPConfig config;
  if (!WebPAnimDecoderGetInfo(dec, &info) || info.frame_count < 1) goto done;

  WebPAnimEncoderOptions enc_options;
  if (!WebPAnimEncoderOptionsInit(&enc_options)) goto done;
  enc_options.anim_params.loop_count = (int)info.loop_count;
  enc_options.anim_params.bgcolor = info.bgcolor;
  enc_options.allow_mixed = 0;
  enc_options.minimize_size = 0;
  enc = WebPAnimEncoderNew((int)info.canvas_width, (int)info.canvas_height, &enc_options);
  if (enc == NULL) goto done;

  if (!WebPConfigInit(&config)) goto done;
  config.lossless = 0;
  config.quality = attempt->quality;
  config.alpha_quality = (int)attempt->quality;
  config.method = 4;
  config.thread_level = 1;
  if (!WebPValidateConfig(&config)) goto done;

  if (!WebPPictureInit(&picture)) goto done;
  picture.use_argb = 1;
  picture.width = (int)info.canvas_width;
  picture.height = (int)info.canvas_height;
  if (!WebPPictureAlloc(&picture)) goto done;
  picture_ready = 1;

  int index = 0;
  int last_timestamp = 0;
  while (WebPAnimDecoderHasMoreFrames(dec)) {
    uint8_t* rgba;
    int timestamp;  // When this frame ends.
    if (!WebPAnimDecoderGetNext(dec, &rgba, &timestamp)) goto done;
    // The frame starts where the previous one ended.
    const int start = last_timestamp;
    last_timestamp = timestamp;
    if (index++ % attempt->step != 0) continue;
    if (!WebPPictureImportRGBA(&picture, rgba, (int)info.canvas_width * 4)) goto done;
    if (!WebPAnimEncoderAdd(enc, &picture, start, &config)) goto done;
  }
  // The end of the last frame.
  if (!WebPAnimEncoderAdd(enc, NULL, last_timestamp, NULL)) goto done;
  WebPDataInit(result);
  ok = WebPAnimEncoderAssemble(enc, result);

done:
  if (picture_ready) WebPPictureFree(&picture);
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

  const size_t attempts = sizeof(kAttempts) / sizeof(kAttempts[0]);
  for (size_t i = 0; i < attempts; ++i) {
    WebPData encoded;
    if (!Encode(&input, &kAttempts[i], &encoded)) return 0;
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
      return 0;
    }
    *out = (uint8_t*)encoded.bytes;
    *out_size = encoded.size;
    return 1;
  }
  return 0;
}

void sticker_shrink_free(uint8_t* data) { WebPFree(data); }
