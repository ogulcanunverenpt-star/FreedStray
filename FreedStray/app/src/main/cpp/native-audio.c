#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "NativeAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define RING_BUFFER_SIZE (64 * 1024)

static int16_t g_ring_buffer[RING_BUFFER_SIZE];
static size_t g_write_pos = 0;
static size_t g_read_pos = 0;

JNIEXPORT void JNICALL
Java_com_godot_freedstray_voice_NativeAudioProcessor_initAudioBuffer(
    JNIEnv *env,
    jobject instance
) {
    g_write_pos = 0;
    g_read_pos = 0;
    memset(g_ring_buffer, 0, sizeof(g_ring_buffer));
    LOGI("Native Audio Ring Buffer initialized.");
}

JNIEXPORT jshortArray JNICALL
Java_com_godot_freedstray_voice_NativeAudioProcessor_processAudioFrame(
    JNIEnv *env,
    jobject instance,
    jshortArray pcmArray,
    jfloat gainFactor,
    jint noiseThreshold
) {
    if (!pcmArray) return NULL;

    jsize len = (*env)->GetArrayLength(env, pcmArray);
    jshort *pcm = (*env)->GetShortArrayElements(env, pcmArray, NULL);

    jshortArray resultArray = (*env)->NewShortArray(env, len);
    jshort *outPcm = malloc(len * sizeof(jshort));

    for (int i = 0; i < len; i++) {
        int32_t val = (int32_t)(pcm[i] * gainFactor);

        // Noise gate suppression
        if (abs(val) < noiseThreshold) {
            val = 0;
        }

        // Hard clipping protection
        if (val > 32767) val = 32767;
        if (val < -32768) val = -32768;

        outPcm[i] = (int16_t)val;
    }

    (*env)->SetShortArrayRegion(env, resultArray, 0, len, outPcm);

    free(outPcm);
    (*env)->ReleaseShortArrayElements(env, pcmArray, pcm, 0);

    return resultArray;
}
