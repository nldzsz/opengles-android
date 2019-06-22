package com.media.opengl_es.utils;

import android.content.Context;

public class PixelUtil {

    public static int dp2px(Context context,int px) {

        float density = context.getResources().getDisplayMetrics().density;
        return (int)(px * density);
    }
}
