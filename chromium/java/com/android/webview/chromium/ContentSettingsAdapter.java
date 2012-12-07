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

import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebSettings.TextSize;
import android.webkit.WebSettings.ZoomDensity;

import org.chromium.content.browser.ContentSettings;
import org.chromium.android_webview.AwSettings;

public class ContentSettingsAdapter extends android.webkit.WebSettings {
    ContentSettings mContentSettings;
    AwSettings mAwSettings;

    public ContentSettingsAdapter(ContentSettings chromeSettings, AwSettings awSettings) {
        mContentSettings = chromeSettings;
        mAwSettings = awSettings;
    }

    @Override
    @Deprecated
    public void setNavDump(boolean enabled) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    @Deprecated
    public boolean getNavDump() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public void setSupportZoom(boolean support) {
        mContentSettings.setSupportZoom(support);
    }

    @Override
    public boolean supportZoom() {
        return mContentSettings.supportZoom();
    }

    @Override
    public void setBuiltInZoomControls(boolean enabled) {
        mContentSettings.setBuiltInZoomControls(enabled);
    }

    @Override
    public boolean getBuiltInZoomControls() {
        return mContentSettings.getBuiltInZoomControls();
    }

    @Override
    public void setDisplayZoomControls(boolean enabled) {
        mContentSettings.setDisplayZoomControls(enabled);
    }

    @Override
    public boolean getDisplayZoomControls() {
        return mContentSettings.getDisplayZoomControls();
    }

    @Override
    public void setAllowFileAccess(boolean allow) {
        mAwSettings.setAllowFileAccess(allow);
    }

    @Override
    public boolean getAllowFileAccess() {
        return mAwSettings.getAllowFileAccess();
    }

    @Override
    public void setAllowContentAccess(boolean allow) {
        mAwSettings.setAllowContentAccess(allow);
    }

    @Override
    public boolean getAllowContentAccess() {
        return mAwSettings.getAllowContentAccess();
    }

    @Override
    public void setLoadWithOverviewMode(boolean overview) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public boolean getLoadWithOverviewMode() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public void setEnableSmoothTransition(boolean enable) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public boolean enableSmoothTransition() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public void setUseWebViewBackgroundForOverscrollBackground(boolean view) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public boolean getUseWebViewBackgroundForOverscrollBackground() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public void setSaveFormData(boolean save) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public boolean getSaveFormData() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public void setSavePassword(boolean save) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public boolean getSavePassword() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public synchronized void setTextZoom(int textZoom) {
        mContentSettings.setTextZoom(textZoom);
    }

    @Override
    public synchronized int getTextZoom() {
        return mContentSettings.getTextZoom();
    }

    @Override
    public void setDefaultZoom(ZoomDensity zoom) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public ZoomDensity getDefaultZoom() {
        UnimplementedWebViewApi.invoke();
        return null;
    }

    @Override
    public void setLightTouchEnabled(boolean enabled) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public boolean getLightTouchEnabled() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public synchronized void setUserAgent(int ua) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized int getUserAgent() {
        UnimplementedWebViewApi.invoke();
        return 0;
    }

    @Override
    public synchronized void setUseWideViewPort(boolean use) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized boolean getUseWideViewPort() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public synchronized void setSupportMultipleWindows(boolean support) {
        mContentSettings.setSupportMultipleWindows(support);
    }

    @Override
    public synchronized boolean supportMultipleWindows() {
        return mContentSettings.supportMultipleWindows();
    }

    @Override
    public synchronized void setLayoutAlgorithm(LayoutAlgorithm l) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized LayoutAlgorithm getLayoutAlgorithm() {
        UnimplementedWebViewApi.invoke();
        return null;
    }

    @Override
    public synchronized void setStandardFontFamily(String font) {
        mContentSettings.setStandardFontFamily(font);
    }

    @Override
    public synchronized String getStandardFontFamily() {
        return mContentSettings.getStandardFontFamily();
    }

    @Override
    public synchronized void setFixedFontFamily(String font) {
        mContentSettings.setFixedFontFamily(font);
    }

    @Override
    public synchronized String getFixedFontFamily() {
        return mContentSettings.getFixedFontFamily();
    }

    @Override
    public synchronized void setSansSerifFontFamily(String font) {
        mContentSettings.setSansSerifFontFamily(font);
    }

    @Override
    public synchronized String getSansSerifFontFamily() {
        return mContentSettings.getSansSerifFontFamily();
    }

    @Override
    public synchronized void setSerifFontFamily(String font) {
        mContentSettings.setSerifFontFamily(font);
    }

    @Override
    public synchronized String getSerifFontFamily() {
        return mContentSettings.getSerifFontFamily();
    }

    @Override
    public synchronized void setCursiveFontFamily(String font) {
        mContentSettings.setCursiveFontFamily(font);
    }

    @Override
    public synchronized String getCursiveFontFamily() {
        return mContentSettings.getCursiveFontFamily();
    }

    @Override
    public synchronized void setFantasyFontFamily(String font) {
        mContentSettings.setFantasyFontFamily(font);
    }

    @Override
    public synchronized String getFantasyFontFamily() {
        return mContentSettings.getFantasyFontFamily();
    }

