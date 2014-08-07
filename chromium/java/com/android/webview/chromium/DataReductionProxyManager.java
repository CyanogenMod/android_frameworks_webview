/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebView;

import org.chromium.android_webview.AwContentsStatics;

/**
 * Controls data reduction proxy. This logic will be moved to upstream fully.
 */
public final class DataReductionProxyManager {

    // The setting Uri. Used when querying GoogleSettings.
    private static final Uri CONTENT_URI = Uri.parse("content://com.google.settings/partner");

    // Setting name for allowing data reduction proxy. Used when querying GoogleSettings.
    // Setting type: int ( 0 = disallow, 1 = allow )
    private static final String WEBVIEW_DATA_REDUCTION_PROXY = "use_webview_data_reduction_proxy";

    private static final String TAG = "DataReductionProxySettingListener";

    /*
     * Listen for DataReductionProxySetting changes and take action.
     * TODO: This is the old mechanism. Will be obsolete after L release.
     * remove before release.
     */
    private static final class ProxySettingListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyDataReductionProxySettingsAsync(context);
        }
    }

    // Accessed from multiple threads.
    private volatile static boolean sOptedOutDataReductionProxy = false;

    private ProxySettingListener mProxySettingListener;

    private ContentObserver mProxySettingObserver;

    public DataReductionProxyManager() { }

    public void start(final Context context) {
        applyDataReductionProxySettingsAsync(context);
        IntentFilter filter = new IntentFilter();
        filter.addAction(WebView.DATA_REDUCTION_PROXY_SETTING_CHANGED);
        mProxySettingListener = new ProxySettingListener();
        context.registerReceiver(mProxySettingListener, filter);
        ContentResolver resolver = context.getContentResolver();
        mProxySettingObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                applyDataReductionProxySettingsAsync(context);
            }
        };
        resolver.registerContentObserver(
                    Uri.withAppendedPath(CONTENT_URI, WEBVIEW_DATA_REDUCTION_PROXY),
                    false, mProxySettingObserver);
    }

    private static class ProxySettings {
        boolean enabled;
        String key;
    }

    private static void applyDataReductionProxySettingsAsync(final Context context) {
        AsyncTask<Void, Void, ProxySettings> task = new AsyncTask<Void, Void, ProxySettings>() {
            @Override
            protected ProxySettings doInBackground(Void... params) {
                return readProxySettings(context);
            }
            @Override
            protected void onPostExecute(ProxySettings settings) {
                if (settings.enabled) {
                    // Set the data reduction proxy key.
                    AwContentsStatics.setDataReductionProxyKey(settings.key);
                }
                AwContentsStatics.setDataReductionProxyEnabled(settings.enabled);
            }
        };
        task.execute();
    }

    private static ProxySettings readProxySettings(Context context) {
        ProxySettings settings = new ProxySettings();
        settings.enabled = isDataReductionProxyEnabled(context);
        if (settings.enabled) {
            // Re-read the key in case it was updated.
            String key = Settings.Secure.getString(context.getContentResolver(),
                Settings.Global.WEBVIEW_DATA_REDUCTION_PROXY_KEY);
            if (key == null || key.isEmpty()) {
                Log.w(TAG, "No DRP key available");
                settings.enabled = false;
            }
            settings.key = key;
        }
        return settings;
    }

    private static boolean isDataReductionProxyEnabled(Context context) {
        if (sOptedOutDataReductionProxy) {
            return false;
        }

        boolean enabled = getProxySetting(context.getContentResolver(),
                    WEBVIEW_DATA_REDUCTION_PROXY) != 0;
        // intentional fallback. remove before L release.
        if (!enabled) {
            enabled = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.WEBVIEW_DATA_REDUCTION_PROXY, 0) != 0;
        }
        return enabled;
    }

    // Read query setting from GoogleSettings.
    private static int getProxySetting(ContentResolver resolver, String name) {
        String value = null;
        Cursor c = null;
        try {
            c = resolver.query(CONTENT_URI, new String[] { "value" },
                    "name=?", new String[]{ name }, null);
            if (c != null && c.moveToNext()) value = c.getString(0);
        } catch (SQLException e) {
            // SQL error: return null, but don't cache it.
            Log.e(TAG, "Can't get key " + name + " from " + CONTENT_URI, e);
        } finally {
            if (c != null) c.close();
        }
        int enabled = 0;
        try {
            if (value != null) {
                enabled = Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "cannot parse" + value, e);
        }
        return enabled;
    }

    // UI thread
    public static void optOutDataReductionProxy() {
        if (!sOptedOutDataReductionProxy) {
            sOptedOutDataReductionProxy = true;
            AwContentsStatics.setDataReductionProxyEnabled(false);
        }
    }
}
