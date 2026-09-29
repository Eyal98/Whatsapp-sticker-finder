// JNI entry for StickerShrinker.shrink (Kotlin).
#include <jni.h>
#include <stdlib.h>

#include "sticker_shrink.h"

JNIEXPORT jbyteArray JNICALL
Java_com_eyal98_stickerfinder_keyboard_StickerShrinker_nativeShrink(JNIEnv* env, jclass clazz, jbyteArray input, jint max_bytes) {
  (void)clazz;
  const jsize size = (*env)->GetArrayLength(env, input);
  jbyte* bytes = (*env)->GetByteArrayElements(env, input, NULL);
  if (bytes == NULL) return NULL;
  uint8_t* out = NULL;
  size_t out_size = 0;
  const int ok = sticker_shrink((const uint8_t*)bytes, (size_t)size, (size_t)max_bytes, &out, &out_size);
  (*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);
  if (!ok) return NULL;
  jbyteArray result = (*env)->NewByteArray(env, (jsize)out_size);
  if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, (jsize)out_size, (const jbyte*)out);
  sticker_shrink_free(out);
  return result;
}
