package com.myapp.data.browser

/**
 * Симулятор i-mode браузера с навигацией, кэшем и имитацией загрузки.
 * 
 * Структура данных:
 * - backStack: история для кнопки "назад" (LIFO)
 * - currentPage: текущая отображаемая страница
 * - forwardStack: история для кнопки "вперед" (LIFO)
 * - cache: LRU кэш загруженных страниц
 */
class BrowserSimulator(
    private val cache: PageCache = PageCache()
) {
    /**
     * Данные о загруженной странице.
     */
    data class PageData(
        val url: String,
        val html: String,
        val loadTimeMs: Long = 0L, // Время загрузки в миллисекундах
        val wasFromCache: Boolean = false,
        val isNoCache: Boolean = false
    )

    private val backStack = mutableListOf<PageData>()
    private var currentPage: PageData? = null
    private val forwardStack = mutableListOf<PageData>()

    /**
     * Эмулировать переход по ссылке (новая страница).
     * 
     * Правила:
     * - forwardStack полностью очищается
     * - currentPage пушится в backStack
     * - новая страница становится currentPage
     * - кэш проверяется и обновляется
     */
    fun navigateToLink(url: String, htmlContent: String, isNoCache: Boolean = false): PageData {
        // Сохраняем текущую страницу в историю назад
        currentPage?.let { backStack.add(it) }

        // Очищаем историю вперед (ключевое отличие от кнопки Forward!)
        forwardStack.clear()

        // Вычисляем время загрузки
        val loadTimeMs = calculateLoadTime(htmlContent)

        // Проверяем кэш
        val cachedHtml = cache.get(url)
        val wasFromCache = cachedHtml != null
        val html = cachedHtml ?: htmlContent

        // Добавляем в кэш (если не no-cache)
        if (!isNoCache) {
            cache.put(url, html, isNoCache)
        }

        // Устанавливаем новую текущую страницу
        currentPage = PageData(
            url = url,
            html = html,
            loadTimeMs = if (wasFromCache) 0 else loadTimeMs,
            wasFromCache = wasFromCache,
            isNoCache = isNoCache
        )

        return currentPage!!
    }

    /**
     * Кнопка "назад" - вернуться на предыдущую страницу.
     * 
     * Правила:
     * - currentPage пушится в forwardStack
     * - поп из backStack → новый currentPage
     * - кэш проверяется
     * - возвращает null если backStack пуст
     */
    fun goBack(): PageData? {
        // Проверяем, есть ли история назад
        if (backStack.isEmpty()) {
            return null
        }

        // Сохраняем текущую в историю вперед
        currentPage?.let { forwardStack.add(0, it) }

        // Берем из истории назад
        currentPage = backStack.removeAt(backStack.size - 1)

        // Проверяем кэш (должна быть там, но обновляем LRU)
        currentPage?.let { page ->
            cache.get(page.url) // Обновляем LRU время доступа
        }

        return currentPage
    }

    /**
     * Кнопка "вперед" - вернуться на следующую страницу.
     * 
     * Правила:
     * - currentPage пушится в backStack
     * - поп из forwardStack → новый currentPage
     * - кэш проверяется
     * - возвращает null если forwardStack пуст
     */
    fun goForward(): PageData? {
        // Проверяем, есть ли история вперед
        if (forwardStack.isEmpty()) {
            return null
        }

        // Сохраняем текущую в историю назад
        currentPage?.let { backStack.add(it) }

        // Берем из истории вперед (LIFO - с индекса 0)
        currentPage = forwardStack.removeAt(0)

        // Проверяем кэш
        currentPage?.let { page ->
            cache.get(page.url) // Обновляем LRU время доступа
        }

        return currentPage
    }

    /**
     * Получить текущую страницу.
     */
    fun getCurrentPage(): PageData? = currentPage

    /**
     * Проверить, доступна ли кнопка "назад".
     */
    fun canGoBack(): Boolean = backStack.isNotEmpty()

    /**
     * Проверить, доступна ли кнопка "вперед".
     */
    fun canGoForward(): Boolean = forwardStack.isNotEmpty()

    /**
     * Очистить весь браузер (выключение телефона - только RAM).
     */
    fun reset() {
        backStack.clear()
        currentPage = null
        forwardStack.clear()
        cache.clear()
    }

    /**
     * Получить статистику навигации для отладки.
     */
    fun getNavigationState(): NavigationState {
        return NavigationState(
            backStackSize = backStack.size,
            currentPageUrl = currentPage?.url,
            forwardStackSize = forwardStack.size,
            backStackUrls = backStack.map { it.url },
            forwardStackUrls = forwardStack.map { it.url },
            cacheStats = cache.getStats()
        )
    }

    /**
     * Состояние навигации для отладки.
     */
    data class NavigationState(
        val backStackSize: Int,
        val currentPageUrl: String?,
        val forwardStackSize: Int,
        val backStackUrls: List<String>,
        val forwardStackUrls: List<String>,
        val cacheStats: PageCache.CacheStats
    )

    companion object {
        /**
         * Вычислить время загрузки страницы на основе размера HTML и скорости подключения.
         * 
         * Эмуляция скоростей:
         * - 9600 bps (ранний i-mode) = 1200 байт/сек
         * - 28.8 kbps (GPRS) = 3600 байт/сек
         * - 64 kbps (GPRS/3G) = 8000 байт/сек
         */
        fun calculateLoadTime(htmlContent: String, networkSpeedBps: Int = 9600): Long {
            val sizeBytes = htmlContent.toByteArray(Charsets.UTF_8).size.toLong()
            val speedBytesPerSec = networkSpeedBps / 8L
            
            // loadTime = sizeBytes / speedBytesPerSec * 1000 миллисекунд
            return if (speedBytesPerSec > 0) {
                (sizeBytes * 1000) / speedBytesPerSec
            } else {
                0L
            }
        }
    }
}
