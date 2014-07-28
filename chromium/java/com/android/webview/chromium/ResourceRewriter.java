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
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Helper class used to fix up resource ids.
 * This is mostly a copy of the code in frameworks/base/core/java/android/app/LoadedApk.java.
 * TODO: Remove if a cleaner mechanism is provided (either public API or AAPT is changed to generate
 * this code).
 */
class ResourceRewriter {

    public static void rewriteRValues(Context ctx) {
        // Rewrite the R 'constants' for all library apks.
        SparseArray<String> packageIdentifiers = ctx.getResources().getAssets()
                .getAssignedPackageIdentifiers();
        final int N = packageIdentifiers.size();
        for (int i = 0; i < N; i++) {
            final int id = packageIdentifiers.keyAt(i);
            final String name = packageIdentifiers.valueAt(i);
            if (id == 0x01 || id == 0x7f) {
                continue;
            }

            // TODO(mkosiba): We should use jarjar to remove the redundant R classes here, but due
            // to a bug in jarjar it's not possible to rename classes with '$' in their name.
            // See b/15684775.
            rewriteRValues(com.android.webview.chromium.R.class, id);
            rewriteRValues(org.chromium.content.R.class, id);
            rewriteRValues(org.chromium.ui.R.class, id);

            break;
        }

    }

    private static void rewriteIntField(Field field, int packageId) throws IllegalAccessException {
        int requiredModifiers = Modifier.STATIC | Modifier.PUBLIC;
        int bannedModifiers = Modifier.FINAL;

        int mod = field.getModifiers();
        if ((mod & requiredModifiers) != requiredModifiers ||
                (mod & bannedModifiers) != 0) {
            throw new IllegalArgumentException("Field " + field.getName() +
                    " is not rewritable");
        }

        if (field.getType() != int.class && field.getType() != Integer.class) {
            throw new IllegalArgumentException("Field " + field.getName() +
                    " is not an integer");
        }

        try {
            int resId = field.getInt(null);
            int newId = (resId & 0x00ffffff) | (packageId << 24);
            field.setInt(null, newId);
        } catch (IllegalAccessException e) {
            // This should not occur (we check above if we can write to it)
            throw new IllegalArgumentException(e);
        }
    }

    private static void rewriteIntArrayField(Field field, int packageId) {
        int requiredModifiers = Modifier.STATIC | Modifier.PUBLIC;

        if ((field.getModifiers() & requiredModifiers) != requiredModifiers) {
            throw new IllegalArgumentException("Field " + field.getName() +
                    " is not rewritable");
        }

        if (field.getType() != int[].class) {
            throw new IllegalArgumentException("Field " + field.getName() +
                    " is not an integer array");
        }

        try {
            int[] array = (int[]) field.get(null);
            for (int i = 0; i < array.length; i++) {
                array[i] = (array[i] & 0x00ffffff) | (packageId << 24);
            }
        } catch (IllegalAccessException e) {
            // This should not occur (we check above if we can write to it)
            throw new IllegalArgumentException(e);
        }
    }

    private static void rewriteRValues(final Class<?> rClazz, int id) {
        try {
            Class<?>[] declaredClasses = rClazz.getDeclaredClasses();
            for (Class<?> clazz : declaredClasses) {
                try {
                    if (clazz.getSimpleName().equals("styleable")) {
                        for (Field field : clazz.getDeclaredFields()) {
                            if (field.getType() == int[].class) {
                                rewriteIntArrayField(field, id);
                            }
                        }

                    } else {
                        for (Field field : clazz.getDeclaredFields()) {
                            rewriteIntField(field, id);
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to rewrite R values for " +
                            clazz.getName(), e);
                }
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to rewrite R values", e);
        }
    }

}
