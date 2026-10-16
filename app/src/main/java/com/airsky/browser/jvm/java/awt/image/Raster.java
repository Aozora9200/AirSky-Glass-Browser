package com.airsky.browser.jvm.java.awt.image;

/** java.awt.image.Raster の互換クラス(BufferedImage の画素の配列を見る)。 */
public class Raster {
    final BufferedImage image;
    protected int width, height;

    Raster(BufferedImage image) {
        this.image = image;
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getMinX() { return 0; }
    public int getMinY() { return 0; }
    public int getNumBands() { return image.jvm$opaque() ? 3 : 4; }
    public int getNumDataElements() { return 1; }
    public DataBuffer getDataBuffer() { return new DataBufferInt(image.jvm$pixels(), width * height); }

    public int[] getPixel(int x, int y, int[] arr) {
        int bands = getNumBands();
        if (arr == null) arr = new int[bands];
        int v = image.getRGB(x, y);
        arr[0] = (v >> 16) & 0xFF;
        arr[1] = (v >> 8) & 0xFF;
        arr[2] = v & 0xFF;
        if (bands > 3) arr[3] = (v >>> 24) & 0xFF;
        return arr;
    }

    public int[] getPixels(int x, int y, int w, int h, int[] arr) {
        int bands = getNumBands();
        if (arr == null) arr = new int[w * h * bands];
        int k = 0;
        int[] px = new int[bands];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                getPixel(x + i, y + j, px);
                for (int b = 0; b < bands; b++) arr[k++] = px[b];
            }
        }
        return arr;
    }

    public int getSample(int x, int y, int b) {
        return getPixel(x, y, null)[b];
    }

    public Object getDataElements(int x, int y, Object out) {
        int[] arr = out instanceof int[] ? (int[]) out : new int[1];
        arr[0] = image.jvm$pixels()[y * width + x];
        return arr;
    }

    public Object getDataElements(int x, int y, int w, int h, Object out) {
        int[] arr = out instanceof int[] ? (int[]) out : new int[w * h];
        int[] p = image.jvm$pixels();
        for (int j = 0; j < h; j++) System.arraycopy(p, (y + j) * width + x, arr, j * w, w);
        return arr;
    }
}
