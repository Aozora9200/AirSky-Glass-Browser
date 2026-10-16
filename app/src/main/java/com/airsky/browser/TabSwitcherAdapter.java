package com.airsky.browser;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * タブ一覧(グリッド)のアダプタ。実データ(タイトル・サムネイル等)は毎回 {@link TabSwitcher.Host} から取得する。
 *
 * 通常の一覧では、タブグループのタブを 1 枚の「グループのカード」にたたんで出す(検索中はたたまない)。
 * 非アクティブなタブは一覧から外し、一番上の「非アクティブなタブ」のカード 1 枚にまとめる。
 * groupFilter を指定すると、そのグループのタブだけを並べる(グループを開いた時のパネル用)。
 * {@link #FILTER_INACTIVE} なら非アクティブなタブ・グループだけを並べる。
 */
class TabSwitcherAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Callbacks {
        void onCardClicked(int tabIndex);
        void onCardClose(int adapterPosition);
        void onGroupClicked(int groupId);
        void onGroupMenu(int groupId, @NonNull View anchor);
        /** 一番上の「非アクティブなタブ」を押した。 */
        default void onInactiveClicked() {}
    }

    static final int TYPE_TAB = 0;
    static final int TYPE_GROUP = 1;
    static final int TYPE_INACTIVE = 2;
    /** groupFilter: 通常の一覧。 */
    static final int FILTER_NONE = -1;
    /** groupFilter: 非アクティブなタブだけ。 */
    static final int FILTER_INACTIVE = -2;
    /** 「非アクティブなタブ」のカードの印(Entry.tabIndex)。 */
    private static final int INACTIVE_HEADER = -100;
    private static final Object PAYLOAD_SELECTION = new Object();
    /** グループのカードの安定 ID(タブ ID と重ならないようにずらす)。 */
    private static final long GROUP_ID_BASE = 1L << 40;

    /** 一覧の 1 枚。タブか、たたんだグループ(groupId と先頭のタブの tabIndex)。 */
    static final class Entry {
        int tabIndex;
        final int groupId;

        Entry(int tabIndex, int groupId) {
            this.tabIndex = tabIndex;
            this.groupId = groupId;
        }

        boolean isGroup() {
            return groupId >= 0;
        }

        boolean isInactiveHeader() {
            return tabIndex == INACTIVE_HEADER;
        }

        boolean isTab() {
            return !isGroup() && !isInactiveHeader();
        }
    }

    /** サムネイルの読み込み(通常の一覧とグループのパネルで共有する)。 */
    static final class ThumbCache {
        private final LruCache<Integer, Bitmap> diskThumbs = new LruCache<Integer, Bitmap>(16 * 1024 * 1024) {
            @Override
            protected int sizeOf(Integer key, Bitmap value) {
                return value.getByteCount();
            }
        };
        private final Set<Integer> loading = new HashSet<>();
        private final ExecutorService loader = Executors.newSingleThreadExecutor();
        private final Handler main = new Handler(Looper.getMainLooper());
        private final List<TabSwitcherAdapter> listeners = new ArrayList<>();

        /** メモリ上のページ画像か、読み込み済みの保存画像。無ければ読み込みを始めて null。 */
        @Nullable
        Bitmap get(TabSwitcher.Host host, int index) {
            int id = host.getTabId(index);
            Bitmap bm = host.getTabSnapshot(index);
            if (bm == null) bm = diskThumbs.get(id);
            if (bm != null) return bm;

            File f = host.getTabSnapshotFile(index);
            if (f == null || !f.exists() || !loading.add(id)) return null;
            loader.execute(() -> {
                Bitmap decoded = null;
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    decoded = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                } catch (Throwable e) { // OOM 等でもタブ一覧は落とさない
                    e.printStackTrace();
                }
                final Bitmap result = decoded;
                main.post(() -> {
                    loading.remove(id);
                    if (result == null) return;
                    diskThumbs.put(id, result);
                    for (TabSwitcherAdapter a : listeners) a.onThumbnailLoaded(id);
                });
            });
            return null;
        }

        void release() {
            loader.shutdownNow();
            diskThumbs.evictAll();
            listeners.clear();
        }
    }

    private final TabSwitcher.Host host;
    private TabSwitcher.Palette palette;
    private final Callbacks callbacks;
    private final ThumbCache thumbs;
    /** このグループのタブだけを出す。-1 なら通常の一覧(グループはたたむ)。 */
    private final int groupFilter;
    private final List<Entry> visible = new ArrayList<>();
    /** 非アクティブなタブの index(reload() の時点)。 */
    private Set<Integer> inactive = new HashSet<>();
    private String query = "";

    TabSwitcherAdapter(TabSwitcher.Host host, TabSwitcher.Palette palette, ThumbCache thumbs,
                       int groupFilter, Callbacks callbacks) {
        this.host = host;
        this.palette = palette;
        this.thumbs = thumbs;
        this.groupFilter = groupFilter;
        this.callbacks = callbacks;
        thumbs.listeners.add(this);
        setHasStableIds(true);
    }

    // ---- データ ----

    /** タブの一覧を作り直す(検索文字列で絞り込む)。 */
    void reload() {
        visible.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean collapse = groupFilter < 0 && q.isEmpty() && host.supportsGroups();
        inactive = groupFilter < 0 ? host.getInactiveTabIndices() : new HashSet<>();
        // 通常の一覧では、非アクティブなタブを一番上のカード 1 枚にまとめる(検索中はまとめない)
        boolean hideInactive = groupFilter == FILTER_NONE && q.isEmpty();
        if (hideInactive && !inactive.isEmpty()) visible.add(new Entry(INACTIVE_HEADER, -1));
        Set<Integer> seenGroups = new HashSet<>();
        int count = host.getTabCount();
        for (int i = 0; i < count; i++) {
            int gid = host.supportsGroups() ? host.getTabGroupId(i) : -1;
            if (groupFilter >= 0 && gid != groupFilter) continue;
            if (groupFilter == FILTER_INACTIVE && !inactive.contains(i)) continue;
            if (hideInactive && inactive.contains(i)) continue;
            if (!q.isEmpty()) {
                if (matches(i, q)) visible.add(new Entry(i, -1));
                continue;
            }
            if (collapse && gid >= 0) {
                // グループは最初のタブの位置に 1 枚だけ出す
                if (seenGroups.add(gid)) visible.add(new Entry(i, gid));
                continue;
            }
            visible.add(new Entry(i, -1));
        }
        notifyDataSetChanged();
    }

    void setPalette(TabSwitcher.Palette palette) {
        this.palette = palette;
    }

    void setQuery(String q) {
        query = q != null ? q : "";
        reload();
    }

    boolean isFiltering() {
        return !query.trim().isEmpty();
    }

    int getGroupFilter() {
        return groupFilter;
    }

    /** 非アクティブなタブ・グループを並べるパネル用か。 */
    boolean isInactiveList() {
        return groupFilter == FILTER_INACTIVE;
    }

    /** その位置が一番上の「非アクティブなタブ」のカードか。 */
    boolean isInactiveHeaderAt(int position) {
        return position >= 0 && position < visible.size() && visible.get(position).isInactiveHeader();
    }

    /** 非アクティブなタブ・グループの数(グループは 1 つと数える)。 */
    int inactiveEntryCount() {
        Set<Integer> groups = new HashSet<>();
        int n = 0;
        for (int i : inactive) {
            int gid = host.supportsGroups() ? host.getTabGroupId(i) : -1;
            if (gid < 0 || groups.add(gid)) n++;
        }
        return n;
    }

    private boolean matches(int index, String q) {
        String t = host.getTabTitle(index);
        String u = host.getTabUrl(index);
        return (t != null && t.toLowerCase(Locale.ROOT).contains(q))
                || (u != null && u.toLowerCase(Locale.ROOT).contains(q));
    }

    /** その位置のタブの index。グループのカードなら -1。 */
    int tabIndexAt(int position) {
        if (position < 0 || position >= visible.size()) return -1;
        Entry e = visible.get(position);
        return e.isTab() ? e.tabIndex : -1;
    }

    /** その位置のグループの ID。タブのカードなら -1。 */
    int groupIdAt(int position) {
        if (position < 0 || position >= visible.size()) return -1;
        return visible.get(position).groupId;
    }

    /** そのタブのカードの位置。たたんだグループに入っていれば、そのグループのカードの位置。 */
    int positionForTab(int tabIndex) {
        int gid = host.supportsGroups() ? host.getTabGroupId(tabIndex) : -1;
        for (int p = 0; p < visible.size(); p++) {
            Entry e = visible.get(p);
            if (e.isTab() && e.tabIndex == tabIndex) return p;
            if (e.isGroup() && e.groupId == gid) return p;
        }
        return -1;
    }

    /** タブを閉じた後の更新(タブのカードを閉じた場合)。後ろのタブの index を 1 つずつ詰める。 */
    void onTabRemoved(int position) {
        if (position < 0 || position >= visible.size()) return;
        int removedIndex = visible.remove(position).tabIndex;
        for (Entry e : visible) {
            if (e.tabIndex > removedIndex) e.tabIndex--;
        }
        Set<Integer> shifted = new HashSet<>();
        for (int i : inactive) {
            if (i != removedIndex) shifted.add(i > removedIndex ? i - 1 : i);
        }
        inactive = shifted;
        notifyItemRemoved(position);
        refreshSelection();
    }

    /** ドラッグで並べ替えた時(検索で絞り込んでいない時だけ呼ばれる)。並びは離した時に確定する。 */
    void onEntryMoved(int from, int to) {
        visible.add(to, visible.remove(from));
        notifyItemMoved(from, to);
    }

    /**
     * 今の並び(ドラッグ後)を、タブ全体の新しい並び(元の index の並び)にする。
     * グループのカードはそのグループのタブを順に並べる。グループのパネルでは、
     * グループのタブが入っていた場所に新しい順で入れ直す。
     */
    List<Integer> computeTabOrder() {
        int count = host.getTabCount();
        List<Integer> order = new ArrayList<>(count);
        if (groupFilter >= 0) {
            int k = 0;
            for (int i = 0; i < count; i++) {
                if (host.getTabGroupId(i) == groupFilter && k < visible.size()) {
                    order.add(visible.get(k++).tabIndex);
                } else {
                    order.add(i);
                }
            }
            return order;
        }
        if (groupFilter == FILTER_INACTIVE) return null; // 非アクティブなタブは並べ替えない
        for (Entry e : visible) {
            if (e.isInactiveHeader()) {
                // 非アクティブなタブは元の並びのまま、カードの位置に入れる
                for (int i = 0; i < count; i++) {
                    if (inactive.contains(i)) order.add(i);
                }
                continue;
            }
            if (!e.isGroup()) {
                order.add(e.tabIndex);
                continue;
            }
            for (int i = 0; i < count; i++) {
                if (host.getTabGroupId(i) == e.groupId && !inactive.contains(i)) order.add(i);
            }
        }
        return order.size() == count ? order : null;
    }

    /** 選択中(現在のタブ)の色だけ塗り直す。 */
    void refreshSelection() {
        notifyItemRangeChanged(0, visible.size(), PAYLOAD_SELECTION);
    }

    private void onThumbnailLoaded(int tabId) {
        for (int p = 0; p < visible.size(); p++) {
            Entry e = visible.get(p);
            if (e.isInactiveHeader()) continue;
            if (!e.isGroup()) {
                if (host.getTabId(e.tabIndex) == tabId) notifyItemChanged(p);
            } else {
                for (int i : host.getTabsInGroup(e.groupId)) {
                    if (host.getTabId(i) == tabId) {
                        notifyItemChanged(p);
                        break;
                    }
                }
            }
        }
    }

    void release() {
        thumbs.listeners.remove(this);
    }

    // ---- RecyclerView ----

    @Override
    public int getItemCount() {
        return visible.size();
    }

    @Override
    public int getItemViewType(int position) {
        Entry e = visible.get(position);
        return e.isInactiveHeader() ? TYPE_INACTIVE : e.isGroup() ? TYPE_GROUP : TYPE_TAB;
    }

    @Override
    public long getItemId(int position) {
        Entry e = visible.get(position);
        if (e.isInactiveHeader()) return GROUP_ID_BASE * 2;
        return e.isGroup() ? GROUP_ID_BASE + e.groupId : host.getTabId(e.tabIndex);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_GROUP) {
            return new GroupHolder(inflater.inflate(R.layout.item_tab_group_card, parent, false));
        }
        if (viewType == TYPE_INACTIVE) {
            return new InactiveHolder(inflater.inflate(R.layout.item_inactive_tabs_header, parent, false));
        }
        return new CardHolder(inflater.inflate(R.layout.item_tab_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            Entry e = visible.get(position);
            if (h instanceof CardHolder) {
                applySelection((CardHolder) h, e.tabIndex == host.getCurrentTabIndex());
            } else if (h instanceof GroupHolder) {
                applyGroupSelection((GroupHolder) h, e.groupId);
            }
            return;
        }
        super.onBindViewHolder(h, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Entry entry = visible.get(position);
        if (holder instanceof InactiveHolder) {
            bindInactive((InactiveHolder) holder);
            return;
        }
        if (holder instanceof GroupHolder) {
            bindGroup((GroupHolder) holder, entry.groupId);
            return;
        }
        CardHolder h = (CardHolder) holder;
        int index = entry.tabIndex;

        h.title.setText(displayTitle(index));

        Bitmap icon = host.getTabIcon(index);
        h.hasIcon = icon != null;
        if (icon != null) {
            h.favicon.setImageBitmap(icon);
        } else {
            h.favicon.setImageResource(R.drawable.ic_ts_globe);
        }

        h.boundTabId = host.getTabId(index);
        Bitmap bm = thumbs.get(host, index);
        if (bm != null) {
            h.thumb.setImageBitmap(bm);
        } else {
            h.thumb.setImageDrawable(null);
        }
        applySelection(h, index == host.getCurrentTabIndex());

        h.itemView.setAlpha(1f);
        h.itemView.setTranslationX(0f);
        h.thumb.setAlpha(1f);

        h.card.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) callbacks.onCardClicked(tabIndexAt(pos));
        });
        h.close.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) callbacks.onCardClose(pos);
        });
    }

    private String displayTitle(int index) {
        String title = host.getTabTitle(index);
        if (title == null || title.isEmpty()) title = host.getTabUrl(index);
        if (title == null || title.isEmpty()) title = Lang.t("新しいタブ");
        return title;
    }

    private void bindGroup(GroupHolder h, int groupId) {
        TabGroup g = host.getGroup(groupId);
        List<Integer> tabs = host.getTabsInGroup(groupId);
        h.title.setText(g != null ? g.displayTitle(tabs.size()) : tabs.size() + Lang.t(" 個のタブ"));

        for (int i = 0; i < 4; i++) {
            TabGroupMosaicView.Tile tile = h.mosaic.getTile(i);
            tile.root.setBackgroundColor(palette.thumbPlaceholder);
            tile.more.setVisibility(View.GONE);
            if (i >= tabs.size()) {
                tile.thumb.setImageDrawable(null);
                tile.thumb.setAlpha(1f);
                tile.favicon.setVisibility(View.GONE);
                continue;
            }
            if (i == 3 && tabs.size() > 4) {
                // 5 枚以上は最後のマスに残りの数を出す
                tile.thumb.setImageDrawable(null);
                tile.favicon.setVisibility(View.GONE);
                tile.more.setVisibility(View.VISIBLE);
                tile.more.setText("+" + (tabs.size() - 3));
                tile.more.setTextColor(palette.onCard);
                continue;
            }
            int index = tabs.get(i);
            Bitmap bm = thumbs.get(host, index);
            tile.thumb.setImageBitmap(bm);
            tile.thumb.setAlpha(1f);
            Bitmap icon = host.getTabIcon(index);
            tile.favicon.setVisibility(View.VISIBLE);
            if (icon != null) {
                tile.favicon.setImageBitmap(icon);
                tile.favicon.setImageTintList(null);
            } else {
                tile.favicon.setImageResource(R.drawable.ic_ts_globe);
                tile.favicon.setImageTintList(ColorStateList.valueOf(palette.onCard));
            }
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(palette.card);
            tile.favicon.setBackground(badge);
        }
        applyGroupSelection(h, groupId);

        h.itemView.setAlpha(1f);
        h.itemView.setTranslationX(0f);
        h.card.setOnClickListener(v -> callbacks.onGroupClicked(groupId));
        h.menu.setOnClickListener(v -> callbacks.onGroupMenu(groupId, v));
        h.card.setContentDescription(Lang.t("タブグループ ") + h.title.getText() + Lang.t("、") + tabs.size() + Lang.t(" 個のタブ"));
    }

    /** 一番上の「非アクティブなタブ」のカード。数と、中のタブのファビコンを少しだけ出す。 */
    private void bindInactive(InactiveHolder h) {
        android.content.Context c = h.itemView.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        // 中のタブ・グループの数(グループは 1 つと数える)
        List<Integer> sorted = new ArrayList<>(inactive);
        java.util.Collections.sort(sorted);
        Set<Integer> groups = new HashSet<>();
        int tabsCount = 0;
        List<Bitmap> icons = new ArrayList<>();
        for (int i : sorted) {
            int gid = host.supportsGroups() ? host.getTabGroupId(i) : -1;
            if (gid >= 0) {
                if (!groups.add(gid)) continue;
            } else {
                tabsCount++;
            }
            if (icons.size() < 3) icons.add(host.getTabIcon(i));
        }
        h.title.setTextColor(palette.onCard);
        h.summary.setTextColor(palette.hint);
        String count = tabsCount + Lang.t(" 個のタブ") + (groups.isEmpty() ? "" : Lang.t("・") + groups.size() + Lang.t(" 個のグループ"));
        h.summary.setText(count + Lang.t("\n" + "しばらく使用していないタブと重複しているタブ"));
        h.card.setBackground(cardBackground(palette.card, palette.onCard));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(14 * d);
        iconBg.setColor(palette.accent);
        h.iconBg.setBackground(iconBg);
        h.icon.setImageTintList(ColorStateList.valueOf(palette.onAccent));
        h.chevron.setImageTintList(ColorStateList.valueOf(palette.onCard));

        h.icons.removeAllViews();
        int size = Math.round(26 * d);
        for (int i = 0; i < icons.size(); i++) {
            ImageView iv = new ImageView(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(-Math.round(8 * d)); // 少し重ねる
            int pad = Math.round(4 * d);
            iv.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(palette.thumbPlaceholder);
            bg.setStroke(Math.round(2 * d), palette.card);
            iv.setBackground(bg);
            Bitmap icon = icons.get(i);
            if (icon != null) {
                iv.setImageBitmap(icon);
            } else {
                iv.setImageResource(R.drawable.ic_ts_globe);
                iv.setImageTintList(ColorStateList.valueOf(palette.onCard));
            }
            h.icons.addView(iv, lp);
        }
        h.itemView.setAlpha(1f);
        h.itemView.setTranslationX(0f);
        h.card.setOnClickListener(v -> callbacks.onInactiveClicked());
        h.card.setContentDescription(Lang.t("非アクティブなタブ、") + count);
    }

    private void applySelection(CardHolder h, boolean selected) {
        int bg = selected ? palette.cardSelected : palette.card;
        int fg = selected ? palette.onCardSelected : palette.onCard;
        h.card.setBackground(cardBackground(bg, fg));
        h.title.setTextColor(fg);
        h.close.setImageTintList(ColorStateList.valueOf(fg));
        // ファビコンが無い時の地球アイコンだけ文字色に合わせる
        h.favicon.setImageTintList(h.hasIcon ? null : ColorStateList.valueOf(fg));
        h.thumb.setBackgroundColor(palette.thumbPlaceholder);
    }

    private void applyGroupSelection(GroupHolder h, int groupId) {
        int current = host.getCurrentTabIndex();
        boolean selected = current >= 0 && host.getTabGroupId(current) == groupId;
        int bg = selected ? palette.cardSelected : palette.card;
        int fg = selected ? palette.onCardSelected : palette.onCard;
        h.card.setBackground(cardBackground(bg, fg));
        h.title.setTextColor(fg);
        h.menu.setImageTintList(ColorStateList.valueOf(fg));
        // 選択中のカードの色(青など)とグループの色が同じでも見えるよう、文字色の縁を付ける
        TabGroup g = host.getGroup(groupId);
        GradientDrawable dot = (GradientDrawable) TabGroupUi.dot(TabGroup.colorValue(
                h.itemView.getContext(), g != null ? g.color : 0, host.isSecret()));
        if (selected) {
            dot.setStroke(Math.round(2 * h.itemView.getResources().getDisplayMetrics().density), fg);
        }
        h.dot.setBackground(dot);
    }

    private RippleDrawable cardBackground(int bg, int fg) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(palette.cardRadius);
        // タップ時の波紋(文字色を薄くした色)
        int ripple = (fg & 0x00FFFFFF) | 0x26000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), d, null);
    }

    /**
     * タブを開く/タブ一覧を開くアニメーションで、そのタブの画像が出ている場所。
     * グループのカードなら、そのタブのマス(5 枚目以降なら全体)。
     */
    @Nullable
    View transitionTargetFor(@NonNull RecyclerView.ViewHolder holder, int tabIndex) {
        if (holder instanceof CardHolder) return ((CardHolder) holder).thumb;
        if (holder instanceof GroupHolder) {
            GroupHolder g = (GroupHolder) holder;
            List<Integer> tabs = host.getTabsInGroup(host.getTabGroupId(tabIndex));
            int i = tabs.indexOf(tabIndex);
            if (i >= 0 && i < 4 && !(i == 3 && tabs.size() > 4)) return g.mosaic.getTile(i).thumb;
            return g.mosaic;
        }
        return null;
    }

    static class CardHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final ImageView favicon;
        final TextView title;
        final ImageButton close;
        final TopCropImageView thumb;
        int boundTabId = -1;
        boolean hasIcon;

        CardHolder(View v) {
            super(v);
            card = v.findViewById(R.id.tcCard);
            favicon = v.findViewById(R.id.tcFavicon);
            title = v.findViewById(R.id.tcTitle);
            close = v.findViewById(R.id.tcClose);
            thumb = v.findViewById(R.id.tcThumb);
        }
    }

    static class InactiveHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final View iconBg;
        final ImageView icon;
        final TextView title;
        final TextView summary;
        final LinearLayout icons;
        final ImageView chevron;

        InactiveHolder(View v) {
            super(v);
            card = v.findViewById(R.id.itCard);
            iconBg = v.findViewById(R.id.itIconBg);
            icon = v.findViewById(R.id.itIcon);
            title = v.findViewById(R.id.itTitle);
            summary = v.findViewById(R.id.itSummary);
            icons = v.findViewById(R.id.itIcons);
            chevron = v.findViewById(R.id.itChevron);
        }
    }

    static class GroupHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final View dot;
        final TextView title;
        final ImageButton menu;
        final TabGroupMosaicView mosaic;

        GroupHolder(View v) {
            super(v);
            card = v.findViewById(R.id.tgCard);
            dot = v.findViewById(R.id.tgDot);
            title = v.findViewById(R.id.tgTitle);
            menu = v.findViewById(R.id.tgMenu);
            mosaic = v.findViewById(R.id.tgMosaic);
        }
    }

    /** 表示中の画像(アニメーション用)。 */
    @Nullable
    static Bitmap currentBitmap(@Nullable View target) {
        if (target instanceof ImageView
                && ((ImageView) target).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            return ((android.graphics.drawable.BitmapDrawable) ((ImageView) target).getDrawable()).getBitmap();
        }
        return null;
    }
}
