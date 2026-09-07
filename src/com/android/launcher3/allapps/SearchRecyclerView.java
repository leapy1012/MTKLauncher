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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.RecyclerViewFastScroller;

/** A RecyclerView for AllApps Search results. */
public class SearchRecyclerView extends AllAppsRecyclerView {

    private Consumer<View> mChildAttachedConsumer;

    /** ColorOS empty-space tap tracking (Oppo OplusAllAppsRecyclerView). */
    private final boolean mColorOsDrawer;
    private final int mTouchSlop;
    private boolean mColorOsTrackingClick;
    private float mDownX;
    private float mDownY;

    public SearchRecyclerView(Context context) {
        this(context, null);
    }

    public SearchRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SearchRecyclerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mColorOsDrawer = context.getResources().getBoolean(R.bool.config_coloros_drawer);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /** This will be called just before a new child is attached to the window. */
    public void setChildAttachedConsumer(Consumer<View> childAttachedConsumer) {
        mChildAttachedConsumer = childAttachedConsumer;
    }

    @Override
    protected void updatePoolSize() {
        RecycledViewPool pool = getRecycledViewPool();
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_ICON, mNumAppsPerRow);
        // TODO(b/206905515): Add maxes for other View types.
    }

    @Override
    public boolean supportsFastScrolling() {
        return false;
    }

    @Override
    public RecyclerViewFastScroller getScrollbar() {
        return null;
    }

    @Override
    public void onChildAttachedToWindow(@NonNull View child) {
        if (mChildAttachedConsumer != null) {
            mChildAttachedConsumer.accept(child);
        }
        super.onChildAttachedToWindow(child);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean handled = super.onTouchEvent(e);
        if (mColorOsDrawer) {
            tryHandleColorOsEmptySpaceGesture(e);
        }
        return handled;
    }

    /**
     * Oppo: empty-space tap on search RV — hide IME first, then exit search.
     * Icon taps are ignored here so launch still works normally.
     */
    private void tryHandleColorOsEmptySpaceGesture(MotionEvent e) {
        final int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mColorOsTrackingClick = true;
            mDownX = e.getX();
            mDownY = e.getY();
            return;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (mColorOsTrackingClick
                    && (Math.abs(e.getX() - mDownX) > mTouchSlop
                    || Math.abs(e.getY() - mDownY) > mTouchSlop)) {
                mColorOsTrackingClick = false;
                ActivityContext ctx = ActivityContext.lookupContext(getContext());
                if (ctx.getAppsView() != null) {
                    ctx.getAppsView().getSearchUiManager().onSearchRecyclerViewScroll();
                }
            }
            return;
        }
        if (action != MotionEvent.ACTION_UP) {
            if (action == MotionEvent.ACTION_CANCEL) {
                mColorOsTrackingClick = false;
            }
            return;
        }
        if (!mColorOsTrackingClick) {
            return;
        }
        mColorOsTrackingClick = false;
        View under = findChildViewUnder(e.getX(), e.getY());
        if (under instanceof BubbleTextView) {
            // Icon hit — launch path owns this touch.
            return;
        }
        ActivityContext ctx = ActivityContext.lookupContext(getContext());
        if (ctx.getAppsView() != null) {
            ctx.getAppsView().getSearchUiManager().onSearchRecyclerViewClick();
        }
    }
}
