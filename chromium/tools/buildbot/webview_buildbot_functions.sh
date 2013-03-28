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

function bb_webview_set_lunch_type() {
  case "$1" in
    clank-webview)
      LUNCH_TYPE="nakasi-eng"
      ;;
    clank-webview-tot)
      LUNCH_TYPE="nakasi-eng"
      ;;
    *)
      LUNCH_TYPE=""
      echo "Unable to determine lunch type from: ${BUILDBOT_BUILDERNAME}"
      echo "@@@STEP_FAILURE@@@"
      exit 1
      ;;
  esac
  echo "Using lunch type: $LUNCH_TYPE"
}

function bb_webview_build_android() {
  echo "@@@BUILD_STEP Compile Android@@@"

  local MAKE_COMMAND="make"
  if [ "$USE_GOMA" -eq 1 ]; then
    echo "Building using GOMA"
    MAKE_COMMAND="${GOMA_DIR}/goma-android-make"
  fi

  bb_run_step $MAKE_COMMAND $MAKE_PARAMS showcommands

  if [ "$USE_GOMA" -eq 1 ]; then
    bb_stop_goma_internal
  fi
}

function bb_webview_goma_setup() {
  # Set to 0 to disable goma in case of problems.
  USE_GOMA=1
  if [ -z "$GOMA_DIR" ]; then
    export GOMA_DIR=/b/build/goma
  fi
  if [ ! -d $GOMA_DIR ]; then
    USE_GOMA=0
  fi

  if [ "$USE_GOMA" -eq 1 ]; then
    MAKE_PARAMS="-j150 -l20"
  else
    MAKE_PARAMS="-j16"
  fi

  bb_setup_goma_internal
}

# Basic setup for all bots to run after a source tree checkout.
# Args:
#   $1: Android source root.
function bb_webview_baseline_setup {
  SRC_ROOT="$1"
  cd $SRC_ROOT

  echo "@@@BUILD_STEP Environment setup@@@"
  . build/envsetup.sh

  bb_webview_set_lunch_type $BUILDBOT_BUILDERNAME
  lunch $LUNCH_TYPE

  if [[ $BUILDBOT_CLOBBER ]]; then
    echo "@@@BUILD_STEP Clobber@@@"

    rm -rf ${ANDROID_PRODUCT_OUT}
    rm -rf ${ANDROID_HOST_OUT}
  fi

  # Add the upstream build/android folder to the Python path.
  # This is required since we don't want to check out the clank scripts into a
  # subfolder of the upstream chromium_org checkout (that would make repo think
  # those are uncommited changes and cause potential issues).
  export PYTHONPATH="$PYTHONPATH:${BB_DIR}/../"

  # The CTS bot runs using repo only.
  export CHECKOUT="repo"

  bb_webview_goma_setup
}

function bb_webview_smart_sync {
  echo "@@@BUILD_STEP Smart Sync (sync -s) @@@"
  bb_run_step repo sync -s -j8 -df
}

function bb_webview_sync_and_merge {
  WEBVIEW_TOOLS_DIR="${ANDROID_SRC_ROOT}/frameworks/webview/chromium/tools"

  echo "@@@BUILD_STEP Sync Chromium Repos@@@"
  bb_run_step ${WEBVIEW_TOOLS_DIR}/sync_chromium_repos.sh

  echo "@@@BUILD_STEP Merge from Chromium@@@"
  bb_run_step python ${WEBVIEW_TOOLS_DIR}/merge_from_chromium.py \
    --unattended \
    --svn_revision=HEAD
}
