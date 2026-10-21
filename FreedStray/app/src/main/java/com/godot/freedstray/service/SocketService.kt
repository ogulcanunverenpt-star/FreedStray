package com.godot.freedstray.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.godot.freedstray.data.local.LocalChatStorage
import com.godot.freedstray.network.EncryptedSocketClient
import com.godot.freedstray.notification.NotificationHelper
import com.godot.freedstray.ui.screens.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class SocketService : Service() {

    companion object {
        private const val TAG = "SocketService"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SocketService created.")
        NotificationHelper.createNotificationChannels(this)

        // Initialize Call Managers to observe socket signals
        com.godot.freedstray.voice.VoiceCallManager
        com.godot.freedstray.voice.VideoCallManager

        startForeground(
            NOTIFICATION_ID,
            NotificationHelper.buildForegroundServiceNotification(this, "Encrypted Socket Active")
        )

        observeConnectionStatus()
        observeSocketMessages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val savedUsername = com.godot.freedstray.data.local.SessionManager.getUsername(this)
        val defaultUsername = savedUsername?.ifEmpty { null } ?: EncryptedSocketClient.getUniqueDeviceHostname(this)
        val username = intent?.getStringExtra("username")?.ifEmpty { null } ?: defaultUsername
        val host = intent?.getStringExtra("host")?.ifEmpty { null } ?: EncryptedSocketClient.DEFAULT_HOST
        val port = intent?.getIntExtra("port", EncryptedSocketClient.DEFAULT_PORT) ?: EncryptedSocketClient.DEFAULT_PORT

        EncryptedSocketClient.connect(username, host, port)
        return START_STICKY
    }

    private fun observeConnectionStatus() {
        serviceScope.launch {
            EncryptedSocketClient.connectionStatus.collectLatest { connected ->
                val statusMsg = if (connected) "Encrypted Socket Connected (${EncryptedSocketClient.currentUsername})" else "Disconnected, Retrying..."
                Log.i(TAG, "Status updated: $statusMsg")
            }
        }
    }

    private fun observeSocketMessages() {
        serviceScope.launch {
            EncryptedSocketClient.incomingMessages.collectLatest { jsonStr ->
                try {
                    val jsonObj = JSONObject(jsonStr)
                    val type = jsonObj.optString("type")

                    if (type == "chat_message") {
                        val sender = jsonObj.optString("sender")
                        val recipient = jsonObj.optString("recipient", "ALL")
                        val message = jsonObj.optString("message")

                        if (sender != EncryptedSocketClient.currentUsername) {
                            val chatMsg = ChatMessage(
                                sender = sender,
                                recipient = recipient,
                                message = message,
                                isMe = false
                            )
                            LocalChatStorage.saveMessage(this@SocketService, chatMsg)

                            // Suppress chat notification ONLY when user is currently on the 'chat' screen
                            if (EncryptedSocketClient.currentScreen != "chat") {
                                NotificationHelper.showChatNotification(this@SocketService, sender, message)
                            }
                        }
                    } else if (type == "voice_signal") {
                        val action = jsonObj.optString("action")
                        val sender = jsonObj.optString("sender")
                        val recipient = jsonObj.optString("recipient")

                        if (recipient == EncryptedSocketClient.currentUsername) {
                            if (action == "CALL_OFFER") {
                                // Always show call notification and launch app to show Private Chat & Call Dialog
                                NotificationHelper.showVoiceCallNotification(this@SocketService, sender)

                                try {
                                    val callIntent = Intent(this@SocketService, com.godot.freedstray.MainActivity::class.java).apply {
                                        putExtra("navigate_to", "chat")
                                        putExtra("chat_sender", sender)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(callIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to launch activity for incoming call: ${e.message}")
                                }
                            } else if (action == "CALL_END" || action == "CALL_REJECT") {
                                NotificationHelper.clearCallNotification(this@SocketService)
                            }
                        }
                    } else if (type == "video_signal") {
                        val action = jsonObj.optString("action")
                        val sender = jsonObj.optString("sender")
                        val recipient = jsonObj.optString("recipient")

                        if (recipient == EncryptedSocketClient.currentUsername) {
                            if (action == "VIDEO_OFFER") {
                                NotificationHelper.showVideoCallNotification(this@SocketService, sender)

                                try {
                                    val callIntent = Intent(this@SocketService, com.godot.freedstray.MainActivity::class.java).apply {
                                        putExtra("navigate_to", "video_call")
                                        putExtra("chat_sender", sender)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(callIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to launch activity for incoming video call: ${e.message}")
                                }
                            } else if (action == "VIDEO_END" || action == "VIDEO_REJECT") {
                                NotificationHelper.clearCallNotification(this@SocketService)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing socket message: ${e.message}")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "SocketService destroyed.")
        serviceJob.cancel()
        EncryptedSocketClient.disconnect()
    }
}
