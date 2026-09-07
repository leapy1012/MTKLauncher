package com.android.launcher3.allapps.coloros;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Oppo-style open category folder.
 *
 * <p>Drawer chrome fades out; wallpaper dim stays via All Apps scrim. Title is
 * left-aligned to the first icon drawable ({@code CategoryFolder#setNamePadding}).
 * Empty-space taps dismiss like Oppo {@code interceptOutsideTouch}.
 */
public final class ColorOsCategoryFolderView extends AbstractFloatingView {

    private static final long OPEN_MS = 220;
    private static final long CLOSE_MS = 180;
    private static final int COLS = 4;

    private final Launcher mLauncher;
    private final TextView mTitle;
    private final GridLayout mGrid;
    private final LinearLayout mBody;
    private final ScrollView mScroll;
    private final List<View> mHiddenChrome = new ArrayList<>();
    private final Rect mTmpRect = new Rect();
    private final int mTouchSlop;

    private float mDownX;
    private float mDownY;
    private boolean mInterceptForDismiss;

    public ColorOsCategoryFolderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorOsCategoryFolderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
        setClickable(true);
        setFocusable(true);
        setBackgroundColor(0x99000000);

        int sidePad = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_side_padding);
        int titleH = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_title_height);
        int contentGap = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_content_margin_top);

        mTitle = new TextView(context);
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.coloros_category_folder_title_size));
        mTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        mTitle.setPadding(sidePad, 0, sidePad, 0);
        mTitle.setSingleLine(true);
        mTitle.setIncludeFontPadding(false);

        mGrid = new GridLayout(context);
        mGrid.setColumnCount(COLS);
        mGrid.setUseDefaultMargins(false);
        mGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        mGrid.setPadding(sidePad, 0, sidePad, 0);
        // Do not swallow empty-cell taps — parent dismisses empty space.
        mGrid.setClickable(false);

        mScroll = new ScrollView(context);
        mScroll.setFillViewport(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setClickable(false);
        FrameLayout scrollInner = new FrameLayout(context);
        scrollInner.setClickable(false);
        scrollInner.addView(mGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mScroll.addView(scrollInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mBody = new LinearLayout(context);
        mBody.setOrientation(VERTICAL);
        mBody.setClickable(false);
        mBody.addView(mTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, titleH));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = contentGap;
        mBody.addView(mScroll, scrollLp);

        addView(mBody, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Open (or replace) the category folder overlay. */
    public static void show(@NonNull Launcher launcher, @NonNull CharSequence title,
            @NonNull List<AppInfo> apps, @Nullable View anchor) {
        if (apps.isEmpty()) {
            return;
        }
        AbstractFloatingView.closeOpenViews(launcher, true, TYPE_FOLDER_FULL_SHEET);
        ColorOsCategoryFolderView view = new ColorOsCategoryFolderView(launcher, null);
        DragLayer dragLayer = launcher.getDragLayer();
        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.customPosition = true;
        lp.x = 0;
        lp.y = 0;
        lp.width = dragLayer.getWidth() > 0 ? dragLayer.getWidth()
                : launcher.getDeviceProfile().widthPx;
        lp.height = dragLayer.getHeight() > 0 ? dragLayer.getHeight()
                : launcher.getDeviceProfile().heightPx;
        dragLayer.addView(view, lp);
        view.bringToFront();
        view.bind(title, apps);
        view.setDrawerChromeVisible(false);
        view.mIsOpen = true;
        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .setDuration(OPEN_MS)
                .setInterpolator(Interpolators.DEACCEL)
                .start();
    }

    private void bind(CharSequence title, List<AppInfo> apps) {
        mTitle.setText(title);
        mGrid.removeAllViews();

        int sidePad = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_side_padding);
        int cellH = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_cell_height);
        int spaceTop = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_space_top);
        int titleH = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_title_height);
        int contentGap = getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_content_margin_top);
        int centerRow = getResources().getInteger(R.integer.coloros_category_folder_center_row);

        DragLayer dragLayer = mLauncher.getDragLayer();
        int width = Math.max(dragLayer.getWidth(), mLauncher.getDeviceProfile().widthPx);
        int height = Math.max(dragLayer.getHeight(), mLauncher.getDeviceProfile().heightPx);
        Rect insets = dragLayer.getInsets();
        int usableH = height - insets.bottom;

        int cellW = Math.max(1, (width - sidePad * 2) / COLS);
        int n = apps.size();
        int rows = Math.max(1, (n + COLS - 1) / COLS);
        int contentH = rows * cellH;
        int folderH = spaceTop + titleH + contentGap + contentH;

        int leftover = Math.max(0, usableH - folderH);
        int topMargin = rows >= centerRow ? spaceTop : leftover / 2 + spaceTop;

        LayoutParams bodyLp = (LayoutParams) mBody.getLayoutParams();
        if (bodyLp == null) {
            bodyLp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        bodyLp.topMargin = topMargin;
        bodyLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        bodyLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mBody.setLayoutParams(bodyLp);

        // Provisional title inset = side + half leftover in cell (refined after layout).
        int iconSize = mLauncher.getDeviceProfile().allAppsIconSizePx;
        int provisional = sidePad + Math.max(0, (cellW - iconSize) / 2);
        mTitle.setPadding(provisional, 0, sidePad, 0);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        List<AppInfo> copy = new ArrayList<>(apps);
        for (int i = 0; i < copy.size(); i++) {
            AppInfo info = copy.get(i);
            BubbleTextView icon = (BubbleTextView) inflater.inflate(
                    R.layout.all_apps_icon, mGrid, false);
            icon.setOnClickListener(ItemClickHandler.INSTANCE);
            icon.applyFromApplicationInfo(info);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLS),
                    GridLayout.spec(i % COLS));
            glp.width = cellW;
            glp.height = cellH;
            mGrid.addView(icon, glp);
        }
        ViewGroup.LayoutParams gridLp = mGrid.getLayoutParams();
        if (gridLp != null) {
            gridLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            gridLp.height = contentH;
            mGrid.setLayoutParams(gridLp);
        }

        // Oppo CategoryFolder.setNamePadding: namePad + first icon's getIconBounds().left
        // (+ grid side padding, since iconBounds are relative to the BubbleTextView).
        final int sidePadFinal = sidePad;
        mGrid.post(() -> alignTitleToFirstIcon(sidePadFinal));
    }

    /** Match Oppo: title starts at the left edge of the first icon glyph. */
    private void alignTitleToFirstIcon(int sidePad) {
        if (mGrid.getChildCount() == 0) {
            return;
        }
        View child = mGrid.getChildAt(0);
        if (!(child instanceof BubbleTextView)) {
            return;
        }
        BubbleTextView icon = (BubbleTextView) child;
        if (icon.getWidth() <= 0) {
            icon.post(() -> alignTitleToFirstIcon(sidePad));
            return;
        }
        icon.getIconBounds(mTmpRect);
        // Align title ink to icon glyph: grid side pad + iconBounds.left (Oppo setNamePadding).
        int padStart = sidePad + mTmpRect.left;
        mTitle.setPadding(padStart, 0, sidePad, 0);
    }

    private boolean isOverIcon(MotionEvent ev) {
        DragLayer dl = mLauncher.getDragLayer();
        for (int i = 0; i < mGrid.getChildCount(); i++) {
            View child = mGrid.getChildAt(i);
            if (child instanceof BubbleTextView && dl.isEventOverView(child, ev)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverTitle(MotionEvent ev) {
        return mLauncher.getDragLayer().isEventOverView(mTitle, ev);
    }

    /** Fade tabs / search / lists out so wallpaper+scrim remain (Oppo open). */
    private void setDrawerChromeVisible(boolean visible) {
        ActivityAllAppsContainerView<?> apps = mLauncher.getAppsView();
        if (apps == null) {
            return;
        }
        if (!visible) {
            mHiddenChrome.clear();
            hideChrome(apps.findViewById(R.id.coloros_category_tab_header));
            hideChrome(apps.findViewById(R.id.coloros_category_list));
            hideChrome(apps.findViewById(R.id.coloros_letter_index));
            hideChrome(apps.getSearchView());
            hideChrome(apps.getAppsRecyclerViewContainer());
            hideChrome(apps.findViewById(R.id.apps_list_view));
        } else {
            for (View v : mHiddenChrome) {
                if (v == null) {
                    continue;
                }
                v.animate().cancel();
                v.setVisibility(VISIBLE);
                v.setEnabled(true);
                v.animate()
                        .alpha(1f)
                        .setDuration(CLOSE_MS)
                        .setInterpolator(Interpolators.DEACCEL)
                        .start();
            }
            mHiddenChrome.clear();
        }
    }

    private void hideChrome(@Nullable View v) {
        if (v == null || v.getVisibility() != VISIBLE) {
            return;
        }
        mHiddenChrome.add(v);
        v.animate().cancel();
        v.animate()
                .alpha(0f)
                .setDuration(OPEN_MS)
                .setInterpolator(Interpolators.ACCEL)
                .withEndAction(() -> {
                    if (!mIsOpen) {
                        return;
                    }
                    v.setVisibility(INVISIBLE);
                    v.setEnabled(false);
                })
                .start();
    }

    @Override
    protected void handleClose(boolean animate) {
        if (!mIsOpen) {
            return;
        }
        mIsOpen = false;
        for (View v : mHiddenChrome) {
            if (v != null) {
                v.animate().cancel();
            }
        }
        setDrawerChromeVisible(true);
        if (!animate) {
            removeFromParent();
            return;
        }
        animate()
                .alpha(0f)
                .setDuration(CLOSE_MS)
                .setInterpolator(Interpolators.ACCEL)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeFromParent();
                    }
                })
                .start();
    }

    private void removeFromParent() {
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_FOLDER_FULL_SHEET) != 0;
    }

    @Override
    public boolean canInterceptEventsInSystemGestureRegion() {
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mDownX = ev.getX();
            mDownY = ev.getY();
            // Claim empty-space taps so DragLayer routes them here (Oppo outside dismiss).
            mInterceptForDismiss = !isOverIcon(ev);
            return mInterceptForDismiss;
        }
        return mInterceptForDismiss;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (!mInterceptForDismiss) {
            return false;
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_UP:
                float dx = ev.getX() - mDownX;
                float dy = ev.getY() - mDownY;
                if (dx * dx + dy * dy < mTouchSlop * mTouchSlop && !isOverIcon(ev)) {
                    close(true);
                }
                mInterceptForDismiss = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mInterceptForDismiss = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Fallback dismiss for empty taps delivered via normal view dispatch.
        if (ev.getAction() == MotionEvent.ACTION_UP
                && !isOverIcon(ev)
                && !isOverTitle(ev)) {
            float dx = ev.getX() - mDownX;
            float dy = ev.getY() - mDownY;
            if (dx * dx + dy * dy < mTouchSlop * mTouchSlop) {
                close(true);
                return true;
            }
        } else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mDownX = ev.getX();
            mDownY = ev.getY();
        }
        return super.onTouchEvent(ev);
    }
}
