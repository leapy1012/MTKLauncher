package com.android.launcher3.allapps.coloros;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customize.overlay.controller.CategoryController;
import com.android.customize.overlay.model.CategoryInfo;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Oppo Categories: full-width Recent suggestion card + 2-column folder cards.
 *
 * <p>Icon metrics mirror {@code OplusCategoryIconContainer#initIconSize}: half-screen
 * width drives a shared cell size; the drawn icon is smaller than the cell so gutters
 * appear between icons. Recent uses that same cell size (not full-width inflation).
 */
public final class ColorOsCategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_RECENT = 0;
    private static final int TYPE_CATEGORY = 1;
    private static final int MAX_PREVIEW = 4;
    private static final int MAX_RECENT_COLLECT = 8;

    private final CategoryController mController;
    private final List<Row> mRows = new ArrayList<>();
    private float mDensity = 3f;
    @Nullable
    private CellMetrics mMetrics;

    public ColorOsCategoryAdapter(CategoryController controller) {
        mController = controller;
    }

    public void bind(@NonNull Context context, @NonNull List<AppInfo> apps,
            @NonNull List<CategoryInfo> categories) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mMetrics = metricsForContext(context);
        mRows.clear();

        List<AppInfo> recent = collectRecentApps(context, apps);
        if (!recent.isEmpty()) {
            mRows.add(Row.recent(context.getString(R.string.coloros_category_recently_installed),
                    recent));
        }

        for (CategoryInfo info : categories) {
            List<AppInfo> resolved = resolveApps(info);
            if (resolved.isEmpty()) {
                continue;
            }
            mRows.add(Row.category(info.getFolderName(), resolved));
        }
        notifyDataSetChanged();
    }

    public void attachSpanSizeLookup(GridLayoutManager glm) {
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position < 0 || position >= mRows.size()) {
                    return 1;
                }
                return mRows.get(position).type == TYPE_RECENT ? glm.getSpanCount() : 1;
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_RECENT) {
            return createRecentVH(parent.getContext());
        }
        return createCategoryVH(parent.getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = mRows.get(position);
        if (holder instanceof RecentVH) {
            ((RecentVH) holder).bind(row);
        } else if (holder instanceof CategoryVH) {
            ((CategoryVH) holder).bind(row);
        }
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    /**
     * Oppo {@code initIconSize} with screen-aware icon so inner gutters stay ~8dp
     * (fixed 54dp on 1224/500dpi left huge empty cells vs Oppo 1080/480).
     */
    private CellMetrics metricsForContext(Context context) {
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int pagePad = context.getResources().getDimensionPixelSize(
                R.dimen.coloros_category_page_padding);
        int folderPad = context.getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_padding);
        int half = screenW / 2;
        // Target Oppo phone inner (~8dp); grow icon on wider / denser panels.
        int targetInner = dp(8);
        int iconSize = (half - pagePad - folderPad - targetInner * 5) / 2;
        iconSize = Math.max(dp(48), Math.min(dp(64), iconSize));
        int inner = Math.max(dp(6), (half - pagePad - folderPad - iconSize * 2) / 5);
        int cell = (inner * 3) / 2 + iconSize;
        int colContent = half - pagePad;
        if (cell * 2 + inner * 2 + folderPad > colContent) {
            cell = Math.max(iconSize, (colContent - folderPad - inner * 2) / 2);
        }
        return new CellMetrics(cell, inner, folderPad / 2, iconSize);
    }

    private CellMetrics requireMetrics(Context context) {
        if (mMetrics == null) {
            mMetrics = metricsForContext(context);
        }
        return mMetrics;
    }

    private CategoryVH createCategoryVH(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        GridLayoutManager.LayoutParams lp = new GridLayoutManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Oppo column gap is page padding only — no extra item margins.
        lp.setMargins(0, dp(16), 0, 0);
        root.setLayoutParams(lp);

        // Oppo draws frosted bg inset by folderPad/2 each side → ~16dp column gap.
        int cardInset = context.getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_padding) / 2;
        FrameLayout card = new FrameLayout(context);
        card.setBackground(cardBackground(context));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMarginStart(cardInset);
        cardLp.setMarginEnd(cardInset);
        root.addView(card, cardLp);

        TwoByTwoIconGrid icons = new TwoByTwoIconGrid(context);
        card.addView(icons, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = newTextLabel(context);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new CategoryVH(root, card, icons, title);
    }

    private RecentVH createRecentVH(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        GridLayoutManager.LayoutParams lp = new GridLayoutManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Oppo category_suggested_folder_padding_top — space under segment to card.
        int recentTop = context.getResources().getDimensionPixelSize(
                R.dimen.coloros_category_recent_padding_top);
        lp.setMargins(0, recentTop, 0, 0);
        root.setLayoutParams(lp);

        FrameLayout card = new FrameLayout(context);
        card.setBackground(cardBackground(context));
        // Oppo suggestion bg also inset by folderPad/2.
        int side = context.getResources().getDimensionPixelSize(
                R.dimen.coloros_category_folder_padding) / 2;
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMarginStart(side);
        cardLp.setMarginEnd(side);
        root.addView(card, cardLp);

        FrameLayout icons = new FrameLayout(context);
        card.addView(icons, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = newTextLabel(context);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new RecentVH(root, card, icons, title);
    }

    private TextView newTextLabel(Context context) {
        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(12);
        title.setMaxLines(1);
        title.setPadding(dp(4), dp(4), dp(4), dp(2));
        return title;
    }

    private GradientDrawable cardBackground(Context context) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(context.getResources().getDimension(
                R.dimen.coloros_category_card_radius));
        bg.setColor(context.getResources().getColor(R.color.coloros_category_card_surface, null));
        return bg;
    }

    private List<AppInfo> resolveApps(CategoryInfo info) {
        List<AppInfo> out = new ArrayList<>();
        for (String component : info.getComponentNames()) {
            AppInfo app = mController.getAppInfo(component);
            if (app != null) {
                out.add(app);
            }
        }
        return out;
    }

    private static List<AppInfo> collectRecentApps(Context context, List<AppInfo> apps) {
        PackageManager pm = context.getPackageManager();
        class Ranked {
            final AppInfo app;
            final long rank;
            final boolean userApp;

            Ranked(AppInfo app, long rank, boolean userApp) {
                this.app = app;
                this.rank = rank;
                this.userApp = userApp;
            }
        }
        List<Ranked> ranked = new ArrayList<>();
        for (AppInfo app : apps) {
            if (app == null || app.componentName == null) {
                continue;
            }
            try {
                android.content.pm.PackageInfo pi =
                        pm.getPackageInfo(app.componentName.getPackageName(), 0);
                android.content.pm.ApplicationInfo ai = pi.applicationInfo;
                boolean system = ai != null
                        && (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean updatedSystem = ai != null
                        && (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                        != 0;
                ranked.add(new Ranked(app, Math.max(pi.firstInstallTime, pi.lastUpdateTime),
                        !system || updatedSystem));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Skip.
            }
        }
        Collections.sort(ranked, (a, b) -> Long.compare(b.rank, a.rank));

        List<AppInfo> collected = new ArrayList<>(MAX_RECENT_COLLECT);
        for (Ranked r : ranked) {
            if (r.userApp) {
                collected.add(r.app);
                if (collected.size() >= MAX_RECENT_COLLECT) {
                    break;
                }
            }
        }
        if (collected.size() < MAX_RECENT_COLLECT) {
            for (Ranked r : ranked) {
                if (!collected.contains(r.app)) {
                    collected.add(r.app);
                    if (collected.size() >= MAX_RECENT_COLLECT) {
                        break;
                    }
                }
            }
        }
        if (collected.size() > MAX_PREVIEW) {
            return new ArrayList<>(collected.subList(0, MAX_PREVIEW));
        }
        return collected;
    }

    private int dp(int value) {
        return Math.round(value * mDensity);
    }

    @Nullable
    private Bitmap stackedPreview(List<AppInfo> apps, int cellPx) {
        if (apps.size() <= MAX_PREVIEW) {
            return null;
        }
        int start = MAX_PREVIEW - 1;
        int end = Math.min(apps.size(), start + 4);
        List<Bitmap> bits = new ArrayList<>();
        for (int i = start; i < end; i++) {
            AppInfo app = apps.get(i);
            if (app.bitmap != null && app.bitmap.icon != null) {
                bits.add(app.bitmap.icon);
            }
        }
        if (bits.size() < 2) {
            return null;
        }
        return createFolderPreviewBitmap(bits.toArray(new Bitmap[0]), cellPx);
    }

    private static Bitmap createFolderPreviewBitmap(Bitmap[] bitmaps, int intrinsicSize) {
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        Rect src = new Rect();
        Rect dst = new Rect();
        Bitmap preview = Bitmap.createBitmap(intrinsicSize, intrinsicSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(preview);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(
                0, Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        float size = intrinsicSize;
        // Oppo folder_preview_padding (~7/size) + sub gap (~3).
        float inset = size * 0.08f;
        float gap = size * 0.04f;
        float small = (size - inset * 2f - gap) / 2f;
        float step = small + gap;
        for (int index = 0; index < bitmaps.length && index < 4; index++) {
            Bitmap bitmap = bitmaps[index];
            if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
            int col = index % 2;
            int row = index / 2;
            float x = inset + col * step;
            float y = inset + row * step;
            src.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            dst.set(Math.round(x), Math.round(y),
                    Math.round(x + small), Math.round(y + small));
            canvas.drawBitmap(bitmap, src, dst, paint);
        }
        return preview;
    }

    private static final class CellMetrics {
        final int cell;
        final int inner;
        final int sideInset;
        final int icon;

        CellMetrics(int cell, int inner, int sideInset, int icon) {
            this.cell = cell;
            this.inner = inner;
            this.sideInset = sideInset;
            this.icon = icon;
        }
    }

    private static final class Row {
        final int type;
        final String title;
        final List<AppInfo> apps;

        private Row(int type, String title, List<AppInfo> apps) {
            this.type = type;
            this.title = title;
            this.apps = apps;
        }

        static Row recent(String title, List<AppInfo> apps) {
            return new Row(TYPE_RECENT, title, apps);
        }

        static Row category(String title, List<AppInfo> apps) {
            return new Row(TYPE_CATEGORY, title, apps);
        }
    }

    private final class CategoryVH extends RecyclerView.ViewHolder {
        private final FrameLayout card;
        private final TwoByTwoIconGrid icons;
        private final TextView title;

        CategoryVH(View itemView, FrameLayout card, TwoByTwoIconGrid icons, TextView title) {
            super(itemView);
            this.card = card;
            this.icons = icons;
            this.title = title;
        }

        void bind(Row row) {
            title.setText(row.title);
            CellMetrics m = requireMetrics(itemView.getContext());
            int cardH = m.cell * 2 + m.inner * 2;
            LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) card.getLayoutParams();
            if (clp.height != cardH) {
                clp.height = cardH;
                card.setLayoutParams(clp);
            }
            FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) icons.getLayoutParams();
            // Card already has Oppo side inset; icon pad is inner only.
            if (ilp.leftMargin != m.inner || ilp.topMargin != m.inner
                    || ilp.rightMargin != m.inner || ilp.bottomMargin != m.inner) {
                ilp.setMargins(m.inner, m.inner, m.inner, m.inner);
                icons.setLayoutParams(ilp);
            }
            icons.setMetrics(m);
            icons.bind(row.apps);
        }
    }

    private final class RecentVH extends RecyclerView.ViewHolder {
        private final FrameLayout card;
        private final FrameLayout icons;
        private final TextView title;

        RecentVH(View itemView, FrameLayout card, FrameLayout icons, TextView title) {
            super(itemView);
            this.card = card;
            this.icons = icons;
            this.title = title;
        }

        void bind(Row row) {
            title.setText(row.title);
            final List<AppInfo> apps = row.apps;
            final int n = Math.min(MAX_PREVIEW, apps.size());
            final CellMetrics m = requireMetrics(itemView.getContext());

            Runnable layout = () -> {
                int w = card.getWidth();
                if (w <= 0) {
                    w = itemView.getWidth();
                }
                if (w <= 0) {
                    return;
                }
                FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) icons.getLayoutParams();
                // Horizontal inset handled in child leftMargin (Oppo suggestion layout).
                ilp.setMargins(0, m.inner, 0, m.inner);
                ilp.height = m.cell;
                icons.setLayoutParams(ilp);

                LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) card.getLayoutParams();
                clp.height = m.cell + m.inner * 2;
                card.setLayoutParams(clp);

                icons.removeAllViews();
                // Oppo onLayoutSuggestion: leftover width becomes gaps between cells.
                // Card already inset by folderPad/2; only use inner pad here.
                int pad = m.inner;
                int contentW = Math.max(0, w);
                int avail = Math.max(0, contentW - pad * 2);
                int gap = n > 1 ? Math.max(0, (avail - m.cell * n) / (n - 1)) : 0;
                int iconPad = Math.max(0, (m.cell - m.icon) / 2);
                for (int i = 0; i < n; i++) {
                    AppInfo app = apps.get(i);
                    ImageView iv = new ImageView(itemView.getContext());
                    if (app.bitmap != null) {
                        iv.setImageBitmap(app.bitmap.icon);
                    }
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setPadding(iconPad, iconPad, iconPad, iconPad);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(m.cell, m.cell);
                    lp.leftMargin = pad + i * (m.cell + gap);
                    lp.topMargin = 0;
                    icons.addView(iv, lp);
                }
            };
            if (card.getWidth() > 0) {
                layout.run();
            } else {
                card.post(layout);
            }
        }
    }

    private final class TwoByTwoIconGrid extends ViewGroup {
        private CellMetrics mM;
        private List<AppInfo> mApps = Collections.emptyList();

        TwoByTwoIconGrid(Context context) {
            super(context);
            setClipChildren(true);
            mM = new CellMetrics(dp(54), dp(8), dp(8), dp(54));
        }

        void setMetrics(CellMetrics m) {
            if (m != null && (mM == null || m.cell != mM.cell || m.icon != mM.icon)) {
                mM = m;
                requestLayout();
            }
        }

        void bind(List<AppInfo> apps) {
            mApps = apps;
            removeAllViews();
            boolean overflow = apps.size() > MAX_PREVIEW;
            int slots = Math.min(MAX_PREVIEW, apps.size());
            int iconPad = Math.max(0, (mM.cell - mM.icon) / 2);
            for (int i = 0; i < slots; i++) {
                ImageView iv = new ImageView(getContext());
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(iconPad, iconPad, iconPad, iconPad);
                boolean lastStacked = overflow && i == slots - 1;
                if (lastStacked) {
                    Bitmap stacked = stackedPreview(apps, Math.max(mM.icon, dp(48)));
                    if (stacked != null) {
                        iv.setImageBitmap(stacked);
                    } else if (apps.get(i).bitmap != null) {
                        iv.setImageBitmap(apps.get(i).bitmap.icon);
                    }
                } else if (apps.get(i).bitmap != null) {
                    iv.setImageBitmap(apps.get(i).bitmap.icon);
                }
                addView(iv);
            }
            post(this::refreshStackedAtCellSize);
            requestLayout();
        }

        private void refreshStackedAtCellSize() {
            if (mApps.size() <= MAX_PREVIEW || getChildCount() == 0) {
                return;
            }
            ImageView last = (ImageView) getChildAt(getChildCount() - 1);
            Bitmap stacked = stackedPreview(mApps, mM.icon);
            if (stacked != null) {
                last.setImageBitmap(stacked);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int h = mM.cell * 2;
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                h = MeasureSpec.getSize(heightMeasureSpec);
            }
            setMeasuredDimension(w, h);
            int cellSpec = MeasureSpec.makeMeasureSpec(mM.cell, MeasureSpec.EXACTLY);
            int n = Math.min(4, getChildCount());
            for (int i = 0; i < n; i++) {
                getChildAt(i).measure(cellSpec, cellSpec);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int n = Math.min(4, getChildCount());
            if (n == 0) {
                return;
            }
            // Oppo onLayoutCategory: cells are adjacent (no distributed gap).
            int cell = mM.cell;
            for (int i = 0; i < n; i++) {
                int col = i % 2;
                int row = i / 2;
                int cl = col * cell;
                int ct = row * cell;
                getChildAt(i).layout(cl, ct, cl + cell, ct + cell);
            }
        }
    }
}
