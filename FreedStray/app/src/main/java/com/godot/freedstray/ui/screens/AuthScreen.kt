package com.godot.freedstray.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godot.freedstray.BuildConfig
import com.godot.freedstray.data.remote.RetrofitClient
import com.godot.freedstray.network.EncryptedSocketClient
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onLoginSuccess: (username: String, role: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0: Sign In, 1: Register
    val defaultHost = if (BuildConfig.SERVER_HOST == "156.67.27.88") "156.67.27.88" else BuildConfig.SERVER_HOST
    var serverHost by remember { mutableStateOf("$defaultHost:${BuildConfig.TCP_PORT}") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FreedStray C2",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3B82F6)
        )
        Text(
            text = "Secure Device Management & Mode Control",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF3B82F6),
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF3B82F6)
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Sign In (Socket)", color = if (selectedTab == 0) Color.White else Color(0xFF64748B)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Register (Socket)", color = if (selectedTab == 1) Color.White else Color(0xFF64748B)) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = serverHost,
                    onValueChange = { serverHost = it },
                    label = { Text("Server Address (IP:Port)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true

                        scope.launch {
                            try {
                                val parts = serverHost.trim().split(":")
                                val host = parts.getOrNull(0)?.ifEmpty { null } ?: BuildConfig.SERVER_HOST
                                val port = parts.getOrNull(1)?.toIntOrNull() ?: BuildConfig.TCP_PORT

                                if (selectedTab == 0) {
                                    // Direct Encrypted TCP Socket Login
                                    val resp = EncryptedSocketClient.loginViaSocket(host, port, username, password)
                                    if (resp.success && resp.token != null) {
                                        RetrofitClient.authToken = resp.token
                                        com.godot.freedstray.data.local.SessionManager.saveSession(
                                            context = context,
                                            token = resp.token,
                                            username = resp.username ?: username,
                                            role = resp.role ?: "default",
                                            serverHost = serverHost
                                        )
                                        Toast.makeText(context, "Sign In Successful!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(resp.username ?: username, resp.role ?: "default")
                                    } else {
                                        Toast.makeText(context, resp.error ?: "Sign In Failed.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    // Direct Encrypted TCP Socket Register
                                    val resp = EncryptedSocketClient.registerViaSocket(host, port, username, password)
                                    if (resp.success && resp.token != null) {
                                        RetrofitClient.authToken = resp.token
                                        com.godot.freedstray.data.local.SessionManager.saveSession(
                                            context = context,
                                            token = resp.token,
                                            username = resp.username ?: username,
                                            role = resp.role ?: "default",
                                            serverHost = serverHost
                                        )
                                        Toast.makeText(context, "Registration Successful!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(resp.username ?: username, resp.role ?: "default")
                                    } else {
                                        Toast.makeText(context, resp.error ?: "Registration Failed.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Socket Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text(
                            text = if (selectedTab == 0) "Sign In (Socket)" else "Register (Socket)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
