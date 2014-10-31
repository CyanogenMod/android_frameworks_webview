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
import org.chromium.base.CommandLine;

import java.lang.reflect.Field;

/**
 * Controls data reduction proxy. This logic will be moved to upstream fully.
 */
public final class DataReductionProxyManager {

    // The setting Uri. Used when querying GoogleSettings.
    private static final Uri CONTENT_URI = Uri.parse("content://com.google.settings/partner");

    // Setting name for allowing data reduction proxy. Used when querying GoogleSettings.
    // Setting type: int ( 0 = disallow, 1 = allow )
    private static final String WEBVIEW_DATA_REDUCTION_PROXY = "use_webview_data_reduction_proxy";

    private static final String DRP_CLASS = "com.android.webview.chromium.Drp";
    private static final String TAG = "DataReductionProxySettingListener";

    // This is the same as Chromium data_reduction_proxy::switches::kEnableDataReductionProxy.
    private static final String ENABLE_DATA_REDUCTION_PROXY = "enable-spdy-proxy-auth";
    // This is the same as Chromium data_reduction_proxy::switches::kDataReductionProxyKey.
    private static final String DATA_REDUCTION_PROXY_KEY = "spdy-proxy-auth-value";

    /*
     * Listen for DataReductionProxySetting changes and take action.
     * TODO: This is the old mechanism. Will be obsolete after L release.
     * remove before release.
     */
    private static final class ProxySettingListener extends BroadcastReceiver {

        final String mKey;

        ProxySettingListener(final String key) {
            mKey = key;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            applyDataReductionProxySettingsAsync(context, mKey);
        }
    }

    private ProxySettingListener mProxySettingListener;

    private ContentObserver mProxySettingObserver;

    public DataReductionProxyManager() { }

    public void start(final Context context) {
        // This is the DRP key embedded in WebView apk.
        final String embeddedKey = readKey();

        // Developers could test DRP by passing ENABLE_DATA_REDUCTION_PROXY and (optionally)
        // DATA_REDUCTION_PROXY_KEY to the commandline switches.  In this case, we will try to
        // initialize DRP from commandline. And ignore user's preference.  If
        // DATA_REDUCTION_PROXY_KEY is specified in commandline, use it.  Otherwise, use the key
        // embedded in WebView apk.
        CommandLine cl = CommandLine.getInstance();
        if (cl.hasSwitch(ENABLE_DATA_REDUCTION_PROXY)) {
            String key = cl.getSwitchValue(DATA_REDUCTION_PROXY_KEY, embeddedKey);
            if (key == null || key.isEmpty()) {
                return;
            }

            // Now we will enable DRP because we've got a commandline switch to enable it.
            // We won't listen to Google Settings preference change because commandline switches
            // trump that.
            AwContentsStatics.setDataReductionProxyKey(key);
            AwContentsStatics.setDataReductionProxyEnabled(true);
            return;
        }

        // Now, there is no commandline switches to enable DRP, and reading the
        // DRP key from WebView apk failed. Just return and leave DRP disabled.
        if (embeddedKey == null || embeddedKey.isEmpty()) {
            return;
        }

        applyDataReductionProxySettingsAsync(context, embeddedKey);
        IntentFilter filter = new IntentFilter();
        filter.addAction(WebView.DATA_REDUCTION_PROXY_SETTING_CHANGED);
        mProxySettingListener = new ProxySettingListener(embeddedKey);
        context.registerReceiver(mProxySettingListener, filter);
        ContentResolver resolver = context.getContentResolver();
        mProxySettingObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                applyDataReductionProxySettingsAsync(context, embeddedKey);
            }
        };
        resolver.registerContentObserver(
                    Uri.withAppendedPath(CONTENT_URI, WEBVIEW_DATA_REDUCTION_PROXY),
                    false, mProxySettingObserver);
    }

    private String readKey() {
        try {
            Class<?> cls = Class.forName(DRP_CLASS);
            Field f = cls.getField("KEY");
            return (String) f.get(null);
        } catch (ClassNotFoundException ex) {
            Log.e(TAG, "No DRP key due to exception:" + ex);
        } catch (NoSuchFieldException ex) {
            Log.e(TAG, "No DRP key due to exception:" + ex);
        } catch (SecurityException ex) {
            Log.e(TAG, "No DRP key due to exception:" + ex);
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "No DRP key due to exception:" + ex);
        } catch (IllegalAccessException ex) {
            Log.e(TAG, "No DRP key due to exception:" + ex);
        } catch (NullPointerException ex) {
            Log.e(TAG, "No DRP key due to exception:" + ex);
        }
        return null;
    }

    private static void applyDataReductionProxySettingsAsync(final Context context,
            final String key) {
        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return isDataReductionProxyEnabled(context);
            }
            @Override
            protected void onPostExecute(Boolean enabled) {
                if (enabled) {
                    // Set the data reduction proxy key.
                    AwContentsStatics.setDataReductionProxyKey(key);
                }
                AwContentsStatics.setDataReductionProxyEnabled(enabled);
            }
        };
        task.execute();
    }

    private static boolean isDataReductionProxyEnabled(Context context) {
        return getProxySetting(context.getContentResolver(),
                    WEBVIEW_DATA_REDUCTION_PROXY) != 0;
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
}
