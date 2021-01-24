//
// Created by hou on 2021/1/24.
//
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <jni.h>

#include "net.h"
typedef struct BoxInfo {
    float x1;
    float y1;
    float x2;
    float y2;
    float score;
    int label;
} BoxInfo;

namespace yolocv {
    typedef struct {
        int width;
        int height;
    } YoloSize;
}

// FIXME DeleteGlobalRef is missing for objCls
static jclass objCls = NULL;
static jmethodID constructortorId;
static jfieldID xId;
static jfieldID yId;
static jfieldID wId;
static jfieldID hId;
static jfieldID labelId;
static jfieldID probId;


class YoloV4 {
public:
    YoloV4(AAssetManager *mgr, const char *param, const char *bin);

    ~YoloV4();

    std::vector<BoxInfo>
    detect(JNIEnv *env, jobject image, bool use_GPU, float threshold, float nms_threshold);
    constexpr static const char* labels[]={"person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
                                    "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
                                    "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
                                    "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
                                    "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
                                    "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
                                    "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
                                    "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
                                    "hair drier", "toothbrush"};
private:
    static std::vector<BoxInfo>
    decode_infer(ncnn::Mat &data, const yolocv::YoloSize &frame_size, int net_size, int num_classes, float threshold);

//    static void nms(std::vector<BoxInfo>& result,float nms_threshold);
    ncnn::Net *Net;
    int input_size = 640 / 2;
    int num_class = 80;
public:
    static YoloV4 *detector;
    //static bool hasGPU;
};


//bool YoloV4::hasGPU = true;
YoloV4 *YoloV4::detector = nullptr;

YoloV4::YoloV4(AAssetManager *mgr, const char *param, const char *bin) {
    Net = new ncnn::Net();
    // opt 需要在加载前设置
    //hasGPU = ncnn::get_gpu_count() > 0;
    //Net->opt.use_vulkan_compute = hasGPU ;  // gpu
    Net->opt.use_fp16_arithmetic = true;  // fp16运算加速
    Net->load_param(mgr, param);
    Net->load_model(mgr, bin);
}

YoloV4::~YoloV4() {
    delete Net;
}

std::vector<BoxInfo>
YoloV4::detect(JNIEnv *env, jobject image, bool use_GPU, float threshold, float nms_threshold) {
    AndroidBitmapInfo img_size;
    AndroidBitmap_getInfo(env, image, &img_size);
    ncnn::Mat in_net = ncnn::Mat::from_android_bitmap_resize(env, image, ncnn::Mat::PIXEL_RGBA2RGB, input_size,
                                                             input_size);
    float norm[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    float mean[3] = {0, 0, 0};
    in_net.substract_mean_normalize(mean, norm);
    auto ex = Net->create_extractor();
    ex.set_light_mode(true);
    ex.set_num_threads(4);
    bool hasGPU = ncnn::get_gpu_count() > 0;
    ex.set_vulkan_compute(hasGPU & use_GPU);
    ex.input(0, in_net);
    std::vector<BoxInfo> result;
    ncnn::Mat blob;
    ex.extract("output", blob);
    auto boxes = decode_infer(blob, {(int) img_size.width, (int) img_size.height}, input_size, num_class, threshold);
    result.insert(result.begin(), boxes.begin(), boxes.end());
//    nms(result,nms_threshold);
    return result;
}

inline float fast_exp(float x) {
    union {
        uint32_t i;
        float f;
    } v{};
    v.i = (1 << 23) * (1.4426950409 * x + 126.93490512f);
    return v.f;
}

inline float sigmoid(float x) {
    return 1.0f / (1.0f + fast_exp(-x));
}

std::vector<BoxInfo>
YoloV4::decode_infer(ncnn::Mat &data, const yolocv::YoloSize &frame_size, int net_size, int num_classes, float threshold) {
    std::vector<BoxInfo> result;
    for (int i = 0; i < data.h; i++) {
        BoxInfo box;
        const float *values = data.row(i);
        box.label = values[0] - 1;
        box.score = values[1];
        box.x1 = values[2] * (float) frame_size.width;
        box.y1 = values[3] * (float) frame_size.height;
        box.x2 = values[4] * (float) frame_size.width;
        box.y2 = values[5] * (float) frame_size.height;
        result.push_back(box);
    }
    return result;
}

extern "C" {
JNIEXPORT jboolean JNICALL
Java_com_ironsublimate_ncnn_1objectdetection_YOLOv4Tiny_Init(JNIEnv *env, jobject thiz,
                                                             jobject assetManager) {
    if (YoloV4::detector == nullptr) {
        AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
        YoloV4::detector = new YoloV4(mgr, "yolov4-tiny-opt.param", "yolov4-tiny-opt.bin");

        // init jni glue
        jclass localObjCls = env->FindClass(
                "com/ironsublimate/ncnn_objectdetection/NCNNDetector$Obj");
        objCls = reinterpret_cast<jclass>(env->NewGlobalRef(localObjCls));

        constructortorId = env->GetMethodID(objCls, "<init>", "()V");

        xId = env->GetFieldID(objCls, "x", "F");
        yId = env->GetFieldID(objCls, "y", "F");
        wId = env->GetFieldID(objCls, "w", "F");
        hId = env->GetFieldID(objCls, "h", "F");
        labelId = env->GetFieldID(objCls, "label", "Ljava/lang/String;");
        probId = env->GetFieldID(objCls, "prob", "F");
    }
    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_ironsublimate_ncnn_1objectdetection_YOLOv4Tiny_Detect(JNIEnv *env, jobject thiz,
                                                               jobject bitmap, jboolean use_gpu) {
    float threshold = 0.3;
    float nms_threshold = 0.7;
    auto result = YoloV4::detector->detect(env, bitmap,use_gpu, threshold, nms_threshold);

    jobjectArray ret = env->NewObjectArray(result.size(), objCls, nullptr);
    int i = 0;

    for (const auto &box:result) {
        jobject jObj = env->NewObject(objCls, constructortorId, thiz);

        env->SetFloatField(jObj, xId, box.x1);
        env->SetFloatField(jObj, yId, box.y1);
        env->SetFloatField(jObj, wId, box.x2 - box.x1);
        env->SetFloatField(jObj, hId, box.y2 - box.y1);
        env->SetObjectField(jObj, labelId, env->NewStringUTF(YoloV4::labels[box.label]));
        env->SetFloatField(jObj, probId, box.score);

        env->SetObjectArrayElement(ret, i++, jObj);
    }
    return ret;
}

JNIEXPORT jboolean JNICALL
Java_com_ironsublimate_ncnn_1objectdetection_YOLOv4Tiny_Deinit(JNIEnv *env, jobject thiz) {
    delete YoloV4::detector;
    YoloV4::detector = nullptr;
    return JNI_TRUE;
}
}