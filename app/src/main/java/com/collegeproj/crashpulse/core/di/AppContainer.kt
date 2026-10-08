package com.collegeproj.crashpulse.core.di

import android.content.Context
import com.collegeproj.crashpulse.core.network.ApiClient
import com.collegeproj.crashpulse.core.network.CrashPulseRepository
import com.collegeproj.crashpulse.core.storage.AppPreferences
import com.collegeproj.crashpulse.core.storage.SessionStore

class AppContainer(context: Context) {
    val sessionStore = SessionStore(context)
    val preferences = AppPreferences(context)
    val apiClient = ApiClient(sessionStore, preferences)
    val repository = CrashPulseRepository(apiClient, sessionStore, preferences)
}
