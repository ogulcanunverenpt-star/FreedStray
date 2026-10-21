package com.godot.freedstray.data.local

import android.content.Context
import android.content.SharedPreferences

object SessionManager {

    private const val PREF_NAME = "freedstray_session"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"
    private const val KEY_SERVER_HOST = "server_host"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(context: Context, token: String, username: String, role: String, serverHost: String? = null) {
        getPrefs(context).edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            putString(KEY_USERNAME, username)
            putString(KEY_ROLE, role)
            if (!serverHost.isNullOrBlank()) {
                putString(KEY_SERVER_HOST, serverHost)
            }
            apply()
        }
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getAuthToken(context).isNullOrBlank()
    }

    fun getAuthToken(context: Context): String? {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, null)
    }

    fun getUsername(context: Context): String? {
        return getPrefs(context).getString(KEY_USERNAME, null)
    }

    fun getRole(context: Context): String {
        return getPrefs(context).getString(KEY_ROLE, "user") ?: "user"
    }

    fun getServerHost(context: Context): String? {
        return getPrefs(context).getString(KEY_SERVER_HOST, null)
    }
}
