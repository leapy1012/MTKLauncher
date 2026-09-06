package com.android.launcher3.allapps.coloros;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customize.overlay.controller.CategoryController;
import com.android.customize.overlay.model.CategoryInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.AlphabeticalAppsList.FastScrollSectionInfo;
import com.android.launcher3.allapps.BaseAllAppsAdapter;
import com.android.launcher3.allapps.search.AppsSearchContainerLayout;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.settings.SettingsActivity;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.coui.appcompat.poplist.COUIPopupListWindow;
import com.coui.appcompat.poplist.PopupListItem;
import com.coui.appcompat.segmentbutton.COUISegmentButtonLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ColorOS drawer chrome: COUI segment tabs + reliable letter rail + overflow menu.
 */
public final class ColorOsDrawerChrome {

    private static final int MENU_ID_SELECT = 0;
    private static final int MENU_ID_SORT = 1;
    private static final int MENU_ID_SETTINGS = 2;
    /** Oppo OplusCategoryPagedView snap duration. */
    private static final long PAGE_SWITCH_MS = 600L;
    private static final PathInterpolator PAGE_SWITCH_INTERPOLATOR =
            new PathInterpolator(0.3f, 0f, 0.1f, 1f);

    private final ActivityAllAppsContainerView<?> mContainer;
    private final Launcher mLauncher;

    private View mTabHeader;
    private COUISegmentButtonLayout mSegment;
    private ColorOsLetterRail mLetterIndex;
    private ColorOsLetterClusterOverlay mLetterCluster;
    private RecyclerView mCategoryList;
    private View mTopFadeOverlay;
    private View mBottomFadeOverlay;
    private ColorOsCategoryAdapter mCategoryAdapter;
    private boolean mShowingCategories;
    private boolean mPageAnimating;
    /** Ignore segment callbacks while swipe syncs the pill. */
    private boolean mSuppressSegmentCallback;
    /** Section shown in the last cluster filter; used to land the list on dismiss. */
    @Nullable private String mLastClusterSection;
    private final Rect mInsets = new Rect();

    private COUIPopupListWindow mPopupWindow;
    private final ArrayList<PopupListItem> mPopupItems = new ArrayList<>();

    // Oppo All↔Categories horizontal page swipe (OplusCategoryPagedView).
    private int mTouchSlop;
    private int mMinFlingVelocity;
    private float mSwipeDownX;
    private float mSwipeDownY;
    private boolean mSwipeTracking;
    private boolean mSwipeIntercepted;
    @Nullable private VelocityTracker mVelocityTracker;

    private final CategoryController mCategoryController = new CategoryController();

    public ColorOsDrawerChrome(ActivityAllAppsContainerView<?> container) {
        mContainer = container;
        mLauncher = Launcher.cast(Launcher.getLauncher(container.getContext()));
    }

    public static boolean isEnabled(Context context) {
        return context.getResources().getBoolean(R.bool.config_coloros_drawer);
    }

    /** True while the Categories page is the active drawer page. */
    public boolean isShowingCategories() {
        return mShowingCategories;
    }

    /**
     * Oppo/AOSP All-apps dismiss gate for the Categories list: allow the parent
     * drawer to pull down only when the category grid is scrolled to the top
     * (same rule as {@link AllAppsRecyclerView} / {@code computeVerticalScrollOffset()==0}).
     */
    public boolean shouldContainerScroll(MotionEvent ev) {
        if (mCategoryList == null || mCategoryList.getVisibility() != View.VISIBLE) {
            return true;
        }
        View search = mContainer.getSearchView();
        if (search != null && mLauncher.getDragLayer().isEventOverView(search, ev)) {
            return true;
        }
        if (!mLauncher.getDragLayer().isEventOverView(mCategoryList, ev)
                && !mLauncher.getDragLayer().isEventOverView(mContainer, ev)) {
            return true;
        }
        // Still scrolling the category grid — do not dismiss the drawer.
        return mCategoryList.computeVerticalScrollOffset() == 0;
    }

    private void initPageSwipeGesture() {
        ViewConfiguration vc = ViewConfiguration.get(mContainer.getContext());
        mTouchSlop = vc.getScaledTouchSlop();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
    }

