package com.airsky.browser;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * タブ一覧の「タブグループ」(開いているグループ・閉じて保存したグループ)の一覧。
 * 行はひとまとまりの角丸の箱に見えるよう、先頭と末尾の角だけ大きく丸める(Chrome と同じ)。
 */
class TabGroupListAdapter extends RecyclerView.Adapter<TabGroupListAdapter.RowHolder> {

    interface Callbacks {
        void onGroupRowClicked(int groupId);
        void onGroupRowMenu(int groupId, @NonNull View anchor);
    }

    private final TabSwitcher.Host host;
    private TabSwitcher.Palette palette;
    private final Callbacks callbacks;
    private final List<TabGroup> groups = new ArrayList<>();
    private String query = "";

    TabGroupListAdapter(TabSwitcher.Host host, TabSwitcher.Palette palette, Callbacks callbacks) {
        this.host = host;
        this.palette = palette;
        this.callbacks = callbacks;
        setHasStableIds(true);
    }

    void setPalette(TabSwitcher.Palette palette) {
        this.palette = palette;
    }

    void setQuery(String q) {
        query = q != null ? q.trim().toLowerCase(Locale.ROOT) : "";
        reload();
    }

    void reload() {
        groups.clear();
        for (TabGroup g : host.getGroups()) {
            if (!query.isEmpty() && !g.displayTitle(tabCount(g)).toLowerCase(Locale.ROOT).contains(query)) continue;
            groups.add(g);
        }
        notifyDataSetChanged();
    }

    private int tabCount(TabGroup g) {
        return g.closed ? g.saved.size() : host.countTabsInGroup(g.id);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    @Override
    public long getItemId(int position) {
        return groups.get(position).id;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RowHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tab_group_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder h, int position) {
        TabGroup g = groups.get(position);
        float d = h.itemView.getResources().getDisplayMetrics().density;
        int count = tabCount(g);

        // 先頭と末尾の行だけ外側の角を大きく丸める
        float big = 28 * d, small = 6 * d;
        float top = position == 0 ? big : small;
        float bottom = position == groups.size() - 1 ? big : small;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.card);
        bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        int ripple = (palette.onCard & 0x00FFFFFF) | 0x26000000;
        h.itemView.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), bg, null));

        h.title.setText(g.displayTitle(count));
        h.title.setTextColor(palette.onCard);
        h.subtitle.setText(TabGroupUi.updatedText(g.updated));
        h.subtitle.setTextColor(palette.hint);
        h.dot.setBackground(TabGroupUi.dot(TabGroup.colorValue(h.itemView.getContext(), g.color, host.isSecret())));
        h.menu.setImageTintList(ColorStateList.valueOf(palette.onCard));

        // 最初の 4 つのタブのファビコンを 2 × 2 で並べる
        GradientDrawable box = new GradientDrawable();
        box.setColor(palette.thumbPlaceholder);
        box.setCornerRadius(22 * d);
        h.icons.setBackground(box);
        h.icons.removeAllViews();
        // 92dp の箱(内側の余白 5dp)に 2 × 2 で収まる大きさ
        int cell = Math.round(37 * d);
        int pad = Math.round(2 * d);
        for (int i = 0; i < 4; i++) {
            FrameLayout tile = new FrameLayout(h.itemView.getContext());
            GradientDrawable t = new GradientDrawable();
            t.setColor(palette.card);
            t.setCornerRadius(14 * d);
            tile.setBackground(t);
            Bitmap icon = i < count ? iconOf(g, i) : null;
            if (i < count) {
                ImageView iv = new ImageView(h.itemView.getContext());
                if (icon != null) {
                    iv.setImageBitmap(icon);
                } else {
                    iv.setImageResource(R.drawable.ic_ts_globe);
                    iv.setImageTintList(ColorStateList.valueOf(palette.hint));
                }
                int s = Math.round(20 * d);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, android.view.Gravity.CENTER);
                tile.addView(iv, lp);
            }
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cell;
            glp.height = cell;
            glp.setMargins(pad, pad, pad, pad);
            h.icons.addView(tile, glp);
        }

        h.itemView.setOnClickListener(v -> callbacks.onGroupRowClicked(g.id));
        h.menu.setOnClickListener(v -> callbacks.onGroupRowMenu(g.id, v));
        h.itemView.setContentDescription(g.displayTitle(count) + Lang.t("、") + count + Lang.t(" 個のタブ、")
                + TabGroupUi.updatedText(g.updated));
    }

    @Nullable
    private Bitmap iconOf(TabGroup g, int i) {
        if (g.closed) {
            return i < g.saved.size() ? host.getFaviconForUrl(g.saved.get(i).url) : null;
        }
        List<Integer> tabs = host.getTabsInGroup(g.id);
        return i < tabs.size() ? host.getTabIcon(tabs.get(i)) : null;
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final GridLayout icons;
        final View dot;
        final TextView title, subtitle;
        final ImageButton menu;

        RowHolder(View v) {
            super(v);
            icons = v.findViewById(R.id.tgIcons);
            dot = v.findViewById(R.id.tgDot);
            title = v.findViewById(R.id.tgTitle);
            subtitle = v.findViewById(R.id.tgSubtitle);
            menu = v.findViewById(R.id.tgMenu);
        }
    }
}
