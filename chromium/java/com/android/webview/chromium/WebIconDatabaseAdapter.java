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

import android.content.ContentResolver;
import android.webkit.WebIconDatabase;
import android.webkit.WebIconDatabase.IconListener;

/**
 * Chromium implementation of WebIconDatabase -- forwards calls to the
 * chromium internal implementation.
 */
final class WebIconDatabaseAdapter extends WebIconDatabase {
    @Override
    public void open(String path) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void close() {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void removeAllIcons() {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void requestIconForPageUrl(String url, IconListener listener) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void bulkRequestIconForPageUrl(ContentResolver cr, String where,
            IconListener listener) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void retainIconForPageUrl(String url) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void releaseIconForPageUrl(String url) {
        UnimplementedWebViewApi.invoke();
    }
}