    /**
     * Oppo {@code OplusCategoryPagedView}: horizontal swipe switches All ↔ Categories.
     * Returns true when this gesture should own the stream.
     */
    public boolean onInterceptPageSwipe(MotionEvent ev) {
        if (mPageAnimating || mTabHeader == null) {
            return false;
        }
        if (mLetterCluster != null && mLetterCluster.isShowing()) {
            return false;
        }
        // Don't steal taps on the segment / overflow menu.
        if (isEventOverChrome(ev)) {
            return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mSwipeDownX = ev.getX();
                mSwipeDownY = ev.getY();
                mSwipeTracking = true;
                mSwipeIntercepted = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mSwipeTracking || mSwipeIntercepted) {
                    break;
                }
                float dx = ev.getX() - mSwipeDownX;
                float dy = ev.getY() - mSwipeDownY;
                if (Math.abs(dx) > mTouchSlop && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                    mSwipeIntercepted = true;
                    if (mContainer.getParent() != null) {
                        mContainer.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endSwipeTracking();
                break;
            default:
                break;
        }
        return mSwipeIntercepted;
    }

    /** Consume the page-swipe stream after {@link #onInterceptPageSwipe} claimed it. */
    public boolean onPageSwipeTouch(MotionEvent ev) {
        if (!mSwipeIntercepted && ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_UP: {
                float dx = ev.getX() - mSwipeDownX;
                mVelocityTracker.computeCurrentVelocity(1000);
                float vx = mVelocityTracker.getXVelocity();
                boolean flingLeft = vx < -mMinFlingVelocity && Math.abs(vx) > Math.abs(
                        mVelocityTracker.getYVelocity());
                boolean flingRight = vx > mMinFlingVelocity && Math.abs(vx) > Math.abs(
                        mVelocityTracker.getYVelocity());
                int width = Math.max(1, mContainer.getWidth());
                boolean dragLeft = dx < -width * 0.22f;
                boolean dragRight = dx > width * 0.22f;
                if ((flingLeft || dragLeft) && !mShowingCategories) {
                    switchPageFromSwipe(/* toCategories= */ true);
                } else if ((flingRight || dragRight) && mShowingCategories) {
                    switchPageFromSwipe(/* toCategories= */ false);
                }
                endSwipeTracking();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                endSwipeTracking();
                return true;
            default:
                return mSwipeIntercepted;
        }
    }

    private boolean isEventOverChrome(MotionEvent ev) {
        if (mTabHeader != null
                && mLauncher.getDragLayer().isEventOverView(mTabHeader, ev)) {
            return true;
        }
        View search = mContainer.getSearchView();
        return search != null && mLauncher.getDragLayer().isEventOverView(search, ev);
    }

    private void endSwipeTracking() {
        mSwipeTracking = false;
        mSwipeIntercepted = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void switchPageFromSwipe(boolean toCategories) {
        if (mPageAnimating || toCategories == mShowingCategories) {
            return;
        }
        if (mSegment != null) {
            // Drive the existing page animation + keep the segment pill in sync.
            mSegment.selectSegmentAt(toCategories ? 1 : 0);
        } else if (toCategories) {
            showCategoriesPage();
        } else {
            showAllPage();
        }
    }

    public void attach() {
        LayoutInflater inflater = LayoutInflater.from(mContainer.getContext());

        RecyclerViewFastScroller scroller = mContainer.findViewById(R.id.fast_scroller);
        if (scroller != null) {
            scroller.setVisibility(View.GONE);
        }
        View popup = mContainer.findViewById(R.id.fast_scroller_popup);
        if (popup != null) {
            popup.setVisibility(View.GONE);
        }
        View header = mContainer.findViewById(R.id.all_apps_header);
        if (header != null) {
            header.setVisibility(View.GONE);
        }

        // Oppo all_apps_bg_layer: full-bleed tint behind chrome + lists so DST_OUT
        // fades reveal a uniform surface (not a brighter wallpaper strip).
        ensureFullBleedBgLayer();

        mTabHeader = inflater.inflate(R.layout.coloros_all_apps_category_tab_header, mContainer, false);
        RelativeLayout.LayoutParams tabLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tabLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        mContainer.addView(mTabHeader, tabLp);
        // Apply status-bar clearance immediately so a later letter-index failure
        // cannot leave tabs stuck under the system icons.
        applyTabTopInset();

        mSegment = mTabHeader.findViewById(R.id.coloros_segment_group);
        ImageView menu = mTabHeader.findViewById(R.id.coloros_all_apps_menu);
        String all = mContainer.getResources().getString(R.string.coloros_floating_tab_all);
        String categories = mContainer.getResources().getString(R.string.coloros_floating_tab_category);
        mSegment.setSegmentButtons(new String[]{all, categories});
        mSegment.setSegmentSelectedTextColor(0xFF000000);
        mSegment.setSegmentUnselectedTextColor(
                mContainer.getResources().getColor(R.color.coloros_all_apps_text, null));
        // Oppo: translucent dark track + solid white selected pill (COUI light-theme
        // track is nearly invisible on the drawer scrim).
        final int trackColor = mContainer.getResources()
                .getColor(R.color.coloros_all_apps_segment_track, null);
        final int selectedColor = mContainer.getResources()
                .getColor(R.color.coloros_all_apps_segment_selected, null);
        mSegment.setSegmentButtonDrawDelegate(
                new COUISegmentButtonLayout.SegmentButtonDrawDelegate() {
                    @Override
                    public Paint[] getCustomBackgroundPaint() {
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setColor(trackColor);
                        return new Paint[]{p};
                    }

                    @Override
                    public Paint[] getCustomIndicatorPaint() {
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setColor(selectedColor);
                        return new Paint[]{p};
                    }
                });
        mSegment.selectSegmentAt(0);
        mSegment.setOnSelectedSegmentChangeListener((from, to, progress) -> {
            if (mSuppressSegmentCallback) {
                return;
            }
            if (to == 1) {
                if (!mShowingCategories) {
                    showCategoriesPage();
                }
            } else if (mShowingCategories) {
                showAllPage();
            }
        });
        menu.setOnClickListener(this::showOverflowMenu);
        menu.setImageResource(R.drawable.ic_overflow_menu);
        menu.clearColorFilter();

        mLetterCluster = new ColorOsLetterClusterOverlay(mContainer);
        mLetterCluster.bindChrome(mTabHeader, mContainer.getSearchView());
        mLetterCluster.setDismissListener(this::onLetterClusterDismissed);

        mLetterIndex = new ColorOsLetterRail(mContainer.getContext());
        mLetterIndex.setId(R.id.coloros_letter_index);
        RelativeLayout.LayoutParams letterLp = new RelativeLayout.LayoutParams(
                mContainer.getResources().getDimensionPixelSize(
                        R.dimen.coloros_all_apps_index_width),
                dp(220));
        letterLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        letterLp.addRule(RelativeLayout.BELOW, R.id.coloros_category_tab_header);
        letterLp.setMarginEnd(dp(2));
        mContainer.addView(mLetterIndex, letterLp);
        mLetterIndex.setElevation(dp(8));
        mLetterIndex.setListener(new ColorOsLetterRail.Listener() {
            @Override
            public void onLetterScrubStart() {
                AllAppsRecyclerView rv = resolveAppsRecyclerView();
                if (rv != null) {
                    rv.stopScroll();
                }
            }

            @Override
            public void onLetter(String letter, int centerYInRail) {
                if (mLetterCluster != null && mLetterCluster.isShowing()
                        && letter.equals(mLetterCluster.getSection())) {
                    updateLetterClusterY(centerYInRail);
                    return;
                }
                showLetterCluster(letter, centerYInRail);
                // Keep list scrolled under the cluster so dismiss lands on the section.
                jumpToLetterImmediate(letter);
            }
        });
        attachLetterRailScrollSync();
        updateLetterRailVisibility();
        refreshLetterRailSections();
        // hasValue must track model updates — early refresh with an empty list
        // previously stamped every letter as empty and killed scrub entirely.
        mContainer.getAppsStore().addUpdateListener(this::refreshLetterRailSections);
        mLetterIndex.post(this::refreshLetterRailSections);

        mCategoryList = new ColorOsCategoryRecyclerView(mContainer.getContext());
        mCategoryList.setId(R.id.coloros_category_list);
        GridLayoutManager glm = new GridLayoutManager(mContainer.getContext(), 2);
        mCategoryAdapter = new ColorOsCategoryAdapter(mCategoryController);
        mCategoryAdapter.attachSpanSizeLookup(glm);
        mCategoryList.setLayoutManager(glm);
        mCategoryList.setAdapter(mCategoryAdapter);
        mCategoryList.setVisibility(View.GONE);
        // Oppo pager: clipChildren + inset top (not full-bleed).
        mCategoryList.setClipToPadding(true);
        mCategoryList.setClipChildren(true);
        mCategoryList.setBackground(null);
        int hPad = dp(16);
        mCategoryList.setPadding(hPad, 0, hPad, dp(16));
        RelativeLayout.LayoutParams catLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        catLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        catLp.addRule(RelativeLayout.ABOVE, R.id.search_container_all_apps);
        catLp.topMargin = mContainer.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_content_margin_top);
        catLp.bottomMargin = 0;
        mContainer.addView(mCategoryList, catLp);
        // Prefetch so the first All→Categories switch is not a cold bind/layout.
        bindCategories();
        mContainer.getAppsStore().addUpdateListener(() -> {
            // Avoid main-thread PackageManager storms during model churn; refresh
            // only when Categories is visible or still empty.
            if (mShowingCategories || mCategoryAdapter == null
                    || mCategoryAdapter.getItemCount() == 0) {
                bindCategories();
            }
        });

        applyDrawerColumns();
        setInsets(mLauncher.getDeviceProfile().getInsets());
        // Oppo category pager clips children; only relax clip during page-slide frames.
        mContainer.setClipChildren(true);
        mContainer.setClipToPadding(false);
        initPageSwipeGesture();
        showAllPage();
        mTabHeader.bringToFront();
        mLetterIndex.bringToFront();
        menu.bringToFront();
    }

    public void setInsets(Rect insets) {
        if (insets != null) {
            mInsets.set(insets);
        }
        reapplyContentLayout();
    }

    /**
     * Re-pin apps list under the tab header and refresh search / letter chrome.
     * Safe to call after {@code setupHeader()} / {@code rebindAdapters()}.
     */
    public void reapplyContentLayout() {
        if (mTabHeader == null) {
            return;
        }
        applyTabTopInset();
        applySearchBottomInset();
        mContainer.layoutColorOsAppsBelowTabs();
        // After tab measures, re-pin apps + categories with the real tab bottom.
        mTabHeader.post(() -> {
            mContainer.layoutColorOsAppsBelowTabs();
            layoutCategoryListUnderTabs();
            applyListFadePadding();
        });

        // Oppo category/all apps: paddingTop/Bottom = 0 so content nests into the
        // fade bands (soft bottom is visible at rest). Top inset is layout margin.
        mContainer.applyColorOsListPadding(0, 0);
        layoutCategoryListUnderTabs();
        applyListFadePadding();

        // Reopen / search-exit can force the apps RV visible again — reassert
        // All vs Categories exclusivity every layout pass.
        syncPageVisibility();

        if (mLetterIndex != null) {
            if (mLetterCluster != null) {
                mLetterCluster.bindChrome(mTabHeader, mContainer.getSearchView());
            }
            updateLetterRailVisibility();
            refreshLetterRailSections();
            mLetterIndex.bringToFront();
        }
        mTabHeader.bringToFront();
        View menu = mTabHeader.findViewById(R.id.coloros_all_apps_menu);
        if (menu != null) {
            menu.setVisibility(View.VISIBLE);
            menu.bringToFront();
        }
    }

    private void applyTabTopInset() {
        if (mTabHeader == null) {
            return;
        }
        int statusTop = resolveStatusTop();
        int gap = mContainer.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_tab_gap_below_status);
        RelativeLayout.LayoutParams tabLp =
                (RelativeLayout.LayoutParams) mTabHeader.getLayoutParams();
        if (tabLp == null) {
            return;
        }
        tabLp.topMargin = statusTop + gap;
        tabLp.height = dp(52);
        mTabHeader.setLayoutParams(tabLp);
        mTabHeader.setPadding(0, 0, 0, 0);
    }

    private void applySearchBottomInset() {
        View search = mContainer.getSearchView();
        if (search instanceof AppsSearchContainerLayout) {
            AppsSearchContainerLayout searchView = (AppsSearchContainerLayout) search;
            Runnable apply = () -> {
                Rect r = new Rect(mInsets);
                r.bottom = resolveNavBottom();
                searchView.setInsets(r);
                applyListFadePadding();
            };
            apply.run();
            // Insets can be 0 during first attach; refresh once the window is ready.
            searchView.post(apply);
        }
    }

    /**
     * Oppo category pager: list nests slightly under the segment (fade lives there),
     * bottom pinned above search via {@link RelativeLayout#ABOVE}.
     */
    private void layoutCategoryListUnderTabs() {
        if (mCategoryList == null) {
            return;
        }
        ViewGroup.LayoutParams raw = mCategoryList.getLayoutParams();
        if (!(raw instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        int overlap = mContainer.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_under_tab_overlap);
        int belowGap = mContainer.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_below_tab_gap);
        int topMargin = mContainer.getResources().getDimensionPixelSize(
                R.dimen.coloros_all_apps_content_margin_top);
        if (mTabHeader != null) {
            int tabBottom = mTabHeader.getBottom();
            if (tabBottom <= 0) {
                RelativeLayout.LayoutParams tabLp =
                        (RelativeLayout.LayoutParams) mTabHeader.getLayoutParams();
                int topM = tabLp != null ? Math.max(0, tabLp.topMargin) : 0;
                tabBottom = mTabHeader.getMeasuredHeight() + topM;
            }
            if (tabBottom > overlap) {
                topMargin = tabBottom - overlap + belowGap;
            }
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
        boolean changed = lp.width != ViewGroup.LayoutParams.MATCH_PARENT
                || lp.height != ViewGroup.LayoutParams.MATCH_PARENT
                || lp.topMargin != topMargin
                || lp.bottomMargin != 0
                || lp.getRule(RelativeLayout.ALIGN_PARENT_TOP) != RelativeLayout.TRUE
                || lp.getRule(RelativeLayout.ABOVE) != R.id.search_container_all_apps
                || lp.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM) != 0
                || lp.getRule(RelativeLayout.BELOW) != 0;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.removeRule(RelativeLayout.BELOW);
        lp.removeRule(RelativeLayout.ALIGN_TOP);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.ABOVE, R.id.search_container_all_apps);
        lp.topMargin = topMargin;
        lp.bottomMargin = 0;
        if (changed) {
            mCategoryList.setLayoutParams(lp);
        }
        mCategoryList.setTranslationX(0f);
        mCategoryList.setTranslationY(0f);
        mCategoryList.setClipToPadding(true);
        mCategoryList.setClipChildren(true);
    }

