//
// Created by IronSublimate on 2021/1/22.
//

//android
#include <jni.h>
#include <android/log.h>

//ncnn
#include "gpu.h"
#include "platform.h"

extern "C" {
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "NCNN Detector", "JNI_OnLoad");

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "NCNN Detector", "JNI_OnUnload");

    ncnn::destroy_gpu_instance();
}

}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_ironsublimate_ncnn_1objectdetection_NCNNDetector_get_1ncnn_1version(JNIEnv *env,
                                                                             jclass clazz) {
    return env->NewStringUTF(NCNN_VERSION_STRING);
}