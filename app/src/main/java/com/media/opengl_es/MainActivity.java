package com.media.opengl_es;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.media.opengl_es.utils.MLog;
import com.media.opengl_es.utils.PixelUtil;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.id_spinner)
    Spinner mSpinner;

    @BindView(R.id.id_content_layout)
    RelativeLayout contentLayout;

    @BindView(R.id.id_btn1)
    Button btn1;

    @BindView(R.id.id_btn2)
    Button btn2;

    @BindView(R.id.id_btn3)
    Button btn3;

    private static final String[] items = {
            "SurfaceView",
            "GLSurfaceView",
            "TextureView"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);


        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,items);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != 0) {
                    btn2.setVisibility(View.GONE);
                    btn3.setVisibility(View.GONE);
                } else {
                    btn2.setVisibility(View.VISIBLE);
                    btn3.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        requestPermission();

    }

    @Override
    protected void onStop() {
        super.onStop();


    }

    /** 遇到问题：
     * 通过getAssets().openFd(fileName).getFileDescriptor();获取的文件句柄，然后由BitmapFactory.decodeFileDescriptor()无法解析图片
     * 为Bitmap；
     * 解决方案，换成context.getAssets().open(fileName)用decodeStream()的方式解析图片
     * */
    @OnClick(R.id.id_btn1)
    void onclickBtn1() {

        int postion = mSpinner.getSelectedItemPosition();
        if (postion == 0) {
            loadBitmapBySurfaceView();
        } else if (postion == 1) {
            loadBitmapByGLSurfaceView();
        } else {
            loadBitmapByTextureView();
        }
    }

    // 将渲染结果保存
    @OnClick(R.id.id_btn2)
    void onclickBtn2() {
        int h = PixelUtil.dp2px(this,200);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,h);
        lp.addRule(RelativeLayout.BELOW, R.id.id_btn3);
        lp.topMargin = PixelUtil.dp2px(this,20);
        lp.leftMargin = PixelUtil.dp2px(this,20);
        lp.rightMargin = PixelUtil.dp2px(this,20);

        surfaceView = new MySurfaceView(this);
        contentLayout.addView(surfaceView);
        surfaceView.setLayoutParams(lp);

        Bitmap bm = BitmapFactory.decodeResource(getResources(),R.drawable.test_4);
        BitmapFactory.Options op1 = new BitmapFactory.Options();
        op1.inPreferredConfig = Bitmap.Config.ARGB_8888;

//        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.test_1,op1);
        MLog.log("大小 " + bm.getConfig());
        if (bm == null) {
            MLog.log("bitmap 为 null");
            return;
        }

        surfaceView.addTringleLine(bm);
    }

    @OnClick(R.id.id_btn3)
    void onclickBtn3() {
        Bitmap bt = null;
        if (surfaceView != null) {
            bt = surfaceView.getBitmap();
        }

        if (bt == null) {
            Toast.makeText(this,"图片还未渲染，请先渲染",Toast.LENGTH_SHORT);
        }

        MLog.log("保存结果" + MediaStore.Images.Media.insertImage(getContentResolver(),bt,"test","test"));
    }


    // 通过GLSusrfaceView加载一张图片
    private MyGLSurfaceView glSurfaceView;
    private void loadBitmapByGLSurfaceView() {
        int h = PixelUtil.dp2px(this,200);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,h);
        lp.addRule(RelativeLayout.BELOW, R.id.id_btn3);
        lp.topMargin = PixelUtil.dp2px(this,20);
        lp.leftMargin = PixelUtil.dp2px(this,20);
        lp.rightMargin = PixelUtil.dp2px(this,20);

        glSurfaceView = new MyGLSurfaceView(this);
        contentLayout.addView(glSurfaceView);
        glSurfaceView.setLayoutParams(lp);
//        glSurfaceView.setBackgroundColor(getResources().getColor(R.color.colorPrimary));

//        InputStream in = PathTool.getInputStream(this,"1.png");
//        Bitmap bm = BitmapFactory.decodeStream(in);
        Bitmap bm = BitmapFactory.decodeResource(getResources(),R.drawable.test_4);
        if (bm == null) {
            MLog.log("bitmap 为 null");
            return;
        }

        glSurfaceView.loadBitmap(bm);
    }

    // 通过SusrfaceView加载一张图片
    private MySurfaceView surfaceView;
    private void loadBitmapBySurfaceView() {
        int h = PixelUtil.dp2px(this,200);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,h);
        lp.addRule(RelativeLayout.BELOW, R.id.id_btn3);
        lp.topMargin = PixelUtil.dp2px(this,20);
        lp.leftMargin = PixelUtil.dp2px(this,20);
        lp.rightMargin = PixelUtil.dp2px(this,20);

        surfaceView = new MySurfaceView(this);
        contentLayout.addView(surfaceView);
        surfaceView.setLayoutParams(lp);

        Bitmap bm = BitmapFactory.decodeResource(getResources(),R.drawable.test_4);
        BitmapFactory.Options op1 = new BitmapFactory.Options();
        op1.inPreferredConfig = Bitmap.Config.ARGB_8888;

//        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.test_1,op1);
        MLog.log("大小 " + bm.getConfig());
        if (bm == null) {
            MLog.log("bitmap 为 null");
            return;
        }

        surfaceView.loadBitmap(bm);
    }

    // 通过TextureView加载一张图片
    private MyTextureView textureView;
    private void loadBitmapByTextureView() {
        int h = PixelUtil.dp2px(this,200);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,h);
        lp.addRule(RelativeLayout.BELOW, R.id.id_btn3);
        lp.topMargin = PixelUtil.dp2px(this,20);
        lp.leftMargin = PixelUtil.dp2px(this,20);
        lp.rightMargin = PixelUtil.dp2px(this,20);

        textureView = new MyTextureView(this);
        contentLayout.addView(textureView);
        textureView.setLayoutParams(lp);

        Bitmap bm = BitmapFactory.decodeResource(getResources(),R.drawable.test_4);
        if (bm == null) {
            MLog.log("bitmap 为 null");
            return;
        }

        textureView.loadBitmap(bm);
    }

    //  ====== 权限申请 6.0以上要访问应用内目录必须要进行运行时权限申请======= //
    public static final int EXTERNAL_STORAGE_REQ_CODE = 10 ;
    public void requestPermission(){
        //判断当前Activity是否已经获得了该权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            //如果App的权限申请曾经被用户拒绝过，就需要在这里跟用户做出解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "please give me the permission", Toast.LENGTH_SHORT).show();
            } else {
                //进行权限请求
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO}, EXTERNAL_STORAGE_REQ_CODE);
            }
        }
        else {
            Log.d("测试", "已经有权限了");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case EXTERNAL_STORAGE_REQ_CODE: {
                // 如果请求被拒绝，那么通常grantResults数组为空
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //申请成功，进行相应操作

                } else {
                    //申请失败，可以继续向用户解释。
                }
                return;
            }
        }
    }
    //  ====== 权限申请 ======= //
}
