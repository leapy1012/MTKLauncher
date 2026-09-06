package com.android.launcher3.allapps.coloros;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Categories list: Oppo soft edge fades. Bottom edge is layout-pinned above search.
 */
public class ColorOsCategoryRecyclerView extends RecyclerView {

    private final ColorOsEdgeFadeHelper mFade = new ColorOsEdgeFadeHelper();

    public ColorOsCategoryRecyclerView(Context context) {
        this(context, null);
    }

    public ColorOsCategoryRecyclerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorOsCategoryRecyclerView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClipToPadding(true);
        if (ColorOsEdgeFadeHelper.shouldUse(context)) {
            mFade.setEnabled(true);
            mFade.configureFromResources(context.getResources());
            mFade.ensureHostLayer(this);
            addOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    mFade.applyEdgeAlphas(ColorOsCategoryRecyclerView.this);
                }
            });
        }
    }

    public ColorOsEdgeFadeHelper getEdgeFade() {
        return mFade;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mFade.isEnabled()) {
            mFade.applyEdgeAlphas(this);
        }
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mFade.isEnabled()) {
            mFade.drawSoftScrims(this, canvas);
        }
    }
}
