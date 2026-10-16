package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.Component;

public class KeyEvent extends InputEvent {
    public static final int KEY_FIRST = 400;
    public static final int KEY_LAST = 402;
    public static final int KEY_TYPED = 400;
    public static final int KEY_PRESSED = 401;
    public static final int KEY_RELEASED = 402;
    public static final int VK_ENTER = '\n';
    public static final int VK_BACK_SPACE = '\b';
    public static final int VK_TAB = '\t';
    public static final int VK_CANCEL = 0x03;
    public static final int VK_CLEAR = 0x0C;
    public static final int VK_SHIFT = 0x10;
    public static final int VK_CONTROL = 0x11;
    public static final int VK_ALT = 0x12;
    public static final int VK_PAUSE = 0x13;
    public static final int VK_CAPS_LOCK = 0x14;
    public static final int VK_ESCAPE = 0x1B;
    public static final int VK_SPACE = 0x20;
    public static final int VK_PAGE_UP = 0x21;
    public static final int VK_PAGE_DOWN = 0x22;
    public static final int VK_END = 0x23;
    public static final int VK_HOME = 0x24;
    public static final int VK_LEFT = 0x25;
    public static final int VK_UP = 0x26;
    public static final int VK_RIGHT = 0x27;
    public static final int VK_DOWN = 0x28;
    public static final int VK_COMMA = 0x2C;
    public static final int VK_MINUS = 0x2D;
    public static final int VK_PERIOD = 0x2E;
    public static final int VK_SLASH = 0x2F;
    public static final int VK_0 = 0x30;
    public static final int VK_1 = 0x31;
    public static final int VK_2 = 0x32;
    public static final int VK_3 = 0x33;
    public static final int VK_4 = 0x34;
    public static final int VK_5 = 0x35;
    public static final int VK_6 = 0x36;
    public static final int VK_7 = 0x37;
    public static final int VK_8 = 0x38;
    public static final int VK_9 = 0x39;
    public static final int VK_SEMICOLON = 0x3B;
    public static final int VK_EQUALS = 0x3D;
    public static final int VK_A = 0x41;
    public static final int VK_B = 0x42;
    public static final int VK_C = 0x43;
    public static final int VK_D = 0x44;
    public static final int VK_E = 0x45;
    public static final int VK_F = 0x46;
    public static final int VK_G = 0x47;
    public static final int VK_H = 0x48;
    public static final int VK_I = 0x49;
    public static final int VK_J = 0x4A;
    public static final int VK_K = 0x4B;
    public static final int VK_L = 0x4C;
    public static final int VK_M = 0x4D;
    public static final int VK_N = 0x4E;
    public static final int VK_O = 0x4F;
    public static final int VK_P = 0x50;
    public static final int VK_Q = 0x51;
    public static final int VK_R = 0x52;
    public static final int VK_S = 0x53;
    public static final int VK_T = 0x54;
    public static final int VK_U = 0x55;
    public static final int VK_V = 0x56;
    public static final int VK_W = 0x57;
    public static final int VK_X = 0x58;
    public static final int VK_Y = 0x59;
    public static final int VK_Z = 0x5A;
    public static final int VK_OPEN_BRACKET = 0x5B;
    public static final int VK_BACK_SLASH = 0x5C;
    public static final int VK_CLOSE_BRACKET = 0x5D;
    public static final int VK_NUMPAD0 = 0x60;
    public static final int VK_NUMPAD1 = 0x61;
    public static final int VK_NUMPAD2 = 0x62;
    public static final int VK_NUMPAD3 = 0x63;
    public static final int VK_NUMPAD4 = 0x64;
    public static final int VK_NUMPAD5 = 0x65;
    public static final int VK_NUMPAD6 = 0x66;
    public static final int VK_NUMPAD7 = 0x67;
    public static final int VK_NUMPAD8 = 0x68;
    public static final int VK_NUMPAD9 = 0x69;
    public static final int VK_MULTIPLY = 0x6A;
    public static final int VK_ADD = 0x6B;
    public static final int VK_SEPARATOR = 0x6C;
    public static final int VK_SUBTRACT = 0x6D;
    public static final int VK_DECIMAL = 0x6E;
    public static final int VK_DIVIDE = 0x6F;
    public static final int VK_DELETE = 0x7F;
    public static final int VK_F1 = 0x70;
    public static final int VK_F2 = 0x71;
    public static final int VK_F3 = 0x72;
    public static final int VK_F4 = 0x73;
    public static final int VK_F5 = 0x74;
    public static final int VK_F6 = 0x75;
    public static final int VK_F7 = 0x76;
    public static final int VK_F8 = 0x77;
    public static final int VK_F9 = 0x78;
    public static final int VK_F10 = 0x79;
    public static final int VK_F11 = 0x7A;
    public static final int VK_F12 = 0x7B;
    public static final int VK_INSERT = 0x9B;
    public static final int VK_META = 0x9D;
    public static final int VK_BACK_QUOTE = 0xC0;
    public static final int VK_QUOTE = 0xDE;
    public static final int VK_KP_UP = 0xE0;
    public static final int VK_KP_DOWN = 0xE1;
    public static final int VK_KP_LEFT = 0xE2;
    public static final int VK_KP_RIGHT = 0xE3;
    public static final int VK_UNDEFINED = 0x0;
    public static final char CHAR_UNDEFINED = 0xFFFF;
    public static final int KEY_LOCATION_UNKNOWN = 0;
    public static final int KEY_LOCATION_STANDARD = 1;
    public static final int KEY_LOCATION_LEFT = 2;
    public static final int KEY_LOCATION_RIGHT = 3;
    public static final int KEY_LOCATION_NUMPAD = 4;

