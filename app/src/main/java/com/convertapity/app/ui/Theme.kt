package com.convertapity.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette (violet) for Android 10/11, which have no dynamic color
private val Light = lightColorScheme(
    primary = Color(0xFF6B4EA2), onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDCFF), onPrimaryContainer = Color(0xFF260A5A),
    secondary = Color(0xFF645A70), secondaryContainer = Color(0xFFEBDDF7), onSecondaryContainer = Color(0xFF1F182A),
    tertiary = Color(0xFF7F525A), tertiaryContainer = Color(0xFFFFD9DE), onTertiaryContainer = Color(0xFF321018),
    surface = Color(0xFFFEF7FF), surfaceContainerLow = Color(0xFFF8F1FA), surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE), surfaceContainerHighest = Color(0xFFE6E0E9),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFD3BBFF), onPrimary = Color(0xFF3C1F71),
    primaryContainer = Color(0xFF533689), onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = Color(0xFFCFC1DA), secondaryContainer = Color(0xFF4C4357), onSecondaryContainer = Color(0xFFEBDDF7),
    tertiary = Color(0xFFF1B7C1), tertiaryContainer = Color(0xFF653B43), onTertiaryContainer = Color(0xFFFFD9DE),
    surface = Color(0xFF151218), surfaceContainerLow = Color(0xFF1D1A20), surfaceContainer = Color(0xFF221E24),
    surfaceContainerHigh = Color(0xFF2C292F), surfaceContainerHighest = Color(0xFF37333A),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConvertapityTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}
