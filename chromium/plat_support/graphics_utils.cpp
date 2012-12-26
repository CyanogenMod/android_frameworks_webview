/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Provides a webviewchromium glue layer adapter from the internal Android
// graphics types into the types the chromium stack expects, and back.

#define LOG_TAG "webviewchromium_plat_support"

#include "android_webview/public/browser/draw_sw.h"

#include <cstdlib>
#include <jni.h>
#include <utils/Log.h>
#include "GraphicsJNI.h"

#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))

namespace android {
namespace {

struct PixelInfo : public AwPixelInfo {
  PixelInfo(const SkBitmap* bitmap)
      : bitmap_(bitmap) {
    this->bitmap_->lockPixels();
  }
  ~PixelInfo() {
    this->bitmap_->unlockPixels();
    free(clip_region);
  };
  const SkBitmap* bitmap_;
};

AwPixelInfo* GetPixels(JNIEnv* env, jobject java_canvas) {
  SkCanvas* canvas = GraphicsJNI::getNativeCanvas(env, java_canvas);
  if (!canvas) return NULL;
  SkDevice* device = canvas->getDevice();
  if (!device) return NULL;
  const SkBitmap* bitmap = &device->accessBitmap(true);
  if (!bitmap->lockPixelsAreWritable()) return NULL;

  PixelInfo* pixels = new PixelInfo(bitmap);
  pixels->config = bitmap->config();
  pixels->width = bitmap->width();
  pixels->height = bitmap->height();
  pixels->row_bytes = bitmap->rowBytes();
  pixels->pixels = bitmap->getPixels();
  const SkMatrix& matrix = canvas->getTotalMatrix();
  for (int i = 0; i < 9; i++) {
    pixels->matrix[i] = matrix.get(i);
  }
  // TODO: getTotalClip() is now marked as deprecated, but the replacement,
  // getClipDeviceBounds, does not return the exact region, just the bounds.
  // Work out what we should use instead.
  const SkRegion& region = canvas->getTotalClip();
  pixels->clip_region = NULL;
  pixels->clip_region_size = region.writeToMemory(NULL);
  if (pixels->clip_region_size) {
    pixels->clip_region = malloc(pixels->clip_region_size);
    size_t written = region.writeToMemory(pixels->clip_region);
    ALOG_ASSERT(written == pixels->clip_region_size);
  }
  // WebViewClassic used the DrawFilter for its own purposes (e.g. disabling
  // dithering when zooming/scrolling) so for now at least, just ignore any
  // client supplied DrawFilter.
  ALOGW_IF(canvas->getDrawFilter(),
           "DrawFilter not supported in webviewchromium, will be ignored");
  return pixels;
}

void ReleasePixels(AwPixelInfo* pixels) {
  delete static_cast<PixelInfo*>(pixels);
}

jint GetDrawSWFunctionTable(JNIEnv* env, jclass) {
  static const AwDrawSWFunctionTable function_table = {
      &GetPixels,
      &ReleasePixels,
  };
  return reinterpret_cast<jint>(&function_table);
}

const char kClassName[] = "com/android/webview/chromium/GraphicsUtils";
const JNINativeMethod kJniMethods[] = {
    { "nativeGetDrawSWFunctionTable", "()I",
        reinterpret_cast<void*>(GetDrawSWFunctionTable) },
};

}  // namespace

void RegisterGraphicsUtils(JNIEnv* env) {
  jclass clazz = env->FindClass(kClassName);
  LOG_ALWAYS_FATAL_IF(!clazz, "Unable to find class '%s'", kClassName);

  int res = env->RegisterNatives(clazz, kJniMethods, NELEM(kJniMethods));
  LOG_ALWAYS_FATAL_IF(res < 0, "register native methods failed: res=%d", res);
}

}  // namespace android
