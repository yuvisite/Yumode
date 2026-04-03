package com.myapp.ui

import com.myapp.data.repository.RssRepository
import com.myapp.model.PortalSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PortalRssFeedStore(
    private val rssRepository: RssRepository,
    private val uiScope: CoroutineScope,
    private val onStateChanged: () -> Unit,
    private val errorMapper: (Throwable) -> String,
) {
    var feedStates: Map<String, FeedUiState> = emptyMap()
    var feedPagerStates: Map<String, FeedPagerState> = emptyMap()
    var feedCategorySelections: Map<String, String> = emptyMap()
    var feedTagSelections: Map<String, String> = emptyMap()
    var feedSearchQueries: Map<String, String> = emptyMap()

    fun updateFeedState(
        siteId: String,
        transform: (FeedUiState) -> FeedUiState,
    ) {
        val current = feedStates[siteId] ?: FeedUiState()
        feedStates = feedStates + (siteId to transform(current))
    }

    fun refreshFeed(site: PortalSite) {
        val current = feedStates[site.id] ?: FeedUiState()
        if (current.isLoading) {
            return
        }

        updateFeedState(site.id) { it.copy(isLoading = true, error = null) }
        onStateChanged()

        uiScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { rssRepository.fetchFeed(site) }
            }
                .onSuccess { items ->
                    updateFeedState(site.id) { it.copy(isLoading = false, error = null, items = items) }
                }
                .onFailure { throwable ->
                    updateFeedState(site.id) { it.copy(isLoading = false, error = errorMapper(throwable)) }
                }
            onStateChanged()
        }
    }
}
