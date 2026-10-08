package com.collegeproj.crashpulse.core.di

import android.app.Application

class CrashPulseApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
