package com.airsky.browser.jvm.javax.swing;

/** javax.swing.JPasswordField の互換クラス。 */
public class JPasswordField extends JTextField {
    private char echo = '•';

    public JPasswordField() { super(); }
    public JPasswordField(String text) { super(text); }
    public JPasswordField(int columns) { super(columns); }
    public JPasswordField(String text, int columns) { super(text, columns); }

    public char[] getPassword() { return getText().toCharArray(); }
    public char getEchoChar() { return echo; }
    public void setEchoChar(char c) { echo = c; repaint(); }
    public boolean echoCharIsSet() { return echo != 0; }

    @Override
    protected String jvm$displayText() {
        if (echo == 0) return getText();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getText().length(); i++) sb.append(echo);
        return sb.toString();
    }

    @Override protected boolean jvm$password() { return true; }
}
