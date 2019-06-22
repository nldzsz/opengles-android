package com.media.opengl_es;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.AttributeSet;

import com.media.opengl_es.GLCommon.GLFrameBuffer;
import com.media.opengl_es.GLCommon.GLProgram;
import com.media.opengl_es.utils.MLog;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.locks.Lock;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** SurfaceView会创建一个Surface，该Surface实际上就是对FBO及render buffer的封装。
 *  GLSurfaceView继承于SurfaceView，实现了SurfaceHolder.Callback2和SurfaceHolder.Callback接口，加入了EGL的管理和创建了一个独立的GLThread渲染线程。
 *  它对外提供了GLSurfaceView.Renderer接口
 *  onSurfaceCreated() 当Surface被创建后调用，在此接口中我们可以对EGLConfig进行再次配置(一般用默认就好)，此函数的调用，代表EGL上下文环境及FBO等已经创建好了
 *  onSurfaceChanged() 当Surface大小发生改变时调用，比GLSurfaceView大小改变了，也会伴随着Surface大小的改变，此方法会调用
 *  onDrawFrame()      此函数中，就可以调用opengl es的函数进行渲染工作了，最终渲染的结果(也就是调用glDrawxxx()函数后)会自动传递给前面创建的Surface
 *  传递给Surface后，渲染的结果就会最终显示在屏幕上
 *
 *  GLSurfaceView的特性如下：
 *  1、提供并且管理一个独立的Surface。
 *  2、提供并且管理一个EGL display，它能把opengl渲染的结果传递到上述的Surface上。
 *  3、提供GLSurfaceView.Renderer接口，用户实现该接口，调用opengl es函数实现渲染，setRenderer()函数要在onSurfaceCreated()调用前调用
 *  4、提供并且管理一个渲染线程，让渲染工作和UI线程分离，该线程在setRenderer()时创建
 *  5、支持按需渲染(on-demand)和连续渲染(continuous)两种模式。
 *  默认为连续渲染，onDrawFrame()在各种初始化工作完成后以屏幕刷新率(16ms)的间隔持续调用
 *  按需渲染,onDrawFrame()函数在调用requestRender()后调用，如果停止调用requestRender()，onDrawFrame()函数也停止调用。此方式渲染线程在没有工作时会
 *  休眠，效率可能要高一些
 * */
public class MyGLSurfaceView extends GLSurfaceView {

    // 先保存要显示的纹理
    private Bitmap mBitmap;
    private int mWidth;
    private int mHeight;
    // 顶点坐标
    private ByteBuffer vbuffer;
    // 纹理坐标
    private ByteBuffer fbuffer;

    // 1、初始化GLSurfaceView，包括调用setRenderer()设置GLSurfaceView.Renderer对象
    // setRenderer()将会创建一个渲染线程
    public MyGLSurfaceView(Context context) {
        super(context);
        initGLESContext();
    }

    public MyGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initGLESContext();
    }

    /** 遇到问题：Opengl es函数无法工作，由于没有指定opengl es的版本
     *  解决方案：设置正确的opengl es版本
     **/
    private void initGLESContext() {
        // 设置版本，必须要
        setEGLContextClientVersion(2);

        GLRGBRender render = new GLRGBRender();
        setRenderer(render);
        setRenderMode(RENDERMODE_WHEN_DIRTY); // 默认是连续渲染模式
    }

    // 上传纹理 上传后纹理将显示到屏幕上
    public void loadBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            MLog.log("要加载的纹理 为null");
            return;
        }
        mBitmap = bitmap;
    }

    public void destroy() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }

    /** 2、实现GLSurfaceView.Renderer接口，调用opengl es函数实现渲染
     * 遇到问题：无法成功渲染
     * 解决方案：因为SurfaceView默认就创建了一个Surface，这个Surface中就包含了一个FBO及Render buffer 等等，所以这里如果又创建了fbo 并且将其激活，则会覆盖Surface的行为
     * 所以渲染不到屏幕上去了。解决方法就是不需像IOS那样自己额外创建fbo了
     * */
    private class GLRGBRender implements GLSurfaceView.Renderer{

        // 着色器程序
        private GLProgram mprogram;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            MLog.log("onSurfaceCreated thread " + Thread.currentThread());

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

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            MLog.log("onSurfaceChanged width " + width + " height " + height + " thread " + Thread.currentThread());
            mWidth = width;
            mHeight = height;

            GLES20.glClearColor(1.0f,0.0f,0.0f,1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glViewport(0,0,mWidth,mHeight);


//            // 按需渲染模式，则需要要手动调用该函数才会触发onDraw()函数调用，貌似这里不调用onDrawFrame()也会调用三次
//            requestRender();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            MLog.log("onDrawFrame thread " + Thread.currentThread());

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

//            IntBuffer io = IntBuffer.allocate(1);
//            GLES20.glGenRenderbuffers(1,io);
//            renderbuffer = io.get(0);
//            MLog.log("render buff " + renderbuffer);
//            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,renderbuffer);
//            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_RENDERBUFFER,renderbuffer);

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
//                ByteBuffer pixelsbuffer = ByteBuffer.allocateDirect(mBitmap.getByteCount());
//                mBitmap.copyPixelsToBuffer(pixelsbuffer);
//
//                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGB,width,height,0,GLES20.GL_RGB,GLES20.GL_UNSIGNED_BYTE,pixelsbuffer);

//                mBitmap.recycle();
//                pixelsbuffer.clear();

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
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




