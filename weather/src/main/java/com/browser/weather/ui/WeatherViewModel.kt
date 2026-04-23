package com.browser.weather.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.browser.weather.data.WeatherData
import com.browser.weather.data.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 天气 UI 状态
 */
sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val data: WeatherData) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

/**
 * 城市搜索状态
 */
sealed class CitySearchState {
    object Idle : CitySearchState()
    object Searching : CitySearchState()
    data class Results(val cities: List<CitySearchItem>) : CitySearchState()
    data class Error(val message: String) : CitySearchState()
}

/**
 * 天气 ViewModel
 */
class WeatherViewModel(context: Context) : ViewModel() {

    private val repository = WeatherRepository(context)

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _citySearchState = MutableStateFlow<CitySearchState>(CitySearchState.Idle)
    val citySearchState: StateFlow<CitySearchState> = _citySearchState.asStateFlow()

    private val _isLoadingCity = MutableStateFlow(false)
    val isLoadingCity: StateFlow<Boolean> = _isLoadingCity.asStateFlow()

    init {
        loadWeather()
    }

    /**
     * 加载天气数据（通过 IP 自动定位）
     */
    fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            
            repository.getWeatherByIp()
                .onSuccess { data ->
                    _uiState.value = WeatherUiState.Success(data)
                }
                .onFailure { error ->
                    _uiState.value = WeatherUiState.Error(error.message ?: "Unknown error")
                }
        }
    }

    /**
     * 搜索城市
     */
    fun searchCity(query: String) {
        if (query.isBlank()) {
            _citySearchState.value = CitySearchState.Idle
            return
        }
        
        viewModelScope.launch {
            _citySearchState.value = CitySearchState.Searching
            
            repository.searchCity(query)
                .onSuccess { cities ->
                    val items = cities.map { city ->
                        CitySearchItem(
                            key = "${city.id ?: 0}",
                            name = city.name ?: "Unknown",
                            latitude = city.latitude ?: 0.0,
                            longitude = city.longitude ?: 0.0,
                            country = city.country,
                            administrativeArea = city.admin1
                        )
                    }
                    _citySearchState.value = CitySearchState.Results(items)
                }
                .onFailure { error ->
                    _citySearchState.value = CitySearchState.Error(error.message ?: "Search failed")
                }
        }
    }

    /**
     * 选择城市并加载天气
     */
    fun selectCity(city: CitySearchItem) {
        if (city.key.isBlank()) return
        
        viewModelScope.launch {
            _isLoadingCity.value = true
            
            repository.getWeatherByLatLon(city.latitude, city.longitude, city.name)
                .onSuccess { data ->
                    _uiState.value = WeatherUiState.Success(data)
                }
                .onFailure { error ->
                    _uiState.value = WeatherUiState.Error(error.message ?: "Failed to load weather")
                }
            
            _isLoadingCity.value = false
        }
    }

    /**
     * 重置城市搜索状态
     */
    fun resetCitySearch() {
        _citySearchState.value = CitySearchState.Idle
    }

    /**
     * 刷新天气数据
     */
    fun refresh() {
        loadWeather()
    }

    /**
     * ViewModel Factory
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
                return WeatherViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
