package com.ironsublimate.ncnn_objectdetection;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public interface NCNNDetector {
    class Obj {
        public float x;
        public float y;
        public float w;
        public float h;
        public String label;
        public float prob;
    }
    public boolean Init(AssetManager mgr);

    public Obj[] Detect(Bitmap bitmap, boolean use_gpu);

    public boolean Deinit();
}
