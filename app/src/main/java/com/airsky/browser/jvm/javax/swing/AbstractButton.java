package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;
import com.airsky.browser.jvm.java.awt.ItemSelectable;
import com.airsky.browser.jvm.java.awt.event.ActionEvent;
import com.airsky.browser.jvm.java.awt.event.ActionListener;
import com.airsky.browser.jvm.java.awt.event.ItemEvent;
import com.airsky.browser.jvm.java.awt.event.ItemListener;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeListener;

import java.beans.PropertyChangeListener;
import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.AbstractButton の互換クラス。押すボタン・チェック・ラジオ・メニュー項目の見た目もここで描く。 */
public abstract class AbstractButton extends JComponent implements ItemSelectable, SwingConstants {
    public static final String TEXT_CHANGED_PROPERTY = "text";
    public static final String ICON_CHANGED_PROPERTY = "icon";

    static final int STYLE_PUSH = 0;
    static final int STYLE_CHECK = 1;
    static final int STYLE_RADIO = 2;
    static final int STYLE_MENU = 3;

    private String text = "";
    private Icon icon, pressedIcon, selectedIcon, rolloverIcon, disabledIcon;
    private String command;
    private boolean selected;
    private boolean pressed;
    private int hAlign = CENTER, vAlign = CENTER, hTextPos = TRAILING, vTextPos = CENTER;
    private int gap = 4;
    private int mnemonic;
    private boolean focusPainted = true, contentAreaFilled = true, borderPainted = true, rolloverEnabled;
    private Insets margin;
    private Action action;
    private PropertyChangeListener actionListenerForProps;
    ButtonGroup group;
    private final DefaultButtonModel model = new DefaultButtonModel();
    private final CopyOnWriteArrayList<ActionListener> actionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ItemListener> itemListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    protected AbstractButton() {
        model.button = this;
    }

    int jvm$style() { return STYLE_PUSH; }

    boolean jvm$toggles() { return false; }

    public boolean jvm$pressed() { return pressed; }

    public String getText() { return text; }

    public void setText(String t) {
        String old = text;
        text = t != null ? t : "";
        firePropertyChange(TEXT_CHANGED_PROPERTY, old, text);
        revalidate();
    }

    @Deprecated public String getLabel() { return getText(); }
    @Deprecated public void setLabel(String l) { setText(l); }
    public Icon getIcon() { return icon; }
    public void setIcon(Icon i) { icon = i; revalidate(); }
    public Icon getPressedIcon() { return pressedIcon; }
    public void setPressedIcon(Icon i) { pressedIcon = i; }
    public Icon getSelectedIcon() { return selectedIcon; }
    public void setSelectedIcon(Icon i) { selectedIcon = i; }
    public Icon getRolloverIcon() { return rolloverIcon; }
    public void setRolloverIcon(Icon i) { rolloverIcon = i; }
    public void setRolloverSelectedIcon(Icon i) {}
    public Icon getDisabledIcon() { return disabledIcon; }
    public void setDisabledIcon(Icon i) { disabledIcon = i; }
    public void setDisabledSelectedIcon(Icon i) {}
    public String getActionCommand() { return command != null ? command : text; }
    public void setActionCommand(String c) { command = c; }
    public ButtonModel getModel() { return model; }
    public void setModel(ButtonModel m) {}
    public int getHorizontalAlignment() { return hAlign; }
    public void setHorizontalAlignment(int a) { hAlign = a; repaint(); }
    public int getVerticalAlignment() { return vAlign; }
    public void setVerticalAlignment(int a) { vAlign = a; repaint(); }
    public int getHorizontalTextPosition() { return hTextPos; }
    public void setHorizontalTextPosition(int p) { hTextPos = p; repaint(); }
    public int getVerticalTextPosition() { return vTextPos; }
    public void setVerticalTextPosition(int p) { vTextPos = p; repaint(); }
    public int getIconTextGap() { return gap; }
    public void setIconTextGap(int g) { gap = g; revalidate(); }
    public int getMnemonic() { return mnemonic; }
    public void setMnemonic(int m) { mnemonic = m; }
    public void setMnemonic(char m) { mnemonic = m; }
    public void setDisplayedMnemonicIndex(int i) {}
    public boolean isFocusPainted() { return focusPainted; }
    public void setFocusPainted(boolean b) { focusPainted = b; }
    public boolean isContentAreaFilled() { return contentAreaFilled; }
    public void setContentAreaFilled(boolean b) { contentAreaFilled = b; repaint(); }
    public boolean isBorderPainted() { return borderPainted; }
    public void setBorderPainted(boolean b) { borderPainted = b; repaint(); }
    public boolean isRolloverEnabled() { return rolloverEnabled; }
    public void setRolloverEnabled(boolean b) { rolloverEnabled = b; }
    public Insets getMargin() { return margin != null ? (Insets) margin.clone() : new Insets(2, 14, 2, 14); }
    public void setMargin(Insets m) { margin = m; revalidate(); }
    public void setMultiClickThreshhold(long t) {}
    public void setHideActionText(boolean b) {}

