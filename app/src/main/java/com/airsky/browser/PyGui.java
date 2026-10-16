package com.airsky.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Python のプログラム(tkinter / wx / flet の互換モジュール)から届いた画面の指示を、Android の部品で組み立てる。
 *
 * <p>Python とは 127.0.0.1 のソケットで、1 行 1 つの JSON をやり取りする
 * (Python 側は assets/python/gui/_airsky_gui.py)。部品は Material 3(Pixel 風)の見た目にそろえる。
 * 窓は 1 つめをパネルいっぱいに、Toplevel・ダイアログはその上にカードとして重ねて出す。</p>
 */
final class PyGui {

    interface Host {
        /** 窓ができた・消えた(コンソールと窓の切り替えを出す) */
        void onWindowsChanged(int visibleWindows);

        /** 端末のファイルを選んでもらう(ファイルの選択ダイアログの「取り込む」) */
        void pickDocument(@NonNull ValueCallback<Uri> done);

        @NonNull File workDir();
    }

    interface ValueCallback<T> {
        void accept(@Nullable T value);
    }

    private final Activity a;
    private final Host host;
    private final FrameLayout area;
    private final FrameLayout overlay;
    private final View scrim;
    private final float d;
    private final Handler ui = new Handler(Looper.getMainLooper());
    final String token;
    private ServerSocket server;
    private volatile boolean closed;
    private final List<Conn> conns = new ArrayList<>();
    private final Map<Long, Node> nodes = new HashMap<>();
    private final List<Win> windows = new ArrayList<>();
    private final ConcurrentLinkedQueue<Object[]> inbox = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private ToneGenerator tone;

    private final int cPrimary, cOnPrimary, cSecondaryContainer, cOnSecondaryContainer, cPrimaryContainer,
            cOnPrimaryContainer, cSurface, cOnSurface, cOnSurfaceVariant, cOutline, cOutlineVariant,
            cSurfaceContainer, cSurfaceContainerLow, cSurfaceContainerHigh, cSurfaceContainerHighest;

    PyGui(@NonNull Activity a, @NonNull Host host, @NonNull FrameLayout area) {
        this.a = a;
        this.host = host;
        this.area = area;
        d = a.getResources().getDisplayMetrics().density;
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        token = sb.toString();

        cPrimary = attr(androidx.appcompat.R.attr.colorPrimary);
        cOnPrimary = attr(com.google.android.material.R.attr.colorOnPrimary);
        cPrimaryContainer = attr(com.google.android.material.R.attr.colorPrimaryContainer);
        cOnPrimaryContainer = attr(com.google.android.material.R.attr.colorOnPrimaryContainer);
        cSecondaryContainer = attr(com.google.android.material.R.attr.colorSecondaryContainer);
        cOnSecondaryContainer = attr(com.google.android.material.R.attr.colorOnSecondaryContainer);
        cSurface = attr(com.google.android.material.R.attr.colorSurface);
        cOnSurface = attr(com.google.android.material.R.attr.colorOnSurface);
        cOnSurfaceVariant = attr(com.google.android.material.R.attr.colorOnSurfaceVariant);
        cOutline = attr(com.google.android.material.R.attr.colorOutline);
        cOutlineVariant = attr(com.google.android.material.R.attr.colorOutlineVariant);
        cSurfaceContainer = attr(com.google.android.material.R.attr.colorSurfaceContainer);
        cSurfaceContainerLow = attr(com.google.android.material.R.attr.colorSurfaceContainerLow);
        cSurfaceContainerHigh = attr(com.google.android.material.R.attr.colorSurfaceContainerHigh);
        cSurfaceContainerHighest = attr(com.google.android.material.R.attr.colorSurfaceContainerHighest);

        overlay = new FrameLayout(a);
        scrim = new View(a);
        scrim.setBackgroundColor(Color.argb(110, 0, 0, 0));
        scrim.setClickable(true);
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        overlay.setVisibility(View.GONE);
        area.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private int attr(int id) {
        return MaterialColors.getColor(a, id, Color.GRAY);
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    // ==================================================================
    // 通信
    // ==================================================================

    /** 待ち受けを始めて、ポート番号を返す。 */
    int start() throws IOException {
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            int index = 0;
            while (!closed) {
                try {
                    Socket s = server.accept();
                    Conn c = new Conn(++index, s);
                    synchronized (conns) {
                        conns.add(c);
                    }
                    c.start();
                } catch (IOException e) {
                    if (closed) return;
                }
            }
        }, "py-gui-accept");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    void close() {
        closed = true;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        synchronized (conns) {
            for (Conn c : conns) c.close();
            conns.clear();
        }
        if (tone != null) tone.release();
        tone = null;
    }

    /** プログラムが終わった: 窓を全部閉じる。 */
    void reset() {
        ui.post(() -> {
            synchronized (conns) {
                for (Conn c : conns) c.close();
                conns.clear();
            }
            for (Win w : new ArrayList<>(windows)) removeWindow(w);
            nodes.clear();
            host.onWindowsChanged(0);
        });
    }

    private final class Conn {
        final int index;
        final Socket socket;
        final LinkedBlockingQueue<String> out = new LinkedBlockingQueue<>();
        volatile boolean authed, dead;

        Conn(int index, Socket socket) {
            this.index = index;
            this.socket = socket;
        }

        void start() {
            Thread reader = new Thread(this::read, "py-gui-read-" + index);
            reader.setDaemon(true);
            reader.start();
            Thread writer = new Thread(this::write, "py-gui-write-" + index);
            writer.setDaemon(true);
            writer.start();
        }

        private void read() {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    JSONObject m;
                    try {
                        m = new JSONObject(line);
                    } catch (JSONException e) {
                        continue;
                    }
                    if (!authed) {
                        if ("hello".equals(m.optString("op")) && token.equals(m.optString("token"))) {
                            authed = true;
                            continue;
                        }
                        break; // 合言葉が違う接続は切る
                    }
                    inbox.add(new Object[]{this, m});
                    if (drainScheduled.compareAndSet(false, true)) ui.post(PyGui.this::drain);
                }
            } catch (IOException ignored) {
            }
            close();
        }

