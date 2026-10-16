package com.airsky.browser.jvm.java.awt.datatransfer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.airsky.browser.jvm.JvmRuntime;

/** java.awt.datatransfer.Clipboard の互換クラス。システムのクリップボードは Android のものを使う。 */
public class Clipboard {
    public static final Clipboard SYSTEM = new Clipboard("System");

    private final String name;
    protected Transferable contents;
    protected ClipboardOwner owner;

    public Clipboard(String name) { this.name = name; }

    public String getName() { return name; }

    public synchronized void setContents(Transferable t, ClipboardOwner o) {
        ClipboardOwner old = owner;
        Transferable oldContents = contents;
        contents = t;
        owner = o;
        if (old != null && old != o) old.lostOwnership(this, oldContents);
        if (this == SYSTEM && t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                String text = String.valueOf(t.getTransferData(DataFlavor.stringFlavor));
                Context c = JvmRuntime.host() != null ? JvmRuntime.host().context() : null;
                if (c != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("java", text));
                    });
                }
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized Transferable getContents(Object requestor) {
        if (this == SYSTEM) {
            String text = systemText();
            if (text != null) return new StringSelection(text);
        }
        return contents;
    }

    private static String systemText() {
        Context c = JvmRuntime.host() != null ? JvmRuntime.host().context() : null;
        if (c == null) return null;
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence s = clip.getItemAt(0).coerceToText(c);
                return s != null ? s.toString() : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public DataFlavor[] getAvailableDataFlavors() {
        Transferable t = getContents(null);
        return t != null ? t.getTransferDataFlavors() : new DataFlavor[0];
    }

    public boolean isDataFlavorAvailable(DataFlavor f) {
        Transferable t = getContents(null);
        return t != null && t.isDataFlavorSupported(f);
    }

    public Object getData(DataFlavor f) throws UnsupportedFlavorException, java.io.IOException {
        Transferable t = getContents(null);
        if (t == null) throw new UnsupportedFlavorException(f);
        return t.getTransferData(f);
    }
}