    /** Oppo {@code all_apps_bg_layer} — match_parent tint behind All/Categories chrome. */
    private void ensureFullBleedBgLayer() {
        View existing = mContainer.findViewById(R.id.coloros_all_apps_bg_layer);
        if (existing != null) {
            return;
        }
        View bg = new View(mContainer.getContext());
        bg.setId(R.id.coloros_all_apps_bg_layer);
        bg.setBackgroundColor(mContainer.getResources()
                .getColor(R.color.coloros_all_apps_bg_layer, null));
        bg.setClickable(false);
        bg.setFocusable(false);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mContainer.addView(bg, 0, lp);
    }

    /**
     * Oppo DST_OUT edge fades on the category list (solid under search + soft edges).
     */
    private void applyListFadePadding() {
        View search = mContainer.getSearchView();
        int hPad = dp(16);

        layoutCategoryListUnderTabs();

        if (mCategoryList != null) {
            if (mCategoryList.getPaddingTop() != 0
                    || mCategoryList.getPaddingBottom() != 0
                    || mCategoryList.getPaddingLeft() != hPad
                    || mCategoryList.getPaddingRight() != hPad) {
                mCategoryList.setPadding(hPad, 0, hPad, 0);
            }
            mCategoryList.setClipToPadding(true);
            mCategoryList.setClipChildren(true);
            mCategoryList.setBackground(null);
            if (mCategoryList instanceof ColorOsCategoryRecyclerView) {
                ColorOsEdgeFadeHelper fade =
                        ((ColorOsCategoryRecyclerView) mCategoryList).getEdgeFade();
                fade.configureFromResources(mContainer.getResources());
                fade.setEnabled(true);
                fade.ensureHostLayer(mCategoryList);
                fade.applyToHost(mCategoryList);
                fade.applyEdgeAlphas(mCategoryList);
                mCategoryList.invalidate();
            }
        }

        hideFadeOverlays();

        if (search != null) {
            search.setElevation(dp(12));
            search.bringToFront();
        }
        if (mTabHeader != null) {
            mTabHeader.bringToFront();
        }
        View menu = mTabHeader != null
                ? mTabHeader.findViewById(R.id.coloros_all_apps_menu) : null;
        if (menu != null) {
            menu.bringToFront();
        }
        if (mLetterIndex != null && mLetterIndex.getVisibility() == View.VISIBLE) {
            mLetterIndex.bringToFront();
        }
    }

