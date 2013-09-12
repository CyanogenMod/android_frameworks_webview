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

#include "android_webview/public/browser/draw_gl.h"
#include "android_webview/public/browser/draw_sw.h"

#include <cstdlib>
#include <jni.h>
#include <UniquePtr.h>
#include <utils/Log.h>
#include <utils/Vector.h>
#include "graphic_buffer_impl.h"
#include "GraphicsJNI.h"
#include "SkGraphics.h"
#include "SkPicture.h"

#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))

namespace android {
namespace {

class PixelInfo : public AwPixelInfo {
 public:
  PixelInfo(SkCanvas* canvas, const SkBitmap* bitmap);
  ~PixelInfo();

  void AddRectToClip(const SkIRect& rect);

 private:
  const SkBitmap* bitmap_;
  SkAutoLockPixels bitmap_locker_;
  Vector<int> clip_rect_storage_;
};

class ClipValidator : public SkCanvas::ClipVisitor {
 public:
  ClipValidator() : failed_(false) {}
  bool failed() { return failed_; }

  // ClipVisitor
  virtual void clipRect(const SkRect& rect, SkRegion::Op op, bool antialias) {
    failed_ |= antialias;
  }

  virtual void clipPath(const SkPath&, SkRegion::Op, bool antialias) {
    failed_ |= antialias;
  }

 private:
  bool failed_;
};

PixelInfo::PixelInfo(SkCanvas* canvas, const SkBitmap* bitmap)
    : bitmap_(bitmap),
      bitmap_locker_(*bitmap) {
  memset(this, 0, sizeof(AwPixelInfo));
  version = kAwPixelInfoVersion;
}

PixelInfo::~PixelInfo() {}

void PixelInfo::AddRectToClip(const SkIRect& rect) {
  ALOG_ASSERT(rect.width() >= 0 && rect.height() >= 0);
  clip_rect_storage_.push_back(rect.x());
  clip_rect_storage_.push_back(rect.y());
  clip_rect_storage_.push_back(rect.width());
  clip_rect_storage_.push_back(rect.height());
  clip_rects = const_cast<int*>(clip_rect_storage_.array());
  clip_rect_count = clip_rect_storage_.size() / 4;
}

PixelInfo* TryToCreatePixelInfo(SkCanvas* canvas) {
  // Check the clip can decompose into simple rectangles. This validator is
  // not a perfect guarantee, but it's the closest I can do with the current
  // API. TODO: compile this out in release builds as currently Java canvases
  // do not allow for antialiased clip.
  ClipValidator validator;
  canvas->replayClips(&validator);
  if (validator.failed())
    return NULL;

  SkCanvas::LayerIter layer(SkCanvas::LayerIter(canvas, false));
  if (layer.done())
    return NULL;
  SkDevice* device = layer.device();
  if (!device)
    return NULL;
  const SkBitmap* bitmap = &device->accessBitmap(true);
  if (!bitmap->lockPixelsAreWritable())
    return NULL;
  const SkRegion& region = layer.clip();
  layer.next();
  // Currently don't handle multiple layers well, so early out
  // TODO: Return all layers in PixelInfo
  if (!layer.done())
    return NULL;

  UniquePtr<PixelInfo> pixels(new PixelInfo(canvas, bitmap));
  pixels->config =
      bitmap->config() == SkBitmap::kARGB_8888_Config ? AwConfig_ARGB_8888 :
      bitmap->config() == SkBitmap::kARGB_4444_Config ? AwConfig_ARGB_4444 :
      bitmap->config() == SkBitmap::kRGB_565_Config ? AwConfig_RGB_565 : -1;
  if (pixels->config < 0)
    return NULL;

  pixels->width = bitmap->width();
  pixels->height = bitmap->height();
  pixels->row_bytes = bitmap->rowBytes();
  pixels->pixels = bitmap->getPixels();
  const SkMatrix& matrix = layer.matrix();
  for (int i = 0; i < 9; i++) {
    pixels->matrix[i] = matrix.get(i);
  }

  if (region.isEmpty()) {
    pixels->AddRectToClip(region.getBounds());
  } else {
    SkRegion::Iterator clip_iterator(region);
    for (; !clip_iterator.done(); clip_iterator.next()) {
      pixels->AddRectToClip(clip_iterator.rect());
    }
  }

  // WebViewClassic used the DrawFilter for its own purposes (e.g. disabling
  // dithering when zooming/scrolling) so for now at least, just ignore any
  // client supplied DrawFilter.
  ALOGW_IF(canvas->getDrawFilter(),
           "DrawFilter not supported in webviewchromium, will be ignored");
  return pixels.release();
}

AwPixelInfo* GetPixels(JNIEnv* env, jobject java_canvas) {
  SkCanvas* canvas = GraphicsJNI::getNativeCanvas(env, java_canvas);
  if (!canvas)
    return NULL;

  return TryToCreatePixelInfo(canvas);
}

void ReleasePixels(AwPixelInfo* pixels) {
  delete static_cast<PixelInfo*>(pixels);
}

jobject CreatePicture(JNIEnv* env, SkPicture* picture) {
  jclass clazz = env->FindClass("android/graphics/Picture");
  jmethodID constructor = env->GetMethodID(clazz, "<init>", "(IZ)V");
  ALOG_ASSERT(clazz);
  ALOG_ASSERT(constructor);
  return env->NewObject(clazz, constructor, picture, false);
}

bool IsSkiaVersionCompatible(SkiaVersionFunction function) {
  bool compatible = false;
  if (function && function == &SkGraphics::GetVersion) {
    int android_major, android_minor, android_patch;
    SkGraphics::GetVersion(&android_major, &android_minor, &android_patch);

    int chromium_major, chromium_minor, chromium_patch;
    (*function)(&chromium_major, &chromium_minor, &chromium_patch);

    compatible = android_major == chromium_major &&
                 android_minor == chromium_minor &&
                 android_patch == chromium_patch;
  }
  return compatible;
}

jint GetDrawSWFunctionTable(JNIEnv* env, jclass) {
  static const AwDrawSWFunctionTable function_table = {
      &GetPixels,
      &ReleasePixels,
      &CreatePicture,
      &IsSkiaVersionCompatible,
  };
  return reinterpret_cast<jint>(&function_table);
}

jint GetDrawGLFunctionTable(JNIEnv* env, jclass) {
  static const AwDrawGLFunctionTable function_table = {
    &GraphicBufferImpl::Create,
    &GraphicBufferImpl::Release,
    &GraphicBufferImpl::MapStatic,
    &GraphicBufferImpl::UnmapStatic,
    &GraphicBufferImpl::GetNativeBufferStatic,
    &GraphicBufferImpl::GetStrideStatic,
  };
  return reinterpret_cast<jint>(&function_table);
}

const char kClassName[] = "com/android/webview/chromium/GraphicsUtils";
const JNINativeMethod kJniMethods[] = {
    { "nativeGetDrawSWFunctionTable", "()I",
        reinterpret_cast<void*>(GetDrawSWFunctionTable) },
    { "nativeGetDrawGLFunctionTable", "()I",
        reinterpret_cast<void*>(GetDrawGLFunctionTable) },
};

}  // namespace

void RegisterGraphicsUtils(JNIEnv* env) {
  jclass clazz = env->FindClass(kClassName);
  LOG_ALWAYS_FATAL_IF(!clazz, "Unable to find class '%s'", kClassName);

  int res = env->RegisterNatives(clazz, kJniMethods, NELEM(kJniMethods));
  LOG_ALWAYS_FATAL_IF(res < 0, "register native methods failed: res=%d", res);
}

}  // namespace android
