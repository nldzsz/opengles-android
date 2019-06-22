package com.media.opengl_es;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.media.opengl_es.GLCommon.GLContext;
import com.media.opengl_es.GLCommon.GLProgram;
import com.media.opengl_es.GLCommon.GLSurface;
import com.media.opengl_es.utils.MLog;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** 要使用SurfaceView，它继承自类View，因此它本质上是一个View。但与普通View不同的是，它有自己的Surface。但是它不能向普通view那样进行旋转，缩放等操作
 * 要使用SurfaceView要自己对EGL进行管理，对渲染线程进行管理
 *
 * 住：使用方式和TextureView比较相似
 * */
public class MySurfaceView extends SurfaceView{
    private RenderThread mRenderThread;
    private GLContext mGLcontext;
    private boolean finishRender;
    private Context mContext;

    // 对角线 顶点坐标
    private ByteBuffer vbuffer1;
    // 顶点坐标
    private ByteBuffer vbuffer;
    // 纹理坐标
    private ByteBuffer fbuffer;


    public MySurfaceView(Context context) {
        super(context);
        initView(context);
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    private void initView(Context context) {
        mContext = context;
        mRenderThread = new RenderThread();
        mRenderThread.start();
        getHolder().addCallback(mRenderThread);
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
        mRenderThread.loadBitmap(bitmap,false);
    }


    // 将渲染结果中的图片取出来
    public Bitmap getBitmap() {
        if (!finishRender) {
            return null;
        }
        return mRenderThread.getBitmapFromSurface();
    }

    // 增加对脚线
    public void addTringleLine(Bitmap bitmap) {
        if (mRenderThread == null) {
            return;
        }
        mRenderThread.loadBitmap(bitmap,true);
    }


    private class RenderThread extends Thread implements SurfaceHolder.Callback  {
        private Bitmap mBitmap;
        private Bitmap mBitmapForSave;
        private Surface mSurfaceTexture;
        private Object mLock = new Object();      // 条件锁
        private boolean mRun;      // 标志线程是否结束
        private GLSurface mSurface;
        private GLProgram mprogram;
        private GLProgram mWhiteLineprogram;
        private boolean mAddLine;

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

        @Override
        public void run() {
            Surface st = null;
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

                mSurface.makeCurrentReadFrom(mSurface);

                onSurfaceCreated();

                onDraw();

                /** 遇到问题：渲染结果没有成功显示到屏幕上
                 * 解决方案：因为下面提前释放了SurfaceTexture导致的问题。不应该在这里释放
                 * */
                // 渲染结束 进行相关释放工作
//                st.release();

                MLog.log("渲染结束");
                finishRender = true;

                break;
            }
        }

        private void onSurfaceCreated() {
            // 初始化着色器程序
            mprogram = new GLProgram(vString,fString);
            mprogram.useprogram();

            // 初始化着色器程序
            mWhiteLineprogram = new GLProgram(vString,whiteLineFragString);
            mprogram.useprogram();

            // 对角线顶点坐标
            vbuffer1 = ByteBuffer.allocateDirect(verdata1.length * 4);
            vbuffer1.order(ByteOrder.nativeOrder())
                    .asFloatBuffer().put(verdata1)
                    .position(0);

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

//            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,mBitmap,0);
            /** 注：android 解码图片(JPG,PNG等)默认的格式是ARGB的，但是它的数据在内存中是大端序方式存储的(所有java的数据都是这样存储方式)
             * 而opengl es是按照小端序的方式来处理数据的。所以这里传递internalformat 是GL_RGBA，刚好与ARGBA相反，这样就保证了数据从java端
             * 传给opengl es端时是正确的。
             * 当然，也可以将bitmap的数据先转成小端序再传给opengl es，internalformat 填写GL_ARGB，但java层的opengl es api没有这个选项，所以
             * 只能选择前面的方案
             * GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,mBitmap,0);函数internalformat和type都是-1 将按照GLES20.GL_RGBA和GLES20.GL_UNSIGNED_BYTE
             * 处理，所以下面这方式和它是等价的
             * */
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,mBitmap,GLES20.GL_UNSIGNED_BYTE,0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);

            // 接着画线
            if (mAddLine) {
                MLog.log("开始画线");
                mWhiteLineprogram.useprogram();
                int lineposition = mprogram.attributeLocationForname("position");
                GLES20.glVertexAttribPointer(lineposition,2,GLES20.GL_FLOAT,false,0,vbuffer1);
                GLES20.glEnableVertexAttribArray(position);

                GLES20.glLineWidth(5.0f);
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, 4);
            }

            try {
                mBitmapForSave = mSurface.framebufferToBitmap();
            } catch (IOException io) {
                io.printStackTrace();
            }

            /** 遇到问题，不能成功从frame buffer中截取像素数据
             * 分析，swapBuffers()函数后，frame buffer中数据将被清空了，所以截取像素数据glReadPixels()在swapBuffers()之后调用，肯定没东西了。
             * 解决方案：在swapBuffers()调用之前进行截取
             * */
            // 必须要有，否则渲染结果不会呈现到屏幕上
            mSurface.swapBuffers();

        }

        private void loadBitmap(Bitmap bitmap, boolean addline) {
            synchronized (mLock) {
                mAddLine = addline;
                mBitmap = bitmap;
            }
        }

        private Bitmap getBitmapFromSurface() {
            return mBitmapForSave;
        }

        /** SurfaceHolder.Callback 回调：
         * 1、执行于UI线程
         * 2、surfaceCreated 代表TexutreView的texture已经创建好了，该回调在视图创建之后
         *
         * SurfaceTextureListener 所有线程都执行在UI线程中
         * */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            MLog.log("surfaceCreated 创建了");
            synchronized (mLock) {
                mSurfaceTexture = holder.getSurface();
                mLock.notify();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            MLog.log("surfaceChanged 创建了");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            MLog.log("surfaceDestroyed 创建了");
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

    private static final String whiteLineFragString = "" +
            "void main(){\n" +
            "     gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
            " }";

    // 对角线的顶点坐标
    private static final float verdata1[] = {
            -1.0f,-1.0f,
            1.0f,1.0f,
            1.0f,-1.0f,
            -1.0f,1.0f,
    };

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
