/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowInsets;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.coloros.ColorOsDrawerChrome;
import com.android.launcher3.statemanager.StateManager.StateListener;

/**
 * AllAppsContainerView with launcher specific callbacks
 */
public class LauncherAllAppsContainerView extends ActivityAllAppsContainerView<Launcher> {

    private static final String TAG = "LauncherAllApps";

    private ColorOsDrawerChrome mColorOsChrome;
    private boolean mColorOsChromeAttached;
    private final StateListener<LauncherState> mColorOsStateListener =
            new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    if (toState == LauncherState.ALL_APPS) {
                        ensureColorOsChromeAttached();
                    } else {
                        // Oppo dismisses COUIPopupListWindow when leaving All Apps
                        // (Home / Overview); otherwise the popup window stays floating.
                        dismissColorOsPopup();
                    }
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    if (finalState == LauncherState.ALL_APPS) {
                        ensureColorOsChromeAttached();
                        if (mColorOsChrome != null) {
                            mColorOsChrome.syncPageVisibility();
                        }
                    } else {
                        dismissColorOsPopup();
                        if (mColorOsChrome != null) {
                            // Finish any in-flight All↔Categories crossfade so reopen
                            // does not start with both pages half-visible.
                            mColorOsChrome.syncPageVisibility();
                        }
                    }
                }
            };

    public LauncherAllAppsContainerView(Context context) {
        this(context, null);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ColorOsDrawerChrome.isEnabled(getContext())) {
            mActivityContext.getStateManager().addStateListener(mColorOsStateListener);
            if (isInAllApps()) {
                ensureColorOsChromeAttached();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (ColorOsDrawerChrome.isEnabled(getContext())) {
            mActivityContext.getStateManager().removeStateListener(mColorOsStateListener);
        }
        super.onDetachedFromWindow();
    }

    private void ensureColorOsChromeAttached() {
        if (mColorOsChromeAttached || !ColorOsDrawerChrome.isEnabled(getContext())) {
            return;
        }
        mColorOsChromeAttached = true;
        try {
            mColorOsChrome = new ColorOsDrawerChrome(this);
            mColorOsChrome.attach();
            mColorOsChrome.setInsets(mActivityContext.getDeviceProfile().getInsets());
        } catch (Throwable t) {
            Log.e(TAG, "ColorOS drawer chrome failed", t);
            mColorOsChrome = null;
            mColorOsChromeAttached = false;
        }
    }

    private void dismissColorOsPopup() {
        if (mColorOsChrome != null) {
            mColorOsChrome.dismissPopupWindow();
        }
    }

    /** Called from {@link Launcher#onPause} — screen off / leave task. */
    public void onLauncherPaused() {
        dismissColorOsPopup();
    }

    @Override
    public void reset(boolean animate, boolean exitSearch) {
        dismissColorOsPopup();
        super.reset(animate, exitSearch);
        // reset() → animateToSearchState(false) forces the apps RV visible; if the
        // user left the drawer on Categories, reassert exclusivity after that.
        if (mColorOsChrome != null) {
            mColorOsChrome.syncPageVisibility();
        }
    }

    @Override
    protected void updateSearchResultsVisibility() {
        super.updateSearchResultsVisibility();
        if (mColorOsChrome != null) {
            mColorOsChrome.syncPageVisibility();
        } else if (getResources().getBoolean(R.bool.config_coloros_drawer)
                && mHeader != null) {
            mHeader.setVisibility(GONE);
        }
    }

    @Override
    protected void onColorOsDrawerHeaderReady() {
        ensureColorOsChromeAttached();
        if (mColorOsChrome != null) {
            mColorOsChrome.reapplyContentLayout();
        }
    }

    @Override
    public void setInsets(Rect insets) {
        super.setInsets(insets);
        if (mColorOsChrome != null) {
            mColorOsChrome.setInsets(insets);
        }
    }

    @Override
    protected int computeNavBarScrimHeight(WindowInsets insets) {
        // ColorOS drawer draws search above the nav; skip the solid scrim band.
        if (getResources().getBoolean(R.bool.config_coloros_drawer)) {
            return 0;
        }
        if (Utilities.ATLEAST_Q) {
            return insets.getTappableElementInsets().bottom;
        } else {
            return insets.getStableInsetBottom();
        }
    }

    @Override
    public boolean shouldContainerScroll(MotionEvent ev) {
        // Categories owns its own RecyclerView; AOSP only consults the All apps RV,
        // which stays at offset 0 while Categories scrolls — that made swipe-down
        // dismiss the drawer (unlike Oppo).
        if (mColorOsChrome != null && mColorOsChrome.isShowingCategories()) {
            return mColorOsChrome.shouldContainerScroll(ev);
        }
        return super.shouldContainerScroll(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mColorOsChrome != null && isInAllApps()
                && mColorOsChrome.onInterceptPageSwipe(ev)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mColorOsChrome != null && isInAllApps()
                && mColorOsChrome.onPageSwipeTouch(ev)) {
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean isInAllApps() {
        return mActivityContext.getStateManager().isInStableState(LauncherState.ALL_APPS);
    }
}
