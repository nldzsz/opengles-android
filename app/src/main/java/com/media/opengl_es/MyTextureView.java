package com.media.opengl_es;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import com.media.opengl_es.GLCommon.GLContext;
import com.media.opengl_es.GLCommon.GLProgram;
import com.media.opengl_es.GLCommon.GLSurface;
import com.media.opengl_es.utils.MLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** 它与SurfaceView一样，继承与View，不同的是
 * 1、具有View的所有特性，可以缩放，平移等动画变换，占据view-Hierarchy的位置。SurfaceView不能想普通View那样平移和缩放
 * 2、创建一个可以用于离线渲染的FBO，用来承载纹理的Texture，没有独立的Surface。SurfaceView是有一个独立于普通视图的Surface
 * */
public class MyTextureView extends TextureView {
    private RenderThread mRenderThread;
    private GLContext mGLcontext;

    // 顶点坐标
    private ByteBuffer vbuffer;
    // 纹理坐标
    private ByteBuffer fbuffer;

    public MyTextureView(Context context) {
        super(context);
        initView(context);
    }

    public MyTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    private void initView(Context context) {
        mRenderThread = new RenderThread();
        mRenderThread.start();

        setSurfaceTextureListener(mRenderThread);
    }

    public void onCreate() {

    }

    public void onDestroy() {
        mRenderThread.stopRender();
    }

    // 渲染一张 PNG的图片
    public void loadBitmap(Bitmap bitmap) {
        if (mRenderThread == null) {
            return;
        }
        mRenderThread.loadBitmap(bitmap);
    }

    private class RenderThread extends Thread implements SurfaceTextureListener{
        private Bitmap mBitmap;
        private SurfaceTexture mSurfaceTexture;
        private Object mLock = new Object();      // 条件锁
        private boolean mRun;      // 标志线程是否结束
        private GLSurface mSurface;
        private GLProgram mprogram;

        public RenderThread() {
            MLog.log("RenderThread()");
        }

        public void stopRender() {
            synchronized (mLock) {
                mRun = false;
            }

            if (mBitmap != null) {
                mBitmap.recycle();
                mBitmap = null;
            }

            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
            }

            if (mSurface != null) {
                mSurface.releaseEglSurface();
            }
        }


        /** SurfaceTextureListener回调：
         * 1、执行于UI线程
         * 2、onSurfaceTextureAvailable 代表TexutreView的texture已经创建好了，该回调在视图创建之后
         *
         * SurfaceTextureListener 所有线程都执行在UI线程中
         * */
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            MLog.log("onSurfaceTextureAvailable(" + width + "x" + height + ")");
            synchronized (mLock) {
                mSurfaceTexture = surface;
                mLock.notify();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            MLog.log("onSurfaceTextureSizeChanged(" + width + "x" + height + ")");
        }

