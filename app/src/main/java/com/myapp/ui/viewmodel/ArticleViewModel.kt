package com.myapp.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.myapp.data.cache.HistoryEntry
import com.myapp.data.cache.SavedArticle
import com.myapp.data.cache.SavedArticleStore
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import com.myapp.data.repository.RssRepository
import com.myapp.ui.ArticleUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ArticleViewModel(
    private val rssRepository: RssRepository,
    private val savedArticleStore: SavedArticleStore,
) : ViewModel() {
    data class PreparedArticle(
        val siteId: String,
        val feedItem: FeedItem,
        val state: ArticleUiState,
    )

    data class ArticleScreenState(
        val articleState: ArticleUiState = ArticleUiState(),
        val articleStatesByKey: Map<String, ArticleUiState> = emptyMap(),
        val articleScrollPositions: Map<String, Int> = emptyMap(),
    )

    private val _uiState = MutableLiveData(ArticleScreenState())
    val uiStateLiveData: LiveData<ArticleScreenState> = _uiState

    private val state: ArticleScreenState
        get() = _uiState.value ?: ArticleScreenState()

    fun updateUiState(transform: (ArticleScreenState) -> ArticleScreenState) {
        _uiState.value = transform(state)
    }

    fun setArticleState(value: ArticleUiState) {
        _uiState.value = state.copy(articleState = value)
    }

    fun setArticleStatesByKey(value: Map<String, ArticleUiState>) {
        _uiState.value = state.copy(articleStatesByKey = value)
    }

    fun setArticleScrollPositions(value: Map<String, Int>) {
        _uiState.value = state.copy(articleScrollPositions = value)
    }

    fun rememberScroll(key: String, scrollY: Int) {
        updateUiState { current ->
            current.copy(articleScrollPositions = current.articleScrollPositions + (key to scrollY))
        }
    }

    fun rememberScrollFor(siteId: String, feedItem: FeedItem, scrollY: Int) {
        rememberScroll(buildCacheKey(siteId, feedItem), scrollY)
    }

    fun cacheState(key: String, articleState: ArticleUiState) {
        updateUiState { current ->
            current.copy(articleStatesByKey = current.articleStatesByKey + (key to articleState))
        }
    }

    fun cacheStateFor(siteId: String, feedItem: FeedItem, articleState: ArticleUiState) {
        cacheState(buildCacheKey(siteId, feedItem), articleState)
    }

    fun setCurrentAndCache(key: String, articleState: ArticleUiState) {
        updateUiState { current ->
            current.copy(
                articleState = articleState,
                articleStatesByKey = current.articleStatesByKey + (key to articleState),
            )
        }
    }

    fun setCurrentAndCacheFor(siteId: String, feedItem: FeedItem, articleState: ArticleUiState) {
        setCurrentAndCache(buildCacheKey(siteId, feedItem), articleState)
    }

    fun restoreCachedOrSaved(siteId: String, feedItem: FeedItem): ArticleUiState? {
        val key = buildCacheKey(siteId, feedItem)
        state.articleStatesByKey[key]?.let { return it }

        val saved = savedArticleStore.get(feedItem.url) ?: return null
        val restored = ArticleUiState(isLoading = false, error = null, article = saved.article)
        cacheState(key, restored)
        return restored
    }

    fun prepareSavedArticle(saved: SavedArticle): PreparedArticle {
        val url = saved.article.finalUrl.ifBlank { saved.article.sourceUrl }
        val feedItem =
            FeedItem(
                id = url.ifBlank { saved.article.sourceUrl },
                title = saved.article.title,
                url = url,
                publishedAt = saved.article.publishedAt,
                summary = saved.article.excerpt,
            )
        val loadedState = ArticleUiState(isLoading = false, error = null, article = saved.article)
        return PreparedArticle(
            siteId = saved.siteId,
            feedItem = feedItem,
            state = loadedState,
        )
    }

    fun prepareHistoryArticle(entry: HistoryEntry): Pair<String, FeedItem>? {
        if (entry.kind != "article") {
            return null
        }
        if (entry.siteId.isBlank()) {
            return null
        }
        val feedItem =
            FeedItem(
                id = entry.url.ifBlank { entry.title },
                title = entry.title,
                url = entry.url,
                publishedAt = null,
                summary = null,
            )
        return entry.siteId to feedItem
    }

    suspend fun fetchArticleState(
        site: PortalSite,
        feedItem: FeedItem,
        errorMapper: (Throwable) -> String,
    ): ArticleUiState =
        withContext(Dispatchers.IO) {
            runCatching { rssRepository.fetchArticle(site, feedItem) }
                .fold(
                    onSuccess = { article ->
                        ArticleUiState(isLoading = false, error = null, article = article)
                    },
                    onFailure = { throwable ->
                        ArticleUiState(
                            isLoading = false,
                            error = errorMapper(throwable),
                            article = null,
                        )
                    },
                )
        }

    fun buildCacheKey(siteId: String, feedItem: FeedItem): String =
        "$siteId|${feedItem.id.ifBlank { feedItem.url }}"
}
