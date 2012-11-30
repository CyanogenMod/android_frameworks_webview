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

package com.android.webview.chromium;

import android.app.ActivityThread;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebIconDatabase;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.webkit.WebViewFactoryProvider;
import android.webkit.WebViewProvider;

import org.chromium.android_webview.AwBrowserProcess;
import org.chromium.android_webview.AwContents;
import org.chromium.android_webview.AwGeolocationPermissions;
import org.chromium.base.PathService;
import org.chromium.content.browser.ContentSettings;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.ResourceExtractor;

public class WebViewChromiumFactoryProvider implements WebViewFactoryProvider {

    private final Object mLock = new Object();

    private static final String CHROMIUM_PREFS_NAME = "WebViewChromiumPrefs";

    // Initialization guarded by mLock.
    private SharedPreferences mWebViewChromiumSharedPreferences;
    private Statics mStaticMethods;
    private GeolocationPermissionsAdapter mGeolocationPermissions;
    private CookieManagerAdapter mCookieManager;
    private WebIconDatabaseAdapter mWebIconDatabase;
    private WebStorageAdapter mWebStorage;
    private WebViewDatabaseAdapter mWebViewDatabase;

    // Read/write protected by mLock.
    private boolean mChromiumStarted;

    public WebViewChromiumFactoryProvider() {
        // Only do the minimum initialization here that we know:
        //   a) is safe to do prior to the zygote forking the app-specific process, and
        //   b) gives the maximum benefit w.r.t. cross app memory and startup savings.
        loadLibraries();
    }

    private void loadLibraries() {
        // We don't need to extract any paks because for WebView, they are
        // in the system image.
        ResourceExtractor.setMandatoryPaksToExtract("");
        // Load the main chromium native library.
        AwBrowserProcess.loadLibrary();
        // Load glue-layer support library.
        System.loadLibrary("webviewchromium_plat_support");
        // Connect them up.
        DrawGLFunctor.setChromiumAwDrawGLFunction(AwContents.getAwDrawGLFunction());
    }

    private void ensureChromiumStartedLocked() {
        assert Thread.holdsLock(mLock);

        if (mChromiumStarted) return;

        PathService.override(PathService.DIR_MODULE, "/system/lib/");
        // TODO: DIR_RESOURCE_PAKS_ANDROID needs to live somewhere sensible,
        // inlined here for simplicity setting up the HTMLViewer demo. Unfortunately
        // it can't go into base.PathService, as the native constant it refers to
        // lives in the ui/ layer. See ui/base/ui_base_paths.h
        final int DIR_RESOURCE_PAKS_ANDROID = 3003;
        PathService.override(DIR_RESOURCE_PAKS_ANDROID,
                "/system/framework/webview/paks");

        // Caching for later use, possibly from other threads
        mWebViewChromiumSharedPreferences = ActivityThread.currentApplication().
                getSharedPreferences(CHROMIUM_PREFS_NAME, Context.MODE_PRIVATE);

        AwBrowserProcess.start(ActivityThread.currentApplication());
        mChromiumStarted = true;
    }

    @Override
    public Statics getStatics() {
        synchronized (mLock) {
            if (mStaticMethods == null) {
                mStaticMethods = new WebViewFactoryProvider.Statics() {
                    @Override
                    public String findAddress(String addr) {
                        return ContentViewStatics.findAddress(addr);
                    }

                    @Override
                    public void setPlatformNotificationsEnabled(boolean enable) {
                        // noop
                    }

                    @Override
                    public String getDefaultUserAgent(Context context) {
                        // TODO(perf): Optimization potential: avoid starting chromium up here
                        // by routing this call to AwSettings -> AwContentClient::GetUserAgent.
                        synchronized (mLock) {
                            ensureChromiumStartedLocked();
                        }
                        return ContentSettings.getDefaultUserAgent();
                    }
                };
            }
        }
        return mStaticMethods;
    }

    @Override
    public WebViewProvider createWebView(WebView webView, WebView.PrivateAccess privateAccess) {
        assert Looper.myLooper() == Looper.getMainLooper();
        synchronized (mLock) {
            ensureChromiumStartedLocked();
            ResourceProvider.registerResources(webView.getContext());
        }
        return new WebViewChromium(webView, privateAccess);
    }

    @Override
    public GeolocationPermissions getGeolocationPermissions() {
        synchronized (mLock) {
            if (mGeolocationPermissions == null) {
                ensureChromiumStartedLocked();
                mGeolocationPermissions = new GeolocationPermissionsAdapter(
                        new AwGeolocationPermissions(mWebViewChromiumSharedPreferences));
            }
        }
        return mGeolocationPermissions;
    }

    @Override
    public CookieManager getCookieManager() {
        synchronized (mLock) {
            if (mCookieManager == null) {
                ensureChromiumStartedLocked();
                mCookieManager = new CookieManagerAdapter(
                        new org.chromium.android_webview.CookieManager());
            }
        }
        return mCookieManager;
    }

    @Override
    public WebIconDatabase getWebIconDatabase() {
        synchronized (mLock) {
            if (mWebIconDatabase == null) {
                ensureChromiumStartedLocked();
                mWebIconDatabase = new WebIconDatabaseAdapter();
            }
        }
        return mWebIconDatabase;
    }

    @Override
    public WebStorage getWebStorage() {
        synchronized (mLock) {
            if (mWebStorage == null) {
                ensureChromiumStartedLocked();
                mWebStorage = new WebStorageAdapter();
            }
        }
        return mWebStorage;
    }

    @Override
    public WebViewDatabase getWebViewDatabase(Context context) {
        synchronized (mLock) {
            if (mWebViewDatabase == null) {
                ensureChromiumStartedLocked();
                mWebViewDatabase = new WebViewDatabaseAdapter();
            }
        }
        return mWebViewDatabase;
    }
}
