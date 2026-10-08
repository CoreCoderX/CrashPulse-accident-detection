package com.collegeproj.crashpulse.core.storage

import android.content.Context
import com.collegeproj.crashpulse.BuildConfig

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("crashpulse_preferences", Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = preferences.getString(KEY_API_URL, BuildConfig.DEFAULT_API_BASE_URL) ?: BuildConfig.DEFAULT_API_BASE_URL
        set(value) {
            preferences.edit().putString(KEY_API_URL, normalize(value)).apply()
        }

    fun normalize(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private companion object { const val KEY_API_URL = "api_base_url" }
}
