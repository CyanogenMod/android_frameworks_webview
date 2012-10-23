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
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebIconDatabase;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.webkit.WebViewFactoryProvider;
import android.webkit.WebViewProvider;

import org.chromium.base.PathService;
import org.chromium.base.PathUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.content.app.LibraryLoader;
import org.chromium.content.browser.AndroidBrowserProcess;
import org.chromium.content.browser.ContentSettings;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.ResourceExtractor;

public class WebViewChromiumFactoryProvider implements WebViewFactoryProvider {

    private final Object mLock = new Object();

    // Initialization guarded by mLock.
    private Statics mStaticMethods;

    // Initialization guarded by mLock.
    private CookieManagerAdapter mCookieManagerAdapter;

    // Read/write protected by mLock.
    private boolean mInitialized;

    private void ensureChromiumNativeInitializedLocked() {
        assert Thread.holdsLock(mLock);

        if (mInitialized) {
            return;
        }

        // We must post to the UI thread to cover the case that the user
        // has invoked Chromium startup by using the (thread-safe)
        // CookieManager rather than creating a WebView.
        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                PathUtils.setPrivateDataDirectorySuffix("webview");
                // We don't need to extract any paks because for WebView, they are
                // in the system image.
                ResourceExtractor.setMandatoryPaksToExtract("");

                LibraryLoader.setLibraryToLoad("webviewchromium");

                // TODO: Ultimately we want to do this step in the zygote
                // process, so we should split this init step into two parts -
                // one generic bit that loads the library and another that performs
                // the app specific parts.
                LibraryLoader.loadAndInitSync();

                PathService.override(PathService.DIR_MODULE, "/system/lib/");
                // TODO: DIR_RESOURCE_PAKS_ANDROID needs to live somewhere sensible,
                // inlined here for simplicity setting up the HTMLViewer demo. Unfortunately
                // it can't go into base.PathService, as the native constant it refers to
                // lives in the ui/ layer. See ui/base/ui_base_paths.h
                final int DIR_RESOURCE_PAKS_ANDROID = 3003;
                PathService.override(DIR_RESOURCE_PAKS_ANDROID,
                        "/system/framework/webview/paks");

                AndroidBrowserProcess.initContentViewProcess(ActivityThread.currentApplication(),
                        AndroidBrowserProcess.MAX_RENDERERS_SINGLE_PROCESS);
            }
        });
        mInitialized = true;
    }

    @Override
    public Statics getStatics() {
        synchronized (mLock) {
            // TODO: Optimization potential: most these methods only need the native library
            // loaded, not the entire browser process initialized. See also http://b/7009882
            ensureChromiumNativeInitializedLocked();
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

                    // TODO: There's no @Override to keep the build green for folks building
                    // against jb-dev or an out of date master. At some point, add @Override.
                    public String getDefaultUserAgent(Context context) {
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
            ensureChromiumNativeInitializedLocked();
            ResourceProvider.registerResources(webView.getContext());
        }
        return new WebViewChromium(webView, privateAccess);
    }

    @Override
    public GeolocationPermissions getGeolocationPermissions() {
        UnimplementedWebViewApi.invoke();
        return null;
    }

    @Override
    public CookieManager getCookieManager() {
        synchronized (mLock) {
            ensureChromiumNativeInitializedLocked();
            if (mCookieManagerAdapter == null) {
                mCookieManagerAdapter = new CookieManagerAdapter(
                        new org.chromium.android_webview.CookieManager());
            }
        }
        return mCookieManagerAdapter;
    }

    @Override
    public WebIconDatabase getWebIconDatabase() {
        UnimplementedWebViewApi.invoke();
        return null;
    }

    @Override
    public WebStorage getWebStorage() {
        UnimplementedWebViewApi.invoke();
        return null;
    }

    @Override
    public WebViewDatabase getWebViewDatabase(Context context) {
        synchronized (mLock) {
            ensureChromiumNativeInitializedLocked();
        }
        UnimplementedWebViewApi.invoke();
        return null;
    }
}
