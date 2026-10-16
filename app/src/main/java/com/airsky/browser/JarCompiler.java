package com.airsky.browser;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.origin.Origin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * JAR を Android で動く DEX に変換する(JAR 実行ツール)。
 *
 * <p>1. ASM で各クラスを読み、java.awt / javax.swing / javax.imageio の参照を互換レイヤー
 * (com.airsky.browser.jvm.…)へ付け替える。System.exit はプログラムだけを終わらせる呼び出しに、
 * 相対パスのファイルはプログラムの作業フォルダからの位置になるように書き換える。<br>
 * 2. D8 で DEX にし、JAR の中のリソース(画像など)と一緒に zip にまとめる。</p>
 *
 * <p>結果は JAR ごとにキャッシュし、JAR が変わらなければ次からは変換しない。</p>
 */
final class JarCompiler {

    /** 変換のしかたを変えたら上げる(古いキャッシュを使わない)。 */
    private static final int VERSION = 2;
    static final String SHIM = "com/airsky/browser/jvm/";
    private static final String FILES = SHIM + "JvmFiles";
    private static final String RESOLVE = "(Ljava/lang/String;)Ljava/lang/String;";

    interface Progress {
        void onProgress(String message);
    }

    static final class Result {
        final File dex;
        @Nullable final String manifestMain;
        final List<String> mains;
        /** JAR のクラスが作られた Java の版(8, 11, 17 …)。プログラムに見せる java.version に使う */
        final int javaFeature;

        Result(File dex, @Nullable String manifestMain, List<String> mains, int javaFeature) {
            this.dex = dex;
            this.manifestMain = manifestMain;
            this.mains = mains;
            this.javaFeature = javaFeature;
        }
    }

    private JarCompiler() {}

