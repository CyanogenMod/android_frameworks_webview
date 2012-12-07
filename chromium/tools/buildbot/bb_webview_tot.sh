#!/bin/bash
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

# The buildbot script for the webview ToT bot.

BB_INTERNAL_DIR="$(dirname ${0})"
ANDROID_SRC_ROOT="$(cd "${BB_INTERNAL_DIR}/../../../../.."; pwd)"
BB_DIR="${ANDROID_SRC_ROOT}/external/chromium_org/build/android/buildbot"
. "$BB_DIR/buildbot_functions.sh"
. "$BB_INTERNAL_DIR/webview_buildbot_functions.sh"

bb_webview_baseline_setup "${ANDROID_SRC_ROOT}"
bb_webview_smart_sync
bb_webview_sync_and_merge
bb_webview_build_android
