package com.collegeproj.crashpulse.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "crashpulse_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val accessToken: String? get() = preferences.getString(KEY_ACCESS, null)
    val refreshToken: String? get() = preferences.getString(KEY_REFRESH, null)
    val hasSession: Boolean get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()
    fun save(accessToken: String, refreshToken: String) = preferences.edit().putString(KEY_ACCESS, accessToken).putString(KEY_REFRESH, refreshToken).apply()
    fun clear() = preferences.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
