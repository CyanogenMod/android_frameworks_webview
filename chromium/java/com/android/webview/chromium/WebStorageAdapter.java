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

import android.webkit.ValueCallback;
import android.webkit.WebStorage;

import java.util.Map;

/**
 * Chromium implementation of WebStorage -- forwards calls to the
 * chromium internal implementation.
 */
final class WebStorageAdapter extends WebStorage {
    @Override
    public void getOrigins(ValueCallback<Map> callback) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void getUsageForOrigin(String origin, ValueCallback<Long> callback) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void getQuotaForOrigin(String origin, ValueCallback<Long> callback) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void setQuotaForOrigin(String origin, long quota) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void deleteOrigin(String origin) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void deleteAllData() {
        UnimplementedWebViewApi.invoke();
    }
}
