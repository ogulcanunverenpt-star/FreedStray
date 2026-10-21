package com.godot.freedstray.voice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.godot.freedstray.network.EncryptedSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object VideoCallManager {

    private const val TAG = "VideoCallManager"

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _currentPeer = MutableStateFlow("")
    val currentPeer: StateFlow<String> = _currentPeer

    private val _remoteVideoFrame = MutableStateFlow<Bitmap?>(null)
    val remoteVideoFrame: StateFlow<Bitmap?> = _remoteVideoFrame

    private val managerScope = CoroutineScope(Dispatchers.IO)
    private var observeJob: Job? = null

    init {
        startObservingSocketSignals()
    }

    private fun startObservingSocketSignals() {
        observeJob?.cancel()
        observeJob = managerScope.launch {
            EncryptedSocketClient.incomingMessages.collectLatest { jsonStr ->
                try {
                    val jsonObj = JSONObject(jsonStr)
                    if (jsonObj.optString("type") == "video_signal") {
                        val action = jsonObj.optString("action")
                        val sender = jsonObj.optString("sender")
                        val recipient = jsonObj.optString("recipient")
                        val data = jsonObj.optString("data", "")

                        if (recipient == EncryptedSocketClient.currentUsername || recipient == "ALL") {
                            when (action) {
                                "VIDEO_OFFER" -> {
                                    _currentPeer.value = sender
                                    _callState.value = CallState.RINGING_INCOMING
                                }
                                "VIDEO_ACCEPT" -> {
                                    _currentPeer.value = sender
                                    _callState.value = CallState.CONNECTED
                                }
                                "VIDEO_REJECT", "VIDEO_END" -> {
                                    _callState.value = CallState.ENDED
                                    _remoteVideoFrame.value = null
                                    _currentPeer.value = ""
                                    _callState.value = CallState.IDLE
                                }
                                "VIDEO_CHUNK" -> {
                                    if (_callState.value == CallState.CONNECTED && !data.isNullOrEmpty()) {
                                        processIncomingVideoChunk(data)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling video signal: ${e.message}")
                }
            }
        }
    }

    fun startVideoCall(targetUsername: String) {
        if (targetUsername.isBlank()) return
        _currentPeer.value = targetUsername
        _callState.value = CallState.RINGING_OUTGOING

        val signal = JSONObject().apply {
            put("type", "video_signal")
            put("action", "VIDEO_OFFER")
            put("sender", EncryptedSocketClient.currentUsername)
            put("recipient", targetUsername)
        }
        EncryptedSocketClient.sendRawText(signal.toString())
    }

    fun acceptVideoCall() {
        val peer = _currentPeer.value
        if (peer.isBlank()) return

        _callState.value = CallState.CONNECTED

        val signal = JSONObject().apply {
            put("type", "video_signal")
            put("action", "VIDEO_ACCEPT")
            put("sender", EncryptedSocketClient.currentUsername)
            put("recipient", peer)
        }
        EncryptedSocketClient.sendRawText(signal.toString())
    }

    fun rejectVideoCall() {
        val peer = _currentPeer.value
        if (peer.isNotBlank()) {
            val signal = JSONObject().apply {
                put("type", "video_signal")
                put("action", "VIDEO_REJECT")
                put("sender", EncryptedSocketClient.currentUsername)
                put("recipient", peer)
            }
            EncryptedSocketClient.sendRawText(signal.toString())
        }
        _callState.value = CallState.ENDED
        _remoteVideoFrame.value = null
        _currentPeer.value = ""
        _callState.value = CallState.IDLE
    }

    fun endVideoCall() {
        val peer = _currentPeer.value
        if (peer.isNotBlank()) {
            val signal = JSONObject().apply {
                put("type", "video_signal")
                put("action", "VIDEO_END")
                put("sender", EncryptedSocketClient.currentUsername)
                put("recipient", peer)
            }
            EncryptedSocketClient.sendRawText(signal.toString())
        }
        _callState.value = CallState.ENDED
        _remoteVideoFrame.value = null
        _currentPeer.value = ""
        _callState.value = CallState.IDLE
    }

    fun sendVideoFrame(bitmap: Bitmap) {
        val peer = _currentPeer.value
        if (peer.isBlank() || _callState.value != CallState.CONNECTED) return

        managerScope.launch(Dispatchers.IO) {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                val rawBytes = stream.toByteArray()

                // Process through C-Native Video Processor
                val processedBytes = NativeVideoProcessor.processVideoFrame(
                    rawBytes,
                    bitmap.width,
                    bitmap.height,
                    50
                ) ?: rawBytes

                val base64Str = Base64.encodeToString(processedBytes, Base64.NO_WRAP)

                val signal = JSONObject().apply {
                    put("type", "video_signal")
                    put("action", "VIDEO_CHUNK")
                    put("sender", EncryptedSocketClient.currentUsername)
                    put("recipient", peer)
                    put("data", base64Str)
                }
                EncryptedSocketClient.sendRawText(signal.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding/sending video frame: ${e.message}")
            }
        }
    }

    private fun processIncomingVideoChunk(base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                _remoteVideoFrame.value = bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding incoming video frame: ${e.message}")
        }
    }
}
