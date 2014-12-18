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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LicenseContentProvider extends ContentProvider {
    public static final String LICENSES_URI =
            "content://com.android.webview.chromium.LicenseContentProvider/webview_licenses";
    public static final String LICENSES_CONTENT_TYPE = "text/html";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) {
        if (uri != null && LICENSES_URI.compareTo(uri.toString()) == 0) {
            try {
                return extractAsset("webview_licenses.notice");
            } catch (IOException e) {
                Log.e("WebView", "Failed to open the license file", e);
            }
        }
        return null;
    }

    // This is to work around the known limitation of AssetManager.openFd to refuse
    // opening files that are compressed in the apk file.
    private AssetFileDescriptor extractAsset(String name) throws IOException {
        File extractedFile = new File(getContext().getCacheDir(), name);
        if (!extractedFile.exists()) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                inputStream = getContext().getAssets().open(name);
                outputStream = new BufferedOutputStream(
                        new FileOutputStream(extractedFile.getAbsolutePath()));
                copyStreams(inputStream, outputStream);
            } finally {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            }
        }
        ParcelFileDescriptor parcelFd =
                ParcelFileDescriptor.open(extractedFile, ParcelFileDescriptor.MODE_READ_ONLY);
        if (parcelFd != null) {
            return new AssetFileDescriptor(parcelFd, 0, parcelFd.getStatSize());
        }
        return null;
    }

    private static void copyStreams(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int c;
        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
    }

    @Override
    public String getType(Uri uri) {
        if (uri != null && LICENSES_URI.compareTo(uri.toString()) == 0) {
            return LICENSES_CONTENT_TYPE;
        }
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where,
                      String[] whereArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }
}
