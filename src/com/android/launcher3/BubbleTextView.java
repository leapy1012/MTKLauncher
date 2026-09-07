/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.config.FeatureFlags.ENABLE_DOWNLOAD_APP_UX_V2;
import static com.android.launcher3.config.FeatureFlags.ENABLE_ICON_LABEL_AUTO_SCALING;
import static com.android.launcher3.graphics.PreloadIconDrawable.newPendingIcon;
import static com.android.launcher3.icons.BitmapInfo.FLAG_NO_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.icu.text.MessageFormat;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Property;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.accessibility.BaseAccessibilityDelegate;
import com.android.launcher3.allapps.coloros.ColorOsIconChangeAnimManager;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.iconresize.IconResizeFramePainter;
import com.android.launcher3.iconresize.IconResizeHelper;
import com.android.launcher3.iconresize.IconResizePreviewParams;
import com.android.launcher3.iconresize.MorphIconTransitionHelper;
import com.android.launcher3.iconresize.MorphPlateColorHelper;
import com.android.launcher3.iconresize.MorphShapeHelper;
import com.android.launcher3.iconresize.MorphWorkspaceIconDrawable;
import com.android.launcher3.iconresize.ResizeFrameStrokeState;
import com.android.launcher3.editselection.EditSelectionEligibility;
import com.android.launcher3.editselection.EditSelectionManager;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.graphics.PreloadIconDrawable;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.icons.PlaceHolderIconDrawable;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.IconLabelDotView;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.android.launcher3.dot.NumberDotRenderer;
import com.android.launcher3.dot.DotDrawUtils;
import com.android.launcher3.R;
import com.android.launcher3.LauncherPrefs;
import android.content.res.Configuration;
import java.util.concurrent.TimeUnit;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.pm.PackageManager;
import android.graphics.Point;
import com.android.launcher3.folder.large.HxyLargeFolderProxy;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView implements ItemInfoUpdateReceiver,
        IconLabelDotView, DraggableView, Reorderable {

    private static final int DISPLAY_WORKSPACE = 0;
    private static final int DISPLAY_ALL_APPS = 1;
    private static final int DISPLAY_FOLDER = 2;
    protected static final int DISPLAY_TASKBAR = 5;
    private static final int DISPLAY_SEARCH_RESULT = 6;
    private static final int DISPLAY_SEARCH_RESULT_SMALL = 7;

    private static final float MIN_LETTER_SPACING = -0.05f;
    private static final int MAX_SEARCH_LOOP_COUNT = 20;
    private static final Character NEW_LINE = '\n';
    private static final String EMPTY = "";
    private static final StringMatcherUtility.StringMatcher MATCHER =
            StringMatcherUtility.StringMatcher.getInstance();

    public static final int[] STATE_PRESSED = new int[]{android.R.attr.state_pressed};

    private float mScaleForReorderBounce = 1f;

    private IntArray mBreakPointsIntArray;
    private CharSequence mLastOriginalText;
    private CharSequence mLastModifiedText;
    /** Oppo searchHighlightContent — query substrings to bold in search result labels. */
    @Nullable private List<String> mSearchHighlightContent;

    //hxy_leifengqi update to Dynamic clock 20230302 start
    private DynamicClockIcon dynamicClockIcon = null;
    public ItemInfoWithIcon mItemInfoWithIcon = null;
    //hxy_leifengqi update to Dynamic clock 20230302 end

    private static final Property<BubbleTextView, Float> DOT_SCALE_PROPERTY
            = new Property<BubbleTextView, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mDotParams.scale;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float value) {
            bubbleTextView.mDotParams.scale = value;
            bubbleTextView.invalidate();
        }
    };

    public static final Property<BubbleTextView, Float> TEXT_ALPHA_PROPERTY
            = new Property<BubbleTextView, Float>(Float.class, "textAlpha") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mTextAlpha;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float alpha) {
            bubbleTextView.setTextAlpha(alpha);
        }
    };

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private final ActivityContext mActivity;
    private FastBitmapDrawable mIcon;
    /** Oppo drawer sort: crossfade old→new icon via {@link ColorOsIconChangeAnimManager}. */
    private boolean mIsNeedIconChangeAnim;
    private boolean mIsNeedTextChangeAnim;
    private ColorOsIconChangeAnimManager mIconChangeAnimManager;
    @Nullable
    private ResizeFrameStrokeState mResizeStrokeState;
    @Nullable
    private IconResizePreviewParams mResizePreviewParams;
    private boolean mHideLabelForResizePreview;
    private boolean mCenterVertically;

    protected int mDisplay;

    private final CheckLongPressHelper mLongPressHelper;

    private final boolean mLayoutHorizontal;
    private final boolean mIsRtl;
    public final int mIconSize;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHideBadge = false;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIsIconVisible = true;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mTextColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private ColorStateList mTextColorStateList;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTextAlpha = 1;

    @ViewDebug.ExportedProperty(category = "launcher")
    private DotInfo mDotInfo;
    public NumberDotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    protected NumberDotRenderer.DrawParams mDotParams;
    private Animator mDotScaleAnim;
    private boolean mForceHideDot;

    @ViewDebug.ExportedProperty(category = "launcher")
    public boolean mStayPressed;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIgnorePressedStateChange;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisableRelayout = false;

    private HandlerRunnable mIconLoadRequest;

    private boolean mEnableIconUpdateAnimation = false;
    public static boolean mShowInstallBadge = false;
    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);
        mIsRtl = (getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL);
        DeviceProfile grid = mActivity.getDeviceProfile();

        mDisplay = a.getInteger(R.styleable.BubbleTextView_iconDisplay, DISPLAY_WORKSPACE);
        final int defaultIconSize;
        if (mDisplay == DISPLAY_WORKSPACE) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.iconTextSizePx);
            setCompoundDrawablePadding(grid.iconDrawablePaddingPx);
            defaultIconSize = grid.iconSizePx;
            setCenterVertically(grid.isScalableGrid);
        } else if (mDisplay == DISPLAY_ALL_APPS) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.allAppsIconTextSizePx);
            setCompoundDrawablePadding(grid.allAppsIconDrawablePaddingPx);
            defaultIconSize = grid.allAppsIconSizePx;
            if (getResources().getBoolean(R.bool.config_coloros_drawer)) {
                // ColorOS drawer is always on a dark scrim; keep labels readable.
                setTextColor(getContext().getColor(R.color.coloros_all_apps_text));
            }
        } else if (mDisplay == DISPLAY_FOLDER) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.folderChildTextSizePx);
            setCompoundDrawablePadding(grid.folderChildDrawablePaddingPx);
            defaultIconSize = grid.folderChildIconSizePx;
        } else if (mDisplay == DISPLAY_SEARCH_RESULT) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.allAppsIconTextSizePx);
            defaultIconSize = getResources().getDimensionPixelSize(R.dimen.search_row_icon_size);
        } else if (mDisplay == DISPLAY_SEARCH_RESULT_SMALL) {
            defaultIconSize = getResources().getDimensionPixelSize(
                    R.dimen.search_row_small_icon_size);
        } else if (mDisplay == DISPLAY_TASKBAR) {
            defaultIconSize = grid.iconSizePx;
        } else {
            // widget_selection or shortcut_popup
            defaultIconSize = grid.iconSizePx;
        }

        mCenterVertically = a.getBoolean(R.styleable.BubbleTextView_centerVertically, false);

        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                defaultIconSize);
        a.recycle();

        mLongPressHelper = new CheckLongPressHelper(this);

        mDotParams = new NumberDotRenderer.DrawParams(getResources().getDimension(R.dimen.unread_text_number_size));
        // liu-db add label double lines start
        setMaxLines(LauncherPrefs.getPrefs(context).getBoolean(LauncherPrefs.WORKSPACE_APP_NAME, true) ? 1 : 2);
        // liu-db add label double lines end
        if (mDisplay == DISPLAY_WORKSPACE && grid.useOppoWorkspaceMetrics()) {
            setMaxLines(2);
            setIncludeFontPadding(false);
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        setEllipsize(TruncateAt.END);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
        setTextAlpha(1f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // Disable marques when not focused to that, so that updating text does not cause relayout.
        setEllipsize(focused ? TruncateAt.MARQUEE : TruncateAt.END);
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    public void setHideBadge(boolean hideBadge) {
        mHideBadge = hideBadge;
    }

    /**
     * Resets the view so it can be recycled.
     */
    public void reset() {
        if (mIconChangeAnimManager != null) {
            mIconChangeAnimManager.onDestroy();
        }
        mIsNeedIconChangeAnim = false;
        mIsNeedTextChangeAnim = false;
        mDotInfo = null;
        mDotParams.dotColor = Color.TRANSPARENT;
        mDotParams.appColor = Color.TRANSPARENT;
        cancelDotScaleAnim();
        mDotParams.scale = 0f;
        mForceHideDot = false;
        setBackground(null);
        if (FeatureFlags.ENABLE_TWOLINE_ALLAPPS.get()
                || FeatureFlags.ENABLE_TWOLINE_DEVICESEARCH.get()) {
            setMaxLines(1);
        }

        setTag(null);
        mSearchHighlightContent = null;
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
    }

    /**
     * Oppo {@code setSearchHighlightContent}: bold matching query pieces in
     * {@link #applyLabel} for All Apps search results.
     */
    public void setSearchHighlightContent(@Nullable List<String> highlights) {
        mSearchHighlightContent = highlights;
    }

    public void setIsNeedIconChangeAnim(boolean need) {
        mIsNeedIconChangeAnim = need;
    }

    public boolean getIsNeedIconChangeAnim() {
        return mIsNeedIconChangeAnim;
    }

    public void setIsNeedTextChangeAnim(boolean need) {
        mIsNeedTextChangeAnim = need;
    }

    public boolean getIsNeedTextChangeAnim() {
        return mIsNeedTextChangeAnim;
    }

    public boolean isAllAppsDisplay() {
        return mDisplay == DISPLAY_ALL_APPS;
    }

    private ColorOsIconChangeAnimManager getIconChangeAnimManager() {
        if (mIconChangeAnimManager == null) {
            mIconChangeAnimManager = new ColorOsIconChangeAnimManager(this);
        }
        return mIconChangeAnimManager;
    }

    /**
     * Updates the compound drawable without changing {@link #mIcon}. Used while a
     * LayerDrawable crossfade is running (Oppo {@code setIcon(LayerDrawable)} path).
     */
    public void applyIconVisual(Drawable icon) {
        if (icon == null) {
            return;
        }
        icon.setBounds(0, 0, mIconSize, mIconSize);
        updateIcon(icon);
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    private void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        applyFromWorkspaceItem(info, /* animate = */ false, /* staggerIndex = */ 0);
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info, boolean animate, int staggerIndex) {
        applyFromWorkspaceItem(info, null);
    }

    /**
     * Returns whether the newInfo differs from the current getTag().
     */
    public boolean shouldAnimateIconChange(WorkspaceItemInfo newInfo) {
        WorkspaceItemInfo oldInfo = getTag() instanceof WorkspaceItemInfo
                ? (WorkspaceItemInfo) getTag()
                : null;
        boolean changedIcons = oldInfo != null && oldInfo.getTargetComponent() != null
                && newInfo.getTargetComponent() != null
                && !oldInfo.getTargetComponent().equals(newInfo.getTargetComponent());
        return changedIcons && isShown();
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        if (delegate instanceof BaseAccessibilityDelegate) {
            super.setAccessibilityDelegate(delegate);
        } else {
            // NO-OP
            // Workaround for b/129745295 where RecyclerView is setting our Accessibility
            // delegate incorrectly. There are no cases when we shouldn't be using the
            // LauncherAccessibilityDelegate for BubbleTextView.
        }
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info, PreloadIconDrawable icon) {
        applyIconAndLabel(info);
        setItemInfo(info);
        applyLoadingState(icon);
        applyDotState(info, false /* animate */);
        applayBadgeState(info);
        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    @UiThread
    public void applyFromApplicationInfo(AppInfo info) {
        applyIconAndLabel(info);

        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);


        // Verify high res immediately
        verifyHighRes();

        if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK) != 0) {
            applyProgressLevel();
        }
        applyDotState(info, false /* animate */);
        applayBadgeState(info);
        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    /**
     * Apply label and tag using a generic {@link ItemInfoWithIcon}
     */
    @UiThread
    public void applyFromItemInfoWithIcon(ItemInfoWithIcon info) {
        applyIconAndLabel(info);
        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    protected void setItemInfo(ItemInfoWithIcon itemInfo) {
        setTag(itemInfo);
    }

    @UiThread
    protected void applyIconAndLabel(ItemInfoWithIcon info) {
        int flags = shouldUseTheme() ? FLAG_THEMED : 0;
        if (mHideBadge) {
            flags |= FLAG_NO_BADGE;
        }
        FastBitmapDrawable iconDrawable = info.newIcon(getContext(), flags);
        mDotParams.appColor = iconDrawable.getIconColor();
        mDotParams.dotColor = Themes.getAttrColor(getContext(), R.attr.notificationDotColor);
        //hxy_leifengqi update to Dynamic clock 20230302 start
        if(iconDrawable instanceof DynamicClockIcon){
            dynamicClockIcon = (DynamicClockIcon)iconDrawable;
            mItemInfoWithIcon = info;
        }
        //hxy_leifengqi update to Dynamic clock 20230302 end
        setIcon(iconDrawable);
        applyLabel(info);
    }

    //hxy_leifengqi update to Dynamic clock 20230302 start
    @Override
    public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {
        // TODO Auto-generated method stub
        if (dynamicClockIcon != null) {
            dynamicClockIcon.run();
        }
        super.setCompoundDrawables(left, top, right, bottom);
    }
    //hxy_leifengqi update to Dynamic clock 20230302 end

    protected boolean shouldUseTheme() {
        return mDisplay == DISPLAY_WORKSPACE || mDisplay == DISPLAY_FOLDER
                || mDisplay == DISPLAY_TASKBAR;
    }

    /**
     *  Only if actual text can be displayed in two line, the {@code true} value will be effective.
     */
    protected boolean shouldUseTwoLine() {
        //modify:byxiangchangsong for workspace two line start
        boolean enableTwoLine  = getResources().getBoolean(R.bool.config_iconlabel_double_lines);
        //modify:byxiangchangsong for workspace two line end
        return (FeatureFlags.ENABLE_TWOLINE_ALLAPPS.get() && (mDisplay == DISPLAY_ALL_APPS 
                || mDisplay == DISPLAY_WORKSPACE)) && enableTwoLine//modify:byxiangchangsong for workspace two line
                || (FeatureFlags.ENABLE_TWOLINE_DEVICESEARCH.get()
                && mDisplay == DISPLAY_SEARCH_RESULT);
    }

    @UiThread
    @VisibleForTesting
    public void applyLabel(ItemInfoWithIcon info) {
        CharSequence label = info.title;
        if (label != null) {
            mLastOriginalText = label;
            mLastModifiedText = mLastOriginalText;
            mBreakPointsIntArray = StringMatcherUtility.getListOfBreakpoints(label, MATCHER);
            CharSequence display = applySearchHighlight(label);
            if (mIsNeedTextChangeAnim && !TextUtils.isEmpty(getText())) {
                getIconChangeAnimManager().changeTextWithFade(display);
            } else {
                setText(display);
            }
        }
        if (info.contentDescription != null) {
            setContentDescription(info.isDisabled()
                    ? getContext().getString(R.string.disabled_app_label, info.contentDescription)
                    : info.contentDescription);
        }
    }

    /**
     * Oppo {@code changeTextHighlightContent}: bold + frost background on the first
     * case-insensitive occurrence of each highlight token.
     */
    private CharSequence applySearchHighlight(CharSequence label) {
        if (mSearchHighlightContent == null || mSearchHighlightContent.isEmpty()
                || TextUtils.isEmpty(label)) {
            return label;
        }
        String text = label.toString();
        String lower = text.toLowerCase(Locale.getDefault());
        SpannableString spannable = new SpannableString(text);
        boolean any = false;
        int bg = getResources().getColor(R.color.coloros_drawer_search_highlight_bg, null);
        for (String token : mSearchHighlightContent) {
            if (TextUtils.isEmpty(token)) {
                continue;
            }
            int start = lower.indexOf(token.toLowerCase(Locale.getDefault()));
            if (start >= 0) {
                int end = start + token.length();
                spannable.setSpan(new BackgroundColorSpan(bg), start, end,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                any = true;
            }
        }
        return any ? spannable : label;
    }

    /** This is used for testing to forcefully set the display to ALL_APPS */
    @VisibleForTesting
    public void setDisplayAllApps() {
        mDisplay = DISPLAY_ALL_APPS;
    }

    /**
     * Overrides the default long press timeout.
     */
    public void setLongPressTimeoutFactor(float longPressTimeoutFactor) {
        mLongPressHelper.setLongPressTimeoutFactor(longPressTimeoutFactor);
    }

    @Override
    public void refreshDrawableState() {
        if (!mIgnorePressedStateChange) {
            super.refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mStayPressed) {
            mergeDrawableStates(drawableState, STATE_PRESSED);
        }
        return drawableState;
    }

    /** Returns the icon for this view. */
    public FastBitmapDrawable getIcon() {
        return mIcon;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // ignore events if they happen in padding area
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }
        if (isLongClickable()) {
            super.onTouchEvent(event);
            mLongPressHelper.onTouchEvent(event);
            // Keep receiving the rest of the events
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        if (mDisplay == DISPLAY_TASKBAR) {
            // Allow touching within padding on taskbar, given icon sizes are smaller.
            return false;
        }
        return y < getPaddingTop()
                || x < getPaddingLeft()
                || y > getHeight() - getPaddingBottom()
                || x > getWidth() - getPaddingRight();
    }

    void setStayPressed(boolean stayPressed) {
        mStayPressed = stayPressed;
        refreshDrawableState();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (mIcon != null) {
            mIcon.setVisible(isVisible, false);
        }
    }

    public void clearPressedBackground() {
        setPressed(false);
        setStayPressed(false);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Unlike touch events, keypress event propagate pressed state change immediately,
        // without waiting for onClickHandler to execute. Disable pressed state changes here
        // to avoid flickering.
        mIgnorePressedStateChange = true;
        boolean result = super.onKeyUp(keyCode, event);
        mIgnorePressedStateChange = false;
        refreshDrawableState();
        return result;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // checkForEllipsis();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        // checkForEllipsis();
    }

    private void checkForEllipsis() {
        if (!ENABLE_ICON_LABEL_AUTO_SCALING.get()) {
            return;
        }
        float width = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (width <= 0) {
            return;
        }
        setLetterSpacing(0);

        String text = getText().toString();
        TextPaint paint = getPaint();
        if (paint.measureText(text) < width) {
            return;
        }

        float spacing = findBestSpacingValue(paint, text, width, MIN_LETTER_SPACING);
        // Reset the paint value so that the call to TextView does appropriate diff.
        paint.setLetterSpacing(0);
        setLetterSpacing(spacing);
    }

    /**
     * Find the appropriate text spacing to display the provided text
     * @param paint the paint used by the text view
     * @param text the text to display
     * @param allowedWidthPx available space to render the text
     * @param minSpacingEm minimum spacing allowed between characters
     * @return the final textSpacing value
     *
     * @see #setLetterSpacing(float)
     */
    private float findBestSpacingValue(TextPaint paint, String text, float allowedWidthPx,
            float minSpacingEm) {
        paint.setLetterSpacing(minSpacingEm);
        if (paint.measureText(text) > allowedWidthPx) {
            // If there is no result at high limit, we can do anything more
            return minSpacingEm;
        }

        float lowLimit = 0;
        float highLimit = minSpacingEm;

        for (int i = 0; i < MAX_SEARCH_LOOP_COUNT; i++) {
            float value = (lowLimit + highLimit) / 2;
            paint.setLetterSpacing(value);
            if (paint.measureText(text) < allowedWidthPx) {
                highLimit = value;
            } else {
                lowLimit = value;
            }
        }

        // At the end error on the higher side
        return highLimit;
    }

    @SuppressWarnings("wrongcall")
    protected void drawWithoutDot(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mResizePreviewParams != null && mResizePreviewParams.isActive()) {
            drawResizePreview(canvas);
            return;
        }
        super.onDraw(canvas);
        if (mResizeStrokeState != null && mResizeStrokeState.isActive()) {
            IconResizeFramePainter.drawFrame(canvas, this, mResizeStrokeState);
        }
        drawEditSelectionCheckIfNecessary(canvas);
        drawDotIfNecessary(canvas);
    }

    protected void drawEditSelectionCheckIfNecessary(Canvas canvas) {
        if (com.android.launcher3.allapps.coloros.ColorOsDrawerSelectController
                .drawCheckIfNeeded(this, canvas)) {
            return;
        }
        if (!(mActivity instanceof Launcher launcher)) {
            return;
        }
        EditSelectionManager selection = launcher.getEditSelectionManager();
        if (!selection.isActive()) {
            return;
        }
        if (!EditSelectionEligibility.canShowCheckmark(getContext(), this)) {
            return;
        }
        Object tag = getTag();
        if (!(tag instanceof ItemInfo)) {
            return;
        }
        Rect iconBounds = new Rect();
        getIconBounds(iconBounds);
        if (iconBounds.isEmpty()) {
            return;
        }
        int size = getResources().getDimensionPixelSize(R.dimen.edit_selection_check_size);
        int topOffset = getResources().getDimensionPixelSize(R.dimen.edit_selection_check_top_offset);
        int rightOffset = getResources().getDimensionPixelSize(
                R.dimen.edit_selection_check_right_offset);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int left;
        if (rtl) {
            left = iconBounds.left - rightOffset;
        } else {
            left = iconBounds.right - size + rightOffset;
        }
        int top = iconBounds.top - topOffset;
        Drawable check = getContext().getDrawable(selection.isSelected(this)
                ? R.drawable.launcher_ic_app_selected
                : R.drawable.launcher_ic_app_unselected);
        if (check == null) {
            return;
        }
        check = check.mutate();
        check.setBounds(left, top, left + size, top + size);
        check.draw(canvas);
    }

    private void drawResizePreview(Canvas canvas) {
        IconResizePreviewParams params = mResizePreviewParams;
        if (params == null) {
            return;
        }
        Rect iconRect = new Rect(
                Math.round(params.getX()),
                Math.round(params.getY()),
                Math.round(params.getX() + params.getSizeX()),
                Math.round(params.getY() + params.getSizeY()));
        if (iconRect.width() <= 0 || iconRect.height() <= 0) {
            return;
        }

        Drawable icon = buildResizePreviewDrawable(params, iconRect);
        if (icon != null) {
            icon.setBounds(iconRect);
            icon.draw(canvas);
        }

        if (mResizeStrokeState != null && mResizeStrokeState.isActive()) {
            IconResizeFramePainter.drawFrame(canvas, this, mResizeStrokeState, iconRect);
        }
        drawDotIfNecessary(canvas);
    }

    @Nullable
    private Drawable buildResizePreviewDrawable(IconResizePreviewParams params, Rect iconRect) {
        if (mIcon == null) {
            return null;
        }
        Object tag = getTag();
        if (!(tag instanceof ItemInfo info)) {
            return mIcon;
        }
        android.content.ComponentName cn = info.getTargetComponent();
        Drawable inner = MorphIconTransitionHelper.unwrapBaseIcon(mIcon);
        inner = MorphPlateColorHelper.loadMorphForeground(getContext(), cn, inner);
        int spanX = params.getSpanX();
        int spanY = params.getSpanY();
        return new MorphWorkspaceIconDrawable(
                getContext(),
                inner,
                mIconSize,
                spanX,
                spanY,
                iconRect.width(),
                iconRect.height(),
                cn,
                params.getRadius(),
                MorphWorkspaceIconDrawable.ScaleMode.MORPH);
    }

    @Nullable
    public ResizeFrameStrokeState getResizeStrokeState() {
        return mResizeStrokeState;
    }

    @Nullable
    public IconResizePreviewParams getResizePreviewParams() {
        return mResizePreviewParams;
    }

    public void enterResizePreviewMode(IconResizePreviewParams initial) {
        mResizePreviewParams = new IconResizePreviewParams(
                initial.getRadius(), initial.getSizeX(), initial.getSizeY(),
                initial.getX(), initial.getY());
        mResizePreviewParams.setActive(true);
        mResizePreviewParams.setSpanX(initial.getSpanX());
        mResizePreviewParams.setSpanY(initial.getSpanY());
        mHideLabelForResizePreview = true;
        setText("");
        applyCompoundDrawables(new ColorDrawable(Color.TRANSPARENT));
        invalidate();
    }

    public void updateResizePreviewLayout(IconResizePreviewParams params) {
        if (mResizePreviewParams == null) {
            return;
        }
        mResizePreviewParams.setRadius(params.getRadius());
        mResizePreviewParams.setSizeX(params.getSizeX());
        mResizePreviewParams.setSizeY(params.getSizeY());
        mResizePreviewParams.setX(params.getX());
        mResizePreviewParams.setSpanX(params.getSpanX());
        mResizePreviewParams.setSpanY(params.getSpanY());
        invalidate();
    }

    public void exitResizePreviewMode() {
        mResizePreviewParams = null;
        mHideLabelForResizePreview = false;
        Object tag = getTag();
        if (tag instanceof WorkspaceItemInfo info) {
            applyFromWorkspaceItem(info);
        } else {
            refreshWorkspaceIconDisplay();
        }
        invalidate();
    }

    /** Icon bounds for a span preset without mutating {@link ItemInfo}. */
    public void getIconBoundsForSpan(int spanX, int spanY, Rect outBounds) {
        spanX = IconResizeHelper.normalizeSpan(spanX);
        spanY = IconResizeHelper.normalizeSpan(spanY);
        if (spanX == IconResizeHelper.MIN_SPAN && spanY == IconResizeHelper.MIN_SPAN) {
            getIconBounds(mIconSize, outBounds);
            return;
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        Rect morph = IconResizeHelper.getMorphIconBounds(dp, spanX, spanY);
        if (spanX == 1 && spanY == 2) {
            Utilities.setRectToViewCenterWhenMorph(this, mIconSize, morph.height(), outBounds);
        } else if (spanX == 2 && spanY == 1) {
            Utilities.setRectToViewCenterWhenMorph(this, morph.width(), mIconSize, outBounds);
        } else if (spanX == 2 && spanY == 2) {
            Utilities.setRectToViewCenterWhenMorph(
                    this, morph.width(), morph.height(), outBounds);
        } else {
            Utilities.setRectToViewCenter(this, mIconSize, outBounds);
        }
        if (!mLayoutHorizontal) {
            outBounds.offsetTo(outBounds.left, getPaddingTop());
        } else if (mIsRtl) {
            outBounds.offsetTo((getWidth() - mIconSize) - getPaddingRight(), outBounds.top);
        } else {
            outBounds.offsetTo(getPaddingLeft(), outBounds.top);
        }
    }

    /** Oppo: frame stroke is drawn on the icon; {@link AppIconResizeFrame} draws the handle. */
    public void setResizeFrameStrokeActive(boolean active, @Nullable Runnable overlayInvalidator) {
        if (active) {
            if (mResizeStrokeState == null) {
                mResizeStrokeState = new ResizeFrameStrokeState(getContext());
            }
            mResizeStrokeState.activate(this, overlayInvalidator);
        } else if (mResizeStrokeState != null) {
            mResizeStrokeState.deactivate(this);
        }
        invalidate();
    }

    /**
     * Draws the notification dot in the top right corner of the icon bounds.
     *
     * @param canvas The canvas to draw to.
     */
    protected void drawDotIfNecessary(Canvas canvas) {
        if (!mForceHideDot && hasDot() && mDotParams.scale > 0) {
            getIconBounds(mDotParams.iconBounds);
            Utilities.scaleRectAboutCenter(mDotParams.iconBounds,
                    IconShape.getNormalizationScale());
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.translate(scrollX, scrollY);
            mDotParams.scale = 1.0f;
            mDotParams.unreadNum = mDotInfo.getNotificationCount();
            if (mDotRenderer.mShowNumber) {
                DotDrawUtils.draw(canvas, new DotDrawUtils.DotNumParams(mDotRenderer, mIconSize, mDotParams), false);
            } else {
                mDotRenderer.draw(canvas, mDotParams);
            }
            canvas.translate(-scrollX, -scrollY);
        }
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    private boolean hasDot() {
        return mDotInfo != null;
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(Rect outBounds) {
        getIconBounds(mIconSize, outBounds);
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(int iconSize, Rect outBounds) {
        Object tag = getTag();
        if (tag instanceof ItemInfo info && IconResizeHelper.isEnabled()
                && IconResizeHelper.canResize(info) && IconResizeHelper.hasExtendedSpan(info)) {
            DeviceProfile dp = ActivityContext.lookupContext(getContext()).getDeviceProfile();
            Rect morph = IconResizeHelper.getMorphIconBounds(dp, info.spanX, info.spanY);
            int spanX = IconResizeHelper.normalizeSpan(info.spanX);
            int spanY = IconResizeHelper.normalizeSpan(info.spanY);
            if (spanX == 1 && spanY == 2) {
                Utilities.setRectToViewCenterWhenMorph(this, iconSize, morph.height(), outBounds);
            } else if (spanX == 2 && spanY == 1) {
                Utilities.setRectToViewCenterWhenMorph(this, morph.width(), iconSize, outBounds);
            } else if (spanX == 2 && spanY == 2) {
                Utilities.setRectToViewCenterWhenMorph(this, morph.width(), morph.height(), outBounds);
            } else {
                Utilities.setRectToViewCenter(this, iconSize, outBounds);
            }
            if (!mLayoutHorizontal) {
                outBounds.offsetTo(outBounds.left, getPaddingTop());
            } else if (mIsRtl) {
                outBounds.offsetTo((getWidth() - iconSize) - getPaddingRight(), outBounds.top);
            } else {
                outBounds.offsetTo(getPaddingLeft(), outBounds.top);
            }
            return;
        }
        outBounds.set(0, 0, iconSize, iconSize);
        if (mLayoutHorizontal) {
            int top = (getHeight() - iconSize) / 2;
            if (mIsRtl) {
                outBounds.offsetTo(getWidth() - iconSize - getPaddingRight(), top);
            } else {
                outBounds.offsetTo(getPaddingLeft(), top);
            }
        } else {
            outBounds.offset((getWidth() - iconSize) / 2, getPaddingTop());
        }
    }

    /**
     * Sets whether to vertically center the content.
     */
    public void setCenterVertically(boolean centerVertically) {
        mCenterVertically = centerVertically;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mCenterVertically) {
            Paint.FontMetrics fm = getPaint().getFontMetrics();
            int cellHeightPx = mIconSize + getCompoundDrawablePadding() +
                    (int) Math.ceil(fm.bottom - fm.top);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setPadding(getPaddingLeft(), (height - cellHeightPx) / 2, getPaddingRight(),
                    getPaddingBottom());
        }
        if (getTag() != null && getTag() instanceof ItemInfo && ((ItemInfo) getTag()).container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            android.util.Log.d("liu-db", "onMeasure: tag=" + getTag() + ", paddingTop=" + getPaddingTop());
            HxyLargeFolderProxy.setFolderPaddingTop(getPaddingTop());
        }
        // Only apply two line for all_apps and device search only if necessary.
        if (shouldUseTwoLine() && (mLastOriginalText != null)) {
            CharSequence modifiedString = modifyTitleToSupportMultiLine(
                    MeasureSpec.getSize(widthMeasureSpec) - getCompoundPaddingLeft()
                            - getCompoundPaddingRight(),
                    mLastOriginalText,
                    getPaint(), mBreakPointsIntArray);
            if (!TextUtils.equals(modifiedString, mLastModifiedText)) {
                mLastModifiedText = modifiedString;
                setText(modifiedString);
                // if text contains NEW_LINE, set max lines to 2
                if (TextUtils.indexOf(modifiedString, NEW_LINE) != -1) {
                    setSingleLine(false);
                    setMaxLines(2);
                } else {
                    setSingleLine(true);
                    setMaxLines(1);
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            IconResizeHelper.applyIconDrawableBounds(this);
        }
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        mTextColorStateList = null;
        super.setTextColor(getModifiedColor());
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        mTextColor = colors.getDefaultColor();
        mTextColorStateList = colors;
        if (Float.compare(mTextAlpha, 1) == 0) {
            super.setTextColor(colors);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    public boolean shouldTextBeVisible() {
        // Text should be visible everywhere but the hotseat.
        Object tag = getParent() instanceof FolderIcon ? ((View) getParent()).getTag() : getTag();
        boolean visibleStatus = LauncherPrefs.getPrefs(getContext()).getBoolean(LauncherPrefs.WORKSPACE_DOCKED_APP, false);
        ItemInfo info = tag instanceof ItemInfo ? (ItemInfo) tag : null;
        if (info != null && (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT || info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION)) {
            return visibleStatus;
        } else {
            return true;
        }
    }

    public void setTextVisibility(boolean visible) {
        boolean visibleStatus = LauncherPrefs.getPrefs(getContext()).getBoolean(LauncherPrefs.WORKSPACE_DOCKED_APP, false);
        if (visibleStatus) {
            visible = true;
        }
        setTextAlpha(visible ? 1 : 0);
    }

    public void setTextAlpha(float alpha) {
        mTextAlpha = alpha;
        if (mTextColorStateList != null) {
            setTextColor(mTextColorStateList);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    private int getModifiedColor() {
        if (mTextAlpha == 0) {
            // Special case to prevent text shadows in high contrast mode
            return Color.TRANSPARENT;
        }
        return setColorAlphaBound(mTextColor, Math.round(Color.alpha(mTextColor) * mTextAlpha));
    }

    /**
     * Creates an animator to fade the text in or out.
     *
     * @param fadeIn Whether the text should fade in or fade out.
     */
    public ObjectAnimator createTextAlphaAnimator(boolean fadeIn) {
        float toAlpha = shouldTextBeVisible() && fadeIn ? 1 : 0;
        return ObjectAnimator.ofFloat(this, TEXT_ALPHA_PROPERTY, toAlpha);
    }

    /**
     * Generate a new string that will support two line text depending on the current string.
     * This method calculates the limited width of a text view and creates a string to fit as
     * many words as it can until the limit is reached. Once the limit is reached, we decide to
     * either return the original title or continue on a new line. How to get the new string is by
     * iterating through the list of break points and determining if the strings between the break
     * points can fit within the line it is in.
     *  Example assuming each character takes up one spot:
     *  title = "Battery Stats", breakpoint = [6], stringPtr = 0, limitedWidth = 7
     *  We get the current word -> from sublist(0, breakpoint[i]+1) so sublist (0,7) -> Battery,
     *  now stringPtr = 7 then from sublist(7) the current string is " Stats" and the runningWidth
     *  at this point exceeds limitedWidth and so we put " Stats" onto the next line (after checking
     *  if the first char is a SPACE, we trim to append "Stats". So resulting string would be
     *  "Battery\nStats"
     */
    public static CharSequence modifyTitleToSupportMultiLine(int limitedWidth, CharSequence title,
            TextPaint paint, IntArray breakPoints) {
        // current title is less than the width allowed so we can just skip
        if (title == null || paint.measureText(title, 0, title.length()) <= limitedWidth) {
            return title;
        }
        float currentWordWidth, runningWidth = 0;
        CharSequence currentWord;
        StringBuilder newString = new StringBuilder();
        int stringPtr = 0;
        for (int i = 0; i < breakPoints.size()+1; i++) {
            if (i < breakPoints.size()) {
                currentWord = title.subSequence(stringPtr, breakPoints.get(i)+1);
            } else {
                // last word from recent breakpoint until the end of the string
                currentWord = title.subSequence(stringPtr, title.length());
            }
            currentWordWidth = paint.measureText(currentWord,0, currentWord.length());
            runningWidth += currentWordWidth;
            if (runningWidth <= limitedWidth) {
                newString.append(currentWord);
            } else {
                // there is no more space
                if (i == 0) {
                    // if the first words exceeds width, just return as the first line will ellipse
                    return title;
                } else {
                    // If putting word onto a new line, make sure there is no space or new line
                    // character in the beginning of the current word and just put in the rest of
                    // the characters.
                    CharSequence lastCharacters = title.subSequence(stringPtr, title.length());
                    int beginningLetterType =
                            Character.getType(Character.codePointAt(lastCharacters,0));
                    if (beginningLetterType == Character.SPACE_SEPARATOR
                            || beginningLetterType == Character.LINE_SEPARATOR) {
                        lastCharacters = lastCharacters.length() > 1
                                ? lastCharacters.subSequence(1, lastCharacters.length())
                                : EMPTY;
                    }
                    newString.append(NEW_LINE).append(lastCharacters);
                    return newString.toString();
                }
            }
            if (i >= breakPoints.size()) {
                // no need to look forward into the string if we've already finished processing
                break;
            }
            stringPtr = breakPoints.get(i)+1;
        }
        return newString.toString();
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    /**
     * Applies the loading progress value to the progress bar.
     *
     * If this app is installing, the progress bar will be updated with the installation progress.
     * If this app is installed and downloading incrementally, the progress bar will be updated
     * with the total download progress.
     */
    public void applyLoadingState(PreloadIconDrawable icon) {
        if (getTag() instanceof ItemInfoWithIcon) {
            WorkspaceItemInfo info = (WorkspaceItemInfo) getTag();
            if ((info.runtimeStatusFlags & FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0
                    || info.hasPromiseIconUi()
                    || (info.runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) != 0
                    || (ENABLE_DOWNLOAD_APP_UX_V2.get() && icon != null)) {
                updateProgressBarUi(info.getProgressLevel() == 100 ? icon : null);
            }
        }
    }

    private void updateProgressBarUi(PreloadIconDrawable oldIcon) {
        FastBitmapDrawable originalIcon = mIcon;
        PreloadIconDrawable preloadDrawable = applyProgressLevel();
        if (preloadDrawable != null && oldIcon != null) {
            preloadDrawable.maybePerformFinishedAnimation(oldIcon, () -> setIcon(originalIcon));
        }
    }

    /** Applies the given progress level to the this icon's progress bar. */
    @Nullable
    public PreloadIconDrawable applyProgressLevel() {
        if (!(getTag() instanceof ItemInfoWithIcon)) {
            return null;
        }

        ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
        int progressLevel = info.getProgressLevel();
        if (progressLevel >= 100) {
            setContentDescription(info.contentDescription != null
                    ? info.contentDescription : "");
        } else if (progressLevel > 0) {
            setDownloadStateContentDescription(info, progressLevel);
        } else {
            setContentDescription(getContext()
                    .getString(R.string.app_waiting_download_title, info.title));
        }
        if (mIcon != null) {
            PreloadIconDrawable preloadIconDrawable;
            if (mIcon instanceof PreloadIconDrawable) {
                preloadIconDrawable = (PreloadIconDrawable) mIcon;
                preloadIconDrawable.setLevel(progressLevel);
                preloadIconDrawable.setIsDisabled(ENABLE_DOWNLOAD_APP_UX_V2.get()
                        ? info.getProgressLevel() == 0
                        : !info.isAppStartable());
            } else {
                preloadIconDrawable = makePreloadIcon();
                setIcon(preloadIconDrawable);
            }
            return preloadIconDrawable;
        }
        return null;
    }

    /**
     * Creates a PreloadIconDrawable with the appropriate progress level without mutating this
     * object.
     */
    @Nullable
    public PreloadIconDrawable makePreloadIcon() {
        if (!(getTag() instanceof ItemInfoWithIcon)) {
            return null;
        }

        ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
        int progressLevel = info.getProgressLevel();
        final PreloadIconDrawable preloadDrawable = newPendingIcon(getContext(), info);

        preloadDrawable.setLevel(progressLevel);
        preloadDrawable.setIsDisabled(ENABLE_DOWNLOAD_APP_UX_V2.get()
                ? info.getProgressLevel() == 0
                : !info.isAppStartable());
        return preloadDrawable;
    }

    public void applyDotState(ItemInfo itemInfo, boolean animate) {
        if (mIcon instanceof FastBitmapDrawable) {
            boolean wasDotted = mDotInfo != null;
            mDotInfo = mActivity.getDotInfoForItem(itemInfo);
            boolean isDotted = mDotInfo != null;
            float newDotScale = isDotted ? 1f : 0;
            if (mDisplay == DISPLAY_ALL_APPS) {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererAllApps;
            } else {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererWorkSpace;
            }
            if (wasDotted || isDotted) {
                // Animate when a dot is first added or when it is removed.
                if (animate && (wasDotted ^ isDotted) && isShown()) {
                    animateDotScale(newDotScale);
                } else {
                    cancelDotScaleAnim();
                    mDotParams.scale = newDotScale;
                    invalidate();
                }
            }
            if (!TextUtils.isEmpty(itemInfo.contentDescription)) {
                if (itemInfo.isDisabled()) {
                    setContentDescription(getContext().getString(R.string.disabled_app_label,
                            itemInfo.contentDescription));
                } else if (hasDot()) {
                    int count = mDotInfo.getNotificationCount();
                    setContentDescription(
                            getAppLabelPluralString(itemInfo.contentDescription.toString(), count));
                } else {
                    setContentDescription(itemInfo.contentDescription);
                }
            }
        }
    }

    private void applayBadgeState(ItemInfo itemInfo) {
        if (mIcon instanceof FastBitmapDrawable) {
            boolean wasDotted = mShowInstallBadge;
            boolean isDotted = mActivity.getBadgeInfoForItem(itemInfo);
            mShowInstallBadge = isDotted;
            if (wasDotted || isDotted) {
                postInvalidate();
            }
        }
    }

    private void setDownloadStateContentDescription(ItemInfoWithIcon info, int progressLevel) {
        if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK)
                != 0) {
            String percentageString = NumberFormat.getPercentInstance()
                    .format(progressLevel * 0.01);
            if ((info.runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                            R.string.app_installing_title, info.title, percentageString));
            } else if ((info.runtimeStatusFlags
                    & FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                            R.string.app_downloading_title, info.title, percentageString));
            }
        }
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    public void setIcon(FastBitmapDrawable icon) {
        if (mIsIconVisible) {
            applyCompoundDrawables(icon);
        }
        mIcon = icon;
        if (mIcon != null) {
            mIcon.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
        }
    }

    /** Re-applies workspace morph plate wrapping after span changes. */
    public void refreshWorkspaceIconDisplay() {
        if (mIcon != null && mIsIconVisible) {
            applyCompoundDrawables(mIcon);
            invalidate();
        }
    }

    /** Shows an in-progress morph transition drawable (resize handle drag). */
    public void applyMorphTransitionDrawable(Drawable morph, int spanX, int spanY) {
        if (morph == null) {
            return;
        }
        Rect bounds = IconResizeHelper.getIconDrawableBounds(this, spanX, spanY);
        morph.setBounds(0, 0, bounds.width(), bounds.height());
        updateIcon(morph);
        invalidate();
    }

    public void setIconDrawable(Drawable icon) {
        setIcon((FastBitmapDrawable) icon);
    }

    @Override
    public void setIconVisible(boolean visible) {
        mIsIconVisible = visible;
        if (!mIsIconVisible) {
            resetIconScale();
        }
        Drawable icon = visible ? mIcon : new ColorDrawable(Color.TRANSPARENT);
        applyCompoundDrawables(icon);
    }

    /** Sets the icon visual state to disabled or not. */
    public void setIconDisabled(boolean isDisabled) {
        if (mIcon != null) {
            mIcon.setIsDisabled(isDisabled);
        }
    }

    protected boolean iconUpdateAnimationEnabled() {
        return mEnableIconUpdateAnimation;
    }

    protected void applyCompoundDrawables(Drawable icon) {
        if (icon == null) {
            // Icon can be null when we use the BubbleTextView for text only.
            return;
        }

        // If we had already set an icon before, disable relayout as the icon size is the
        // same as before.
        mDisableRelayout = mIcon != null;

        Object tag = getTag();
        if (tag instanceof ItemInfo info && IconResizeHelper.isEnabled()
                && IconResizeHelper.canResize(info)) {
            Drawable active = MorphIconTransitionHelper.getActiveDrawable(this);
            if (active != null) {
                icon = active;
            } else {
                icon = IconResizeHelper.wrapMorphDisplayDrawable(this, icon);
            }
            Rect bounds = IconResizeHelper.getIconDrawableBounds(this, info.spanX, info.spanY);
            icon.setBounds(0, 0, bounds.width(), bounds.height());
        } else {
            icon.setBounds(0, 0, mIconSize, mIconSize);
        }

        updateIcon(icon);

        // If the current icon is a placeholder color, animate its update.
        if (mIcon != null
                && mIcon instanceof PlaceHolderIconDrawable
                && iconUpdateAnimationEnabled()) {
            ((PlaceHolderIconDrawable) mIcon).animateIconUpdate(icon);
        }

        if (mIsNeedIconChangeAnim
                && getResources().getBoolean(R.bool.config_coloros_drawer)) {
            getIconChangeAnimManager().startIconChangeAnimIfNeeded(icon);
        }

        mDisableRelayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mDisableRelayout) {
            super.requestLayout();
        }
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     */
    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mDisableRelayout = true;
            mEnableIconUpdateAnimation = true;

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            if (info instanceof AppInfo) {
                applyFromApplicationInfo((AppInfo) info);
            } else if (info instanceof WorkspaceItemInfo) {
                applyFromWorkspaceItem((WorkspaceItemInfo) info);
                mActivity.invalidateParent(info);
            } else if (info != null) {
                applyFromItemInfoWithIcon(info);
            }

            mDisableRelayout = false;
            mEnableIconUpdateAnimation = false;
        }
    }

    /**
     * Verifies that the current icon is high-res otherwise posts a request to load the icon.
     */
    public void verifyHighRes() {
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        if (getTag() instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
            if (info.usingLowResIcon()) {
                mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                        .updateIconInBackground(BubbleTextView.this, info);
            }
        }
    }

    public int getIconSize() {
        return mIconSize;
    }

    public boolean isDisplaySearchResult() {
        return mDisplay == DISPLAY_SEARCH_RESULT ||
                mDisplay == DISPLAY_SEARCH_RESULT_SMALL;
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getIconBounds(bounds);
    }

    public void getSourceVisualDragBounds(Rect bounds) {
        getIconBounds(bounds);
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        resetIconScale();
        setForceHideDot(true);
        return () -> { };
    }

    private void resetIconScale() {
        if (mIcon != null) {
            mIcon.resetScale();
        }
    }

    private void updateIcon(Drawable newIcon) {
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(newIcon, null, null, null);
        } else {
            setCompoundDrawables(null, newIcon, null, null);
        }
    }

    private String getAppLabelPluralString(String appName, int notificationCount) {
        MessageFormat icuCountFormat = new MessageFormat(
                getResources().getString(R.string.dotted_app_label),
                Locale.getDefault());
        HashMap<String, Object> args = new HashMap();
        args.put("app_name", appName);
        args.put("count", notificationCount);
        return icuCountFormat.format(args);
    }

    /**
     * Starts a long press action and returns the corresponding pre-drag condition
     */
    public PreDragCondition startLongPressAction() {
        PopupContainerWithArrow popup = PopupContainerWithArrow.showForIcon(this);
        return popup != null ? popup.createPreDragCondition(true) : null;
    }

    /**
     * Returns true if the view can show long-press popup
     */
    public boolean canShowLongPressPopup() {
        return getTag() instanceof ItemInfo && ShortcutUtil.supportsShortcuts((ItemInfo) getTag());
    }

    public String getTargetPackageName() {
        Object tag = getTag();
        if (tag instanceof ItemInfo itemInfo) {
            return itemInfo.getTargetPackage();
        }
        return null;
    }

    public boolean canShowBadge() {
        return getTag() instanceof ItemInfo && ShortcutUtil.supportsWorkspaceShortcuts((ItemInfo) getTag());
    }

	public void drawBadgeIfNecessary(Canvas canvas) {
        if (canShowBadge() && mActivity.getBadgeInfoForItem((ItemInfo) getTag()) && mOrientation == Configuration.ORIENTATION_PORTRAIT) {
            // 绘制小圆点(竖屏状态)
            Paint paint = new Paint();
            // 设置小圆点颜色
            paint.setColor(Themes.getAttrColor(getContext(), R.attr.notificationDotColor));
            // 圆点半径
            int radius = 8;
            // 圆点距离左侧的距离
            int x = getWidth() / 2 - ((int) getPaint().measureText(getText().toString()) / 2) - radius * 2;
            if (x < (int) (radius * 1.5f)) {
                x = (int) (radius * 1.5f);
            }
            // android.util.Log.d("liu-db", "drawBadgeIfNecessary: x = " + x);
            Paint.FontMetrics fm = getPaint().getFontMetrics();
            // 圆点在TextView垂直居中
            int y = getBaseline() + ((int) (fm.ascent / 2)) + radius / 2;
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mOrientation = newConfig.orientation;
    }

    public void checkAppUsage() {
        String packageName = getTargetPackageName();
        if (!TextUtils.isEmpty(packageName) && mActivity.checkBadgeShow(packageName)) {
            try {
                UsageStatsManager usageStatsManager = (UsageStatsManager)
                        getContext().getSystemService(Context.USAGE_STATS_SERVICE);
                UsageStats usageStats = usageStatsManager.queryUsageStats(
                                UsageStatsManager.INTERVAL_BEST,
                                System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 365,
                                System.currentTimeMillis())
                        .stream()
                        .filter(stat -> stat.getPackageName().equals(packageName))
                        .findFirst()
                        .orElse(null);

                if (!(usageStats == null || usageStats.getLastTimeUsed() == 0)) {
                    mShowInstallBadge = false;
                    mActivity.removeAppInstallationBadge(packageName);
                }
            } catch (Exception e) {
                android.util.Log.e("launcher-ldb", "Error checking app usage", e);
            }
        }
    }

    public Rect getIconRect() {
        int newHeight;
        int newWidth;
        int iconSize = this.mIconSize;
        Point center = new Point(getScrollX() + (getWidth() >> 1), getScrollY() + getPaddingTop() + (iconSize >> 1));
        Rect iconRect = new Rect();
        iconRect.left = center.x - (iconSize >> 1);
        iconRect.top = center.y - (iconSize >> 1);
        iconRect.right = iconRect.left + iconSize;
        iconRect.bottom = iconRect.top + iconSize;
        int centerX = (iconRect.left + iconRect.right) / 2;
        int centerY = (iconRect.top + iconRect.bottom) / 2;
        if (getContext().getResources().getBoolean(17891898)) {
            newWidth = (int) (((double) (iconRect.right - iconRect.left)) * 0.92d);
            newHeight = (int) (((double) (iconRect.bottom - iconRect.top)) * 0.92d);
        } else {
            newWidth = (int) (((double) (iconRect.right - iconRect.left)) * 0.85d);
            newHeight = (int) (((double) (iconRect.bottom - iconRect.top)) * 0.85d);
        }
        iconRect.left = centerX - (newWidth / 2);
        iconRect.top = centerY - (newHeight / 2);
        iconRect.right = (newWidth / 2) + centerX;
        iconRect.bottom = (newHeight / 2) + centerY;
        return iconRect;
    }
}
