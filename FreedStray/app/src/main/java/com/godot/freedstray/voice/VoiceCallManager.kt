package com.godot.freedstray.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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

enum class CallState {
    IDLE,
    RINGING_OUTGOING,
    RINGING_INCOMING,
    CONNECTED,
    ENDED
}

object VoiceCallManager {

    private const val TAG = "VoiceCallManager"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val BUFFER_SIZE_FACTOR = 2

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _currentPeer = MutableStateFlow("")
    val currentPeer: StateFlow<String> = _currentPeer

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var isRecording = false
    private var isPlaying = false

    private val managerScope = CoroutineScope(Dispatchers.IO)
    private var recordJob: Job? = null
    private var observeJob: Job? = null

    init {
        NativeAudioProcessor.initAudioBuffer()
        startObservingSocketSignals()
    }

    private fun startObservingSocketSignals() {
        observeJob?.cancel()
        observeJob = managerScope.launch {
            EncryptedSocketClient.incomingMessages.collectLatest { jsonStr ->
                try {
                    val jsonObj = JSONObject(jsonStr)
                    if (jsonObj.optString("type") == "voice_signal") {
                        val action = jsonObj.optString("action")
                        val sender = jsonObj.optString("sender")
                        val recipient = jsonObj.optString("recipient")
                        val data = jsonObj.optString("data", null)

                        if (recipient == EncryptedSocketClient.currentUsername || recipient == "ALL") {
                            when (action) {
                                "CALL_OFFER" -> {
                                    _currentPeer.value = sender
                                    _callState.value = CallState.RINGING_INCOMING
                                }
                                "CALL_ACCEPT" -> {
                                    _currentPeer.value = sender
                                    _callState.value = CallState.CONNECTED
                                    startAudioEngine()
                                }
                                "CALL_REJECT", "CALL_END" -> {
                                    stopAudioEngine()
                                    _callState.value = CallState.ENDED
                                    _currentPeer.value = ""
                                    _callState.value = CallState.IDLE
                                }
                                "AUDIO_CHUNK" -> {
                                    if (_callState.value == CallState.CONNECTED && !data.isNullOrEmpty()) {
                                        playIncomingAudioChunk(data)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling voice signal: ${e.message}")
                }
            }
        }
    }

    fun initiateCall(peerUsername: String) {
        _currentPeer.value = peerUsername
        _callState.value = CallState.RINGING_OUTGOING
        EncryptedSocketClient.sendVoiceSignal("CALL_OFFER", peerUsername)
    }

    fun acceptCall() {
        val peer = _currentPeer.value
        if (peer.isNotEmpty()) {
            _callState.value = CallState.CONNECTED
            EncryptedSocketClient.sendVoiceSignal("CALL_ACCEPT", peer)
            startAudioEngine()
        }
    }

    fun rejectCall() {
        val peer = _currentPeer.value
        if (peer.isNotEmpty()) {
            EncryptedSocketClient.sendVoiceSignal("CALL_REJECT", peer)
        }
        stopAudioEngine()
        _callState.value = CallState.IDLE
        _currentPeer.value = ""
    }

    fun endCall() {
        val peer = _currentPeer.value
        if (peer.isNotEmpty()) {
            EncryptedSocketClient.sendVoiceSignal("CALL_END", peer)
        }
        stopAudioEngine()
        _callState.value = CallState.IDLE
        _currentPeer.value = ""
    }

    @SuppressLint("MissingPermission")
    private fun startAudioEngine() {
        if (isRecording) return

        try {
            val minRecordBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                minRecordBufSize
            )

            val minTrackBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build()
                )
                .setBufferSizeInBytes(minTrackBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            audioTrack?.play()

            isRecording = true
            isPlaying = true

            recordJob = managerScope.launch {
                val pcmBuffer = ShortArray(640)
                while (isRecording) {
                    val readSize = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                    if (readSize > 0) {
                        // Process PCM audio frame in C Native layer (gain & noise gate)
                        val processedPcm = NativeAudioProcessor.processAudioFrame(pcmBuffer, 1.3f, 80) ?: pcmBuffer
                        val byteBuffer = ByteArray(processedPcm.size * 2)
                        for (i in processedPcm.indices) {
                            val v = processedPcm[i].toInt()
                            byteBuffer[i * 2] = (v and 0x00FF).toByte()
                            byteBuffer[i * 2 + 1] = ((v shr 8) and 0x00FF).toByte()
                        }
                        val base64Chunk = Base64.encodeToString(byteBuffer, Base64.NO_WRAP)
                        val peer = _currentPeer.value
                        if (peer.isNotEmpty()) {
                            EncryptedSocketClient.sendVoiceSignal("AUDIO_CHUNK", peer, base64Chunk)
                        }
                    }
                }
            }

            Log.i(TAG, "Audio engine started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio engine: ${e.message}")
        }
    }

    private fun playIncomingAudioChunk(base64Chunk: String) {
        try {
            val byteData = Base64.decode(base64Chunk, Base64.NO_WRAP)
            if (isPlaying && audioTrack != null) {
                audioTrack?.write(byteData, 0, byteData.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio chunk: ${e.message}")
        }
    }

    private fun stopAudioEngine() {
        isRecording = false
        isPlaying = false
        recordJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio track/record: ${e.message}")
        }

        audioRecord = null
        audioTrack = null
        Log.i(TAG, "Audio engine stopped.")
    }
}
