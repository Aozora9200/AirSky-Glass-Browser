package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.Lang;

import com.airsky.browser.jvm.JvmFiles;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.HeadlessException;
import com.airsky.browser.jvm.javax.swing.filechooser.FileFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * javax.swing.JFileChooser の互換クラス。プログラムの作業フォルダの中から、一覧で選ぶ・名前を入れる。
 */
public class JFileChooser extends JComponent {
    public static final int OPEN_DIALOG = 0;
    public static final int SAVE_DIALOG = 1;
    public static final int CUSTOM_DIALOG = 2;
    public static final int CANCEL_OPTION = 1;
    public static final int APPROVE_OPTION = 0;
    public static final int ERROR_OPTION = -1;
    public static final int FILES_ONLY = 0;
    public static final int DIRECTORIES_ONLY = 1;
    public static final int FILES_AND_DIRECTORIES = 2;

    private File dir;
    private File selected;
    private File[] selectedFiles = new File[0];
    private String title;
    private int mode = FILES_ONLY;
    private boolean multi, acceptAll = true;
    private FileFilter filter;
    private final List<FileFilter> filters = new ArrayList<>();

    public JFileChooser() { this((File) null); }
    public JFileChooser(String path) { this(path != null ? new File(JvmFiles.resolve(path)) : null); }
    public JFileChooser(File dir) { this.dir = dir != null ? dir : JvmFiles.home(); }

    public File getCurrentDirectory() { return dir; }
    public void setCurrentDirectory(File d) { dir = d != null ? d : JvmFiles.home(); }
    public void changeToParentDirectory() { File p = dir.getParentFile(); if (p != null) dir = p; }
    public File getSelectedFile() { return selected; }
    public void setSelectedFile(File f) { selected = f; }
    public File[] getSelectedFiles() { return selectedFiles.clone(); }
    public void setSelectedFiles(File[] f) { selectedFiles = f != null ? f.clone() : new File[0]; }
    public void setDialogTitle(String t) { title = t; }
    public String getDialogTitle() { return title; }
    public void setFileSelectionMode(int m) { mode = m; }
    public int getFileSelectionMode() { return mode; }
    public void setMultiSelectionEnabled(boolean b) { multi = b; }
    public boolean isMultiSelectionEnabled() { return multi; }
    public void setAcceptAllFileFilterUsed(boolean b) { acceptAll = b; }
    public void setFileFilter(FileFilter f) { filter = f; if (f != null && !filters.contains(f)) filters.add(f); }
    public FileFilter getFileFilter() { return filter; }
    public void addChoosableFileFilter(FileFilter f) { if (f != null) { filters.add(f); if (filter == null) filter = f; } }
    public FileFilter[] getChoosableFileFilters() { return filters.toArray(new FileFilter[0]); }
    public void setFileHidingEnabled(boolean b) {}
    public void setDialogType(int t) {}
    public void setApproveButtonText(String t) {}
    public void rescanCurrentDirectory() {}

    public int showOpenDialog(Component parent) throws HeadlessException {
        File d = dir;
        while (true) {
            File[] list = d.listFiles();
            List<File> files = new ArrayList<>();
            if (list != null) {
                Arrays.sort(list, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
                for (File f : list) {
                    if (f.isHidden()) continue;
                    if (f.isDirectory() || mode != DIRECTORIES_ONLY && (filter == null || filter.accept(f))) files.add(f);
                }
            }
            List<String> labels = new ArrayList<>();
            boolean canUp = !d.equals(JvmFiles.home()) && d.getParentFile() != null;
            if (canUp) labels.add(Lang.t("⬑ 上のフォルダ"));
            if (mode != FILES_ONLY) labels.add(Lang.t("✓ このフォルダを選ぶ"));
            for (File f : files) labels.add(f.isDirectory() ? "📁 " + f.getName() : f.getName());
            if (labels.isEmpty()) {
                String where = d.getPath();
                JvmRuntime.blocking(() -> JvmRuntime.host().showDialog(title != null ? title : Lang.t("開く"),
                        Lang.t("このフォルダにはファイルがありません。\n") + where, JOptionPane.INFORMATION_MESSAGE, new String[]{"OK"}), -1);
                return CANCEL_OPTION;
            }
            String[] arr = labels.toArray(new String[0]);
            String t = title != null ? title : Lang.t("開く: ") + d.getName();
            int i = JvmRuntime.blocking(() -> JvmRuntime.host().showChoice(t, arr, -1), -1);
            if (i < 0) return CANCEL_OPTION;
            if (canUp) {
                if (i == 0) {
                    d = d.getParentFile();
                    continue;
                }
                i--;
            }
            if (mode != FILES_ONLY) {
                if (i == 0) {
                    return approve(d);
                }
                i--;
            }
            File f = files.get(i);
            if (f.isDirectory()) {
                d = f;
                continue;
            }
            return approve(f);
        }
    }

    private int approve(File f) {
        selected = f;
        selectedFiles = new File[]{f};
        dir = f.isDirectory() ? f : f.getParentFile();
        return APPROVE_OPTION;
    }

    public int showSaveDialog(Component parent) throws HeadlessException {
        String init = selected != null ? selected.getName() : "";
        String where = dir.getPath();
        String name = JvmRuntime.blocking(() -> JvmRuntime.host().showInput(title != null ? title : Lang.t("保存"),
                Lang.t("保存先: ") + where, init, false, false), null);
        if (name == null || name.trim().isEmpty()) return CANCEL_OPTION;
        File f = name.startsWith("/") ? new File(name) : new File(dir, name.trim());
        return approve(f);
    }

    public int showDialog(Component parent, String approveText) throws HeadlessException { return showOpenDialog(parent); }
}