    @NonNull
    static Result compile(@NonNull File jar, @NonNull File outDir, @NonNull Progress progress) throws IOException {
        if (!outDir.isDirectory() && !outDir.mkdirs()) throw new IOException(Lang.t("作業フォルダを作れません"));
        File out = new File(outDir, "app.zip");
        File stampFile = new File(outDir, "stamp");
        File mainsFile = new File(outDir, "mains.txt");
        File featureFile = new File(outDir, "java_version");
        String stamp = VERSION + ":" + jar.length() + ":" + jar.lastModified() + ":" + Build.VERSION.SDK_INT;
        if (out.isFile() && stamp.equals(readText(stampFile)) && mainsFile.isFile()) {
            List<String> lines = readLines(mainsFile);
            String manifestMain = lines.isEmpty() || lines.get(0).isEmpty() ? null : lines.get(0);
            int feature = 8;
            try {
                feature = Integer.parseInt(String.valueOf(readText(featureFile)).trim());
            } catch (NumberFormatException ignored) {
            }
            return new Result(out, manifestMain, lines.size() > 1 ? new ArrayList<>(lines.subList(1, lines.size())) : new ArrayList<>(), feature);
        }

        progress.onProgress(Lang.t("JAR を読み込んでいます…"));
        List<byte[]> classes = new ArrayList<>();
        Map<String, byte[]> resources = new TreeMap<>();
        List<String> mains = new ArrayList<>();
        String manifestMain = null;
        int count = 0;
        int[] maxMajor = {52};
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                byte[] data = readAll(zin);
                if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    try {
                        Manifest m = new Manifest(new java.io.ByteArrayInputStream(data));
                        String mc = m.getMainAttributes().getValue("Main-Class");
                        if (mc != null && !mc.trim().isEmpty()) manifestMain = mc.trim();
                    } catch (IOException ignored) {
                    }
                    resources.put(name, data);
                } else if (name.endsWith(".class")) {
                    if (name.startsWith("META-INF/") || name.endsWith("module-info.class")
                            || name.startsWith("java/") || name.equals("package-info.class")) {
                        continue;
                    }
                    classes.add(rewrite(data, mains, maxMajor));
                    if (++count % 200 == 0) progress.onProgress(Lang.t("クラスを書き換えています… (") + count + ")");
                } else if (!isSignature(name)) {
                    resources.put(name, data);
                }
            }
        }
        if (classes.isEmpty()) throw new IOException(Lang.t("この JAR には Java のクラスが入っていません"));

        progress.onProgress(Lang.t("Android 用に変換しています… (") + classes.size() + Lang.t(" クラス)"));
        Map<Integer, byte[]> dexes = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        DiagnosticsHandler handler = new DiagnosticsHandler() {
            @Override
            public void error(Diagnostic d) {
                synchronized (errors) {
                    errors.add(d.getDiagnosticMessage());
                }
            }

            @Override public void warning(Diagnostic d) {}
            @Override public void info(Diagnostic d) {}
        };
        D8Command.Builder b = D8Command.builder(handler)
                .setMinApiLevel(Math.min(Math.max(Build.VERSION.SDK_INT, 26), 35))
                .setMode(CompilationMode.RELEASE)
                .setProgramConsumer(new DexIndexedConsumer() {
                    @Override
                    public synchronized void accept(int fileIndex, ByteDataView data, Set<String> descriptors,
                                                    DiagnosticsHandler h) {
                        dexes.put(fileIndex, data.copyByteData());
                    }

                    @Override public void finished(DiagnosticsHandler h) {}
                });
        for (byte[] c : classes) b.addClassProgramData(c, Origin.unknown());
        try {
            D8.run(b.build());
        } catch (Exception e) {
            String msg = errors.isEmpty() ? String.valueOf(e.getMessage()) : String.join("\n", errors);
            throw new IOException(Lang.t("変換できませんでした:\n") + msg, e);
        }
        if (dexes.isEmpty()) throw new IOException(Lang.t("変換の結果が空でした"));

        progress.onProgress(Lang.t("まとめています…"));
        File tmp = new File(outDir, "app.zip.tmp");
        try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(tmp))) {
            for (Map.Entry<Integer, byte[]> d : dexes.entrySet()) {
                int i = d.getKey();
                zout.putNextEntry(new ZipEntry(i == 0 ? "classes.dex" : "classes" + (i + 1) + ".dex"));
                zout.write(d.getValue());
                zout.closeEntry();
            }
            for (Map.Entry<String, byte[]> r : resources.entrySet()) {
                zout.putNextEntry(new ZipEntry(r.getKey()));
                zout.write(r.getValue());
                zout.closeEntry();
            }
        }
        if (out.exists()) {
            out.setWritable(true);
            out.delete();
        }
        if (!tmp.renameTo(out)) throw new IOException(Lang.t("変換した結果を保存できません"));
        // Android 14 からは、読み込む DEX を書き込みできないようにする必要がある
        out.setReadOnly();

        StringBuilder sb = new StringBuilder(manifestMain != null ? manifestMain : "").append('\n');
        for (String m : mains) sb.append(m).append('\n');
        writeText(mainsFile, sb.toString());
        int feature = Math.max(8, Math.min(maxMajor[0], 65) - 44);
        writeText(featureFile, String.valueOf(feature));
        writeText(stampFile, stamp);
        return new Result(out, manifestMain, mains, feature);
    }

    private static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/")) return false;
        String n = name.toUpperCase(java.util.Locale.ROOT);
        return n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC");
    }

    // ------------------------------------------------------------------
    // クラスの書き換え
    // ------------------------------------------------------------------

    /** AWT / Swing の参照を互換レイヤーへ付け替える。 */
    private static final Remapper REMAPPER = new Remapper() {
        @Override
        public String map(String internalName) {
            if (internalName.startsWith("java/awt/") || internalName.startsWith("javax/swing/")
                    || internalName.startsWith("javax/imageio/")) {
                return SHIM + internalName;
            }
            return internalName;
        }
    };

    private static byte[] rewrite(byte[] data, List<String> mains, int[] maxMajor) {
        // 新しすぎる Java のクラスファイルは、読める版(Java 21)として扱う
        int major = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        if (major > maxMajor[0] && major < 100) maxMajor[0] = major;
        if (major > 65) {
            data = data.clone();
            data[4] = 0;
            data[5] = 0;
            data[6] = 0;
            data[7] = 65;
        }
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor rewriter = new ClassVisitor(Opcodes.ASM9, writer) {
            private String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (name.equals("main") && desc.equals("([Ljava/lang/String;)V")
                        && (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_PUBLIC) != 0) {
                    mains.add(className.replace('/', '.'));
                }
                return new CallRewriter(super.visitMethod(access, name, desc, signature, exceptions));
            }
        };
        reader.accept(new ClassRemapper(rewriter, REMAPPER), 0);
        return writer.toByteArray();
    }

    /** System.exit・System.getProperty・相対パスのファイルを開く呼び出しを書き換える。 */
    private static final class CallRewriter extends MethodVisitor {
        CallRewriter(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        private void resolveTop() {
            super.visitMethodInsn(Opcodes.INVOKESTATIC, FILES, "resolve", RESOLVE, false);
        }

        /** 一番上のすぐ下にある文字列(パス)を変換する。 */
        private void resolveSecond() {
            super.visitInsn(Opcodes.SWAP);
            resolveTop();
            super.visitInsn(Opcodes.SWAP);
        }

        @Override
        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            if (op == Opcodes.INVOKESTATIC && owner.equals("java/lang/System")) {
                if (name.equals("exit") && desc.equals("(I)V")) {
                    super.visitMethodInsn(op, SHIM + "JvmSystem", "exit", desc, false);
                    return;
                }
                if (name.equals("getProperty") || name.equals("getProperties")) {
                    super.visitMethodInsn(op, FILES, name, desc, false);
                    return;
                }
            }
            if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Runtime")
                    && (name.equals("exit") || name.equals("halt")) && desc.equals("(I)V")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, SHIM + "JvmSystem", "runtimeExit",
                        "(Ljava/lang/Runtime;I)V", false);
                return;
            }
            if (op == Opcodes.INVOKESPECIAL && name.equals("<init>") && opensPath(owner)) {
                switch (desc) {
                    case "(Ljava/lang/String;)V":
                        resolveTop();
                        break;
                    case "(Ljava/lang/String;Z)V":
                    case "(Ljava/lang/String;Ljava/lang/String;)V":
                    case "(Ljava/lang/String;Ljava/nio/charset/Charset;)V":
                        resolveSecond();
                        break;
                    default:
                        break;
                }
            }
            if (op == Opcodes.INVOKESTATIC && (owner.equals("java/nio/file/Paths") && name.equals("get")
                    || owner.equals("java/nio/file/Path") && name.equals("of"))
                    && desc.equals("(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")) {
                resolveSecond();
                super.visitMethodInsn(op, "java/nio/file/Paths", "get", desc, false);
                return;
            }
            super.visitMethodInsn(op, owner, name, desc, itf);
        }

        private static boolean opensPath(String owner) {
            switch (owner) {
                case "java/io/File":
                case "java/io/FileInputStream":
                case "java/io/FileOutputStream":
                case "java/io/FileReader":
                case "java/io/FileWriter":
                case "java/io/PrintWriter":
                case "java/io/PrintStream":
                case "java/io/RandomAccessFile":
                    return true;
                default:
                    return false;
            }
        }
    }

    // ------------------------------------------------------------------

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    @Nullable
    private static String readText(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static List<String> readLines(File f) {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.add(line.trim());
        } catch (IOException ignored) {
        }
        while (out.size() > 1 && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private static void writeText(File f, String s) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(s);
        }
    }
}
