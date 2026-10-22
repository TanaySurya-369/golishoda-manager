package com.golishoda.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF087F72)
private val LightColors = lightColorScheme(primary = Green, secondary = Color(0xFF386A60), background = Color(0xFFF7F9F8), surface = Color.White)
private val DarkColors = darkColorScheme(primary = Color(0xFF73D8C7), secondary = Color(0xFFA0D0C6))

@Composable
fun GolishodaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