    int keyCode;
    char keyChar;
    int keyLocation;

    public KeyEvent(Component source, int id, long when, int modifiers, int keyCode, char keyChar) {
        this(source, id, when, modifiers, keyCode, keyChar, id == KEY_TYPED ? KEY_LOCATION_UNKNOWN : KEY_LOCATION_STANDARD);
    }

    public KeyEvent(Component source, int id, long when, int modifiers, int keyCode, char keyChar, int keyLocation) {
        super(source, id, when, modifiers);
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.keyLocation = keyLocation;
    }

    @Deprecated
    public KeyEvent(Component source, int id, long when, int modifiers, int keyCode) {
        this(source, id, when, modifiers, keyCode, (char) keyCode);
    }

    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int k) { keyCode = k; }
    public char getKeyChar() { return keyChar; }
    public void setKeyChar(char c) { keyChar = c; }
    public int getKeyLocation() { return keyLocation; }
    public int getExtendedKeyCode() { return keyCode; }

    public boolean isActionKey() {
        switch (keyCode) {
            case VK_HOME: case VK_END: case VK_PAGE_UP: case VK_PAGE_DOWN: case VK_UP: case VK_DOWN:
            case VK_LEFT: case VK_RIGHT: case VK_F1: case VK_F2: case VK_F3: case VK_F4: case VK_F5:
            case VK_F6: case VK_F7: case VK_F8: case VK_F9: case VK_F10: case VK_F11: case VK_F12:
            case VK_INSERT: case VK_CAPS_LOCK: case VK_PAUSE:
                return true;
            default:
                return false;
        }
    }

    public static int getExtendedKeyCodeForChar(int c) {
        char u = Character.toUpperCase((char) c);
        if ((u >= 'A' && u <= 'Z') || (u >= '0' && u <= '9')) return u;
        return VK_UNDEFINED;
    }

    public static String getKeyText(int keyCode) {
        if (keyCode >= VK_0 && keyCode <= VK_9 || keyCode >= VK_A && keyCode <= VK_Z) return String.valueOf((char) keyCode);
        switch (keyCode) {
            case VK_ENTER: return "Enter";
            case VK_BACK_SPACE: return "Backspace";
            case VK_TAB: return "Tab";
            case VK_SHIFT: return "Shift";
            case VK_CONTROL: return "Ctrl";
            case VK_ALT: return "Alt";
            case VK_ESCAPE: return "Escape";
            case VK_SPACE: return "Space";
            case VK_LEFT: return "Left";
            case VK_UP: return "Up";
            case VK_RIGHT: return "Right";
            case VK_DOWN: return "Down";
            case VK_DELETE: return "Delete";
            case VK_HOME: return "Home";
            case VK_END: return "End";
            case VK_PAGE_UP: return "Page Up";
            case VK_PAGE_DOWN: return "Page Down";
            default:
                if (keyCode >= VK_F1 && keyCode <= VK_F12) return "F" + (keyCode - VK_F1 + 1);
                return "Unknown keyCode: 0x" + Integer.toHexString(keyCode);
        }
    }

    @Override
    public String paramString() { return "id=" + id + ",keyCode=" + keyCode + ",keyChar='" + keyChar + "'"; }
}