    public boolean isSelected() { return selected; }

    public void setSelected(boolean b) {
        if (selected == b) return;
        if (b && group != null) group.jvm$select(this);
        selected = b;
        repaint();
        fireStateChanged();
        fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, this,
                b ? ItemEvent.SELECTED : ItemEvent.DESELECTED));
    }

    void jvm$setSelectedQuietly(boolean b) {
        if (selected == b) return;
        selected = b;
        repaint();
        fireStateChanged();
        fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, this,
                b ? ItemEvent.SELECTED : ItemEvent.DESELECTED));
    }

    @Override
    public Object[] getSelectedObjects() { return selected ? new Object[]{text} : null; }

    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public void addItemListener(ItemListener l) { if (l != null) itemListeners.add(l); }
    public void removeItemListener(ItemListener l) { itemListeners.remove(l); }
    public ItemListener[] getItemListeners() { return itemListeners.toArray(new ItemListener[0]); }
    public void addChangeListener(ChangeListener l) { if (l != null) changeListeners.add(l); }
    public void removeChangeListener(ChangeListener l) { changeListeners.remove(l); }

    protected void fireActionPerformed(ActionEvent e) {
        for (ActionListener l : actionListeners) l.actionPerformed(e);
    }

    protected void fireItemStateChanged(ItemEvent e) {
        for (ItemListener l : itemListeners) l.itemStateChanged(e);
    }

    protected void fireStateChanged() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : changeListeners) l.stateChanged(e);
    }

    public Action getAction() { return action; }

    public void setAction(Action a) {
        if (action != null) {
            actionListeners.remove(action);
            if (actionListenerForProps != null) action.removePropertyChangeListener(actionListenerForProps);
        }
        action = a;
        if (a != null) {
            Object name = a.getValue(Action.NAME);
            if (name != null) setText(String.valueOf(name));
            Object ic = a.getValue(Action.SMALL_ICON);
            if (ic instanceof Icon) setIcon((Icon) ic);
            Object cmd = a.getValue(Action.ACTION_COMMAND_KEY);
            if (cmd != null) setActionCommand(String.valueOf(cmd));
            Object tip = a.getValue(Action.SHORT_DESCRIPTION);
            if (tip != null) setToolTipText(String.valueOf(tip));
            setEnabled(a.isEnabled());
            actionListeners.add(a);
            actionListenerForProps = e -> {
                if ("enabled".equals(e.getPropertyName())) setEnabled(a.isEnabled());
                else if (Action.NAME.equals(e.getPropertyName()) && e.getNewValue() != null) setText(String.valueOf(e.getNewValue()));
            };
            a.addPropertyChangeListener(actionListenerForProps);
        }
    }

    public void doClick() { doClick(0); }

    public void doClick(int pressTime) {
        if (!isEnabled()) return;
        jvm$clicked(System.currentTimeMillis(), 0);
    }

    /** 押された(クリック)。切り替えるボタンは選択を変えてから、アクションを知らせる。 */
    void jvm$clicked(long when, int modifiers) {
        if (jvm$toggles()) {
            if (group != null && jvm$style() == STYLE_RADIO) {
                if (!selected) setSelected(true);
            } else if (group != null && selected && group.jvm$requireSelection()) {
                // グループの中で選択中のものは、押しても外れない
            } else {
                setSelected(!selected);
            }
        }
        fireActionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand(), when, modifiers));
    }

    @Override
    public boolean jvm$wantsMouse() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (!isEnabled()) return;
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED: pressed = true; repaint(); break;
            case MouseEvent.MOUSE_RELEASED: pressed = false; repaint(); break;
            case MouseEvent.MOUSE_CLICKED: jvm$clicked(e.getWhen(), e.getModifiers()); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------
    // 大きさと見た目
    // ------------------------------------------------------------------

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        int tw = text.isEmpty() ? 0 : fm.stringWidth(JvmLook.plain(text));
        int iw = icon != null ? icon.getIconWidth() : 0, ih = icon != null ? icon.getIconHeight() : 0;
        int content = tw + iw + (tw > 0 && iw > 0 ? gap : 0);
        int h = Math.max(fm.getHeight(), ih);
        Insets in = getBorder() != null ? getInsets() : new Insets(0, 0, 0, 0);
        int w;
        switch (jvm$style()) {
            case STYLE_CHECK:
            case STYLE_RADIO:
                w = content + 26;
                h = Math.max(h, 18) + 8;
                break;
            case STYLE_MENU:
                w = content + 40;
                h += 16;
                break;
            default: {
                Insets m = getMargin();
                w = content + m.left + m.right + 6;
                h += m.top + m.bottom + 12;
                break;
            }
        }
        return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
    }

    @Override public Dimension getMinimumSize() { return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        JvmLook.smooth(g);
        int w = getWidth(), h = getHeight();
        boolean en = isEnabled();
        Color fgSet = isForegroundSet() ? getForeground() : null;
        Color text0 = en ? (fgSet != null ? fgSet : JvmLook.TEXT) : JvmLook.DISABLED;
        int style = jvm$style();
        int left = 0;
        if (style == STYLE_PUSH) {
            boolean selectedLook = jvm$toggles() && selected;
            if (contentAreaFilled) {
                Color bg = isBackgroundSet() && !(getBackground() instanceof com.airsky.browser.jvm.java.awt.SystemColor)
                        ? getBackground() : (selectedLook ? JvmLook.PRIMARY : JvmLook.TONAL);
                if (pressed) bg = isBackgroundSet() ? bg.darker() : JvmLook.PRESSED;
                if (!en) bg = new Color(0xE3, 0xE3, 0xE3);
                g.setColor(bg);
                int arc = Math.min(h, 36);
                g.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);
            } else if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, w, h);
            }
            text0 = en ? (fgSet != null ? fgSet : (selectedLook && contentAreaFilled ? JvmLook.ON_PRIMARY : JvmLook.ON_TONAL)) : JvmLook.DISABLED;
            paintLabel(g, text0, 0, w);
            return;
        }
        if (isOpaque() || pressed) {
            g.setColor(pressed ? new Color(0xE8, 0xEA, 0xED) : getBackground());
            g.fillRect(0, 0, w, h);
        }
        if (style == STYLE_CHECK || style == STYLE_RADIO) {
            int s = 16, top = (h - s) / 2;
            if (style == STYLE_RADIO) {
                g.setColor(en ? (selected ? JvmLook.PRIMARY : JvmLook.OUTLINE) : JvmLook.DISABLED);
                g.drawOval(3, top, s, s);
                if (selected) g.fillOval(7, top + 4, s - 8, s - 8);
            } else if (selected) {
                g.setColor(en ? JvmLook.PRIMARY : JvmLook.DISABLED);
                g.fillRoundRect(3, top, s, s, 5, 5);
                g.setColor(Color.WHITE);
                g.drawLine(6, top + 8, 9, top + 11);
                g.drawLine(9, top + 11, 15, top + 5);
            } else {
                g.setColor(en ? JvmLook.OUTLINE : JvmLook.DISABLED);
                g.drawRoundRect(3, top, s, s, 5, 5);
            }
            left = 26;
            paintLabel(g, text0, left, w - left);
            return;
        }
        // メニュー項目
        if (this instanceof JMenu && ((JMenu) this).isTopLevelMenu()) {
            g.setColor(text0);
            g.setFont(getFont());
            JvmLook.text(g, JvmLook.plain(text), 0, 0, w, h, 1);
            return;
        }
        if (selected) {
            g.setColor(text0);
            g.drawString("✓", 10, (h + g.getFontMetrics().getAscent()) / 2 - 2);
        }
        paintLabel(g, text0, 28, w - 28);
    }

    private void paintLabel(Graphics g, Color color, int x0, int width) {
        Icon ic = icon;
        if (!isEnabled() && disabledIcon != null) ic = disabledIcon;
        else if (pressed && pressedIcon != null) ic = pressedIcon;
        else if (selected && selectedIcon != null) ic = selectedIcon;
        FontMetrics fm = g.getFontMetrics(getFont());
        String t = JvmLook.plain(text);
        int tw = t.isEmpty() ? 0 : fm.stringWidth(t);
        int iw = ic != null ? ic.getIconWidth() : 0;
        int content = tw + iw + (tw > 0 && iw > 0 ? gap : 0);
        int h = getHeight();
        int x;
        boolean push = jvm$style() == STYLE_PUSH;
        if (push && (hAlign == CENTER)) x = x0 + (width - content) / 2;
        else if (hAlign == RIGHT || hAlign == TRAILING) x = x0 + width - content - 8;
        else x = x0 + (push ? 12 : 0);
        if (ic != null) {
            boolean iconRight = hTextPos == LEFT || hTextPos == LEADING;
            int ix = iconRight ? x + tw + (tw > 0 ? gap : 0) : x;
            ic.paintIcon(this, g, ix, (h - ic.getIconHeight()) / 2);
            if (!iconRight) x += iw + (tw > 0 ? gap : 0);
        }
        if (tw > 0) {
            g.setColor(color);
            g.setFont(getFont());
            g.drawString(t, x, (h - fm.getHeight()) / 2 + fm.getAscent());
        }
    }
}
