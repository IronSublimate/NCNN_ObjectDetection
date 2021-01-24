//
// Create by RangiLyu
// 2020 / 10 / 2
//
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <jni.h>

#include "net.h"

typedef struct HeadInfo {
    std::string cls_layer;
    std::string dis_layer;
    int stride;
} HeadInfo;

typedef struct BoxInfo {
    float x1;
    float y1;
    float x2;
    float y2;
    float score;
    int label;
} BoxInfo;

// FIXME DeleteGlobalRef is missing for objCls
static jclass objCls = NULL;
static jmethodID constructortorId;
static jfieldID xId;
static jfieldID yId;
static jfieldID wId;
static jfieldID hId;
static jfieldID labelId;
static jfieldID probId;

class NanoDet {
public:
    NanoDet(AAssetManager *mgr, const char *param, const char *bin);

    ~NanoDet();

    std::vector<BoxInfo>
    detect(JNIEnv *env, jobject image, bool use_gpu, float score_threshold,
           float nms_threshold);

    constexpr static const char *labels[] = {"person", "bicycle", "car", "motorcycle", "airplane",
                                             "bus",
                                             "train", "truck", "boat", "traffic light",
                                             "fire hydrant", "stop sign", "parking meter", "bench",
                                             "bird",
                                             "cat", "dog", "horse", "sheep", "cow",
                                             "elephant", "bear", "zebra", "giraffe", "backpack",
                                             "umbrella",
                                             "handbag", "tie", "suitcase", "frisbee",
                                             "skis", "snowboard", "sports ball", "kite",
                                             "baseball bat",
                                             "baseball glove", "skateboard", "surfboard",
                                             "tennis racket", "bottle", "wine glass", "cup", "fork",
                                             "knife",
                                             "spoon", "bowl", "banana", "apple",
                                             "sandwich", "orange", "broccoli", "carrot", "hot dog",
                                             "pizza",
                                             "donut", "cake", "chair", "couch",
                                             "potted plant", "bed", "dining table", "toilet", "tv",
                                             "laptop",
                                             "mouse", "remote", "keyboard", "cell phone",
                                             "microwave", "oven", "toaster", "sink", "refrigerator",
                                             "book",
                                             "clock", "vase", "scissors", "teddy bear",
                                             "hair drier", "toothbrush"};
    ncnn::Net *Net;
private:
    void preprocess(JNIEnv *env, jobject image, ncnn::Mat &in);

    void decode_infer(ncnn::Mat &cls_pred, ncnn::Mat &dis_pred, int stride, float threshold,
                      std::vector<std::vector<BoxInfo>> &results, float width_ratio,
                      float height_ratio);

    BoxInfo disPred2Bbox(const float *&dfl_det, int label, float score, int x, int y, int stride,
                         float width_ratio, float height_ratio);

    static void nms(std::vector<BoxInfo> &result, float nms_threshold);

    int input_size = 320;
    int num_class = 80;
    int reg_max = 7;
    std::vector<HeadInfo> heads_info{
            // cls_pred|dis_pred|stride
            {"792", "795", 8},
            {"814", "817", 16},
            {"836", "839", 32},
    };

public:
    static NanoDet *detector;
};


NanoDet *NanoDet::detector = nullptr;

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

template<typename _Tp>
int activation_function_softmax(const _Tp *src, _Tp *dst, int length) {
    const _Tp alpha = *std::max_element(src, src + length);
    _Tp denominator{0};

    for (int i = 0; i < length; ++i) {
        dst[i] = fast_exp(src[i] - alpha);
        denominator += dst[i];
    }

    for (int i = 0; i < length; ++i) {
        dst[i] /= denominator;
    }

    return 0;
}

NanoDet::NanoDet(AAssetManager *mgr, const char *param, const char *bin) {
    this->Net = new ncnn::Net();
    // opt 需要在加载前设置
    this->Net->opt.use_vulkan_compute = false; //hasGPU && useGPU;  // gpu
    this->Net->opt.use_fp16_arithmetic = true;  // fp16运算加速
    this->Net->opt.use_fp16_packed = true;
    this->Net->opt.use_fp16_storage = true;
    this->Net->load_param(mgr, param);
    this->Net->load_model(mgr, bin);
}

