package com.godot.freedstray.data.local

import android.content.Context
import android.content.SharedPreferences

object DomainFilterManager {

    private const val PREF_NAME = "freedstray_domain_filter"
    private const val KEY_BLOCKED_DOMAINS = "blocked_domains"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getBlockedDomains(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_BLOCKED_DOMAINS, emptySet()) ?: emptySet()
    }

    @Synchronized
    fun blockDomain(context: Context, domain: String): Boolean {
        val cleanDomain = cleanDomainString(domain)
        if (cleanDomain.isEmpty()) return false

        val current = getBlockedDomains(context).toMutableSet()
        current.add(cleanDomain)
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_DOMAINS, current).apply()
        return true
    }

    @Synchronized
    fun unblockDomain(context: Context, domain: String): Boolean {
        val cleanDomain = cleanDomainString(domain)
        if (cleanDomain.isEmpty()) return false

        val current = getBlockedDomains(context).toMutableSet()
        current.remove(cleanDomain)
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_DOMAINS, current).apply()
        return true
    }

    fun isDomainBlocked(context: Context, domain: String): Boolean {
        val cleanDomain = cleanDomainString(domain)
        if (cleanDomain.isEmpty()) return false

        val blocked = getBlockedDomains(context)
        return blocked.any { cleanDomain == it || cleanDomain.endsWith(".$it") }
    }

    private fun cleanDomainString(domain: String): String {
        return domain.trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .split("/").firstOrNull() ?: ""
    }
}
