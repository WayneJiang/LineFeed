package com.waynejiang.linefeed.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal20,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = GreenGray99,
    onBackground = GreenGray10,
    surface = GreenGray99,
    onSurface = GreenGray10,
    surfaceVariant = GreenGray95,
    onSurfaceVariant = GreenGray20,
    outline = GreenGray20,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Amber10,
    tertiaryContainer = Amber40,
    onTertiaryContainer = Amber90,
    error = Red80,
    onError = Red20,
    errorContainer = Red40,
    onErrorContainer = Red90,
    background = GreenGray10,
    onBackground = GreenGray90,
    surface = GreenGray10,
    onSurface = GreenGray90,
    surfaceVariant = GreenGray20,
    onSurfaceVariant = GreenGray90,
    outline = GreenGray90,
)

/** Extra brand colors Material's [androidx.compose.material3.ColorScheme] has no slot for. */
data class LineFeedExtraColors(val weatherHero: Color)

private val LocalLineFeedExtraColors = staticCompositionLocalOf {
    LineFeedExtraColors(weatherHero = WeatherHeroLight)
}

object LineFeedTheme {
    val extraColors: LineFeedExtraColors
        @Composable get() = LocalLineFeedExtraColors.current
}

/**
 * No dynamic color (PLAN.md §0): a fixed brand palette keeps the weather-hero/service-card/article
 * visual language consistent across every device instead of following the user's wallpaper.
 */
@Composable
fun LineFeedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extraColors = LineFeedExtraColors(weatherHero = if (darkTheme) WeatherHeroDark else WeatherHeroLight)

    androidx.compose.runtime.CompositionLocalProvider(LocalLineFeedExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LineFeedTypography,
            content = content,
        )
    }
}
