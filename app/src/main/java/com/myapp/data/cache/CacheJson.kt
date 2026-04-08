package com.myapp.data.cache

import com.myapp.model.ArticleBlock
import com.myapp.model.ArticleBlockType
import com.myapp.model.CitySearchResult
import com.myapp.model.CurrencyRate
import com.myapp.model.DailyForecast
import com.myapp.model.FeedItem
import com.myapp.model.SanitizedArticle
import com.myapp.model.SavedCity
import com.myapp.model.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONObject

internal fun ratesToJson(rates: List<CurrencyRate>): String =
    JSONArray().apply {
        rates.forEach { rate ->
            put(
                JSONObject()
                    .put("code", rate.code)
                    .put("name", rate.name)
                    .put("rate", rate.rate)
                    .put("exchangeDate", rate.exchangeDate),
            )
        }
    }.toString()

internal fun ratesFromJson(json: String): List<CurrencyRate> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                CurrencyRate(
                    code = item.getString("code"),
                    name = item.getString("name"),
                    rate = item.getDouble("rate"),
                    exchangeDate = item.getString("exchangeDate"),
                ),
            )
        }
    }
}

internal fun searchResultsToJson(results: List<CitySearchResult>): String =
    JSONArray().apply {
        results.forEach { city ->
            put(city.toJson())
        }
    }.toString()

internal fun searchResultsFromJson(json: String): List<CitySearchResult> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            add(array.getJSONObject(index).toCitySearchResult())
        }
    }
}

internal fun weatherToJson(snapshot: WeatherSnapshot): String =
    JSONObject()
        .put("city", snapshot.city.toJson())
        .put("currentTime", snapshot.currentTime)
        .put("temperature", snapshot.temperature)
        .put("apparentTemperature", snapshot.apparentTemperature)
        .put("windSpeed", snapshot.windSpeed)
        .put("weatherCode", snapshot.weatherCode)
        .put("timezone", snapshot.timezone)
        .put(
            "forecast",
            JSONArray().apply {
                snapshot.forecast.forEach { item ->
                    put(
                        JSONObject()
                            .put("date", item.date)
                            .put("minTemperature", item.minTemperature)
                            .put("maxTemperature", item.maxTemperature)
                            .put("weatherCode", item.weatherCode),
                    )
                }
            },
        )
        .toString()

internal fun weatherFromJson(json: String): WeatherSnapshot {
    val item = JSONObject(json)
    val forecastJson = item.getJSONArray("forecast")
    val forecast = buildList {
        for (index in 0 until forecastJson.length()) {
            val day = forecastJson.getJSONObject(index)
            add(
                DailyForecast(
                    date = day.getString("date"),
                    minTemperature = day.getDouble("minTemperature"),
                    maxTemperature = day.getDouble("maxTemperature"),
                    weatherCode = day.getInt("weatherCode"),
                ),
            )
        }
    }

    return WeatherSnapshot(
        city = item.getJSONObject("city").toSavedCity(),
        currentTime = item.getString("currentTime"),
        temperature = item.getDouble("temperature"),
        apparentTemperature = item.getDouble("apparentTemperature"),
        windSpeed = item.getDouble("windSpeed"),
        weatherCode = item.getInt("weatherCode"),
        timezone = item.getString("timezone"),
        forecast = forecast,
    )
}

internal fun feedItemsToJson(items: List<FeedItem>): String =
    JSONArray().apply {
        items.forEach { item ->
            put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("publishedAt", item.publishedAt)
                    .put("summary", item.summary)
                    .put("inlineContentHtml", item.inlineContentHtml)
                    .put(
                        "categories",
                        JSONArray().apply {
                            item.categories.forEach(::put)
                        },
                    ),
            )
        }
    }.toString()

internal fun feedItemsFromJson(json: String): List<FeedItem> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                FeedItem(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    url = item.getString("url"),
                    publishedAt = item.optString("publishedAt").ifBlank { null },
                    summary = item.optString("summary").ifBlank { null },
                    inlineContentHtml = item.optString("inlineContentHtml").ifBlank { null },
                    categories = item.optJSONArray("categories")?.let { categories ->
                        buildList {
                            for (categoryIndex in 0 until categories.length()) {
                                add(categories.getString(categoryIndex))
                            }
                        }
                    } ?: emptyList(),
                ),
            )
        }
    }
}

internal fun articleToJson(article: SanitizedArticle): String =
    JSONObject()
        .put("title", article.title)
        .put("sourceUrl", article.sourceUrl)
        .put("finalUrl", article.finalUrl)
        .put("sourceHost", article.sourceHost)
        .put("publishedAt", article.publishedAt)
        .put("excerpt", article.excerpt)
        .put(
            "blocks",
            JSONArray().apply {
                article.blocks.forEach { block ->
                    put(
                        JSONObject()
                            .put("type", block.type.name)
                            .put("text", block.text)
                            .put("imageUrl", block.imageUrl)
                            .put("imageCaption", block.imageCaption),
                    )
                }
            },
        )
        .toString()

internal fun articleFromJson(json: String): SanitizedArticle {
    val item = JSONObject(json)
    val blocksJson = item.getJSONArray("blocks")
    val blocks = buildList {
        for (index in 0 until blocksJson.length()) {
            val block = blocksJson.getJSONObject(index)
            add(
                ArticleBlock(
                    type = enumValueOf(block.getString("type")),
                    text = block.getString("text"),
                    imageUrl = block.optString("imageUrl").ifBlank { null },
                    imageCaption = block.optString("imageCaption").ifBlank { null },
                ),
            )
        }
    }

    return SanitizedArticle(
        title = item.getString("title"),
        sourceUrl = item.getString("sourceUrl"),
        finalUrl = item.getString("finalUrl"),
        sourceHost = item.getString("sourceHost"),
        publishedAt = item.optString("publishedAt").ifBlank { null },
        excerpt = item.optString("excerpt").ifBlank { null },
        blocks = blocks,
    )
}

private fun SavedCity.toJson(): JSONObject =
    JSONObject()
        .put("name", name)
        .put("admin1", admin1)
        .put("countryCode", countryCode)
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("timezone", timezone)

private fun CitySearchResult.toJson(): JSONObject =
    JSONObject()
        .put("name", name)
        .put("admin1", admin1)
        .put("countryCode", countryCode)
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("timezone", timezone)

private fun JSONObject.toSavedCity(): SavedCity =
    SavedCity(
        name = getString("name"),
        admin1 = optString("admin1").ifBlank { null },
        countryCode = getString("countryCode"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        timezone = getString("timezone"),
    )

private fun JSONObject.toCitySearchResult(): CitySearchResult =
    CitySearchResult(
        name = getString("name"),
        admin1 = optString("admin1").ifBlank { null },
        countryCode = getString("countryCode"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        timezone = getString("timezone"),
    )
