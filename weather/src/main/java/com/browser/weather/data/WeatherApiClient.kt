package com.browser.weather.data

import android.content.Context
import com.blankj.utilcode.util.LogUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 天气 API 客户端
 *
 * 使用免费 API:
 * - 天气数据: Open-Meteo (https://open-meteo.com/)
 * - 城市搜索: Open-Meteo Geocoding API
 * - IP 定位: api.ip.sb
 */
class WeatherApiClient(private val context: Context) {

    companion object {
        private const val TAG = "WeatherApiClient"
        private const val OPEN_METEO_BASE = "https://api.open-meteo.com/v1"
        private const val GEOCODING_BASE = "https://geocoding-api.open-meteo.com/v1"
        private const val IP_GEO_URL = "https://api.ip.sb/geoip"
        private const val IP_GEO_FALLBACK_URL = "http://ip-api.com/json/"

        private const val CURRENT_PARAMS = "temperature_2m,relative_humidity_2m,is_day,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m,uv_index,visibility"
        private const val DAILY_PARAMS = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy { Gson() }

    /**
     * 通过 IP 地址自动定位并获取天气
     * 1. 调用 api.ip.sb 获取经纬度和城市名
     * 2. 调用 Open-Meteo 获取天气数据
     */
    suspend fun getWeatherByIp(): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: IP 定位获取经纬度（主 API + 备用 API）
                val (lat, lon, cityName) = getIpGeoLocation()

                LogUtils.d(TAG, "IP Geo: city=$cityName, lat=$lat, lon=$lon")

                // Step 2: 获取天气数据
                fetchWeather(lat, lon, cityName)
            } catch (e: Exception) {
                LogUtils.e(TAG, "getWeatherByIp error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * IP 定位：先尝试 api.ip.sb，失败则 fallback 到 ip-api.com
     */
    private fun getIpGeoLocation(): Triple<Double, Double, String> {
        // 尝试主 API: api.ip.sb
        try {
            val geoBody = executeRequest(IP_GEO_URL)
            LogUtils.d(TAG, "ip.sb raw response: $geoBody")
            val ipGeo = gson.fromJson(geoBody, IpGeoResponse::class.java)
            val lat = ipGeo.latitude
            val lon = ipGeo.longitude
            if (lat != null && lon != null) {
                val cityName = ipGeo.city?.takeIf { it.isNotBlank() }
                    ?: ipGeo.region?.takeIf { it.isNotBlank() }
                    ?: ipGeo.country?.takeIf { it.isNotBlank() }
                    ?: "My Location"
                return Triple(lat, lon, cityName)
            }
            LogUtils.w(TAG, "ip.sb returned no lat/lon, trying fallback...")
        } catch (e: Exception) {
            LogUtils.w(TAG, "ip.sb failed: ${e.message}, trying fallback...")
        }

        // Fallback API: ip-api.com
        val fallbackBody = executeRequest(IP_GEO_FALLBACK_URL)
        LogUtils.d(TAG, "ip-api.com raw response: $fallbackBody")
        val fallback = gson.fromJson(fallbackBody, IpApiComResponse::class.java)
        val lat = fallback.lat ?: throw Exception("All IP geolocation APIs failed: no latitude")
        val lon = fallback.lon ?: throw Exception("All IP geolocation APIs failed: no longitude")
        val cityName = fallback.city?.takeIf { it.isNotBlank() }
            ?: fallback.regionName?.takeIf { it.isNotBlank() }
            ?: fallback.country?.takeIf { it.isNotBlank() }
            ?: "My Location"
        return Triple(lat, lon, cityName)
    }

    /**
     * 通过经纬度和城市名获取天气
     */
    suspend fun getWeatherByLatLon(lat: Double, lon: Double, cityName: String): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                fetchWeather(lat, lon, cityName)
            } catch (e: Exception) {
                LogUtils.e(TAG, "getWeatherByLatLon error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * 搜索城市 (使用 Open-Meteo Geocoding API)
     */
    suspend fun searchCity(query: String): Result<List<GeocodingResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$GEOCODING_BASE/search?name=$encodedQuery&count=10&language=en&format=json"
                val body = executeRequest(url)
                val response = gson.fromJson(body, GeocodingResponse::class.java)
                val results = response.results ?: emptyList()
                LogUtils.d(TAG, "City search '$query': ${results.size} results")
                Result.success(results)
            } catch (e: Exception) {
                LogUtils.e(TAG, "searchCity error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 调用 Open-Meteo Forecast API 获取天气并转换为 WeatherData
     */
    private fun fetchWeather(lat: Double, lon: Double, cityName: String): Result<WeatherData> {
        val url = "$OPEN_METEO_BASE/forecast?latitude=$lat&longitude=$lon" +
                "&current=$CURRENT_PARAMS" +
                "&daily=$DAILY_PARAMS" +
                "&timezone=auto&forecast_days=5"

        val body = executeRequest(url)
        val response = gson.fromJson(body, OpenMeteoResponse::class.java)

        return parseOpenMeteoResponse(response, cityName)
    }

    /**
     * 解析 Open-Meteo 响应为 WeatherData
     */
    private fun parseOpenMeteoResponse(response: OpenMeteoResponse, cityName: String): Result<WeatherData> {
        try {
            val current = response.current ?: return Result.failure(Exception("No current weather data"))
            val daily = response.daily

            // 当前天气
            val temperature = current.temperature?.toInt() ?: 0
            val weatherCode = current.weatherCode ?: 0
            val isDay = (current.isDay ?: 1) == 1
            val humidity = current.humidity ?: 0
            val uvIndex = current.uvIndex?.toInt() ?: 0
            val windSpeed = current.windSpeed?.toFloat() ?: 0f
            val windDegrees = current.windDirection ?: 0.0
            val pressure = current.pressure?.toFloat() ?: 1013f
            val visibility = (current.visibility ?: 10000.0).toFloat() / 1000f // 米 → 公里

            // 今日高低温
            val tempHigh = daily?.tempMax?.firstOrNull()?.toInt() ?: (temperature + 3)
            val tempLow = daily?.tempMin?.firstOrNull()?.toInt() ?: (temperature - 3)

            // 日出日落
            val sunrise = daily?.sunrise?.firstOrNull()?.let { extractTime(it) } ?: "06:00"
            val sunset = daily?.sunset?.firstOrNull()?.let { extractTime(it) } ?: "18:00"

            // 降水概率
            val precipProbability = daily?.precipProbMax?.firstOrNull() ?: 0

            // 5天预报
            val dailyForecasts = buildDailyForecasts(daily)

            val weatherData = WeatherData(
                cityName = cityName,
                temperature = temperature,
                temperatureHigh = tempHigh,
                temperatureLow = tempLow,
                weatherText = getWeatherText(weatherCode),
                weatherIcon = weatherCode,
                humidity = humidity,
                uvIndex = uvIndex,
                uvIndexText = getUvIndexText(uvIndex),
                windSpeed = windSpeed,
                windDirection = getWindDirectionText(windDegrees),
                isDayTime = isDay,
                pressure = pressure,
                visibility = visibility,
                sunrise = sunrise,
                sunset = sunset,
                precipitationProbability = precipProbability,
                dailyForecasts = dailyForecasts
            )

            return Result.success(weatherData)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Parse error: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * 构建5天预报数据
     */
    private fun buildDailyForecasts(daily: OpenMeteoDaily?): List<DailyForecastData> {
        if (daily == null) return emptyList()

        val times = daily.time ?: return emptyList()
        val count = minOf(times.size, 5)

        return (0 until count).map { i ->
            DailyForecastData(
                dayOfWeek = extractDayOfWeek(times[i]),
                iconId = daily.weatherCode?.getOrNull(i) ?: 0,
                tempHigh = daily.tempMax?.getOrNull(i)?.toInt() ?: 0,
                tempLow = daily.tempMin?.getOrNull(i)?.toInt() ?: 0,
                precipitationProbability = daily.precipProbMax?.getOrNull(i) ?: 0
            )
        }
    }

    private fun executeRequest(url: String): String {
        LogUtils.d(TAG, "Request URL: $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()

        LogUtils.d(TAG, "Response code: ${response.code}")

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            LogUtils.e(TAG, "Error body: $errorBody")
            throw Exception("Request failed: ${response.code} ${response.message}")
        }

        return response.body?.string() ?: throw Exception("Empty response body")
    }

    // ==================== 工具方法 ====================

    /**
     * WMO 天气代码 → 天气描述文字
     */
    private fun getWeatherText(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45 -> "Fog"
            48 -> "Rime Fog"
            51 -> "Light Drizzle"
            53 -> "Moderate Drizzle"
            55 -> "Dense Drizzle"
            56, 57 -> "Freezing Drizzle"
            61 -> "Slight Rain"
            63 -> "Moderate Rain"
            65 -> "Heavy Rain"
            66, 67 -> "Freezing Rain"
            71 -> "Slight Snow"
            73 -> "Moderate Snow"
            75 -> "Heavy Snow"
            77 -> "Snow Grains"
            80 -> "Slight Rain Showers"
            81 -> "Moderate Rain Showers"
            82 -> "Violent Rain Showers"
            85 -> "Slight Snow Showers"
            86 -> "Heavy Snow Showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with Hail"
            else -> "Unknown"
        }
    }

    /**
     * UV 指数 → 描述文字
     */
    private fun getUvIndexText(uvIndex: Int): String {
        return when {
            uvIndex <= 2 -> "Low"
            uvIndex <= 5 -> "Moderate"
            uvIndex <= 7 -> "High"
            uvIndex <= 10 -> "Very High"
            else -> "Extreme"
        }
    }

    /**
     * 风向角度 → 方位文字
     */
    private fun getWindDirectionText(degrees: Double): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }

    /**
     * 从 ISO 日期时间字符串提取时间 (如 "2026-01-07T07:52" -> "07:52")
     */
    private fun extractTime(dateTimeStr: String): String {
        return try {
            dateTimeStr.substringAfter("T").take(5)
        } catch (e: Exception) {
            "00:00"
        }
    }

    /**
     * 从日期字符串提取星期几 (如 "2026-01-07" -> "Wed")
     */
    private fun extractDayOfWeek(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateStr)
            val dayFormat = SimpleDateFormat("EEE", Locale.US)
            dayFormat.format(date ?: Date())
        } catch (e: Exception) {
            "---"
        }
    }
}
