package com.browser.weather.data

import android.content.Context

/**
 * 天气数据仓库
 * 封装 API 调用，提供简洁接口给 ViewModel
 */
class WeatherRepository(context: Context) {

    private val apiClient = WeatherApiClient(context)

    /**
     * 通过 IP 获取当前位置天气
     */
    suspend fun getWeatherByIp(): Result<WeatherData> {
        return apiClient.getWeatherByIp()
    }

    /**
     * 通过经纬度和城市名获取天气
     */
    suspend fun getWeatherByLatLon(lat: Double, lon: Double, cityName: String): Result<WeatherData> {
        return apiClient.getWeatherByLatLon(lat, lon, cityName)
    }

    /**
     * 搜索城市
     */
    suspend fun searchCity(query: String): Result<List<GeocodingResult>> {
        return apiClient.searchCity(query)
    }
}
