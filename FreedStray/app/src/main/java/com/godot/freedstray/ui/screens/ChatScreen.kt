package com.godot.freedstray.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.godot.freedstray.data.local.LocalChatStorage
import com.godot.freedstray.network.EncryptedSocketClient
import com.godot.freedstray.voice.CallState
import com.godot.freedstray.voice.VoiceCallManager
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val recipient: String,
    val message: String,
    val isMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent", // "sent", "delivered", "read"
    val messageType: String = "text", // "text", "file", "location"
    val fileName: String? = null,
    val fileData: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Composable
fun ChatScreen(
    currentUsername: String,
    initialRecipient: String = "ALL",
    onNavigateToVideoCall: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var recipient by remember { mutableStateOf(initialRecipient) }
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    // Pagination & Scroll State
    var pageSize by remember { mutableIntStateOf(30) }
    val listState = rememberLazyListState()

    val callState by VoiceCallManager.callState.collectAsState()
    val currentPeer by VoiceCallManager.currentPeer.collectAsState()

    // File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fName = getFileNameFromUri(context, uri)
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null && bytes.isNotEmpty()) {
                    if (bytes.size > 3 * 1024 * 1024) {
                        Toast.makeText(context, "Dosya boyutu çok büyük (Maksimum 3 MB)", Toast.LENGTH_LONG).show()
                        return@rememberLauncherForActivityResult
                    }

                    val localFilePath = saveFileBytesToCache(context, fName, bytes)
                    val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    val chatMsg = ChatMessage(
                        sender = currentUsername,
                        recipient = recipient,
                        message = "📄 $fName",
                        isMe = true,
                        messageType = "file",
                        fileName = fName,
                        fileData = localFilePath ?: base64Data
                    )
                    messages.add(chatMsg)
                    LocalChatStorage.saveMessage(context, chatMsg)

                    val payload = JSONObject().apply {
                        put("type", "chat_message")
                        put("msgId", chatMsg.id)
                        put("sender", currentUsername)
                        put("recipient", recipient)
                        put("message", chatMsg.message)
                        put("messageType", "file")
                        put("fileName", fName)
                        put("fileData", base64Data)
                    }
                    EncryptedSocketClient.sendRawText(payload.toString())
                    Toast.makeText(context, "Dosya gönderildi: $fName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Dosya okuma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val loc = getCurrentLocation(context)
            if (loc != null) {
                val lat = loc.latitude
                val lng = loc.longitude
                val chatMsg = ChatMessage(
                    sender = currentUsername,
                    recipient = recipient,
                    message = "📍 Konum: $lat, $lng",
                    isMe = true,
                    messageType = "location",
                    latitude = lat,
                    longitude = lng
                )
                messages.add(chatMsg)
                LocalChatStorage.saveMessage(context, chatMsg)

                val payload = JSONObject().apply {
                    put("type", "chat_message")
                    put("msgId", chatMsg.id)
                    put("sender", currentUsername)
                    put("recipient", recipient)
                    put("message", chatMsg.message)
                    put("messageType", "location")
                    put("latitude", lat)
                    put("longitude", lng)
                }
                EncryptedSocketClient.sendRawText(payload.toString())
                Toast.makeText(context, "Konum gönderildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Konum alınamadı. GPS açık mı?", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Konum izni reddedildi.", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        EncryptedSocketClient.currentScreen = "chat"
        onDispose {
            EncryptedSocketClient.currentScreen = "home"
        }
    }

    LaunchedEffect(Unit) {
        val savedMessages = LocalChatStorage.getMessages(context)
        messages.addAll(savedMessages)

        EncryptedSocketClient.incomingMessages.collectLatest { jsonStr ->
            try {
                val jsonObj = JSONObject(jsonStr)
                val type = jsonObj.optString("type")

                if (type == "chat_message") {
                    val msgId = jsonObj.optString("msgId", UUID.randomUUID().toString())
                    val sender = jsonObj.optString("sender")
                    val rec = jsonObj.optString("recipient", "ALL")
                    val msg = jsonObj.optString("message")

                    val msgType = jsonObj.optString("messageType", "text")
                    val fName = if (jsonObj.has("fileName")) jsonObj.optString("fileName") else null
                    val fData = if (jsonObj.has("fileData")) jsonObj.optString("fileData") else null
                    val lat = if (jsonObj.has("latitude")) jsonObj.optDouble("latitude") else null
                    val lng = if (jsonObj.has("longitude")) jsonObj.optDouble("longitude") else null

                    val isCurrentChatOpen = (rec == currentUsername && recipient == sender) || rec == "ALL"
                    val initialStatus = if (isCurrentChatOpen) "read" else "delivered"

                    val chatMsg = ChatMessage(
                        id = msgId,
                        sender = sender,
                        recipient = rec,
                        message = msg,
                        isMe = sender == currentUsername,
                        status = initialStatus,
                        messageType = msgType,
                        fileName = fName,
                        fileData = fData,
                        latitude = lat,
                        longitude = lng
                    )

                    messages.add(chatMsg)
                    LocalChatStorage.saveMessage(context, chatMsg)

                    // Send delivery and read ACKs back to sender
                    EncryptedSocketClient.sendChatAck(msgId, sender, "delivered")
                    if (isCurrentChatOpen) {
                        EncryptedSocketClient.sendChatAck(msgId, sender, "read")
                    }
                } else if (type == "chat_ack") {
                    val ackMsgId = jsonObj.optString("msgId")
                    val ackStatus = jsonObj.optString("status", "delivered")

                    for (i in messages.indices) {
                        if (messages[i].id == ackMsgId) {
                            val existing = messages[i]
                            if (existing.status != "read") {
                                messages[i] = existing.copy(status = ackStatus)
                                LocalChatStorage.updateMessageStatus(context, ackMsgId, ackStatus)
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    // Filter messages for current recipient or general chat
    val filteredMessages = messages.filter { msg ->
        if (recipient == "ALL") {
            msg.recipient == "ALL" || msg.sender == "ALL"
        } else {
            (msg.sender == currentUsername && msg.recipient == recipient) ||
                    (msg.sender == recipient && (msg.recipient == currentUsername || msg.recipient == "ALL"))
        }
    }

    // Paginated messages (30 most recent initially)
    val hasMoreMessages = filteredMessages.size > pageSize
    val visibleMessages = filteredMessages.takeLast(pageSize)

    // Auto-scroll to bottom on opening or new messages
    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.size - 1)
        }
    }

    // Load older messages when user scrolls near the top
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstIndex ->
                if (firstIndex == 0 && hasMoreMessages) {
                    pageSize += 30
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        // Private Chat Header with Recipient Profile Picture & Call Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Picture with Initial
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (recipient == "ALL") Color(0xFF10B981) else Color(0xFF3B82F6),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (recipient == "ALL") "G" else recipient.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (recipient == "ALL") "General Lobby Chat" else recipient,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (recipient == "ALL") "Public Lobby Stream" else "AES-256 Encrypted Private Chat",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Voice & Video Call Action Section
                if (recipient != "ALL") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                onNavigateToVideoCall?.invoke(recipient)
                            },
                            modifier = Modifier
                                .background(Color(0xFF3B82F6), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (callState == CallState.IDLE) {
                                    VoiceCallManager.initiateCall(recipient)
                                    Toast.makeText(context, "Arama başlatılıyor: $recipient...", Toast.LENGTH_SHORT).show()
                                } else {
                                    VoiceCallManager.endCall()
                                }
                            },
                            modifier = Modifier
                                .background(
                                    if (callState == CallState.CONNECTED) Color(0xFFEF4444) else Color(0xFF10B981),
                                    CircleShape
                                )
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (callState == CallState.CONNECTED) Icons.Default.CallEnd else Icons.Default.PhoneInTalk,
                                contentDescription = "Voice Call",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Voice Call Controls Bar (if in call or ringing)
        if (callState != CallState.IDLE) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (callState) {
                        CallState.CONNECTED -> Color(0xFF065F46)
                        CallState.RINGING_INCOMING, CallState.RINGING_OUTGOING -> Color(0xFF78350F)
                        else -> Color(0xFF1E293B)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (callState == CallState.CONNECTED) Icons.Default.Mic else Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when (callState) {
                                    CallState.RINGING_OUTGOING -> "Calling $currentPeer..."
                                    CallState.RINGING_INCOMING -> "Incoming Call from $currentPeer"
                                    CallState.CONNECTED -> "Voice Call Active ($currentPeer)"
                                    else -> "Call Ended"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "JNI C-Native Encrypted VoIP",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Row {
                        if (callState == CallState.RINGING_INCOMING) {
                            Button(
                                onClick = { VoiceCallManager.acceptCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Accept", fontSize = 12.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { VoiceCallManager.rejectCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Reject", fontSize = 12.sp, color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = { VoiceCallManager.endCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("End", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasMoreMessages) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▲ Load older messages (${filteredMessages.size - visibleMessages.size} remaining)",
                            color = Color(0xFF3B82F6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { pageSize += 30 }
                        )
                    }
                }
            }

            items(visibleMessages, key = { it.id }) { msg ->
                val bubbleColor = if (msg.isMe) Color(0xFF3B82F6) else Color(0xFF1E293B)
                val alignment = if (msg.isMe) Alignment.End else Alignment.Start

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
                    ) {
                        if (!msg.isMe) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF64748B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = msg.sender.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Card(
                            modifier = Modifier.widthIn(max = 280.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = bubbleColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (msg.isMe) "You" else msg.sender,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.isMe) Color.White.copy(alpha = 0.8f) else Color(0xFF10B981)
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                // Render content based on messageType
                                when (msg.messageType) {
                                    "file" -> {
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.InsertDriveFile,
                                                    contentDescription = "File",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = msg.fileName ?: "Ekli Dosya",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "Doküman / Medya",
                                                        fontSize = 10.sp,
                                                        color = Color.White.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        openFileWithProvider(context, msg.fileData, msg.fileName)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Aç / İndir", fontSize = 11.sp, color = Color.White)
                                                }

                                                if (msg.isMe) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    val (statusSymbol, statusColor) = when (msg.status) {
                                                        "read" -> "✓✓" to Color(0xFF60A5FA)
                                                        "delivered" -> "✓✓" to Color.White.copy(alpha = 0.7f)
                                                        else -> "✓" to Color.White.copy(alpha = 0.6f)
                                                    }
                                                    Text(
                                                        text = statusSymbol,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    "location" -> {
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = "Location",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Paylaşılan Konum",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            if (msg.latitude != null && msg.longitude != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "${msg.latitude}, ${msg.longitude}",
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val geoUri = Uri.parse("https://maps.google.com/?q=${msg.latitude},${msg.longitude}")
                                                            val intent = Intent(Intent.ACTION_VIEW, geoUri)
                                                            try {
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Harita uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.height(30.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Haritada Aç", fontSize = 11.sp, color = Color.White)
                                                    }

                                                    if (msg.isMe) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        val (statusSymbol, statusColor) = when (msg.status) {
                                                            "read" -> "✓✓" to Color(0xFF60A5FA)
                                                            "delivered" -> "✓✓" to Color.White.copy(alpha = 0.7f)
                                                            else -> "✓" to Color.White.copy(alpha = 0.6f)
                                                        }
                                                        Text(
                                                            text = statusSymbol,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = statusColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Text(
                                                text = msg.message,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                            if (msg.isMe) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val (statusSymbol, statusColor) = when (msg.status) {
                                                    "read" -> "✓✓" to Color(0xFF60A5FA)
                                                    "delivered" -> "✓✓" to Color.White.copy(alpha = 0.7f)
                                                    else -> "✓" to Color.White.copy(alpha = 0.6f)
                                                }
                                                Text(
                                                    text = statusSymbol,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = statusColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Message Input Bar with Attach File and Share Location Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach File Button
            IconButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1E293B), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Dosya Ekle",
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Share Location Button
            IconButton(
                onClick = {
                    val hasFineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                    if (hasFineLoc || hasCoarseLoc) {
                        val loc = getCurrentLocation(context)
                        if (loc != null) {
                            val lat = loc.latitude
                            val lng = loc.longitude
                            val chatMsg = ChatMessage(
                                sender = currentUsername,
                                recipient = recipient,
                                message = "📍 Konum: $lat, $lng",
                                isMe = true,
                                messageType = "location",
                                latitude = lat,
                                longitude = lng
                            )
                            messages.add(chatMsg)
                            LocalChatStorage.saveMessage(context, chatMsg)

                            val payload = JSONObject().apply {
                                put("type", "chat_message")
                                put("msgId", chatMsg.id)
                                put("sender", currentUsername)
                                put("recipient", recipient)
                                put("message", chatMsg.message)
                                put("messageType", "location")
                                put("latitude", lat)
                                put("longitude", lng)
                            }
                            EncryptedSocketClient.sendRawText(payload.toString())
                            Toast.makeText(context, "Konum gönderildi", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Konum alınamadı. GPS açık mı?", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1E293B), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Konum Paylaş",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Mesaj yazın...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        val chatMsg = ChatMessage(
                            sender = currentUsername,
                            recipient = recipient,
                            message = messageText.trim(),
                            isMe = true
                        )

                        messages.add(chatMsg)
                        LocalChatStorage.saveMessage(context, chatMsg)

                        val payload = JSONObject().apply {
                            put("type", "chat_message")
                            put("msgId", chatMsg.id)
                            put("sender", currentUsername)
                            put("recipient", recipient)
                            put("message", chatMsg.message)
                            put("messageType", "text")
                        }
                        EncryptedSocketClient.sendRawText(payload.toString())

                        messageText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF3B82F6), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Gönder",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun saveFileBytesToCache(context: Context, fileName: String, bytes: ByteArray): String? {
    return try {
        val dir = File(context.cacheDir, "chat_files")
        if (!dir.exists()) dir.mkdirs()
        val cleanName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val outFile = File(dir, "${System.currentTimeMillis()}_$cleanName")
        outFile.writeBytes(bytes)
        outFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun openFileWithProvider(context: Context, filePathOrData: String?, fileName: String?) {
    if (filePathOrData.isNullOrEmpty()) {
        Toast.makeText(context, "Dosya verisi bulunamadı", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val file = if (filePathOrData.startsWith("/")) File(filePathOrData) else null
        if (file != null && file.exists()) {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = context.contentResolver.getType(contentUri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Dosyayı Aç"))
        } else {
            Toast.makeText(context, "Dosya bulunamadı veya henüz indirilmedi", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Dosya açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return name
}

@SuppressLint("MissingPermission")
fun getCurrentLocation(context: Context): Location? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var loc: Location? = null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    val l = lm.getLastKnownLocation(provider)
                    if (l != null && (loc == null || l.time > loc.time)) {
                        loc = l
                    }
                }
            } catch (e: Exception) {
                // Ignore individual provider errors
            }
        }

        if (loc == null) {
            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else null

            if (provider != null) {
                lm.requestSingleUpdate(provider, object : android.location.LocationListener {
                    override fun onLocationChanged(l: Location) {}
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }, android.os.Looper.getMainLooper())
            }
        }

        loc
    } catch (e: Exception) {
        null
    }
}
