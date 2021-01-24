package com.ironsublimate.ncnn_objectdetection;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public abstract class NCNNDetector {
    class Obj {
        public float x;
        public float y;
        public float w;
        public float h;
        public String label;
        public float prob;
    }
    static {
        System.loadLibrary("ncnn_detector");
    }
    public abstract boolean Init(AssetManager mgr);

    public abstract Obj[] Detect(Bitmap bitmap, boolean use_gpu);

    public abstract boolean Deinit();
}
