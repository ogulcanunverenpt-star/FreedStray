package com.godot.freedstray.data.local

import android.content.Context
import android.util.Base64
import android.util.Log
import com.godot.freedstray.crypto.NativeCryptoBridge
import com.godot.freedstray.ui.screens.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LocalChatStorage {

    private const val TAG = "LocalChatStorage"
    private const val PREFS_NAME = "freedstray_encrypted_chat"
    private const val KEY_ENCRYPTED_MESSAGES = "encrypted_messages_data"

    @Synchronized
    fun saveMessage(context: Context, chatMessage: ChatMessage) {
        try {
            val messages = getMessages(context).toMutableList()

            val existingIndex = messages.indexOfFirst { it.id == chatMessage.id }
            if (existingIndex >= 0) {
                messages[existingIndex] = chatMessage
            } else {
                messages.add(chatMessage)
            }

            persistMessages(context, messages)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving encrypted message: ${e.message}")
        }
    }

    @Synchronized
    fun updateMessageStatus(context: Context, msgId: String, newStatus: String) {
        try {
            val messages = getMessages(context).toMutableList()
            var updated = false
            for (i in messages.indices) {
                if (messages[i].id == msgId) {
                    messages[i] = messages[i].copy(status = newStatus)
                    updated = true
                    break
                }
            }
            if (updated) {
                persistMessages(context, messages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message status: ${e.message}")
        }
    }

    private fun saveFileDataToFile(context: Context, msgId: String, fileName: String?, fileData: String?): String? {
        if (fileData.isNullOrEmpty()) return null
        if (fileData.startsWith("/") || fileData.startsWith("content://") || fileData.startsWith("file://")) {
            return fileData // Already a local path or URI
        }
        return try {
            val dir = File(context.cacheDir, "chat_files")
            if (!dir.exists()) dir.mkdirs()
            val cleanName = (fileName ?: "file_$msgId").replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val outFile = File(dir, "${msgId}_$cleanName")
            if (!outFile.exists() && fileData.length > 30) {
                val bytes = Base64.decode(fileData, Base64.DEFAULT)
                outFile.writeBytes(bytes)
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to offload file data to disk: ${e.message}")
            null
        }
    }

    private fun persistMessages(context: Context, messages: List<ChatMessage>) {
        val jsonArray = JSONArray()
        for (msg in messages) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("sender", msg.sender)
                put("recipient", msg.recipient)
                put("message", msg.message)
                put("isMe", msg.isMe)
                put("timestamp", msg.timestamp)
                put("status", msg.status)
                put("messageType", msg.messageType)
                if (msg.fileName != null) put("fileName", msg.fileName)
                if (msg.fileData != null) {
                    val filePath = saveFileDataToFile(context, msg.id, msg.fileName, msg.fileData)
                    put("fileData", filePath ?: msg.fileData)
                }
                if (msg.latitude != null) put("latitude", msg.latitude)
                if (msg.longitude != null) put("longitude", msg.longitude)
            }
            jsonArray.put(obj)
        }

        val plainJson = jsonArray.toString()
        val encBytes = NativeCryptoBridge.encryptPayload(NativeCryptoBridge.DEFAULT_AES_KEY_HEX, plainJson)

        if (encBytes != null) {
            val hexEncrypted = encBytes.joinToString("") { "%02x".format(it) }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ENCRYPTED_MESSAGES, hexEncrypted).apply()
        }
    }

    @Synchronized
    fun getMessages(context: Context): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hexEncrypted = prefs.getString(KEY_ENCRYPTED_MESSAGES, null) ?: return emptyList()

            val encBytes = NativeCryptoBridge.hexToBytes(hexEncrypted)
            val decryptedJson = NativeCryptoBridge.decryptPayload(NativeCryptoBridge.DEFAULT_AES_KEY_HEX, encBytes)

            if (!decryptedJson.isNullOrEmpty()) {
                val jsonArray = JSONArray(decryptedJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    messages.add(
                        ChatMessage(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            sender = obj.optString("sender"),
                            recipient = obj.optString("recipient"),
                            message = obj.optString("message"),
                            isMe = obj.optBoolean("isMe"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            status = obj.optString("status", "sent"),
                            messageType = obj.optString("messageType", "text"),
                            fileName = if (obj.has("fileName")) obj.optString("fileName") else null,
                            fileData = if (obj.has("fileData")) obj.optString("fileData") else null,
                            latitude = if (obj.has("latitude")) obj.optDouble("latitude") else null,
                            longitude = if (obj.has("longitude")) obj.optDouble("longitude") else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading encrypted messages: ${e.message}")
        }
        return messages
    }
}
