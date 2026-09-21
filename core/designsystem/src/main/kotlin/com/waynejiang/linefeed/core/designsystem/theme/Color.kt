package com.waynejiang.linefeed.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// LineFeed's own green (not LINE's brand green, not a dynamic/Material You color): a slightly
// deeper, less saturated green so it reads calmly next to a weather hero card and doesn't fight
// with article thumbnails. Picked and tuned by hand rather than generated from a Material theme
// builder, then checked for ~4.5:1 contrast against both surface colors below.
internal val Green10 = Color(0xFF00210A)
internal val Green20 = Color(0xFF033913)
internal val Green30 = Color(0xFF0A5420)
internal val Green40 = Color(0xFF13712E)
internal val Green80 = Color(0xFF8CDA9C)
internal val Green90 = Color(0xFFA9F5B8)
internal val Green95 = Color(0xFFC6FFCF)
internal val Green99 = Color(0xFFF6FFF1)

internal val GreenGray10 = Color(0xFF171D18)
internal val GreenGray20 = Color(0xFF2B322C)
internal val GreenGray90 = Color(0xFFDEE5DC)
internal val GreenGray95 = Color(0xFFEDF2EA)
internal val GreenGray99 = Color(0xFFF9FBF6)

internal val Teal20 = Color(0xFF00363D)
internal val Teal40 = Color(0xFF4B6268)
internal val Teal80 = Color(0xFFB2CBD2)
internal val Teal90 = Color(0xFFCEE7EE)

internal val Amber40 = Color(0xFF7A5900)
internal val Amber80 = Color(0xFFFABD1B)
internal val Amber90 = Color(0xFFFFDF9C)
internal val Amber10 = Color(0xFF261A00)

internal val Red10 = Color(0xFF410002)
internal val Red20 = Color(0xFF690005)
internal val Red40 = Color(0xFFBA1A1A)
internal val Red80 = Color(0xFFFFB4AB)
internal val Red90 = Color(0xFFFFDAD6)

/** Weather hero card uses its own fixed-ish green wash independent of light/dark surface colors. */
internal val WeatherHeroLight = Color(0xFF14522A)
internal val WeatherHeroDark = Color(0xFF0A3B1B)
