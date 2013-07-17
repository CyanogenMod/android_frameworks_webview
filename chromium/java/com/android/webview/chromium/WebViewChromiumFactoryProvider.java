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
import android.os.Build;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebIconDatabase;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.webkit.WebViewFactoryProvider;
import android.webkit.WebViewProvider;

import org.chromium.android_webview.AwBrowserContext;
import org.chromium.android_webview.AwBrowserProcess;
import org.chromium.android_webview.AwContents;
import org.chromium.android_webview.AwCookieManager;
import org.chromium.android_webview.AwFormDatabase;
import org.chromium.android_webview.AwGeolocationPermissions;
import org.chromium.android_webview.AwQuotaManagerBridge;
import org.chromium.android_webview.AwSettings;
import org.chromium.base.PathService;
import org.chromium.base.ThreadUtils;
import org.chromium.content.app.LibraryLoader;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.ResourceExtractor;
import org.chromium.content.common.CommandLine;
import org.chromium.content.common.ProcessInitException;

public class WebViewChromiumFactoryProvider implements WebViewFactoryProvider {

    private final Object mLock = new Object();

    private static final String CHROMIUM_PREFS_NAME = "WebViewChromiumPrefs";
    private static final String COMMAND_LINE_FILE = "/data/local/tmp/webview-command-line";

    // Initialization guarded by mLock.
    private AwBrowserContext mBrowserContext;
    private Statics mStaticMethods;
    private GeolocationPermissionsAdapter mGeolocationPermissions;
    private CookieManagerAdapter mCookieManager;
    private WebIconDatabaseAdapter mWebIconDatabase;
    private WebStorageAdapter mWebStorage;
    private WebViewDatabaseAdapter mWebViewDatabase;

    // Read/write protected by mLock.
    private boolean mStarted;

    public WebViewChromiumFactoryProvider() {
        // Load chromium library.
        AwBrowserProcess.loadLibrary();
        // Load glue-layer support library.
        System.loadLibrary("webviewchromium_plat_support");
    }

    private void initPlatSupportLibrary() {
        DrawGLFunctor.setChromiumAwDrawGLFunction(AwContents.getAwDrawGLFunction());
        AwContents.setAwDrawSWFunctionTable(GraphicsUtils.getDrawSWFunctionTable());
        AwContents.setAwDrawGLFunctionTable(GraphicsUtils.getDrawGLFunctionTable());
    }

    private void ensureChromiumStartedLocked() {
        assert Thread.holdsLock(mLock);

        if (mStarted) {
            return;
        }

        // We must post to the UI thread to cover the case that the user
        // has invoked Chromium startup by using the (thread-safe)
        // CookieManager rather than creating a WebView.
        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                if (Build.IS_DEBUGGABLE) {
                    CommandLine.initFromFile(COMMAND_LINE_FILE);
                } else {
                    CommandLine.init(null);
                }

                CommandLine cl = CommandLine.getInstance();
                // TODO: currently in a relase build the DCHECKs only log. We either need to insall
                // a report handler with SetLogReportHandler to make them assert, or else compile
                // them out of the build altogether (b/8284203). Either way, so long they're
                // compiled in, we may as unconditionally enable them here.
                cl.appendSwitch("enable-dcheck");

                // TODO: Remove when GL is supported by default in the upstream code.
                if (!cl.hasSwitch("disable-webview-gl-mode")) {
                    cl.appendSwitch("testing-webview-gl-mode");
                }

                // We don't need to extract any paks because for WebView, they are
                // in the system image.
                ResourceExtractor.setMandatoryPaksToExtract("");

                try {
                    LibraryLoader.ensureInitialized();
                } catch(ProcessInitException e) {
                    throw new RuntimeException("Error initializing WebView library", e);
                }

                PathService.override(PathService.DIR_MODULE, "/system/lib/");
                // TODO: DIR_RESOURCE_PAKS_ANDROID needs to live somewhere sensible,
                // inlined here for simplicity setting up the HTMLViewer demo. Unfortunately
                // it can't go into base.PathService, as the native constant it refers to
                // lives in the ui/ layer. See ui/base/ui_base_paths.h
                final int DIR_RESOURCE_PAKS_ANDROID = 3003;
                PathService.override(DIR_RESOURCE_PAKS_ANDROID,
                        "/system/framework/webview/paks");

                AwBrowserProcess.start(ActivityThread.currentApplication());
                initPlatSupportLibrary();
            }
        });
        mStarted = true;
    }

    @Override
    public Statics getStatics() {
        synchronized (mLock) {
            if (mStaticMethods == null) {
                // TODO: Optimization potential: most these methods only need the native library
                // loaded and initialized, not the entire browser process started.
                // See also http://b/7009882
                ensureChromiumStartedLocked();
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
                        return AwSettings.getDefaultUserAgent();
                    }
                };
            }
        }
        return mStaticMethods;
    }

    @Override
    public WebViewProvider createWebView(WebView webView, WebView.PrivateAccess privateAccess) {
        assert Looper.myLooper() == Looper.getMainLooper();
        AwBrowserContext browserContext;
        synchronized (mLock) {
            ensureChromiumStartedLocked();
            ResourceProvider.registerResources(webView.getContext());
            browserContext = getBrowserContextLocked();
        }
        // Make sure GeolocationPermissions is created before creating a webview
        getGeolocationPermissions();
        return new WebViewChromium(webView, privateAccess, browserContext);
    }

    @Override
    public GeolocationPermissions getGeolocationPermissions() {
        synchronized (mLock) {
            if (mGeolocationPermissions == null) {
                ensureChromiumStartedLocked();
                mGeolocationPermissions = new GeolocationPermissionsAdapter(
                        getBrowserContextLocked().getGeolocationPermissions());
            }
        }
        return mGeolocationPermissions;
    }

    private AwBrowserContext getBrowserContextLocked() {
        assert Thread.holdsLock(mLock);
        assert mStarted;
        if (mBrowserContext == null) {
            mBrowserContext = new AwBrowserContext(
                    ActivityThread.currentApplication().getSharedPreferences(
                            CHROMIUM_PREFS_NAME, Context.MODE_PRIVATE));
        }
        return mBrowserContext;
    }

    @Override
    public CookieManager getCookieManager() {
        synchronized (mLock) {
            if (mCookieManager == null) {
                ensureChromiumStartedLocked();
                mCookieManager = new CookieManagerAdapter(new AwCookieManager());
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
                mWebStorage = new WebStorageAdapter(AwQuotaManagerBridge.getInstance());
            }
        }
        return mWebStorage;
    }

    @Override
    public WebViewDatabase getWebViewDatabase(Context context) {
        synchronized (mLock) {
            if (mWebViewDatabase == null) {
                ensureChromiumStartedLocked();
                AwBrowserContext browserContext =  getBrowserContextLocked();
                mWebViewDatabase = new WebViewDatabaseAdapter(
                        browserContext.getFormDatabase(),
                        browserContext.getHttpAuthDatabase(context));
            }
        }
        return mWebViewDatabase;
    }
}
