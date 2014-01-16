/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.uiautomator.platform;

import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiScrollable;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.janktesthelper.JankTestBase;

import java.io.File;
import java.io.IOException;

/**
 * Jank test for Android Webview.
 *
 * To run
 * 1) Install the test application (com.android.webview.chromium.shell)
 * 2) Place a directories containing the test pages on the test device in
 *    $EXTERNAL_STORAGE/AwJankPages. Each directory should contain an index.html
 *    file as the main file of the test page.
 * 3) Build this test and push the resulting Jar file to /data/local/tmp/WebViewJankTests.jar
 * 4) Run the test using the command:
 *    adb shell uiautomator runtest WebViewJankTests.jar
 *
 * The test will run for each of the test pages. The results will be saved on the device in
 * /data/local/tmp/jankoutput.txt.
 */
public class WebViewFlingTest extends JankTestBase {

    private static final long TEST_DELAY_TIME_MS = 2 * 1000; // 2 seconds
    private static final long PAGE_LOAD_DELAY_TIME_MS = 20 * 1000; // 10 seconds
    private static final int MIN_DATA_SIZE = 50;
    private static final String AW_WINDOW_NAME =
            "com.android.webview.chromium.shell/com.android.webview.chromium.shell.JankActivity";
    private static final String AW_CONTAINER = "com.android.webview.chromium.shell:id/container";
    private static final String START_CMD =
            "am start -n com.android.webview.chromium.shell/.JankActivity -d ";
    private UiScrollable mWebPageDisplay = null;

    public void testBrowserPageFling() throws UiObjectNotFoundException, IOException {
        String externalStorage = System.getenv().get("EXTERNAL_STORAGE");
        File base = new File(externalStorage, "AwJankPages");
        File files[] = base.listFiles();
        assertNotNull("No test pages", files);
        for(File file: files) {
            runBrowserPageFling(file, true);
            runBrowserPageFling(file, false);
        }
    }

    private void resetFlingTest() throws UiObjectNotFoundException {
        // the idea is to fling to beginning first then fling to middle-ish so we have enough
        // room to fling both forward and backward without hitting either end
        getContainer().flingToBeginning(20);
        getContainer().scrollForward();
        getContainer().scrollForward();
        getContainer().scrollForward();
        getContainer().scrollForward();
    }

    private void loadUrl(String url) throws IOException {
        Runtime.getRuntime().exec(START_CMD + url);
        // Need to find a good way of detecting when the page is loaded
        sleep(PAGE_LOAD_DELAY_TIME_MS);
    }

    private void flingForward() throws UiObjectNotFoundException {
        getContainer().flingForward();
    }

    private void flingBackward() throws UiObjectNotFoundException {
        getContainer().flingBackward();
    }

    private UiScrollable getContainer() {
        if (mWebPageDisplay == null) {
            mWebPageDisplay =
                    new UiScrollable(new UiSelector().resourceId(AW_CONTAINER).instance(0));
        }
        return mWebPageDisplay;
    }

    private void runBrowserPageFling(File testDir, boolean down) throws UiObjectNotFoundException, IOException {
        String testCaseName = String.format("%s_%s_%s", mTestCaseName, testDir.getName(), down? "down" : "up");

        File testPageIndex = new File(testDir, "index.html");

        assertTrue("Test page doesn't have an index.html", testPageIndex.exists());

        loadUrl("file://" + testPageIndex.getAbsolutePath());
        for (int i = 0; i < getIteration(); i++) {
            resetFlingTest();
            sleep(TEST_DELAY_TIME_MS);

            startTrace(mTestCaseName, i);
            getSurfaceFlingerHelper().clearBuffer(AW_WINDOW_NAME);
            if(down) {
                flingForward();
            } else {
                flingBackward();
            }
            sleep(DEFAULT_ANIMATION_TIME);
            boolean result =
                    getSurfaceFlingerHelper().dumpFrameLatency(AW_WINDOW_NAME, true);
            assertTrue("dump frame latency failed", result);

            waitForTrace();
            assertTrue(String.format("Sample size is less than expected: %d", MIN_DATA_SIZE),
                    validateResults(MIN_DATA_SIZE));
            // record the result in an array
            recordResults(testCaseName, i);
        }
        // calculate average and save the results
        saveResults(testCaseName);
    }
}
