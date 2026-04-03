package com.myapp.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.myapp.data.repository.RssRepository
import com.myapp.model.PortalSite
import com.myapp.ui.FeedUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RssViewModel(
    private val rssRepository: RssRepository,
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    data class RssUiState(
        val feedStates: Map<String, FeedUiState> = emptyMap(),
        val feedPagerStates: Map<String, com.myapp.ui.FeedPagerState> = emptyMap(),
        val feedCategorySelections: Map<String, String> = emptyMap(),
        val feedTagSelections: Map<String, String> = emptyMap(),
        val feedSearchQueries: Map<String, String> = emptyMap(),
    )

    private val _uiState = MutableLiveData(RssUiState())
    val uiStateLiveData: LiveData<RssUiState> = _uiState

    private val state: RssUiState
        get() = _uiState.value ?: RssUiState()

    val feedStates: Map<String, FeedUiState>
        get() = state.feedStates

    fun setFeedPagerStates(value: Map<String, com.myapp.ui.FeedPagerState>) {
        updateState { it.copy(feedPagerStates = value) }
    }

    fun setFeedCategorySelections(value: Map<String, String>) {
        updateState { it.copy(feedCategorySelections = value) }
    }

    fun setFeedTagSelections(value: Map<String, String>) {
        updateState { it.copy(feedTagSelections = value) }
    }

    fun setFeedSearchQueries(value: Map<String, String>) {
        updateState { it.copy(feedSearchQueries = value) }
    }

    private fun updateState(transform: (RssUiState) -> RssUiState) {
        _uiState.value = transform(state)
    }

    fun updateUiState(transform: (RssUiState) -> RssUiState) {
        updateState(transform)
    }

    fun updateFeedState(
        siteId: String,
        transform: (FeedUiState) -> FeedUiState,
    ) {
        val current = feedStates[siteId] ?: FeedUiState()
        updateState { currentState ->
            currentState.copy(feedStates = currentState.feedStates + (siteId to transform(current)))
        }
    }

    fun refreshFeed(site: PortalSite) {
        val current = feedStates[site.id] ?: FeedUiState()
        if (current.isLoading) {
            return
        }

        updateFeedState(site.id) { it.copy(isLoading = true, error = null) }

        vmScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { rssRepository.fetchFeed(site) }
            }
                .onSuccess { items ->
                    updateFeedState(site.id) { it.copy(isLoading = false, error = null, items = items) }
                }
                .onFailure { throwable ->
                    updateFeedState(site.id) {
                        it.copy(isLoading = false, error = throwable.message ?: "Failed to load feed")
                    }
                }
        }
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}
