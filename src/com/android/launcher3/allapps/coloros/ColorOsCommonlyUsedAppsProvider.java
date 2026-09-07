package com.android.launcher3.allapps.coloros;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Top apps for ColorOS empty-search (Oppo predicted row / LeftScreen
 * {@code Commonly used application}).
 *
 * <p>Same ranking as {@code UsageStatsController}: last-used via
 * {@link UsageStatsManager}, mapped onto drawer {@link AppInfo}s, padded to
 * {@link #TARGET_COUNT}. Does not depend on minus-screen UI / OverlayContainer.
 */
public final class ColorOsCommonlyUsedAppsProvider {

    /** Matches LeftScreen {@code UsageStatsController.TARGET_STATS_APP_SIZE}. */
    public static final int TARGET_COUNT = 10;

    private ColorOsCommonlyUsedAppsProvider() {}

    /**
     * Build up to {@link #TARGET_COUNT} adapter items for empty search.
     * Safe to call off the main thread.
     */
    @WorkerThread
    @NonNull
    public static ArrayList<AdapterItem> build(@NonNull Context context,
            @Nullable AllAppsStore store) {
        ArrayList<AdapterItem> out = new ArrayList<>(TARGET_COUNT);
        if (store == null) {
            return out;
        }
        AppInfo[] all = store.getApps();
        if (all == null || all.length == 0) {
            return out;
        }

        List<AppInfo> prioritized = mapUsageStatsToApps(context, all);
        Set<ComponentKey> used = new HashSet<>();
        for (AppInfo info : prioritized) {
            if (info == null || info.componentName == null) {
                continue;
            }
            ComponentKey key = new ComponentKey(info.componentName, info.user);
            if (!used.add(key)) {
                continue;
            }
            out.add(AdapterItem.asApp(info));
            if (out.size() >= TARGET_COUNT) {
                return out;
            }
        }
        // Pad with remaining drawer apps (store order) like LeftScreen.
        for (AppInfo info : all) {
            if (info == null || info.componentName == null) {
                continue;
            }
            ComponentKey key = new ComponentKey(info.componentName, info.user);
            if (!used.add(key)) {
                continue;
            }
            out.add(AdapterItem.asApp(info));
            if (out.size() >= TARGET_COUNT) {
                break;
            }
        }
        return out;
    }

    @NonNull
    private static List<AppInfo> mapUsageStatsToApps(@NonNull Context context,
            @NonNull AppInfo[] all) {
        UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
        if (usm == null) {
            return Collections.emptyList();
        }
        List<UsageStats> stats;
        try {
            stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, 0L,
                    System.currentTimeMillis());
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
        if (stats == null || stats.isEmpty()) {
            return Collections.emptyList();
        }

        List<UsageStats> sorted = new ArrayList<>(stats);
        Collections.sort(sorted, Comparator.comparingLong(UsageStats::getLastTimeUsed)
                .reversed());

        PackageManager pm = context.getPackageManager();
        List<AppInfo> prioritized = new ArrayList<>();
        Set<String> seenPkgs = new HashSet<>();
        for (UsageStats stat : sorted) {
            String pkg = stat.getPackageName();
            if (TextUtils.isEmpty(pkg) || !seenPkgs.add(pkg)) {
                continue;
            }
            AppInfo match = null;
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                ResolveInfo ri = pm.resolveActivity(launch, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri != null && ri.activityInfo != null) {
                    String flat = ri.activityInfo.getComponentName().flattenToString();
                    for (AppInfo info : all) {
                        if (info.componentName != null
                                && flat.equals(info.componentName.flattenToString())) {
                            match = info;
                            break;
                        }
                    }
                }
            }
            if (match == null) {
                for (AppInfo info : all) {
                    if (info.componentName != null
                            && pkg.equals(info.componentName.getPackageName())) {
                        match = info;
                        break;
                    }
                }
            }
            if (match != null) {
                prioritized.add(match);
                if (prioritized.size() >= TARGET_COUNT) {
                    break;
                }
            }
        }
        return prioritized;
    }
}
