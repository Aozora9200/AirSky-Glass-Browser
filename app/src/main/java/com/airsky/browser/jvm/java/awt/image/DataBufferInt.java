package com.airsky.browser.jvm.java.awt.image;

/** 画像の画素(int の配列)。この配列に書いた画素は、画像を描く時に反映される。 */
public final class DataBufferInt extends DataBuffer {
    private final int[] data;

    public DataBufferInt(int size) { this(new int[size], size); }

    public DataBufferInt(int[] data, int size) {
        super(TYPE_INT, size);
        this.data = data;
    }

    public int[] getData() { return data; }
    public int[] getData(int bank) { return data; }
    public int[][] getBankData() { return new int[][]{data}; }
    @Override public int getElem(int bank, int i) { return data[i]; }
    @Override public void setElem(int bank, int i, int val) { data[i] = val; }
}