    private void hideFadeOverlays() {
        if (mTopFadeOverlay != null) {
            mTopFadeOverlay.setVisibility(View.GONE);
        }
        if (mBottomFadeOverlay != null) {
            mBottomFadeOverlay.setVisibility(View.GONE);
        }
    }

    private void ensureFadeOverlays(int topFadeH, int softBotH) {
        // Overlays disabled — caused dark banding over content.
    }

    private void layoutFadeOverlays(int topFadeH, int softBotH, int bottomFadeH) {
        hideFadeOverlays();
    }

    private int resolveStatusTop() {
        int top = Math.max(0, mInsets.top);
        WindowInsets wi = mContainer.getRootWindowInsets();
        if (wi != null) {
            top = Math.max(top, wi.getInsets(WindowInsets.Type.statusBars()).top);
            top = Math.max(top, wi.getInsets(WindowInsets.Type.displayCutout()).top);
        }
        if (top <= 0) {
            top = dp(36);
        }
        return top;
    }

    private int resolveNavBottom() {
        int bottom = Math.max(0, mInsets.bottom);
        WindowInsets wi = mContainer.getRootWindowInsets();
        if (wi != null) {
            bottom = Math.max(bottom, wi.getInsets(WindowInsets.Type.navigationBars()).bottom);
            bottom = Math.max(bottom, wi.getInsets(WindowInsets.Type.tappableElement()).bottom);
        }
        return bottom;
    }

    /** Re-read prefs and push column count into All Apps adapters. */
    public void applyDrawerColumns() {
        int cols = ColorOsDrawerColumns.resolve(
                mContainer.getContext(), mLauncher.getDeviceProfile());
        AllAppsRecyclerView active = mContainer.getActiveRecyclerView();
        applyColumnsToRv(active, cols);
        // Also update search / work holders if present.
        View appsList = mContainer.findViewById(R.id.apps_list_view);
        if (appsList instanceof AllAppsRecyclerView) {
            applyColumnsToRv((AllAppsRecyclerView) appsList, cols);
        }
    }

    private void applyColumnsToRv(@Nullable AllAppsRecyclerView rv, int cols) {
        if (rv == null) {
            return;
        }
        if (rv.getAdapter() instanceof BaseAllAppsAdapter) {
            ((BaseAllAppsAdapter<?>) rv.getAdapter()).setAppsPerRow(cols);
        }
        if (rv.getApps() != null) {
            rv.getApps().setNumAppsPerRowAllApps(cols);
        }
        if (rv.getAdapter() != null) {
            rv.swapAdapter(rv.getAdapter(), true);
            rv.getRecycledViewPool().clear();
        }
    }

