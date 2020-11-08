package com.ironsublimate.ncnn_objectdetection;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public interface NCNNDetector {

    public boolean Init(AssetManager mgr);

    public Obj[] Detect(Bitmap bitmap, boolean use_gpu);
}
