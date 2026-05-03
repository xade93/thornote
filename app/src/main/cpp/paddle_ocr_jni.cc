#include "pipeline.h"
#include <android/bitmap.h>
#include <jni.h>
#include <memory>
#include <opencv2/imgproc.hpp>
#include <string>

namespace {

std::string ToString(JNIEnv *env, jstring value) {
  const char *chars = env->GetStringUTFChars(value, nullptr);
  std::string result(chars == nullptr ? "" : chars);
  if (chars != nullptr) {
    env->ReleaseStringUTFChars(value, chars);
  }
  return result;
}

cv::Mat BitmapToBgr(JNIEnv *env, jobject bitmap) {
  AndroidBitmapInfo info;
  if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
    return cv::Mat();
  }

  void *pixels = nullptr;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
    return cv::Mat();
  }

  cv::Mat bgr;
  if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
    cv::Mat rgba(info.height, info.width, CV_8UC4, pixels, info.stride);
    cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);
  } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
    cv::Mat rgb565(info.height, info.width, CV_8UC2, pixels, info.stride);
    cv::cvtColor(rgb565, bgr, cv::COLOR_BGR5652BGR);
  }

  AndroidBitmap_unlockPixels(env, bitmap);
  return bgr;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_thornotes_ocr_PaddleOcrNative_nativeInit(
    JNIEnv *env, jobject, jstring detModelPath, jstring clsModelPath,
    jstring recModelPath, jstring configPath, jstring labelPath,
    jint cpuThreadNum, jstring cpuPowerMode) {
  try {
    auto pipeline = std::make_unique<Pipeline>(
        ToString(env, detModelPath), ToString(env, clsModelPath),
        ToString(env, recModelPath), ToString(env, cpuPowerMode), cpuThreadNum,
        ToString(env, configPath), ToString(env, labelPath));
    return reinterpret_cast<jlong>(pipeline.release());
  } catch (...) {
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_thornotes_ocr_PaddleOcrNative_nativeRelease(JNIEnv *, jobject,
                                                     jlong ctx) {
  if (ctx != 0) {
    delete reinterpret_cast<Pipeline *>(ctx);
  }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_thornotes_ocr_PaddleOcrNative_nativeRecognizeBitmap(JNIEnv *env,
                                                             jobject,
                                                             jlong ctx,
                                                             jobject bitmap) {
  if (ctx == 0 || bitmap == nullptr) {
    return env->NewStringUTF("");
  }

  try {
    cv::Mat bgr = BitmapToBgr(env, bitmap);
    std::string text = reinterpret_cast<Pipeline *>(ctx)->Recognize(bgr);
    return env->NewStringUTF(text.c_str());
  } catch (...) {
    return env->NewStringUTF("");
  }
}

