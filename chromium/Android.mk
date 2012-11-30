#
# Copyright (C) 2012 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This package provides the 'glue' layer between Chromium and WebView.

LOCAL_PATH := $(call my-dir)
CHROMIUM_PATH := external/chromium_org

# Java glue layer JAR, calls directly into the chromium AwContents Java API.
include $(CLEAR_VARS)

LOCAL_MODULE := webviewchromium

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES += google-common \
                               android_webview_java

LOCAL_SRC_FILES := $(call all-java-files-under, java)

LOCAL_REQUIRED_MODULES := \
        libwebviewchromium \
        libwebviewchromium_plat_support \
        webviewchromium_pak \
        webviewchromium_strings_pak

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_JAVA_LIBRARY)

# Native support library (libwebviewchromium_plat_support.so) - does NOT link
# any native chromium code.
include $(CLEAR_VARS)

LOCAL_MODULE:= libwebviewchromium_plat_support

LOCAL_SRC_FILES:=       \
        plat_support/draw_gl_functor.cpp \
        plat_support/jni_entry_point.cpp

LOCAL_C_INCLUDES:= \
        $(CHROMIUM_PATH)

LOCAL_SHARED_LIBRARIES += \
        libutils \
        libcutils

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
