package com.ironsublimate.ncnn_objectdetection;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class YOLOv4Tiny extends NCNNDetector {
    public native boolean Init(AssetManager mgr);

    public native Obj[] Detect(Bitmap bitmap, boolean use_gpu);

    public native boolean Deinit();
}
