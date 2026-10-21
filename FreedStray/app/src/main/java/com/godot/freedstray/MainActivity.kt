package com.godot.freedstray

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import com.godot.freedstray.voice.VideoCallManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.godot.freedstray.data.remote.RetrofitClient
import com.godot.freedstray.network.EncryptedSocketClient
import com.godot.freedstray.service.AppLockerService
import com.godot.freedstray.service.SocketService
import com.godot.freedstray.ui.screens.AppControlScreen
import com.godot.freedstray.ui.screens.AuthScreen
import com.godot.freedstray.ui.screens.ChatScreen
import com.godot.freedstray.ui.screens.HomeScreen
import com.godot.freedstray.ui.screens.VideoCallScreen
import com.godot.freedstray.ui.screens.VoiceCallScreen
import com.godot.freedstray.ui.theme.FreedStrayTheme
import com.godot.freedstray.voice.CallState
import com.godot.freedstray.voice.VoiceCallManager

class MainActivity : ComponentActivity() {

    private var initialNavigateTarget: String? = null
    private var initialChatSender: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            Toast.makeText(this, "Microphone permission is required for voice calls.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val target = intent?.getStringExtra("navigate_to")
        if (!target.isNullOrEmpty()) {
            initialNavigateTarget = target
            initialChatSender = intent.getStringExtra("chat_sender")

            val callAction = intent.getStringExtra("call_action")
            if (callAction == "ACCEPT") {
                VoiceCallManager.acceptCall()
            } else if (callAction == "REJECT") {
                VoiceCallManager.rejectCall()
            }

            val videoCallAction = intent.getStringExtra("video_call_action")
            if (videoCallAction == "ACCEPT") {
                com.godot.freedstray.voice.VideoCallManager.acceptVideoCall()
            } else if (videoCallAction == "REJECT") {
                com.godot.freedstray.voice.VideoCallManager.rejectVideoCall()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNotificationIntent(intent)
        requestRequiredPermissions()

        val hasSession = com.godot.freedstray.data.local.SessionManager.isLoggedIn(this)
        val savedToken = com.godot.freedstray.data.local.SessionManager.getAuthToken(this)
        val savedUser = com.godot.freedstray.data.local.SessionManager.getUsername(this) ?: "User"
        val savedRole = com.godot.freedstray.data.local.SessionManager.getRole(this)

        if (hasSession && !savedToken.isNullOrBlank()) {
            RetrofitClient.authToken = savedToken
            startAppServices(savedUser)
        }

        setContent {
            FreedStrayTheme {
                var isLoggedIn by remember { mutableStateOf(hasSession) }
                var currentUsername by remember { mutableStateOf(savedUser) }
                var currentRole by remember { mutableStateOf(savedRole) }
                var currentScreen by remember { mutableStateOf("home") }
                var selectedChatRecipient by remember { mutableStateOf("ALL") }

                val callState by VoiceCallManager.callState.collectAsState()
                val currentPeer by VoiceCallManager.currentPeer.collectAsState()

                val videoCallState by VideoCallManager.callState.collectAsState()
                val videoCurrentPeer by VideoCallManager.currentPeer.collectAsState()

                LaunchedEffect(currentScreen) {
                    EncryptedSocketClient.currentScreen = currentScreen
                }

                LaunchedEffect(videoCallState) {
                    if (videoCallState == CallState.CONNECTED || videoCallState == CallState.RINGING_OUTGOING) {
                        if (videoCurrentPeer.isNotEmpty()) {
                            selectedChatRecipient = videoCurrentPeer
                            currentScreen = "video_call"
                        }
                    }
                }

                LaunchedEffect(initialNavigateTarget) {
                    val target = initialNavigateTarget
                    if (!target.isNullOrEmpty() && isLoggedIn) {
                        currentScreen = target
                        if (target == "chat" && !initialChatSender.isNullOrEmpty()) {
                            selectedChatRecipient = initialChatSender!!
                        }
                        initialNavigateTarget = null
                    }
                }

                // Global Incoming Voice Call Answer Modal Dialog
                if (callState == CallState.RINGING_INCOMING && currentPeer.isNotEmpty()) {
                    Dialog(
                        onDismissRequest = { VoiceCallManager.rejectCall() }
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color(0xFF3B82F6), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentPeer.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = currentPeer,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Incoming Voice Call...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = {
                                            VoiceCallManager.acceptCall()
                                            selectedChatRecipient = currentPeer
                                            currentScreen = "chat"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneInTalk,
                                            contentDescription = "Accept",
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Accept", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Button(
                                        onClick = {
                                            VoiceCallManager.rejectCall()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CallEnd,
                                            contentDescription = "Reject",
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reject", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Global Incoming Video Call Answer Modal Dialog
                if (videoCallState == CallState.RINGING_INCOMING && videoCurrentPeer.isNotEmpty()) {
                    Dialog(
                        onDismissRequest = { VideoCallManager.rejectVideoCall() }
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color(0xFF3B82F6), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = videoCurrentPeer,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Gelen Görüntülü Arama...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = {
                                            VideoCallManager.acceptVideoCall()
                                            selectedChatRecipient = videoCurrentPeer
                                            currentScreen = "video_call"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = "Accept",
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kabul Et", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Button(
                                        onClick = {
                                            VideoCallManager.rejectVideoCall()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CallEnd,
                                            contentDescription = "Reject",
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reddet", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isLoggedIn) {
                    AuthScreen(
                        onLoginSuccess = { user, role ->
                            currentUsername = user
                            currentRole = role
                            isLoggedIn = true
                            currentScreen = initialNavigateTarget ?: "home"
                            startAppServices(user)
                        }
                    )
                } else {
                    Scaffold(
                        topBar = {
                            if (currentScreen != "home") {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = when (currentScreen) {
                                                "app_control" -> "App & Access Control"
                                                "chat" -> if (selectedChatRecipient == "ALL") "General Live Chat" else "Private Chat ($selectedChatRecipient)"
                                                "call" -> "Voice VoIP Call"
                                                "video_call" -> if (selectedChatRecipient == "ALL") "Görüntülü Konuşma" else "Görüntülü Konuşma ($selectedChatRecipient)"
                                                else -> "FreedStray Lobby"
                                            },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { currentScreen = "home" }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color.White
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A))
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                "home" -> HomeScreen(
                                    username = currentUsername,
                                    role = currentRole,
                                    onNavigateToAppControl = { currentScreen = "app_control" },
                                    onNavigateToChat = {
                                        selectedChatRecipient = "ALL"
                                        currentScreen = "chat"
                                    },
                                    onNavigateToPrivateChat = { targetUser ->
                                        selectedChatRecipient = targetUser
                                        currentScreen = "chat"
                                    },
                                    onNavigateToVoiceCall = { currentScreen = "call" },
                                    onLogout = {
                                        com.godot.freedstray.data.local.SessionManager.clearSession(this@MainActivity)
                                        RetrofitClient.authToken = null
                                        isLoggedIn = false
                                        stopService(Intent(this@MainActivity, SocketService::class.java))
                                        stopService(Intent(this@MainActivity, AppLockerService::class.java))
                                    }
                                )
                                "app_control" -> AppControlScreen(
                                    currentUsername = currentUsername,
                                    currentRole = currentRole
                                )
                                "chat" -> ChatScreen(
                                    currentUsername = currentUsername,
                                    initialRecipient = selectedChatRecipient,
                                    onNavigateToVideoCall = { targetUser ->
                                        selectedChatRecipient = targetUser
                                        currentScreen = "video_call"
                                    }
                                )
                                "call" -> VoiceCallScreen(currentUsername = currentUsername)
                                "video_call" -> VideoCallScreen(
                                    currentUsername = currentUsername,
                                    initialTargetUser = selectedChatRecipient
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Overlay permission check
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Ignore overlay permission error
            }
        }
    }

    private fun startAppServices(username: String) {
        val socketIntent = Intent(this, SocketService::class.java).apply {
            putExtra("username", username)
        }
        val lockerIntent = Intent(this, AppLockerService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(socketIntent)
            startForegroundService(lockerIntent)
        } else {
            startService(socketIntent)
            startService(lockerIntent)
        }
    }
}
