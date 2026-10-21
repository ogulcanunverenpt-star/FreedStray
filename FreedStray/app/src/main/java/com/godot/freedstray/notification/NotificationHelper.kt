package com.godot.freedstray.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.godot.freedstray.MainActivity

object NotificationHelper {

    const val SERVICE_CHANNEL_ID = "freedstray_service_channel"
    const val CHAT_CHANNEL_ID = "freedstray_chat_channel"
    const val CALL_CHANNEL_ID = "freedstray_call_channel"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "FreedStray Socket Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps persistent socket connection to FreedStray server"
            }

            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID,
                "FreedStray Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming chat messages"
            }

            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "FreedStray Voice Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice call alerts"
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(chatChannel)
            manager.createNotificationChannel(callChannel)
        }
    }

    fun buildForegroundServiceNotification(context: Context, statusText: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("FreedStray Service")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showChatNotification(context: Context, sender: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "chat")
            putExtra("chat_sender", sender)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setContentTitle("Message from $sender")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showVoiceCallNotification(context: Context, callerName: String) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "chat")
            putExtra("chat_sender", callerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 100, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "chat")
            putExtra("chat_sender", callerName)
            putExtra("call_action", "ACCEPT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context, 101, acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rejectIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "chat")
            putExtra("chat_sender", callerName)
            putExtra("call_action", "REJECT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            context, 102, rejectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setContentTitle("Incoming Voice Call")
            .setContentText("$callerName is calling...")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9999, notification)
    }

    fun showVideoCallNotification(context: Context, callerName: String) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "video_call")
            putExtra("chat_sender", callerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 200, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "video_call")
            putExtra("chat_sender", callerName)
            putExtra("video_call_action", "ACCEPT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context, 201, acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rejectIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "video_call")
            putExtra("chat_sender", callerName)
            putExtra("video_call_action", "REJECT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            context, 202, rejectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setContentTitle("Gelen Görüntülü Arama")
            .setContentText("$callerName görüntülü arıyor...")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Yanıtla", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reddet", rejectPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9998, notification)
    }

    fun clearCallNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(9999)
    }
}
