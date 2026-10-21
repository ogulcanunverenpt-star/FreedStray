#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "NativeVideo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

JNIEXPORT jbyteArray JNICALL
Java_com_godot_freedstray_voice_NativeVideoProcessor_processVideoFrame(
    JNIEnv *env,
    jobject instance,
    jbyteArray frameData,
    jint width,
    jint height,
    jint quality
) {
    if (!frameData) return NULL;

    jsize len = (*env)->GetArrayLength(env, frameData);
    jbyte *bytes = (*env)->GetByteArrayElements(env, frameData, NULL);

    // Fast pass-through or frame optimization / byte buffer processing in C
    jbyteArray resultArray = (*env)->NewByteArray(env, len);
    if (resultArray) {
        (*env)->SetByteArrayRegion(env, resultArray, 0, len, bytes);
    }

    (*env)->ReleaseByteArrayElements(env, frameData, bytes, JNI_ABORT);
    return resultArray;
}