NanoDet::~NanoDet() {
    delete this->Net;
}

void NanoDet::preprocess(JNIEnv *env, jobject image, ncnn::Mat &in) {
    in = ncnn::Mat::from_android_bitmap_resize(env, image, ncnn::Mat::PIXEL_RGBA2BGR, input_size,
                                               input_size);
//    in = ncnn::Mat::from_pixels(image.data, ncnn::Mat::PIXEL_BGR, img_w, img_h);
    //in = ncnn::Mat::from_pixels_resize(image.data, ncnn::Mat::PIXEL_BGR, img_w, img_h, this->input_width, this->input_height);

    const float mean_vals[3] = {103.53f, 116.28f, 123.675f};
    const float norm_vals[3] = {0.017429f, 0.017507f, 0.017125f};
    in.substract_mean_normalize(mean_vals, norm_vals);
}

std::vector<BoxInfo>
NanoDet::detect(JNIEnv *env, jobject image, bool use_gpu, float score_threshold,
                float nms_threshold) {
    AndroidBitmapInfo img_size;
    AndroidBitmap_getInfo(env, image, &img_size);
    float width_ratio = (float) img_size.width / (float) this->input_size;
    float height_ratio = (float) img_size.height / (float) this->input_size;

    ncnn::Mat input;
    this->preprocess(env, image, input);

    auto ex = this->Net->create_extractor();
    ex.set_light_mode(true);
    ex.set_num_threads(4);
    bool hasGPU = ncnn::get_gpu_count() > 0;
    ex.set_vulkan_compute(hasGPU & use_gpu);
    ex.input("input.1", input);
    std::vector<std::vector<BoxInfo>> results;
    results.resize(this->num_class);

    for (const auto &head_info : this->heads_info) {
        ncnn::Mat dis_pred;
        ncnn::Mat cls_pred;
        ex.extract(head_info.dis_layer.c_str(), dis_pred);
        ex.extract(head_info.cls_layer.c_str(), cls_pred);

        this->decode_infer(cls_pred, dis_pred, head_info.stride, score_threshold, results,
                           width_ratio, height_ratio);
    }

    std::vector<BoxInfo> dets;
    for (int i = 0; i < (int) results.size(); i++) {
        this->nms(results[i], nms_threshold);

        for (auto box : results[i]) {
            dets.push_back(box);
        }
    }
    return dets;
}


void NanoDet::decode_infer(ncnn::Mat &cls_pred, ncnn::Mat &dis_pred, int stride, float threshold,
                           std::vector<std::vector<BoxInfo>> &results, float width_ratio,
                           float height_ratio) {
    int feature_h = this->input_size / stride;
    int feature_w = this->input_size / stride;

    //cv::Mat debug_heatmap = cv::Mat(feature_h, feature_w, CV_8UC3);
    for (int idx = 0; idx < feature_h * feature_w; idx++) {
        const float *scores = cls_pred.row(idx);
        int row = idx / feature_w;
        int col = idx % feature_w;
        float score = 0;
        int cur_label = 0;
        for (int label = 0; label < this->num_class; label++) {
            if (scores[label] > score) {
                score = scores[label];
                cur_label = label;
            }
        }
        if (score > threshold) {
            //std::cout << "label:" << cur_label << " score:" << score << std::endl;
            const float *bbox_pred = dis_pred.row(idx);
            results[cur_label].push_back(
                    this->disPred2Bbox(bbox_pred, cur_label, score, col, row, stride, width_ratio,
                                       height_ratio));
            //debug_heatmap.at<cv::Vec3b>(row, col)[0] = 255;
            //cv::imshow("debug", debug_heatmap);
        }

    }
}

