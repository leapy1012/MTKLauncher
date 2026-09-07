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

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

/**
 * Oppo-style IME sync for the bottom ColorOS search pill.
 *
 * <p>While a ColorOS <em>search session</em> is active, this helper stays enabled even
 * when the IME is hidden (resting search like {@code oppo_search2.png}). Results are
 * settled just above the pill via {@code translationY}, matching
 * {@code SearchListUtils.getSearchListTargetTranslateY}.
 */
public final class ColorOsSearchImeHelper {

    private final View mSearchView;
    private final ActivityAllAppsContainerView<?> mAppsView;
    private boolean mEnabled;
    private float mSearchTranslationY;
    private int mLastImeBottom;

    public ColorOsSearchImeHelper(@NonNull View searchView,
            @NonNull ActivityAllAppsContainerView<?> appsView) {
        mSearchView = searchView;
        mAppsView = appsView;
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) {
            if (enabled) {
                refreshFromRootInsets();
            }
            return;
        }
        mEnabled = enabled;
        if (!enabled) {
            applySearchTranslationY(0f);
            applyResultsTranslationY(0f);
            mLastImeBottom = 0;
            mSearchView.setWindowInsetsAnimationCallback(null);
            return;
        }
        mSearchView.setWindowInsetsAnimationCallback(
                new WindowInsetsAnimation.Callback(
                        WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    @NonNull
                    @Override
                    public WindowInsets onProgress(@NonNull WindowInsets insets,
                            @NonNull List<WindowInsetsAnimation> runningAnimations) {
                        applyFromInsets(insets);
                        return insets;
                    }

                    @Override
                    public void onEnd(@NonNull WindowInsetsAnimation animation) {
                        refreshFromRootInsets();
                        settleResultsAfterLayout();
                    }
                });
        refreshFromRootInsets();
    }

    public float getSearchTranslationY() {
        return mSearchTranslationY;
    }

    public boolean isRaised() {
        return mSearchTranslationY != 0f;
    }

    /** Re-read root insets and re-apply (IME hide / layout pass). */
    public void refreshFromRootInsets() {
        if (!mEnabled) {
            return;
        }
        WindowInsets root = mSearchView.getRootWindowInsets();
        if (root != null) {
            applyFromInsets(root);
        } else {
            applySearchTranslationY(0f);
            applyResultsTranslationY(computeResultsTranslationY());
        }
    }

    /** Recompute results-list translation after the adapter item count changes. */
    public void onSearchResultsChanged() {
        if (!mEnabled) {
            return;
        }
        settleResultsAfterLayout();
    }

    /**
     * Re-apply pill + results settle after drawer layout passes that may have
     * cleared translationY (e.g. {@code layoutColorOsAppsBelowTabs}).
     */
    public void settleAfterHostLayout() {
        if (!mEnabled) {
            return;
        }
        refreshFromRootInsets();
        settleResultsAfterLayout();
    }

    private void settleResultsAfterLayout() {
        View searchRv = mAppsView.getSearchRecyclerView();
        Runnable apply = () -> {
            if (!mEnabled) {
                return;
            }
            applyResultsTranslationY(computeResultsTranslationY());
        };
        if (searchRv == null) {
            apply.run();
            return;
        }
        searchRv.post(apply);
        // After children bind/measure (frequent 2×5), settle again with real heights.
        searchRv.post(() -> searchRv.post(apply));
    }

    private void applyFromInsets(@NonNull WindowInsets insets) {
        if (!mEnabled) {
            return;
        }
        int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
        mLastImeBottom = ime;
        if (ime <= 0) {
            applySearchTranslationY(0f);
            applyResultsTranslationY(computeResultsTranslationY());
            return;
        }
        int gap = mSearchView.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_search_ime_gap);
        int bottomMargin = 0;
        ViewGroup.LayoutParams lp = mSearchView.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            bottomMargin = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
        }
        float searchTy = -Math.max(0, ime + gap - bottomMargin);
        applySearchTranslationY(searchTy);
        applyResultsTranslationY(computeResultsTranslationY());
    }

    /**
     * Oppo {@code SearchListUtils.getSearchListTargetTranslateY}: bottom-align the
     * result grid just above the search pill.
     */
    private float computeResultsTranslationY() {
        View searchRv = mAppsView.getSearchRecyclerView();
        if (searchRv == null || searchRv.getVisibility() != View.VISIBLE) {
            return 0f;
        }

        int gap = mSearchView.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_search_ime_gap);
        float searchTop = mSearchView.getTop() + mSearchView.getTranslationY();
        float targetContentBottom = searchTop - gap;

        int contentTopInRv = 0;
        int contentH;
        int laidOut = measureLaidOutContentHeight(searchRv);
        if (laidOut > 0 && searchRv instanceof RecyclerView
                && ((RecyclerView) searchRv).getChildCount() > 0) {
            // Child tops already include any RV padding; use span of icons only.
            RecyclerView rv = (RecyclerView) searchRv;
            contentTopInRv = Math.max(0, rv.getChildAt(0).getTop());
            contentH = laidOut;
        } else {
            contentH = estimateContentHeight(searchRv);
        }

        float desiredRvTy = targetContentBottom - contentH - contentTopInRv - searchRv.getTop();
        return Math.max(0f, desiredRvTy);
    }

    private static int measureLaidOutContentHeight(@NonNull View searchRv) {
        if (!(searchRv instanceof RecyclerView)) {
            return 0;
        }
        RecyclerView rv = (RecyclerView) searchRv;
        int n = rv.getChildCount();
        if (n <= 0) {
            return 0;
        }
        return Math.max(0, rv.getChildAt(n - 1).getBottom() - rv.getChildAt(0).getTop());
    }

    private int estimateContentHeight(@NonNull View searchRv) {
        ActivityContext ctx = ActivityContext.lookupContext(mSearchView.getContext());
        DeviceProfile dp = ctx.getDeviceProfile();
        int cols = Math.max(1, ColorOsDrawerColumns.resolve(mSearchView.getContext(), dp));
        int iconCount = 0;
        boolean onlyEmpty = false;
        if (searchRv instanceof RecyclerView) {
            RecyclerView.Adapter<?> adapter = ((RecyclerView) searchRv).getAdapter();
            if (adapter != null) {
                int n = adapter.getItemCount();
                if (n == 1 && adapter.getItemViewType(0)
                        == com.android.launcher3.allapps.BaseAllAppsAdapter
                        .VIEW_TYPE_EMPTY_SEARCH) {
                    onlyEmpty = true;
                } else {
                    for (int i = 0; i < n; i++) {
                        if (adapter.getItemViewType(i)
                                == com.android.launcher3.allapps.BaseAllAppsAdapter
                                .VIEW_TYPE_ICON) {
                            iconCount++;
                        }
                    }
                    if (iconCount == 0) {
                        iconCount = n;
                    }
                }
            }
        }
        if (onlyEmpty || iconCount <= 0) {
            return mSearchView.getResources().getDimensionPixelSize(
                    R.dimen.coloros_all_apps_search_empty_block_height);
        }
        int rows = Math.max(1, (iconCount + cols - 1) / cols);
        // Icon + label only — not full allAppsCellHeightPx (extra gap made ty clamp to 0).
        int textH = Utilities.calculateTextHeight(dp.allAppsIconTextSizePx);
        int cellFromProfile = dp.allAppsIconSizePx + dp.allAppsIconDrawablePaddingPx + textH;
        int cellMin = mSearchView.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_search_cell_height);
        // Cap so a 2-row frequent grid always fits above a raised pill.
        int cell = Math.min(Math.max(cellFromProfile, cellMin),
                Math.round(dp.allAppsCellHeightPx * 0.92f));
        return rows * cell;
    }

    private void applySearchTranslationY(float ty) {
        mSearchTranslationY = ty;
        mSearchView.setTranslationY(ty);
    }

    private void applyResultsTranslationY(float ty) {
        View searchRv = mAppsView.getSearchRecyclerView();
        if (searchRv != null) {
            searchRv.setTranslationY(ty);
        }
    }

    /** Clear IME raise / settle (session exit). */
    public void resetTranslation() {
        applySearchTranslationY(0f);
        applyResultsTranslationY(0f);
        mLastImeBottom = 0;
    }

    public static boolean isColorOs(@Nullable View v) {
        return v != null && v.getResources().getBoolean(R.bool.config_coloros_drawer);
    }
}
