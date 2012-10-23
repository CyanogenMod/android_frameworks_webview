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

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import org.chromium.android_webview.AwResource;
import org.chromium.content.app.AppResource;

public class ResourceProvider {
    private static boolean sInitialized;

    static void registerResources(Context context) {
        if (sInitialized) {
            return;
        }

        Resources.Theme theme = context.getTheme();

        AppResource.DRAWABLE_ICON_ACTION_BAR_SHARE =
                resolveThemeAttr(theme, com.android.internal.R.attr.actionModeShareDrawable);
        AppResource.DRAWABLE_ICON_ACTION_BAR_WEB_SEARCH =
                resolveThemeAttr(theme, com.android.internal.R.attr.actionModeWebSearchDrawable);

        AppResource.STRING_ACTION_BAR_SHARE = com.android.internal.R.string.share;
        AppResource.STRING_ACTION_BAR_WEB_SEARCH = com.android.internal.R.string.websearch;

        AwResource.setResources(context.getResources());

        AwResource.RAW_LOAD_ERROR = com.android.internal.R.raw.loaderror;
        AwResource.RAW_NO_DOMAIN = com.android.internal.R.raw.nodomain;

        AwResource.STRING_DEFAULT_TEXT_ENCODING =
                com.android.internal.R.string.default_text_encoding;

        sInitialized = true;
    }

    private static int resolveThemeAttr(Resources.Theme theme, int attr) {
        TypedValue valueHolder = new TypedValue();
        theme.resolveAttribute(attr, valueHolder, true);
        return valueHolder.resourceId;
    }
}
