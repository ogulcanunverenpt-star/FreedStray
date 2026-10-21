package com.godot.freedstray.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<AuthResponse>

    @GET("api/users/list")
    suspend fun getUsersList(@Header("Authorization") token: String): Response<UsersListResponse>

    @POST("api/users/update-role")
    suspend fun updateRole(
        @Header("Authorization") token: String,
        @Body request: UpdateRoleRequest
    ): Response<GenericResponse>

    @POST("api/manager/assign")
    suspend fun assignManager(
        @Header("Authorization") token: String,
        @Body request: AssignManagerRequest
    ): Response<GenericResponse>

    @GET("api/manager/assignments")
    suspend fun getManagerAssignments(@Header("Authorization") token: String): Response<ManagerAssignmentsResponse>

    @POST("api/apps/report")
    suspend fun reportInstalledApps(
        @Header("Authorization") token: String,
        @Body request: ReportAppsRequest
    ): Response<GenericResponse>

    @GET("api/apps/list")
    suspend fun getInstalledApps(
        @Header("Authorization") token: String,
        @Query("targetUsername") targetUsername: String? = null
    ): Response<AppsListResponse>

    @POST("api/apps/rule/update")
    suspend fun updateAppRule(
        @Header("Authorization") token: String,
        @Body request: UpdateAppRuleRequest
    ): Response<GenericResponse>

    @GET("api/apps/rules/active")
    suspend fun getActiveAppRules(
        @Header("Authorization") token: String,
        @Query("targetUsername") targetUsername: String? = null
    ): Response<ActiveRulesResponse>
}
