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

import java.lang.reflect.Field;

public class ResourceProvider {
    private static boolean sInitialized;

    static void registerResources(Context context) {
        if (sInitialized) {
            return;
        }

        AwResource.setResources(context.getResources());

        Resources.Theme theme = context.getTheme();

        // dimen

        org.chromium.content.R.dimen.link_preview_overlay_radius =
                com.android.internal.R.dimen.webviewchromium_link_preview_overlay_radius;

        verifyFields(org.chromium.content.R.dimen.class);

        // drawable

        org.chromium.content.R.drawable.ic_menu_share_holo_light =
                resolveThemeAttr(theme, com.android.internal.R.attr.actionModeShareDrawable);
        org.chromium.content.R.drawable.ic_menu_search_holo_light =
                resolveThemeAttr(theme, com.android.internal.R.attr.actionModeWebSearchDrawable);
        org.chromium.content.R.drawable.ondemand_overlay =
                com.android.internal.R.drawable.webviewchromium_ondemand_overlay;

        verifyFields(org.chromium.content.R.drawable.class);

        //id

        org.chromium.content.R.id.month = com.android.internal.R.id.webviewchromium_month;
        org.chromium.content.R.id.year = com.android.internal.R.id.webviewchromium_year;
        org.chromium.content.R.id.pickers = com.android.internal.R.id.webviewchromium_pickers;
        org.chromium.content.R.id.date_picker = com.android.internal.R.id.webviewchromium_date_picker;
        org.chromium.content.R.id.time_picker = com.android.internal.R.id.webviewchromium_time_picker;

        verifyFields(org.chromium.content.R.id.class);

        // layout

        org.chromium.content.R.layout.date_time_picker_dialog =
                com.android.internal.R.layout.webviewchromium_date_time_picker_dialog;
        org.chromium.content.R.layout.month_picker =
                com.android.internal.R.layout.webviewchromium_month_picker;

        verifyFields(org.chromium.content.R.layout.class);

        // string

        org.chromium.content.R.string.accessibility_content_view =
                com.android.internal.R.string.webviewchromium_accessibility_content_view;
        org.chromium.content.R.string.accessibility_date_picker_month =
                com.android.internal.R.string.webviewchromium_accessibility_date_picker_month;
        org.chromium.content.R.string.accessibility_date_picker_year =
                com.android.internal.R.string.webviewchromium_accessibility_date_picker_year;
        org.chromium.content.R.string.accessibility_datetime_picker_date =
                com.android.internal.R.string.webviewchromium_accessibility_datetime_picker_date;
        org.chromium.content.R.string.accessibility_datetime_picker_time =
                com.android.internal.R.string.webviewchromium_accessibility_datetime_picker_time;
        org.chromium.content.R.string.actionbar_share =
                com.android.internal.R.string.share;
        org.chromium.content.R.string.actionbar_web_search =
                com.android.internal.R.string.websearch;
        org.chromium.content.R.string.date_picker_dialog_clear =
                com.android.internal.R.string.webviewchromium_date_picker_dialog_clear;
        org.chromium.content.R.string.date_picker_dialog_set =
                com.android.internal.R.string.webviewchromium_date_picker_dialog_set;
        org.chromium.content.R.string.date_picker_dialog_title =
                com.android.internal.R.string.webviewchromium_date_picker_dialog_title;
        org.chromium.content.R.string.date_time_picker_dialog_title =
                com.android.internal.R.string.webviewchromium_date_time_picker_dialog_title;
        org.chromium.content.R.string.media_player_error_button =
                com.android.internal.R.string.webviewchromium_media_player_error_button;
        org.chromium.content.R.string.media_player_error_text_invalid_progressive_playback =
                com.android.internal.R.string.webviewchromium_media_player_error_text_invalid_progressive_playback;
        org.chromium.content.R.string.media_player_error_text_unknown =
                com.android.internal.R.string.webviewchromium_media_player_error_text_unknown;
        org.chromium.content.R.string.media_player_error_title =
                com.android.internal.R.string.webviewchromium_media_player_error_title;
        org.chromium.content.R.string.media_player_loading_video =
                com.android.internal.R.string.webviewchromium_media_player_loading_video;
        org.chromium.content.R.string.month_picker_dialog_title =
                com.android.internal.R.string.webviewchromium_month_picker_dialog_title;

        verifyFields(org.chromium.content.R.string.class);

        // Resources needed by android_webview/

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

    // Verify that all the fields defined in |R| have a valid mapping. This
    // ensures that if a resource is added upstream, we won't miss providing
    // a mapping downstream.
    private static void verifyFields(Class<?> R) {
        Field[] fields = R.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                if (fields[i].getInt(null) == 0) {
                    throw new RuntimeException("Missing resource mapping for " +
                            R.getName() + "." + fields[i].getName());
                }
            } catch (IllegalAccessException e) { }
        }
    }
}
