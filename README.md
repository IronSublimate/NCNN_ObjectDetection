# Android Camera Object Detection
> Author: IronSublimate
>
> Source Code: https://github.com/IronSublimate/NCNN_ObjectDetection

use Tencent's [NCNN](https://github.com/Tencent/ncnn) framework to run yolov5 and mobilenet-ssd

## Acknowledgement
+ CameraX Demo
  + [akhilbattula / android-camerax-java](https://github.com/akhilbattula/android-camerax-java) 
+ NCNN Object Detection Demo
  + [nihui / ncnn-android-mobilenetssd](https://github.com/nihui/ncnn-android-mobilenetssd)
  + [nihui / ncnn-android-yolov5](https://github.com/nihui/ncnn-android-yolov5)
+ Hide Action Bar, Status Bar and Navigation Bar
  + [gyf-dev / ImmersionBar](https://github.com/gyf-dev/ImmersionBar)

## How to Build and Run
### step1
https://github.com/Tencent/ncnn/releases

download ncnn-android-vulkan-lib.zip or build ncnn for android yourself

### step2
extract ncnn-android-vulkan-lib.zip into app/src/main/jni or change the ncnn path to yours in app/src/main/jni/CMakeLists.txt

### step3
open this project with Android Studio, build it and enjoy!

## screenshot
![](screenshot.png)

## Development

1. Write a java class extends NCNNDetector and fininsh 3 method inherited from  NCNNDetector. These methods might be **native** methods.

   ```java
   public boolean Init(AssetManager mgr);
   public NCNNDetector.Obj[] Detect(Bitmap bitmap, boolean use_gpu);
   public boolean Deinit();
   ```

2. Write \*jni.cpp to define native methods above and add your cpp in CMakeLists.txt.

    ```cmake
    add_library(
            ncnn_detector SHARED
            NCNN_detector_public_jni.cpp
            mobilenetssdncnn_jni.cpp
            yolov5ncnn_jni.cpp
            # add your cpp here
    )
    ```
    
    **Warning** If there are more than two dynamic library(.so) both linking to libncnn.a, you will get  Fatal signal 6 (SIGABRT) in running fuction *load_param*. You must compile all \*jni.cpp into one dynamic library(libncnn_detector.so).
    
    See also https://github.com/Tencent/ncnn/issues/976 
    
3. Add your class in MainActivity.java

    ```java
    private final HashMap<String, String> detectMethods = new HashMap<String, String>() {{
        // method name : class name
        //method name shows in GUI
        //class name is used to reflect
        put("MobileNet SSD", MobilenetSSDNcnn.class.getName());
        put("YOLOv5", YoloV5Ncnn.class.getName());
        //your class
    }};
    ```

    