package com.camera.haobo.camerademo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Camera;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SeekBar mSeekBar; //滑动调整
    private RelativeLayout optionArea;
    private TextView mDevLog; //DevLog
    private TextView mFilterName;
    private Button mSwitchBtn; //切换前后置相机按钮
    //private ListView mFilterContainer;
    private LinearLayout mFilterContainer;
    private int[] itemId;
    private String[] filterNames; //滤镜名
    private String[] items;
    private ImageView[] itemBg; //滤镜选中背景
    private int lastItemBgIndex;

    private int frontCameraId; //前置相机id
    private int currentCameraId; //当前相机id
    private FrameLayout preview;
    private Camera mCamera;
    private CameraPreview mPreview;

    private static final String TAG = "CameraDemo";
    private static final int REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        //初始化界面
        initFilter();

        //获得前置相机id
        int num = Camera.getNumberOfCameras();
        for (int i = 0; i < num; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                frontCameraId = i;
                break;
            }
        }
        currentCameraId = frontCameraId;
    }

    public void initFilter() {
        preview = findViewById(R.id.camera_preview);
        optionArea = findViewById(R.id.option_area);
        mDevLog = findViewById(R.id.devLog);
        mFilterName = findViewById(R.id.filterName);
        mFilterContainer = findViewById(R.id.filterContainer);
        mSwitchBtn = findViewById(R.id.buttonSwitch);
        mSeekBar = findViewById(R.id.seekBar);

        //设置滑动条响应事件
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d(TAG, "onProgressChanged " + progress);
                mDevLog.setText("DevLog:\nratio " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //根据 R.array.filter_items 的配置动态添加滤镜item
        //drawable 文件夹中的图片名需与 filter_items 一一对应
        //在R.array.filter_name 中配置滤镜中文名（drawable文件夹中的文件名只允许英文字母和数字）
        Resources resources = this.getResources();
        filterNames = resources.getStringArray(R.array.filter_name);
        items = resources.getStringArray(R.array.filter_items);
        itemId = new int[filterNames.length];
        itemBg = new ImageView[filterNames.length];
        for (int i = 0; i < items.length; i++) {
            final int resourceId = resources.getIdentifier(items[i], "drawable", this.getPackageName());
            if (resourceId == 0)
                continue;

            LinearLayout item = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.filter_item, null);
            item.setId(4100 + i);
            itemId[i] = item.getId();

            FrameLayout bgView = (FrameLayout) item.getChildAt(0);
            itemBg[i] = (ImageView) bgView.getChildAt(0);
            CardView cardView = (CardView) bgView.getChildAt(1);
            cardView.setCardBackgroundColor(Color.TRANSPARENT);
            cardView.setCardElevation(0);

            ImageView imageView = (ImageView) (cardView.getChildAt(0));
            imageView.setId(i);
            imageView.setImageResource(resourceId);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);

            TextView textView = (TextView) item.getChildAt(1);
            textView.setText(filterNames[i]);

            mFilterContainer.addView(item);
        }
    }

    //切换前置后置相机
    public void onSwitchCamera(View v) {
        mCamera.stopPreview();
        mCamera.release();

        if (currentCameraId == 0)
            currentCameraId = frontCameraId;
        else
            currentCameraId = 0;

        mCamera = getCameraInstance(currentCameraId);
        mPreview.setCamera(mCamera);
    }

    //滤镜点击事件
    public void onItemClick(View v) {
        for (int i = 0; i < itemId.length; i++) {
            if (v.getId() == itemId[i]) {
                mDevLog.setText("DevLog:\n" + filterNames[i]);
                Log.d(TAG, "onItemClick " + filterNames[i]);

                if (lastItemBgIndex != i) {
                    itemBg[lastItemBgIndex].setImageDrawable(null); //清除上一个滤镜的选中效果
                    if (i != 0) {
                        itemBg[i].setImageResource(R.drawable.filter_bg_selected); //设置点击滤镜的选中效果
                    }
                    lastItemBgIndex = i;
                }
                break;
            }
        }
    }

    //初始化相机
    public void initCamera() {

        if (mCamera == null) {
            mCamera = getCameraInstance(currentCameraId);

            if (mPreview == null) {
                mPreview = new CameraPreview(this, mCamera);
                preview.addView(mPreview);
            } else {
                mPreview.setCamera(mCamera);
            }
        }
    }

    //检查并请求权限，拥有权限的情况下初始化相机
    public Boolean checkPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            initCamera();
            return true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE);
            return false;
        }
    }

    //请求权限结果回调，请求成功则初始化相机，否则继续请求
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initCamera();
                } else {
                    checkPermission();
                }
                return;
            }
        }
    }

    //请求相机实例，一般 id 为 0 代表后置主相机
    public Camera getCameraInstance(int id) {
        Camera c = null;
        try {
            c = Camera.open(id);
            c.setDisplayOrientation(90);
            Camera.Size size = c.getParameters().getPreviewSize();
            List<Camera.Size> sizes = c.getParameters().getSupportedPictureSizes();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return c;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //每次启动都检查权限
        checkPermission();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //切后台时释放相机
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    //相机预览实现
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public void setCamera(Camera camera) {
            mCamera = camera;
            startPreview();
        }

        public void startPreview() {
            //获取屏幕比例
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            mDevLog.setText("screenX:" + size.x + "\nscreenY:" + size.y);
            //获取相机预览比例
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            float ratio = previewSize.height / (float)previewSize.width;
            if(previewSize.height < previewSize.width)
                ratio = previewSize.width / (float)previewSize.height;

            //屏幕比例大于相机预览比例的情况下（新款的全面屏基本都会这样）调整界面布局尺寸到相机预览比例
            if(size.y / (float)size.x > ratio){
                optionArea.setBackgroundColor(Color.WHITE);
                int height = (int)(size.x * ratio);
                getHolder().setFixedSize(size.x, height);
                this.requestLayout();
            }

            try {
                mCamera.setPreviewDisplay(mHolder);
                Camera.Parameters parameters = mCamera.getParameters();
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

                mCamera.setParameters(parameters);
                mCamera.startPreview();
            } catch (Exception e) {
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            //在 onPause 中已处理
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

            if (mHolder.getSurface() == null) {
                return;
            }

            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }

            startPreview();
        }
    }

}


