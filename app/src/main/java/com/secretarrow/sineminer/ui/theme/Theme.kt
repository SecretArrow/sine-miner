package com.secretarrow.sineminer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

private val Scrim = Color(0xFF000000)

/**
 * Theme configuration for SineMiner.
 *
 * - Material 3 by default.
 * - Optional Material You dynamic color on Android 12+ (set in settings).
 * - Brand color (teal/cyan) preserved across both schemes as primary.
 */
@Composable
fun SineMinerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                @Suppress("DEPRECATION")
                window.decorView.systemBarsVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SineMinerTypography,
        shapes = SineMinerShapes,
        content = content,
    )
}

val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = OnSurfaceLight,
    primaryContainer = BrandPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandSecondary,
    onSecondary = OnSurfaceLight,
    secondaryContainer = BrandSecondary.copy(alpha = 0.15f),
    onSecondaryContainer = BrandSecondary,
    tertiary = BrandAccent,
    onTertiary = OnSurfaceLight,
    tertiaryContainer = BrandAccent.copy(alpha = 0.15f),
    onTertiaryContainer = BrandAccent,
    error = BrandError,
    onError = OnSurfaceLight,
    errorContainer = BrandError.copy(alpha = 0.15f),
    onErrorContainer = BrandError,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = BrandPrimary,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Scrim,
)

val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = OnSurfaceDark,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = BrandPrimary,
    secondary = BrandSecondary,
    onSecondary = OnSurfaceDark,
    secondaryContainer = BrandSecondary.copy(alpha = 0.20f),
    onSecondaryContainer = BrandSecondary,
    tertiary = BrandAccent,
    onTertiary = OnSurfaceDark,
    tertiaryContainer = BrandAccent.copy(alpha = 0.20f),
    onTertiaryContainer = BrandAccent,
    error = BrandError,
    onError = OnSurfaceDark,
    errorContainer = BrandError.copy(alpha = 0.20f),
    onErrorContainer = BrandError,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = BrandPrimary,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = Scrim,
)
