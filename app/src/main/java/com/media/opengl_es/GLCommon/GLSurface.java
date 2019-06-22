package com.media.opengl_es.GLCommon;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.media.opengl_es.utils.MLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** 对EGL的封装
 * 1、EGL：
 * 它是OpenGL ES 渲染 API 和本地窗口系统（native platform window system）之间的一个中间接口层，主要由厂商来实现。EGL 提供了如下机制：
 *  与设备的原生窗口系统通信
 *  查询绘图表面的可用类型和配置
 *  创建绘图表面
 *  在 OpenGL ES 和其他图形渲染 API 之间同步渲染
 *  管理纹理贴图等渲染资源
 *
 * 我们可以直接用 GLSurfaceView 来进行 OpenGL 的渲染，就是因为在 GLSurfaceView 的内部已经完成了对 EGL 的使用封装，当然我们也可以封装
 * 自己的 EGL 环境。
 *
 * 2、EGLDisplay：
 * 对实际显示设备的抽象
 * 3、EGLSurface
 * 用来存储图像的内存区域
 * 4、EGLContext

 * 绘制上下文
 * 1、最终将EGLDisplay、EGLConfig、EGLContext、和EGLSurface封装到一起
 * 2、一个EGLContext代表一个上下文，它可以被多个EGLSurface 共享使用，所以这也是为为什么分GLSurface和GLContext两个类的原因
 * 3、通过GLSurface实现上下文切换，将渲染结果呈现到屏幕上等等操作。以及取回渲染缓冲区中的记过
 *
 * 参考 google的示例代码 grafika 地址：https://github.com/google/grafika/
 * */
public class GLSurface {

    private GLContext mEglContext;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    private int mWidth = -1;
    private int mHeight = -1;

    public GLSurface(GLContext context) {
        mEglContext = context;
    }

    /**
     * 创建一个window Surface，最终的渲染结果将呈现到屏幕上;Surface和SurfaceTexture都可以成功创建EGLSurface
     * <p>
     * @param surface May be a Surface or SurfaceTexture.
     */
    public void createWindowSurface(Object surface) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        mEGLSurface = mEglContext.createWindowSurface(surface);

        // Don't cache width/height here, because the size of the underlying surface can change
        // out from under us (see e.g. HardwareScalerActivity).
        //mWidth = mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH);
        //mHeight = mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT);
    }

    /**
     * Creates an off-screen surface.
     */
    public void createOffscreenSurface(int width, int height) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        mEGLSurface = mEglContext.createOffscreenSurface(width, height);
        mWidth = width;
        mHeight = height;
    }

    // 获取Surface的宽和高
    public int getWidth() {
        if (mWidth < 0) {
            return mEglContext.querySurface(mEGLSurface, EGL14.EGL_WIDTH);
        } else {
            return mWidth;
        }
    }

    public int getHeight() {
        if (mHeight < 0) {
            return mEglContext.querySurface(mEGLSurface, EGL14.EGL_HEIGHT);
        } else {
            return mHeight;
        }
    }

    // 释放资源
    public void releaseEglSurface() {
        mEglContext.releaseSurface(mEGLSurface);
        mEGLSurface = EGL14.EGL_NO_SURFACE;
        mWidth = mHeight = -1;
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
    public void makeCurrent() {
        mEglContext.makeCurrent(mEGLSurface);
    }

    /**
     * Makes our EGL context and surface current for drawing, using the supplied surface
     * for reading.
     */
    public void makeCurrentReadFrom(GLSurface readSurface) {
        mEglContext.makeCurrent(mEGLSurface, readSurface.mEGLSurface);
    }

    /**
     * 将渲染缓冲区中数据发送到Surface指定的屏幕区域里面，这样屏幕将可以显示该渲染结果
     * EGL中有两个缓冲区，分别叫前台和后台，前台用于在屏幕上显示，后台用于供opengl es渲染，当opengl es渲染完成后，还必须通过下面方法将后台数据copy到
     * 前台才能最终显示在屏幕上
     * @return false on failure
     */
    public boolean swapBuffers() {
        boolean result = mEglContext.swapBuffers(mEGLSurface);
        if (!result) {
            MLog.log("WARNING: swapBuffers() failed");
        }
        return result;
    }

    /**
     * Sends the presentation time stamp to EGL.
     *
     * @param nsecs Timestamp, in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        mEglContext.setPresentationTime(mEGLSurface, nsecs);
    }

    // 获取渲染结果，以bitmap形式返回
    public Bitmap framebufferToBitmap() throws IOException {

        if (!mEglContext.isCurrent(mEGLSurface)) {
            throw new RuntimeException("Expected EGL context/surface is not current");
        }

        // glReadPixels fills in a "direct" ByteBuffer with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  While the Bitmap
        // constructor that takes an int[] wants little-endian ARGB (blue/red swapped), the
        // Bitmap "copy pixels" method wants the same format GL provides.
        //
        // Ideally we'd have some way to re-use the ByteBuffer, especially if we're calling
        // here often.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside down relative to what appears on screen if the
        // typical GL conventions are used.

        // 读取设置字节对齐
        GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT,1);
        int width = getWidth();
        int height = getHeight();
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer colobu = IntBuffer.allocate(1);
        GLES20.glGetIntegerv(GLES20.GL_IMPLEMENTATION_COLOR_READ_FORMAT,colobu);
        IntBuffer typebu = IntBuffer.allocate(1);
        GLES20.glGetIntegerv(GLES20.GL_IMPLEMENTATION_COLOR_READ_TYPE,typebu);
        MLog.log("类型 colobu " + colobu.get(0) + "typebu " + typebu.get(0));

        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        MLog.log("要读取的长和宽 w="+width + " hei " + height);
        /** 遇到问题：魅族 pro 7-s一直返回 0x502错误(GL_INVALID_OPERATION)，该错误根据官方文档的解释是glReadPixels()函数的format和type和frame buffer
         * 中像素的实际format、type不匹配造成的，返回错误之后buf得不到任何数据
         * 分析：但实际上format和type是对应上的，而且buf也读取到了正确的像素数据，仍然返回该错误，不知道为何，有待进一步研究。
         * */
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = "glReadPixels: glError 0x" + Integer.toHexString(error);
            MLog.log(msg);
//            throw new RuntimeException(msg);
        }
        buf.rewind();

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(180);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buf);

        // 创建新的图片
        Bitmap resizedBitmap = Bitmap.createBitmap(bmp, 0, 0,
                bmp.getWidth(), bmp.getHeight(), matrix, true);


        return resizedBitmap;
    }

    // 获取渲染结果，并保存到文件中
    public void saveFrame(File file) throws IOException {
        Bitmap bmp = framebufferToBitmap();

        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(file.getAbsolutePath()));
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bmp.recycle();
        } finally {
            if (bos != null) bos.close();
        }
        MLog.log("Saved " + bmp.getWidth() + "x" + bmp.getHeight() + " frame as '" + file + "'");
    }
}