    /**
     * Oppo {@code dismissPopupWindow}: tear down immediately so Home / pause / sort
     * cannot leave a floating or dimmed {@link COUIPopupListWindow}.
     * Also exits letter-cluster filter mode.
     */
    public void dismissPopupWindow() {
        dismissLetterCluster();
        if (mPopupWindow == null) {
            return;
        }
        if (mPopupWindow.isShowing()) {
            mPopupWindow.forceDismiss();
        }
    }

    /** Exit Oppo-style A–Z filtered / cluster overlay. */
    public void dismissLetterCluster() {
        if (mLetterCluster != null && mLetterCluster.isShowing()) {
            mLetterCluster.dismiss();
        }
    }

    /**
     * Oppo: after leaving cluster, drop scrub (blue) styling. Prefer the filtered
     * section as the follow highlight — with few apps the list often cannot scroll
     * that section to row 0, so syncing from first-visible always stuck on B.
     */
    private void onLetterClusterDismissed() {
        if (mLetterIndex == null) {
            return;
        }
        mLetterIndex.endScrubStyle();
        AllAppsRecyclerView rv = resolveAppsRecyclerView();
        String section = mLastClusterSection;
        if (section != null) {
            jumpToLetterImmediate(section);
            mLetterIndex.setFollowLetter(section);
        } else if (rv != null) {
            rv.post(() -> syncLetterRailFromScroll(rv));
        } else {
            mLetterIndex.clearActiveLetter();
        }
    }

    private void showLetterCluster(String letter, int centerYInRail) {
        if (mLetterCluster == null || mShowingCategories || letter == null) {
            return;
        }
        AllAppsRecyclerView rv = resolveAppsRecyclerView();
        if (rv == null || rv.getApps() == null) {
            return;
        }
        List<AppInfo> apps = ColorOsLetterClusterOverlay.appsForSection(rv.getApps(), letter);
        if (apps.isEmpty()) {
            // Oppo never selects empty sections; keep previous cluster if any.
            return;
        }
        mLastClusterSection = letter;
        mLetterIndex.setScrubLetter(letter);
        // Overlay is full-screen; map rail letter center into container coordinates.
        int[] railLoc = new int[2];
        int[] containerLoc = new int[2];
        mLetterIndex.getLocationInWindow(railLoc);
        mContainer.getLocationInWindow(containerLoc);
        int yInOverlay = (railLoc[1] - containerLoc[1]) + centerYInRail;
        mLetterCluster.showSection(letter, apps, yInOverlay);
        // Letter rail stays above the cluster (Oppo keeps A–Z while filtering).
        mLetterIndex.bringToFront();
    }

    private void updateLetterClusterY(int centerYInRail) {
        if (mLetterCluster == null || mLetterIndex == null) {
            return;
        }
        int[] railLoc = new int[2];
        int[] containerLoc = new int[2];
        mLetterIndex.getLocationInWindow(railLoc);
        mContainer.getLocationInWindow(containerLoc);
        mLetterCluster.updateLetterY((railLoc[1] - containerLoc[1]) + centerYInRail);
    }

    /**
     * Oppo {@code handleManagerClick}: {@link COUIPopupListWindow} with Select / Sort / Settings.
     */
    private void showOverflowMenu(View anchor) {
        ensurePopupWindow();
        // Oppo ignores re-click while showing; we force-dismiss so a dimmed
        // in-flight exit cannot leave a stuck floating window.
        if (mPopupWindow.isShowing()) {
            mPopupWindow.forceDismiss();
            return;
        }
        rebuildPopupItems();
        mPopupWindow.setItemList(mPopupItems);
        mPopupWindow.show(anchor);
    }

    private void ensurePopupWindow() {
        if (mPopupWindow != null) {
            return;
        }
        // Force light UI mode so System Dark Mode cannot invert the Oppo-style
        // white panel / black labels (Select was unreadable on a force-darkened panel).
        Context couiContext = createLightCouiPopupContext();
        mPopupWindow = new COUIPopupListWindow(couiContext);
        mPopupWindow.setUseBackgroundBlur(false);
        mPopupWindow.setDismissTouchOutside(true);
        mPopupWindow.setOnItemClickListener(this::onPopupMainItemClick);
        mPopupWindow.setSubMenuClickListener(this::onPopupSubMenuItemClick);
    }

    private Context createLightCouiPopupContext() {
        Context base = mContainer.getContext();
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_NO;
        Context light = base.createConfigurationContext(cfg);
        return new ContextThemeWrapper(light, com.coui.appcompat.R.style.Theme_COUI_Blue);
    }

    private void rebuildPopupItems() {
        Context context = mContainer.getContext();
        mPopupItems.clear();
        // Oppo Categories overflow is Select + Settings only; Sort lives on All.
        ColorStateList titleColor = ColorStateList.valueOf(0xFF000000);

        PopupListItem.Builder builder = new PopupListItem.Builder();
        builder.setTitle(context.getString(R.string.coloros_drawer_app_sort_select));
        builder.setTitleColorList(titleColor);
        builder.setForceTint(0);
        builder.setIsEnable(true);
        builder.setId(MENU_ID_SELECT);
        mPopupItems.add(builder.build());

        if (!mShowingCategories) {
            builder.reset();
            builder.setTitle(context.getString(R.string.coloros_drawer_app_sort_sort));
            builder.setTitleColorList(titleColor);
            builder.setForceTint(0);
            builder.setIsEnable(true);
            builder.setId(MENU_ID_SORT);
            attachSortSubMenu(builder);
            mPopupItems.add(builder.build());
        }

        builder.reset();
        builder.setTitle(context.getString(R.string.coloros_drawer_category_settings));
        builder.setTitleColorList(titleColor);
        builder.setForceTint(0);
        builder.setIsEnable(true);
        builder.setId(MENU_ID_SETTINGS);
        mPopupItems.add(builder.build());
    }

