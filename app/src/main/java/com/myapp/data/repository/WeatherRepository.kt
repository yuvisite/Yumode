package com.myapp.data.repository

import com.myapp.data.cache.DiskCacheStore
import com.myapp.data.cache.searchResultsFromJson
import com.myapp.data.cache.searchResultsToJson
import com.myapp.data.cache.weatherFromJson
import com.myapp.data.cache.weatherToJson
import com.myapp.data.network.SimpleNetworkClient
import com.myapp.model.CitySearchResult
import com.myapp.model.DailyForecast
import com.myapp.model.SavedCity
import com.myapp.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WeatherRepository(
    private val cacheStore: DiskCacheStore? = null,
) {
    suspend fun searchCities(query: String): List<CitySearchResult> =
        withContext(Dispatchers.IO) {
            val cacheKey = "weather-search-${query.trim().lowercase()}"
            cacheStore?.readFresh(cacheKey, SEARCH_CACHE_TTL_MS)
                ?.let(::searchResultsFromJson)
                ?.let { return@withContext it }

            runCatching {
                val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                val response = SimpleNetworkClient.get(
                    "https://geocoding-api.open-meteo.com/v1/search?name=$encodedQuery&count=5&language=en",
                )
                parseSearchResults(response).also { results ->
                    cacheStore?.write(cacheKey, searchResultsToJson(results))
                }
            }.getOrElse { throwable ->
                cacheStore?.readAny(cacheKey)
                    ?.let(::searchResultsFromJson)
                    ?: throw throwable
            }
        }

    suspend fun fetchWeather(city: SavedCity): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            val cacheKey = "weather-${city.latitude}-${city.longitude}"

            cacheStore?.readFresh(cacheKey, WEATHER_CACHE_TTL_MS)
                ?.let(::weatherFromJson)
                ?.let { return@withContext it }

            runCatching {
                val response = SimpleNetworkClient.get(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=${city.latitude}" +
                        "&longitude=${city.longitude}" +
                        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                        "&timezone=auto" +
                        "&forecast_days=3",
                )
                parseWeather(city, response).also { snapshot ->
                    cacheStore?.write(cacheKey, weatherToJson(snapshot))
                }
            }.getOrElse { throwable ->
                cacheStore?.readAny(cacheKey)
                    ?.let(::weatherFromJson)
                    ?: throw throwable
            }
        }

    private fun parseSearchResults(response: String): List<CitySearchResult> {
        val json = JSONObject(response)
        val results = json.optJSONArray("results") ?: return emptyList()

        return buildList {
            for (index in 0 until results.length()) {
                val item = results.getJSONObject(index)
                add(
                    CitySearchResult(
                        name = item.getString("name"),
                        admin1 = item.optString("admin1").ifBlank { null },
                        countryCode = item.optString("country_code").ifBlank { "--" },
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude"),
                        timezone = item.optString("timezone").ifBlank { "auto" },
                    ),
                )
            }
        }
    }

    private fun parseWeather(
        city: SavedCity,
        response: String,
    ): WeatherSnapshot {
        val json = JSONObject(response)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")

        val dates = daily.getJSONArray("time")
        val weatherCodes = daily.getJSONArray("weather_code")
        val maxTemperatures = daily.getJSONArray("temperature_2m_max")
        val minTemperatures = daily.getJSONArray("temperature_2m_min")

        val forecast = buildList {
            val size = minOf(
                dates.length(),
                weatherCodes.length(),
                maxTemperatures.length(),
                minTemperatures.length(),
            )

            for (index in 0 until size) {
                add(
                    DailyForecast(
                        date = dates.getString(index),
                        minTemperature = minTemperatures.getDouble(index),
                        maxTemperature = maxTemperatures.getDouble(index),
                        weatherCode = weatherCodes.getDouble(index).toInt(),
                    ),
                )
            }
        }

        return WeatherSnapshot(
            city = city,
            currentTime = current.getString("time"),
            temperature = current.getDouble("temperature_2m"),
            apparentTemperature = current.getDouble("apparent_temperature"),
            windSpeed = current.getDouble("wind_speed_10m"),
            weatherCode = current.getDouble("weather_code").toInt(),
            timezone = json.optString("timezone").ifBlank { city.timezone },
            forecast = forecast,
        )
    }

    private companion object {
        const val SEARCH_CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        const val WEATHER_CACHE_TTL_MS = 15L * 60L * 1000L
    }
}
