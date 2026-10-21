package com.godot.freedstray.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.godot.freedstray.service.AppLockerService
import com.godot.freedstray.service.SocketService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device boot completed. Starting FreedStray services...")

            val socketIntent = Intent(context, SocketService::class.java)
            val lockerIntent = Intent(context, AppLockerService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(socketIntent)
                context.startForegroundService(lockerIntent)
            } else {
                context.startService(socketIntent)
                context.startService(lockerIntent)
            }
        }
    }
}
