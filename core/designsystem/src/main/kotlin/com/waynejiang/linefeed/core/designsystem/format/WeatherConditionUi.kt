package com.waynejiang.linefeed.core.designsystem.format

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.waynejiang.linefeed.core.designsystem.R
import com.waynejiang.linefeed.core.domain.model.WeatherCondition

/** Icon + label pair the weather hero/detail screens render for a given [WeatherCondition]. */
data class WeatherConditionUi(val icon: ImageVector, @StringRes val labelRes: Int)

fun WeatherCondition.toUi(): WeatherConditionUi = when (this) {
    WeatherCondition.CLEAR -> WeatherConditionUi(Icons.Filled.WbSunny, R.string.designsystem_weather_clear)
    WeatherCondition.PARTLY_CLOUDY -> WeatherConditionUi(Icons.Filled.WbCloudy, R.string.designsystem_weather_partly_cloudy)
    WeatherCondition.CLOUDY -> WeatherConditionUi(Icons.Filled.Cloud, R.string.designsystem_weather_cloudy)
    WeatherCondition.FOG -> WeatherConditionUi(Icons.Filled.CloudQueue, R.string.designsystem_weather_fog)
    WeatherCondition.DRIZZLE -> WeatherConditionUi(Icons.Filled.WaterDrop, R.string.designsystem_weather_drizzle)
    WeatherCondition.RAIN -> WeatherConditionUi(Icons.Filled.Grain, R.string.designsystem_weather_rain)
    WeatherCondition.SNOW -> WeatherConditionUi(Icons.Filled.AcUnit, R.string.designsystem_weather_snow)
    WeatherCondition.SHOWERS -> WeatherConditionUi(Icons.Filled.Grain, R.string.designsystem_weather_showers)
    WeatherCondition.THUNDERSTORM -> WeatherConditionUi(Icons.Filled.Thunderstorm, R.string.designsystem_weather_thunderstorm)
    WeatherCondition.UNKNOWN -> WeatherConditionUi(Icons.AutoMirrored.Filled.HelpOutline, R.string.designsystem_weather_unknown)
}
