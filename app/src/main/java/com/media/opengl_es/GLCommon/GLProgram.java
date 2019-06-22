package com.media.opengl_es.GLCommon;

import android.opengl.GLES20;

import com.media.opengl_es.BuildConfig;
import com.media.opengl_es.utils.MLog;

import java.nio.IntBuffer;

public class GLProgram {

    private int program;

    /**
     *  根据GLSL编写的顶点着色器和片段着色器初始化；初始化完成后，最终生成的程序将作为app与glsl交
     *  互的桥梁；具体交互流程如下：
     *  1、通过glGetAttribLocation()、glGetUniformLocation()等系列函数获取glsl的变量句柄
     *  2、通过glglVertexAttribPointer()、glTexImage2D、glUniform1i()等系列函数给glsl变量设置值
     *  与opengl es交互了
     *  vString:顶点着色器
     *  fString:片段着色器
     */
    public GLProgram(String vShaderString, String fShaderString) {
        int vShader,fShader;
        vShader = initShader(vShaderString,GLES20.GL_VERTEX_SHADER);
        fShader = initShader(fShaderString,GLES20.GL_FRAGMENT_SHADER);

        program = GLES20.glCreateProgram();
        if (program == 0) {
            MLog.log("glCreateProgram fail");
        }

        GLES20.glAttachShader(program,vShader);
        GLES20.glAttachShader(program,fShader);

        GLES20.glLinkProgram(program);
        GLES20.glValidateProgram(program);

        IntBuffer status = IntBuffer.allocate(1);
        if (BuildConfig.DEBUG) {
            IntBuffer log_len = IntBuffer.allocate(1);
            GLES20.glGetProgramiv(program,GLES20.GL_INFO_LOG_LENGTH,log_len);
            if (log_len.get(0) > 0) {
                MLog.log("program line log: " + GLES20.glGetProgramInfoLog(program));
            }
        }

        GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status);
        if (status.get(0) == GLES20.GL_FALSE) {
            MLog.log("link program fail");
        }

    }

    private int initShader(String shaderString,int type) {

        if (shaderString.length() == 0) {
            MLog.log("initShader fail shader string null");
            return 0;
        }

        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            MLog.log("initShader fail shader glCreateShader return 0");
            return 0;
        }

        GLES20.glShaderSource(shader,shaderString);
        GLES20.glCompileShader(shader);

        if (BuildConfig.DEBUG) {
            IntBuffer loglenght = IntBuffer.allocate(1);
            GLES20.glGetShaderiv(shader,GLES20.GL_INFO_LOG_LENGTH,loglenght);
            if (loglenght.get(0) > 0) {
                String info = GLES20.glGetShaderInfoLog(shader);
                MLog.log("compile shader: " + info);
            }
        }

        IntBuffer status = IntBuffer.allocate(1);
        GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status);
        if (status.get(0) == GLES20.GL_FALSE) {
            MLog.log("shader compile fail");
            return 0;
        }

        return shader;
    }

    /** 获取顶点着色器GLSL中的attribute修饰的顶点变量句柄;
     *  比如attribute vec4 position;(一般用来表示几何图元的坐标)和attribute vec2 texcoord;(一般用来表示纹理坐标)
     *  atrname:顶点着色器中由attribute修饰的变量，变量名不能以gl_开头，否则这里返回-1
     *  return:成功返回>0的整数，失败返回-1
     *
     */
    public int attributeLocationForname(String name) {
        return GLES20.glGetAttribLocation(program,name);
    }

    /** 获取片段着色器GLSL中的纹理句柄,app通过此句柄来设置纹理相关属性和传递图片给open gl es；
     *  比如uniform sampler2D inputImageTexture;
     *  uname:顶点着色器中由attribute修饰的变量，变量名不能以gl_开头，否则这里返回-1
     *  return:成功返回>0的整数，失败返回-1
     */
    public int uniformaLocationForname(String name) {
        return GLES20.glGetUniformLocation(program,name);
    }

    // 让生成的最终程序处于运行状态,这样最终调用绘图指令的时候前面设置的这些参数才会真正执行
    public void useprogram() {
        if (program == 0) {
            MLog.log("program == 0");
            return;
        }
        GLES20.glUseProgram(program);
    }

    public void destroy() {
        if (program > 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }
}
