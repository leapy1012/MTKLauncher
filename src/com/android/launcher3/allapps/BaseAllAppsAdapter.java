/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.touch.ItemLongClickListener.INSTANCE_ALL_APPS;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.List;
import java.util.Objects;

/**
 * Adapter for all the apps.
 *
 * @param <T> Type of context inflating all apps.
 */
public abstract class BaseAllAppsAdapter<T extends Context & ActivityContext> extends
        RecyclerView.Adapter<BaseAllAppsAdapter.ViewHolder> {

    public static final String TAG = "BaseAllAppsAdapter";

    // A normal icon
    public static final int VIEW_TYPE_ICON = 1 << 1;
    // The message shown when there are no filtered results
    public static final int VIEW_TYPE_EMPTY_SEARCH = 1 << 2;
    // A divider that separates the apps list and the search market button
    public static final int VIEW_TYPE_ALL_APPS_DIVIDER = 1 << 3;

    public static final int VIEW_TYPE_WORK_EDU_CARD = 1 << 4;
    public static final int VIEW_TYPE_WORK_DISABLED_CARD = 1 << 5;

    public static final int NEXT_ID = 6;

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_ICON;

    protected final SearchAdapterProvider<?> mAdapterProvider;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View v) {
            super(v);
        }
    }

    /** Sets the number of apps to be displayed in one row of the all apps screen. */
    public abstract void setAppsPerRow(int appsPerRow);

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        /** Common properties */
        // The type of this item
        public final int viewType;

        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated ItemInfoWithIcon for the item
        public AppInfo itemInfo = null;
        /**
         * Oppo {@code searchHighlightContent}: query substrings to bold in the
         * icon label while showing search results. Null outside search.
         */
        @Nullable public List<String> searchHighlightContent = null;

        public AdapterItem(int viewType) {
            this.viewType = viewType;
        }

        /**
         * Factory method for AppIcon AdapterItem
         */
        public static AdapterItem asApp(AppInfo appInfo) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_ICON);
            item.itemInfo = appInfo;
            return item;
        }

        protected boolean isCountedForAccessibility() {
            return viewType == VIEW_TYPE_ICON;
        }

        /**
         * Returns true if the items represent the same object
         */
        public boolean isSameAs(AdapterItem other) {
            if (other.viewType != viewType || other.getClass() != getClass()) {
                return false;
            }
            // Icon identity by component+user so DiffUtil can emit MOVE ops on sort.
            if (viewType == VIEW_TYPE_ICON) {
                if (itemInfo == null || other.itemInfo == null) {
                    return itemInfo == other.itemInfo;
                }
                return Objects.equals(itemInfo.componentName, other.itemInfo.componentName)
                        && Objects.equals(itemInfo.user, other.itemInfo.user);
            }
            return true;
        }

        /**
         * This is called only if {@link #isSameAs} returns true to check if the contents are same
         * as well. Returning true will prevent redrawing of thee item.
         */
        public boolean isContentSame(AdapterItem other) {
            if (viewType == VIEW_TYPE_ICON) {
                // Same AppInfo instance after reorder → animate move only, no rebind.
                // Highlight query must also match or DiffUtil would skip a needed rebind.
                return itemInfo == other.itemInfo
                        && Objects.equals(searchHighlightContent, other.searchHighlightContent);
            }
            return itemInfo == null && other.itemInfo == null;
        }

        /** Sets the alpha of the decorator for this item. Returns true if successful. */
        public boolean setDecorationFillAlpha(int alpha) {
            return false;
        }
    }

    protected final T mActivityContext;
    protected final AlphabeticalAppsList<T> mApps;
    // The text to show when there are no search results and no market search handler.
    protected int mAppsPerRow;

    protected final LayoutInflater mLayoutInflater;
    protected final OnClickListener mOnIconClickListener;
    protected OnLongClickListener mOnIconLongClickListener = INSTANCE_ALL_APPS;
    protected OnFocusChangeListener mIconFocusListener;
    private final int mExtraTextHeight;

    public BaseAllAppsAdapter(T activityContext, LayoutInflater inflater,
            AlphabeticalAppsList<T> apps, SearchAdapterProvider<?> adapterProvider) {
        Resources res = activityContext.getResources();
        mActivityContext = activityContext;
        mApps = apps;
        mLayoutInflater = inflater;

        mOnIconClickListener = mActivityContext.getItemOnClickListener();

        mAdapterProvider = adapterProvider;
        mExtraTextHeight = Utilities.calculateTextHeight(
                mActivityContext.getDeviceProfile().allAppsIconTextSizePx);
    }

    /**
     * Sets the long click listener for icons
     */
    public void setOnIconLongClickListener(@Nullable OnLongClickListener listener) {
        mOnIconLongClickListener = listener;
    }

    /** Checks if the passed viewType represents all apps divider. */
    public static boolean isDividerViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_DIVIDER);
    }

    /** Checks if the passed viewType represents all apps icon. */
    public static boolean isIconViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_ICON);
    }

    public void setIconFocusListener(OnFocusChangeListener focusListener) {
        mIconFocusListener = focusListener;
    }

    /**
     * Returns the layout manager.
     */
    public abstract RecyclerView.LayoutManager getLayoutManager();

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ICON:
                int layout = !FeatureFlags.ENABLE_TWOLINE_ALLAPPS.get() ? R.layout.all_apps_icon
                        : R.layout.all_apps_icon_twoline;
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        layout, parent, false);
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(mIconFocusListener);
                icon.setOnClickListener(mOnIconClickListener);
                icon.setOnLongClickListener(mOnIconLongClickListener);
                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                // All apps bg transparent need change text color same with workspace start
                if (android.os.SystemProperties.getBoolean("ro.launcher.allapp.bgtransp",false)) {
                    icon.setTextAppearance(R.style.TransBgAppIcon);
                }
                // All apps bg transparent need change text color same with workspace end                
                icon.getLayoutParams().height =
                        mActivityContext.getDeviceProfile().allAppsCellHeightPx;
                if (FeatureFlags.ENABLE_TWOLINE_ALLAPPS.get()) {
                    icon.getLayoutParams().height += mExtraTextHeight;
                }
                return new ViewHolder(icon);
            case VIEW_TYPE_EMPTY_SEARCH:
                int emptyLayout = mActivityContext.getResources().getBoolean(
                        R.bool.config_coloros_drawer)
                        ? R.layout.coloros_all_apps_empty_search
                        : R.layout.all_apps_empty_search;
                return new ViewHolder(mLayoutInflater.inflate(emptyLayout, parent, false));
            case VIEW_TYPE_ALL_APPS_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.all_apps_divider, parent, false));
            case VIEW_TYPE_WORK_EDU_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_edu, parent, false));
            case VIEW_TYPE_WORK_DISABLED_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_paused, parent, false));
            default:
                if (mAdapterProvider.isViewSupported(viewType)) {
                    return mAdapterProvider.onCreateViewHolder(mLayoutInflater, parent, viewType);
                }
                throw new RuntimeException("Unexpected view type" + viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_ICON: {
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                // Oppo isNeedResetIcon: skip reset when sort icon-change anim is armed so
                // the previous FastBitmapDrawable remains available for crossfade.
                if (!icon.getIsNeedIconChangeAnim()) {
                    icon.reset();
                }
                // Oppo BaseAllAppsAdapter: bold matching query substrings in search results.
                icon.setSearchHighlightContent(adapterItem.searchHighlightContent);
                icon.applyFromApplicationInfo(adapterItem.itemInfo);
                break;
            }
            case VIEW_TYPE_EMPTY_SEARCH: {
                AppInfo info = mApps.getAdapterItems().get(position).itemInfo;
                TextView empty = holder.itemView.findViewById(R.id.empty_text);
                if (empty == null && holder.itemView instanceof TextView) {
                    empty = (TextView) holder.itemView;
                }
                if (empty != null) {
                    if (mActivityContext.getResources().getBoolean(R.bool.config_coloros_drawer)) {
                        // Oppo: short "No results" (no query echo).
                        empty.setText(R.string.coloros_all_apps_no_search_results);
                    } else if (info != null) {
                        empty.setText(mActivityContext.getString(
                                R.string.all_apps_no_search_results, info.title));
                    }
                }
                View lottie = holder.itemView.findViewById(R.id.coloros_search_empty_lottie);
                if (lottie instanceof com.airbnb.lottie.LottieAnimationView) {
                    com.airbnb.lottie.LottieAnimationView lav =
                            (com.airbnb.lottie.LottieAnimationView) lottie;
                    if (!lav.isAnimating()) {
                        lav.playAnimation();
                    }
                }
                break;
            }
            case VIEW_TYPE_ALL_APPS_DIVIDER:
            case VIEW_TYPE_WORK_DISABLED_CARD:
                // nothing to do
                break;
            case VIEW_TYPE_WORK_EDU_CARD:
                ((WorkEduCard) holder.itemView).setPosition(position);
                break;
            default:
                if (mAdapterProvider.isViewSupported(holder.getItemViewType())) {
                    mAdapterProvider.onBindView(holder, position);
                }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (holder.getItemViewType() == VIEW_TYPE_ICON
                && holder.itemView instanceof BubbleTextView) {
            boolean sortIconChange = false;
            for (Object payload : payloads) {
                if (payload instanceof Integer
                        && (Integer) payload
                        == AlphabeticalAppsList.VIEW_TYPE_ICON_CHANGE_APPSORT_PAYLOAD) {
                    sortIconChange = true;
                    break;
                }
            }
            // Arm before rebind (Oppo BaseAllAppsAdapter.onBindViewHolder payloads).
            ((BubbleTextView) holder.itemView).setIsNeedIconChangeAnim(sortIconChange);
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
        // Always recycle and we will reset the view when it is bound
        return true;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    protected static boolean isViewType(int viewType, int viewTypeMask) {
        return (viewType & viewTypeMask) != 0;
    }

}
