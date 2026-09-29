// Makes an animated WebP sticker fit WhatsApp's rules for animated stickers, keeping its look,
// timing (as far as the rules allow) and metadata (the sticker pack's EXIF). See sticker_shrink.c.
#ifndef STICKER_SHRINK_H_
#define STICKER_SHRINK_H_

#include <stddef.h>
#include <stdint.h>

// WhatsApp's rules for animated stickers (its sticker pack validator).
#define STICKER_SIZE 512
#define STICKER_MIN_FRAME_MS 8
#define STICKER_MAX_TOTAL_MS 10000

typedef struct {
  int width;
  int height;
  int frames;
  int total_ms;
  int min_frame_ms;
  int max_frame_ms;
  int loop_count;
  int has_exif;
  int has_xmp;
  int has_iccp;
  int animated;
} StickerInfo;

// Reads [in]'s layout without decoding it. Returns 0 when it isn't a WebP.
int sticker_info(const uint8_t* in, size_t in_size, StickerInfo* info);

// Whether an animated sticker of [size] bytes with [info] breaks one of WhatsApp's rules.
int sticker_needs_fitting(const StickerInfo* info, size_t size, size_t max_bytes);

// Re-encodes the animated WebP in [in]: a 512 x 512 canvas (scaled to fit, centered), frames of
// at least 8 ms and 10 s in all (sped up if longer), at most [max_bytes]. On success returns 1 and
// sets [*out] / [*out_size] to a buffer the caller frees with sticker_shrink_free(). Returns 0
// when it isn't an animated WebP or can't be made small enough.
int sticker_shrink(const uint8_t* in, size_t in_size, size_t max_bytes, uint8_t** out, size_t* out_size);

void sticker_shrink_free(uint8_t* data);

#endif  // STICKER_SHRINK_H_
