package com.myapp.model

data class CurrencyRate(
    val code: String,
    val name: String,
    val rate: Double,
    val exchangeDate: String,
)

data class SavedCity(
    val name: String,
    val admin1: String?,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
)

data class CitySearchResult(
    val name: String,
    val admin1: String?,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    fun toSavedCity(): SavedCity =
        SavedCity(
            name = name,
            admin1 = admin1,
            countryCode = countryCode,
            latitude = latitude,
            longitude = longitude,
            timezone = timezone,
        )
}

data class DailyForecast(
    val date: String,
    val minTemperature: Double,
    val maxTemperature: Double,
    val weatherCode: Int,
)

data class WeatherSnapshot(
    val city: SavedCity,
    val currentTime: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val windSpeed: Double,
    val weatherCode: Int,
    val timezone: String,
    val forecast: List<DailyForecast>,
)

fun SavedCity.label(): String =
    listOfNotNull(name, admin1, countryCode)
        .distinct()
        .joinToString(", ")

fun CitySearchResult.label(): String =
    listOfNotNull(name, admin1, countryCode)
        .distinct()
        .joinToString(", ")