    @Override
    public synchronized void setMinimumFontSize(int size) {
        mContentSettings.setMinimumFontSize(size);
    }

    @Override
    public synchronized int getMinimumFontSize() {
        return mContentSettings.getMinimumFontSize();
    }

    @Override
    public synchronized void setMinimumLogicalFontSize(int size) {
        mContentSettings.setMinimumLogicalFontSize(size);
    }

    @Override
    public synchronized int getMinimumLogicalFontSize() {
        return mContentSettings.getMinimumLogicalFontSize();
    }

    @Override
    public synchronized void setDefaultFontSize(int size) {
        mContentSettings.setDefaultFontSize(size);
    }

    @Override
    public synchronized int getDefaultFontSize() {
        return mContentSettings.getDefaultFontSize();
    }

    @Override
    public synchronized void setDefaultFixedFontSize(int size) {
        mContentSettings.setDefaultFixedFontSize(size);
    }

    @Override
    public synchronized int getDefaultFixedFontSize() {
        return mContentSettings.getDefaultFixedFontSize();
    }

    @Override
    public synchronized void setLoadsImagesAutomatically(boolean flag) {
        mContentSettings.setLoadsImagesAutomatically(flag);
    }

    @Override
    public synchronized boolean getLoadsImagesAutomatically() {
        return mContentSettings.getLoadsImagesAutomatically();
    }

    @Override
    public synchronized void setBlockNetworkImage(boolean flag) {
        mContentSettings.setImagesEnabled(!flag);
    }

    @Override
    public synchronized boolean getBlockNetworkImage() {
        return !mContentSettings.getImagesEnabled();
    }

    @Override
    public synchronized void setBlockNetworkLoads(boolean flag) {
        mAwSettings.setBlockNetworkLoads(flag);
    }

    @Override
    public synchronized boolean getBlockNetworkLoads() {
        return mAwSettings.getBlockNetworkLoads();
    }

    @Override
    public synchronized void setJavaScriptEnabled(boolean flag) {
        mContentSettings.setJavaScriptEnabled(flag);
    }

    @Override
    public void setAllowUniversalAccessFromFileURLs(boolean flag) {
        mContentSettings.setAllowUniversalAccessFromFileURLs(flag);
    }

    @Override
    public void setAllowFileAccessFromFileURLs(boolean flag) {
        mContentSettings.setAllowFileAccessFromFileURLs(flag);
    }

    @Override
    public synchronized void setPluginsEnabled(boolean flag) {
        mContentSettings.setPluginsEnabled(flag);
    }

    @Override
    public synchronized void setPluginState(PluginState state) {
        mContentSettings.setPluginState(state);
    }

    @Override
    public synchronized void setDatabasePath(String databasePath) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized void setGeolocationDatabasePath(String databasePath) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized void setAppCacheEnabled(boolean flag) {
        mContentSettings.setAppCacheEnabled(flag);
    }

    @Override
    public synchronized void setAppCachePath(String appCachePath) {
        mContentSettings.setAppCachePath(appCachePath);
    }

    @Override
    public synchronized void setAppCacheMaxSize(long appCacheMaxSize) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized void setDatabaseEnabled(boolean flag) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized void setDomStorageEnabled(boolean flag) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized boolean getDomStorageEnabled() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public synchronized String getDatabasePath() {
        UnimplementedWebViewApi.invoke();
        return null;
    }

    @Override
    public synchronized boolean getDatabaseEnabled() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public synchronized void setGeolocationEnabled(boolean flag) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized boolean getJavaScriptEnabled() {
        return mContentSettings.getJavaScriptEnabled();
    }

    @Override
    public boolean getAllowUniversalAccessFromFileURLs() {
        return mContentSettings.getAllowUniversalAccessFromFileURLs();
    }

    @Override
    public boolean getAllowFileAccessFromFileURLs() {
        return mContentSettings.getAllowFileAccessFromFileURLs();
    }

    @Override
    public synchronized boolean getPluginsEnabled() {
        return mContentSettings.getPluginsEnabled();
    }

    @Override
    public synchronized PluginState getPluginState() {
        return mContentSettings.getPluginState();
    }

    @Override
    public synchronized void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {
        mContentSettings.setJavaScriptCanOpenWindowsAutomatically(flag);
    }

    @Override
    public synchronized boolean getJavaScriptCanOpenWindowsAutomatically() {
        return mContentSettings.getJavaScriptCanOpenWindowsAutomatically();
    }

    @Override
    public synchronized void setDefaultTextEncodingName(String encoding) {
        mContentSettings.setDefaultTextEncodingName(encoding);
    }

    @Override
    public synchronized String getDefaultTextEncodingName() {
        return mContentSettings.getDefaultTextEncodingName();
    }

    @Override
    public synchronized void setUserAgentString(String ua) {
        mContentSettings.setUserAgentString(ua);
    }

    @Override
    public synchronized String getUserAgentString() {
        return mContentSettings.getUserAgentString();
    }

    @Override
    public void setNeedInitialFocus(boolean flag) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public synchronized void setRenderPriority(RenderPriority priority) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void setCacheMode(int mode) {
        mAwSettings.setCacheMode(mode);
    }

    @Override
    public int getCacheMode() {
        return mAwSettings.getCacheMode();
    }
}
