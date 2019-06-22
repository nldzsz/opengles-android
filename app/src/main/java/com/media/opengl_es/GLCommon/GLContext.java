package com.media.opengl_es.GLCommon;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

import com.media.opengl_es.utils.MLog;

/** 对EGLContext的封装
 * EGLContext 是EGL上下文，要使用opengl es则必须要先创建改上下文
 * 1、首先需要选择EGLDisplay，可以理解为要绘制的地方的一个抽象。
 * 2、然后要配置EGLConfig，它是上下文的配置参数，比如RGBA的位宽等等
 *
 * 参考 google的示例代码 grafika 地址：https://github.com/google/grafika/
 * */
public class GLContext {

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig mEGLConfig;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;

    /**
     * Constructor flag: surface must be recordable.  This discourages EGL from using a
     * pixel format that cannot be converted efficiently to something usable by the video
     * encoder.
     */
    public static final int FLAG_RECORDABLE = 0x01;

    // Android-specific extension.
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;


    public GLContext() {
        initEGLDisplay();
        initEGLConfig();
        initEGLContext();
    }


    // 1、创建 EGLDisplay
    private void initEGLDisplay() {
        // 用于绘制的地方的一个抽闲
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            MLog.log("eglGetDisplay fail");
        }

        // 初始化EGLDisplay，还可以在这个函数里面获取版本号
        boolean ret = EGL14.eglInitialize(mEGLDisplay,null,0,null,0);
        if (!ret) {
            MLog.log("eglInitialize fail");
        }
    }

    // 2、创建EGLSurface的配置
    // 定义 EGLConfig 属性配置，这里定义了红、绿、蓝、透明度、深度、模板缓冲的位数，属性配置数组要以EGL14.EGL_NONE结尾
    private static final int[] EGL_CONFIG = {
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
    };
    private void initEGLConfig() {
        // 所有符合配置的 EGLConfig 个数
        int[] numConfigs = new int[1];
        // 所有符合配置的 EGLConfig
        EGLConfig[] configs = new EGLConfig[1];

        // 会获取所有满足 EGL_CONFIG 的 config，然后取第一个
        EGL14.eglChooseConfig(mEGLDisplay, EGL_CONFIG, 0, configs, 0, configs.length, numConfigs, 0);
        mEGLConfig = configs[0];
    }

    // 3、创建上下文EGLContext
    private static final int[] EGLCONTEXT_ATTRIBUTE = {
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE,
    };
    private void initEGLContext() {
        // 创建上下文
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, mEGLConfig, EGL14.EGL_NO_CONTEXT, EGLCONTEXT_ATTRIBUTE, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            MLog.log("eglCreateContext fail");
        }
    }

    /**
     * 根据给定的Surface或者SurfaceTexture 创建EGLSurface,最终的渲染将呈现到屏幕上
     * surface：来自SurfaceView的Surface或者TextureView的SurfaceTexture或者手动创建的SurfaceTexture
     * <p>
     * If this is destined for MediaCodec, the EGLConfig should have the "recordable" attribute.
     */
    public EGLSurface createWindowSurface(Object surface) {
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
            throw new RuntimeException("invalid surface: " + surface);
        }

        // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface,
                surfaceAttribs, 0);
        checkEglError("eglCreateWindowSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /**
     * 创建一块离屏渲染用的EGLSurface，所谓离屏渲染 就是最终的渲染结果不会呈现到屏幕上，所以不要SurfaceView或者Textureview的Surface，
     * 但是要指定这块区域的宽和高
     * Creates an EGL surface associated with an offscreen buffer.
     */
    public EGLSurface createOffscreenSurface(int width, int height) {
        int[] surfaceAttribs = {
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig,
                surfaceAttribs, 0);
        checkEglError("eglCreatePbufferSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /** 切换上下文到当前线程，因为opengl es上下文是一个全局变量，同一时刻只能在一个线程中使用，所以要在当前线程中使用opengl es的函数指令，则
     *  必须将上下文切换到当前线程，下面的函数代表将draw或者read绑定到context，并且切换到当前上下文，这样openggl es的函数将在draw或者read上
     *  产生作用
     *  EGLBoolean eglMakeCurrent(
     *     EGLDisplay display,
     *     EGLSurface draw,
     *     EGLSurface read,
     *     EGLContext context
     * );
     * draw 代表gldrawArrays()等等用于想渲染缓冲区写入数据的Surface
     * read 代表glReadPixels()，glCopyTexImage2D()和glCopyTexSubImage2D() 用于从渲染缓冲区读取数据的surface
     * 注：read和draw可以是同一个Surface
     */
    public void makeCurrent(EGLSurface eglSurface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            MLog.log( "NOTE: makeCurrent w/o display");
        }
        if (!EGL14.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    // 这里read和draw是不同的缓冲区
    public void makeCurrent(EGLSurface drawSurface, EGLSurface readSurface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            MLog.log("NOTE: makeCurrent w/o display");
        }
        if (!EGL14.eglMakeCurrent(mEGLDisplay, drawSurface, readSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent(draw,read) failed");
        }
    }

    // 将上下文和渲染缓冲区解绑。
    public void makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * 将渲染缓冲区中数据发送到Surface指定的屏幕区域里面，这样屏幕将可以显示该渲染结果
     * EGL中有两个缓冲区，分别叫前台和后台，前台用于在屏幕上显示，后台用于供opengl es渲染，当opengl es渲染完成后，还必须通过下面方法将后台数据copy到
     * 前台才能最终显示在屏幕上
     * @return false on failure
     */
    public boolean swapBuffers(EGLSurface eglSurface) {
        return EGL14.eglSwapBuffers(mEGLDisplay, eglSurface);
    }

    // Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
    public void setPresentationTime(EGLSurface eglSurface, long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, eglSurface, nsecs);
    }

    // Returns true if our context and the specified surface are current.
    public boolean isCurrent(EGLSurface eglSurface) {
        return mEGLContext.equals(EGL14.eglGetCurrentContext()) &&
                eglSurface.equals(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW));
    }

    /**
     *  可以用于获取Surface的宽和高，单位pixel
     *  what:EGL14.EGL_WIDTH，EGL14.EGL_HEIGHT
     */
    public int querySurface(EGLSurface eglSurface, int what) {
        int[] value = new int[1];
        EGL14.eglQuerySurface(mEGLDisplay, eglSurface, what, value, 0);
        return value[0];
    }

    // Writes the current display, context, and surface to the log.
    public static void logCurrent(String msg) {
        EGLDisplay display;
        EGLContext context;
        EGLSurface surface;

        display = EGL14.eglGetCurrentDisplay();
        context = EGL14.eglGetCurrentContext();
        surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
       MLog.log("Current EGL (" + msg + "): display=" + display + ", context=" + context +
                ", surface=" + surface);
    }

    // 释放资源
    public void releaseSurface(EGLSurface eglSurface) {
        EGL14.eglDestroySurface(mEGLDisplay, eglSurface);
    }

    /**
     * Checks for EGL errors.  Throws an exception if an error has been raised.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
