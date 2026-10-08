package com.collegeproj.crashpulse.feature.notifications

import android.content.Context

class AlertDeduplicator(context: Context) {
    private val preferences = context.getSharedPreferences("crashpulse_alert_delivery", Context.MODE_PRIVATE)

    @Synchronized
    fun accept(incidentId: String): Boolean {
        if (preferences.getBoolean(incidentId, false)) return false
        preferences.edit().putBoolean(incidentId, true).apply()
        return true
    }
}
