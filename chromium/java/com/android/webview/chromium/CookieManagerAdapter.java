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

import android.net.WebAddress;

public class CookieManagerAdapter extends android.webkit.CookieManager {

    org.chromium.android_webview.CookieManager mChromeCookieManager;

    public CookieManagerAdapter(org.chromium.android_webview.CookieManager chromeCookieManager) {
        mChromeCookieManager = chromeCookieManager;
    }

    @Override
    public synchronized void setAcceptCookie(boolean accept) {
        mChromeCookieManager.setAcceptCookie(accept);
    }

    @Override
    public synchronized boolean acceptCookie() {
        return mChromeCookieManager.acceptCookie();
    }

    @Override
    public void setCookie(String url, String value) {
        mChromeCookieManager.setCookie(url, value);
    }

    @Override
    public String getCookie(String url) {
        return mChromeCookieManager.getCookie(url);
    }

    @Override
    public String getCookie(String url, boolean privateBrowsing) {
        return mChromeCookieManager.getCookie(url);
    }

    @Override
    public synchronized String getCookie(WebAddress uri) {
        return mChromeCookieManager.getCookie(uri.toString());
    }

    @Override
    public void removeSessionCookie() {
        mChromeCookieManager.removeSessionCookie();
    }

    @Override
    public void removeAllCookie() {
        mChromeCookieManager.removeAllCookie();
    }

    @Override
    public synchronized boolean hasCookies() {
        return mChromeCookieManager.hasCookies();
    }

    @Override
    public synchronized boolean hasCookies(boolean privateBrowsing) {
        return mChromeCookieManager.hasCookies();
    }

    @Override
    public void removeExpiredCookie() {
        mChromeCookieManager.removeExpiredCookie();
    }

    @Override
    protected void flushCookieStore() {
        mChromeCookieManager.flushCookieStore();
    }

    @Override
    protected boolean allowFileSchemeCookiesImpl() {
        return mChromeCookieManager.allowFileSchemeCookies();
    }

    @Override
    protected void setAcceptFileSchemeCookiesImpl(boolean accept) {
        mChromeCookieManager.setAcceptFileSchemeCookies(accept);
    }
}
