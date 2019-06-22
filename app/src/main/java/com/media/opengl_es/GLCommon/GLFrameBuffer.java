package com.media.opengl_es.GLCommon;

import android.opengl.GLES20;
import com.media.opengl_es.utils.MLog;
import java.nio.IntBuffer;

public class GLFrameBuffer {

    private int framebuffer;
    private int texture;
    private int mWidth;
    private int mHeight;

    /** 对FBO帧缓冲区的封装 默认1280x720大小
     * width: fbo缓冲区的宽度
     * height:fbo缓冲区的高度
     * offscreen:是否离屏渲染,如果为false，那么仅仅只是创建一个frame buffer。为true 则还会为其分配内存
     * */
    public GLFrameBuffer(int width,int height,boolean offscreen) {

        mWidth = width;
        mHeight = height;

        IntBuffer frameIntbuffer = IntBuffer.allocate(1);

        // 生成缓冲区FBO
        GLES20.glGenFramebuffers(1,frameIntbuffer);
        framebuffer = frameIntbuffer.get(0);
        if (framebuffer == 0) {
            MLog.log("glGenFramebuffers fail 0");
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);

        // 设置纹理参数
        IntBuffer texIntbuffer = IntBuffer.allocate(1);
        GLES20.glGenTextures(1,texIntbuffer);
        texture = texIntbuffer.get(0);
        if (texture == 0) {
            MLog.log("glGenTextures fail 0");
        }

        // 设置纹理参数
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);

        if (offscreen) {
            // 分配指定格式的一个像素内存块，但是像素数据都初始化为0。
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGB,width,height,0,GLES20.GL_RGB,GLES20.GL_UNSIGNED_BYTE,null);

            /** 此函数的意思就是将当前framebuffer中的渲染结果转换成纹理数据定位到_texture中，那么_texture就是一个已经带有像素数据的纹理对象了(即不需要经过
             *  应用端通过glTexImage2D()函数来赋值了),那么它就可以直接作为其它着色器程序中uniform sampler2D 类型的输入了，通过如下流程：
             *  glUseProgram(otherProgramHandle);       // 其它着色器程序句柄
             *  glGenFramebuffers(1, &otherframebuffer);    // otherframebuffer就是其它着色器程序对应的frame buffer
             *  glBindFramebuffer(GL_FRAMEBUFFER, otherframebuffer);
             *  glActiveTexture(GL_TEXTUREi)    // 这里的i不一定要与前面[self generateTexture]中调用的相同
             *  glBindTexture(GL_TEXTURE_2D, texture); // texutre就是这里生成的_texture，两者一定要相同
             *  glUniform1i(_uniformPresentSampler, i);
             *  .....           // 其它程序
             *  glxxx()
             *  glDrawsArrays();
             *  这段程序将以_texture中所对应的渲染结果作为纹理输入，重新开始渲染流程，期间会用指定的色器程序otherProgramHandle进行处理，最后将渲染的结果保存到
             *  新的帧缓冲区otherframebuffer中，这就是实现离屏渲染的使用流程；多次离屏渲染则依次类推
             *  此函数是实现多次离屏渲染的关键函数
             **/
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, framebuffer, 0);

            /** 检查framebuffer的状态 返回值如下含义：
             * GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT：36054，没有为其指定类型，比如GL_COLOR_ATTACHMENT0
             * GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT：36055，说明没有为其分配image，也就是没有调用glTexImage2D()函数为其分配纹理
             * ....:参考官网
             * 结论：仅仅调用glGenFramebuffers()和glBindFramebuffer()函数，成功创建framebuffer id，肯定不会返回GL_FRAMEBUFFER_COMPLETE的
             * */
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                MLog.log("create frame buffer fail: "+status);
            }
        }

        // 解绑，这样后面设置的值不会把这个 texture id的设置覆盖
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
    }

    public void destroy() {
        if (framebuffer != 0) {
            IntBuffer buf = IntBuffer.allocate(1);
            buf.put(framebuffer);
            GLES20.glDeleteFramebuffers(1,buf);
        }
    }

    public void activeFrameBuffer() {
        MLog.log("frame buff " + framebuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);
//        GLES20.glViewport(0,0,mWidth,mHeight);
    }

    // 获取frame buffer id
    public int getFramebuffer() {
        return framebuffer;
    }

    // 获取 纹理texture id
    public int getTexture() {
        return texture;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }
}
