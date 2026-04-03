package com.myapp.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.myapp.data.repository.CurrencyRepository
import com.myapp.data.repository.WeatherRepository
import com.myapp.model.CitySearchResult
import com.myapp.model.CurrencyRate
import com.myapp.model.SavedCity
import com.myapp.model.WeatherSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ViewModel for weather and currency data in the portal.
 * Separates data fetching and state management from UI concerns.
 * 
 * TODO: This is a foundation for refactoring away from LegacyPortalController.
 * Progressively move state management and business logic from the controller to this class.
 */
class PortalDataViewModel(
    private val weatherRepository: WeatherRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Weather state
    private val _weatherState = MutableLiveData<WeatherUiState>(WeatherUiState.Idle)
    val weatherState: LiveData<WeatherUiState> = _weatherState

    // Currency state
    private val _currencyState = MutableLiveData<CurrencyUiState>(CurrencyUiState.Idle)
    val currencyState: LiveData<CurrencyUiState> = _currencyState


    fun fetchWeather(city: SavedCity) {
        vmScope.launch {
            _weatherState.value = WeatherUiState.Loading
            try {
                val weather = weatherRepository.fetchWeather(city)
                _weatherState.value = WeatherUiState.Success(weather)
            } catch (e: Exception) {
                _weatherState.value = WeatherUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun searchCities(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _weatherState.value = WeatherUiState.SearchError("enter at least 2 chars")
            return
        }

        vmScope.launch {
            _weatherState.value = WeatherUiState.SearchLoading
            try {
                val results = weatherRepository.searchCities(trimmed)
                _weatherState.value = WeatherUiState.SearchSuccess(results)
            } catch (e: Exception) {
                _weatherState.value = WeatherUiState.SearchError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Fetch currency rates.
     */
    fun fetchCurrencyRates() {
        vmScope.launch {
            _currencyState.value = CurrencyUiState.Loading
            try {
                val rates = currencyRepository.fetchRates()
                _currencyState.value = CurrencyUiState.Success(rates)
            } catch (e: Exception) {
                _currencyState.value = CurrencyUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    sealed class WeatherUiState {
        object Idle : WeatherUiState()
        object Loading : WeatherUiState()
        data class Success(val weather: WeatherSnapshot) : WeatherUiState()
        data class Error(val message: String) : WeatherUiState()
        object SearchLoading : WeatherUiState()
        data class SearchSuccess(val results: List<CitySearchResult>) : WeatherUiState()
        data class SearchError(val message: String) : WeatherUiState()
    }

    sealed class CurrencyUiState {
        object Idle : CurrencyUiState()
        object Loading : CurrencyUiState()
        data class Success(val rates: List<CurrencyRate>) : CurrencyUiState()
        data class Error(val message: String) : CurrencyUiState()
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}