    private void attachSortSubMenu(PopupListItem.Builder builder) {
        Context context = mContainer.getContext();
        int current = ColorOsDrawerSort.getSortRule(context);
        String[] options = ColorOsDrawerSort.getSortOptionLabels(context);
        ColorStateList titleColor = ColorStateList.valueOf(0xFF000000);
        ArrayList<PopupListItem> sub = new ArrayList<>(options.length);
        for (int i = 0; i < options.length; i++) {
            PopupListItem.Builder subBuilder = new PopupListItem.Builder();
            subBuilder.setIcon(null);
            subBuilder.setTitle(options[i]);
            subBuilder.setTitleColorList(titleColor);
            subBuilder.setForceTint(0);
            subBuilder.setIsChecked(i == current);
            subBuilder.setIsEnable(true);
            subBuilder.setId(i);
            sub.add(subBuilder.build());
        }
        builder.setDescription(options[Math.max(0, Math.min(current, options.length - 1))]);
        builder.setSubMenuItemList(sub);
    }

    private void onPopupMainItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mPopupItems.size()) {
            return;
        }
        int itemId = mPopupItems.get(position).getId();
        if (itemId == MENU_ID_SELECT) {
            mPopupWindow.forceDismiss();
            // Oppo enters multi-select edit; not ported yet — keep menu parity only.
            return;
        }
        if (itemId == MENU_ID_SETTINGS) {
            mPopupWindow.forceDismiss();
            Intent intent = new Intent(mLauncher, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mLauncher.startActivity(intent);
        }
        // MENU_ID_SORT opens COUI submenu; ignore main click.
    }

    private void onPopupSubMenuItemClick(AdapterView<?> parent, View view, int position, long id) {
        applySortRule(position);
        // forceDismiss clears submenu ListView alpha; animated dismiss can leave it.
        mPopupWindow.forceDismiss();
    }

    private void applySortRule(int rule) {
        ColorOsDrawerSort.setSortRule(mContainer.getContext(), rule);
        AllAppsRecyclerView rv = mContainer.getActiveRecyclerView();
        if (rv != null && rv.getApps() != null) {
            rv.getApps().onDrawerSortRuleChanged();
        }
        View appsList = mContainer.findViewById(R.id.apps_list_view);
        if (appsList instanceof AllAppsRecyclerView) {
            AlphabeticalAppsList<?> apps = ((AllAppsRecyclerView) appsList).getApps();
            if (apps != null && (rv == null || apps != rv.getApps())) {
                apps.onDrawerSortRuleChanged();
            }
        }
        updateLetterRailVisibility();
        // Sort rebuild is async; refresh populated sections after adapters update.
        mContainer.post(this::refreshLetterRailSections);
    }

    /** Oppo hides A–Z rail when sort is not by name. */
    private void updateLetterRailVisibility() {
        if (mLetterIndex == null) {
            return;
        }
        boolean show = !mShowingCategories
                && ColorOsDrawerSort.getSortRule(mContainer.getContext())
                == ColorOsDrawerSort.SORT_BY_NAME;
        mLetterIndex.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            dismissLetterCluster();
            mLetterIndex.clearActiveLetter();
        } else {
            mLetterIndex.bringToFront();
            refreshLetterRailSections();
        }
    }

    /**
     * Oppo {@code isSectionHasValue} / {@code IndexIndicationKey.hasValue}: empty
     * A–Z keys are not selectable while scrubbing.
     * If the app list is not ready yet, leave the rail fail-open (all selectable)
     * so scrubbing still works.
     */
    private void refreshLetterRailSections() {
        if (mLetterIndex == null) {
            return;
        }
        boolean[] hasValue = new boolean[ColorOsLetterRail.LETTERS.length];
        boolean foundAny = false;
        AllAppsRecyclerView rv = resolveAppsRecyclerView();
        AlphabeticalAppsList<?> apps = rv != null ? rv.getApps() : null;
        if (apps != null) {
            List<FastScrollSectionInfo> sections = apps.getFastScrollerSections();
            if (sections != null) {
                for (FastScrollSectionInfo info : sections) {
                    int index = ColorOsLetterRail.indexForSection(info.sectionName);
                    if (index >= 0) {
                        hasValue[index] = true;
                        foundAny = true;
                    }
                }
            }
            if (!foundAny) {
                for (int i = 0; i < ColorOsLetterRail.LETTERS.length; i++) {
                    if (!ColorOsLetterClusterOverlay
                            .appsForSection(apps, ColorOsLetterRail.LETTERS[i]).isEmpty()) {
                        hasValue[i] = true;
                        foundAny = true;
                    }
                }
            }
        }
        if (!foundAny) {
            // Model not ready — do not stamp all-false (that disables scrub).
            return;
        }
        mLetterIndex.setSectionHasValue(hasValue);
    }

    /**
     * Oppo {@code updateMoveTouchBarText}: highlight the section for the first visible icon.
     */
    private void attachLetterRailScrollSync() {
        AllAppsRecyclerView rv = mContainer.getActiveRecyclerView();
        if (rv == null) {
            View appsList = mContainer.findViewById(R.id.apps_list_view);
            if (appsList instanceof AllAppsRecyclerView) {
                rv = (AllAppsRecyclerView) appsList;
            }
        }
        if (rv == null) {
            return;
        }
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // Ignore layout-only callbacks (dy==0). With few apps the list cannot
                // scroll the filtered section to row 0; a layout pass would otherwise
                // always reset the follow highlight back to B.
                if (dy != 0) {
                    syncLetterRailFromScroll(recyclerView);
                }
            }
        });
    }

    private void syncLetterRailFromScroll(RecyclerView recyclerView) {
        if (mLetterIndex == null || mLetterIndex.getVisibility() != View.VISIBLE) {
            return;
        }
        // Cluster owns the active letter while filtered.
        if (mLetterCluster != null && mLetterCluster.isShowing()) {
            return;
        }
        if (!(recyclerView instanceof AllAppsRecyclerView)) {
            return;
        }
        AlphabeticalAppsList<?> apps = ((AllAppsRecyclerView) recyclerView).getApps();
        if (apps == null) {
            return;
        }
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (!(lm instanceof androidx.recyclerview.widget.LinearLayoutManager)) {
            return;
        }
        int first = ((androidx.recyclerview.widget.LinearLayoutManager) lm)
                .findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return;
        }
        List<?> items = apps.getAdapterItems();
        if (first < 0 || first >= items.size()) {
            return;
        }
        Object item = items.get(first);
        if (!(item instanceof BaseAllAppsAdapter.AdapterItem)) {
            return;
        }
        AppInfo info = ((BaseAllAppsAdapter.AdapterItem) item).itemInfo;
        if (info == null || info.sectionName == null || info.sectionName.isEmpty()) {
            // Walk forward to first icon with a section.
            for (int i = first; i < Math.min(first + 8, items.size()); i++) {
                Object o = items.get(i);
                if (o instanceof BaseAllAppsAdapter.AdapterItem) {
                    AppInfo ai = ((BaseAllAppsAdapter.AdapterItem) o).itemInfo;
                    if (ai != null && ai.sectionName != null && !ai.sectionName.isEmpty()) {
                        mLetterIndex.setFollowLetter(ai.sectionName);
                        return;
                    }
                }
            }
            return;
        }
        mLetterIndex.setFollowLetter(info.sectionName);
    }

    /**
     * Immediate scroll (not smooth) so the list lands under the cluster even while
     * the recycler is alpha-hidden.
     */
    private void jumpToLetterImmediate(@Nullable String letter) {
        if (letter == null || mShowingCategories) {
            return;
        }
        AllAppsRecyclerView rv = resolveAppsRecyclerView();
        if (rv == null || rv.getApps() == null) {
            return;
        }
        AlphabeticalAppsList<?> apps = rv.getApps();
        List<?> items = apps.getAdapterItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        String target = letter.toUpperCase(Locale.US);
        int targetPos = -1;
        for (int i = 0; i < items.size(); i++) {
            Object o = items.get(i);
            if (!(o instanceof BaseAllAppsAdapter.AdapterItem)) {
                continue;
            }
            AppInfo info = ((BaseAllAppsAdapter.AdapterItem) o).itemInfo;
            if (info == null) {
                continue;
            }
            char head = 0;
            if (info.sectionName != null && !info.sectionName.isEmpty()) {
                head = Character.toUpperCase(info.sectionName.charAt(0));
            } else if (info.title != null && info.title.length() > 0) {
                head = Character.toUpperCase(info.title.charAt(0));
            }
            if (head == 0) {
                continue;
            }
            boolean match;
            if ("#".equals(target)) {
                match = !Character.isLetter(head);
            } else {
                match = head == target.charAt(0);
            }
            if (match) {
                targetPos = i;
                break;
            }
        }
        if (targetPos < 0) {
            return;
        }
        final int pos = targetPos;
        rv.stopScroll();
        Runnable scroll = () -> {
            RecyclerView.LayoutManager lm = rv.getLayoutManager();
            if (lm instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                ((androidx.recyclerview.widget.LinearLayoutManager) lm)
                        .scrollToPositionWithOffset(pos, 0);
            } else {
                rv.scrollToPosition(pos);
            }
        };
        scroll.run();
        // Second pass after the list is shown again (dismiss path).
        rv.post(scroll);
    }

    /**
     * Enforce All vs Categories exclusivity. Call after any path that may force the
     * apps RecyclerView visible again (search exit, drawer reopen, rebind).
     */
    public void syncPageVisibility() {
        View apps = resolveAppsContentView();
        View appsContainer = mContainer.getAppsRecyclerViewContainer();
        View cats = mCategoryList;
        if (apps != null) {
            apps.animate().cancel();
            apps.setTranslationX(0f);
        }
        if (cats != null) {
            cats.animate().cancel();
            cats.setTranslationX(0f);
        }
        if (appsContainer != null && appsContainer != apps) {
            appsContainer.animate().cancel();
            appsContainer.setTranslationX(0f);
        }
        mPageAnimating = false;

        // Stock AOSP floating header stays hidden in ColorOS drawer.
        View stockHeader = mContainer.getFloatingHeaderView();
        if (stockHeader != null) {
            stockHeader.setVisibility(View.GONE);
        }

        if (mShowingCategories) {
            // GONE (not INVISIBLE): All-page icons must not composite under Categories.
            setGoneOrInvisible(apps, View.GONE);
            if (appsContainer != null && appsContainer != apps) {
                setGoneOrInvisible(appsContainer, View.GONE);
            }
            if (mLetterIndex != null) {
                mLetterIndex.setVisibility(View.GONE);
            }
            dismissLetterCluster();
            if (cats != null) {
                cats.setAlpha(1f);
                cats.setVisibility(View.VISIBLE);
            }
            if (mCategoryAdapter != null && mCategoryAdapter.getItemCount() == 0) {
                bindCategories();
            }
            layoutCategoryListUnderTabs();
            applyListFadePadding();
        } else {
            if (cats != null) {
                cats.setAlpha(1f);
                cats.setVisibility(View.GONE);
            }
            if (apps != null) {
                apps.setAlpha(1f);
                apps.setVisibility(View.VISIBLE);
            }
            if (appsContainer != null && appsContainer != apps) {
                appsContainer.setAlpha(1f);
                appsContainer.setVisibility(View.VISIBLE);
            }
            updateLetterRailVisibility();
            applyListFadePadding();
        }
        View search = mContainer.getSearchView();
        if (search != null) {
            search.bringToFront();
        }
        if (mTabHeader != null) {
            mTabHeader.bringToFront();
        }
    }

    private static void setGoneOrInvisible(View v, int visibility) {
        if (v == null) {
            return;
        }
        v.animate().cancel();
        v.setAlpha(1f);
        v.setVisibility(visibility);
    }

    private void showAllPage() {
        mShowingCategories = false;
        dismissLetterCluster();
        if (mLetterIndex != null) {
            mLetterIndex.setVisibility(View.GONE);
        }
        if (mPageAnimating) {
            syncPageVisibility();
            return;
        }
        // First attach / already on All: snap without animation.
        View apps = resolveAppsContentView();
        View cats = mCategoryList;
        boolean alreadyAll = (cats == null || cats.getVisibility() != View.VISIBLE)
                && (apps == null || apps.getVisibility() == View.VISIBLE);
        if (alreadyAll) {
            syncPageVisibility();
            return;
        }
        crossfadePages(/* toCategories= */ false);
    }

    private void showCategoriesPage() {
        mShowingCategories = true;
        dismissLetterCluster();
        if (mLetterIndex != null) {
            mLetterIndex.setVisibility(View.GONE);
        }
        // Prefer warm cache; bind only if empty so the switch stays jank-free.
        if (mCategoryAdapter == null || mCategoryAdapter.getItemCount() == 0) {
            bindCategories();
        }
        if (mCategoryList != null) {
            mCategoryList.stopScroll();
            mCategoryList.scrollToPosition(0);
        }
        layoutCategoryListUnderTabs();
        applyListFadePadding();
        if (mPageAnimating) {
            syncPageVisibility();
            return;
        }
        View cats = mCategoryList;
        View apps = resolveAppsContentView();
        boolean alreadyCats = cats != null && cats.getVisibility() == View.VISIBLE
                && (apps == null || apps.getVisibility() != View.VISIBLE);
        if (alreadyCats) {
            syncPageVisibility();
            return;
        }
        crossfadePages(/* toCategories= */ true);
    }

    /**
     * Oppo-like page switch: opaque horizontal slide (Categories sits to the right
     * of All). Avoids the blank/flash from alpha fades over translucent cards.
     */
    private void crossfadePages(boolean toCategories) {
        View apps = resolveAppsPageView();
        View cats = mCategoryList;
        if (apps == null || cats == null) {
            return;
        }
        apps.animate().cancel();
        cats.animate().cancel();
        mPageAnimating = true;
        // Allow the outgoing page to slide off-screen without being clipped mid-frame.
        mContainer.setClipChildren(false);

        int width = mContainer.getWidth();
        if (width <= 0) {
            width = mContainer.getResources().getDisplayMetrics().widthPixels;
        }
        final float outX = toCategories ? -width : width;
        final float inFromX = toCategories ? width : -width;

        View show = toCategories ? cats : apps;
        View hide = toCategories ? apps : cats;

        show.setAlpha(1f);
        show.setVisibility(View.VISIBLE);
        show.setTranslationX(inFromX);
        show.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        hide.setAlpha(1f);
        hide.setVisibility(View.VISIBLE);
        hide.setTranslationX(0f);
        hide.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Keep nested apps list in sync when we animate the container.
        View nestedApps = resolveAppsContentView();
        if (!toCategories && nestedApps != null && nestedApps != apps) {
            nestedApps.setAlpha(1f);
            nestedApps.setVisibility(View.VISIBLE);
            nestedApps.setTranslationX(0f);
        }

        show.animate()
                .translationX(0f)
                .setDuration(PAGE_SWITCH_MS)
                .setInterpolator(PAGE_SWITCH_INTERPOLATOR)
                .start();
        hide.animate()
                .translationX(outX)
                .setDuration(PAGE_SWITCH_MS)
                .setInterpolator(PAGE_SWITCH_INTERPOLATOR)
                .withEndAction(() -> {
                    hide.setVisibility(View.GONE);
                    hide.setTranslationX(0f);
                    hide.setLayerType(View.LAYER_TYPE_NONE, null);
                    show.setTranslationX(0f);
                    show.setLayerType(View.LAYER_TYPE_NONE, null);
                    mPageAnimating = false;
                    mContainer.setClipChildren(true);
                    syncPageVisibility();
                })
                .start();
    }

    @Nullable
    private View resolveAppsPageView() {
        View container = mContainer.getAppsRecyclerViewContainer();
        if (container != null) {
            return container;
        }
        return resolveAppsContentView();
    }

    @Nullable
    private View resolveAppsContentView() {
        View mainList = mContainer.findViewById(R.id.apps_list_view);
        if (mainList != null) {
            return mainList;
        }
        return mContainer.getActiveRecyclerView();
    }

    private void bindCategories() {
        if (mCategoryAdapter == null) {
            return;
        }
        List<AppInfo> apps = new ArrayList<>();
        for (AppInfo appInfo : mContainer.getAppsStore().getApps()) {
            if (appInfo != null) {
                apps.add(appInfo);
            }
        }
        List<CategoryInfo> categories = mCategoryController.getCategories(mLauncher, apps);
        mCategoryAdapter.bind(mContainer.getContext(), apps, categories);
    }

    private int dp(int value) {
        return Math.round(value * mContainer.getResources().getDisplayMetrics().density);
    }

    private void jumpToLetter(@Nullable String letter) {
        if (letter == null || mShowingCategories) {
            return;
        }
        AllAppsRecyclerView rv = resolveAppsRecyclerView();
        if (rv == null || rv.getApps() == null) {
            return;
        }
        AlphabeticalAppsList<?> apps = rv.getApps();
        List<FastScrollSectionInfo> sections = apps.getFastScrollerSections();
        if (sections == null || sections.isEmpty()) {
            return;
        }
        String target = letter.toUpperCase(Locale.US);
        FastScrollSectionInfo exact = null;
        FastScrollSectionInfo next = null;
        for (FastScrollSectionInfo info : sections) {
            if (info.sectionName == null || info.sectionName.isEmpty()) {
                continue;
            }
            String section = info.sectionName.toUpperCase(Locale.US);
            char head = section.charAt(0);
            if ("#".equals(target)) {
                if (!Character.isLetter(head)) {
                    exact = info;
                    break;
                }
                continue;
            }
            if (section.startsWith(target) || String.valueOf(head).equals(target)) {
                exact = info;
                break;
            }
            // Oppo-style: if this letter has no apps, land on the next section after it.
            if (next == null && Character.isLetter(head) && head > target.charAt(0)) {
                next = info;
            }
        }
        FastScrollSectionInfo go = exact != null ? exact : next;
        if (go == null) {
            return;
        }
        rv.scrollToFastScrollSection(go);
    }

    @Nullable
    private AllAppsRecyclerView resolveAppsRecyclerView() {
        AllAppsRecyclerView rv = mContainer.getActiveRecyclerView();
        if (rv != null) {
            return rv;
        }
        View appsList = mContainer.findViewById(R.id.apps_list_view);
        return appsList instanceof AllAppsRecyclerView
                ? (AllAppsRecyclerView) appsList
                : null;
    }
}
