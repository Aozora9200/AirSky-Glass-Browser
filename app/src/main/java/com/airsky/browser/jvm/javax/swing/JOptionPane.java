package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.Lang;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.BorderLayout;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.FlowLayout;
import com.airsky.browser.jvm.java.awt.Frame;
import com.airsky.browser.jvm.java.awt.HeadlessException;
import com.airsky.browser.jvm.java.awt.Window;

/** javax.swing.JOptionPane の互換クラス。文字だけのダイアログは Android のダイアログで出す。 */
public class JOptionPane extends JComponent {
    public static final Object UNINITIALIZED_VALUE = "uninitializedValue";
    public static final int DEFAULT_OPTION = -1;
    public static final int YES_NO_OPTION = 0;
    public static final int YES_NO_CANCEL_OPTION = 1;
    public static final int OK_CANCEL_OPTION = 2;
    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int CANCEL_OPTION = 2;
    public static final int OK_OPTION = 0;
    public static final int CLOSED_OPTION = -1;
    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;

    public JOptionPane() {}

    private static String text(Object message) {
        if (message == null) return "";
        if (message instanceof Object[]) {
            StringBuilder sb = new StringBuilder();
            for (Object o : (Object[]) message) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(text(o));
            }
            return sb.toString();
        }
        if (message instanceof JLabel) return String.join("\n", JvmLook.lines(((JLabel) message).getText()));
        if (message instanceof Icon) return "";
        String s = String.valueOf(message);
        return String.join("\n", s.regionMatches(true, 0, "<html>", 0, 6) ? JvmLook.lines(s) : new String[]{s});
    }

    private static boolean hasComponent(Object message) {
        if (message instanceof JLabel) return false;
        if (message instanceof Component) return true;
        if (message instanceof Object[]) {
            for (Object o : (Object[]) message) if (hasComponent(o)) return true;
        }
        return false;
    }

    private static String[] buttons(int optionType) {
        switch (optionType) {
            case YES_NO_OPTION: return new String[]{Lang.t("はい"), Lang.t("いいえ")};
            case YES_NO_CANCEL_OPTION: return new String[]{Lang.t("はい"), Lang.t("いいえ"), Lang.t("キャンセル")};
            case OK_CANCEL_OPTION: return new String[]{"OK", Lang.t("キャンセル")};
            default: return new String[]{"OK"};
        }
    }

    private static int result(int optionType, int index) {
        if (index < 0) return CLOSED_OPTION;
        if (optionType == OK_CANCEL_OPTION) return index == 0 ? OK_OPTION : CANCEL_OPTION;
        return index;
    }

    private static int dialog(Component parent, Object message, String title, int messageType, String[] labels) {
        if (hasComponent(message)) return componentDialog(parent, message, title, labels);
        String t = title;
        String m = text(message);
        return JvmRuntime.blocking(() -> JvmRuntime.host().showDialog(t, m, messageType, labels), -1);
    }

    /** 部品を入れたメッセージは、互換レイヤーのダイアログを作って出す。 */
    private static int componentDialog(Component parent, Object message, String title, String[] labels) {
        Window owner = parent != null ? parent.jvm$window() : null;
        JDialog d = new JDialog(owner, title, Dialog$.MODAL);
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        Object[] parts = message instanceof Object[] ? (Object[]) message : new Object[]{message};
        for (Object o : parts) {
            Component c = o instanceof Component ? (Component) o : new JLabel(String.valueOf(o));
            if (c instanceof JComponent) ((JComponent) c).setAlignmentX(0f);
            body.add(c);
            body.add(Box.createVerticalStrut(8));
        }
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        final int[] chosen = {-1};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            JButton b = new JButton(labels[i]);
            b.addActionListener(e -> {
                chosen[0] = index;
                d.dispose();
            });
            row.add(b);
        }
        d.getContentPane().add(body, BorderLayout.CENTER);
        d.getContentPane().add(row, BorderLayout.SOUTH);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.pack();
        d.setVisible(true);
        return chosen[0];
    }

    private static final class Dialog$ {
        static final com.airsky.browser.jvm.java.awt.Dialog.ModalityType MODAL =
                com.airsky.browser.jvm.java.awt.Dialog.ModalityType.APPLICATION_MODAL;
    }

    public static void showMessageDialog(Component parent, Object message) throws HeadlessException {
        showMessageDialog(parent, message, Lang.t("メッセージ"), INFORMATION_MESSAGE);
    }

    public static void showMessageDialog(Component parent, Object message, String title, int messageType) throws HeadlessException {
        dialog(parent, message, title, messageType, buttons(DEFAULT_OPTION));
    }

    public static void showMessageDialog(Component parent, Object message, String title, int messageType, Icon icon) throws HeadlessException {
        showMessageDialog(parent, message, title, messageType);
    }

    public static int showConfirmDialog(Component parent, Object message) throws HeadlessException {
        return showConfirmDialog(parent, message, Lang.t("オプションの選択"), YES_NO_CANCEL_OPTION);
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int optionType) throws HeadlessException {
        return showConfirmDialog(parent, message, title, optionType, QUESTION_MESSAGE);
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType) throws HeadlessException {
        return result(optionType, dialog(parent, message, title, messageType, buttons(optionType)));
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon) throws HeadlessException {
        return showConfirmDialog(parent, message, title, optionType, messageType);
    }

    public static int showOptionDialog(Component parent, Object message, String title, int optionType, int messageType,
                                       Icon icon, Object[] options, Object initialValue) throws HeadlessException {
        if (options == null || options.length == 0) return showConfirmDialog(parent, message, title, optionType, messageType);
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = String.valueOf(options[i]);
        return dialog(parent, message, title, messageType, labels);
    }

    public static String showInputDialog(Object message) throws HeadlessException { return showInputDialog(null, message, null); }
    public static String showInputDialog(Object message, Object initial) { return showInputDialog(null, message, initial); }
    public static String showInputDialog(Component parent, Object message) throws HeadlessException { return showInputDialog(parent, message, null); }

    public static String showInputDialog(Component parent, Object message, Object initial) {
        Object r = showInputDialog(parent, message, Lang.t("入力"), QUESTION_MESSAGE, null, null, initial);
        return r != null ? String.valueOf(r) : null;
    }

    public static String showInputDialog(Component parent, Object message, String title, int messageType) throws HeadlessException {
        Object r = showInputDialog(parent, message, title, messageType, null, null, null);
        return r != null ? String.valueOf(r) : null;
    }

    public static Object showInputDialog(Component parent, Object message, String title, int messageType, Icon icon,
                                         Object[] selectionValues, Object initialSelectionValue) throws HeadlessException {
        String m = text(message);
        if (selectionValues != null && selectionValues.length > 0) {
            String[] items = new String[selectionValues.length];
            int checked = -1;
            for (int i = 0; i < items.length; i++) {
                items[i] = String.valueOf(selectionValues[i]);
                if (selectionValues[i] != null && selectionValues[i].equals(initialSelectionValue)) checked = i;
            }
            final int c0 = checked;
            String t = title != null && !title.isEmpty() ? title : m;
            int i = JvmRuntime.blocking(() -> JvmRuntime.host().showChoice(t, items, c0), -1);
            return i >= 0 ? selectionValues[i] : null;
        }
        String init = initialSelectionValue != null ? String.valueOf(initialSelectionValue) : "";
        return JvmRuntime.blocking(() -> JvmRuntime.host().showInput(title, m, init, false, false), null);
    }

    public static void showInternalMessageDialog(Component parent, Object message) { showMessageDialog(parent, message); }
    public static void showInternalMessageDialog(Component parent, Object message, String title, int type) { showMessageDialog(parent, message, title, type); }
    public static int showInternalConfirmDialog(Component parent, Object message) { return showConfirmDialog(parent, message); }
    public static int showInternalConfirmDialog(Component parent, Object message, String title, int optionType) { return showConfirmDialog(parent, message, title, optionType); }
    public static String showInternalInputDialog(Component parent, Object message) { return showInputDialog(parent, message); }

    public static Frame getRootFrame() { return null; }
    public static void setRootFrame(Frame f) {}
    public static Frame getFrameForComponent(Component c) {
        Window w = c != null ? c.jvm$window() : null;
        return w instanceof Frame ? (Frame) w : null;
    }
}
