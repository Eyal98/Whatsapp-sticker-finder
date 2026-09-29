// Makes an animated WebP sticker small enough for WhatsApp, keeping its size in pixels and its
// metadata (the sticker pack's EXIF). See sticker_shrink.c.
#ifndef STICKER_SHRINK_H_
#define STICKER_SHRINK_H_

#include <stddef.h>
#include <stdint.h>

// Re-encodes the animated WebP in [in] to at most [max_bytes]. On success returns 1 and sets
// [*out] / [*out_size] to a buffer the caller frees with sticker_shrink_free(). Returns 0 when it
// isn't an animated WebP, or it can't be made small enough.
int sticker_shrink(const uint8_t* in, size_t in_size, size_t max_bytes, uint8_t** out, size_t* out_size);

void sticker_shrink_free(uint8_t* data);

#endif  // STICKER_SHRINK_H_
