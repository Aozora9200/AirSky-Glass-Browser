package com.airsky.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ブックマークの画面(一覧・編集・移動先)で共通の見た目。
 * フォルダは「角丸のタイル(左上にアイコン、右下に中身の数)」、ブックマークはページの画像か頭文字のタイル。
 */
final class BookmarkViews {

    private BookmarkViews() {}

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    /** 決まったフォルダごとの色(タイルの下地・アイコン)。普通のフォルダは落ち着いた色。 */
    static int[] folderColors(@NonNull View v, @NonNull BookmarkStore.Node folder) {
        int bg, fg;
        if (folder.id == BookmarkStore.ROOT_BOOKMARKS) {
            bg = com.google.android.material.R.attr.colorPrimaryContainer;
            fg = com.google.android.material.R.attr.colorOnPrimaryContainer;
        } else if (folder.id == BookmarkStore.ROOT_OTHER) {
            bg = com.google.android.material.R.attr.colorSecondaryContainer;
            fg = com.google.android.material.R.attr.colorOnSecondaryContainer;
        } else if (folder.id == BookmarkStore.ROOT_READING) {
            bg = com.google.android.material.R.attr.colorTertiaryContainer;
            fg = com.google.android.material.R.attr.colorOnTertiaryContainer;
        } else {
            bg = com.google.android.material.R.attr.colorSurfaceContainerHighest;
            fg = com.google.android.material.R.attr.colorOnSurfaceVariant;
        }
        return new int[]{MaterialColors.getColor(v, bg), MaterialColors.getColor(v, fg)};
    }

    /**
     * フォルダのタイル。
     * @param tile アイコン(R.id.bmTileIcon)と数(R.id.bmTileCount)を持つ角丸の板
     */
    static void bindFolderTile(@NonNull View tile, @NonNull BookmarkStore.Node folder, int count) {
        int[] c = folderColors(tile, folder);
        tile.setBackgroundTintList(ColorStateList.valueOf(c[0]));
        ImageView icon = tile.findViewById(R.id.bmTileIcon);
        icon.setImageResource(BookmarkStore.iconOf(folder));
        icon.setImageTintList(ColorStateList.valueOf(c[1]));
        TextView n = tile.findViewById(R.id.bmTileCount);
        n.setText(String.valueOf(count));
        n.setTextColor(c[1]);
        n.setVisibility(View.VISIBLE);
    }

    /**
     * ブックマークのタイル。保存した時のページの画像があれば画像、無ければサイト名の頭文字。
     * @param image R.id.bmThumb / @param letter R.id.bmLetter
     */
    static void bindPageTile(@NonNull View tile, @NonNull ImageView image, @NonNull TextView letter,
                             @NonNull BookmarkStore.Node node) {
        String host = hostOf(node.url);
        String label = host != null ? host : node.title;
        String first = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(Locale.ROOT);
        // サイトごとに決まった色(同じサイトはいつも同じ色)
        int[][] palette = {
                {com.google.android.material.R.attr.colorPrimaryContainer, com.google.android.material.R.attr.colorOnPrimaryContainer},
                {com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorOnSecondaryContainer},
                {com.google.android.material.R.attr.colorTertiaryContainer, com.google.android.material.R.attr.colorOnTertiaryContainer},
        };
        int[] p = palette[Math.abs(label.hashCode()) % palette.length];
        tile.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(tile, p[0])));
        letter.setText(first);
        letter.setTextColor(MaterialColors.getColor(tile, p[1]));
        letter.setVisibility(View.VISIBLE);
        image.setImageDrawable(null);
        image.setVisibility(View.GONE);

        final String path = node.thumb;
        image.setTag(path);
        if (path == null || path.isEmpty() || path.matches("\\d+")) return; // 古い版は数字(リソース ID)を入れていた
        Bitmap cached = THUMBS.get(path);
        if (cached != null) {
            showThumb(image, letter, cached);
            return;
        }
        int size = Math.max(96, tile.getLayoutParams() != null ? tile.getLayoutParams().width : 160);
        EXECUTOR.execute(() -> {
            Bitmap bm = decode(path, size);
            if (bm == null) return;
            THUMBS.put(path, bm);
            MAIN.post(() -> {
                if (path.equals(image.getTag())) showThumb(image, letter, bm);
            });
        });
    }

    private static void showThumb(ImageView image, TextView letter, Bitmap bm) {
        image.setImageBitmap(bm);
        image.setVisibility(View.VISIBLE);
        letter.setVisibility(View.GONE);
    }

    @Nullable
    private static Bitmap decode(String path, int size) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 画像を作り直した時などに、古い縮小画像を捨てる。 */
    static void forget(@Nullable String path) {
        if (path != null) THUMBS.remove(path);
    }

    @Nullable
    static String hostOf(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            String h = Uri.parse(url).getHost();
            if (h == null || h.isEmpty()) return null;
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return null;
        }
    }

    /** 「ブックマーク」「その他のブックマーク」など、保存先として見せる名前。 */
    @NonNull
    static String folderName(@NonNull Context ctx, long id) {
        BookmarkStore.Node n = BookmarkStore.get(ctx).node(id);
        return n != null ? n.title : Lang.t("ブックマーク");
    }
}
