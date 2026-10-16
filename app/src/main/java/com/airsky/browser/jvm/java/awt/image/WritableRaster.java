package com.airsky.browser.jvm.java.awt.image;

public class WritableRaster extends Raster {
    WritableRaster(BufferedImage image) { super(image); }

    public void setPixel(int x, int y, int[] arr) {
        int a = getNumBands() > 3 ? arr[3] : 255;
        image.setRGB(x, y, (a << 24) | ((arr[0] & 0xFF) << 16) | ((arr[1] & 0xFF) << 8) | (arr[2] & 0xFF));
    }

    public void setPixels(int x, int y, int w, int h, int[] arr) {
        int bands = getNumBands();
        int[] px = new int[bands];
        int k = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                for (int b = 0; b < bands; b++) px[b] = arr[k++];
                setPixel(x + i, y + j, px);
            }
        }
    }

    public void setSample(int x, int y, int b, int s) {
        int[] px = getPixel(x, y, null);
        px[b] = s;
        setPixel(x, y, px);
    }

    public void setDataElements(int x, int y, Object in) {
        image.jvm$pixels()[y * width + x] = ((int[]) in)[0];
    }

    public void setDataElements(int x, int y, int w, int h, Object in) {
        int[] arr = (int[]) in;
        int[] p = image.jvm$pixels();
        for (int j = 0; j < h; j++) System.arraycopy(arr, j * w, p, (y + j) * width + x, w);
    }
}
