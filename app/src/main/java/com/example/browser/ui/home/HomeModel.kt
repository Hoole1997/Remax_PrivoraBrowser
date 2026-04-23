package com.example.browser.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.browser.weather.data.WeatherData
import com.browser.weather.data.WeatherRepository
import com.example.browser.base.BaseModel
import com.example.browser.data.website.QuickWebsiteRepository
import kotlinx.coroutines.launch

class HomeModel(
    private val repository: QuickWebsiteRepository,
    private val weatherRepository: WeatherRepository
) : BaseModel() {

    val quickWebsites = repository.observeWebsites()

    private val _weatherData = MutableLiveData<WeatherData?>()
    val weatherData: LiveData<WeatherData?> = _weatherData

    fun loadWeather() {
        viewModelScope.launch {
            weatherRepository.getWeatherByIp()
                .onSuccess {
                    _weatherData.postValue(it)
                }
                .onFailure {
                    // Log error or ignore
                }
        }
    }

    fun removeQuickWebsite(id: Long): Boolean {
        return repository.removeWebsite(id)
    }

    class Factory(
        private val repository: QuickWebsiteRepository,
        private val weatherRepository: WeatherRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeModel::class.java)) {
                return HomeModel(repository, weatherRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
