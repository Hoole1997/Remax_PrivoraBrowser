package com.browser.weather.data

import com.google.gson.annotations.SerializedName

// ==================== Open-Meteo API 响应模型 ====================

/**
 * Open-Meteo 天气预报 API 响应
 * https://api.open-meteo.com/v1/forecast
 */
data class OpenMeteoResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val current: OpenMeteoCurrent? = null,
    val daily: OpenMeteoDaily? = null
)

/**
 * Open-Meteo 当前天气
 */
data class OpenMeteoCurrent(
    @SerializedName("temperature_2m") val temperature: Double? = null,
    @SerializedName("relative_humidity_2m") val humidity: Int? = null,
    @SerializedName("is_day") val isDay: Int? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("surface_pressure") val pressure: Double? = null,
    @SerializedName("wind_speed_10m") val windSpeed: Double? = null,
    @SerializedName("wind_direction_10m") val windDirection: Double? = null,
    @SerializedName("uv_index") val uvIndex: Double? = null,
    @SerializedName("visibility") val visibility: Double? = null
)

/**
 * Open-Meteo 每日预报
 */
data class OpenMeteoDaily(
    val time: List<String>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null,
    @SerializedName("temperature_2m_max") val tempMax: List<Double>? = null,
    @SerializedName("temperature_2m_min") val tempMin: List<Double>? = null,
    val sunrise: List<String>? = null,
    val sunset: List<String>? = null,
    @SerializedName("precipitation_probability_max") val precipProbMax: List<Int>? = null
)

// ==================== Open-Meteo Geocoding API 响应模型 ====================

/**
 * Open-Meteo 地理编码搜索响应
 * https://geocoding-api.open-meteo.com/v1/search
 */
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

/**
 * 地理编码搜索结果
 */
data class GeocodingResult(
    val id: Int? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    val admin1: String? = null,
    @SerializedName("country_code") val countryCode: String? = null
)

// ==================== IP 地理定位响应模型 ====================

/**
 * IP 地理定位 API 响应 (api.ip.sb)
 */
data class IpGeoResponse(
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

/**
 * 备用 IP 地理定位 API 响应 (ip-api.com)
 * http://ip-api.com/json/
 */
data class IpApiComResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("regionName") val regionName: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    @SerializedName("query") val query: String? = null
)

// ==================== UI 展示数据模型 ====================

/**
 * 每日预报简化数据，用于 UI 展示
 */
data class DailyForecastData(
    val dayOfWeek: String,         // 如 "Thu", "Fri"
    val iconId: Int,               // WMO 天气代码
    val tempHigh: Int,             // 最高温度 (摄氏度)
    val tempLow: Int,              // 最低温度 (摄氏度)
    val precipitationProbability: Int  // 降水概率 %
)

/**
 * 整合后的天气数据，用于 UI 展示
 */
data class WeatherData(
    val cityName: String,
    val temperature: Int,          // 摄氏度
    val temperatureHigh: Int,      // 最高温度 (摄氏度)
    val temperatureLow: Int,       // 最低温度 (摄氏度)
    val weatherText: String,       // 如 "Cloudy", "Sunny"
    val weatherIcon: Int,          // WMO 天气代码
    val humidity: Int,             // 湿度百分比
    val uvIndex: Int,              // 紫外线指数
    val uvIndexText: String,       // 紫外线描述
    val windSpeed: Float,          // 风速 km/h
    val windDirection: String,     // 风向
    val isDayTime: Boolean,
    val pressure: Float,           // 气压 mb/hPa
    val visibility: Float,         // 能见度 km
    val sunrise: String,           // 日出时间 如 "07:52"
    val sunset: String,            // 日落时间 如 "17:16"
    val precipitationProbability: Int,  // 降水概率 %
    val dailyForecasts: List<DailyForecastData>  // 5天预报
)
