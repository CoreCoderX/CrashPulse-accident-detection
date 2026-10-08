package com.collegeproj.crashpulse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFFC83B45), secondary = Color(0xFF536477), background = Color(0xFFF8F9FB), surface = Color(0xFFFFFFFF))
private val Dark = darkColorScheme(primary = Color(0xFFFF8B8B), secondary = Color(0xFFBAC8D8), background = Color(0xFF111419), surface = Color(0xFF1B1E24))

@Composable
fun CrashPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
