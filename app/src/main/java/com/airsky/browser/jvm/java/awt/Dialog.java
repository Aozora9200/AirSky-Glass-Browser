package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmRuntime;

/** java.awt.Dialog の互換クラス。モーダルなら閉じるまで呼んだところで待つ(EDT はイベントを処理し続ける)。 */
public class Dialog extends Window {
    public enum ModalityType { MODELESS, DOCUMENT_MODAL, APPLICATION_MODAL, TOOLKIT_MODAL }
    public static final ModalityType DEFAULT_MODALITY_TYPE = ModalityType.APPLICATION_MODAL;

    private String title = "";
    private boolean modal;
    private boolean resizable = true;
    private boolean undecorated;

    public Dialog(Frame owner) { this(owner, "", false); }
    public Dialog(Frame owner, boolean modal) { this(owner, "", modal); }
    public Dialog(Frame owner, String title) { this(owner, title, false); }

    public Dialog(Frame owner, String title, boolean modal) {
        super((Window) owner);
        this.title = title != null ? title : "";
        this.modal = modal;
    }

    public Dialog(Dialog owner) { this(owner, "", false); }
    public Dialog(Dialog owner, String title) { this(owner, title, false); }

    public Dialog(Dialog owner, String title, boolean modal) {
        super((Window) owner);
        this.title = title != null ? title : "";
        this.modal = modal;
    }

    public Dialog(Window owner) { this(owner, "", ModalityType.MODELESS); }
    public Dialog(Window owner, String title) { this(owner, title, ModalityType.MODELESS); }
    public Dialog(Window owner, ModalityType type) { this(owner, "", type); }

    public Dialog(Window owner, String title, ModalityType type) {
        super(owner);
        this.title = title != null ? title : "";
        this.modal = type != null && type != ModalityType.MODELESS;
    }

    public String getTitle() { return title; }

    public void setTitle(String t) {
        title = t != null ? t : "";
        if (jvm$isShown()) JvmRuntime.addWindow(this);
    }

    public boolean isModal() { return modal; }
    public void setModal(boolean b) { modal = b; }
    public ModalityType getModalityType() { return modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS; }
    public void setModalityType(ModalityType t) { modal = t != null && t != ModalityType.MODELESS; }
    public boolean isResizable() { return resizable; }
    public void setResizable(boolean b) { resizable = b; }
    public boolean isUndecorated() { return undecorated; }
    public void setUndecorated(boolean b) { undecorated = b; }

    @Override
    public void setVisible(boolean b) {
        boolean wasShown = jvm$isShown();
        super.setVisible(b);
        if (b && modal && !wasShown) {
            JvmRuntime.pumpUntil(() -> !jvm$isShown());
        }
    }

    @Override
    public String jvm$title() { return title; }
}
