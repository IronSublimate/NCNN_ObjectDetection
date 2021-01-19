package com.ironsublimate.ncnn_objectdetection;

import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.core.Camera;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private  static final int settings_result_code=2;
    private Executor executor = Executors.newSingleThreadExecutor();
    private int REQUEST_CODE_PERMISSIONS = 1001;
    //    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    private ProcessCameraProvider cameraProvider = null;
    //    private NCNNDetector ncnnDetector = new MobilenetSSDNcnn();
    private NCNNDetector ncnnDetector = null;

    //settings
    private boolean useGPU = false;

    PreviewView mPreviewView;
    //    ImageView imageView;
//    ImageView captureImage;
//    TextView textView1;
    Overlay overlay;
    Button button_setting;
    volatile boolean isProcessing = false; //太sb了

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //隐藏标题栏
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        transparentTopBottom();
//        fullScreen();
        setContentView(R.layout.activity_main);

        mPreviewView = findViewById(R.id.previewView);
//        imageView = findViewById(R.id.imageView);

//        captureImage = findViewById(R.id.captureImg);
//        textView1 = findViewById(R.id.textView1);
        overlay = findViewById(R.id.overlay);
        button_setting = findViewById(R.id.button_setting);
//        findViewById(R.id.linearLayout).bringToFront();
//        findViewById(R.id.previewView).bringToFront();
        button_setting.setOnClickListener((View view) -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, settings_result_code);
        });
        Log.d(TAG, "On Create");

        if (ncnnDetector == null) {
            ncnnDetector = new YoloV5Ncnn();
            boolean ret_init = ncnnDetector.Init(getAssets());
            if (!ret_init) {
                Log.e("MainActivity", "mobilenetssdncnn Init failed");
            }
        }

        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        //初始化目标检测

    }

    //
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, requestCode, data);
        switch (requestCode) {
            case settings_result_code:
                if (resultCode == RESULT_OK) {
                    this.useGPU = data.getBooleanExtra(this.getResources().getString(R.string.useGPU), false);
                    Toast.makeText(MainActivity.this, "" + this.useGPU, Toast.LENGTH_SHORT).show();
                    int method_index = data.getIntExtra(this.getResources().getString(R.string.method_index), -1);
                    if (method_index >= 0) {
                        Toast.makeText(MainActivity.this, "" + method_index, Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            default:
        }
    }

    @Override
    protected void onDestroy() {
//        if(imageAnalysis != null) {
//            imageAnalysis.clearAnalyzer();
//
//        }
        while (isProcessing) {
        } //image analyse时间过长，导致转屏的时候libc空指针异常崩溃
//        if (cameraProvider != null) {
//            cameraProvider.unbindAll();
//        }
        super.onDestroy();
    }

    //顶部底部全透明
    private void transparentTopBottom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 以上 全透明
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            // 状态栏（以上几行代码必须，参考setStatusBarColor|setNavigationBarColor方法源码）
            window.setStatusBarColor(Color.TRANSPARENT);
            // 虚拟导航键
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 4.4 以上 半透明
            Window window = getWindow();
            // 状态栏
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            // 虚拟导航键
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
    }

    private void fullScreen() {
        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void startCamera() {

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);

            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    //    static int i = 0;
    ImageAnalysis imageAnalysis = null;

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(executor, image -> {
            isProcessing = true;
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            // insert your code here.
            Bitmap bitmap = mPreviewView.getBitmap();
//            Log.i(TAG,"Bitmap width:"+bitmap.getWidth());
//            Log.i(TAG,"Bitmap height:"+bitmap.getHeight());
            if (bitmap != null) {
                Log.d(TAG, "before detect");
                NCNNDetector.Obj[] objs = ncnnDetector.Detect(bitmap, false);
                Log.d(TAG, "after detect");
                runOnUiThread(() -> {
                    overlay.drawRects(objs);
                });
            }
            image.close();
            isProcessing = false;
        });

        ImageCapture.Builder builder = new ImageCapture.Builder();

        //Vendor-Extensions (The CameraX extensions dependency in build.gradle)
        HdrImageCaptureExtender hdrImageCaptureExtender = HdrImageCaptureExtender.create(builder);

        // Query if extension is available (optional).
        if (hdrImageCaptureExtender.isExtensionAvailable(cameraSelector)) {
            // Enable the extension if available.
            hdrImageCaptureExtender.enableExtension(cameraSelector);
        }

        final ImageCapture imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();

        preview.setSurfaceProvider(mPreviewView.createSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis, imageCapture);
//        captureImage.setOnClickListener(v -> {
//
//            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
//            File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date()) + ".jpg");
//
//            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
//            imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
//                @Override
//                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
//                    new Handler(Looper.getMainLooper()).post(new Runnable() {
//                        @Override
//                        public void run() {
//                            Toast.makeText(MainActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
//                        }
//                    });
//                }
//
//                @Override
//                public void onError(@NonNull ImageCaptureException error) {
//                    error.printStackTrace();
//                }
//            });
//        });
    }

    private Bitmap rotateBitmap(Bitmap origin, float alpha) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(alpha);
        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    private void YUV2Bitmap() {

    }

    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {

        }

        return app_folder_path;
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }
}
