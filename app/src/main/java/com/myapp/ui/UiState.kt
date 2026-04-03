package com.myapp.ui

import com.myapp.model.CitySearchResult
import com.myapp.model.CurrencyRate
import com.myapp.model.FeedItem
import com.myapp.model.SavedCity
import com.myapp.model.SanitizedArticle
import com.myapp.model.WeatherSnapshot

internal data class RatesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rates: List<CurrencyRate> = emptyList(),
)

internal data class WeatherUiState(
    val isLoadingWeather: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null,
    val searchError: String? = null,
    val searchResults: List<CitySearchResult> = emptyList(),
    val data: WeatherSnapshot? = null,
)

internal data class FeedUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val items: List<FeedItem> = emptyList(),
)

internal data class FeedPagerState(
    val pageIndex: Int = 0,
    val selectedSlot: Int = 0,
)

internal data class ArticleUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val article: SanitizedArticle? = null,
)

internal val quickCities = listOf(
    SavedCity("Kyiv", "Kyiv City", "UA", 50.4501, 30.5234, "Europe/Kyiv"),
    SavedCity("Lviv", "Lviv", "UA", 49.8397, 24.0297, "Europe/Kyiv"),
    SavedCity("Odesa", "Odesa", "UA", 46.4825, 30.7233, "Europe/Kyiv"),
    SavedCity("Kharkiv", "Kharkiv", "UA", 49.9935, 36.2304, "Europe/Kyiv"),
    SavedCity("Dnipro", "Dnipro", "UA", 48.4647, 35.0462, "Europe/Kyiv"),
)

internal const val FeedPageSize = 8

internal fun FeedPagerState.normalizeFor(itemCount: Int): FeedPagerState {
    if (itemCount <= 0) {
        return FeedPagerState()
    }

    val maxPageIndex = ((itemCount - 1) / FeedPageSize).coerceAtLeast(0)
    val normalizedPage = pageIndex.coerceIn(0, maxPageIndex)
    val visibleCount = (itemCount - normalizedPage * FeedPageSize).coerceAtMost(FeedPageSize)
    val normalizedSlot = selectedSlot.coerceIn(0, (visibleCount - 1).coerceAtLeast(0))

    return copy(
        pageIndex = normalizedPage,
        selectedSlot = normalizedSlot,
    )
}