        /** 该回调发生在 TextView即将要释放之前
         * 注意该回调的返回
         * 1、返回 true  在该函数回调完成后由TextureView自己释放它的Surfacetexture
         * 2、返回 false 则要由由自己手动释放SurfaceTexture
         * 3、建议在这里返回true，否则有可能阻塞线程
         * */
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            MLog.log("onSurfaceTextureDestr oyed");
            synchronized (mLock) {
                mSurfaceTexture = null;
            }

            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }

        @Override
        public void run() {
            SurfaceTexture st = null;
            mRun = true;
            while (true) {
                // 等待直到TextureView的SurfaceTexture创建成功
                synchronized (mLock) {
                    while (mRun && (st = mSurfaceTexture) == null) {

                        try {
                            // 阻塞当前线程，直到在其它线程调用mLock.notifyAll()/mLock.notify()函数
                            MLog.log("texutre 还未创建，等待");
                            mLock.wait();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }

                        if (!mRun) {
                            break;
                        }
                    }
                }
                MLog.log("开始渲染 ");
                // 获取到了SurfaceTexture，那么开始做渲染工作
                mGLcontext = new GLContext();
                mSurface = new GLSurface(mGLcontext);

                /** 遇到问题：
                 * 奔溃：
                 * eglCreateWindowSurface: native_window_api_connect (win=0x75d7e8d010) failed (0xffffffed) (already connected to another API?)
                 * 解决方案：
                 * 因为SurfaceTexture还未与前面的EGLContext解绑就又被绑定到其它EGLContext，导致奔溃。原因就是下面渲染结束后没有跳出循环;在while语句最后添加
                 * break;语句
                 * */
                mSurface.createWindowSurface(st);

                mSurface.makeCurrent();

                onSurfaceCreated();

                onDraw();

                /** 遇到问题：渲染结果没有成功显示到屏幕上
                 * 解决方案：因为下面提前释放了SurfaceTexture导致的问题。不应该在这里释放
                 * */
                // 渲染结束 进行相关释放工作
//                st.release();

                MLog.log("渲染结束");

                break;
            }
        }


        private void onSurfaceCreated() {
            // 初始化着色器程序
            mprogram = new GLProgram(vString,fString);
            mprogram.useprogram();

            // 初始化顶点坐标和纹理坐标v
            vbuffer = ByteBuffer.allocateDirect(verdata.length * 4);
            vbuffer.order(ByteOrder.nativeOrder())
                    .asFloatBuffer().put(verdata)
                    .position(0);
            fbuffer = ByteBuffer.allocateDirect(texdata.length * 4);
            fbuffer.order(ByteOrder.nativeOrder())
                    .asFloatBuffer().put(texdata)
                    .position(0);
        }

        private void onDraw() {
            if (mBitmap == null) {
                MLog.log("mBitmap nulll");
                return;
            }
            int width = mSurface.getWidth();
            int height = mSurface.getHeight();
            MLog.log("width "+width + "height " + height);

            GLES20.glViewport(0,0,width,height);
            GLES20.glClearColor(1.0f,0,0,1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 为着色器程序赋值
            mprogram.useprogram();

            int position = mprogram.attributeLocationForname("position");
            int texcoord = mprogram.attributeLocationForname("texcoord");
            int texture = mprogram.uniformaLocationForname("texture");
            GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,vbuffer);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(texcoord,2,GLES20.GL_FLOAT,false,0,fbuffer);
            GLES20.glEnableVertexAttribArray(texcoord);
            MLog.log("position " + position + " texcoord " + texcoord + " texture " + texture);

            // 开始上传纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            // 设置纹理参数
            IntBuffer texIntbuffer = IntBuffer.allocate(1);
            GLES20.glGenTextures(1,texIntbuffer);
            texture = texIntbuffer.get(0);
            if (texture == 0) {
                MLog.log("glGenTextures fail 0");
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);

            // 设置纹理参数
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);

            // 第二个参数和前面用glActiveTexture()函数激活的纹理单元编号要一致，这样opengl es才知道用哪个纹理单元对象 去处理纹理
            GLES20.glUniform1i(texture,0);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,mBitmap,0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);

            // 必须要有，否则渲染结果不会呈现到屏幕上
            mSurface.swapBuffers();

        }

        private void loadBitmap(Bitmap bitmap) {
            synchronized (mLock) {
                mBitmap = bitmap;
            }
        }

    }


    /** 遇到问题，glsl编译通不过
     *  1、void修饰main()函数
     *  2、varying 修饰的变量必须加精度修饰符 比如 highp
     */
    // 顶点着色器
    private static final String vString = "attribute vec4 position;\n" +
            " attribute vec2 texcoord;\n" +
            " \n" +
            " varying highp vec2 tex_coord;\n" +
            " \n" +
            " void main(){\n" +
            "     gl_Position = position;\n" +
            "     tex_coord = texcoord;\n" +
            " }";
    // 片元着色器
    private static final String fString = "uniform sampler2D texture;\n" +
            " \n" +
            " varying highp vec2 tex_coord;\n" +
            " \n" +
            " void main(){\n" +
            "     gl_FragColor = texture2D(texture,tex_coord);\n" +
            " }";
    // 顶点坐标
    private static final float verdata[] = {
            -1.0f,-1.0f,
            1.0f,-1.0f,
            -1.0f,1.0f,
            1.0f,1.0f
    };
    // 纹理坐标
    private static final float texdata[] = {
            0.0f,1.0f,
            1.0f,1.0f,
            0.0f,0.0f,
            1.0f,0.0f,
    };


}
