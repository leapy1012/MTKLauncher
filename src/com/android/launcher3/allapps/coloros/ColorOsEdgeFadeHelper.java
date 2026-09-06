package com.android.launcher3.allapps.coloros;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.android.launcher3.R;

/**
 * Soft Oppo-like edge fades for DreamLauncher's <b>inset</b> drawer lists.
 *
 * <p>Oppo {@code OplusAllAppsRecyclerView} uses tall DST_OUT bands because the list
 * paints <i>under</i> tabs and search. Our lists are layout-clipped above search /
 * under tabs, so we only need the soft dissolve segments (~32dp top, ~38–56dp
 * bottom). Using Oppo's full 128dp solid band here blacks out half the cards.
 */
public final class ColorOsEdgeFadeHelper {

    private final Paint mFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode mDstOut =
            new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);

    private int mTopFadePx;
    private int mBottomFadePx;
    private int mSoftTopPx;
    private int mSoftBottomPx;
    private boolean mEnabled;

    public ColorOsEdgeFadeHelper() {
        mFadePaint.setXfermode(mDstOut);
    }

    public void configureFromResources(@NonNull Resources res) {
        int topSoft = res.getDimensionPixelSize(R.dimen.coloros_all_apps_top_fade_height);
        int overlap = res.getDimensionPixelSize(R.dimen.coloros_all_apps_under_tab_overlap);
        int softBot = res.getDimensionPixelSize(R.dimen.coloros_all_apps_bottom_fade_soft_height);
        // Soft dissolve covers the under-segment nest so fade starts at the tab, not in empty air.
        setFadeHeights(topSoft + overlap, softBot + dp(res, 8), softBot, topSoft);
    }

    private static int dp(Resources res, int v) {
        return Math.round(v * res.getDisplayMetrics().density);
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public int getTopFadePx() {
        return mTopFadePx;
    }

    public int getBottomFadePx() {
        return mBottomFadePx;
    }

    public int getSoftTopPx() {
        return mSoftTopPx;
    }

    public int getSoftBottomPx() {
        return mSoftBottomPx;
    }

    public void setFadeHeights(int topPx, int bottomPx, int softBottomPx) {
        setFadeHeights(topPx, bottomPx, softBottomPx,
                Math.min(topPx, softBottomPx > 0 ? softBottomPx : topPx));
    }

    public void setFadeHeights(int topPx, int bottomPx, int softBottomPx, int softTopPx) {
        mTopFadePx = Math.max(0, topPx);
        mBottomFadePx = Math.max(0, bottomPx);
        mSoftBottomPx = Math.max(0, Math.min(softBottomPx, mBottomFadePx));
        mSoftTopPx = Math.max(0, Math.min(softTopPx, mTopFadePx));
    }

    public void ensureHostLayer(@NonNull View host) {
        if (host.getLayerType() != View.LAYER_TYPE_HARDWARE) {
            host.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            host.setRenderEffect(null);
        }
    }

    public void applyToHost(@NonNull View host) {
        ensureHostLayer(host);
    }

    public void drawSoftScrims(@NonNull View host, @NonNull Canvas canvas) {
        if (!mEnabled || host.getWidth() <= 0 || host.getHeight() <= 0) {
            return;
        }
        final int w = host.getWidth();
        final int h = host.getHeight();
        final int topH = Math.min(mTopFadePx, h);
        final int botH = Math.min(mBottomFadePx, h);

        // Oppo soft stops (EB/C2 family) without a long solid black plate.
        if (topH > 0) {
            mFadePaint.setShader(new LinearGradient(0, 0, 0, topH,
                    new int[]{0xD9000000, 0x8C000000, 0x33000000, 0x00000000},
                    new float[]{0f, 0.28f, 0.65f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, topH, mFadePaint);
        }
        if (botH > 0) {
            float y0 = h - botH;
            mFadePaint.setShader(new LinearGradient(0, y0, 0, h,
                    new int[]{0x00000000, 0x33000000, 0x8C000000, 0xD9000000},
                    new float[]{0f, 0.35f, 0.7f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, y0, w, h, mFadePaint);
        }
        mFadePaint.setShader(null);
    }

    public void applyEdgeAlphas(@NonNull ViewGroup host) {
        if (!mEnabled) {
            return;
        }
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child.getAlpha() != 1f) {
                child.setAlpha(1f);
            }
        }
    }

    public void applyBottomEdgeAlphas(@NonNull ViewGroup host) {
        applyEdgeAlphas(host);
    }

    public static boolean shouldUse(@NonNull Context context) {
        try {
            return context.getResources().getBoolean(R.bool.config_coloros_drawer);
        } catch (Resources.NotFoundException e) {
            return false;
        }
    }
}
