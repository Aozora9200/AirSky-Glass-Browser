package com.airsky.browser;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * ページのリンクを長押しした時のメニュー(Chrome のリンクのメニューと同じ並び)。
 * 上にリンク先のファビコン・サイト名・URL を出し、その下に操作を並べる。
 */
final class LinkMenuSheet {

    /** メニューの 1 項目。 */
    static final class Item {
        @DrawableRes final int icon;
        final String label;
        final Runnable action;

        Item(@DrawableRes int icon, @NonNull String label, @NonNull Runnable action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
        }
    }

    private LinkMenuSheet() {}

    /**
     * @param favicon リンク先のサイトのファビコン(分かれば)
     */
    static BottomSheetDialog show(@NonNull Activity activity, @NonNull String url, @Nullable Bitmap favicon,
                                  @NonNull List<Item> items) {
        BottomSheetDialog dialog = TopSheet.adapt(activity, new BottomSheetDialog(activity));
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_link_menu, null);
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        ImageView icon = sheet.findViewById(R.id.lm_icon);
        if (favicon != null) {
            icon.setImageBitmap(favicon);
            icon.setImageTintList(null);
        } else {
            icon.setImageTintList(ColorStateList.valueOf(
                    MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        }
        ((TextView) sheet.findViewById(R.id.lm_title)).setText(siteName(url));
        TextView urlView = sheet.findViewById(R.id.lm_url);
        urlView.setText(url);

        LinearLayout rows = sheet.findViewById(R.id.lm_rows);
        LayoutInflater inflater = LayoutInflater.from(sheet.getContext());
        int onVariant = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant);
        boolean[] chosen = {false};
        List<View> rowViews = new ArrayList<>();
        for (Item item : items) {
            View row = inflater.inflate(R.layout.item_link_menu_row, rows, false);
            ImageView ri = row.findViewById(R.id.lm_row_icon);
            ri.setImageResource(item.icon);
            ri.setImageTintList(ColorStateList.valueOf(onVariant));
            ((TextView) row.findViewById(R.id.lm_row_label)).setText(item.label);
            row.setOnClickListener(v -> {
                if (chosen[0]) return;
                chosen[0] = true;
                dialog.dismiss();
                item.action.run();
            });
            rows.addView(row);
            rowViews.add(row);
        }

        // 開く時: 行が上から順にふわっと出てくる
        dialog.setOnShowListener(d -> {
            float density = activity.getResources().getDisplayMetrics().density;
            for (int i = 0; i < rowViews.size(); i++) {
                View r = rowViews.get(i);
                r.setAlpha(0f);
                r.setTranslationY(8 * density);
                r.animate().alpha(1f).translationY(0f).setStartDelay(30L + 18L * i).setDuration(220)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        });
        dialog.show();
        return dialog;
    }

    /** URL のサイト名(ホスト名から www. を除いたもの)。 */
    @NonNull
    static String siteName(@NonNull String url) {
        String host = null;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception ignored) {
        }
        if (host == null || host.isEmpty()) return url;
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
