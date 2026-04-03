package com.myapp.data.browser

import android.util.Log
import kotlinx.coroutines.CoroutineScope

/**
 * Менеджер загрузки и навигации страниц для i-mode браузера.
 * Интегрирует BrowserSimulator, PageCache и PageLoadingSimulator.
 */
class BrowserPageManager(
    private val networkSpeedBps: Int = PageLoadingSimulator.SPEED_EARLY_IMODE,
    @Suppress("UNUSED_PARAMETER") private val coroutineScope: CoroutineScope? = null
) {
    private val cache = PageCache()
    private val browser = BrowserSimulator(cache)
    private val loader = PageLoadingSimulator(networkSpeedBps)
    private val tag = "BrowserPageManager"

    private fun logDebug(message: String) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message)
        }
    }

    /**
     * Результат загрузки страницы с полной информацией.
     */
    data class PageLoadResult(
        val success: Boolean,
        val url: String,
        val html: String = "",
        val loadTimeMs: Long = 0L,
        val wasFromCache: Boolean = false,
        val isNoCache: Boolean = false,
        val errorMessage: String? = null,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false
    )

    /**
     * Загрузить страницу по ссылке (новая страница, очищает forward stack).
     */
    suspend fun loadPageFromLink(
        url: String,
        htmlContent: String,
        isNoCache: Boolean = false,
        onProgress: suspend (progress: Int) -> Unit = {}
    ): PageLoadResult {
        return try {
            logDebug("Loading page from link: $url (noCache=$isNoCache)")
            
            // Проверяем кэш
            val cachedHtml = cache.get(url)
            
            val loadResult = if (cachedHtml != null) {
                // Загружаем из кэша (мгновенно)
                logDebug("Page found in cache: $url")
                loader.loadFromCache(url, cachedHtml)
            } else {
                // Загружаем с задержкой
                logDebug("Loading page from network: $url (${htmlContent.length} bytes)")
                loader.loadPageWithDelay(url, { htmlContent }, onProgress)
            }

            if (!loadResult.success) {
                return PageLoadResult(
                    success = false,
                    url = url,
                    errorMessage = loadResult.errorMessage,
                    canGoBack = browser.canGoBack(),
                    canGoForward = browser.canGoForward()
                )
            }

            // Навигируем в браузербе симуляторе
            val pageData = browser.navigateToLink(url, loadResult.html, isNoCache)
            
            val stats = browser.getNavigationState()
            logDebug("Navigation state: back=${stats.backStackSize}, forward=${stats.forwardStackSize}")

            PageLoadResult(
                success = true,
                url = url,
                html = pageData.html,
                loadTimeMs = pageData.loadTimeMs,
                wasFromCache = loadResult.wasFromCache,
                isNoCache = isNoCache,
                canGoBack = browser.canGoBack(),
                canGoForward = browser.canGoForward()
            )
        } catch (e: Exception) {
            Log.e(tag, "Error loading page: $url", e)
            PageLoadResult(
                success = false,
                url = url,
                errorMessage = e.message,
                canGoBack = browser.canGoBack(),
                canGoForward = browser.canGoForward()
            )
        }
    }

    /**
     * Зарегистрировать уже загруженную страницу без дополнительной искусственной задержки.
     */
    fun registerLoadedPage(
        url: String,
        htmlContent: String,
        isNoCache: Boolean = false
    ): PageLoadResult {
        return try {
            logDebug("Registering loaded page: $url (noCache=$isNoCache)")
            val wasFromCache = cache.contains(url)
            val pageData = browser.navigateToLink(url, htmlContent, isNoCache)

            val stats = browser.getNavigationState()
            logDebug("Navigation state: back=${stats.backStackSize}, forward=${stats.forwardStackSize}")

            PageLoadResult(
                success = true,
                url = url,
                html = pageData.html,
                loadTimeMs = 0L,
                wasFromCache = wasFromCache,
                isNoCache = isNoCache,
                canGoBack = browser.canGoBack(),
                canGoForward = browser.canGoForward()
            )
        } catch (e: Exception) {
            Log.e(tag, "Error registering page: $url", e)
            PageLoadResult(
                success = false,
                url = url,
                errorMessage = e.message,
                canGoBack = browser.canGoBack(),
                canGoForward = browser.canGoForward()
            )
        }
    }

    /**
     * Перейти на предыдущую страницу (кнопка "назад").
     */
    fun goBack(): PageLoadResult? {
        val pageData = browser.goBack() ?: return null
        
        logDebug("Going back to: ${pageData.url}")
        val stats = browser.getNavigationState()
        logDebug("Navigation state: back=${stats.backStackSize}, forward=${stats.forwardStackSize}")

        return PageLoadResult(
            success = true,
            url = pageData.url,
            html = pageData.html,
            loadTimeMs = 0L, // Из кэша - мгновенно
            wasFromCache = true,
            canGoBack = browser.canGoBack(),
            canGoForward = browser.canGoForward()
        )
    }

    /**
     * Перейти на следующую страницу (кнопка "вперед").
     */
    fun goForward(): PageLoadResult? {
        val pageData = browser.goForward() ?: return null
        
        logDebug("Going forward to: ${pageData.url}")
        val stats = browser.getNavigationState()
        logDebug("Navigation state: back=${stats.backStackSize}, forward=${stats.forwardStackSize}")

        return PageLoadResult(
            success = true,
            url = pageData.url,
            html = pageData.html,
            loadTimeMs = 0L, // Из кэша - мгновенно
            wasFromCache = true,
            canGoBack = browser.canGoBack(),
            canGoForward = browser.canGoForward()
        )
    }

    /**
     * Проверить, доступна ли кнопка "назад".
     */
    fun canGoBack(): Boolean = browser.canGoBack()

    /**
     * Проверить, доступна ли кнопка "вперед".
     */
    fun canGoForward(): Boolean = browser.canGoForward()

    /**
     * Получить текущую страницу.
     */
    fun getCurrentPage(): BrowserSimulator.PageData? = browser.getCurrentPage()

    /**
     * Очистить браузер (выключение телефона).
     */
    fun reset() {
        logDebug("Resetting browser (power off)")
        browser.reset()
    }

    /**
     * Проверить, есть ли страница в RAM-кэше.
     */
    fun isPageCached(url: String): Boolean = cache.contains(url)

    /**
     * Получить информацию о сети.
     */
    fun getNetworkInfo(): PageLoadingSimulator.NetworkInfo = loader.getNetworkInfo()

    /**
     * Получить статистику навигации.
     */
    fun getNavigationState(): BrowserSimulator.NavigationState = browser.getNavigationState()

    /**
     * Получить статистику кэша.
     */
    fun getCacheStats(): PageCache.CacheStats = cache.getStats()

    /**
     * Изменить скорость сети.
     */
    fun setNetworkSpeed(speedBps: Int) {
        logDebug("Network speed changed: $speedBps bps -> ${loader.getNetworkInfo().networkType}")
    }

    /**
     * Логировать текущее состояние для отладки.
     */
    fun logDebugState() {
        if (!Log.isLoggable(tag, Log.DEBUG)) return
        
        val navState = browser.getNavigationState()
        val cacheStats = cache.getStats()
        val netInfo = loader.getNetworkInfo()
        
        Log.d(tag, """
            ========== Browser State ==========
            Current URL: ${navState.currentPageUrl}
            Back stack: ${navState.backStackSize} pages
            Forward stack: ${navState.forwardStackSize} pages
            
            ========== Cache State ==========
            Pages in cache: ${cacheStats.totalPages}
            Used: ${cacheStats.usedSizeBytes} / ${cacheStats.maxSizeBytes} bytes
            Usage: ${(cacheStats.usedSizeBytes * 100) / cacheStats.maxSizeBytes}%
            
            ========== Network ==========
            Type: ${netInfo.networkType}
            Speed: ${netInfo.speedBps} bps (${netInfo.speedKbps} kbps)
            Typical page load: ${netInfo.typicalLoadTimeMs}ms
            ===================================
        """.trimIndent())
    }
}
