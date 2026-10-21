package com.godot.freedstray.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.godot.freedstray.voice.CallState
import com.godot.freedstray.voice.VideoCallManager
import kotlinx.coroutines.delay

@Composable
fun VideoCallScreen(
    currentUsername: String,
    initialTargetUser: String = ""
) {
    val context = LocalContext.current
    val callState by VideoCallManager.callState.collectAsState()
    val currentPeer by VideoCallManager.currentPeer.collectAsState()
    val remoteFrame by VideoCallManager.remoteVideoFrame.collectAsState()

    var targetPeerInput by remember { mutableStateOf(initialTargetUser) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoOff by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // Local Video Frame Simulation / Capture Loop
    LaunchedEffect(callState, isVideoOff) {
        if (callState == CallState.CONNECTED && !isVideoOff) {
            var frameCount = 0
            while (callState == CallState.CONNECTED && !isVideoOff) {
                frameCount++
                val bmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(AndroidColor.DKGRAY)
                val paint = Paint().apply {
                    color = AndroidColor.CYAN
                    textSize = 28f
                    isAntiAlias = true
                }
                canvas.drawText("CAM: $currentUsername (#$frameCount)", 20f, 120f, paint)

                VideoCallManager.sendVideoFrame(bmp)
                delay(150) // ~7 FPS stream for low bandwidth
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Görüntülü Konuşma",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "C-Native JNI Processing & AES-256 Encrypted Video",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Main Video View Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (callState == CallState.CONNECTED && remoteFrame != null) {
                // Remote Video Display Frame
                Image(
                    bitmap = remoteFrame!!.asImageBitmap(),
                    contentDescription = "Uzak Görüntü",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black, RoundedCornerShape(24.dp))
                )

                // Remote User Label Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "GÖRÜNTÜ: $currentPeer",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Placeholder / Status Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                when (callState) {
                                    CallState.CONNECTED -> Color(0xFF10B981)
                                    CallState.RINGING_INCOMING, CallState.RINGING_OUTGOING -> Color(0xFFF59E0B)
                                    else -> Color(0xFF3B82F6)
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call Icon",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (currentPeer.isNotEmpty()) currentPeer else if (targetPeerInput.isNotEmpty()) targetPeerInput else "Arama Bekleniyor",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when (callState) {
                            CallState.IDLE -> "Görüntülü konuşma başlatmaya hazır"
                            CallState.RINGING_OUTGOING -> "Aranıyor..."
                            CallState.RINGING_INCOMING -> "Gelen Görüntülü Arama..."
                            CallState.CONNECTED -> "Görüntü Bekleniyor..."
                            CallState.ENDED -> "Arama Sonlandı"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Bottom Controls Section
        Column(modifier = Modifier.fillMaxWidth()) {
            if (callState == CallState.IDLE) {
                OutlinedTextField(
                    value = targetPeerInput,
                    onValueChange = { targetPeerInput = it },
                    label = { Text("Aranacak Kullanıcı", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val peer = if (targetPeerInput.isNotBlank()) targetPeerInput else initialTargetUser
                        if (peer.isNotBlank()) {
                            VideoCallManager.startVideoCall(peer)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Görüntülü Ara", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else if (callState == CallState.RINGING_INCOMING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { VideoCallManager.acceptVideoCall() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Kabul Et", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { VideoCallManager.rejectVideoCall() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CallEnd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Reddet", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Button
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(52.dp)
                            .background(if (isMuted) Color(0xFFEF4444) else Color(0xFF334155), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }

                    // Video Toggle Button
                    IconButton(
                        onClick = { isVideoOff = !isVideoOff },
                        modifier = Modifier
                            .size(52.dp)
                            .background(if (isVideoOff) Color(0xFFEF4444) else Color(0xFF334155), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isVideoOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Video Toggle",
                            tint = Color.White
                        )
                    }

                    // End Call Button
                    IconButton(
                        onClick = { VideoCallManager.endVideoCall() },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
