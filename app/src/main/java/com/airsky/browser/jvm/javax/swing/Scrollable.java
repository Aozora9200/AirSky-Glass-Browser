package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Rectangle;

public interface Scrollable {
    Dimension getPreferredScrollableViewportSize();
    int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction);
    int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction);
    boolean getScrollableTracksViewportWidth();
    boolean getScrollableTracksViewportHeight();
}
