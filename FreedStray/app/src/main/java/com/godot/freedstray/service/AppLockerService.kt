package com.godot.freedstray.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.godot.freedstray.data.remote.AppRuleDto
import com.godot.freedstray.data.remote.RetrofitClient
import com.godot.freedstray.notification.NotificationHelper
import com.godot.freedstray.ui.LockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLockerService : Service() {

    companion object {
        private const val TAG = "AppLockerService"
        private const val NOTIFICATION_ID = 1002

        private val unlockedPackages = mutableSetOf<String>()

        fun unlockPackage(packageName: String) {
            unlockedPackages.add(packageName)
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var activeRules = listOf<AppRuleDto>()
    private var lastRulesFetchTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AppLockerService created.")
        NotificationHelper.createNotificationChannels(this)

        startForeground(
            NOTIFICATION_ID,
            NotificationHelper.buildForegroundServiceNotification(this, "Uygulama Modu ve Kilit Takibi Aktif")
        )

        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (true) {
                try {
                    // Fetch rules every 30 seconds
                    if (System.currentTimeMillis() - lastRulesFetchTime > 30000) {
                        fetchActiveRules()
                    }

                    val currentForegroundPkg = getForegroundPackageName()
                    if (currentForegroundPkg.isNotEmpty() && currentForegroundPkg != packageName) {
                        checkAndEnforceRule(currentForegroundPkg)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Monitoring loop error: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private suspend fun fetchActiveRules() {
        try {
            val token = RetrofitClient.getBearerToken()
            if (token.isNotBlank() && !RetrofitClient.authToken.isNullOrEmpty()) {
                val response = RetrofitClient.apiService.getActiveAppRules(token)
                if (response.isSuccessful && response.body()?.success == true) {
                    activeRules = response.body()?.rules ?: emptyList()
                    lastRulesFetchTime = System.currentTimeMillis()
                    Log.d(TAG, "Fetched ${activeRules.size} active app rules")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active rules: ${e.message}")
        }
    }

    private fun checkAndEnforceRule(packageName: String) {
        val fullPhoneRule = activeRules.firstOrNull { it.ruleType == "FULL_PHONE_PIN" && it.isActive == 1 }
        if (fullPhoneRule != null && !unlockedPackages.contains("FULL_PHONE_PIN")) {
            launchLockOverlay(
                packageName = packageName,
                appName = "Telefon",
                ruleType = "FULL_PHONE_PIN",
                pinCode = fullPhoneRule.pinCode ?: "",
                reason = "Telefon bütünüyle PIN koruma moduna alınmıştır."
            )
            return
        }

        val matchingRule = activeRules.firstOrNull { it.packageName == packageName && it.isActive == 1 } ?: return

        when (matchingRule.ruleType) {
            "ALWAYS_BLOCKED" -> {
                launchLockOverlay(
                    packageName = packageName,
                    appName = matchingRule.appName ?: packageName,
                    ruleType = "ALWAYS_BLOCKED",
                    pinCode = "",
                    reason = "Bu uygulama yönetici tarafından engellenmiştir."
                )
            }
            "TIME_RESTRICTED" -> {
                val isPermitted = isCurrentTimeInWindow(matchingRule.timeStart, matchingRule.timeEnd)
                if (!isPermitted) {
                    launchLockOverlay(
                        packageName = packageName,
                        appName = matchingRule.appName ?: packageName,
                        ruleType = "TIME_RESTRICTED",
                        pinCode = matchingRule.pinCode ?: "",
                        reason = "Bu uygulama sadece ${matchingRule.timeStart ?: "00:00"} - ${matchingRule.timeEnd ?: "23:59"} saatleri arasında kullanılabilir."
                    )
                }
            }
            "PIN_LOCKED" -> {
                if (!unlockedPackages.contains(packageName)) {
                    launchLockOverlay(
                        packageName = packageName,
                        appName = matchingRule.appName ?: packageName,
                        ruleType = "PIN_LOCKED",
                        pinCode = matchingRule.pinCode ?: "",
                        reason = "Bu uygulamaya girmek için PIN kodunu yazmalısınız."
                    )
                }
            }
        }
    }

    private fun isCurrentTimeInWindow(startTime: String?, endTime: String?): Boolean {
        if (startTime.isNullOrBlank() || endTime.isNullOrBlank()) return true
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val nowStr = sdf.format(Date())
            val now = sdf.parse(nowStr)
            val start = sdf.parse(startTime)
            val end = sdf.parse(endTime)

            if (now != null && start != null && end != null) {
                if (start.before(end)) {
                    now.after(start) && now.before(end)
                } else {
                    // Overnight range (e.g. 22:00 to 06:00)
                    now.after(start) || now.before(end)
                }
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun getForegroundPackageName(): String {
        var currentApp = ""
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val usageEvents = usm.queryEvents(time - 2000, time)
            val event = UsageEvents.Event()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentApp = event.packageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting usage stats: ${e.message}")
        }
        return currentApp
    }

    private fun launchLockOverlay(packageName: String, appName: String, ruleType: String, pinCode: String, reason: String) {
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("package_name", packageName)
            putExtra("app_name", appName)
            putExtra("rule_type", ruleType)
            putExtra("pin_code", pinCode)
            putExtra("reason", reason)
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AppLockerService destroyed.")
        serviceJob.cancel()
    }
}
