package com.airsky.browser.jvm.java.awt.datatransfer;

public class StringSelection implements Transferable, ClipboardOwner {
    private final String data;

    public StringSelection(String data) { this.data = data; }

    @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.stringFlavor}; }
    @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.stringFlavor.equals(f); }

    @Override
    public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
        return data;
    }

    @Override public void lostOwnership(Clipboard c, Transferable t) {}
}
