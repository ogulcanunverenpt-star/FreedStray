package com.godot.freedstray.data.remote

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val username: String,
    val password: String,
    val enable2FA: Boolean = false
)

data class LoginRequest(
    val username: String,
    val password: String,
    val totpToken: String? = null
)

data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val username: String? = null,
    val role: String? = null,
    val totpEnabled: Boolean = false,
    val requires2FA: Boolean = false,
    val qrCode: String? = null,
    val message: String? = null,
    val error: String? = null
)

data class UserDto(
    val id: Int? = null,
    val username: String,
    val role: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("totp_enabled") val totpEnabled: Int? = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class UsersListResponse(
    val success: Boolean,
    val users: List<UserDto> = emptyList(),
    val error: String? = null
)

data class UpdateRoleRequest(
    val targetUsername: String,
    val newRole: String
)

data class AssignManagerRequest(
    val managerUsername: String,
    val defaultUsername: String
)

data class ManagerAssignmentDto(
    val id: Int? = null,
    @SerializedName("manager_username") val managerUsername: String,
    @SerializedName("default_username") val defaultUsername: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ManagerAssignmentsResponse(
    val success: Boolean,
    val assignments: List<ManagerAssignmentDto> = emptyList(),
    val error: String? = null
)

data class DeviceAppDto(
    val id: Int? = null,
    val username: String? = null,
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_name") val appName: String,
    @SerializedName("is_system") val isSystem: Int = 0
)

data class ReportAppsRequest(
    val apps: List<DeviceAppDto>
)

data class AppsListResponse(
    val success: Boolean,
    val apps: List<DeviceAppDto> = emptyList(),
    val error: String? = null
)

data class AppRuleDto(
    val id: Int? = null,
    @SerializedName("manager_username") val managerUsername: String? = null,
    @SerializedName("target_username") val targetUsername: String,
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_name") val appName: String? = null,
    @SerializedName("rule_type") val ruleType: String, // ALWAYS_ALLOWED, ALWAYS_BLOCKED, TIME_RESTRICTED, PIN_LOCKED, FULL_PHONE_PIN
    @SerializedName("time_start") val timeStart: String? = null,
    @SerializedName("time_end") val timeEnd: String? = null,
    @SerializedName("pin_code") val pinCode: String? = null,
    @SerializedName("is_active") val isActive: Int = 1
)

data class UpdateAppRuleRequest(
    val targetUsername: String,
    val packageName: String,
    val appName: String? = null,
    val ruleType: String,
    val timeStart: String? = null,
    val timeEnd: String? = null,
    val pinCode: String? = null,
    val isActive: Boolean = true
)

data class ActiveRulesResponse(
    val success: Boolean,
    val rules: List<AppRuleDto> = emptyList(),
    val error: String? = null
)

data class GenericResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)
