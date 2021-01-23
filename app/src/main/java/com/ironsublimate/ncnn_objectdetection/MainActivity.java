package com.ironsublimate.ncnn_objectdetection;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public static final String detectMethodsIntentName = "DETECT_METHOD_INTENT";
    private static final String TAG = "MainActivity";
    private static final int settings_result_code = 2;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final int REQUEST_CODE_PERMISSIONS = 1001;
    //    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    private ProcessCameraProvider cameraProvider = null;
    private NCNNDetector ncnnDetector = null;
    private static final HashMap<String, String> detectMethods = new HashMap<String, String>() {{
        // method name : class name
        //method name shows in GUI
        //class name is used to reflect
        put("MobileNet SSD", MobilenetSSDNcnn.class.getName());
        put("YOLOv5", YoloV5Ncnn.class.getName());
    }};
    //settings
    private boolean useGPU = false;

    // UI
    PreviewView mPreviewView;
    //    ImageView imageView;
//    ImageView captureImage;
//    TextView textView1;
    Overlay overlay;
    Button button_setting;
    TextView textViewFPS;
    LinearLayout ll_settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //UI
        mPreviewView = findViewById(R.id.previewView);
        overlay = findViewById(R.id.overlay);
        overlay.setOnClickListener(view->{
//            supportRequestWindowFeature(Window.FEATURE_ACTION_BAR);

//            button_setting.setVisibility(View.INVISIBLE);

//            Toast.makeText(this,"overlay click",Toast.LENGTH_SHORT);
        });

        textViewFPS=findViewById(R.id.textView_FPS);
        ll_settings=findViewById(R.id.ll_settings);
        ll_settings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(detectMethodsIntentName, this.detectMethods);
            startActivity(intent);
        });

        ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_BAR).init();

        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        Log.d(TAG, "On Create");


        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        //初始化目标检测
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Load  Preference

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.useGPU = sharedPreferences.getBoolean(this.getResources().getString(R.string.useGPU), false);
        String methodClassName = sharedPreferences.getString(this.getResources().getString(R.string.method_index), "");

        //Choose a new detector in settings or first init detector
        if (ncnnDetector == null || !ncnnDetector.getClass().getName().equals(methodClassName)) {
            try {
                ncnnDetector = (NCNNDetector) (Class.forName(methodClassName).newInstance());
                boolean ret_init = ncnnDetector.Init(getAssets());
                if (!ret_init) {
                    Log.e(TAG, "NCNN Detector Init failed");
                }
            } catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        waitCameraProcessFinished();
        //image analyse时间过长，导致转屏的时候libc空指针异常崩溃
        super.onDestroy();
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

    @SuppressLint("DefaultLocale")
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
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            // insert your code here.
            Bitmap bitmap = mPreviewView.getBitmap();
//            Log.i(TAG,"Bitmap width:"+bitmap.getWidth());
//            Log.i(TAG,"Bitmap height:"+bitmap.getHeight());
            if (bitmap != null) {
//                Log.d(TAG, "before detect");
                long tick=System.nanoTime();
                NCNNDetector.Obj[] objs = ncnnDetector.Detect(bitmap, this.useGPU);
                long tock=System.nanoTime();
//                Log.d(TAG, "after detect");`
                double fps=1.0e9/(tock-tick);
                runOnUiThread(() -> {
                    overlay.drawRects(objs);
                    textViewFPS.setText(String.format("FPS: %4.2f",fps));
                });
            }
            image.close();
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

    //will catch libc NULL pointer error if not wait for detection finished
    private void waitCameraProcessFinished() {
        executor.shutdownNow();
        imageAnalysis.clearAnalyzer();
        while(!executor.isTerminated()){}
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