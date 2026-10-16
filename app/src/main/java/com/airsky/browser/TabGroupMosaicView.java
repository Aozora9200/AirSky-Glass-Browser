package com.airsky.browser;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * タブ一覧のタブグループのカードの中身。グループのタブを 2 × 2 の小さなサムネイルで並べる(Chrome と同じ)。
 * 高さはタブのカードのサムネイルと同じ(幅 × {@link TopCropImageView#ASPECT})にして、一覧の行をそろえる。
 */
public class TabGroupMosaicView extends ViewGroup {

    /** 1 マス分。サムネイル・左上のファビコン・「+3」などの残りの数。 */
    static final class Tile {
        final FrameLayout root;
        final TopCropImageView thumb;
        final ImageView favicon;
        final TextView more;

        Tile(FrameLayout root, TopCropImageView thumb, ImageView favicon, TextView more) {
            this.root = root;
            this.thumb = thumb;
            this.favicon = favicon;
            this.more = more;
        }
    }

    private final Tile[] tiles = new Tile[4];
    private final int gap;
    private final float tileRadius;

    public TabGroupMosaicView(Context context) {
        this(context, null);
    }

    public TabGroupMosaicView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        gap = Math.round(6 * d);
        tileRadius = 14 * d;
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = createTile(context, d);
            addView(tiles[i].root);
        }
    }

    private Tile createTile(Context context, float d) {
        FrameLayout root = new FrameLayout(context);
        root.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), tileRadius);
            }
        });
        root.setClipToOutline(true);

        TopCropImageView thumb = new TopCropImageView(context);
        thumb.setCornerRadius(tileRadius);
        root.addView(thumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView favicon = new ImageView(context);
        int size = Math.round(22 * d);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
        flp.setMargins(Math.round(6 * d), Math.round(6 * d), 0, 0);
        int pad = Math.round(3 * d);
        favicon.setPadding(pad, pad, pad, pad);
        favicon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        favicon.setElevation(2 * d);
        root.addView(favicon, flp);

        TextView more = new TextView(context);
        more.setGravity(Gravity.CENTER);
        more.setTextSize(18);
        more.setVisibility(GONE);
        root.addView(more, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new Tile(root, thumb, favicon, more);
    }

    Tile getTile(int i) {
        return tiles[i];
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = Math.round(w * TopCropImageView.ASPECT);
        int tw = (w - gap) / 2;
        int th = (h - gap) / 2;
        for (Tile t : tiles) {
            t.root.measure(MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(th, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        int tw = (w - gap) / 2;
        int th = (h - gap) / 2;
        for (int i = 0; i < tiles.length; i++) {
            int x = (i % 2) * (tw + gap);
            int y = (i / 2) * (th + gap);
            tiles[i].root.layout(x, y, x + tw, y + th);
        }
    }
}
