package com.myapp.data.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Симулятор загрузки страницы с имитацией сетевых задержек.
 * Эмулирует реалистичные времена загрузки для i-mode браузера.
 */
class PageLoadingSimulator(
    private val networkSpeedBps: Int = 9600 // ранний i-mode по умолчанию
) {
    /**
     * Результат загрузки страницы.
     */
    data class LoadResult(
        val success: Boolean,
        val html: String = "",
        val loadTimeMs: Long = 0L,
        val errorMessage: String? = null,
        val wasFromCache: Boolean = false
    )

    /**
     * Эмулировать загрузку страницы с реалистичной задержкой.
     * 
     * @param url URL страницы
     * @param htmlSupplier функция для получения HTML контента
     * @param onProgress callback для обновления прогресса (0-100)
     */
    suspend fun loadPageWithDelay(
        @Suppress("UNUSED_PARAMETER") url: String,
        htmlSupplier: suspend () -> String,
        onProgress: suspend (progress: Int) -> Unit = {}
    ): LoadResult {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val html = htmlSupplier()
                val sizeBytes = html.toByteArray(Charsets.UTF_8).size
                
                // Вычисляем времязагрузки
                val totalLoadTimeMs = calculateLoadTime(sizeBytes)
                
                // Эмулируем прогресс загрузки с несколькими шагами
                val stepCount = 10
                val stepDelayMs = totalLoadTimeMs / stepCount
                
                for (step in 1..stepCount) {
                    // Задерживаемся на каждом шаге
                    delay(stepDelayMs)
                    
                    // Отправляем обновление прогресса
                    val progress = (step * 100) / stepCount
                    onProgress(progress)
                }
                
                val actualLoadTimeMs = System.currentTimeMillis() - startTime
                
                LoadResult(
                    success = true,
                    html = html,
                    loadTimeMs = actualLoadTimeMs,
                    wasFromCache = false
                )
            } catch (e: Exception) {
                // Log specific exceptions if needed in the future
                LoadResult(
                    success = false,
                    errorMessage = when (e) {
                        is java.io.IOException -> "Network error: ${e.message}"
                        is kotlinx.coroutines.CancellationException -> throw e // Re-throw cancellation
                        else -> e.message ?: "Unknown error during page load"
                    }
                )
            }
        }
    }

    /**
     * Эмулировать загрузку из кэша (мгновенная).
     */
    suspend fun loadFromCache(
        @Suppress("UNUSED_PARAMETER") url: String,
        html: String
    ): LoadResult = withContext(Dispatchers.IO) {
        LoadResult(
            success = true,
            html = html,
            loadTimeMs = 0L,
            wasFromCache = true
        )
    }

    /**
     * Вычислить время загрузки в миллисекундах.
     * 
     * Формула: loadTime = sizeBytes / (speedBps / 8) * 1000
     */
    fun calculateLoadTime(sizeBytes: Int): Long {
        val speedBytesPerSec = networkSpeedBps / 8
        return if (speedBytesPerSec > 0) {
            (sizeBytes.toLong() * 1000) / speedBytesPerSec
        } else {
            0L
        }
    }

    /**
     * Получить информацию о текущей скорости сети.
     */
    fun getNetworkInfo(): NetworkInfo {
        val speedBytesPerSec = networkSpeedBps / 8
        val typicalPageSize = 5000 // типичная страница i-mode 3-8 KB
        val typicalLoadTime = calculateLoadTime(typicalPageSize)
        
        return NetworkInfo(
            speedBps = networkSpeedBps,
            speedBytesPerSec = speedBytesPerSec,
            speedKbps = networkSpeedBps / 1000.0,
            typicalPageSizeBytes = typicalPageSize,
            typicalLoadTimeMs = typicalLoadTime,
            networkType = classifyNetworkType(networkSpeedBps)
        )
    }

    /**
     * Классифицировать тип сети по скорости.
     */
    private fun classifyNetworkType(speedBps: Int): String {
        return when {
            speedBps <= 9600 -> "i-mode (9.6 kbps)"
            speedBps <= 28800 -> "GPRS (28.8 kbps)"
            speedBps <= 56000 -> "Modem (56 kbps)"
            speedBps <= 128000 -> "ISDN (128 kbps)"
            speedBps <= 384000 -> "3G (384 kbps)"
            else -> "Fast (${speedBps / 1000} kbps)"
        }
    }

    /**
     * Информация о сети для отладки.
     */
    data class NetworkInfo(
        val speedBps: Int,
        val speedBytesPerSec: Int,
        val speedKbps: Double,
        val typicalPageSizeBytes: Int,
        val typicalLoadTimeMs: Long,
        val networkType: String
    )

    companion object {
        // Предустановки скоростей
        const val SPEED_EARLY_IMODE = 9600      // 9.6 kbps
        const val SPEED_GPRS = 28800             // 28.8 kbps
        const val SPEED_GPRS_HIGH = 56000        // 56 kbps
        const val SPEED_ISDN = 128000            // 128 kbps
        const val SPEED_3G = 384000              // 384 kbps
        const val SPEED_MODERN_4G = 1000000      // 1 Mbps
    }
}
