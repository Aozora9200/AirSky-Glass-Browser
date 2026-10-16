package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmFiles;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Image;
import com.airsky.browser.jvm.java.awt.MediaTracker;
import com.airsky.browser.jvm.java.awt.Toolkit;
import com.airsky.browser.jvm.java.awt.image.ImageObserver;

/** javax.swing.ImageIcon の互換クラス。 */
public class ImageIcon implements Icon, java.io.Serializable {
    private Image image;
    private String description;

    public ImageIcon() {}
    public ImageIcon(String filename) { this(filename, filename); }

    public ImageIcon(String filename, String description) {
        image = Toolkit.getDefaultToolkit().getImage(JvmFiles.resolve(filename));
        this.description = description;
    }

    public ImageIcon(java.net.URL url) { this(url, url != null ? url.toString() : null); }

    public ImageIcon(java.net.URL url, String description) {
        image = url != null ? Toolkit.getDefaultToolkit().getImage(url) : null;
        this.description = description;
    }

    public ImageIcon(Image image) { this(image, null); }

    public ImageIcon(Image image, String description) {
        this.image = image;
        this.description = description;
    }

    public ImageIcon(byte[] data) { this(data, null); }

    public ImageIcon(byte[] data, String description) {
        image = Toolkit.getDefaultToolkit().createImage(data);
        this.description = description;
    }

    public Image getImage() { return image; }
    public void setImage(Image i) { image = i; }
    public String getDescription() { return description; }
    public void setDescription(String d) { description = d; }
    public int getImageLoadStatus() { return MediaTracker.COMPLETE; }
    public void setImageObserver(ImageObserver o) {}
    public ImageObserver getImageObserver() { return null; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (image != null) g.drawImage(image, x, y, c);
    }

    @Override public int getIconWidth() { return image != null ? image.getWidth(null) : -1; }
    @Override public int getIconHeight() { return image != null ? image.getHeight(null) : -1; }
    @Override public String toString() { return description != null ? description : super.toString(); }
}
