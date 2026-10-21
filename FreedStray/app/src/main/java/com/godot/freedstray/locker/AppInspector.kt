package com.godot.freedstray.locker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.godot.freedstray.data.remote.DeviceAppDto
import com.godot.freedstray.data.remote.ReportAppsRequest
import com.godot.freedstray.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppInspector {

    private const val TAG = "AppInspector"

    fun getInstalledApps(context: Context): List<DeviceAppDto> {
        val pm = context.packageManager
        val apps = mutableListOf<DeviceAppDto>()

        try {
            val installedApplications = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApplications) {
                // Filter launchable or user apps
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName

                // Skip self package
                if (packageName == context.packageName) continue

                apps.add(
                    DeviceAppDto(
                        packageName = packageName,
                        appName = appName,
                        isSystem = if (isSystem) 1 else 0
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps: ${e.message}")
        }

        return apps.sortedBy { it.appName }
    }

    suspend fun reportAppsToServer(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = RetrofitClient.getBearerToken()
                if (token.isBlank() || RetrofitClient.authToken.isNullOrEmpty()) {
                    Log.w(TAG, "Auth token missing, skipping app report")
                    return@withContext false
                }

                val appList = getInstalledApps(context)
                val response = RetrofitClient.apiService.reportInstalledApps(
                    token = token,
                    request = ReportAppsRequest(apps = appList)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.i(TAG, "Successfully reported ${appList.size} installed apps to server")
                    true
                } else {
                    Log.e(TAG, "App report failed: ${response.errorBody()?.string()}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report apps to server: ${e.message}")
                false
            }
        }
    }
}
