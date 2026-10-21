package com.godot.freedstray.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.godot.freedstray.data.local.DomainFilterManager
import com.godot.freedstray.data.remote.AssignManagerRequest
import com.godot.freedstray.data.remote.DeviceAppDto
import com.godot.freedstray.data.remote.RetrofitClient
import com.godot.freedstray.data.remote.UpdateAppRuleRequest
import com.godot.freedstray.data.remote.UpdateRoleRequest
import com.godot.freedstray.data.remote.UserDto
import com.godot.freedstray.locker.AppInspector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlScreen(
    currentUsername: String,
    currentRole: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userList by remember { mutableStateOf(listOf<UserDto>()) }
    var selectedTargetUser by remember { mutableStateOf("") }
    var targetUserApps by remember { mutableStateOf(listOf<DeviceAppDto>()) }
    var installedApps by remember { mutableStateOf(listOf<DeviceAppDto>()) }
    var isLoading by remember { mutableStateOf(false) }

    // Selected App for Modal Dialog
    var selectedAppForModal by remember { mutableStateOf<DeviceAppDto?>(null) }

    // Domain Whitelist / Blocklist
    var domainInput by remember { mutableStateOf("") }
    var blockedDomainsList by remember { mutableStateOf(setOf<String>()) }

    // Domain Alert Dialog State
    var domainAlertMessage by remember { mutableStateOf<String?>(null) }

    val isManagerOrAdmin = currentRole.lowercase() == "content_manager" || currentRole.lowercase() == "admin"

    LaunchedEffect(Unit) {
        installedApps = AppInspector.getInstalledApps(context)
        blockedDomainsList = DomainFilterManager.getBlockedDomains(context)

        if (isManagerOrAdmin) {
            try {
                val token = RetrofitClient.getBearerToken()
                val resp = RetrofitClient.apiService.getUsersList(token)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    userList = resp.body()?.users ?: emptyList()
                    val defaultUsers = userList.filter { it.role == "default" || it.username != currentUsername }
                    if (defaultUsers.isNotEmpty()) {
                        selectedTargetUser = defaultUsers.first().username
                    }
                }
            } catch (e: Exception) {
                // Ignore offline error
            }
        }
    }

    // When selectedTargetUser changes, load their installed apps
    LaunchedEffect(selectedTargetUser) {
        if (isManagerOrAdmin && selectedTargetUser.isNotBlank()) {
            try {
                val token = RetrofitClient.getBearerToken()
                val resp = RetrofitClient.apiService.getInstalledApps(token, selectedTargetUser)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    targetUserApps = resp.body()?.apps ?: emptyList()
                } else {
                    targetUserApps = emptyList()
                }
            } catch (e: Exception) {
                targetUserApps = emptyList()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Text(
            text = "App & Access Control Panel",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Role: ${currentRole.uppercase()} | User: $currentUsername",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!isManagerOrAdmin) {
            // Default User View: Cannot edit Whitelist/Blocklist. Can Sync Apps.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default User Mode", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your application access rules and domain blocklists are managed remotely by your Content Manager / Admin.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val ok = AppInspector.reportAppsToServer(context)
                                isLoading = false
                                Toast.makeText(context, if (ok) "Installed Apps Synchronized to Server!" else "Sync Failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Sync Installed Apps to Server", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Device Installed Apps (${installedApps.size})", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(installedApps) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(app.appName.take(1).uppercase(), color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                Text(app.packageName, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Content Manager / Admin View: Select Assigned Default User
            if (currentRole.lowercase() == "admin") {
                AdminUserManagementSection(
                    userList = userList,
                    onUpdateRole = { targetUser, newRole ->
                        scope.launch {
                            try {
                                val token = RetrofitClient.getBearerToken()
                                val resp = RetrofitClient.apiService.updateRole(
                                    token,
                                    UpdateRoleRequest(targetUser, newRole)
                                )
                                if (resp.isSuccessful && resp.body()?.success == true) {
                                    Toast.makeText(context, "Role Updated: $targetUser -> $newRole", Toast.LENGTH_SHORT).show()
                                    // Refresh user list
                                    val refreshResp = RetrofitClient.apiService.getUsersList(token)
                                    if (refreshResp.isSuccessful) {
                                        userList = refreshResp.body()?.users ?: emptyList()
                                    }
                                } else {
                                    Toast.makeText(context, resp.body()?.error ?: "Failed to update role.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onAssignManager = { manager, defaultUser ->
                        scope.launch {
                            try {
                                val token = RetrofitClient.getBearerToken()
                                val resp = RetrofitClient.apiService.assignManager(
                                    token,
                                    AssignManagerRequest(manager, defaultUser)
                                )
                                if (resp.isSuccessful && resp.body()?.success == true) {
                                    Toast.makeText(context, "Manager Assigned Successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, resp.body()?.error ?: "Failed to assign manager.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Target User Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Target Assigned User Management", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = selectedTargetUser,
                        onValueChange = { selectedTargetUser = it },
                        label = { Text("Target Username (Default User)", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Domain Whitelist / Blocklist Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Domain Filter",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Domain Whitelist / Blocklist", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        placeholder = { Text("e.g. twitter.com, instagram.com", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (domainInput.isNotBlank() && selectedTargetUser.isNotBlank()) {
                                    scope.launch {
                                        val token = RetrofitClient.getBearerToken()
                                        val resp = RetrofitClient.apiService.updateAppRule(
                                            token,
                                            UpdateAppRuleRequest(
                                                targetUsername = selectedTargetUser,
                                                packageName = domainInput.trim().lowercase(),
                                                appName = "Domain: " + domainInput.trim().lowercase(),
                                                ruleType = "DOMAIN_BLOCKED",
                                                isActive = true
                                            )
                                        )
                                        if (resp.isSuccessful && resp.body()?.success == true) {
                                            DomainFilterManager.blockDomain(context, domainInput)
                                            blockedDomainsList = DomainFilterManager.getBlockedDomains(context)
                                            domainAlertMessage = "Domain '$domainInput' BLOCKED for user '$selectedTargetUser'!"
                                            domainInput = ""
                                        } else {
                                            Toast.makeText(context, resp.body()?.error ?: "Failed to block domain.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Block Domain", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (domainInput.isNotBlank() && selectedTargetUser.isNotBlank()) {
                                    scope.launch {
                                        val token = RetrofitClient.getBearerToken()
                                        val resp = RetrofitClient.apiService.updateAppRule(
                                            token,
                                            UpdateAppRuleRequest(
                                                targetUsername = selectedTargetUser,
                                                packageName = domainInput.trim().lowercase(),
                                                appName = "Domain: " + domainInput.trim().lowercase(),
                                                ruleType = "DOMAIN_ALLOWED",
                                                isActive = true
                                            )
                                        )
                                        if (resp.isSuccessful && resp.body()?.success == true) {
                                            DomainFilterManager.unblockDomain(context, domainInput)
                                            blockedDomainsList = DomainFilterManager.getBlockedDomains(context)
                                            domainAlertMessage = "Domain '$domainInput' WHITELISTED for user '$selectedTargetUser'!"
                                            domainInput = ""
                                        } else {
                                            Toast.makeText(context, resp.body()?.error ?: "Failed to whitelist domain.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Whitelist", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Installed Apps Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Apps of $selectedTargetUser (${if (targetUserApps.isNotEmpty()) targetUserApps.size else installedApps.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayApps = if (targetUserApps.isNotEmpty()) targetUserApps else installedApps

            // Installed Apps List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayApps) { app ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAppForModal = app },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(app.appName.take(1).uppercase(), color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                Text(app.packageName, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }

                            Text("Configure", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog for App Actions (Block, PIN, Time Limit)
    selectedAppForModal?.let { app ->
        AppActionModalDialog(
            app = app,
            targetUsername = if (isManagerOrAdmin) selectedTargetUser else currentUsername,
            onDismiss = { selectedAppForModal = null },
            onApplyRule = { ruleType, pin, tStart, tEnd ->
                scope.launch {
                    try {
                        val token = RetrofitClient.getBearerToken()
                        val target = if (isManagerOrAdmin) selectedTargetUser else currentUsername
                        val resp = RetrofitClient.apiService.updateAppRule(
                            token,
                            UpdateAppRuleRequest(
                                targetUsername = target,
                                packageName = app.packageName,
                                appName = app.appName,
                                ruleType = ruleType,
                                timeStart = tStart,
                                timeEnd = tEnd,
                                pinCode = pin,
                                isActive = true
                            )
                        )
                        if (resp.isSuccessful && resp.body()?.success == true) {
                            domainAlertMessage = "Rule '$ruleType' applied to ${app.appName} for user '$target'!"
                        } else {
                            Toast.makeText(context, resp.body()?.error ?: "Failed to apply rule.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        selectedAppForModal = null
                    }
                }
            }
        )
    }

    // Domain Alert Modal Dialog
    domainAlertMessage?.let { msg ->
        Dialog(onDismissRequest = { domainAlertMessage = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Access Rule Applied",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { domainAlertMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUserManagementSection(
    userList: List<UserDto>,
    onUpdateRole: (targetUser: String, newRole: String) -> Unit,
    onAssignManager: (manager: String, defaultUser: String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Admin Role & Manager Assignments", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(modifier = Modifier.height(160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(userList) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Role: ${user.role}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val nextRole = when (user.role) {
                                    "default" -> "content_manager"
                                    "content_manager" -> "admin"
                                    else -> "default"
                                }
                                onUpdateRole(user.username, nextRole)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Role: ${user.role}", fontSize = 10.sp)
                        }

                        if (user.role == "default") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val managers = userList.filter { it.role == "content_manager" || it.role == "admin" }
                                    if (managers.isNotEmpty()) {
                                        onAssignManager(managers.first().username, user.username)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Assign", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppActionModalDialog(
    app: DeviceAppDto,
    targetUsername: String,
    onDismiss: () -> Unit,
    onApplyRule: (ruleType: String, pinCode: String?, timeStart: String?, timeEnd: String?) -> Unit
) {
    var pinCodeInput by remember { mutableStateOf("1234") }
    var timeStartInput by remember { mutableStateOf("09:00") }
    var timeEndInput by remember { mutableStateOf("17:00") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Target User: $targetUsername",
                            fontSize = 11.sp,
                            color = Color(0xFF10B981)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Text(text = app.packageName, fontSize = 11.sp, color = Color(0xFF94A3B8))

                Spacer(modifier = Modifier.height(16.dp))

                // Action 1: Block App
                Button(
                    onClick = { onApplyRule("ALWAYS_BLOCKED", null, null, null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Block Application Completely", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action 2: Allow App
                Button(
                    onClick = { onApplyRule("ALWAYS_ALLOWED", null, null, null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unblock / Always Allow", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action 3: PIN Lock
                Text("PIN Protection", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pinCodeInput,
                        onValueChange = { pinCodeInput = it },
                        label = { Text("4-digit PIN", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApplyRule("PIN_LOCKED", pinCodeInput, null, null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set PIN")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action 4: Time Restriction
                Text("Time-Restricted Access", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = timeStartInput,
                        onValueChange = { timeStartInput = it },
                        label = { Text("Start", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = timeEndInput,
                        onValueChange = { timeEndInput = it },
                        label = { Text("End", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onApplyRule("TIME_RESTRICTED", null, timeStartInput, timeEndInput) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply Time Limits", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
