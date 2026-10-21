package com.godot.freedstray.voice

import android.util.Log

object NativeAudioProcessor {

    private const val TAG = "NativeAudioProcessor"

    init {
        try {
            System.loadLibrary("freedstray_native")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native audio processor library: ${e.message}")
        }
    }

    external fun initAudioBuffer()
    external fun processAudioFrame(pcmArray: ShortArray, gainFactor: Float = 1.2f, noiseThreshold: Int = 100): ShortArray?
}
