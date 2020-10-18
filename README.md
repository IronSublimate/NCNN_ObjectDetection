# Android Camera Object Detection

use Tencent's [NCNN](https://github.com/Tencent/ncnn) framework and mobilenet-ssd

## Reference Code
+ [akhilbattula / android-camerax-java](https://github.com/akhilbattula/android-camerax-java) 
+ [nihui / ncnn-android-mobilenetssd](https://github.com/nihui/ncnn-android-mobilenetssd)

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