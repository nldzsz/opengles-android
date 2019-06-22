package com.media.opengl_es.utils;

import android.util.Log;

import com.media.opengl_es.BuildConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日志打印类 tag为 mlog
 */
public class MLog {
//    private static final boolean isDebug = true;
private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH", Locale.US);//日期格式;

    private static Date date = new Date();//因为log日志是使用日期命名的，使用静态成员变量主要是为了在整个程序运行期间只存在一个.log文件中;

    public static void log(String l) {
        if (BuildConfig.DEBUG) {
            Log.d("Mlog", l);
        }
    }

    public static void logt(String tag,String l) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, l);
        }
    }

}
