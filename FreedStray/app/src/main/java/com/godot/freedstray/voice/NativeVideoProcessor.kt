package com.godot.freedstray.voice

import android.util.Log

object NativeVideoProcessor {

    private const val TAG = "NativeVideoProcessor"

    init {
        try {
            System.loadLibrary("freedstray_native")
            Log.i(TAG, "libfreedstray_native.so loaded for NativeVideoProcessor.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libfreedstray_native.so: ${e.message}")
        }
    }

    external fun processVideoFrame(
        frameData: ByteArray,
        width: Int,
        height: Int,
        quality: Int
    ): ByteArray?
}
