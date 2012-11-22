#!/usr/bin/env python
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

"""
Executes WebView CTS tests and verifies results against known failures.
"""

import re
import signal
import subprocess
import sys

# Eventually this list will be empty!
EXPECTED_FAILURES = set([
  'android.webkit.cts.GeolocationTest#testSimpleGeolocationRequestAcceptOnce',
  'android.webkit.cts.GeolocationTest#testGeolocationPermissions',
  'android.webkit.cts.GeolocationTest#testSimpleGeolocationRequestAcceptAlways',
  'android.webkit.cts.GeolocationTest#testSimpleGeolocationRequestReject',
  'android.webkit.cts.WebBackForwardListTest#testGetCurrentItem',
  'android.webkit.cts.WebChromeClientTest#testOnReceivedIcon',
  'android.webkit.cts.WebChromeClientTest#testWindows',
  'android.webkit.cts.WebHistoryItemTest#testWebHistoryItem',
  'android.webkit.cts.WebSettingsTest#testAccessCacheMode',
  'android.webkit.cts.WebSettingsTest#testAccessDefaultTextEncodingName',
  'android.webkit.cts.WebSettingsTest#testAccessLayoutAlgorithm',
  'android.webkit.cts.WebSettingsTest#testAccessLightTouchEnabled',
  'android.webkit.cts.WebSettingsTest#testAccessNavDump',
  'android.webkit.cts.WebSettingsTest#testAccessPluginsEnabled',
  'android.webkit.cts.WebSettingsTest#testAccessSaveFormData',
  'android.webkit.cts.WebSettingsTest#testAccessSavePassword',
  'android.webkit.cts.WebSettingsTest#testAccessTextSize',
  'android.webkit.cts.WebSettingsTest#testAccessUseWideViewPort',
  'android.webkit.cts.WebSettingsTest#testAccessUserAgent',
  'android.webkit.cts.WebSettingsTest#testAppCacheDisabled',
  'android.webkit.cts.WebSettingsTest#testAppCacheEnabled',
  'android.webkit.cts.WebSettingsTest#testIframesWhenAccessFromFileURLsEnabled',
  'android.webkit.cts.WebSettingsTest#testXHRWhenAccessFromFileURLsEnabled',
  'android.webkit.cts.WebViewClientTest#testDoUpdateVisitedHistory',
  'android.webkit.cts.WebViewClientTest#testOnScaleChanged',
  'android.webkit.cts.WebViewTest#testAccessHttpAuthUsernamePassword',
  'android.webkit.cts.WebViewTest#testCapturePicture',
  'android.webkit.cts.WebViewTest#testClearHistory',
  'android.webkit.cts.WebViewTest#testFindAddress',
  'android.webkit.cts.WebViewTest#testGetContentHeight',
  'android.webkit.cts.WebViewTest#testGetHitTestResult',
  'android.webkit.cts.WebViewTest#testGetZoomControls',
  'android.webkit.cts.WebViewTest#testGoBackAndForward',
  'android.webkit.cts.WebViewTest#testLoadDataWithBaseUrl',
  'android.webkit.cts.WebViewTest#testOnReceivedSslError',
  'android.webkit.cts.WebViewTest#testOnReceivedSslErrorProceed',
  'android.webkit.cts.WebViewTest#testPageScroll',
  'android.webkit.cts.WebViewTest#testPauseResumeTimers',
  'android.webkit.cts.WebViewTest#testRequestChildRectangleOnScreen',
  'android.webkit.cts.WebViewTest#testRequestFocusNodeHref',
  'android.webkit.cts.WebViewTest#testSaveAndRestorePicture',
  'android.webkit.cts.WebViewTest#testSaveAndRestoreState',
  'android.webkit.cts.WebViewTest#testSavePassword',
  'android.webkit.cts.WebViewTest#testScrollBarOverlay',
  'android.webkit.cts.WebViewTest#testSecureSiteSetsCertificate',
  'android.webkit.cts.WebViewTest#testSetDownloadListener',
  'android.webkit.cts.WebViewTest#testSetInitialScale',
  'android.webkit.cts.WebViewTest#testSetScrollBarStyle',
  'android.webkit.cts.WebViewTest#testSetWebViewClient',
  'android.webkit.cts.WebViewTest#testSslErrorProceedResponseNotReusedForDifferentHost',
  'android.webkit.cts.WebViewTest#testSslErrorProceedResponseReusedForSameHost',
  'android.webkit.cts.WebViewTest#testStopLoading',
  'android.webkit.cts.WebViewTest#testZoom',
])

def main():
  proc = subprocess.Popen(
      ['cts-tradefed', 'run', 'singleCommand', 'cts', '-p', 'android.webkit'],
      stdout=subprocess.PIPE, stderr=subprocess.PIPE)

  try:
    (stdout, stderr) = proc.communicate();
  except KeyboardInterrupt:
    proc.send_signal(signal.SIGINT)
    proc.communicate();  # Wait for process to finish.
    # http://www.gnu.org/software/libc/manual/html_node/Exit-Status.html
    return 128

  passes = set(re.findall(r'.*: (.*) PASS', stdout))
  failures = set(re.findall(r'.*: (.*) FAIL', stdout))
  print '%d passes; %d failures' % (len(passes), len(failures))

  unexpected_passes = EXPECTED_FAILURES.difference(failures)
  if len(unexpected_passes) > 0:
    print 'UNEXPECTED PASSES (update expectations!):'
    for test in unexpected_passes:
      print '\t%s' % (test)

  unexpected_failures = failures.difference(EXPECTED_FAILURES)
  if len(unexpected_failures) > 0:
    print 'UNEXPECTED FAILURES (please fix!):'
    for test in unexpected_failures:
      print '\t%s' % (test)

  print '\nstdout dump follows...'
  print stdout

  unexpected_failures_count = len(unexpected_failures)
  unexpected_passes_count = len(unexpected_passes)

  # Allow buildbot script to distinguish failures and possibly out of date
  # test expectations.
  if len(passes) + len(failures) < 100:
    print 'Ran less than 100 cts tests? Something must be wrong'
    return 2
  elif unexpected_failures_count > 0:
    return 1
  elif unexpected_passes_count >= 5:
    print ('More than 5 new passes? Either you''re running webview classic, or '
           'it really is time to fix failure expectations.')
    return 2
  elif unexpected_passes_count > 0:
    return 3  # STEP_WARNINGS
  else:
    return 0


if __name__ == '__main__':
  sys.exit(main())