        private void write() {
            try {
                OutputStream os = socket.getOutputStream();
                while (!dead) {
                    String s = out.take();
                    if (dead) break;
                    StringBuilder sb = new StringBuilder(s).append('\n');
                    String more;
                    while ((more = out.poll()) != null) sb.append(more).append('\n');
                    os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }

        void send(JSONObject o) {
            if (!dead) out.add(o.toString());
        }

        void close() {
            dead = true;
            out.add("");
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void drain() {
        drainScheduled.set(false);
        Object[] item;
        int n = 0;
        while ((item = inbox.poll()) != null) {
            try {
                handle((Conn) item[0], (JSONObject) item[1]);
            } catch (RuntimeException e) {
                android.util.Log.w("PyGui", "GUI の指示を処理できません: " + item[1], e);
            }
            if (++n > 4000) { // 次のフレームで続ける
                if (drainScheduled.compareAndSet(false, true)) ui.post(this::drain);
                return;
            }
        }
    }

    private void emit(Node n, String ev, Object... kv) {
        try {
            JSONObject o = new JSONObject().put("ev", ev).put("id", n.id);
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
            n.conn.send(o);
        } catch (JSONException ignored) {
        }
    }

    private static void reply(Conn c, int req, Object value) {
        try {
            c.send(new JSONObject().put("reply", req).put("value", value == null ? JSONObject.NULL : value));
        } catch (JSONException ignored) {
        }
    }

    // ==================================================================
    // 部品
    // ==================================================================

    private final class Node {
        final Conn conn;
        final int id;
        final String type;
        View view;
        @Nullable PyLayout box;
        @Nullable Node parent;
        final List<Node> kids = new ArrayList<>();
        final JSONObject props = new JSONObject();
        @Nullable Win win;
        boolean visibleProp = true;
        boolean tabHidden;
        // 種類ごと
        @Nullable TextView text;
        @Nullable EditText edit;
        @Nullable TextInputLayout til;
        @Nullable Slider slider;
        @Nullable TextView sliderLabel;
        @Nullable ListView list;
        @Nullable PyListAdapter adapter;
        @Nullable TabLayout tabs;
        @Nullable LinearLayout tableHeader;
        boolean suppress;
        float fontSize = -1;

        Node(Conn conn, int id, String type) {
            this.conn = conn;
            this.id = id;
            this.type = type;
        }

        long key() {
            return ((long) conn.index << 32) | (id & 0xffffffffL);
        }
    }

    private final class Win {
        final Node node;
        final LinearLayout card;
        final TextView title;
        final ImageButton menuButton;
        final ImageButton closeButton;
        final TextView status;
        final PyLayout root;
        final NestedScrollView scroll;
        boolean toplevel, dialog, visible = true;
        @Nullable JSONArray menu;
        int prefW = -1, prefH = -1;

        Win(Node node) {
            this.node = node;
            card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout header = new LinearLayout(a);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(20), dp(4), dp(4), dp(4));
            title = new TextView(a);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setTextColor(cOnSurface);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            menuButton = iconButton(R.drawable.ic_jr_more, Lang.t("メニュー"));
            menuButton.setVisibility(View.GONE);
            header.addView(menuButton);
            closeButton = iconButton(R.drawable.ic_si_close, Lang.t("閉じる"));
            header.addView(closeButton);
            card.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

            root = new PyLayout(a);
            node.box = root;
            node.view = card;
            root.events = layoutEvents;
            root.conf = true;
            PyWindowHost hostView = new PyWindowHost(a, root);
            scroll = new NestedScrollView(a);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.addView(hostView, new FrameLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
            slp.setMargins(dp(8), 0, dp(8), dp(8));
            card.addView(scroll, slp);

            status = new TextView(a);
            status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            status.setTextColor(cOnSurfaceVariant);
            status.setPadding(dp(20), dp(2), dp(20), dp(10));
            status.setVisibility(View.GONE);
            card.addView(status);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cSurfaceContainerLow);
            bg.setCornerRadius(dp(28));
            card.setBackground(bg);
            card.setClipToOutline(true);

            closeButton.setOnClickListener(v -> emit(node, "close"));
            menuButton.setOnClickListener(v -> showWindowMenu(this, v));
        }
    }

    private ImageButton iconButton(int icon, String desc) {
        ImageButton b = new ImageButton(a);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(cOnSurfaceVariant));
        TypedValue tv = new TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setContentDescription(desc);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return b;
    }

    private final PyLayout.Events layoutEvents = new PyLayout.Events() {
        @Override
        public void mouse(@NonNull PyLayout v, @NonNull String type, float x, float y) {
            Node n = (Node) v.getTag(R.id.py_node);
            if (n == null) return;
            sendMouse(n, type, x, y);
        }

        @Override
        public void configure(@NonNull PyLayout v, int w, int h, int x, int y) {
            Node n = (Node) v.getTag(R.id.py_node);
            if (n != null) emit(n, "configure", "w", w, "h", h, "x", x, "y", y);
        }
    };

    private void sendMouse(Node n, String type, float x, float y) {
        if ("click".equals(type)) {
            emit(n, "mouse", "type", "release", "x", x, "y", y, "btn", 1, "click", true);
        } else {
            emit(n, "mouse", "type", type, "x", x, "y", y, "btn", 1, "pressed", true);
        }
    }

    // ==================================================================
    // 指示の処理
    // ==================================================================

    @Nullable
    private Node node(Conn c, JSONObject m, String key) {
        if (!m.has(key) || m.isNull(key)) return null;
        return nodes.get(((long) c.index << 32) | (m.optInt(key) & 0xffffffffL));
    }

    private void handle(Conn c, JSONObject m) {
        String op = m.optString("op");
        switch (op) {
            case "new":
                create(c, m);
                break;
            case "set": {
                Node n = node(c, m, "id");
                JSONObject p = m.optJSONObject("props");
                if (n != null && p != null) applyProps(n, p);
                break;
            }
            case "layout": {
                Node n = node(c, m, "id");
                JSONObject o = m.optJSONObject("opts");
                if (n != null && o != null && n.win == null) {
                    PyLayout.LP lp = PyLayout.lp(n.view);
                    lp.setOpts(o, d);
                    n.view.setLayoutParams(lp);
                    updateVisibility(n);
                }
                break;
            }
            case "order": {
                Node n = node(c, m, "id");
                JSONArray ids = m.optJSONArray("ids");
                if (n != null && n.box != null && ids != null) {
                    List<View> views = new ArrayList<>();
                    for (int i = 0; i < ids.length(); i++) {
                        Node k = nodes.get(((long) c.index << 32) | (ids.optInt(i) & 0xffffffffL));
                        if (k != null && k.view.getParent() == n.box) views.add(k.view);
                    }
                    n.box.setOrder(views);
                    if ("notebook".equals(n.type)) updateTabs(n);
                }
                break;
            }
            case "reparent": {
                Node n = node(c, m, "id");
                Node p = node(c, m, "parent");
                if (n != null && p != null && p.box != null && n.win == null) {
                    ViewParent old = n.view.getParent();
                    if (old != p.box) {
                        if (old instanceof ViewGroup) ((ViewGroup) old).removeView(n.view);
                        p.box.addView(n.view);
                        if (n.parent != null) n.parent.kids.remove(n);
                        n.parent = p;
                        p.kids.add(n);
                    }
                }
                break;
            }
            case "destroy": {
                Node n = node(c, m, "id");
                if (n != null) destroy(n);
                break;
            }
            case "raise": {
                Node n = node(c, m, "id");
                if (n != null && n.win != null) {
                    windows.remove(n.win);
                    windows.add(n.win);
                    refreshWindows();
                } else if (n != null) {
                    n.view.bringToFront();
                }
                break;
            }
            case "focus": {
                Node n = node(c, m, "id");
                if (n != null && n.edit != null) {
                    n.edit.requestFocus();
                    InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(n.edit, 0);
                }
                break;
            }
            case "dialog":
                dialog(c, m);
                break;
            case "popup":
                popup(c, m);
                break;
            case "clipboard": {
                ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Python", m.optString("text")));
                break;
            }
            case "clipboard_get": {
                ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                CharSequence t = null;
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                        && cm.getPrimaryClip().getItemCount() > 0) {
                    t = cm.getPrimaryClip().getItemAt(0).coerceToText(a);
                }
                reply(c, m.optInt("req"), t != null ? t.toString() : null);
                break;
            }
            case "toast":
                Toast.makeText(a, m.optString("text"), Toast.LENGTH_SHORT).show();
                break;
            case "bell":
                try {
                    if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
                } catch (RuntimeException ignored) {
                }
                break;
            case "open_url": {
                String url = m.optString("url");
                if (!url.isEmpty()) {
                    try {
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url), a, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) {
                    }
                }
                break;
            }
            default:
                // 知らない指示は無視する(互換モジュールが新しい時など)
        }
    }

    // ------------------------------------------------------------------
    // 作る
    // ------------------------------------------------------------------

    private void create(Conn c, JSONObject m) {
        int id = m.optInt("id");
        String type = m.optString("type", "frame");
        JSONObject props = m.optJSONObject("props");
        if (props == null) props = new JSONObject();
        Node n = new Node(c, id, type);
        Node parent = node(c, m, "parent");
        if ("window".equals(type)) {
            Win w = new Win(n);
            n.win = w;
            w.root.setTag(R.id.py_node, n);
            nodes.put(n.key(), n);
            windows.add(w);
            w.toplevel = props.optBoolean("toplevel");
            w.dialog = props.optBoolean("dialog");
            if (w.toplevel) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
                overlay.addView(w.card, lp);
                w.card.setElevation(dp(6));
            } else {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
                area.addView(w.card, area.indexOfChild(overlay), lp);
            }
            applyProps(n, props);
            playWindowIn(w);
            refreshWindows();
            return;
        }
        n.view = makeView(n, props);
        n.view.setTag(R.id.py_node, n);
        if (n.box != null) n.box.setTag(R.id.py_node, n);
        PyLayout.LP lp = new PyLayout.LP();
        n.view.setLayoutParams(lp);
        nodes.put(n.key(), n);
        if (parent != null && parent.box != null) {
            n.parent = parent;
            parent.kids.add(n);
            parent.box.addView(n.view);
        }
        applyProps(n, props);
        updateVisibility(n);
    }

    @SuppressLint("ClickableViewAccessibility")
    private View makeView(Node n, JSONObject props) {
        switch (n.type) {
            case "frame":
            case "canvas":
            case "labelframe": {
                PyLayout box = new PyLayout(a);
                box.events = layoutEvents;
                n.box = box;
                if ("labelframe".equals(n.type)) {
                    box.title = "";
                    box.titleColor = cOnSurfaceVariant;
                    box.borderColor = cOutlineVariant;
                }
                String scroll = props.optString("scroll", "");
                if ("v".equals(scroll)) {
                    NestedScrollView sv = new NestedScrollView(a);
                    sv.setFillViewport(true);
                    sv.addView(box, new FrameLayout.LayoutParams(-1, -2));
                    return sv;
                }
                if ("h".equals(scroll)) {
                    HorizontalScrollView sv = new HorizontalScrollView(a);
                    sv.setFillViewport(true);
                    sv.addView(box, new FrameLayout.LayoutParams(-2, -1));
                    return sv;
                }
                return box;
            }
            case "label": {
                TextView t = new TextView(a);
                t.setTextColor(cOnSurface);
                t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                t.setIncludeFontPadding(true);
                n.text = t;
                return t;
            }
            case "button": {
                MaterialButton b = new MaterialButton(a);
                b.setAllCaps(false);
                b.setMinWidth(0);
                b.setMinimumWidth(0);
                b.setMinHeight(dp(40));
                b.setMinimumHeight(dp(40));
                b.setInsetTop(dp(2));
                b.setInsetBottom(dp(2));
                b.setPadding(dp(14), 0, dp(14), 0);
                b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                styleButton(b, props.optString("style", "tonal"));
                b.setOnClickListener(v -> emit(n, "command"));
                b.setOnLongClickListener(v -> {
                    emit(n, "long");
                    return false;
                });
                n.text = b;
                return b;
            }
            case "entry":
            case "text":
                return makeEdit(n, props);
            case "check":
            case "switch": {
                CompoundButton cb = "switch".equals(n.type) || props.optBoolean("switch")
                        ? new MaterialSwitch(a) : new MaterialCheckBox(a);
                cb.setTextColor(cOnSurface);
                cb.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                cb.setOnCheckedChangeListener((v, checked) -> {
                    if (!n.suppress) emit(n, "value", "value", checked);
                });
                n.text = cb;
                return cb;
            }
            case "radio": {
                MaterialRadioButton r = new MaterialRadioButton(a);
                r.setTextColor(cOnSurface);
                r.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                r.setOnClickListener(v -> {
                    // 選ばれたことだけを伝え、他の丸の状態は Python から届く
                    r.setChecked(true);
                    emit(n, "value", "value", true);
                });
                n.text = r;
                return r;
            }
            case "scale": {
                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView label = new TextView(a);
                label.setTextColor(cOnSurfaceVariant);
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                label.setVisibility(View.GONE);
                col.addView(label);
                Slider s = new Slider(a);
                s.setMinimumWidth(dp(160));
                s.addOnChangeListener((sl, value, fromUser) -> {
                    if (fromUser && !n.suppress) emit(n, "value", "value", (double) value);
                    updateSliderLabel(n);
                });
                s.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                    @Override
                    public void onStartTrackingTouch(@NonNull Slider slider) {
                        if (slider.getParent() != null) slider.getParent().requestDisallowInterceptTouchEvent(true);
                    }

                    @Override
                    public void onStopTrackingTouch(@NonNull Slider slider) {
                        emit(n, "value", "value", (double) slider.getValue(), "end", true);
                    }
                });
                col.addView(s, new LinearLayout.LayoutParams(-1, -2));
                n.slider = s;
                n.sliderLabel = label;
                return col;
            }
            case "progress": {
                LinearProgressIndicator p = new LinearProgressIndicator(a);
                p.setTrackCornerRadius(dp(3));
                p.setTrackThickness(dp(6));
                p.setMinimumWidth(dp(160));
                p.setPadding(0, dp(8), 0, dp(8));
                return p;
            }
            case "ring": {
                CircularProgressIndicator p = new CircularProgressIndicator(a);
                p.setIndicatorSize(dp(36));
                return p;
            }
            case "listbox":
            case "table":
                return makeList(n);
            case "combo":
                return makeCombo(n, props);
            case "spin":
                return makeSpin(n, props);
            case "separator": {
                View v = new View(a);
                v.setBackgroundColor(cOutlineVariant);
                return v;
            }
            case "notebook":
                return makeNotebook(n, props);
            case "space":
            default:
                return new View(a);
        }
    }

    private void styleButton(MaterialButton b, String style) {
        int bg, fg, stroke = 0;
        float elevation = 0;
        float radius = dp(20);
        switch (style) {
            case "filled":
                bg = cPrimary;
                fg = cOnPrimary;
                break;
            case "outlined":
                bg = Color.TRANSPARENT;
                fg = cPrimary;
                stroke = Math.max(1, dp(1));
                break;
            case "text":
            case "icon":
                bg = Color.TRANSPARENT;
                fg = cPrimary;
                break;
            case "elevated":
                bg = cSurfaceContainerLow;
                fg = cPrimary;
                elevation = dp(2);
                break;
            case "fab":
                bg = cPrimaryContainer;
                fg = cOnPrimaryContainer;
                radius = dp(16);
                b.setMinHeight(dp(56));
                b.setMinimumHeight(dp(56));
                b.setMinWidth(dp(56));
                b.setMinimumWidth(dp(56));
                b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                elevation = dp(3);
                break;
            case "tonal":
            default:
                bg = cSecondaryContainer;
                fg = cOnSecondaryContainer;
                break;
        }
        if ("icon".equals(style)) {
            b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            b.setPadding(dp(8), 0, dp(8), 0);
            b.setMinWidth(dp(44));
            b.setMinimumWidth(dp(44));
            b.setTextColor(cOnSurfaceVariant);
            fg = cOnSurfaceVariant;
        }
        b.setBackgroundTintList(ColorStateList.valueOf(bg));
        b.setTextColor(fg);
        b.setStrokeWidth(stroke);
        b.setStrokeColor(ColorStateList.valueOf(cOutline));
        b.setElevation(elevation);
        b.setCornerRadius(Math.round(radius));
        b.setRippleColor(ColorStateList.valueOf((fg & 0x00ffffff) | 0x33000000));
    }

    private View makeEdit(Node n, JSONObject props) {
        boolean multi = "text".equals(n.type);
        EditText e;
        View outer;
        if (props.optBoolean("outlined")) {
            TextInputLayout til = new TextInputLayout(a, null,
                    com.google.android.material.R.attr.textInputOutlinedStyle);
            til.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
            TextInputEditText te = new TextInputEditText(til.getContext());
            til.addView(te, new LinearLayout.LayoutParams(-1, -2));
            n.til = til;
            e = te;
            outer = til;
        } else {
            e = new EditText(a);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cSurfaceContainerHighest);
            bg.setCornerRadius(dp(12));
            e.setBackground(bg);
            e.setPadding(dp(14), dp(10), dp(14), dp(10));
            outer = e;
        }
        e.setTextColor(cOnSurface);
        e.setHintTextColor(cOnSurfaceVariant);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setSingleLine(false);
            e.setHorizontallyScrolling(false);
            e.setVerticalScrollBarEnabled(true);
            e.setOnTouchListener((v, ev) -> {
                // 外側のスクロールより先に、文字欄の中をスクロールする
                if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                    v.getParent().requestDisallowInterceptTouchEvent(ev.getActionMasked() != MotionEvent.ACTION_UP);
                }
                return false;
            });
        } else {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setImeOptions(EditorInfo.IME_ACTION_DONE);
            e.setOnEditorActionListener((v, action, ev) -> {
                if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO
                        || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                    emit(n, "submit");
                    return true;
                }
                return false;
            });
        }
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!n.suppress) {
                    String v = s.toString();
                    try {
                        n.props.put("value", v);
                    } catch (JSONException ignored) {
                    }
                    emit(n, "value", "value", v);
                }
            }
        });
        e.setOnFocusChangeListener((v, has) -> emit(n, "focus", "in", has));
        n.edit = e;
        n.text = e;
        return outer;
    }

    private View makeList(Node n) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cSurfaceContainer);
        bg.setCornerRadius(dp(12));
        col.setBackground(bg);
        col.setClipToOutline(true);
        if ("table".equals(n.type)) {
            LinearLayout header = new LinearLayout(a);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setBackgroundColor(cSurfaceContainerHigh);
            header.setVisibility(View.GONE);
            col.addView(header, new LinearLayout.LayoutParams(-1, -2));
            n.tableHeader = header;
        }
        ListView lv = new ListView(a);
        lv.setDivider(null);
        lv.setNestedScrollingEnabled(true);
        lv.setSelector(android.R.color.transparent);
        PyListAdapter ad = new PyListAdapter(n);
        lv.setAdapter(ad);
        lv.setOnItemClickListener((parent, view, position, id) -> ad.onClick(position));
        lv.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        n.list = lv;
        n.adapter = ad;
        return col;
    }

    /** Listbox / Treeview / ListCtrl の行。 */
    private final class PyListAdapter extends BaseAdapter {
        final Node n;
        final List<List<String>> rows = new ArrayList<>();
        final List<Integer> selected = new ArrayList<>();
        float[] widths = new float[0];
        String mode = "browse";
        int selectBg;
        long lastClick;
        int lastPos = -1;

        PyListAdapter(Node n) {
            this.n = n;
            selectBg = cSecondaryContainer;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = convertView instanceof LinearLayout ? (LinearLayout) convertView : null;
            List<String> cells = rows.get(position);
            if (row == null || row.getChildCount() != Math.max(1, cells.size())) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setMinimumHeight(dp(36));
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), 0, dp(12), 0);
                for (int i = 0; i < Math.max(1, cells.size()); i++) {
                    TextView t = new TextView(a);
                    t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setPadding(0, dp(8), dp(8), dp(8));
                    float w = i < widths.length ? widths[i] : 1;
                    row.addView(t, new LinearLayout.LayoutParams(0, -2, Math.max(0.1f, w)));
                }
            }
            boolean sel = selected.contains(position);
            int fg = sel ? cOnSecondaryContainer : cOnSurface;
            for (int i = 0; i < row.getChildCount(); i++) {
                TextView t = (TextView) row.getChildAt(i);
                t.setText(i < cells.size() ? cells.get(i) : "");
                t.setTextColor(fg);
                if (n.fontSize > 0) t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, n.fontSize);
            }
            row.setBackgroundColor(sel ? selectBg : Color.TRANSPARENT);
            return row;
        }

        void onClick(int position) {
            long now = System.currentTimeMillis();
            boolean dbl = position == lastPos && now - lastClick < 400;
            lastClick = now;
            lastPos = position;
            if (mode.equals("multiple") || mode.equals("extended")) {
                if (selected.contains(position) && !dbl) selected.remove((Integer) position);
                else if (!selected.contains(position)) selected.add(position);
            } else {
                selected.clear();
                selected.add(position);
            }
            notifyDataSetChanged();
            JSONArray sel = new JSONArray();
            for (int i : selected) sel.put(i);
            emit(n, "value", "value", sel, "double", dbl, "toggle", true);
        }
    }

    private View makeCombo(Node n, JSONObject props) {
        TextInputLayout til = new TextInputLayout(a, null,
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        til.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
        MaterialAutoCompleteTextView ac = new MaterialAutoCompleteTextView(til.getContext());
        ac.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        ac.setThreshold(Integer.MAX_VALUE);
        til.addView(ac, new LinearLayout.LayoutParams(-1, -2));
        til.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        ac.setPadding(dp(14), dp(12), dp(36), dp(12));
        ac.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            String v = item != null ? item.toString() : "";
            emit(n, "value", "value", v, "selected", true);
        });
        ac.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!n.suppress && ac.getKeyListener() != null && ac.hasFocus()) {
                    emit(n, "value", "value", s.toString());
                }
            }
        });
        ac.setOnEditorActionListener((v, action, ev) -> {
            emit(n, "submit");
            return false;
        });
        ac.setOnClickListener(v -> ac.showDropDown());
        n.til = til;
        n.edit = ac;
        n.text = ac;
        return til;
    }

    private View makeSpin(Node n, JSONObject props) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cSurfaceContainerHighest);
        bg.setCornerRadius(dp(12));
        row.setBackground(bg);
        MaterialButton minus = new MaterialButton(a);
        MaterialButton plus = new MaterialButton(a);
        for (MaterialButton b : new MaterialButton[]{minus, plus}) {
            styleButton(b, "icon");
            b.setInsetTop(0);
            b.setInsetBottom(0);
        }
        minus.setText("−");
        plus.setText("+");
        minus.setOnClickListener(v -> emit(n, "command", "dir", "down"));
        plus.setOnClickListener(v -> emit(n, "command", "dir", "up"));
        EditText e = new EditText(a);
        e.setBackground(null);
        e.setSingleLine(true);
        e.setGravity(Gravity.CENTER);
        e.setTextColor(cOnSurface);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!n.suppress) emit(n, "value", "value", s.toString());
            }
        });
        e.setOnEditorActionListener((v, action, ev) -> {
            emit(n, "submit");
            return false;
        });
        row.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        n.edit = e;
        n.text = e;
        return row;
    }

    private View makeNotebook(Node n, JSONObject props) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TabLayout tabs = new TabLayout(a);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabs.setTabGravity(TabLayout.GRAVITY_START);
        tabs.setBackgroundColor(Color.TRANSPARENT);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (n.suppress || n.box == null) return;
                n.box.selectedTab = tab.getPosition();
                updateTabs(n);
                emit(n, "tab", "index", tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        PyLayout box = new PyLayout(a);
        box.events = layoutEvents;
        n.box = box;
        n.tabs = tabs;
        boolean bottom = props.optBoolean("bar");
        if (!bottom) col.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        col.addView(box, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (bottom) {
            tabs.setTabMode(TabLayout.MODE_FIXED);
            tabs.setTabGravity(TabLayout.GRAVITY_FILL);
            tabs.setBackgroundColor(cSurfaceContainer);
            col.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        }
        return col;
    }

    /** Notebook の中身: 選んでいるタブの子だけを出す。 */
    private void updateTabs(Node n) {
        if (n.box == null) return;
        List<View> order = new ArrayList<>(n.box.order);
        for (Node k : n.kids) {
            if (!order.contains(k.view)) order.add(k.view);
        }
        int i = 0;
        for (View v : order) {
            Node k = (Node) v.getTag(R.id.py_node);
            if (k == null || !"tab".equals(PyLayout.lp(v).m)) continue;
            k.tabHidden = i != n.box.selectedTab;
            updateVisibility(k);
            i++;
        }
        n.box.requestLayout();
    }

    // ------------------------------------------------------------------
    // 表示・非表示・消す
    // ------------------------------------------------------------------

    private void updateVisibility(Node n) {
        if (n.win != null) return;
        String m = PyLayout.lp(n.view).m;
        boolean show = n.visibleProp && !n.tabHidden && !m.equals("none");
        int vis = show ? View.VISIBLE : View.GONE;
        if (n.view.getVisibility() != vis) n.view.setVisibility(vis);
        if (n.view.getParent() instanceof View) ((View) n.view.getParent()).requestLayout();
    }

    private void destroy(Node n) {
        for (Node k : new ArrayList<>(n.kids)) destroy(k);
        nodes.remove(n.key());
        if (n.parent != null) n.parent.kids.remove(n);
        if (n.win != null) {
            removeWindow(n.win);
            return;
        }
        ViewParent p = n.view.getParent();
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(n.view);
    }

    private void removeWindow(Win w) {
        windows.remove(w);
        ViewParent p = w.card.getParent();
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(w.card);
        refreshWindows();
    }

    private void playWindowIn(Win w) {
        w.card.setAlpha(0f);
        w.card.setScaleX(0.94f);
        w.card.setScaleY(0.94f);
        w.card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
    }

    /** 窓の重なりを整える: いちばん新しい普通の窓をパネルいっぱいに、Toplevel はその上に重ねる。 */
    private void refreshWindows() {
        Win main = null;
        for (Win w : windows) if (!w.toplevel && w.visible) main = w;
        int shown = 0;
        boolean anyTop = false;
        for (Win w : windows) {
            boolean show;
            if (w.toplevel) {
                show = w.visible;
                if (show) {
                    anyTop = true;
                    w.card.bringToFront();
                }
            } else {
                show = w == main;
            }
            w.card.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) shown++;
            if (w.toplevel) {
                int maxW = Math.round(area.getWidth() * 0.92f), maxH = Math.round(area.getHeight() * 0.9f);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) w.card.getLayoutParams();
                int pw = w.prefW > 0 ? dp(w.prefW + 16) : -2;
                lp.width = maxW > 0 ? (pw > 0 ? Math.min(pw, maxW) : (w.dialog ? Math.min(dp(380), maxW) : maxW)) : -2;
                lp.height = -2;
                w.card.setLayoutParams(lp);
                if (maxH > 0) w.scroll.getLayoutParams().height = -2;
                w.card.setMinimumHeight(0);
                w.card.post(() -> {
                    int lim = Math.round(area.getHeight() * 0.9f);
                    if (lim > 0 && w.card.getHeight() > lim) {
                        FrameLayout.LayoutParams l2 = (FrameLayout.LayoutParams) w.card.getLayoutParams();
                        l2.height = lim;
                        w.card.setLayoutParams(l2);
                    }
                });
            }
        }
        overlay.setVisibility(anyTop ? View.VISIBLE : View.GONE);
        overlay.bringToFront();
        scrim.setOnClickListener(v -> {
            Win top = topWindow();
            if (top != null && top.toplevel && top.dialog) emit(top.node, "close");
        });
        host.onWindowsChanged(shown);
    }

    /** キーを送る先(いちばん上に見えている窓)。 */
    @Nullable
    private Win topWindow() {
        Win top = null;
        for (Win w : windows) if (w.visible && w.toplevel) top = w;
        if (top != null) return top;
        for (Win w : windows) if (w.visible && !w.toplevel) top = w;
        return top;
    }

    boolean hasWindow() {
        return topWindow() != null;
    }

    @Nullable
    String topTitle() {
        Win w = topWindow();
        return w != null ? w.title.getText().toString() : null;
    }

    // ------------------------------------------------------------------
    // キー
    // ------------------------------------------------------------------

    /** 物理キーボード・画面のキーのボタンからのキー。送ったら true。 */
    boolean sendKey(boolean down, @NonNull String keysym, @NonNull String ch, int code, @NonNull List<String> mods) {
        Win w = topWindow();
        if (w == null) return false;
        JSONArray ms = new JSONArray();
        for (String m : mods) ms.put(m);
        emit(w.node, "key", "type", down ? "press" : "release", "keysym", keysym, "char", ch, "code", code, "mods", ms);
        return true;
    }

    static String keysym(int keyCode, int unicode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return "Return";
            case KeyEvent.KEYCODE_DEL:
                return "BackSpace";
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return "Delete";
            case KeyEvent.KEYCODE_TAB:
                return "Tab";
            case KeyEvent.KEYCODE_ESCAPE:
                return "Escape";
            case KeyEvent.KEYCODE_SPACE:
                return "space";
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "Right";
            case KeyEvent.KEYCODE_DPAD_UP:
                return "Up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "Down";
            case KeyEvent.KEYCODE_MOVE_HOME:
                return "Home";
            case KeyEvent.KEYCODE_MOVE_END:
                return "End";
            case KeyEvent.KEYCODE_PAGE_UP:
                return "Prior";
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return "Next";
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return "Shift_L";
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return "Control_L";
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return "Alt_L";
            case KeyEvent.KEYCODE_COMMA:
                return "comma";
            case KeyEvent.KEYCODE_PERIOD:
                return "period";
            case KeyEvent.KEYCODE_MINUS:
                return "minus";
            case KeyEvent.KEYCODE_EQUALS:
                return "equal";
            case KeyEvent.KEYCODE_SLASH:
                return "slash";
            case KeyEvent.KEYCODE_SEMICOLON:
                return "semicolon";
            default:
                if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
                    return "F" + (keyCode - KeyEvent.KEYCODE_F1 + 1);
                }
                if (unicode > 0) return String.valueOf((char) unicode);
                return "??";
        }
    }

    // ------------------------------------------------------------------
    // props の反映
    // ------------------------------------------------------------------

    private void applyProps(Node n, JSONObject p) {
        Iterator<String> it = p.keys();
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next());
        for (String k : keys) {
            try {
                n.props.put(k, p.opt(k));
            } catch (JSONException ignored) {
            }
        }
        if (n.win != null) {
            applyWindowProps(n.win, p, keys);
            return;
        }
        n.suppress = true;
        try {
            for (String k : keys) applyProp(n, k, p.isNull(k) ? null : p.opt(k));
        } finally {
            n.suppress = false;
        }
        if (n.view.getParent() instanceof View) ((View) n.view.getParent()).requestLayout();
        n.view.requestLayout();
    }

    private void applyWindowProps(Win w, JSONObject p, List<String> keys) {
        Node n = w.node;
        for (String k : keys) {
            Object v = p.isNull(k) ? null : p.opt(k);
            switch (k) {
                case "title":
                    w.title.setText(v != null ? v.toString() : "");
                    break;
                case "menu":
                    w.menu = v instanceof JSONArray ? (JSONArray) v : null;
                    w.menuButton.setVisibility(w.menu != null && w.menu.length() > 0 ? View.VISIBLE : View.GONE);
                    break;
                case "status": {
                    String s = v != null ? v.toString() : "";
                    w.status.setText(s);
                    w.status.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
                    break;
                }
                case "visible":
                    w.visible = v == null || Boolean.TRUE.equals(v);
                    refreshWindows();
                    break;
                case "bg": {
                    Integer c = color(v);
                    w.root.setBackgroundColor(c != null ? c : Color.TRANSPARENT);
                    break;
                }
                case "width":
                    w.prefW = v instanceof Number ? ((Number) v).intValue() : -1;
                    if (w.toplevel) refreshWindows();
                    break;
                case "height":
                    w.prefH = v instanceof Number ? ((Number) v).intValue() : -1;
                    break;
                case "modal":
                    w.closeButton.setVisibility(Boolean.TRUE.equals(v) ? View.GONE : View.VISIBLE);
                    break;
                default:
                    // 中身の並べ方(flet の Page は Column と同じ)
                    applyLayoutProp(n, w.root, k, v);
            }
        }
        w.root.requestLayout();
    }

    @Nullable
    private Integer color(@Nullable Object v) {
        if (!(v instanceof String)) return null;
        String s = (String) v;
        if (s.startsWith("@")) {
            switch (s) {
                case "@surfaceContainer":
                    return cSurfaceContainer;
                case "@surfaceContainerHigh":
                    return cSurfaceContainerHigh;
                case "@primary":
                    return cPrimary;
                default:
                    return cSurfaceContainerLow;
            }
        }
        return PyDraw.color(s);
    }

    private static float num(@Nullable Object v, float def) {
        return v instanceof Number ? ((Number) v).floatValue() : def;
    }

    /** PyLayout(frame / window の中身)の並べ方の設定。処理したら true。 */
    private boolean applyLayoutProp(Node n, PyLayout box, String k, @Nullable Object v) {
        switch (k) {
            case "orient":
                box.orient = v != null ? v.toString() : "v";
                return true;
            case "spacing":
                box.spacing = num(v, 0);
                return true;
            case "main":
                box.main = v != null ? v.toString() : "start";
                return true;
            case "cross":
                box.cross = v != null ? v.toString() : "start";
                return true;
            case "propagate":
                box.propagate = !Boolean.FALSE.equals(v);
                return true;
            case "rows":
            case "cols": {
                if (!(v instanceof JSONObject)) return true;
                Map<Integer, float[]> target = k.equals("rows") ? box.rows : box.cols;
                target.clear();
                JSONObject o = (JSONObject) v;
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    JSONArray arr = o.optJSONArray(key);
                    if (arr == null) continue;
                    try {
                        target.put(Integer.parseInt(key), new float[]{(float) arr.optDouble(0), (float) arr.optDouble(1),
                                (float) arr.optDouble(2)});
                    } catch (NumberFormatException ignored) {
                    }
                }
                return true;
            }
            case "padding": {
                if (v instanceof JSONArray) {
                    JSONArray arr = (JSONArray) v;
                    for (int i = 0; i < 4; i++) box.pad[i] = dp((float) arr.optDouble(i, 0));
                } else {
                    Arrays.fill(box.pad, 0);
                }
                return true;
            }
            case "draw":
                box.draw = v instanceof JSONArray ? PyDraw.parse((JSONArray) v) : null;
                box.invalidate();
                return true;
            case "mouse":
                box.mouse = Boolean.TRUE.equals(v);
                return true;
            case "conf":
                box.conf = Boolean.TRUE.equals(v);
                if (box.conf) box.post(box::resendConfigure);
                return true;
            case "wrap":
                if (v instanceof Boolean) {
                    box.wrap = (Boolean) v;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void applyProp(Node n, String k, @Nullable Object v) {
        if (n.box != null && applyLayoutProp(n, n.box, k, v)) return;
        PyLayout.LP lp = PyLayout.lp(n.view);
        switch (k) {
            case "visible":
                n.visibleProp = v == null || Boolean.TRUE.equals(v);
                updateVisibility(n);
                return;
            case "state": {
                String s = v != null ? v.toString() : "normal";
                boolean enabled = !s.equals("disabled");
                n.view.setEnabled(enabled);
                if (n.edit != null) {
                    n.edit.setEnabled(enabled);
                    boolean ro = s.equals("readonly");
                    n.edit.setFocusable(!ro && enabled);
                    n.edit.setFocusableInTouchMode(!ro && enabled);
                    n.edit.setLongClickable(true);
                    n.edit.setTextIsSelectable(ro);
                } else if (n.text != null) {
                    n.text.setEnabled(enabled);
                }
                if (n.slider != null) n.slider.setEnabled(enabled);
                n.view.setAlpha(enabled ? 1f : 0.5f);
                return;
            }
            case "width":
                lp.pw = v instanceof Number ? dp(((Number) v).floatValue()) : -1;
                if (n.box != null) n.box.explicitW = lp.pw;
                return;
            case "height":
                lp.ph = v instanceof Number ? dp(((Number) v).floatValue()) : -1;
                if (n.box != null) n.box.explicitH = lp.ph;
                return;
            case "minw":
                lp.pminw = v instanceof Number ? dp(((Number) v).floatValue()) : -1;
                return;
            case "minh":
                lp.pminh = v instanceof Number ? dp(((Number) v).floatValue()) : -1;
                return;
            case "margin": {
                Arrays.fill(lp.cm, 0);
                if (v instanceof JSONArray) {
                    JSONArray arr = (JSONArray) v;
                    for (int i = 0; i < 4; i++) lp.cm[i] = dp((float) arr.optDouble(i, 0));
                } else if (v instanceof Number && "separator".equals(n.type)) {
                    int m = dp(((Number) v).floatValue());
                    boolean vert = "vertical".equals(n.props.optString("orient"));
                    lp.cm[vert ? 0 : 1] = m;
                    lp.cm[vert ? 2 : 3] = m;
                }
                return;
            }
            case "wchars":
            case "hlines":
                sizeByChars(n);
                return;
            case "alpha":
                n.view.setAlpha(num(v, 1));
                return;
            case "tooltip":
                TooltipCompat.setTooltipText(n.view, v != null ? v.toString() : null);
                return;
            case "fg": {
                Integer c = color(v);
                if (c == null) return;
                if (n.text != null) n.text.setTextColor(c);
                if (n.view instanceof LinearProgressIndicator) ((LinearProgressIndicator) n.view).setIndicatorColor(c);
                if (n.view instanceof CircularProgressIndicator) ((CircularProgressIndicator) n.view).setIndicatorColor(c);
                if ("separator".equals(n.type)) n.view.setBackgroundColor(c);
                if (n.box != null && n.box.title != null) n.box.titleColor = c;
                if (n.view instanceof MaterialButton) ((MaterialButton) n.view).setIconTint(ColorStateList.valueOf(c));
                return;
            }
            case "bg":
            case "radius":
            case "border":
            case "elevation":
            case "clickable":
                applyBackground(n);
                return;
            case "font":
                applyFont(n, v instanceof JSONObject ? (JSONObject) v : null);
                return;
            case "padding": {
                if (!(v instanceof JSONArray)) return;
                JSONArray arr = (JSONArray) v;
                n.view.setPadding(dp((float) arr.optDouble(0) + 4), dp((float) arr.optDouble(1) + 2),
                        dp((float) arr.optDouble(2) + 4), dp((float) arr.optDouble(3) + 2));
                return;
            }
            case "mouse": {
                boolean on = Boolean.TRUE.equals(v);
                if (n.box == null) {
                    n.view.setOnTouchListener(on ? (view, ev) -> {
                        float x = ev.getX() / d, y = ev.getY() / d;
                        switch (ev.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                sendMouse(n, "press", x, y);
                                break;
                            case MotionEvent.ACTION_MOVE:
                                sendMouse(n, "motion", x, y);
                                break;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                sendMouse(n, "release", x, y);
                                break;
                            default:
                        }
                        // 部品(ボタンなど)の普段の動きはそのまま
                        if (n.text != null && !(n.view instanceof MaterialButton) && n.edit == null) {
                            return true;
                        }
                        return false;
                    } : null);
                }
                return;
            }
            case "see":
                if (n.list != null && v instanceof Number) n.list.smoothScrollToPosition(((Number) v).intValue());
                if (n.edit != null && v instanceof Number) {
                    int pos = Math.min(((Number) v).intValue(), n.edit.length());
                    n.edit.setSelection(pos);
                }
                return;
            default:
        }
        switch (n.type) {
            case "label":
            case "button":
            case "check":
            case "switch":
            case "radio":
                applyTextProp(n, k, v);
                return;
            case "entry":
            case "text":
            case "combo":
            case "spin":
                applyEditProp(n, k, v);
                return;
            case "scale":
                applySliderProp(n, k, v);
                return;
            case "progress":
            case "ring":
                applyProgressProp(n, k, v);
                return;
            case "listbox":
            case "table":
                applyListProp(n, k, v);
                return;
            case "notebook":
                applyNotebookProp(n, k, v);
                return;
            case "labelframe":
                if (k.equals("text") && n.box != null) {
                    n.box.title = v != null ? v.toString() : "";
                    n.box.invalidate();
                }
                return;
            case "separator":
                if (k.equals("orient") || k.equals("thickness")) sizeSeparator(n);
                return;
            default:
        }
    }

    private void applyBackground(Node n) {
        JSONObject p = n.props;
        Integer bg = color(p.opt("bg"));
        float radius = (float) p.optDouble("radius", 0);
        JSONObject border = p.optJSONObject("border");
        if (n.view instanceof MaterialButton) {
            MaterialButton b = (MaterialButton) n.view;
            if (bg != null) {
                b.setBackgroundTintList(ColorStateList.valueOf(bg));
                if (p.isNull("fg") || !p.has("fg")) {
                    b.setTextColor(luminance(bg) > 0.55 ? Color.BLACK : Color.WHITE);
                }
            }
            if (p.has("radius")) b.setCornerRadius(dp(radius));
            return;
        }
        if (n.edit != null && n.til == null && !"spin".equals(n.type)) {
            if (bg != null) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(bg);
                g.setCornerRadius(dp(12));
                n.edit.setBackground(g);
            }
            return;
        }
        if (n.til != null) {
            if (bg != null) {
                n.til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
                n.til.setBoxBackgroundColor(bg);
            }
            return;
        }
        View target = n.box != null ? n.box : n.view;
        if (bg == null && border == null && radius <= 0) {
            target.setBackground(null);
        } else {
            GradientDrawable g = new GradientDrawable();
            g.setColor(bg != null ? bg : Color.TRANSPARENT);
            if (radius > 0) g.setCornerRadius(Math.min(dp(radius), dp(2000)));
            if (border != null) {
                Integer bc = color(border.opt("color"));
                g.setStroke(Math.max(1, dp((float) border.optDouble("width", 1))), bc != null ? bc : cOutline);
            }
            target.setBackground(g);
            if (radius > 0) target.setClipToOutline(true);
        }
        float elev = (float) p.optDouble("elevation", 0);
        target.setElevation(dp(elev));
        if (n.box != null) {
            boolean clickable = p.optBoolean("clickable");
            if (clickable) {
                n.box.setOnClickListener(v -> emit(n, "command"));
                n.box.setOnLongClickListener(v -> {
                    emit(n, "long");
                    return true;
                });
                TypedValue tv = new TypedValue();
                a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
                n.box.setForeground(androidx.core.content.ContextCompat.getDrawable(a, tv.resourceId));
            } else {
                n.box.setOnClickListener(null);
                n.box.setClickable(false);
                n.box.setForeground(null);
            }
        }
    }

    private static double luminance(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
    }

    private void applyFont(Node n, @Nullable JSONObject f) {
        TextView t = n.text;
        if (f == null) return;
        float size = (float) f.optDouble("size", -1);
        if (size > 0) n.fontSize = size;
        if (t != null) {
            if (size > 0) t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
            t.setTypeface(PyDraw.typeface(f));
            int flags = t.getPaintFlags() & ~(Paint.UNDERLINE_TEXT_FLAG | Paint.STRIKE_THRU_TEXT_FLAG);
            if (f.optBoolean("underline")) flags |= Paint.UNDERLINE_TEXT_FLAG;
            if (f.optBoolean("strike")) flags |= Paint.STRIKE_THRU_TEXT_FLAG;
            t.setPaintFlags(flags);
        }
        if (n.adapter != null) n.adapter.notifyDataSetChanged();
        sizeByChars(n);
    }

    /** tk の width(文字数)・height(行数)から大きさを決める。 */
    private void sizeByChars(Node n) {
        PyLayout.LP lp = PyLayout.lp(n.view);
        int wchars = n.props.optInt("wchars", 0), hlines = n.props.optInt("hlines", 0);
        Paint paint = new Paint();
        float size = n.fontSize > 0 ? n.fontSize : (n.edit != null ? 15 : 14);
        paint.setTextSize(size * d);
        if (n.text != null) paint.setTypeface(n.text.getTypeface());
        float charW = paint.measureText("0");
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineH = (fm.descent - fm.ascent) * 1.12f;
        if (wchars > 0 && !n.props.has("width")) {
            int extra = n.view.getPaddingLeft() + n.view.getPaddingRight();
            if (n.edit != null) extra = dp(28);
            if ("spin".equals(n.type)) extra = dp(100);
            if (n.view instanceof MaterialButton) extra = dp(28);
            if (n.view instanceof CompoundButton) extra = dp(44);
            if ("listbox".equals(n.type)) extra = dp(24);
            lp.pw = Math.round(charW * wchars + extra);
        }
        if (hlines > 0 && !n.props.has("height")) {
            if ("listbox".equals(n.type) || "table".equals(n.type)) {
                lp.ph = dp(36) * hlines + (n.tableHeader != null && n.tableHeader.getVisibility() == View.VISIBLE ? dp(36) : 0);
            } else if (n.edit != null) {
                lp.ph = Math.round(lineH * hlines + dp(20));
                n.edit.setMinLines(1);
            } else if (n.text != null) {
                lp.ph = Math.round(lineH * hlines + n.view.getPaddingTop() + n.view.getPaddingBottom());
            }
        }
    }

    private void applyTextProp(Node n, String k, @Nullable Object v) {
        TextView t = n.text;
        if (t == null) return;
        switch (k) {
            case "text": {
                String s = v != null ? v.toString() : "";
                if (n.props.optBoolean("dropdown")) s = s + "  ▾";
                t.setText(s);
                return;
            }
            case "value":
                if (t instanceof CompoundButton) ((CompoundButton) t).setChecked(Boolean.TRUE.equals(v));
                return;
            case "style":
                if (t instanceof MaterialButton) {
                    styleButton((MaterialButton) t, v != null ? v.toString() : "tonal");
                    applyBackground(n);
                    Integer fg = color(n.props.opt("fg"));
                    if (fg != null) t.setTextColor(fg);
                }
                return;
            case "anchor":
            case "justify":
                t.setGravity(gravity(n.props.optString("anchor", "center"), n.props.optString("justify", "")));
                return;
            case "wrap":
                if (v instanceof Number) t.setMaxWidth(dp(((Number) v).floatValue()));
                return;
            case "maxlines":
                if (v instanceof Number) {
                    t.setMaxLines(((Number) v).intValue());
                    t.setEllipsize(TextUtils.TruncateAt.END);
                }
                return;
            case "selectable":
                t.setTextIsSelectable(Boolean.TRUE.equals(v));
                return;
            case "link":
                t.setTextColor(cPrimary);
                t.setPaintFlags(t.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                return;
            case "image":
            case "compound":
            case "fit":
                applyImage(n);
                return;
            default:
        }
    }

    private static int gravity(String anchor, String justify) {
        int g;
        switch (anchor) {
            case "w":
                g = Gravity.START | Gravity.CENTER_VERTICAL;
                break;
            case "e":
                g = Gravity.END | Gravity.CENTER_VERTICAL;
                break;
            case "n":
                g = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                break;
            case "s":
                g = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                break;
            case "nw":
                g = Gravity.TOP | Gravity.START;
                break;
            case "ne":
                g = Gravity.TOP | Gravity.END;
                break;
            case "sw":
                g = Gravity.BOTTOM | Gravity.START;
                break;
            case "se":
                g = Gravity.BOTTOM | Gravity.END;
                break;
            default:
                g = Gravity.CENTER;
        }
        if ("left".equals(justify)) g = (g & Gravity.VERTICAL_GRAVITY_MASK) | Gravity.START;
        else if ("right".equals(justify)) g = (g & Gravity.VERTICAL_GRAVITY_MASK) | Gravity.END;
        else if ("center".equals(justify)) g = (g & Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_HORIZONTAL;
        return g;
    }

    private void applyImage(Node n) {
        TextView t = n.text;
        if (t == null) return;
        JSONObject ref = n.props.optJSONObject("image");
        if (ref == null) {
            t.setCompoundDrawables(null, null, null, null);
            return;
        }
        Bitmap bmp = PyImages.get(a, ref, () -> applyImage(n));
        if (bmp == null) return;
        Drawable dr = new BitmapDrawable(a.getResources(), bmp);
        int w = bmp.getWidth(), h = bmp.getHeight();
        PyLayout.LP lp = PyLayout.lp(n.view);
        String compound = n.props.optString("compound", "none");
        if ("none".equals(compound) && (lp.pw > 0 || lp.ph > 0)) {
            // 大きさの決まった画像(flet の Image など): 収まるように縮める
            float sx = lp.pw > 0 ? lp.pw / (float) w : Float.MAX_VALUE;
            float sy = lp.ph > 0 ? lp.ph / (float) h : Float.MAX_VALUE;
            float s = Math.min(sx, sy);
            if ("cover".equals(n.props.optString("fit")) || "fill".equals(n.props.optString("fit"))) {
                s = Math.max(lp.pw > 0 ? sx : 0, lp.ph > 0 ? sy : 0);
            }
            if (s != Float.MAX_VALUE && s > 0) {
                w = Math.round(w * s);
                h = Math.round(h * s);
            }
        }
        dr.setBounds(0, 0, w, h);
        switch (compound) {
            case "left":
                t.setCompoundDrawables(dr, null, null, null);
                break;
            case "right":
                t.setCompoundDrawables(null, null, dr, null);
                break;
            case "bottom":
                t.setCompoundDrawables(null, null, null, dr);
                break;
            case "none":
                t.setText("");
                t.setCompoundDrawables(null, dr, null, null);
                t.setCompoundDrawablePadding(0);
                break;
            default:
                t.setCompoundDrawables(null, dr, null, null);
        }
        t.setCompoundDrawablePadding(dp(4));
        if (n.view.getParent() instanceof View) ((View) n.view.getParent()).requestLayout();
    }

    private void applyEditProp(Node n, String k, @Nullable Object v) {
        EditText e = n.edit;
        if (e == null) return;
        switch (k) {
            case "value": {
                String s = v != null ? v.toString() : "";
                if ("combo".equals(n.type) && e instanceof MaterialAutoCompleteTextView) {
                    if (!s.equals(e.getText().toString())) ((MaterialAutoCompleteTextView) e).setText(s, false);
                    return;
                }
                if (!s.equals(e.getText().toString())) {
                    int sel = e.getSelectionEnd();
                    e.setText(s);
                    e.setSelection(Math.max(0, Math.min(s.length(), sel < 0 ? s.length() : sel)));
                }
                applySpans(n);
                if (n.props.optBoolean("autoscroll")) e.setSelection(e.length());
                return;
            }
            case "spans":
                applySpans(n);
                return;
            case "password":
            case "reveal": {
                boolean pw = n.props.optBoolean("password");
                if (pw) {
                    e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    e.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    if (n.til != null && n.props.optBoolean("reveal")) {
                        n.til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                    }
                } else {
                    e.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
                return;
            }
            case "hint":
                if (n.til != null && !n.props.has("label")) n.til.setHint(v != null ? v.toString() : null);
                else if (n.til != null) n.til.setPlaceholderText(v != null ? v.toString() : null);
                else e.setHint(v != null ? v.toString() : null);
                return;
            case "label":
                if (n.til != null) n.til.setHint(v != null ? v.toString() : null);
                return;
            case "prefix":
                if (n.til != null) n.til.setPrefixText(v != null ? v.toString() : null);
                return;
            case "suffix":
                if (n.til != null) n.til.setSuffixText(v != null ? v.toString() : null);
                return;
            case "error":
                if (n.til != null) n.til.setError(v != null ? v.toString() : null);
                return;
            case "helper":
                if (n.til != null) n.til.setHelperText(v != null ? v.toString() : null);
                return;
            case "maxlen":
                e.setFilters(v instanceof Number ? new InputFilter[]{new InputFilter.LengthFilter(((Number) v).intValue())}
                        : new InputFilter[0]);
                return;
            case "keyboard": {
                String kb = v != null ? v.toString() : "text";
                int type;
                switch (kb) {
                    case "number":
                        type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
                        break;
                    case "phone":
                        type = InputType.TYPE_CLASS_PHONE;
                        break;
                    case "email":
                        type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                        break;
                    case "url":
                        type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
                        break;
                    case "multiline":
                        type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                        break;
                    default:
                        type = InputType.TYPE_CLASS_TEXT;
                }
                if (!n.props.optBoolean("password")) e.setInputType(type);
                return;
            }
            case "autofocus":
                if (Boolean.TRUE.equals(v)) e.post(e::requestFocus);
                return;
            case "items": {
                if (e instanceof MaterialAutoCompleteTextView) {
                    List<String> items = new ArrayList<>();
                    if (v instanceof JSONArray) {
                        JSONArray arr = (JSONArray) v;
                        for (int i = 0; i < arr.length(); i++) items.add(arr.optString(i));
                    }
                    ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, items) {
                        @NonNull
                        @Override
                        public android.widget.Filter getFilter() {
                            // 入力した文字で候補を絞らない(いつも全部を出す)
                            return new android.widget.Filter() {
                                @Override
                                protected FilterResults performFiltering(CharSequence constraint) {
                                    FilterResults r = new FilterResults();
                                    r.values = items;
                                    r.count = items.size();
                                    return r;
                                }

                                @Override
                                protected void publishResults(CharSequence constraint, FilterResults results) {
                                    notifyDataSetChanged();
                                }
                            };
                        }
                    };
                    ((MaterialAutoCompleteTextView) e).setAdapter(ad);
                }
                return;
            }
            case "readonly": {
                boolean ro = Boolean.TRUE.equals(v);
                if (ro) {
                    e.setKeyListener(null);
                    e.setFocusable(false);
                    e.setCursorVisible(false);
                } else {
                    e.setKeyListener(android.text.method.TextKeyListener.getInstance());
                    e.setFocusableInTouchMode(true);
                    e.setCursorVisible(true);
                    e.setInputType(InputType.TYPE_CLASS_TEXT);
                }
                return;
            }
            case "nowrap":
                e.setHorizontallyScrolling(Boolean.TRUE.equals(v));
                return;
            case "justify":
                e.setGravity(gravity("text".equals(n.type) ? "nw" : "w", v != null ? v.toString() : ""));
                return;
            default:
        }
    }

    /** tk の Text のタグ(色・太字など)を文字に付ける。 */
    private void applySpans(Node n) {
        EditText e = n.edit;
        JSONArray spans = n.props.optJSONArray("spans");
        if (e == null) return;
        Editable ed = e.getText();
        for (Object o : ed.getSpans(0, ed.length(), Object.class)) {
            if (o instanceof ForegroundColorSpan || o instanceof BackgroundColorSpan || o instanceof StyleSpan
                    || o instanceof UnderlineSpan || o instanceof StrikethroughSpan || o instanceof AbsoluteSizeSpan) {
                ed.removeSpan(o);
            }
        }
        if (spans == null) return;
        for (int i = 0; i < spans.length(); i++) {
            JSONArray sp = spans.optJSONArray(i);
            if (sp == null) continue;
            int s = Math.max(0, Math.min(ed.length(), sp.optInt(0))), en = Math.max(s, Math.min(ed.length(), sp.optInt(1)));
            if (s >= en) continue;
            JSONObject st = sp.optJSONObject(2);
            if (st == null) continue;
            Integer fg = color(st.opt("fg")), bg = color(st.opt("bg"));
            if (fg != null) ed.setSpan(new ForegroundColorSpan(fg), s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (bg != null) ed.setSpan(new BackgroundColorSpan(bg), s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            JSONObject f = st.optJSONObject("font");
            if (f != null) {
                int style = (f.optBoolean("bold") ? Typeface.BOLD : 0) | (f.optBoolean("italic") ? Typeface.ITALIC : 0);
                if (style != 0) ed.setSpan(new StyleSpan(style), s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (f.has("size")) {
                    ed.setSpan(new AbsoluteSizeSpan(Math.round((float) f.optDouble("size") * d)), s, en,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            if (st.optBoolean("underline")) ed.setSpan(new UnderlineSpan(), s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (st.optBoolean("strike")) ed.setSpan(new StrikethroughSpan(), s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void applySliderProp(Node n, String k, @Nullable Object v) {
        Slider s = n.slider;
        if (s == null) return;
        JSONObject p = n.props;
        switch (k) {
            case "from":
            case "to":
            case "step":
            case "value": {
                float from = (float) p.optDouble("from", 0), to = (float) p.optDouble("to", 100);
                if (to < from) {
                    float t = from;
                    from = to;
                    to = t;
                }
                if (to == from) to = from + 1;
                float step = (float) p.optDouble("step", 0);
                if (step > 0) {
                    double steps = (to - from) / step;
                    if (Math.abs(steps - Math.round(steps)) > 1e-4) step = 0; // 範囲を割り切れない刻みは使えない
                }
                float value = (float) p.optDouble("value", from);
                value = Math.max(from, Math.min(to, value));
                if (step > 0) value = from + Math.round((value - from) / step) * step;
                s.setValueFrom(from);
                s.setValueTo(to);
                s.setStepSize(Math.max(0, step));
                s.setValue(Math.max(from, Math.min(to, value)));
                updateSliderLabel(n);
                return;
            }
            case "label":
            case "showvalue":
                updateSliderLabel(n);
                return;
            default:
        }
    }

    private void updateSliderLabel(Node n) {
        if (n.sliderLabel == null || n.slider == null) return;
        String label = n.props.optString("label", "");
        boolean show = n.props.optBoolean("showvalue");
        float v = n.slider.getValue();
        String vs = (v == Math.rint(v)) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
        String text = label.isEmpty() ? (show ? vs : "") : (show ? label + "  " + vs : label);
        n.sliderLabel.setText(text);
        n.sliderLabel.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void applyProgressProp(Node n, String k, @Nullable Object v) {
        JSONObject p = n.props;
        com.google.android.material.progressindicator.BaseProgressIndicator<?> pi =
                (com.google.android.material.progressindicator.BaseProgressIndicator<?>) n.view;
        boolean ind = "indeterminate".equals(p.optString("mode"));
        if (pi.isIndeterminate() != ind) {
            pi.setVisibility(View.INVISIBLE);
            pi.setIndeterminate(ind);
            pi.setVisibility(n.visibleProp ? View.VISIBLE : View.GONE);
        }
        if (!ind) {
            float max = (float) p.optDouble("max", 100);
            pi.setMax(1000);
            pi.setProgressCompat(Math.round((float) p.optDouble("value", 0) / Math.max(1e-6f, max) * 1000), true);
        }
    }

    private void applyListProp(Node n, String k, @Nullable Object v) {
        PyListAdapter ad = n.adapter;
        if (ad == null) return;
        switch (k) {
            case "items": {
                ad.rows.clear();
                if (v instanceof JSONArray) {
                    JSONArray arr = (JSONArray) v;
                    for (int i = 0; i < arr.length(); i++) ad.rows.add(java.util.Collections.singletonList(arr.optString(i)));
                }
                ad.notifyDataSetChanged();
                return;
            }
            case "rows": {
                ad.rows.clear();
                if (v instanceof JSONArray) {
                    JSONArray arr = (JSONArray) v;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONArray r = arr.optJSONArray(i);
                        List<String> cells = new ArrayList<>();
                        if (r != null) for (int c = 0; c < r.length(); c++) cells.add(r.optString(c));
                        ad.rows.add(cells);
                    }
                }
                ad.notifyDataSetChanged();
                return;
            }
            case "widths": {
                if (v instanceof JSONArray) {
                    JSONArray arr = (JSONArray) v;
                    ad.widths = new float[arr.length()];
                    for (int i = 0; i < arr.length(); i++) ad.widths[i] = (float) arr.optDouble(i, 100);
                }
                rebuildHeader(n);
                ad.notifyDataSetChanged();
                return;
            }
            case "headings":
                rebuildHeader(n);
                return;
            case "selection": {
                ad.selected.clear();
                if (v instanceof JSONArray) {
                    JSONArray arr = (JSONArray) v;
                    for (int i = 0; i < arr.length(); i++) ad.selected.add(arr.optInt(i));
                }
                ad.notifyDataSetChanged();
                return;
            }
            case "select_mode":
                ad.mode = v != null ? v.toString() : "browse";
                return;
            case "select_bg": {
                Integer c = color(v);
                if (c != null) ad.selectBg = c;
                ad.notifyDataSetChanged();
                return;
            }
            default:
        }
    }

    private void rebuildHeader(Node n) {
        LinearLayout h = n.tableHeader;
        if (h == null || n.adapter == null) return;
        h.removeAllViews();
        JSONArray heads = n.props.optJSONArray("headings");
        if (heads == null || heads.length() == 0) {
            h.setVisibility(View.GONE);
            return;
        }
        h.setVisibility(View.VISIBLE);
        h.setPadding(dp(12), 0, dp(12), 0);
        for (int i = 0; i < heads.length(); i++) {
            TextView t = new TextView(a);
            t.setText(heads.optString(i));
            t.setTextColor(cOnSurfaceVariant);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(0, dp(10), dp(8), dp(10));
            float w = i < n.adapter.widths.length ? n.adapter.widths[i] : 1;
            h.addView(t, new LinearLayout.LayoutParams(0, -2, Math.max(0.1f, w)));
        }
        sizeByChars(n);
    }

    private void applyNotebookProp(Node n, String k, @Nullable Object v) {
        TabLayout tabs = n.tabs;
        if (tabs == null || n.box == null) return;
        switch (k) {
            case "tabs": {
                JSONArray arr = v instanceof JSONArray ? (JSONArray) v : new JSONArray();
                tabs.removeAllTabs();
                for (int i = 0; i < arr.length(); i++) tabs.addTab(tabs.newTab().setText(arr.optString(i)), false);
                selectTab(n, n.props.optInt("selected", 0));
                return;
            }
            case "selected":
                selectTab(n, v instanceof Number ? ((Number) v).intValue() : 0);
                return;
            default:
        }
    }

    private void selectTab(Node n, int index) {
        if (n.tabs == null || n.box == null) return;
        n.box.selectedTab = index;
        TabLayout.Tab t = n.tabs.getTabAt(index);
        if (t != null && !t.isSelected()) t.select();
        updateTabs(n);
    }

    private void sizeSeparator(Node n) {
        PyLayout.LP lp = PyLayout.lp(n.view);
        boolean vert = "vertical".equals(n.props.optString("orient"));
        int th = Math.max(1, dp((float) n.props.optDouble("thickness", 1)));
        if (vert) {
            lp.pw = th;
            lp.ph = -1;
        } else {
            lp.ph = th;
            lp.pw = -1;
        }
        n.view.setLayoutParams(lp);
    }

    // ==================================================================
    // メニュー
    // ==================================================================

    private void showWindowMenu(Win w, View anchor) {
        if (w.menu == null) return;
        PopupMenu pm = new PopupMenu(a, anchor);
        Map<MenuItem, String> keys = new HashMap<>();
        fillMenu(pm.getMenu(), w.menu, keys);
        pm.setOnMenuItemClickListener(item -> {
            String key = keys.get(item);
            if (key != null && !key.isEmpty()) emit(w.node, "menu", "key", key);
            return true;
        });
        pm.show();
    }

    private void fillMenu(Menu menu, JSONArray items, Map<MenuItem, String> keys) {
        int group = 1;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            String type = it.optString("type", "command");
            if ("separator".equals(type)) {
                group++;
                continue;
            }
            String label = it.optString("label");
            String accel = it.optString("accel", "");
            if ("cascade".equals(type)) {
                SubMenu sub = menu.addSubMenu(group, Menu.NONE, Menu.NONE, label);
                JSONArray kids = it.optJSONArray("items");
                if (kids != null) fillMenu(sub, kids, keys);
                continue;
            }
            CharSequence title = label;
            if (!accel.isEmpty()) {
                SpannableString s = new SpannableString(label + "    " + accel);
                s.setSpan(new ForegroundColorSpan(cOnSurfaceVariant), label.length(), s.length(), 0);
                title = s;
            }
            MenuItem mi = menu.add(group, Menu.NONE, Menu.NONE, title);
            mi.setEnabled(it.optBoolean("enabled", true));
            if ("checkbutton".equals(type) || "radiobutton".equals(type)) {
                mi.setCheckable(true);
                mi.setChecked(it.optBoolean("checked"));
            }
            keys.put(mi, it.optString("key"));
        }
    }

    private void popup(Conn c, JSONObject m) {
        int req = m.optInt("req");
        JSONArray items = m.optJSONArray("items");
        Node anchorNode = node(c, m, "id");
        View anchor = anchorNode != null ? anchorNode.view : null;
        if (anchor == null || !anchor.isAttachedToWindow()) {
            Win w = topWindow();
            anchor = w != null ? w.title : area;
        }
        if (items == null) {
            reply(c, req, null);
            return;
        }
        PopupMenu pm = new PopupMenu(a, anchor);
        Map<MenuItem, String> keys = new HashMap<>();
        fillMenu(pm.getMenu(), items, keys);
        final boolean[] done = {false};
        pm.setOnMenuItemClickListener(item -> {
            String key = keys.get(item);
            if (key == null) return false;
            if (!done[0]) {
                done[0] = true;
                reply(c, req, key);
            }
            return true;
        });
        pm.setOnDismissListener(menu -> anchorNodeDismiss(c, req, done));
        pm.show();
    }

    private void anchorNodeDismiss(Conn c, int req, boolean[] done) {
        // 項目を押した時も先に dismiss が来ることがあるので、少し待ってから「選ばなかった」と返す
        ui.postDelayed(() -> {
            if (!done[0]) {
                done[0] = true;
                reply(c, req, null);
            }
        }, 200);
    }

    // ==================================================================
    // ダイアログ
    // ==================================================================

    private void dialog(Conn c, JSONObject m) {
        String kind = m.optString("kind");
        int req = m.optInt("req");
        switch (kind) {
            case "message":
                messageDialog(c, req, m);
                break;
            case "input":
                inputDialog(c, req, m);
                break;
            case "choice":
            case "multichoice":
                choiceDialog(c, req, m, kind.equals("multichoice"));
                break;
            case "color":
                colorDialog(c, req, m);
                break;
            case "open":
                new FileChooser(c, req, m).show();
                break;
            case "save":
                saveDialog(c, req, m);
                break;
            default:
                reply(c, req, null);
        }
    }

    private void messageDialog(Conn c, int req, JSONObject m) {
        JSONArray buttons = m.optJSONArray("buttons");
        String title = m.optString("title");
        String icon = m.optString("icon", "info");
        if (title.isEmpty()) {
            title = icon.equals("error") ? Lang.t("エラー") : icon.equals("warning") ? Lang.t("警告") : icon.equals("question") ? Lang.t("確認") : Lang.t("メッセージ");
        }
        final int[] chosen = {-1};
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle(title).setMessage(m.optString("message"));
        int iconRes = icon.equals("error") || icon.equals("warning") ? R.drawable.ic_si_warning
                : icon.equals("question") ? R.drawable.ic_si_info : R.drawable.ic_si_info;
        b.setIcon(iconRes);
        int n = buttons != null ? buttons.length() : 0;
        if (n == 0) {
            b.setPositiveButton("OK", (dd, w) -> chosen[0] = 0);
        } else if (n <= 3) {
            b.setPositiveButton(buttons.optString(0), (dd, w) -> chosen[0] = 0);
            if (n > 1) b.setNegativeButton(buttons.optString(1), (dd, w) -> chosen[0] = 1);
            if (n > 2) b.setNeutralButton(buttons.optString(2), (dd, w) -> chosen[0] = 2);
        } else {
            String[] items = new String[n];
            for (int i = 0; i < n; i++) items[i] = buttons.optString(i);
            b.setItems(items, (dd, w) -> chosen[0] = w);
        }
        b.setOnDismissListener(dd -> reply(c, req, chosen[0]));
        b.show();
    }

    private FrameLayout boxed(View v) {
        FrameLayout box = new FrameLayout(a);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(v, new FrameLayout.LayoutParams(-1, -2));
        return box;
    }

    private void inputDialog(Conn c, int req, JSONObject m) {
        EditText e = new EditText(a);
        e.setText(m.optString("initial"));
        String num = m.optString("number", "text");
        if (m.optBoolean("password")) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if ("int".equals(num)) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else if ("float".equals(num)) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else if (m.optBoolean("multiline")) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        e.setSelectAllOnFocus(true);
        final String[] value = {null};
        String title = m.optString("title");
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                .setTitle(title.isEmpty() ? Lang.t("入力") : title)
                .setView(boxed(e))
                .setPositiveButton("OK", (dd, w) -> value[0] = e.getText().toString())
                .setNegativeButton(Lang.t("キャンセル"), null);
        String msg = m.optString("message");
        if (!msg.isEmpty()) b.setMessage(msg);
        androidx.appcompat.app.AlertDialog dlg = b.create();
        dlg.setOnDismissListener(dd -> {
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(e.getWindowToken(), 0);
            reply(c, req, value[0]);
        });
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dlg.show();
        e.requestFocus();
    }

    private void choiceDialog(Conn c, int req, JSONObject m, boolean multi) {
        JSONArray arr = m.optJSONArray("items");
        String[] items = new String[arr != null ? arr.length() : 0];
        for (int i = 0; i < items.length; i++) items[i] = arr.optString(i);
        String title = m.optString("title");
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle(title.isEmpty() ? Lang.t("選択") : title);
        if (multi) {
            boolean[] checked = new boolean[items.length];
            JSONArray cur = m.optJSONArray("checked");
            if (cur != null) for (int i = 0; i < cur.length(); i++) {
                int k = cur.optInt(i, -1);
                if (k >= 0 && k < checked.length) checked[k] = true;
            }
            final boolean[] ok = {false};
            b.setMultiChoiceItems(items, checked, (dd, w, on) -> checked[w] = on);
            b.setPositiveButton("OK", (dd, w) -> ok[0] = true);
            b.setNegativeButton(Lang.t("キャンセル"), null);
            b.setOnDismissListener(dd -> {
                if (!ok[0]) {
                    reply(c, req, null);
                    return;
                }
                JSONArray out = new JSONArray();
                for (int i = 0; i < checked.length; i++) if (checked[i]) out.put(i);
                reply(c, req, out);
            });
        } else {
            final int[] chosen = {-1};
            int sel = m.optInt("checked", -1);
            b.setSingleChoiceItems(items, sel, (dd, w) -> {
                chosen[0] = w;
                dd.dismiss();
            });
            b.setNegativeButton(Lang.t("キャンセル"), null);
            b.setOnDismissListener(dd -> reply(c, req, chosen[0]));
        }
        b.show();
    }

    private static final int[] PALETTE = {
            0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
            0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722,
            0xFF795548, 0xFF9E9E9E, 0xFF607D8B, 0xFF000000, 0xFFFFFFFF, 0xFFD3E3FD, 0xFF0B57D0, 0xFF146C2E};

    private void colorDialog(Conn c, int req, JSONObject m) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        EditText hex = new EditText(a);
        hex.setSingleLine(true);
        hex.setHint("#RRGGBB");
        String initial = m.optString("initial", "");
        if (!initial.isEmpty() && !"null".equals(initial)) hex.setText("#" + initial.substring(Math.max(1, initial.length() - 6)));
        for (int colr : PALETTE) {
            View sw = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(colr);
            g.setStroke(Math.max(1, dp(1)), cOutlineVariant);
            sw.setBackground(g);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(36);
            lp.height = dp(36);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            sw.setOnClickListener(v -> hex.setText(String.format("#%06X", colr & 0xFFFFFF)));
            grid.addView(sw, lp);
        }
        col.addView(grid);
        col.addView(hex);
        final String[] value = {null};
        new MaterialAlertDialogBuilder(a)
                .setTitle(m.optString("title").isEmpty() ? Lang.t("色を選ぶ") : m.optString("title"))
                .setView(boxed(col))
                .setPositiveButton("OK", (dd, w) -> {
                    Integer colr = PyDraw.color(hex.getText().toString().trim());
                    if (colr != null) value[0] = String.format("#%06x", colr & 0xFFFFFF);
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setOnDismissListener(dd -> reply(c, req, value[0]))
                .show();
    }

    private File startDir(JSONObject m) {
        File work = host.workDir();
        String init = m.optString("initialdir", "");
        File dir = init.isEmpty() ? work : new File(init);
        if (!dir.isDirectory() || !inside(dir, a.getFilesDir()) && !inside(dir, a.getCacheDir())) dir = work;
        return dir;
    }

    private static boolean inside(File f, File root) {
        try {
            String fp = f.getCanonicalPath(), rp = root.getCanonicalPath();
            return fp.equals(rp) || fp.startsWith(rp + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private void saveDialog(Conn c, int req, JSONObject m) {
        File dir = startDir(m);
        EditText e = new EditText(a);
        e.setSingleLine(true);
        String initial = m.optString("initialfile", "");
        String ext = m.optString("defaultextension", "");
        if (initial.isEmpty()) initial = "untitled" + (ext.isEmpty() ? ".txt" : (ext.startsWith(".") ? ext : "." + ext));
        e.setText(initial);
        int dot = initial.lastIndexOf('.');
        final String[] value = {null};
        String title = m.optString("title");
        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(a)
                .setTitle(title.isEmpty() ? Lang.t("名前を付けて保存") : title)
                .setMessage(Lang.t("保存先: ") + relative(dir) + Lang.t("\n(プログラムの作業フォルダ。Python 実行ツールの「作業フォルダを書き出す」で端末に保存できます)"))
                .setView(boxed(e))
                .setPositiveButton(Lang.t("保存"), (dd, w) -> {
                    String name = e.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File f = new File(dir, name);
                    File parent = f.getParentFile();
                    if (parent != null) parent.mkdirs();
                    value[0] = f.getAbsolutePath();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .create();
        dlg.setOnDismissListener(dd -> reply(c, req, value[0]));
        dlg.show();
        e.requestFocus();
        if (dot > 0) e.setSelection(0, dot);
    }

    private String relative(File dir) {
        File work = host.workDir();
        try {
            String w = work.getCanonicalPath(), p = dir.getCanonicalPath();
            if (p.equals(w)) return Lang.t("作業フォルダ");
            if (p.startsWith(w + File.separator)) return Lang.t("作業フォルダ/") + p.substring(w.length() + 1);
            return p;
        } catch (IOException e) {
            return dir.getPath();
        }
    }

    /** ファイル・フォルダを選ぶダイアログ(作業フォルダの中から。端末のファイルを取り込むこともできる)。 */
    private final class FileChooser {
        final Conn c;
        final int req;
        final JSONObject m;
        final boolean multiple, directory;
        final List<String> patterns = new ArrayList<>();
        File dir;
        final List<File> picked = new ArrayList<>();
        boolean replied;
        androidx.appcompat.app.AlertDialog dlg;

        FileChooser(Conn c, int req, JSONObject m) {
            this.c = c;
            this.req = req;
            this.m = m;
            multiple = m.optBoolean("multiple");
            directory = m.optBoolean("directory");
            dir = startDir(m);
            JSONArray types = m.optJSONArray("filetypes");
            if (types != null && types.length() > 0) {
                JSONArray first = types.optJSONArray(0);
                JSONArray pats = first != null ? first.optJSONArray(1) : null;
                if (pats != null) for (int i = 0; i < pats.length(); i++) patterns.add(pats.optString(i).toLowerCase(Locale.ROOT));
            }
        }

        boolean matches(File f) {
            if (patterns.isEmpty()) return true;
            String name = f.getName().toLowerCase(Locale.ROOT);
            for (String p : patterns) {
                if (p.equals("*") || p.equals("*.*")) return true;
                String regex = p.replace(".", "\\.").replace("*", ".*").replace("?", ".");
                if (name.matches(regex)) return true;
            }
            return false;
        }

        void done(@Nullable List<File> files) {
            if (replied) return;
            replied = true;
            if (files == null || files.isEmpty()) {
                reply(c, req, null);
                return;
            }
            JSONArray out = new JSONArray();
            for (File f : files) out.put(f.getAbsolutePath());
            reply(c, req, out);
        }

        void show() {
            List<File> entries = new ArrayList<>();
            File[] kids = dir.listFiles();
            if (kids != null) {
                Arrays.sort(kids, (x, y) -> {
                    if (x.isDirectory() != y.isDirectory()) return x.isDirectory() ? -1 : 1;
                    return x.getName().compareToIgnoreCase(y.getName());
                });
                for (File k : kids) {
                    if (k.getName().equals("__pycache__") || k.getName().equals(".airsky")) continue;
                    if (k.isDirectory() || (!directory && matches(k))) entries.add(k);
                }
            }
            List<String> labels = new ArrayList<>();
            boolean canUp = !samePath(dir, host.workDir()) && inside(dir, host.workDir());
            if (canUp) labels.add(Lang.t("⬆  上のフォルダへ"));
            if (!directory) labels.add(Lang.t("📥  端末のファイルを取り込む…"));
            for (File f : entries) {
                String mark = picked.contains(f) ? "✓ " : "";
                labels.add(mark + (f.isDirectory() ? "📁  " : "📄  ") + f.getName());
            }
            String title = m.optString("title");
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                    .setTitle((title.isEmpty() ? (directory ? Lang.t("フォルダを選ぶ") : Lang.t("ファイルを開く")) : title))
                    .setItems(labels.toArray(new String[0]), (dd, w) -> {
                        int i = w;
                        if (canUp) {
                            if (i == 0) {
                                dir = dir.getParentFile();
                                ui.post(this::show);
                                return;
                            }
                            i--;
                        }
                        if (!directory) {
                            if (i == 0) {
                                importFile();
                                return;
                            }
                            i--;
                        }
                        File f = entries.get(i);
                        if (f.isDirectory()) {
                            dir = f;
                            ui.post(this::show);
                        } else if (multiple) {
                            if (picked.contains(f)) picked.remove(f);
                            else picked.add(f);
                            ui.post(this::show);
                        } else {
                            done(java.util.Collections.singletonList(f));
                        }
                    })
                    .setNegativeButton(Lang.t("キャンセル"), (dd, w) -> done(null));
            if (directory) b.setPositiveButton(Lang.t("このフォルダを選ぶ"), (dd, w) -> done(java.util.Collections.singletonList(dir)));
            if (multiple) b.setPositiveButton(Lang.t("開く (") + picked.size() + ")", (dd, w) -> done(new ArrayList<>(picked)));
            b.setOnCancelListener(dd -> done(null));
            dlg = b.create();
            dlg.setMessage(null);
            dlg.show();
        }

        void importFile() {
            host.pickDocument(uri -> {
                if (uri == null) {
                    show();
                    return;
                }
                new Thread(() -> {
                    File result = null;
                    try {
                        File[] before = dir.listFiles();
                        PyStore.importInto(a, dir, uri);
                        File[] after = dir.listFiles();
                        if (after != null) {
                            List<File> b = before != null ? Arrays.asList(before) : new ArrayList<>();
                            for (File f : after) if (!b.contains(f)) result = f;
                        }
                    } catch (IOException ignored) {
                    }
                    File r = result;
                    ui.post(() -> {
                        if (r == null) {
                            Toast.makeText(a, Lang.t("取り込めませんでした"), Toast.LENGTH_SHORT).show();
                            show();
                        } else if (multiple) {
                            picked.add(r);
                            show();
                        } else {
                            done(java.util.Collections.singletonList(r));
                        }
                    });
                }, "py-import").start();
            });
        }
    }

    private static boolean samePath(File x, File y) {
        try {
            return x.getCanonicalPath().equals(y.getCanonicalPath());
        } catch (IOException e) {
            return x.equals(y);
        }
    }
}
