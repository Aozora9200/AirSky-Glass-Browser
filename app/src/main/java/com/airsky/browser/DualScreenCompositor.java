package com.airsky.browser;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoDisplay;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 二画面表示用に、1 つのページを上下 2 つの画面へ切り分けて描く。
 *
 * <p>Gecko には「上の画面の表示領域 + 下の画面の分」をつなげた縦長の描画面を 1 枚だけ渡す。
 * Gecko はそこへ普通にページを描くので、ページは上の画面の下端から下の画面の上端へ切れ目なく続き、
 * スクロールすれば上で見ていた絵がそのまま下へ(下の絵が上へ)流れていく。</p>
 *
 * <p>描かれた絵は OpenGL の外部テクスチャとして受け取り、上半分を上の画面の TextureView へ等倍で、
 * 残りを下の画面の TextureView へ幅を合わせて縮めて描き写す。描画は専用のスレッドで行う。</p>
 */
final class DualScreenCompositor {

    private static final String TAG = "DualScreen";

    static final int OUTPUT_TOP = 0;
    static final int OUTPUT_BOTTOM = 1;

    private static final String VERTEX_SHADER =
            "attribute vec4 aPos;\n"
            + "attribute vec2 aTex;\n"
            + "uniform mat4 uTexMatrix;\n"
            + "uniform vec2 uCrop;\n"
            + "varying vec2 vTex;\n"
            + "void main() {\n"
            + "  gl_Position = aPos;\n"
            + "  vec2 t = vec2(aTex.x, mix(uCrop.x, uCrop.y, aTex.y));\n"
            + "  vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "varying vec2 vTex;\n"
            + "uniform samplerExternalOES sTex;\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(sTex, vTex);\n"
            + "}\n";

    /** 画面いっぱいの四角形(x, y, u, v)。 */
    private static final float[] QUAD = {
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
    };

    /** 描き写し先(上の画面・下の画面)。GL スレッドだけで触る。 */
    private static final class Output {
        @Nullable SurfaceTexture target;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        int width;
        int height;
        /** ページの描画面のうち、この画面に出す範囲(テクスチャの縦方向。0 = 下端, 1 = 上端)。 */
        float cropBottom;
        float cropTop = 1f;
        boolean visible = true;
    }

