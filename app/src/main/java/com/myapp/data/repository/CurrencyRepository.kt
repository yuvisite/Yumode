package com.myapp.data.repository

import com.myapp.data.cache.DiskCacheStore
import com.myapp.data.cache.ratesFromJson
import com.myapp.data.cache.ratesToJson
import com.myapp.data.network.SimpleNetworkClient
import com.myapp.model.CurrencyRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class CurrencyRepository(
    private val cacheStore: DiskCacheStore? = null,
) {
    private val majorCurrencyCodes = listOf(
        "USD",
        "EUR",
        "PLN",
        "GBP",
        "CHF",
        "CZK",
        "CAD",
        "JPY",
        "CNY",
    )

    suspend fun fetchRates(): List<CurrencyRate> =
        withContext(Dispatchers.IO) {
            val cacheKey = "api-rates-major"

            cacheStore?.readFresh(cacheKey, RATES_CACHE_TTL_MS)
                ?.let(::ratesFromJson)
                ?.takeIf { it.isNotEmpty() }
                ?.let { return@withContext it }

            runCatching {
                val response = SimpleNetworkClient.get(
                    "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json",
                )
                parseRates(response).also { rates ->
                    cacheStore?.write(cacheKey, ratesToJson(rates))
                }
            }.getOrElse { throwable ->
                cacheStore?.readAny(cacheKey)
                    ?.let(::ratesFromJson)
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw throwable
            }
        }

    private fun parseRates(response: String): List<CurrencyRate> {
        val jsonArray = JSONArray(response)
        val allowedCodes = majorCurrencyCodes.toSet()
        val priority = majorCurrencyCodes.withIndex().associate { it.value to it.index }

        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                val code = item.getString("cc")
                if (code !in allowedCodes) {
                    continue
                }

                add(
                    CurrencyRate(
                        code = code,
                        name = item.getString("txt"),
                        rate = item.getDouble("rate"),
                        exchangeDate = item.getString("exchangedate"),
                    ),
                )
            }
        }.sortedBy { priority[it.code] ?: Int.MAX_VALUE }
    }

    private companion object {
        const val RATES_CACHE_TTL_MS = 30L * 60L * 1000L
    }
}
