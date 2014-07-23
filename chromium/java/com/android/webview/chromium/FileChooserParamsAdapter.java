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

import android.content.Context;
import android.net.Uri;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebChromeClient.UploadHelper;

import org.chromium.android_webview.AwContentsClient;

public class FileChooserParamsAdapter extends FileChooserParams {
    private AwContentsClient.FileChooserParams mParams;
    private Context mContext;

    FileChooserParamsAdapter(AwContentsClient.FileChooserParams params, Context context) {
        mParams = params;
        mContext = context;
    }

    @Override
    public int getMode() {
        return mParams.mode;
    }

    @Override
    public String[] getAcceptTypes() {
        if (mParams.acceptTypes == null)
            return new String[0];
        return mParams.acceptTypes.split(";");
    }

    @Override
    public boolean isCaptureEnabled() {
        return mParams.capture;
    }

    @Override
    public CharSequence getTitle() {
        return mParams.title;
    }

    @Override
    public String getDefaultFilename() {
        return mParams.defaultFilename;
    }

    @Override
    public UploadHelper getUploadHelper() {
        return new UploadHelperImpl(mParams, mContext);
    }
}