    private final HandlerThread thread;
    private final Handler gl;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());

    // ---- GL スレッド ----
    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    @Nullable private EGLConfig eglConfig;
    private EGLSurface pbuffer = EGL14.EGL_NO_SURFACE;
    private int program;
    private int aPos;
    private int aTex;
    private int uTexMatrix;
    private int uCrop;
    private int oesTexture;
    private FloatBuffer quad;
    @Nullable private SurfaceTexture glPageTexture;
    private boolean frameReady;
    private final float[] texMatrix = new float[16];
    private final Output[] outputs = {new Output(), new Output()};
    private float clearR, clearG, clearB;
    private boolean released;

    // ---- メインスレッド ----
    @Nullable private GeckoDisplay display;
    @Nullable private SurfaceTexture pageTexture;
    @Nullable private Surface pageSurface;
    private int pageWidth;
    private int pageHeight;

    DualScreenCompositor() {
        thread = new HandlerThread("DualScreenCompositor");
        thread.start();
        gl = new Handler(thread.getLooper());
        gl.post(this::initGl);
    }

    // ------------------------------------------------------------------
    // メインスレッドから
    // ------------------------------------------------------------------

    /** Gecko の描画先を受け取った(表示中のタブが変わった)。 */
    @MainThread
    void bindDisplay(@NonNull GeckoDisplay d) {
        if (display == d) return;
        unbindDisplay();
        display = d;
        if (pageSurface != null) d.surfaceChanged(surfaceInfo(pageSurface));
    }

    private GeckoDisplay.SurfaceInfo surfaceInfo(@NonNull Surface surface) {
        return new GeckoDisplay.SurfaceInfo.Builder(surface)
                .size(pageWidth, pageHeight)
                .newSurfaceProvider(() -> main.post(this::requestNewSurface))
                .build();
    }

    /** Gecko の描画先を手放す(タブが背面に回る前に呼ぶ)。 */
    @MainThread
    void unbindDisplay() {
        GeckoDisplay d = display;
        display = null;
        if (d != null && pageSurface != null) {
            try {
                d.surfaceDestroyed();
            } catch (Exception e) {
                Log.w(TAG, "surfaceDestroyed", e);
            }
        }
    }

    @Nullable
    @MainThread
    GeckoDisplay getDisplay() {
        return display;
    }

    /**
     * ページの描画面の大きさと、上下の画面への切り分け方を決める。
     *
     * @param width     描画面の幅(上の画面の表示領域の幅)
     * @param topHeight 上の画面に出す高さ
     * @param height    描画面全体の高さ(下の画面を使わないなら topHeight と同じ)
     */
    @MainThread
    void setPageGeometry(int width, int topHeight, int height) {
        if (width <= 0 || height <= 0) return;
        topHeight = Math.max(1, Math.min(topHeight, height));
        final float split = 1f - topHeight / (float) height;
        final boolean bottomUsed = topHeight < height;
        gl.post(() -> {
            setCrop(OUTPUT_TOP, split, 1f);
            setCrop(OUTPUT_BOTTOM, 0f, bottomUsed ? split : 0f);
        });
        if (width == pageWidth && height == pageHeight && pageSurface != null) {
            gl.post(this::drawAll);
            return;
        }
        pageWidth = width;
        pageHeight = height;
        recreatePageSurface();
    }

    /** 描き写し先の画面を設定する。target が null なら外す。 */
    @MainThread
    void setOutput(int index, @Nullable SurfaceTexture target, int width, int height) {
        gl.post(() -> attachOutput(index, target, width, height));
    }

    /** 描き写し先の画面だけ大きさが変わった。 */
    @MainThread
    void resizeOutput(int index, int width, int height) {
        gl.post(() -> {
            Output o = outputs[index];
            o.width = width;
            o.height = height;
            drawAll();
        });
    }

    /** その画面へ描き写すか(下の画面でスタートページの続きを見せる間などは止める)。 */
    @MainThread
    void setOutputVisible(int index, boolean visible) {
        gl.post(() -> {
            Output o = outputs[index];
            if (o.visible == visible) return;
            o.visible = visible;
            drawAll();
        });
    }

    /**
     * 外した描き写し先の SurfaceTexture を、GL スレッドで使い終わってから解放する
     * (TextureView の onSurfaceTextureDestroyed で false を返した時)。
     */
    @MainThread
    void releaseOutputTexture(@NonNull SurfaceTexture texture) {
        gl.post(() -> {
            for (Output o : outputs) {
                if (o.target == texture) detachOutput(o);
            }
            texture.release();
        });
    }

    /** まだページが描かれていない所を塗る色。 */
    @MainThread
    void setClearColor(int argb) {
        final float r = ((argb >> 16) & 0xff) / 255f;
        final float g = ((argb >> 8) & 0xff) / 255f;
        final float b = (argb & 0xff) / 255f;
        gl.post(() -> {
            clearR = r;
            clearG = g;
            clearB = b;
        });
    }

    /** Gecko が描画面を作り直してほしいと言ってきた(GPU プロセスの再起動など)。 */
    @MainThread
    void requestNewSurface() {
        if (pageWidth > 0 && pageHeight > 0) recreatePageSurface();
    }

    @MainThread
    void release() {
        unbindDisplay();
        final SurfaceTexture old = pageTexture;
        final Surface oldSurface = pageSurface;
        pageTexture = null;
        pageSurface = null;
        gl.post(() -> {
            if (oldSurface != null) oldSurface.release();
            releaseGl(old);
            thread.quitSafely();
        });
    }

    private void recreatePageSurface() {
        GeckoDisplay d = display;
        if (d != null && pageSurface != null) {
            try {
                d.surfaceDestroyed();
            } catch (Exception e) {
                Log.w(TAG, "surfaceDestroyed", e);
            }
        }
        final SurfaceTexture old = pageTexture;
        final Surface oldSurface = pageSurface;
        // 外部テクスチャには GL スレッドでつなぐ。それまでは描かれても受け取らない
        SurfaceTexture st = new SurfaceTexture(false);
        st.setDefaultBufferSize(pageWidth, pageHeight);
        Surface surface = new Surface(st);
        pageTexture = st;
        pageSurface = surface;
        gl.post(() -> {
            if (old != null) {
                if (glPageTexture == old) {
                    try {
                        old.detachFromGLContext();
                    } catch (Exception ignored) {
                    }
                    glPageTexture = null;
                }
                old.release();
            }
            if (oldSurface != null) oldSurface.release();
            attachPageTexture(st);
        });
        if (d != null) d.surfaceChanged(surfaceInfo(surface));
    }

    // ------------------------------------------------------------------
    // GL スレッド
    // ------------------------------------------------------------------

    private void initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "EGL を初期化できませんでした");
            return;
        }
        int[] attribs = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            Log.e(TAG, "EGL の設定が見つかりませんでした");
            return;
        }
        eglConfig = configs[0];
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        pbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig,
                new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        if (!EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)) {
            Log.e(TAG, "EGL のコンテキストを使えませんでした");
            return;
        }

        program = buildProgram();
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aTex = GLES20.glGetAttribLocation(program, "aTex");
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix");
        uCrop = GLES20.glGetUniformLocation(program, "uCrop");

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        oesTexture = tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        quad = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(QUAD).position(0);
    }

    private int buildProgram() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) Log.e(TAG, "シェーダーをリンクできませんでした: " + GLES20.glGetProgramInfoLog(p));
        return p;
    }

    private static int compile(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) Log.e(TAG, "シェーダーをコンパイルできませんでした: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private boolean glReady() {
        return !released && eglContext != EGL14.EGL_NO_CONTEXT && eglConfig != null;
    }

    private void attachPageTexture(@NonNull SurfaceTexture st) {
        if (!glReady()) return;
        EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext);
        try {
            st.attachToGLContext(oesTexture);
        } catch (Exception e) {
            Log.e(TAG, "ページの描画面をつなげませんでした", e);
            return;
        }
        glPageTexture = st;
        frameReady = false;
        st.setOnFrameAvailableListener(t -> {
            if (t != glPageTexture) return;
            updateAndDraw();
        }, gl);
    }

    private void setCrop(int index, float bottom, float top) {
        Output o = outputs[index];
        o.cropBottom = bottom;
        o.cropTop = top;
    }

    private void attachOutput(int index, @Nullable SurfaceTexture target, int width, int height) {
        Output o = outputs[index];
        if (o.target != target) detachOutput(o);
        o.width = width;
        o.height = height;
        if (target == null || !glReady()) return;
        if (o.surface == EGL14.EGL_NO_SURFACE) {
            o.surface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, target,
                    new int[]{EGL14.EGL_NONE}, 0);
            if (o.surface == null || o.surface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "画面の描画先を作れませんでした");
                o.surface = EGL14.EGL_NO_SURFACE;
                return;
            }
            o.target = target;
        }
        drawAll();
    }

    private void detachOutput(@NonNull Output o) {
        if (o.surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext);
            EGL14.eglDestroySurface(eglDisplay, o.surface);
            o.surface = EGL14.EGL_NO_SURFACE;
        }
        o.target = null;
    }

    private void updateAndDraw() {
        SurfaceTexture st = glPageTexture;
        if (st == null || !glReady()) return;
        EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext);
        try {
            st.updateTexImage();
            st.getTransformMatrix(texMatrix);
            frameReady = true;
        } catch (Exception e) {
            Log.w(TAG, "updateTexImage", e);
            return;
        }
        drawAll();
    }

    private void drawAll() {
        if (!glReady()) return;
        for (Output o : outputs) draw(o);
    }

    private void draw(@NonNull Output o) {
        if (o.surface == EGL14.EGL_NO_SURFACE || o.width <= 0 || o.height <= 0) return;
        if (!EGL14.eglMakeCurrent(eglDisplay, o.surface, o.surface, eglContext)) return;
        GLES20.glViewport(0, 0, o.width, o.height);
        GLES20.glClearColor(clearR, clearG, clearB, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (frameReady && o.visible && o.cropTop > o.cropBottom) {
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
            GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0);
            GLES20.glUniform2f(uCrop, o.cropBottom, o.cropTop);
            quad.position(0);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aPos);
            quad.position(2);
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aTex);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
        EGL14.eglSwapBuffers(eglDisplay, o.surface);
    }

    private void releaseGl(@Nullable SurfaceTexture page) {
        released = true;
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return;
        for (Output o : outputs) detachOutput(o);
        if (page != null) {
            try {
                page.detachFromGLContext();
            } catch (Exception ignored) {
            }
            page.release();
        }
        glPageTexture = null;
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, pbuffer);
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext);
        // 既定の EGLDisplay は Gecko も使っているので eglTerminate はしない
        EGL14.eglReleaseThread();
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
    }
}