BoxInfo
NanoDet::disPred2Bbox(const float *&dfl_det, int label, float score, int x, int y, int stride,
                      float width_ratio, float height_ratio) {
    float ct_x = (x + 0.5) * stride;
    float ct_y = (y + 0.5) * stride;
    std::vector<float> dis_pred;
    dis_pred.resize(4);
    for (int i = 0; i < 4; i++) {
        float dis = 0;
        auto *dis_after_sm = new float[this->reg_max + 1];
        activation_function_softmax(dfl_det + i * (this->reg_max + 1), dis_after_sm,
                                    this->reg_max + 1);
        for (int j = 0; j < this->reg_max + 1; j++) {
            dis += j * dis_after_sm[j];
        }
        dis *= stride;
        //std::cout << "dis:" << dis << std::endl;
        dis_pred[i] = dis;
        delete[] dis_after_sm;
    }
    float xmin = (std::max)(ct_x - dis_pred[0], .0f) * width_ratio;
    float ymin = (std::max)(ct_y - dis_pred[1], .0f) * height_ratio;
    float xmax = (std::min)(ct_x + dis_pred[2], (float) this->input_size) * width_ratio;
    float ymax = (std::min)(ct_y + dis_pred[3], (float) this->input_size) * height_ratio;

    //std::cout << xmin << "," << ymin << "," << xmax << "," << xmax << "," << std::endl;
    return BoxInfo{xmin, ymin, xmax, ymax, score, label};
}

void NanoDet::nms(std::vector<BoxInfo> &input_boxes, float NMS_THRESH) {
    std::sort(input_boxes.begin(), input_boxes.end(),
              [](BoxInfo a, BoxInfo b) { return a.score > b.score; });
    std::vector<float> vArea(input_boxes.size());
    for (int i = 0; i < int(input_boxes.size()); ++i) {
        vArea[i] = (input_boxes.at(i).x2 - input_boxes.at(i).x1 + 1)
                   * (input_boxes.at(i).y2 - input_boxes.at(i).y1 + 1);
    }
    for (int i = 0; i < int(input_boxes.size()); ++i) {
        for (int j = i + 1; j < int(input_boxes.size());) {
            float xx1 = (std::max)(input_boxes[i].x1, input_boxes[j].x1);
            float yy1 = (std::max)(input_boxes[i].y1, input_boxes[j].y1);
            float xx2 = (std::min)(input_boxes[i].x2, input_boxes[j].x2);
            float yy2 = (std::min)(input_boxes[i].y2, input_boxes[j].y2);
            float w = (std::max)(float(0), xx2 - xx1 + 1);
            float h = (std::max)(float(0), yy2 - yy1 + 1);
            float inter = w * h;
            float ovr = inter / (vArea[i] + vArea[j] - inter);
            if (ovr >= NMS_THRESH) {
                input_boxes.erase(input_boxes.begin() + j);
                vArea.erase(vArea.begin() + j);
            } else {
                j++;
            }
        }
    }
}

extern "C" {
JNIEXPORT jboolean JNICALL
Java_com_ironsublimate_ncnn_1objectdetection_NanoDet_Init(JNIEnv *env, jobject thiz, jobject assetManager) {
    if (NanoDet::detector == nullptr) {
        AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
        NanoDet::detector = new NanoDet(mgr, "nanodet_m.param", "nanodet_m.bin");

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
Java_com_ironsublimate_ncnn_1objectdetection_NanoDet_Detect(JNIEnv *env, jobject thiz,
                                                            jobject bitmap, jboolean use_gpu) {
    float threshold = 0.5;
    float nms_threshold = 0.4;
    auto result = NanoDet::detector->detect(env, bitmap, use_gpu, threshold, nms_threshold);

    jobjectArray ret = env->NewObjectArray(result.size(), objCls, nullptr);
    int i = 0;

    for (const auto &box:result) {
        jobject jObj = env->NewObject(objCls, constructortorId, thiz);

        env->SetFloatField(jObj, xId, box.x1);
        env->SetFloatField(jObj, yId, box.y1);
        env->SetFloatField(jObj, wId, box.x2 - box.x1);
        env->SetFloatField(jObj, hId, box.y2 - box.y1);
        env->SetObjectField(jObj, labelId, env->NewStringUTF(NanoDet::labels[box.label]));
        env->SetFloatField(jObj, probId, box.score);

        env->SetObjectArrayElement(ret, i++, jObj);
    }
    return ret;
}

JNIEXPORT jboolean JNICALL
Java_com_ironsublimate_ncnn_1objectdetection_NanoDet_Deinit(JNIEnv *env, jobject thiz) {
    delete NanoDet::detector;
    NanoDet::detector = nullptr;
    return JNI_TRUE;
}
}