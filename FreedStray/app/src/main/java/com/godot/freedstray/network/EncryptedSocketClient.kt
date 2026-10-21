package com.godot.freedstray.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.godot.freedstray.BuildConfig
import com.godot.freedstray.crypto.NativeCryptoBridge
import com.godot.freedstray.data.remote.AuthResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EncryptedSocketClient {

    private const val TAG = "EncryptedSocketClient"
    val DEFAULT_HOST: String = BuildConfig.SERVER_HOST
    val DEFAULT_PORT: Int = BuildConfig.TCP_PORT

    private var socket: Socket? = null
    private var dataInputStream: DataInputStream? = null
    private var dataOutputStream: DataOutputStream? = null

    private var isConnected = false
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var host: String = DEFAULT_HOST
    var port: Int = DEFAULT_PORT
    var currentUsername: String = "Android_Agent"
    var currentScreen: String = "home"

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    private val _connectionStatus = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val connectionStatus: SharedFlow<Boolean> = _connectionStatus

    private val _onlineUsers = MutableStateFlow<List<String>>(emptyList())
    val onlineUsers: StateFlow<List<String>> = _onlineUsers

    fun getUniqueDeviceHostname(context: Context): String {
        val prefs = context.getSharedPreferences("freedstray_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("unique_device_id", null)
        if (deviceId.isNullOrEmpty()) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            val suffix = if (androidId.length >= 6) androidId.take(6) else UUID.randomUUID().toString().take(6)
            deviceId = "Android_$suffix"
            prefs.edit().putString("unique_device_id", deviceId).apply()
        }
        return deviceId
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    private fun tryConnectSocket(primaryHost: String, port: Int): Socket {
        val hostsToTry = mutableSetOf<String>()
        if (primaryHost.isNotBlank()) hostsToTry.add(primaryHost)

        // Add 127.0.0.1 for ADB reverse USB port forwarding
        hostsToTry.add("127.0.0.1")

        if (isEmulator()) {
            hostsToTry.add("156.67.27.88")
        } else {
            hostsToTry.add("156.67.27.88")
            hostsToTry.add("192.168.1.4")
        }

        var lastException: Exception? = null
        for (h in hostsToTry) {
            try {
                Log.i(TAG, "Trying TCP Socket connection to $h:$port...")
                val s = Socket()
                s.connect(InetSocketAddress(h, port), 3000)
                s.tcpNoDelay = true
                Log.i(TAG, "Successfully connected to $h:$port")
                return s
            } catch (e: Exception) {
                Log.w(TAG, "Connection to $h:$port failed: ${e.message}")
                lastException = e
            }
        }
        throw lastException ?: Exception("All connection attempts failed")
    }

    fun updateUsername(newUsername: String) {
        if (newUsername.isNotBlank() && newUsername != currentUsername) {
            currentUsername = newUsername
            if (isConnected) {
                sendRawText("HOSTNAME:$currentUsername")
            }
        }
    }

    fun connect(username: String = currentUsername, serverHost: String = host, serverPort: Int = port) {
        currentUsername = username
        host = serverHost
        port = serverPort

        connectionJob?.cancel()
        disconnectInternal()

        connectionJob = scope.launch {
            while (true) {
                try {
                    val activeSocket = tryConnectSocket(host, port)

                    socket = activeSocket
                    dataInputStream = DataInputStream(activeSocket.getInputStream())
                    dataOutputStream = DataOutputStream(activeSocket.getOutputStream())

                    isConnected = true
                    _connectionStatus.emit(true)
                    Log.i(TAG, "Connected to TCP Socket as ($currentUsername)")

                    // Send Hostname Handshake
                    sendRawText("HOSTNAME:$currentUsername")

                    // Request Online Users
                    sendRawText("""{"type":"get_online_users"}""")

                    // Read Loop
                    readLoop()

                } catch (e: Exception) {
                    Log.e(TAG, "Socket connection error: ${e.message}")
                } finally {
                    disconnectInternal()
                    _connectionStatus.emit(false)
                }
                delay(5000) // Reconnect delay
            }
        }
    }

    private suspend fun readLoop() {
        val dis = dataInputStream ?: return
        while (isConnected) {
            val len = dis.readInt() // Reads 4-byte BE UInt32
            if (len <= 0 || len > 10 * 1024 * 1024) {
                Log.e(TAG, "Invalid frame length: $len")
                break
            }

            val buffer = ByteArray(len)
            dis.readFully(buffer)

            val decryptedJson = NativeCryptoBridge.decryptPayload(NativeCryptoBridge.DEFAULT_AES_KEY_HEX, buffer)
            if (decryptedJson != null) {
                Log.d(TAG, "Received decrypted frame: $decryptedJson")
                try {
                    val jsonObj = JSONObject(decryptedJson)
                    val type = jsonObj.optString("type")

                    if (type == "online_users") {
                        val usersArray = jsonObj.optJSONArray("users")
                        val list = mutableListOf<String>()
                        if (usersArray != null) {
                            for (i in 0 until usersArray.length()) {
                                val u = usersArray.optString(i)
                                if (u.isNotBlank() && u != currentUsername) {
                                    list.add(u)
                                }
                            }
                        }
                        _onlineUsers.value = list
                    }

                    val reqId = if (jsonObj.has("reqId") && !jsonObj.isNull("reqId")) jsonObj.optString("reqId") else null
                    if (reqId != null && pendingRequests.containsKey(reqId)) {
                        pendingRequests[reqId]?.complete(jsonObj)
                    } else {
                        _incomingMessages.emit(decryptedJson)
                    }
                } catch (e: Exception) {
                    _incomingMessages.emit(decryptedJson)
                }
            } else {
                Log.e(TAG, "Failed to decrypt frame of size $len")
            }
        }
    }

    private val sendMutex = Mutex()

    fun sendRawText(plainText: String) {
        scope.launch(Dispatchers.IO) {
            sendMutex.withLock {
                try {
                    val encBytes = NativeCryptoBridge.encryptPayload(NativeCryptoBridge.DEFAULT_AES_KEY_HEX, plainText)
                    val dos = dataOutputStream
                    if (encBytes != null && encBytes.isNotEmpty() && dos != null && isConnected) {
                        dos.writeInt(encBytes.size) // 4-byte BE length
                        dos.write(encBytes)
                        dos.flush()
                        Log.d(TAG, "Sent raw text frame: $plainText")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send payload: ${e.message}")
                }
            }
        }
    }

    suspend fun sendRequestWaitResponse(reqObj: JSONObject, serverHost: String, serverPort: Int, timeoutMs: Long = 10000): JSONObject {
        val reqId = if (reqObj.has("reqId")) reqObj.optString("reqId") else UUID.randomUUID().toString()
        reqObj.put("reqId", reqId)

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[reqId] = deferred

        try {
            if (!isConnected) {
                connect(currentUsername, serverHost, serverPort)
                var count = 0
                while (!isConnected && count < 30) {
                    delay(100)
                    count++
                }
            }

            if (!isConnected) {
                throw Exception("Could not connect to TCP Socket server at $serverHost:$serverPort")
            }

            sendRawText(reqObj.toString())

            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(reqId)
        }
    }

    fun sendChatMessage(msgId: String, recipient: String, message: String) {
        val json = JSONObject().apply {
            put("type", "chat_message")
            put("msgId", msgId)
            put("sender", currentUsername)
            put("recipient", recipient)
            put("message", message)
        }.toString()
        sendRawText(json)
    }

    fun sendChatAck(msgId: String, recipient: String, status: String) {
        val json = JSONObject().apply {
            put("type", "chat_ack")
            put("msgId", msgId)
            put("sender", currentUsername)
            put("recipient", recipient)
            put("status", status)
        }.toString()
        sendRawText(json)
    }

    fun sendVoiceSignal(action: String, recipient: String, data: String? = null) {
        val dataField = if (data != null) ",\"data\":\"$data\"" else ""
        val json = """
            {
                "type": "voice_signal",
                "action": "$action",
                "sender": "$currentUsername",
                "recipient": "$recipient"
                $dataField
            }
        """.trimIndent()
        sendRawText(json)
    }

    suspend fun loginViaSocket(serverHost: String, serverPort: Int, username: String, password: String): AuthResponse {
        return withContext(Dispatchers.IO) {
            try {
                val reqObj = JSONObject().apply {
                    put("type", "login")
                    put("username", username)
                    put("password", password)
                }
                val respObj = sendRequestWaitResponse(reqObj, serverHost, serverPort)

                val success = respObj.optBoolean("success", false)
                val error = if (respObj.has("error") && !respObj.isNull("error")) respObj.getString("error") else null
                val token = if (respObj.has("token") && !respObj.isNull("token")) respObj.getString("token") else null
                val role = if (respObj.has("role") && !respObj.isNull("role")) respObj.getString("role") else "default"
                val user = if (respObj.has("username") && !respObj.isNull("username")) respObj.getString("username") else username

                if (success && user.isNotBlank()) {
                    updateUsername(user)
                }

                AuthResponse(
                    success = success,
                    token = token,
                    username = user,
                    role = role,
                    error = error
                )
            } catch (e: Exception) {
                AuthResponse(success = false, error = "Socket Error: ${e.message}")
            }
        }
    }

    suspend fun registerViaSocket(serverHost: String, serverPort: Int, username: String, password: String): AuthResponse {
        return withContext(Dispatchers.IO) {
            try {
                val reqObj = JSONObject().apply {
                    put("type", "register")
                    put("username", username)
                    put("password", password)
                }
                val respObj = sendRequestWaitResponse(reqObj, serverHost, serverPort)

                val success = respObj.optBoolean("success", false)
                val error = if (respObj.has("error") && !respObj.isNull("error")) respObj.getString("error") else null
                val token = if (respObj.has("token") && !respObj.isNull("token")) respObj.getString("token") else null
                val role = if (respObj.has("role") && !respObj.isNull("role")) respObj.getString("role") else "default"
                val user = if (respObj.has("username") && !respObj.isNull("username")) respObj.getString("username") else username

                if (success && user.isNotBlank()) {
                    updateUsername(user)
                }

                AuthResponse(
                    success = success,
                    token = token,
                    username = user,
                    role = role,
                    error = error
                )
            } catch (e: Exception) {
                AuthResponse(success = false, error = "Socket Error: ${e.message}")
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        disconnectInternal()
    }

    private fun disconnectInternal() {
        isConnected = false
        try {
            dataInputStream?.close()
            dataOutputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        dataInputStream = null
        dataOutputStream = null
    }
}
