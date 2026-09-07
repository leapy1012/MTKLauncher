/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.launcher3.allapps.coloros;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm;
import com.android.launcher3.model.data.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * ColorOS drawer title search: contiguous case-insensitive {@code contains} only.
 *
 * <p>Does <em>not</em> use ordered-subsequence fuzzy matching (that matched
 * {@code Dd} → "Child mode" via Chil*d* mo*d*e) or package-name matching
 * ({@code com.hxy.chlidmode}).
 */
public final class ColorOsDefaultAppSearchAlgorithm extends DefaultAppSearchAlgorithm {

    private static final String TAG = "ColorOsAppSearch";

    public ColorOsDefaultAppSearchAlgorithm(Context context) {
        super(context, true);
    }

    @Override
    @AnyThread
    public ArrayList<AdapterItem> getTitleMatchResult(List<AppInfo> apps, @NonNull String query) {
        final String queryLower = query.trim().toLowerCase(Locale.getDefault());
        final ArrayList<AdapterItem> result = new ArrayList<>();
        if (TextUtils.isEmpty(queryLower) || apps == null) {
            return result;
        }

        for (int i = 0, total = apps.size(); i < total; i++) {
            AppInfo info = apps.get(i);
            if (info == null || info.title == null) {
                continue;
            }
            String title = info.title.toString();
            String titleLower = title.toLowerCase(Locale.getDefault());
            // Contiguous substring only — highlight token must also exist in title.
            if (titleLower.contains(queryLower)) {
                AdapterItem item = AdapterItem.asApp(info);
                item.searchHighlightContent = Collections.singletonList(query.trim());
                result.add(item);
            }
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "query=\"" + queryLower + "\" hits=" + result.size());
        }
        return result;
    }
}
