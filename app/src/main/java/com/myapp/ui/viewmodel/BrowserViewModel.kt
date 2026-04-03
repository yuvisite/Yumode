package com.myapp.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.myapp.data.browser.BrowserPageManager

/**
 * ViewModel for i-mode browser state management.
 * Handles page loading, navigation state, and cache management.
 * 
 * TODO: Migrate browser state from LegacyPortalController to this class.
 */
class BrowserViewModel(
    private val browserPageManager: BrowserPageManager,
) : ViewModel() {

    // Current page state
    private val _pageState = MutableLiveData<PageLoadUiState>(PageLoadUiState.Idle)
    val pageState: LiveData<PageLoadUiState> = _pageState

    // Navigation state
    private val _navigationState = MutableLiveData<NavigationState>()
    val navigationState: LiveData<NavigationState> = _navigationState

    /**
     * Load a page from a link.
     */
    suspend fun loadPage(url: String, html: String, noCache: Boolean = false) {
        _pageState.value = PageLoadUiState.Loading
        try {
            val result = browserPageManager.loadPageFromLink(url, html, noCache) { progress ->
                _pageState.value = PageLoadUiState.Progress(progress)
            }

            if (result.success) {
                _pageState.value = PageLoadUiState.Success(
                    html = result.html,
                    url = result.url,
                    loadTimeMs = result.loadTimeMs,
                    wasFromCache = result.wasFromCache
                )
                updateNavigationState()
            } else {
                _pageState.value = PageLoadUiState.Error(result.errorMessage ?: "Unknown error")
            }
        } catch (e: Exception) {
            _pageState.value = PageLoadUiState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Navigate back.
     */
    fun goBack() {
        val result = browserPageManager.goBack()
        if (result != null) {
            _pageState.value = PageLoadUiState.Success(
                html = result.html,
                url = result.url,
                loadTimeMs = result.loadTimeMs,
                wasFromCache = result.wasFromCache
            )
            updateNavigationState()
        }
    }

    /**
     * Navigate forward.
     */
    fun goForward() {
        val result = browserPageManager.goForward()
        if (result != null) {
            _pageState.value = PageLoadUiState.Success(
                html = result.html,
                url = result.url,
                loadTimeMs = result.loadTimeMs,
                wasFromCache = result.wasFromCache
            )
            updateNavigationState()
        }
    }

    /**
     * Check if back navigation is available.
     */
    fun canGoBack(): Boolean = browserPageManager.canGoBack()

    /**
     * Check if forward navigation is available.
     */
    fun canGoForward(): Boolean = browserPageManager.canGoForward()

    /**
     * Reset the browser state.
     */
    fun reset() {
        browserPageManager.reset()
        _pageState.value = PageLoadUiState.Idle
        _navigationState.value = NavigationState(backEnabled = false, forwardEnabled = false)
    }

    private fun updateNavigationState() {
        _navigationState.value = NavigationState(
            backEnabled = browserPageManager.canGoBack(),
            forwardEnabled = browserPageManager.canGoForward()
        )
    }

    sealed class PageLoadUiState {
        object Idle : PageLoadUiState()
        object Loading : PageLoadUiState()
        data class Progress(val percent: Int) : PageLoadUiState()
        data class Success(
            val html: String,
            val url: String,
            val loadTimeMs: Long,
            val wasFromCache: Boolean
        ) : PageLoadUiState()
        data class Error(val message: String) : PageLoadUiState()
    }

    data class NavigationState(
        val backEnabled: Boolean,
        val forwardEnabled: Boolean
    )
}
