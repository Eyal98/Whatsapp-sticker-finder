# Native code: libwebp (vendored, see libwebp/README.peel-it) and the sticker shrinker built on it.
cmake_minimum_required(VERSION 3.22.1)
project(stickershrink C)

set(WEBP ${CMAKE_CURRENT_SOURCE_DIR}/libwebp)
file(GLOB WEBP_SOURCES
    ${WEBP}/src/dec/*.c ${WEBP}/src/enc/*.c ${WEBP}/src/dsp/*.c ${WEBP}/src/utils/*.c
    ${WEBP}/src/mux/*.c ${WEBP}/src/demux/*.c ${WEBP}/sharpyuv/*.c)

add_library(stickershrink SHARED sticker_shrink.c sticker_shrink_jni.c ${WEBP_SOURCES})
target_include_directories(stickershrink PRIVATE ${WEBP} ${CMAKE_CURRENT_SOURCE_DIR})
# As libwebp's own Android build; NEON is always there on arm64.
target_compile_definitions(stickershrink PRIVATE ANDROID HAVE_MALLOC_H HAVE_PTHREAD WEBP_USE_THREAD "WEBP_EXTERN=extern")
target_compile_options(stickershrink PRIVATE -O2 -fvisibility=hidden -Wno-unused-parameter)
# Only the JNI entry is exported; 16 KB pages (Android 15+ devices).
target_link_options(stickershrink PRIVATE -Wl,-z,max-page-size=16384 -Wl,--gc-sections)
target_link_libraries(stickershrink PRIVATE log)
