package com.myapp.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.text.TextPaint
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.caverock.androidsvg.SVG
import com.myapp.data.browser.BrowserPageManager
import com.myapp.data.browser.PageLoadingSimulator
import com.myapp.data.cache.DiskCacheStore
import com.myapp.data.cache.HistoryEntry
import com.myapp.data.cache.HistoryStore
import com.myapp.data.cache.SavedArticle
import com.myapp.data.cache.SavedArticleStore
import com.myapp.data.preferences.YumodePreferences
import com.myapp.data.registry.AssetPortalRepository
import com.myapp.data.repository.CurrencyRepository
import com.myapp.data.repository.RssPageProperties
import com.myapp.data.repository.RssRepository
import com.myapp.data.repository.WeatherRepository
import com.myapp.model.ArticleBlockType
import com.myapp.model.CitySearchResult
import com.myapp.model.CurrencyRate
import com.myapp.model.FeedItem
import com.myapp.model.PortalCatalog
import com.myapp.model.PortalSite
import com.myapp.model.PortalSource
import com.myapp.model.SavedCity
import com.myapp.model.SiteStatus
import com.myapp.model.SiteTheme
import com.myapp.model.WeatherSnapshot
import com.myapp.ui.viewmodel.ArticleViewModel
import com.myapp.ui.viewmodel.PortalDataViewModel
import com.myapp.ui.viewmodel.RssViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

internal class LegacyPortalController(
    private val activity: AppCompatActivity,
    private val scrollView: ScrollView,
    private val container: LinearLayout,
    private val overlayContainer: FrameLayout,
    private val onSoftKeysChanged: (SoftKeyBarState) -> Unit = {},
) {
    private companion object {
        const val SITE_LOADING_FRAME_MS = 150L
        const val SITE_LOADING_WHITE_FLASH_MS = 70L
        const val MENU_OVERLAY_ITEM_TAG_PREFIX = "overlay-menu-item:"
    }

    private var portalTypeface: Typeface = Typeface.MONOSPACE
    private val compactLineSpacing = 0.92f
    private val playgroundLineSpacing = PLAYGROUND_LINE_SPACING
    private val overclockersOrange = Color.parseColor("#FF7A00")

    private sealed interface PortalScreen {
        data object Home : PortalScreen
        data class Section(val sectionId: String) : PortalScreen
        data class Site(val siteId: String) : PortalScreen
        data class Article(
            val siteId: String,
            val feedItem: FeedItem,
        ) : PortalScreen
        data object Bookmarks : PortalScreen
        data object SavedArticles : PortalScreen
        data object History : PortalScreen
        data object SettingsHome : PortalScreen
        data object SettingsFonts : PortalScreen
        data class PageInfo(
            val info: BrowserPageInfo,
        ) : PortalScreen
    }

    private sealed interface ExitOverlay {
        data class Confirm(val returnScreen: PortalScreen) : ExitOverlay
        data class Ended(val returnScreen: PortalScreen) : ExitOverlay
    }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = YumodePreferences(activity)
    private val cacheStore = DiskCacheStore(activity)
    private val savedArticleStore = SavedArticleStore(cacheStore)
    private val historyStore = HistoryStore(cacheStore)
    private val catalog: PortalCatalog = AssetPortalRepository(activity).loadCatalog()
    private val currencyRepository = CurrencyRepository(cacheStore)
    private val weatherRepository = WeatherRepository(cacheStore)
    private val rssRepository = RssRepository(cacheStore)
    private val browserPageManager = BrowserPageManager(
        networkSpeedBps = PageLoadingSimulator.SPEED_EARLY_IMODE,
        coroutineScope = uiScope
    )
    private val portalDataViewModel: PortalDataViewModel by lazy {
        ViewModelProvider(activity, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PortalDataViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PortalDataViewModel(
                        weatherRepository = weatherRepository,
                        currencyRepository = currencyRepository,
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        })[PortalDataViewModel::class.java]
    }
    private val rssViewModel: RssViewModel by lazy {
        ViewModelProvider(activity, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(RssViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return RssViewModel(
                        rssRepository = rssRepository,
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        })[RssViewModel::class.java]
    }
    private val articleViewModel: ArticleViewModel by lazy {
        ViewModelProvider(activity, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ArticleViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ArticleViewModel(
                        rssRepository = rssRepository,
                        savedArticleStore = savedArticleStore,
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        })[ArticleViewModel::class.java]
    }
    private var portalDataObserversBound: Boolean = false
    private var rssObserversBound: Boolean = false
    private var articleObserversBound: Boolean = false

    private data class BrowserPageInfo(
        val title: String,
        val kind: String,
        val requestedUrl: String?,
        val finalUrl: String?,
        val contentType: String?,
        val byteCount: Int?,
        val secureLabel: String,
        val cacheLabel: String,
    )

    private data class MenuEntry(
        val title: String,
        val enabled: Boolean,
        val action: (() -> Unit)?,
    )

    private data class CatalogPageEntry(
        val title: String,
        val summary: String,
        val onClick: () -> Unit,
    )

    private data class CatalogPage(
        val id: String,
        val title: String,
        val summary: String,
        val entries: List<CatalogPageEntry>,
    )

    private var currentScreen: PortalScreen = PortalScreen.Home
    private var selectedCity: SavedCity = preferences.loadSavedCity() ?: LEGACY_QUICK_CITIES.first()
    private var cityQuery: String = selectedCity.name
    private var ratesState: RatesUiState = RatesUiState()
    private var currencyConverterAmountText: String = "100"
    private var currencyConverterFromIndex: Int = 1
    private var currencyConverterToIndex: Int = 0
    private var currencyConverterBindingAmount: Boolean = false
    private var weatherState: WeatherUiState = WeatherUiState()

    private data class YumodeRssSearchHit(
        val site: PortalSite,
        val item: FeedItem,
    )

    private var yumodeRssSearchQuery: String = ""
    private var yumodeRssSearchShown: Boolean = false
    private var yumodeRssSearchResults: List<YumodeRssSearchHit> = emptyList()

    private var rssUiState: RssViewModel.RssUiState = RssViewModel.RssUiState()
    private val feedStates: Map<String, FeedUiState>
        get() = rssUiState.feedStates
    private var feedPagerStates: Map<String, FeedPagerState>
        get() = rssUiState.feedPagerStates
        set(value) {
            rssViewModel.setFeedPagerStates(value)
        }
    private var feedCategorySelections: Map<String, String>
        get() = rssUiState.feedCategorySelections
        set(value) {
            rssViewModel.setFeedCategorySelections(value)
        }
    private var feedTagSelections: Map<String, String>
        get() = rssUiState.feedTagSelections
        set(value) {
            rssViewModel.setFeedTagSelections(value)
        }
    private var feedSearchQueries: Map<String, String>
        get() = rssUiState.feedSearchQueries
        set(value) {
            rssViewModel.setFeedSearchQueries(value)
        }
    private var articleUiState: ArticleViewModel.ArticleScreenState =
        ArticleViewModel.ArticleScreenState()
    private var articleState: ArticleUiState
        get() = articleUiState.articleState
        set(value) {
            articleViewModel.setArticleState(value)
        }
    private var articleStatesByKey: Map<String, ArticleUiState>
        get() = articleUiState.articleStatesByKey
        set(value) {
            articleViewModel.setArticleStatesByKey(value)
        }
    private var articleScrollPositions: Map<String, Int>
        get() = articleUiState.articleScrollPositions
        set(value) {
            articleViewModel.setArticleScrollPositions(value)
        }
    private var softKeys: SoftKeyBarState = SoftKeyBarState()
    private var backHistory: List<PortalScreen> = emptyList()
    private var forwardHistory: List<PortalScreen> = emptyList()
    private var catalogPageIndices: Map<String, Int> = emptyMap()
    private var menuActions: Map<Int, () -> Unit> = emptyMap()
    private var activeMenuEntries: List<MenuEntry> = emptyList()
    private var pendingFocusTag: String? = null
    private var selectedNewsHubCategoryId: String = NEWS_HUB_TECH_ID
    private var lastMenuFocusedIndex: Int = 0
    private var menuOverlayScrollView: ScrollView? = null
    private var currencyConverterFromView: TextView? = null
    private var currencyConverterToView: TextView? = null
    private var currencyConverterResultView: TextView? = null
    private var arsLogoBitmap: Bitmap? = null
    private var itcLogoBitmap: Bitmap? = null
    private var gagadgetLogoBitmap: Bitmap? = null
    private var lanaPixelTypeface: Typeface? = null
    private var portalTextSizeScale: Float = 1f
    private var exitOverlay: ExitOverlay? = null
    private var exitConfirmSelectYes: Boolean = false
    private var promptOverlay: LegacyPortalPromptOverlay? = null
    private var promptSelectYes: Boolean = true
    private var actionToastText: String? = null
    private val clearActionToast = Runnable {
        actionToastText = null
        renderOverlayOnly()
    }
    private var settingsReturnScreen: PortalScreen? = null
    private var settingsStack: List<PortalScreen> = emptyList()
    private var reopenMenuAfterSettingsBack: Boolean = false
    private var keepMenuVisibleUnderExit: Boolean = false
    private var menuOverlaySavedScrollY: Int? = null
    private var menuOverlaySavedFocusTag: String? = null
    private var exitOverlaySavedScrollY: Int? = null
    private var exitOverlaySavedFocusTag: String? = null
    private var promptOverlaySavedScrollY: Int? = null
    private var promptOverlaySavedFocusTag: String? = null
    private val finishEndedOverlay = Runnable { activity.finish() }
    private var siteLoadingStage: SiteLoadingOverlayStage? = null
    private var siteLoadingTileFrame: Int = 0
    private var pendingSiteLoadingAction: (() -> Unit)? = null
    private val advanceSiteLoadingFrame =
        object : Runnable {
            override fun run() {
                if (siteLoadingStage != SiteLoadingOverlayStage.RECEIVING) {
                    return
                }
                siteLoadingTileFrame = (siteLoadingTileFrame + 1) % 5
                renderOverlayOnly()
                mainHandler.postDelayed(this, SITE_LOADING_FRAME_MS)
            }
        }
    private val showWhiteSiteLoadingStage = Runnable {
        if (siteLoadingStage == null) {
            return@Runnable
        }
        siteLoadingStage = SiteLoadingOverlayStage.WHITE_SCREEN
        renderOverlayOnly()
    }
    private val finishSiteLoadingOverlay =
        Runnable {
            val action = pendingSiteLoadingAction
            stopSiteLoadingOverlay(clearPendingAction = true)
            action?.invoke()
        }

    fun start(skipHomeFeedPrefetch: Boolean = false) {
        bindPortalDataViewModel()
        bindRssViewModel()
        bindArticleViewModel()
        applyPortalFont(preferences.loadPortalFontId())
        refreshRates()
        refreshWeather(selectedCity)
        if (!skipHomeFeedPrefetch && shouldPrefetchHomeNewsFeeds()) {
            prefetchHomeNewsFeeds()
        }
        render()
    }

    fun preloadHomeFeedsForStartup(onComplete: () -> Unit) {
        val sites = homeNewsFeedSites()
        if (sites.isEmpty()) {
            onComplete()
            return
        }
        uiScope.launch {
            try {
                // Never block app startup on network: cap total preload time.
                val results =
                    withTimeoutOrNull(4_500L) {
                        withContext(Dispatchers.IO) {
                            sites.map { site ->
                                val resolvedSite = siteForSelectedFeedSection(site)
                                val outcome =
                                    runCatching { rssRepository.fetchFeed(resolvedSite) }
                                        .fold(
                                            onSuccess = { items -> FeedUiState(isLoading = false, error = null, items = items) },
                                            onFailure = { throwable -> FeedUiState(isLoading = false, error = throwable.toUserMessage()) },
                                        )
                                site.id to outcome
                            }
                        }
                    }
                results.orEmpty().forEach { (siteId, state) ->
                    rssViewModel.updateFeedState(siteId) { state }
                }
            } finally {
                // Always continue startup even if preload fails/hangs.
                onComplete()
            }
        }
    }

    fun dispose() {
        mainHandler.removeCallbacks(finishEndedOverlay)
        mainHandler.removeCallbacks(clearActionToast)
        stopSiteLoadingOverlay(clearPendingAction = true)
        uiScope.cancel()
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        if (siteLoadingStage != null) {
            return true
        }
        if (handleHardwareExitKey(event.keyCode)) {
            return true
        }
        if (softKeys.handleKeyCode(event.keyCode)) {
            return true
        }
        promptOverlay?.let { return handlePromptOverlayKey(it, event.keyCode) }
        if (
            exitOverlay == null &&
            activeMenuEntries.isEmpty() &&
            (
                event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    event.keyCode == KeyEvent.KEYCODE_ENTER ||
                    event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            )
        ) {
            val focused = activity.currentFocus
            if (focused?.isClickable == true) {
                focused.performClick()
                return true
            }
        }
        exitOverlay?.let { return handleExitOverlayKey(it, event.keyCode) }
        if (activeMenuEntries.isNotEmpty()) {
            return handleActiveMenuKey(event.keyCode)
        }
        if (
            currentScreen !is PortalScreen.Article &&
            currentScreen !is PortalScreen.Site &&
            handleCyclicVerticalFocus(event.keyCode)
        ) {
            return true
        }
        return when (val screen = currentScreen) {
            PortalScreen.Home -> handleHomeKey(event.keyCode)
            is PortalScreen.Section -> handleSectionKey(screen.sectionId, event.keyCode)
            is PortalScreen.Site -> handleSiteKey(screen, event.keyCode)
            is PortalScreen.Article -> handleArticleKey(screen, event.keyCode)
            PortalScreen.Bookmarks -> handleSettingsKey(event.keyCode)
            PortalScreen.SavedArticles -> handleSettingsKey(event.keyCode)
            PortalScreen.History -> handleSettingsKey(event.keyCode)
            PortalScreen.SettingsHome -> handleSettingsKey(event.keyCode)
            PortalScreen.SettingsFonts -> handleSettingsKey(event.keyCode)
            is PortalScreen.PageInfo -> handlePageInfoBack()
        }
    }

    private fun handleHardwareExitKey(keyCode: Int): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_ENDCALL,
            KeyEvent.KEYCODE_HOME,
            -> {
                if (exitOverlay == null) {
                    requestExitPrompt(currentScreen)
                }
                true
            }

            else -> false
        }

    private fun handleSettingsKey(keyCode: Int): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_CLEAR,
            KeyEvent.KEYCODE_ESCAPE,
            -> {
                popSettingsScreen()
                true
            }
            else -> false
        }

    private fun handleHomeKey(keyCode: Int): Boolean =
        handleNewsHubQuickSelectKey(keyCode)

    private fun handleExitOverlayKey(
        overlay: ExitOverlay,
        keyCode: Int,
    ): Boolean =
        when (overlay) {
            is ExitOverlay.Confirm ->
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_CLEAR,
                    KeyEvent.KEYCODE_ESCAPE,
                    -> {
                        dismissExitPrompt()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    -> {
                        if (!exitConfirmSelectYes) {
                            exitConfirmSelectYes = true
                            renderOverlayOnly()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    -> {
                        if (exitConfirmSelectYes) {
                            exitConfirmSelectYes = false
                            renderOverlayOnly()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        activateExitConfirmSelection()
                        true
                    }

                    else -> false
                }

            is ExitOverlay.Ended ->
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_CLEAR,
                    KeyEvent.KEYCODE_ESCAPE,
                    -> {
                        activity.finish()
                        true
                    }

                    else -> true
                }
        }

    private fun handlePromptOverlayKey(
        overlay: LegacyPortalPromptOverlay,
        keyCode: Int,
    ): Boolean = handleLegacyPortalPromptKey(
        overlay = overlay,
        keyCode = keyCode,
        isYesSelected = promptSelectYes,
        onSelectYesChanged = { selected ->
            promptSelectYes = selected
            renderOverlayOnly()
        },
        onBack = ::dismissPrompt,
        onActivate = ::activatePromptSelection,
    )

    private fun startSiteLoadingOverlay(
        totalDurationMs: Long,
        preloadAction: (() -> Unit)? = null,
        action: () -> Unit,
    ) {
        stopSiteLoadingOverlay(clearPendingAction = true)
        pendingSiteLoadingAction = action
        siteLoadingTileFrame = 0
        siteLoadingStage = SiteLoadingOverlayStage.RECEIVING
        renderOverlayOnly()
        // Delay preloadAction so loading overlay animation is visible before content updates
        mainHandler.postDelayed({
            preloadAction?.invoke()
        }, 50L)
        mainHandler.postDelayed(advanceSiteLoadingFrame, SITE_LOADING_FRAME_MS)
        val receivingDuration = (totalDurationMs - SITE_LOADING_WHITE_FLASH_MS).coerceAtLeast(420L)
        mainHandler.postDelayed(showWhiteSiteLoadingStage, receivingDuration)
        mainHandler.postDelayed(finishSiteLoadingOverlay, receivingDuration + SITE_LOADING_WHITE_FLASH_MS)
    }

    private fun startSiteLoadingOverlayForAsyncLoad(
        totalDurationMs: Long,
        startLoading: () -> Unit,
        isLoadingComplete: () -> Boolean,
        action: () -> Unit,
    ) {
        stopSiteLoadingOverlay(clearPendingAction = true)
        siteLoadingTileFrame = 0
        siteLoadingStage = SiteLoadingOverlayStage.RECEIVING
        renderOverlayOnly()

        // Start network/data loading after overlay is visible.
        startLoading()

        mainHandler.postDelayed(advanceSiteLoadingFrame, SITE_LOADING_FRAME_MS)
        val receivingDuration = (totalDurationMs - SITE_LOADING_WHITE_FLASH_MS).coerceAtLeast(420L)
        mainHandler.postDelayed({
            if (siteLoadingStage == null) return@postDelayed
            waitForAsyncLoadThenFinish(isLoadingComplete, action)
        }, receivingDuration)
    }

    private fun waitForAsyncLoadThenFinish(
        isLoadingComplete: () -> Boolean,
        action: () -> Unit,
    ) {
        if (siteLoadingStage == null) {
            return
        }
        if (isLoadingComplete()) {
            pendingSiteLoadingAction = action
            siteLoadingStage = SiteLoadingOverlayStage.WHITE_SCREEN
            renderOverlayOnly()
            mainHandler.postDelayed(finishSiteLoadingOverlay, SITE_LOADING_WHITE_FLASH_MS)
            return
        }
        mainHandler.postDelayed({ waitForAsyncLoadThenFinish(isLoadingComplete, action) }, 80L)
    }

    private fun stopSiteLoadingOverlay(clearPendingAction: Boolean) {
        mainHandler.removeCallbacks(advanceSiteLoadingFrame)
        mainHandler.removeCallbacks(showWhiteSiteLoadingStage)
        mainHandler.removeCallbacks(finishSiteLoadingOverlay)
        siteLoadingStage = null
        siteLoadingTileFrame = 0
        if (clearPendingAction) {
            pendingSiteLoadingAction = null
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shouldSimulateSiteLoading(site: PortalSite): Boolean {
        return site.id.isNotBlank()
    }

    private fun shouldSimulatePortalLoading(screen: PortalScreen): Boolean =
        when (screen) {
            PortalScreen.Home,
            is PortalScreen.Section,
            is PortalScreen.Site,
            is PortalScreen.Article,
            -> true

            else -> false
        }

    private fun browserUrlForScreen(screen: PortalScreen): String? =
        when (screen) {
            PortalScreen.Home -> "portal://home"
            is PortalScreen.Section -> "portal://section/${screen.sectionId}"
            is PortalScreen.Site -> "portal://site/${screen.siteId}"
            is PortalScreen.Article -> "article://${screen.siteId}/${screen.feedItem.id}"
            else -> null
        }

    private fun siteOpenDelayMs(): Long =
        Random.nextLong(2_300L, 4_401L)

    private fun siteTransitionDelayMs(): Long =
        Random.nextLong(1_200L, 1_951L)

    private fun articleTransitionDelayMs(fromPortal: Boolean): Long =
        if (fromPortal) {
            Random.nextLong(2_500L, 4_401L)
        } else {
            Random.nextLong(2_200L, 4_201L)
        }

    private fun transientSiteReload(
        totalDurationMs: Long = siteTransitionDelayMs(),
        preloadAction: (() -> Unit)? = null,
    ) {
        startSiteLoadingOverlay(
            totalDurationMs = totalDurationMs,
            preloadAction = preloadAction,
            action = { render() },
        )
    }

    private fun navigatePortalScreen(
        screen: PortalScreen,
        totalDurationMs: Long = siteTransitionDelayMs(),
        preloadAction: (() -> Unit)? = null,
        recordHistory: Boolean = true,
        clearForward: Boolean = true,
    ) {
        if (!shouldSimulatePortalLoading(screen)) {
            preloadAction?.invoke()
            setScreen(screen, recordHistory = recordHistory, clearForward = clearForward)
            return
        }
        startSiteLoadingOverlay(
            totalDurationMs = totalDurationMs,
            preloadAction = preloadAction,
            action = { setScreen(screen, recordHistory = recordHistory, clearForward = clearForward) },
        )
    }

    private fun openPortalHome(
        totalDurationMs: Long = siteTransitionDelayMs(),
        recordHistory: Boolean = true,
        clearForward: Boolean = true,
    ) {
        navigatePortalScreen(
            screen = PortalScreen.Home,
            totalDurationMs = totalDurationMs,
            recordHistory = recordHistory,
            clearForward = clearForward,
        )
    }

    private fun handleSimpleBackKey(): Boolean =
        goBackInHistory() || run {
            openPortalHome(recordHistory = false, clearForward = false)
            true
        }

    private fun handleSectionKey(
        sectionId: String,
        keyCode: Int,
    ): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_BACK ->
                goBackInHistory() || run {
                    openPortalHome(recordHistory = false, clearForward = false)
                    true
                }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> handleCatalogPaginationKey(sectionId, keyCode)

            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9 -> {
                handleCatalogQuickSelectKey(sectionId, keyCode) ||
                if (sectionId == "news") {
                    handleNewsHubQuickSelectKey(keyCode)
                } else {
                    false
                }
            }

            else -> false
        }

    private fun handleCatalogQuickSelectKey(
        sectionId: String,
        keyCode: Int,
    ): Boolean {
        val page = catalogPageBySectionId(sectionId) ?: return false
        val index =
            when (keyCode) {
                KeyEvent.KEYCODE_1 -> 0
                KeyEvent.KEYCODE_2 -> 1
                KeyEvent.KEYCODE_3 -> 2
                KeyEvent.KEYCODE_4 -> 3
                KeyEvent.KEYCODE_5 -> 4
                KeyEvent.KEYCODE_6 -> 5
                KeyEvent.KEYCODE_7 -> 6
                KeyEvent.KEYCODE_8 -> 7
                KeyEvent.KEYCODE_9 -> 8
                else -> return false
            }
        val pageIndex = currentCatalogPageIndex(page.id, page.entries.size)
        val entry = page.entries.drop(pageIndex * CATALOG_PAGE_SIZE).take(CATALOG_PAGE_SIZE).getOrNull(index) ?: return true
        entry.onClick()
        return true
    }

    private fun handleCatalogPaginationKey(
        sectionId: String,
        keyCode: Int,
    ): Boolean {
        val page = catalogPageBySectionId(sectionId) ?: return false
        if (page.entries.isEmpty()) {
            return false
        }
        val pageCount = ((page.entries.size - 1) / CATALOG_PAGE_SIZE) + 1
        if (pageCount <= 1) {
            return false
        }
        val delta =
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> return false
            }
        val currentPage = currentCatalogPageIndex(page.id, page.entries.size)
        val nextPage = (currentPage + delta).coerceIn(0, pageCount - 1)
        if (nextPage == currentPage) {
            return true
        }
        catalogPageIndices = catalogPageIndices + (page.id to nextPage)
        pendingFocusTag = catalogPaginationTag(page.id, nextPage)
        render()
        return true
    }

    private fun handleNewsHubQuickSelectKey(keyCode: Int): Boolean {
        val categories = com.myapp.ui.newsHubCategories()
        val index =
            when (keyCode) {
                KeyEvent.KEYCODE_1 -> 0
                KeyEvent.KEYCODE_2 -> 1
                KeyEvent.KEYCODE_3 -> 2
                KeyEvent.KEYCODE_4 -> 3
                else -> return false
            }
        val category = categories.getOrNull(index) ?: return false
        if (selectedNewsHubCategoryId == category.id) {
            return true
        }
        selectedNewsHubCategoryId = category.id
        pendingFocusTag = com.myapp.ui.newsHubCategoryTag(category.id)
        render()
        return true
    }

    private fun handleSiteKey(
        screen: PortalScreen.Site,
        keyCode: Int,
    ): Boolean {
        val site = catalog.siteById(screen.siteId)
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return goBackInHistory() || run {
                navigatePortalScreen(
                    screen = PortalScreen.Section(site.sectionId),
                    recordHistory = false,
                    clearForward = false,
                )
                true
            }
        }
        if (site.id == "money_rates") {
            handleCurrencyConverterKey(keyCode)?.let { return it }
        }
        if (site.source !is PortalSource.Rss) {
            return false
        }
        handleCategoryCycleKey(site, keyCode)?.let { return it }
        if (site.theme == SiteTheme.ARS) {
            val items = feedStates[site.id]?.items.orEmpty()
            val visibleItems = filteredFeedItems(site.id, items)
            if (visibleItems.isEmpty()) {
                return false
            }
            val pagerState = normalizePagerState(
                itemCount = visibleItems.size,
                pageSize = ARS_FEED_PAGE_SIZE,
                state = feedPagerStates[site.id] ?: FeedPagerState(),
            )
            val maxPageIndex = ((visibleItems.size - 1) / ARS_FEED_PAGE_SIZE).coerceAtLeast(0)
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT ->
                    moveFeedPageBounded(
                        siteId = site.id,
                        itemCount = visibleItems.size,
                        pageSize = ARS_FEED_PAGE_SIZE,
                        pageIndex = pagerState.pageIndex,
                        maxPageIndex = maxPageIndex,
                        delta = -1,
                    )

                KeyEvent.KEYCODE_DPAD_RIGHT ->
                    moveFeedPageBounded(
                        siteId = site.id,
                        itemCount = visibleItems.size,
                        pageSize = ARS_FEED_PAGE_SIZE,
                        pageIndex = pagerState.pageIndex,
                        maxPageIndex = maxPageIndex,
                        delta = 1,
                    )

                KeyEvent.KEYCODE_1 -> openVisibleFeedSlot(site, 0, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_2 -> openVisibleFeedSlot(site, 1, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_3 -> openVisibleFeedSlot(site, 2, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_4 -> openVisibleFeedSlot(site, 3, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_5 -> openVisibleFeedSlot(site, 4, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_6 -> openVisibleFeedSlot(site, 5, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_7 -> openVisibleFeedSlot(site, 6, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_8 -> openVisibleFeedSlot(site, 7, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_9 -> openVisibleFeedSlot(site, 8, ARS_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_0 -> openVisibleFeedSlot(site, 9, ARS_FEED_PAGE_SIZE)
                else -> false
            }
        }
        if (site.theme == SiteTheme.GAGADGET) {
            val items = feedStates[site.id]?.items.orEmpty()
            val visibleItems = filteredFeedItems(site.id, items)
            if (visibleItems.isEmpty()) {
                return false
            }
            val pagerState = normalizePagerState(
                itemCount = visibleItems.size,
                pageSize = GAGADGET_FEED_PAGE_SIZE,
                state = feedPagerStates[site.id] ?: FeedPagerState(),
            )
            val maxPageIndex = ((visibleItems.size - 1) / GAGADGET_FEED_PAGE_SIZE).coerceAtLeast(0)
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT ->
                    moveFeedPageBounded(
                        siteId = site.id,
                        itemCount = visibleItems.size,
                        pageSize = GAGADGET_FEED_PAGE_SIZE,
                        pageIndex = pagerState.pageIndex,
                        maxPageIndex = maxPageIndex,
                        delta = -1,
                    )

                KeyEvent.KEYCODE_DPAD_RIGHT ->
                    moveFeedPageBounded(
                        siteId = site.id,
                        itemCount = visibleItems.size,
                        pageSize = GAGADGET_FEED_PAGE_SIZE,
                        pageIndex = pagerState.pageIndex,
                        maxPageIndex = maxPageIndex,
                        delta = 1,
                    )

                KeyEvent.KEYCODE_1 -> openVisibleFeedSlot(site, 0, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_2 -> openVisibleFeedSlot(site, 1, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_3 -> openVisibleFeedSlot(site, 2, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_4 -> openVisibleFeedSlot(site, 3, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_5 -> openVisibleFeedSlot(site, 4, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_6 -> openVisibleFeedSlot(site, 5, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_7 -> openVisibleFeedSlot(site, 6, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_8 -> openVisibleFeedSlot(site, 7, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_9 -> openVisibleFeedSlot(site, 8, GAGADGET_FEED_PAGE_SIZE)
                KeyEvent.KEYCODE_0 -> openVisibleFeedSlot(site, 9, GAGADGET_FEED_PAGE_SIZE)
                else -> false
            }
        }

        val items = feedStates[site.id]?.items.orEmpty()
        val visibleItems = filteredFeedItems(site.id, items)
        if (visibleItems.isEmpty()) {
            return false
        }
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(visibleItems.size)
        val maxPageIndex = ((visibleItems.size - 1) / FeedPageSize).coerceAtLeast(0)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT ->
                moveFeedPageBounded(
                    siteId = site.id,
                    itemCount = visibleItems.size,
                    pageSize = FeedPageSize,
                    pageIndex = pagerState.pageIndex,
                    maxPageIndex = maxPageIndex,
                    delta = -1,
                )

            KeyEvent.KEYCODE_DPAD_RIGHT ->
                moveFeedPageBounded(
                    siteId = site.id,
                    itemCount = visibleItems.size,
                    pageSize = FeedPageSize,
                    pageIndex = pagerState.pageIndex,
                    maxPageIndex = maxPageIndex,
                    delta = 1,
                )

            KeyEvent.KEYCODE_1 -> openVisibleFeedSlot(site, 0, FeedPageSize)
            KeyEvent.KEYCODE_2 -> openVisibleFeedSlot(site, 1, FeedPageSize)
            KeyEvent.KEYCODE_3 -> openVisibleFeedSlot(site, 2, FeedPageSize)
            KeyEvent.KEYCODE_4 -> openVisibleFeedSlot(site, 3, FeedPageSize)
            KeyEvent.KEYCODE_5 -> openVisibleFeedSlot(site, 4, FeedPageSize)
            KeyEvent.KEYCODE_6 -> openVisibleFeedSlot(site, 5, FeedPageSize)
            KeyEvent.KEYCODE_7 -> openVisibleFeedSlot(site, 6, FeedPageSize)
            KeyEvent.KEYCODE_8 -> openVisibleFeedSlot(site, 7, FeedPageSize)
            else -> false
        }
    }

    private fun handleCurrencyConverterKey(keyCode: Int): Boolean? {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return null
        }
        val focusedTag = activity.currentFocus?.tag as? String ?: return null
        val codes = currencyCodesForConverter()
        if (codes.size < 2) {
            return false
        }
        val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
        return when (focusedTag) {
            CURRENCY_CONVERTER_FROM_TAG -> {
                currencyConverterFromIndex = cycleCurrencyConverterIndex(currencyConverterFromIndex, codes.size, delta)
                refreshCurrencyConverterUi(codes)
                true
            }

            CURRENCY_CONVERTER_TO_TAG -> {
                currencyConverterToIndex = cycleCurrencyConverterIndex(currencyConverterToIndex, codes.size, delta)
                refreshCurrencyConverterUi(codes)
                true
            }

            else -> null
        }
    }

    private fun cycleCurrencyConverterIndex(
        index: Int,
        size: Int,
        delta: Int,
    ): Int {
        if (size <= 0) {
            return 0
        }
        val next = (index + delta) % size
        return if (next < 0) next + size else next
    }

    private fun handleArticleKey(
        screen: PortalScreen.Article,
        keyCode: Int,
    ): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                goBackInHistory() || run {
                    navigatePortalScreen(
                        screen = PortalScreen.Site(screen.siteId),
                        recordHistory = false,
                        clearForward = false,
                    )
                    true
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                handleArticleScrollKey(1)
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                handleArticleScrollKey(-1)
            }

            else -> false
        }

    private fun handleActiveMenuKey(keyCode: Int): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> handleOverlayMenuVerticalFocus(keyCode)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                performFocusedClick()
                true
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_CLEAR,
            KeyEvent.KEYCODE_ESCAPE -> {
                closeMenu()
                true
            }
            KeyEvent.KEYCODE_1 -> performMenuEntry(0)
            KeyEvent.KEYCODE_2 -> performMenuEntry(1)
            KeyEvent.KEYCODE_3 -> performMenuEntry(2)
            KeyEvent.KEYCODE_4 -> performMenuEntry(3)
            KeyEvent.KEYCODE_5 -> performMenuEntry(4)
            KeyEvent.KEYCODE_6 -> performMenuEntry(5)
            KeyEvent.KEYCODE_7 -> performMenuEntry(6)
            KeyEvent.KEYCODE_8 -> performMenuEntry(7)
            KeyEvent.KEYCODE_9 -> performMenuEntry(8)
            KeyEvent.KEYCODE_0 -> performMenuEntry(9)
            KeyEvent.KEYCODE_STAR -> performMenuEntry(10)
            else -> false
        }

    private fun handleArticleScrollKey(direction: Int): Boolean {
        val focusedView = activity.currentFocus
        val canScrollArticle =
            focusedView == null ||
                focusedView.getTag() == ARTICLE_SCROLL_FOCUS_TAG
        if (!canScrollArticle) {
            return false
        }
        val contentHeight = scrollView.getChildAt(0)?.height ?: 0
        val maxScrollY = (contentHeight - scrollView.height).coerceAtLeast(0)
        if (maxScrollY <= 0) {
            return false
        }
        val currentScrollY = scrollView.scrollY
        val step = activity.dp(84)
        val nextScrollY = (currentScrollY + step * direction).coerceIn(0, maxScrollY)
        if (nextScrollY == currentScrollY) {
            return false
        }
        scrollView.smoothScrollTo(0, nextScrollY)
        return true
    }

    private fun moveFeedPageBounded(
        siteId: String,
        itemCount: Int,
        pageSize: Int,
        pageIndex: Int,
        maxPageIndex: Int,
        delta: Int,
    ): Boolean {
        if (itemCount <= pageSize || maxPageIndex <= 0) {
            return false
        }
        if ((delta < 0 && pageIndex <= 0) || (delta > 0 && pageIndex >= maxPageIndex)) {
            return false
        }
        val nextPageIndex = (pageIndex + delta).coerceIn(0, maxPageIndex)
        
        // Show connecting overlay even though data is cached - instant user feedback  
        startSiteLoadingOverlay(
            totalDurationMs = siteTransitionDelayMs(),
            action = {
                feedPagerStates = feedPagerStates + (
                    siteId to normalizePagerState(
                        itemCount = itemCount,
                        pageSize = pageSize,
                        state = (feedPagerStates[siteId] ?: FeedPagerState()).copy(
                            pageIndex = nextPageIndex,
                            selectedSlot = 0,
                        ),
                    )
                )
                render()
            },
        )
        return true
    }

    private fun handleCategoryCycleKey(
        site: PortalSite,
        keyCode: Int,
    ): Boolean? {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return null
        }
        val current = activity.currentFocus ?: return null
        val focusedTag = current.tag as? String ?: return null
        val prefix = categoryFocusTagPrefix(site.id)
        if (!focusedTag.startsWith(prefix)) {
            return null
        }
        val categoryViews =
            buildFocusableList(container)
                .filter { view -> (view.tag as? String)?.startsWith(prefix) == true }
        if (categoryViews.size <= 1) {
            return false
        }
        val currentIndex = categoryViews.indexOf(current).takeIf { it >= 0 } ?: return false
        val nextIndex =
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (currentIndex <= 0) categoryViews.lastIndex else currentIndex - 1
            } else {
                if (currentIndex >= categoryViews.lastIndex) 0 else currentIndex + 1
            }
        categoryViews[nextIndex].requestFocus()
        return true
    }

    private fun updateFeedState(
        siteId: String,
        transform: (FeedUiState) -> FeedUiState,
    ) {
        rssViewModel.updateFeedState(siteId, transform)
    }

    private fun setScreen(
        screen: PortalScreen,
        recordHistory: Boolean = true,
        clearForward: Boolean = true,
    ) {
        rememberArticleScroll()
        if (currentScreen == screen) {
            currentScreen = screen
            render()
            return
        }
        if (recordHistory) {
            backHistory = backHistory + currentScreen
            if (clearForward) {
                forwardHistory = emptyList()
            }
        }
        currentScreen = screen
        trackViewedScreen(screen)
        render()
    }

    private fun trackViewedScreen(screen: PortalScreen) {
        when (screen) {
            PortalScreen.Home -> historyStore.record(kind = "home", siteId = "portal_home", title = "Portal Home")
            is PortalScreen.Section -> {
                val title =
                    catalog.sections.firstOrNull { it.id == screen.sectionId }?.title
                        ?: screen.sectionId
                historyStore.record(kind = "section", siteId = screen.sectionId, title = title)
            }
            is PortalScreen.Site -> {
                val site = catalog.siteById(screen.siteId)
                historyStore.record(kind = "site", siteId = site.id, title = site.title)
            }
            is PortalScreen.Article -> historyStore.recordArticle(siteId = screen.siteId, feedItem = screen.feedItem)
            PortalScreen.Bookmarks -> historyStore.record(kind = "screen", siteId = "bookmarks", title = "Bookmarks")
            PortalScreen.SavedArticles -> historyStore.record(kind = "screen", siteId = "saved_articles", title = "Saved Articles")
            PortalScreen.History -> historyStore.record(kind = "screen", siteId = "history", title = "History")
            PortalScreen.SettingsHome -> historyStore.record(kind = "screen", siteId = "settings", title = "Settings")
            PortalScreen.SettingsFonts -> historyStore.record(kind = "screen", siteId = "settings_fonts", title = "Fonts")
            is PortalScreen.PageInfo -> historyStore.record(kind = "screen", siteId = "page_info", title = "Page Info")
        }
    }

    private fun goBackInHistory(): Boolean {
        val target = backHistory.lastOrNull() ?: return false
        rememberArticleScroll()
        backHistory = backHistory.dropLast(1)
        forwardHistory = forwardHistory + currentScreen
        browserPageManager.goBack()
        restoreScreenState(target)
        currentScreen = target
        render()
        return true
    }

    private fun goForwardInHistory(): Boolean {
        val target = forwardHistory.lastOrNull() ?: return false
        rememberArticleScroll()
        forwardHistory = forwardHistory.dropLast(1)
        backHistory = backHistory + currentScreen
        browserPageManager.goForward()
        restoreScreenState(target)
        currentScreen = target
        render()
        return true
    }

    private fun rememberArticleScroll() {
        val current = currentScreen
        if (current is PortalScreen.Article) {
            articleViewModel.rememberScrollFor(
                siteId = current.siteId,
                feedItem = current.feedItem,
                scrollY = scrollView.scrollY,
            )
        }
    }

    private fun articleScrollKey(
        siteId: String,
        feedItem: FeedItem,
    ): String = articleViewModel.buildCacheKey(siteId, feedItem)

    private fun cacheArticleState(
        siteId: String,
        feedItem: FeedItem,
        state: ArticleUiState,
    ) {
        articleViewModel.cacheStateFor(siteId, feedItem, state)
    }

    private fun setCurrentArticleStateAndCache(
        siteId: String,
        feedItem: FeedItem,
        state: ArticleUiState,
    ) {
        articleViewModel.setCurrentAndCacheFor(siteId, feedItem, state)
    }

    private fun applyResolvedArticleState(
        siteId: String,
        feedItem: FeedItem,
        state: ArticleUiState,
        registerInBrowser: Boolean = true,
    ) {
        setCurrentArticleStateAndCache(siteId, feedItem, state)
        if (registerInBrowser && state.article != null) {
            registerLoadedArticleInBrowser(siteId, feedItem, state)
        }
    }

    private fun restoreCachedArticleState(
        siteId: String,
        feedItem: FeedItem,
    ): ArticleUiState? = articleViewModel.restoreCachedOrSaved(siteId, feedItem)

    private fun restoreScreenState(screen: PortalScreen) {
        when (screen) {
            is PortalScreen.Article -> {
                articleState = restoreCachedArticleState(screen.siteId, screen.feedItem) ?: ArticleUiState(isLoading = false)
            }
            else -> Unit
        }
    }

    private fun articleBrowserPayload(
        feedItem: FeedItem,
        state: ArticleUiState,
    ): String =
        buildString {
            append(feedItem.title)
            val article = state.article ?: return@buildString
            article.excerpt?.takeIf { it.isNotBlank() }?.let {
                append('\n')
                append(it)
            }
            article.blocks.forEach { block ->
                if (block.text.isNotBlank()) {
                    append('\n')
                    append(block.text)
                }
            }
        }

    private fun registerLoadedArticleInBrowser(
        siteId: String,
        feedItem: FeedItem,
        state: ArticleUiState,
    ) {
        browserPageManager.registerLoadedPage(
            url = "article://$siteId/${feedItem.id}",
            htmlContent = articleBrowserPayload(feedItem, state),
            isNoCache = false,
        )
    }

    private fun refreshRates() {
        portalDataViewModel.fetchCurrencyRates()
    }

    private fun refreshWeather(city: SavedCity = selectedCity) {
        portalDataViewModel.fetchWeather(city)
    }

    private fun bindPortalDataViewModel() {
        if (portalDataObserversBound) {
            return
        }
        portalDataObserversBound = true

        portalDataViewModel.currencyState.observe(activity) { state ->
            ratesState =
                when (state) {
                    is PortalDataViewModel.CurrencyUiState.Idle -> ratesState
                    is PortalDataViewModel.CurrencyUiState.Loading ->
                        ratesState.copy(isLoading = true, error = null)

                    is PortalDataViewModel.CurrencyUiState.Success ->
                        ratesState.copy(isLoading = false, error = null, rates = state.rates)

                    is PortalDataViewModel.CurrencyUiState.Error ->
                        ratesState.copy(isLoading = false, error = state.message)
                }
            render()
        }

        portalDataViewModel.weatherState.observe(activity) { state ->
            weatherState =
                when (state) {
                    is PortalDataViewModel.WeatherUiState.Idle -> weatherState
                    is PortalDataViewModel.WeatherUiState.Loading ->
                        weatherState.copy(isLoadingWeather = true, error = null)

                    is PortalDataViewModel.WeatherUiState.Success ->
                        weatherState.copy(isLoadingWeather = false, error = null, data = state.weather)

                    is PortalDataViewModel.WeatherUiState.Error ->
                        weatherState.copy(isLoadingWeather = false, error = state.message)

                    is PortalDataViewModel.WeatherUiState.SearchLoading ->
                        weatherState.copy(
                            isSearching = true,
                            searchError = null,
                            searchResults = emptyList(),
                        )

                    is PortalDataViewModel.WeatherUiState.SearchSuccess ->
                        weatherState.copy(
                            isSearching = false,
                            searchResults = state.results,
                            searchError = if (state.results.isEmpty()) "no cities found" else null,
                        )

                    is PortalDataViewModel.WeatherUiState.SearchError ->
                        weatherState.copy(
                            isSearching = false,
                            searchResults = emptyList(),
                            searchError = state.message,
                        )
                }
            render()
        }
    }

    private fun refreshFeed(site: PortalSite) {
        rssViewModel.refreshFeed(siteForSelectedFeedSection(site))
    }

    private fun updateRssUiState(transform: (RssViewModel.RssUiState) -> RssViewModel.RssUiState) {
        rssViewModel.updateUiState(transform)
    }

    private fun resetPagerSelectionForSite(
        pagerStates: Map<String, FeedPagerState>,
        siteId: String,
    ): Map<String, FeedPagerState> {
        val pagerState = pagerStates[siteId] ?: FeedPagerState()
        return pagerStates + (siteId to pagerState.copy(pageIndex = 0, selectedSlot = 0))
    }

    private fun bindRssViewModel() {
        if (rssObserversBound) {
            return
        }
        rssObserversBound = true

        rssViewModel.uiStateLiveData.observe(activity) { state ->
            val previous = rssUiState
            rssUiState = state
            if (siteLoadingStage == null && shouldRenderForRssStateChange(previous, state)) {
                render()
            }
        }
    }

    private fun shouldRenderForRssStateChange(
        previous: RssViewModel.RssUiState,
        current: RssViewModel.RssUiState,
    ): Boolean {
        if (previous.feedStates != current.feedStates) {
            return true
        }
        if (previous.feedCategorySelections != current.feedCategorySelections) {
            return true
        }
        if (previous.feedTagSelections != current.feedTagSelections) {
            return true
        }
        if (previous.feedSearchQueries != current.feedSearchQueries) {
            return true
        }
        return pagerPageIndexMap(previous.feedPagerStates) != pagerPageIndexMap(current.feedPagerStates)
    }

    private fun pagerPageIndexMap(
        states: Map<String, FeedPagerState>,
    ): Map<String, Int> =
        states.mapValues { (_, pager) -> pager.pageIndex }

    private fun bindArticleViewModel() {
        if (articleObserversBound) {
            return
        }
        articleObserversBound = true

        articleViewModel.uiStateLiveData.observe(activity) { state ->
            articleUiState = state
            if (currentScreen is PortalScreen.Article) {
                render()
            }
        }
    }

    private fun prefetchHomeNewsFeeds() {
        homeNewsFeedSites().forEach { site ->
            val state = feedStates[site.id] ?: FeedUiState()
            if (!state.isLoading && state.items.isEmpty()) {
                refreshFeed(site)
            }
        }
    }

    private fun homeNewsFeedSites(): List<PortalSite> =
        (regionSites(catalog) + technologySites(catalog) + gameSites(catalog) + animationSites(catalog))
            .distinctBy { it.id }
            .filter { it.status == SiteStatus.ACTIVE && it.source is PortalSource.Rss }

    private fun shouldPrefetchHomeNewsFeeds(): Boolean =
        homeNewsFeedSites().any { site ->
            val state = feedStates[site.id] ?: return@any true
            !state.isLoading && state.items.isEmpty()
        }

    private fun searchCities(query: String) {
        val trimmed = query.trim()
        cityQuery = trimmed
        portalDataViewModel.searchCities(trimmed)
    }

    private fun openSection(sectionId: String) {
        navigatePortalScreen(PortalScreen.Section(sectionId))
    }

    private fun openSite(siteId: String) {
        val site = catalog.siteById(siteId)
        val siteUrl = "portal://site/$siteId"
        if (browserPageManager.isPageCached(siteUrl) && isSiteDataReady(site)) {
            setScreen(PortalScreen.Site(siteId))
            return
        }
        if (!shouldSimulateSiteLoading(site)) {
            uiScope.launch {
                loadSiteDataAndWait(site)
                browserPageManager.registerLoadedPage(
                    url = siteUrl,
                    htmlContent = siteId,
                    isNoCache = false,
                )
                setScreen(PortalScreen.Site(siteId))
            }
            return
        }

        var loadCompleted = false
        startSiteLoadingOverlayForAsyncLoad(
            totalDurationMs = siteOpenDelayMs(),
            startLoading = {
                uiScope.launch {
                    loadSiteDataAndWait(site)
                    browserPageManager.registerLoadedPage(
                        url = siteUrl,
                        htmlContent = siteId,
                        isNoCache = false,
                    )
                    loadCompleted = true
                }
            },
            isLoadingComplete = { loadCompleted },
            action = { setScreen(PortalScreen.Site(siteId)) },
        )
    }

    private fun openExternalUrl(url: String) {
        showConfirmPrompt(
            title = "Are you sure?",
            message = "You will leave Yumode.",
            onYes = {
                showActionToast("Opening full browser")
                runCatching {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                        },
                    )
                }.onFailure { error ->
                    if (error is ActivityNotFoundException) {
                        showActionToast("No browser")
                    } else {
                        showActionToast("Open failed")
                    }
                }
            },
            onNo = { showActionToast("Canceled") },
        )
    }

    private fun isSiteDataReady(site: PortalSite): Boolean =
        when {
            site.status != SiteStatus.ACTIVE -> true
            site.id == "money_rates" -> !ratesState.isLoading && (ratesState.rates.isNotEmpty() || ratesState.error != null)
            site.id == "weather_city" -> !weatherState.isLoadingWeather && (weatherState.data != null || weatherState.error != null)
            site.source !is PortalSource.Rss -> true
            else -> {
                val state = feedStates[site.id]
                state != null && !state.isLoading && (state.items.isNotEmpty() || state.error != null)
            }
        }

    private suspend fun loadSiteDataAndWait(site: PortalSite) {
        val sectionFeedUrls = legacySectionFeedUrlsForSite(site.id)
        if (!sectionFeedUrls.isNullOrEmpty() && feedCategorySelections[site.id].isNullOrBlank()) {
            updateFeedCategorySelection(site.id, sectionFeedUrls.keys.first())
        }
        
        if (site.status == SiteStatus.ACTIVE && site.source is PortalSource.Rss) {
            val state = feedStates[site.id] ?: FeedUiState()
            if (!state.isLoading && state.items.isEmpty()) {
                // Ждем загрузку RSS ленты асинхронно
                waitForFeedLoaded(site)
            }
        }
        
        when (site.id) {
            "money_rates" -> {
                if (!ratesState.isLoading && ratesState.rates.isEmpty()) {
                    waitForRatesLoaded()
                }
            }
            "weather_city" -> {
                if (!weatherState.isLoadingWeather && weatherState.data == null) {
                    waitForWeatherLoaded(selectedCity)
                }
            }
        }
    }

    private suspend fun waitForFeedLoaded(site: PortalSite) {
        refreshFeed(site)
        // Ждем пока feedStates[site.id].isLoading станет false
        while (feedStates[site.id]?.isLoading == true) {
            delay(50)
        }
    }

    private suspend fun waitForRatesLoaded() {
        refreshRates()
        while (ratesState.isLoading) {
            delay(50)
        }
    }

    private suspend fun waitForWeatherLoaded(city: SavedCity) {
        refreshWeather(city)
        while (weatherState.isLoadingWeather) {
            delay(50)
        }
    }

    private fun openArticle(
        site: PortalSite,
        feedItem: FeedItem,
    ) {
        val fromPortal = currentScreen == PortalScreen.Home || currentScreen is PortalScreen.Section
        val delayMs = articleTransitionDelayMs(fromPortal)
        
        // Проверяем сохраненные статьи
        restoreCachedArticleState(site.id, feedItem)?.let { restoredState ->
            applyResolvedArticleState(
                siteId = site.id,
                feedItem = feedItem,
                state = restoredState,
                registerInBrowser = true,
            )
            
            setScreen(PortalScreen.Article(site.id, feedItem)) /*
                    preloadAction = null,  // Данные уже загружены
            */
            return
        }

        if (!shouldSimulateSiteLoading(site)) {
            uiScope.launch {
                loadArticleContentAndCache(site, feedItem)
                setScreen(PortalScreen.Article(site.id, feedItem))
            }
            return
        }

        var loadCompleted = false
        startSiteLoadingOverlayForAsyncLoad(
            totalDurationMs = delayMs,
            startLoading = {
                uiScope.launch {
                    loadArticleContentAndCache(site, feedItem)
                    loadCompleted = true
                }
            },
            isLoadingComplete = { loadCompleted },
            action = { setScreen(PortalScreen.Article(site.id, feedItem)) },
        )
    }

    private suspend fun loadArticleContentAndCache(
        site: PortalSite,
        feedItem: FeedItem,
    ) {
        // Устанавливаем loading флаг БЕЗ render() - не показываем еще на экране
        articleState = ArticleUiState(isLoading = true)

        val resolvedState =
            articleViewModel.fetchArticleState(
                site = site,
                feedItem = feedItem,
                errorMapper = { it.toUserMessage() },
            )
        applyResolvedArticleState(
            siteId = site.id,
            feedItem = feedItem,
            state = resolvedState,
            registerInBrowser = true,
        )
        // ПОСЛЕ загрузки articleState готов, БЕЗ render еще
    }

    private fun openVisibleFeedSlot(
        site: PortalSite,
        slotIndex: Int,
        pageSize: Int,
    ): Boolean {
        val items = feedStates[site.id]?.items.orEmpty()
        val visibleItems = filteredFeedItems(site.id, items)
        if (visibleItems.isEmpty()) {
            return false
        }
        val pagerState = normalizePagerState(
            itemCount = visibleItems.size,
            pageSize = pageSize,
            state = feedPagerStates[site.id] ?: FeedPagerState(),
        )
        val pageStart = pagerState.pageIndex * pageSize
        val item = visibleItems.getOrNull(pageStart + slotIndex) ?: return false
        feedPagerStates = feedPagerStates + (
            site.id to normalizePagerState(
                itemCount = visibleItems.size,
                pageSize = pageSize,
                state = pagerState.copy(selectedSlot = slotIndex),
            )
        )
        openArticle(site, item)
        return true
    }

    fun populateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val menuItems = buildMenuEntries().filter { it.enabled }
        val idToAction = LinkedHashMap<Int, () -> Unit>()
        menuItems.forEachIndexed { index, entry ->
            val item = menu.add(Menu.NONE, entry.id, index, entry.title)
            item.isEnabled = entry.enabled
            entry.action?.let { idToAction[entry.id] = it }
        }
        menuActions = idToAction
        return menu.size() > 0
    }

    fun handleOptionsMenuItem(itemId: Int): Boolean {
        val action = menuActions[itemId] ?: return false
        action()
        return true
    }

    private fun buildMenuEntries(): List<LegacyPortalMenuEntry> {
        val articleScreen = currentScreen as? PortalScreen.Article
        val currentArticle = articleState.article
        val currentArticleUrl = currentArticle?.finalUrl?.ifBlank { currentArticle.sourceUrl }.orEmpty()
        val menuBackAction: (() -> Unit)? =
            when (currentScreen) {
                PortalScreen.Bookmarks,
                PortalScreen.SavedArticles,
                PortalScreen.History,
                PortalScreen.SettingsHome,
                PortalScreen.SettingsFonts,
                -> ({ popSettingsScreen() })

                is PortalScreen.PageInfo -> ({ handlePageInfoBack() })
                else -> if (backHistory.isNotEmpty()) ({ goBackInHistory() }) else null
            }
        return LegacyPortalMenuEntries.build(
            LegacyPortalMenuBuildInput(
                canGoBack = menuBackAction != null,
                canGoForward = forwardHistory.isNotEmpty(),
                onBack = { menuBackAction?.invoke() },
                onForward = { goForwardInHistory() },
                openHomeAction = { openPortalHome(recordHistory = false, clearForward = false) },
                reloadAction = buildReloadAction(),
                openBookmarksAction = { openMenuUtilityScreen(PortalScreen.Bookmarks) },
                openSavedArticlesAction = { openMenuUtilityScreen(PortalScreen.SavedArticles) },
                openHistoryAction = { openMenuUtilityScreen(PortalScreen.History) },
                bookmarkSiteId = currentBookmarkableSiteId(),
                bookmarkSiteAlreadyAdded = currentBookmarkableSiteId()?.let { preferences.isSiteBookmarked(it) } == true,
                onAddBookmark = ::confirmAddBookmark,
                onRemoveBookmark = ::confirmRemoveBookmark,
                articleSiteId = articleScreen?.siteId,
                article = currentArticle,
                isArticleSaved = currentArticleUrl.isNotBlank() && savedArticleStore.isSaved(currentArticleUrl),
                onSaveArticle = ::confirmSaveArticle,
                onRemoveSavedArticle = ::confirmRemoveSavedArticle,
                openPcSiteAction = currentArticleUrl.takeIf { it.isNotBlank() }?.let { url -> { openExternalUrl(url) } },
                openPageInfoAction = buildCurrentPageInfo()?.let { info -> { openMenuUtilityScreen(PortalScreen.PageInfo(info)) } },
                exitAction = { requestExitPrompt(currentScreen) },
            ),
        )
    }

    private fun buildReloadAction(): (() -> Unit)? {
        return when (val screen = currentScreen) {
            is PortalScreen.Site -> {
                val site = runCatching { catalog.siteById(screen.siteId) }.getOrNull() ?: return null
                when {
                    site.source is PortalSource.Rss -> ({ transientSiteReload(preloadAction = { refreshFeed(site) }) })
                    site.id == "money_rates" -> ({ transientSiteReload(preloadAction = ::refreshRates) })
                    site.id == "weather_city" -> ({ transientSiteReload(preloadAction = { refreshWeather(selectedCity) }) })
                    else -> null
                }
            }

            is PortalScreen.Article -> {
                val site = runCatching { catalog.siteById(screen.siteId) }.getOrNull() ?: return null
                ({ transientSiteReload(preloadAction = { reloadArticleContent(site, screen.feedItem) }) })
            }

            else -> null
        }
    }

    private fun currentBookmarkableSiteId(): String? =
        when (val screen = currentScreen) {
            is PortalScreen.Site -> screen.siteId
            is PortalScreen.Article -> screen.siteId
            else -> null
        }

    private fun addBookmark(siteId: String) {
        preferences.addBookmarkedSite(siteId)
        showActionToast("Added")
        render()
    }

    private fun removeBookmark(siteId: String) {
        preferences.removeBookmarkedSite(siteId)
        showActionToast("Deleted")
        render()
    }

    private fun buildCurrentPageInfo(): BrowserPageInfo? =
        when (val screen = currentScreen) {
            PortalScreen.Home -> BrowserPageInfo(
                title = "portal home",
                kind = "portal",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "session",
            )

            is PortalScreen.Section -> BrowserPageInfo(
                title = runCatching { catalog.sectionById(screen.sectionId).title }.getOrNull() ?: "section",
                kind = "section",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "session",
            )

            is PortalScreen.Site ->
                runCatching { catalog.siteById(screen.siteId) }.getOrNull()?.let { site ->
                    buildSitePageInfo(site)
                }
            is PortalScreen.Article -> buildArticlePageInfo(screen.siteId, screen.feedItem)
            PortalScreen.Bookmarks -> BrowserPageInfo(
                title = "bookmarks",
                kind = "list",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "saved",
            )
            PortalScreen.SavedArticles -> BrowserPageInfo(
                title = "saved articles",
                kind = "list",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "saved",
            )

            PortalScreen.History -> BrowserPageInfo(
                title = "history",
                kind = "list",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "saved",
            )
            PortalScreen.SettingsHome -> BrowserPageInfo(
                title = "settings",
                kind = "list",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "saved",
            )
            PortalScreen.SettingsFonts -> BrowserPageInfo(
                title = "fonts",
                kind = "list",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "saved",
            )

            is PortalScreen.PageInfo -> screen.info
        }

    private fun buildSitePageInfo(site: PortalSite): BrowserPageInfo =
        when (val source = site.source) {
            is PortalSource.Rss -> {
                val page = rssRepository.feedPageProperties(site.id)
                BrowserPageInfo(
                    title = site.title,
                    kind = "rss feed",
                    requestedUrl = page?.requestedUrl ?: source.feedUrl,
                    finalUrl = page?.finalUrl ?: source.feedUrl,
                    contentType = page?.contentType ?: "application/rss+xml",
                    byteCount = page?.byteCount,
                    secureLabel = securityLabel(page),
                    cacheLabel = cacheLabel(page),
                )
            }

            is PortalSource.Api -> BrowserPageInfo(
                title = site.title,
                kind = "api page",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "session",
            )

            is PortalSource.Static -> BrowserPageInfo(
                title = site.title,
                kind = "local page",
                requestedUrl = null,
                finalUrl = null,
                contentType = null,
                byteCount = null,
                secureLabel = "internal",
                cacheLabel = "bundled",
            )
        }

    private fun buildArticlePageInfo(
        siteId: String,
        feedItem: FeedItem,
    ): BrowserPageInfo {
        val page = rssRepository.articlePageProperties(siteId, feedItem)
        return BrowserPageInfo(
            title = feedItem.title,
            kind = "article",
            requestedUrl = page?.requestedUrl ?: feedItem.url,
            finalUrl = page?.finalUrl ?: feedItem.url,
            contentType = page?.contentType ?: "text/html",
            byteCount = page?.byteCount,
            secureLabel = securityLabel(page),
            cacheLabel = cacheLabel(page),
        )
    }

    private fun securityLabel(page: RssPageProperties?): String =
        when {
            page == null -> "unknown"
            page.isSecure -> "https"
            else -> "not secure"
        }

    private fun cacheLabel(page: RssPageProperties?): String =
        when {
            page == null -> "session"
            page.fromCache -> "cache"
            else -> "live"
        }

    private fun applyScreenTheme() {
        val screen = currentScreen
        val settingsBg = Color.parseColor("#F4F4F4")
        val menuOverlayBg = Color.WHITE
        if (activeMenuEntries.isNotEmpty()) {
            scrollView.setBackgroundColor(menuOverlayBg)
            container.setBackgroundColor(menuOverlayBg)
            return
        }
        if (isInSettings()) {
            scrollView.setBackgroundColor(settingsBg)
            container.setBackgroundColor(settingsBg)
            return
        }
        val backgroundColor =
            when (screen) {
                PortalScreen.Home -> COLOR_HOME_BG
                is PortalScreen.Site -> {
                    val site = catalog.siteById(screen.siteId)
                    if (site.id.startsWith("ann")) {
                        COLOR_ANN_BG
                    } else {
                        when (currentThemeForScreen(currentScreen)) {
                            SiteTheme.ARS -> COLOR_ARS_BG
                            SiteTheme.ITC -> COLOR_ITC_BG
                            SiteTheme.VERGE -> COLOR_VERGE_BG
                            SiteTheme.GAGADGET -> COLOR_GG_BG
                            SiteTheme.MEZHA -> COLOR_MEZHA_BG
                            SiteTheme.STOPGAME -> COLOR_STOPGAME_BG
                            SiteTheme.PLAYGROUND -> COLOR_PLAYGROUND_BG
                            SiteTheme.KYIV_VLADA -> COLOR_KV_BG
                            SiteTheme.UA_44 -> COLOR_UA44_BG
                            SiteTheme.VGORODE -> COLOR_VG_BG
                            else -> Color.WHITE
                        }
                    }
                }
                is PortalScreen.Article -> {
                    val site = catalog.siteById(screen.siteId)
                    if (site.id.startsWith("ann")) {
                        COLOR_ANN_BG
                    } else {
                        when (currentThemeForScreen(currentScreen)) {
                            SiteTheme.ARS -> COLOR_ARS_BG
                            SiteTheme.ITC -> COLOR_ITC_BG
                            SiteTheme.VERGE -> COLOR_VERGE_BG
                            SiteTheme.GAGADGET -> COLOR_GG_BG
                            SiteTheme.MEZHA -> COLOR_MEZHA_BG
                            SiteTheme.STOPGAME -> COLOR_STOPGAME_BG
                            SiteTheme.PLAYGROUND -> COLOR_PLAYGROUND_BG
                            SiteTheme.KYIV_VLADA -> COLOR_KV_BG
                            SiteTheme.UA_44 -> COLOR_UA44_BG
                            SiteTheme.VGORODE -> COLOR_VG_BG
                            else -> Color.WHITE
                        }
                    }
                }
                else -> {
                    when (currentThemeForScreen(currentScreen)) {
                        SiteTheme.ARS -> COLOR_ARS_BG
                        SiteTheme.ITC -> COLOR_ITC_BG
                        SiteTheme.VERGE -> COLOR_VERGE_BG
                        SiteTheme.GAGADGET -> COLOR_GG_BG
                        SiteTheme.MEZHA -> COLOR_MEZHA_BG
                        SiteTheme.STOPGAME -> COLOR_STOPGAME_BG
                        SiteTheme.PLAYGROUND -> COLOR_PLAYGROUND_BG
                        SiteTheme.KYIV_VLADA -> COLOR_KV_BG
                        SiteTheme.UA_44 -> COLOR_UA44_BG
                        SiteTheme.VGORODE -> COLOR_VG_BG
                        else -> Color.WHITE
                    }
                }
            }
        scrollView.setBackgroundColor(backgroundColor)
        container.setBackgroundColor(backgroundColor)
    }

    private fun isArsScreen(screen: PortalScreen): Boolean =
        currentThemeForScreen(screen) == SiteTheme.ARS

    private fun currentThemeForScreen(screen: PortalScreen): SiteTheme =
        when (screen) {
            is PortalScreen.Site -> catalog.siteById(screen.siteId).theme
            is PortalScreen.Article -> catalog.siteById(screen.siteId).theme
            else -> SiteTheme.DEFAULT
        }

    private fun render() {
        applyScreenTheme()
        updateSoftKeys()
        currencyConverterFromView = null
        currencyConverterToView = null
        currencyConverterResultView = null
        menuOverlayScrollView = null
        container.removeAllViews()
        overlayContainer.removeAllViews()
        when (val screen = currentScreen) {
            PortalScreen.Home -> renderHome()
            is PortalScreen.Section -> renderSection(screen.sectionId)
            is PortalScreen.Site -> renderSite(screen.siteId)
            is PortalScreen.Article -> renderArticle(screen)
            PortalScreen.Bookmarks -> renderBookmarks()
            PortalScreen.SavedArticles -> renderSavedArticles()
            PortalScreen.History -> renderHistory()
            PortalScreen.SettingsHome -> renderSettingsHome()
            PortalScreen.SettingsFonts -> renderSettingsFonts()
            is PortalScreen.PageInfo -> renderPageInfo(screen.info)
        }
        if (activeMenuEntries.isNotEmpty()) {
            renderMenuOverlay()
        }
        renderPromptOverlay()
        renderExitOverlay()
        actionToastText?.let { text ->
            LegacyPortalInfoOverlayRenderer.render(
                activity = activity,
                container = overlayContainer,
                appLabel = buildHomePortalLogoText(),
                portalTypeface = portalTypeface,
                compactLineSpacing = compactLineSpacing,
                scaledTextSize = { value -> scaledTextSize(value) },
                message = text,
            )
        }
        siteLoadingStage?.let { stage ->
            LegacyPortalSiteLoadingOverlayRenderer.render(
                activity = activity,
                container = overlayContainer,
                portalTypeface = portalTypeface,
                scaledTextSize = { value -> scaledTextSize(value) },
                stage = stage,
                tileFrame = siteLoadingTileFrame,
            )
        }
        restorePendingFocus()
        restoreMenuOverlayViewState()
        restorePromptOverlayViewState()
        restoreExitOverlayViewState()
        if (
            siteLoadingStage == null &&
            (activeMenuEntries.isNotEmpty() || menuOverlaySavedScrollY == null) &&
            promptOverlaySavedScrollY == null &&
            exitOverlaySavedScrollY == null &&
            (activeMenuEntries.isNotEmpty() || promptOverlay != null || exitOverlay != null || currentScreen !is PortalScreen.Article)
        ) {
            mainHandler.post { scrollView.scrollTo(0, 0) }
        }
    }

    private fun reloadArticleContent(
        site: PortalSite,
        feedItem: FeedItem,
    ) {
        articleState = ArticleUiState(isLoading = true)
        uiScope.launch {
            val resolvedState =
                articleViewModel.fetchArticleState(
                    site = site,
                    feedItem = feedItem,
                    errorMapper = { it.toUserMessage() },
                )
            applyResolvedArticleState(
                siteId = site.id,
                feedItem = feedItem,
                state = resolvedState,
                registerInBrowser = true,
            )
            render()
        }
    }

    private fun renderOverlayOnly() {
        updateSoftKeys()
        menuOverlayScrollView = null
        overlayContainer.removeAllViews()
        if (activeMenuEntries.isNotEmpty()) {
            renderMenuOverlay()
        }
        renderPromptOverlay()
        renderExitOverlay()
        actionToastText?.let { text ->
            LegacyPortalInfoOverlayRenderer.render(
                activity = activity,
                container = overlayContainer,
                appLabel = buildHomePortalLogoText(),
                portalTypeface = portalTypeface,
                compactLineSpacing = compactLineSpacing,
                scaledTextSize = { value -> scaledTextSize(value) },
                message = text,
            )
        }
        siteLoadingStage?.let { stage ->
            LegacyPortalSiteLoadingOverlayRenderer.render(
                activity = activity,
                container = overlayContainer,
                portalTypeface = portalTypeface,
                scaledTextSize = { value -> scaledTextSize(value) },
                stage = stage,
                tileFrame = siteLoadingTileFrame,
            )
        }
    }

    private fun restorePendingFocus() {
        val targetTag = pendingFocusTag ?: return
        pendingFocusTag = null
        mainHandler.post {
            findViewWithTagRecursive(container, targetTag)?.let(::focusViewAndBringIntoView)
        }
    }

    private fun restoreExitOverlayViewState() {
        val savedScrollY = exitOverlaySavedScrollY ?: return
        if (exitOverlay != null) {
            mainHandler.post { scrollView.scrollTo(0, savedScrollY) }
            return
        }
        restoreSavedViewState(
            savedScrollY = savedScrollY,
            savedFocusTag = exitOverlaySavedFocusTag,
            onRestored = {
                exitOverlaySavedScrollY = null
                exitOverlaySavedFocusTag = null
            },
        )
    }

    private fun restoreMenuOverlayViewState() {
        val savedScrollY = menuOverlaySavedScrollY ?: return
        if (activeMenuEntries.isNotEmpty()) {
            return
        }
        restoreSavedViewState(
            savedScrollY = savedScrollY,
            savedFocusTag = menuOverlaySavedFocusTag,
            onRestored = {
                menuOverlaySavedScrollY = null
                menuOverlaySavedFocusTag = null
            },
        )
    }

    private fun restorePromptOverlayViewState() {
        val savedScrollY = promptOverlaySavedScrollY ?: return
        if (promptOverlay != null) {
            mainHandler.post { scrollView.scrollTo(0, savedScrollY) }
            return
        }
        restoreSavedViewState(
            savedScrollY = savedScrollY,
            savedFocusTag = promptOverlaySavedFocusTag,
            onRestored = {
                promptOverlaySavedScrollY = null
                promptOverlaySavedFocusTag = null
            },
        )
    }

    private fun restoreSavedViewState(
        savedScrollY: Int,
        savedFocusTag: String?,
        onRestored: () -> Unit,
    ) {
        mainHandler.post {
            scrollView.scrollTo(0, savedScrollY)
            savedFocusTag?.let { tag ->
                findViewWithTagRecursive(container, tag)?.requestFocus()
            }
            mainHandler.post {
                scrollView.scrollTo(0, savedScrollY)
                onRestored()
            }
        }
    }

    private fun updateSoftKeys() {
        softKeys =
            if (siteLoadingStage != null) {
                SoftKeyBarState()
            } else if (promptOverlay != null) {
                SoftKeyBarState(
                    center = SoftKeyAction(label = "Select", onPress = ::activatePromptSelection),
                    right = SoftKeyAction(label = "Back", onPress = ::dismissPrompt),
                )
            } else if (exitOverlay is ExitOverlay.Confirm) {
                SoftKeyBarState(
                    center = SoftKeyAction(label = "Select", onPress = ::activateExitConfirmSelection),
                    right = SoftKeyAction(label = "Back", onPress = ::dismissExitPrompt),
                )
            } else if (exitOverlay is ExitOverlay.Ended) {
                SoftKeyBarState()
            } else if (activeMenuEntries.isNotEmpty()) {
                SoftKeyBarState(
                    center = SoftKeyAction(label = "Open", onPress = ::performFocusedClick),
                    right = SoftKeyAction(label = "Back", onPress = ::closeMenu),
                )
            } else {
                when (val screen = currentScreen) {
                    PortalScreen.Home -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = exitSoftKeyAction(),
                    )

                    is PortalScreen.Section -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = {
                                openPortalHome(recordHistory = false, clearForward = false)
                            },
                        ),
                    )

                    is PortalScreen.Site -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = {
                                val site = catalog.siteById(screen.siteId)
                                navigatePortalScreen(
                                    screen = PortalScreen.Section(site.sectionId),
                                    recordHistory = false,
                                    clearForward = false,
                                )
                            },
                        ),
                    )

                    is PortalScreen.Article -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = {
                                navigatePortalScreen(
                                    screen = PortalScreen.Site(screen.siteId),
                                    recordHistory = false,
                                    clearForward = false,
                                )
                            },
                        ),
                    )

                    PortalScreen.Bookmarks -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = { popSettingsScreen() },
                            preferFallback = true,
                        ),
                    )
                    PortalScreen.SavedArticles -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = { popSettingsScreen() },
                            preferFallback = true,
                        ),
                    )

                    PortalScreen.History -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = { popSettingsScreen() },
                            preferFallback = true,
                        ),
                    )

                    PortalScreen.SettingsHome -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = { popSettingsScreen() },
                            preferFallback = true,
                        ),
                    )
                    PortalScreen.SettingsFonts -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = { popSettingsScreen() },
                            preferFallback = true,
                        ),
                    )

                    is PortalScreen.PageInfo -> SoftKeyBarState(
                        left = menuSoftKeyAction(),
                        right = backSoftKeyAction(
                            fallback = { handlePageInfoBack() },
                            preferFallback = true,
                        ),
                    )
                }
            }
        onSoftKeysChanged(softKeys)
    }

    private fun menuSoftKeyAction(): SoftKeyAction =
        SoftKeyAction(
            label = "Menu",
            onPress = {
                // Keep softkey menu reliable on real devices.
                openMenuOverlay()
            },
        )

    private fun exitSoftKeyAction(): SoftKeyAction =
        SoftKeyAction(
            label = "Exit",
            onPress = { requestExitPrompt(currentScreen) },
        )

    private fun openMenuOverlay() {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        if (activeMenuEntries.isEmpty()) {
            menuOverlaySavedScrollY = scrollView.scrollY
            menuOverlaySavedFocusTag = activity.currentFocus?.tag as? String
        }
        val menuItems = buildMenuEntries().filter { it.enabled }
        menuActions = menuItems.mapNotNull { it.action?.let { action -> it.id to action } }.toMap(LinkedHashMap())
        lastMenuFocusedIndex = lastMenuFocusedIndex.coerceIn(0, (menuItems.size - 1).coerceAtLeast(0))
        activeMenuEntries =
            menuItems.map { entry ->
                MenuEntry(
                    title = entry.title,
                    enabled = entry.enabled,
                    action = entry.action,
                )
            }
        renderOverlayOnly()
    }

    private fun closeMenu() {
        if (activeMenuEntries.isEmpty()) {
            return
        }
        activeMenuEntries = emptyList()
        restoreUnderlyingFocus(menuOverlaySavedFocusTag)
        menuOverlaySavedScrollY = null
        menuOverlaySavedFocusTag = null
        renderOverlayOnly()
    }

    private fun performMenuEntry(index: Int): Boolean {
        val entry = activeMenuEntries.getOrNull(index) ?: return false
        if (!entry.enabled || entry.action == null) {
            return false
        }
        menuOverlaySavedScrollY = null
        menuOverlaySavedFocusTag = null
        activeMenuEntries = emptyList()
        entry.action.invoke()
        return true
    }

    private fun performFocusedClick() {
        val focusedView = activity.currentFocus
        if (focusedView?.isClickable == true) {
            focusedView.performClick()
        }
    }

    private fun backSoftKeyAction(
        fallback: (() -> Unit)? = null,
        preferFallback: Boolean = false,
    ): SoftKeyAction {
        val action: (() -> Unit)? =
            when {
                preferFallback && fallback != null -> fallback
                backHistory.isNotEmpty() -> ({
                    goBackInHistory()
                    Unit
                })
                fallback != null -> fallback
                else -> null
            }
        return SoftKeyAction(
            label = if (action != null) "Back" else "",
            onPress = action,
        )
    }

    private fun requestExitPrompt(returnScreen: PortalScreen = currentScreen) {
        mainHandler.removeCallbacks(finishEndedOverlay)
        keepMenuVisibleUnderExit = activeMenuEntries.isNotEmpty()
        exitOverlaySavedScrollY = if (keepMenuVisibleUnderExit) null else scrollView.scrollY
        exitOverlaySavedFocusTag =
            if (keepMenuVisibleUnderExit) {
                null
            } else {
                activity.currentFocus?.tag as? String
            }
        exitConfirmSelectYes = false
        exitOverlay = ExitOverlay.Confirm(returnScreen)
        renderOverlayOnly()
    }

    private fun dismissExitPrompt() {
        mainHandler.removeCallbacks(finishEndedOverlay)
        if (exitOverlay == null) {
            return
        }
        exitOverlay = null
        keepMenuVisibleUnderExit = false
        restoreUnderlyingFocus(exitOverlaySavedFocusTag)
        exitOverlaySavedScrollY = null
        exitOverlaySavedFocusTag = null
        renderOverlayOnly()
    }

    private fun confirmExit() {
        val returnScreen = currentScreen
        mainHandler.removeCallbacks(finishEndedOverlay)
        if (!keepMenuVisibleUnderExit) {
            activeMenuEntries = emptyList()
        }
        exitOverlay = ExitOverlay.Ended(returnScreen)
        renderOverlayOnly()
        mainHandler.postDelayed(finishEndedOverlay, 1_000L)
    }

    private fun activateExitConfirmSelection() {
        if (exitConfirmSelectYes) {
            confirmExit()
        } else {
            dismissExitPrompt()
        }
    }

    private fun activatePromptSelection() {
        val overlay = promptOverlay
        if (overlay is LegacyPortalPromptOverlay.Confirm) {
            val action = if (promptSelectYes) overlay.onYes else overlay.onNo
            dismissPrompt()
            action()
        }
    }

    private fun renderExitOverlay() {
        when (exitOverlay) {
            is ExitOverlay.Confirm ->
                LegacyPortalExitRenderer.renderQuitPrompt(
                    activity = activity,
                    container = overlayContainer,
                    appLabel = buildHomePortalLogoText(),
                    portalTypeface = portalTypeface,
                    compactLineSpacing = compactLineSpacing,
                    scaledTextSize = { value -> scaledTextSize(value) },
                    selectYes = exitConfirmSelectYes,
                    onConfirm = ::confirmExit,
                    onCancel = ::dismissExitPrompt,
                )

            is ExitOverlay.Ended ->
                LegacyPortalExitRenderer.renderEnded(
                    activity = activity,
                    container = overlayContainer,
                    appLabel = buildHomePortalLogoText(),
                    portalTypeface = portalTypeface,
                    compactLineSpacing = compactLineSpacing,
                    scaledTextSize = { value -> scaledTextSize(value) },
                )

            null -> Unit
        }
    }

    private fun renderPromptOverlay() {
        val overlay = promptOverlay ?: return
        renderLegacyPortalPromptOverlay(
            overlay = overlay,
            activity = activity,
            container = overlayContainer,
            appLabel = buildHomePortalLogoText(),
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            selectYes = promptSelectYes,
            onConfirm = {
                dismissPrompt()
                if (overlay is LegacyPortalPromptOverlay.Confirm) {
                    overlay.onYes()
                }
            },
            onCancel = {
                dismissPrompt()
                if (overlay is LegacyPortalPromptOverlay.Confirm) {
                    overlay.onNo()
                }
            },
        )
    }

    private fun dismissPrompt() {
        if (promptOverlay == null) {
            return
        }
        promptOverlay = null
        restoreUnderlyingFocus(promptOverlaySavedFocusTag)
        promptOverlaySavedScrollY = null
        promptOverlaySavedFocusTag = null
        renderOverlayOnly()
    }

    private fun showConfirmPrompt(
        title: String,
        message: String,
        yesLabel: String = "YES",
        noLabel: String = "NO",
        onYes: () -> Unit,
        onNo: () -> Unit = {},
    ) {
        mainHandler.removeCallbacks(finishEndedOverlay)
        promptOverlaySavedScrollY = scrollView.scrollY
        promptOverlaySavedFocusTag =
            when {
                activeMenuEntries.isNotEmpty() -> menuOverlaySavedFocusTag ?: (activity.currentFocus?.tag as? String)
                else -> activity.currentFocus?.tag as? String
            }
        activeMenuEntries = emptyList()
        exitOverlay = null
        promptSelectYes = true
        promptOverlay =
            LegacyPortalPromptOverlay.Confirm(
                title = title,
                message = message,
                yesLabel = yesLabel,
                noLabel = noLabel,
                onYes = onYes,
                onNo = onNo,
            )
        renderOverlayOnly()
    }

    private fun confirmAddBookmark(siteId: String) {
        showConfirmPrompt(
            title = "Add to bookmarks",
            message = "",
            onYes = { addBookmark(siteId) },
            onNo = { showActionToast("Canceled") },
        )
    }

    private fun confirmRemoveBookmark(siteId: String) {
        showConfirmPrompt(
            title = "Delete bookmark",
            message = "",
            onYes = { removeBookmark(siteId) },
            onNo = { showActionToast("Canceled") },
        )
    }

    private fun confirmSaveArticle(
        siteId: String,
        article: com.myapp.model.SanitizedArticle,
    ) {
        showConfirmPrompt(
            title = "Save article",
            message = "Save this article?",
            onYes = {
                savedArticleStore.save(siteId, article)
                showActionToast("Added")
                render()
            },
            onNo = { showActionToast("Canceled") },
        )
    }

    private fun confirmRemoveSavedArticle(url: String) {
        showConfirmPrompt(
            title = "Delete saved article",
            message = "Delete saved copy from phone?",
            onYes = {
                savedArticleStore.remove(url)
                showActionToast("Deleted")
                render()
            },
            onNo = { showActionToast("Canceled") },
        )
    }

    private fun showActionToast(text: String) {
        mainHandler.removeCallbacks(clearActionToast)
        actionToastText = text
        renderOverlayOnly()
        mainHandler.postDelayed(clearActionToast, 1_000L)
    }

    private fun openSettings() {
        reopenMenuAfterSettingsBack = false
        if (settingsReturnScreen == null) {
            settingsReturnScreen = currentScreen
        }
        settingsStack = listOf(PortalScreen.SettingsHome)
        setScreen(PortalScreen.SettingsHome, recordHistory = false, clearForward = false)
    }

    private fun pushSettingsScreen(screen: PortalScreen) {
        reopenMenuAfterSettingsBack = false
        if (settingsReturnScreen == null) {
            settingsReturnScreen = currentScreen
        }
        if (settingsStack.isEmpty()) {
            settingsStack = listOf(PortalScreen.SettingsHome)
        }
        settingsStack = settingsStack + screen
        setScreen(screen, recordHistory = false, clearForward = false)
    }

    private fun openMenuUtilityScreen(screen: PortalScreen) {
        settingsReturnScreen = currentScreen
        settingsStack = emptyList()
        reopenMenuAfterSettingsBack = true
        setScreen(screen, recordHistory = false, clearForward = false)
    }

    private fun popSettingsScreen() {
        if (settingsStack.isEmpty()) {
            val returnTo = settingsReturnScreen
            settingsReturnScreen = null
            val reopenMenu = reopenMenuAfterSettingsBack
            reopenMenuAfterSettingsBack = false
            setScreen(returnTo ?: PortalScreen.Home, recordHistory = false, clearForward = false)
            if (reopenMenu) {
                openMenuOverlay()
            }
            return
        }
        if (settingsStack.size <= 1) {
            val returnTo = settingsReturnScreen
            settingsReturnScreen = null
            settingsStack = emptyList()
            val reopenMenu = reopenMenuAfterSettingsBack
            reopenMenuAfterSettingsBack = false
            setScreen(returnTo ?: PortalScreen.Home, recordHistory = false, clearForward = false)
            if (reopenMenu) {
                openMenuOverlay()
            }
            return
        }
        settingsStack = settingsStack.dropLast(1)
        val target = settingsStack.last()
        setScreen(target, recordHistory = false, clearForward = false)
    }

    private fun handlePageInfoBack(): Boolean {
        return if (settingsReturnScreen != null || settingsStack.isNotEmpty() || reopenMenuAfterSettingsBack) {
            popSettingsScreen()
            true
        } else {
            handleSimpleBackKey()
        }
    }

    private fun isInSettings(): Boolean =
        settingsStack.isNotEmpty() ||
            currentScreen == PortalScreen.SettingsHome ||
            currentScreen == PortalScreen.SettingsFonts ||
            currentScreen == PortalScreen.Bookmarks ||
            currentScreen == PortalScreen.SavedArticles ||
            currentScreen == PortalScreen.History ||
            currentScreen is PortalScreen.PageInfo

    private fun renderSettingsHome() {
        val sections =
            listOf(
                SettingsListSection(
                    title = "Settings",
                    items = listOf(
                        SettingsListItem("Fonts", subtitle = "Portal UI font", onClick = { pushSettingsScreen(PortalScreen.SettingsFonts) }),
                        SettingsListItem("Bookmarks", subtitle = "Saved sites", onClick = { pushSettingsScreen(PortalScreen.Bookmarks) }),
                        SettingsListItem("Saved Articles", subtitle = "Offline articles", onClick = { pushSettingsScreen(PortalScreen.SavedArticles) }),
                        SettingsListItem("History", subtitle = "Visited pages", onClick = { pushSettingsScreen(PortalScreen.History) }),
                    ),
                ),
            )
        LegacyPortalSettingsListRenderer.render(
            activity = activity,
            container = container,
            sections = sections,
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            visualStyle = SettingsListVisualStyle.MENU_DENSE,
            denseScale = 1.14f,
        )
    }

    private fun renderSettingsFonts() {
        val current = preferences.loadPortalFontId()
        val sections =
            listOf(
                SettingsListSection(
                    title = "Fonts",
                    items = listOf(
                        SettingsListItem(
                            title = if (current == FONT_ID_CLASSIC) "Classic *" else "Classic",
                            subtitle = "Default",
                            onClick = { updatePortalFont(FONT_ID_CLASSIC); showActionToast("Added") },
                        ),
                        SettingsListItem(
                            title = if (current == FONT_ID_LANA_PIXEL) "Lana Pixel *" else "Lana Pixel",
                            subtitle = "Pixel",
                            onClick = { updatePortalFont(FONT_ID_LANA_PIXEL); showActionToast("Added") },
                        ),
                    ),
                ),
            )
        LegacyPortalSettingsListRenderer.render(
            activity = activity,
            container = container,
            sections = sections,
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            visualStyle = SettingsListVisualStyle.MENU_DENSE,
        )
    }

    private fun openSavedArticle(saved: SavedArticle) {
        val prepared = articleViewModel.prepareSavedArticle(saved)
        applyResolvedArticleState(
            siteId = prepared.siteId,
            feedItem = prepared.feedItem,
            state = prepared.state,
            registerInBrowser = true,
        )
        val site = runCatching { catalog.siteById(prepared.siteId) }.getOrNull()
        if (site != null) {
            setScreen(PortalScreen.Article(prepared.siteId, prepared.feedItem))
        }
    }

    private fun openHistoryEntry(entry: HistoryEntry) {
        when (entry.kind) {
            "home" -> openPortalHome()
            "section" -> openSection(entry.siteId)
            "screen" ->
                when (entry.siteId) {
                    "bookmarks" -> setScreen(PortalScreen.Bookmarks)
                    "saved_articles" -> setScreen(PortalScreen.SavedArticles)
                    "history" -> setScreen(PortalScreen.History)
                    else -> openPortalHome()
                }
            "article" -> {
                val prepared = articleViewModel.prepareHistoryArticle(entry) ?: return
                val site = runCatching { catalog.siteById(prepared.first) }.getOrNull() ?: return
                val feedItem = prepared.second
                openArticle(site, feedItem)
            }
            else -> {
                val site = runCatching { catalog.siteById(entry.siteId) }.getOrNull() ?: return
                openSite(site.id)
            }
        }
    }

    private fun renderHome() {
        val settingsAction = { openSettings() }
        val searchRow = createYumodeGlobalRssSearchRow()
        if (yumodeRssSearchShown && yumodeRssSearchQuery.isNotBlank()) {
            container.addView(searchRow)
            renderYumodeRssSearchResults()
            focusFirstLink()
            return
        }
        LegacyPortalHomeRenderer.render(
            activity = activity,
            container = container,
            catalog = catalog,
            feedStates = feedStates,
            selectedNewsHubCategoryId = selectedNewsHubCategoryId,
            setSelectedNewsHubCategoryId = { value -> selectedNewsHubCategoryId = value },
            setPendingFocusTag = { value -> pendingFocusTag = value },
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            statusLine = buildStatusLine(),
            weatherText = buildHomeWeatherText(),
            searchRow = searchRow,
            openSite = { siteId -> openSite(siteId) },
            openSection = { sectionId -> openSection(sectionId) },
            openExternalUrl = { url -> openExternalUrl(url) },
            openCatalogPage = { pageId -> openCatalogPage(pageId) },
            openArticle = { site, item -> openArticle(site, item) },
            rerender = { render() },
            focusFirstLink = { focusFirstLink() },
            onOpenSettings = { settingsAction() },
            createCategoryRow = { center -> createCategoryRow(center) },
            buildHomePortalLogoText = { buildHomePortalLogoText() },
        )
    }

    private fun openCatalogPage(pageId: String) {
        navigatePortalScreen(PortalScreen.Section(catalogSectionId(pageId)))
    }

    private fun renderSection(sectionId: String) {
        catalogPageBySectionId(sectionId)?.let { page ->
            renderCatalogPage(page)
            return
        }
        val section = catalog.sectionById(sectionId)
        addLink("home") { openPortalHome() }
        addSectionHeader(section.title)
        addPlainText(section.summary, sizeSp = 10f, color = COLOR_MUTED)
        catalog.sitesInSection(sectionId).forEach { site ->
            addLink(site.title) { openSite(site.id) }
        }
        focusFocusableTextAt(1)
    }

    private fun renderCatalogPage(page: CatalogPage) {
        val pageCount = ((page.entries.size - 1) / CATALOG_PAGE_SIZE) + 1
        val currentPage = currentCatalogPageIndex(page.id, page.entries.size)
        val visibleEntries = page.entries.drop(currentPage * CATALOG_PAGE_SIZE).take(CATALOG_PAGE_SIZE)
        addHomeSpecHeader()
        addPlainText(page.title, bold = true, sizeSp = 11f, bottomMarginDp = 1)
        addMetaText(
            if (pageCount > 1) {
                "${page.summary}  page ${currentPage + 1}/$pageCount"
            } else {
                page.summary
            },
        )
        addDivider()
        var lastCategory: String? = null
        visibleEntries.forEachIndexed { index, entry ->
            if (page.id == CATALOG_PC_ID && entry.summary.isNotBlank() && entry.summary != lastCategory) {
                addPlainText(
                    text = entry.summary,
                    sizeSp = 10f,
                    color = COLOR_MUTED,
                    bold = true,
                    verticalPaddingDp = 0,
                    bottomMarginDp = 1,
                )
                lastCategory = entry.summary
            }
            val row =
                addFeedEntry(
                    number = index + 1,
                    title = entry.title,
                    meta = entry.summary.ifBlank { null },
                    onClick = entry.onClick,
                    onFocused = {},
                )
            if (index == 0) {
                row.post { row.requestFocus() }
            }
        }
        if (pageCount > 1) {
            addDivider()
            container.addView(createCatalogPaginationBar(page.id, pageCount, currentPage))
        }
        focusFocusableTextAt(1)
    }

    private fun renderNewsHubSection() {
        addLink("home") { openPortalHome() }
        LegacyPortalHomeRenderer.renderNewsHubSection(
            activity = activity,
            container = container,
            catalog = catalog,
            selectedNewsHubCategoryId = selectedNewsHubCategoryId,
            setPendingFocusTag = { pendingFocusTag = it },
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            openSite = { siteId -> openSite(siteId) },
            onCategorySelected = { categoryId ->
                selectedNewsHubCategoryId = categoryId
                render()
            },
            focusViewAndBringIntoView = { view -> focusViewAndBringIntoView(view) },
        )
    }

    private fun renderMenuOverlay() {
        LegacyPortalMenuRenderer.render(
            activity = activity,
            container = overlayContainer,
            entries = activeMenuEntries.map { it.title },
            enabled = activeMenuEntries.map { it.enabled },
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            initialFocusIndex = lastMenuFocusedIndex,
            onSelect = { index ->
                activeMenuEntries.getOrNull(index)?.let { entry ->
                    if (!entry.enabled || entry.action == null) {
                        return@let
                    }
                    activeMenuEntries = emptyList()
                    entry.action.invoke()
                }
            },
            onItemFocused = { index -> lastMenuFocusedIndex = index },
            requestInitialFocus = { view -> focusOverlayMenuItem(view) },
        ) { createdScrollView ->
            menuOverlayScrollView = createdScrollView
        }
    }

    private fun renderSite(siteId: String) {
        val site = catalog.siteById(siteId)
        val section = catalog.sectionById(site.sectionId)
        val skipSiteChrome = siteId == "weather_city" || siteId == "money_rates"

        addPassiveFocusAnchor()
        if (!skipSiteChrome && site.source !is PortalSource.Rss) {
            addLink("home") { openPortalHome() }
            addLink(section.title) { openSection(section.id) }
        }
        if (!skipSiteChrome && shouldRenderSiteSummary(site)) {
            if (site.id == "overclockers_ua") {
                addPlainText(site.title, bold = true, sizeSp = 12f)
                addDivider()
            } else if (site.id == "4pda") {
                addPlainText("4PDA", bold = true, sizeSp = 12f)
                addDivider()
            } else {
                addPlainText(site.summary, sizeSp = 10f, color = COLOR_MUTED)
                addDivider()
            }
        }

        when {
            site.status == SiteStatus.PLANNED -> {
                addSectionHeader("coming soon")
                if (!skipSiteChrome && shouldRenderSiteSummary(site)) {
                    if (site.id == "overclockers_ua") {
                        addPlainText(site.title, bold = true, sizeSp = 12f)
                        addDivider()
                    } else if (site.id == "4pda") {
                        addPlainText("4PDA", bold = true, sizeSp = 12f)
                        addDivider()
                    } else {
                        addPlainText(site.summary, sizeSp = 10f, color = COLOR_MUTED)
                    }
                }
            }

            site.id == "money_rates" -> renderCurrencySite()
            site.id == "weather_city" -> renderWeatherSite()
            site.source is PortalSource.Rss -> renderRssSite(site)
            else -> addPlainText("page unavailable", sizeSp = 10f, color = COLOR_MUTED)
        }
    }

    private fun renderCurrencySite() {
        addHomeSpecHeader(pageTitle = "Rates")
        when {
            ratesState.isLoading -> addPlainText("Loading exchange rates...", sizeSp = 10f, color = COLOR_MUTED)
            ratesState.error != null -> addPlainText(ratesState.error ?: "", color = COLOR_ERROR)
            else -> {
                ratesState.rates.firstOrNull()?.let {
                    addMetaText("Updated ${it.exchangeDate}")
                }
                if (ratesState.rates.isNotEmpty()) {
                    addDivider()
                }
                ratesState.rates.forEach { rate ->
                    addCurrencyRateRow(rate)
                }
                addDivider()
                addPlainText("Converter", bold = true, sizeSp = 11f)
                addMetaText("Cross-rate via UAH (NBU official)")
                val codes = currencyCodesForConverter()
                if (codes.size < 2) {
                    addMetaText("Load rates to use converter")
                } else {
                    currencyConverterFromIndex = currencyConverterFromIndex.coerceIn(0, codes.lastIndex)
                    currencyConverterToIndex = currencyConverterToIndex.coerceIn(0, codes.lastIndex)
                    addPlainText("Amount", sizeSp = 10f, color = COLOR_MUTED)
                    val amountField =
                        EditText(activity).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    bottomMargin = activity.dp(4)
                                }
                            includeFontPadding = false
                            currencyConverterBindingAmount = true
                            setText(currencyConverterAmountText)
                            currencyConverterBindingAmount = false
                            textSize = scaledTextSize(11f)
                            setLineSpacing(0f, compactLineSpacing)
                            setTextColor(COLOR_TEXT)
                            typeface = portalTypeface
                            setPadding(activity.dp(4), activity.dp(3), activity.dp(4), activity.dp(3))
                            background = createHomeRectDrawable(COLOR_HOME_SEARCH_BG, COLOR_HOME_BORDER_LIGHT)
                            setSingleLine(true)
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            tag = CURRENCY_CONVERTER_AMOUNT_TAG
                            isFocusable = true
                            isFocusableInTouchMode = false

                            fun applyFieldFocusState(focused: Boolean) {
                                background =
                                    createHomeRectDrawable(
                                        fillColor = if (focused) COLOR_HOME_FOCUS_LIGHT else COLOR_HOME_SEARCH_BG,
                                        strokeColor = if (focused) COLOR_HOME_FOCUS else COLOR_HOME_BORDER_LIGHT,
                                        strokeWidthDp = if (focused) 2 else 1,
                                    )
                                setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                            }

                            setOnFocusChangeListener { _, hasFocus -> applyFieldFocusState(hasFocus) }
                            applyFieldFocusState(false)
                            addTextChangedListener(
                                object : TextWatcher {
                                    override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int,
                                    ) {}

                                    override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int,
                                    ) {}

                                    override fun afterTextChanged(s: Editable?) {
                                        if (currencyConverterBindingAmount) {
                                            return
                                        }
                                        currencyConverterAmountText = s?.toString().orEmpty()
                                        refreshCurrencyConverterResultView(codes)
                                    }
                                },
                            )
                        }
                    container.addView(amountField)

                    val fromCode = codes[currencyConverterFromIndex]
                    currencyConverterFromView =
                        addCurrencyConverterSelector(
                        tag = CURRENCY_CONVERTER_FROM_TAG,
                        label = "From",
                        codeProvider = { currencyCodesForConverter().getOrElse(currencyConverterFromIndex) { fromCode } },
                        onClick = {
                            currencyConverterFromIndex = cycleCurrencyConverterIndex(currencyConverterFromIndex, codes.size, 1)
                            refreshCurrencyConverterUi(codes)
                        },
                    )
                    val toCode = codes[currencyConverterToIndex]
                    currencyConverterToView =
                        addCurrencyConverterSelector(
                        tag = CURRENCY_CONVERTER_TO_TAG,
                        label = "To",
                        codeProvider = { currencyCodesForConverter().getOrElse(currencyConverterToIndex) { toCode } },
                        onClick = {
                            currencyConverterToIndex = cycleCurrencyConverterIndex(currencyConverterToIndex, codes.size, 1)
                            refreshCurrencyConverterUi(codes)
                        },
                    )
                    currencyConverterResultView =
                        TextView(activity).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    topMargin = activity.dp(2)
                                }
                            includeFontPadding = false
                            textSize = scaledTextSize(12f)
                            setLineSpacing(0f, compactLineSpacing)
                            typeface = portalTypeface
                        }.also(container::addView)
                    refreshCurrencyConverterUi(codes)
                }
                addDivider()
                // Intentionally omitted: internal cache/source metadata.
            }
        }
    }

    private fun currencyCodesForConverter(): List<String> =
        listOf("UAH") + ratesState.rates.map { it.code }

    private fun uahPerUnit(code: String): Double? =
        when (code) {
            "UAH" -> 1.0
            else -> ratesState.rates.firstOrNull { it.code == code }?.rate
        }

    private fun currencyConversionSummary(codes: List<String>): String? {
        val fromCode = codes.getOrNull(currencyConverterFromIndex) ?: return null
        val toCode = codes.getOrNull(currencyConverterToIndex) ?: return null
        val amount =
            currencyConverterAmountText
                .replace(",", ".")
                .trim()
                .toDoubleOrNull()
                ?: return null
        val fromRate = uahPerUnit(fromCode) ?: return null
        val toRate = uahPerUnit(toCode) ?: return null
        val valueUah =
            if (fromCode == "UAH") {
                amount
            } else {
                amount * fromRate
            }
        val result =
            if (toCode == "UAH") {
                valueUah
            } else {
                valueUah / toRate
        }
        return "${amount.format(4).trimEnd('0').trimEnd('.')} $fromCode = ${result.format(4)} $toCode"
    }

    private fun refreshCurrencyConverterUi(codes: List<String> = currencyCodesForConverter()) {
        val fromCode = codes.getOrNull(currencyConverterFromIndex) ?: return
        val toCode = codes.getOrNull(currencyConverterToIndex) ?: return
        currencyConverterFromView?.let { updateCurrencyConverterSelectorView(it, "From", fromCode) }
        currencyConverterToView?.let { updateCurrencyConverterSelectorView(it, "To", toCode) }
        refreshCurrencyConverterResultView(codes)
    }

    private fun refreshCurrencyConverterResultView(codes: List<String> = currencyCodesForConverter()) {
        currencyConverterResultView?.let { view ->
            val summary = currencyConversionSummary(codes)
            view.text = summary ?: "Enter a valid amount"
            view.setTypeface(portalTypeface, if (summary != null) Typeface.BOLD else Typeface.NORMAL)
            view.setTextColor(if (summary != null) COLOR_TEXT else COLOR_MUTED)
        }
    }

    private fun updateCurrencyConverterSelectorView(
        view: TextView,
        label: String,
        code: String,
    ) {
        val focused = view.isFocused
        view.text = buildStyledText("$label: \u2039 $code \u203a", if (focused) Color.WHITE else COLOR_LINK, underline = focused)
        view.setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
        view.background =
            createHomeRectDrawable(
                fillColor = if (focused) COLOR_HOME_FOCUS else Color.TRANSPARENT,
                strokeColor = if (focused) COLOR_HOME_FOCUS else Color.TRANSPARENT,
            )
    }

    private fun addCurrencyRateRow(rate: CurrencyRate) {
        val box = activity.dp(16)
        val row =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = activity.dp(4)
                        bottomMargin = activity.dp(2)
                    }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        val flagAsset = currencyCodeToFlagSvgAssetPath(rate.code)
        val flagSlot: View =
            if (flagAsset != null) {
                ImageView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(box, box).apply {
                            marginEnd = activity.dp(6)
                        }
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setBackgroundColor(COLOR_HOME_CELL)

                    // androidsvg: render 1x1 SVGs into a PictureDrawable.
                    runCatching {
                        val svg = SVG.getFromAsset(activity.assets, flagAsset)
                        val drawable = PictureDrawable(svg.renderToPicture())
                        drawable.setBounds(0, 0, box, box)
                        setImageDrawable(drawable)
                    }
                }
            } else {
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(box, box).apply {
                            marginEnd = activity.dp(6)
                        }
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    text = "\u2022"
                    textSize = scaledTextSize(10f)
                    setLineSpacing(0f, compactLineSpacing)
                    typeface = portalTypeface
                    setBackgroundColor(COLOR_HOME_CELL)
                }
            }

        val label =
            TextView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                includeFontPadding = false
                textSize = scaledTextSize(12f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                setTypeface(portalTypeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
                text = "${rate.name}  ${rate.code}  ${rate.rate.format(4)} UAH"
            }

        row.addView(flagSlot)
        row.addView(label)
        container.addView(row)
    }

    private fun addCurrencyConverterSelector(
        tag: String,
        label: String,
        codeProvider: () -> String,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = activity.dp(2)
                }
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setPadding(activity.dp(3), activity.dp(2), activity.dp(3), activity.dp(2))
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            this.tag = tag

            fun applyState() {
                updateCurrencyConverterSelectorView(this, label, codeProvider())
            }

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, _ -> applyState() }
            applyState()
            container.addView(this)
        }

    private fun currencyCodeToFlagSvgAssetPath(currencyCode: String): String? {
        val iso =
            when (currencyCode.trim().uppercase(Locale.US)) {
                "USD" -> "us"
                "EUR" -> "eu"
                "PLN" -> "pl"
                "GBP" -> "gb"
                "CHF" -> "ch"
                "CZK" -> "cz"
                "CAD" -> "ca"
                "JPY" -> "jp"
                "CNY" -> "cn"
                "UAH" -> "ua"
                else -> null
            }
        return iso?.let { "sites/1x1/${it}.svg" }
    }

    private fun renderWeatherSite() {
        val kyiv = LEGACY_QUICK_CITIES.first()
        if (selectedCity.name != kyiv.name) {
            selectedCity = kyiv
            cityQuery = kyiv.name
            preferences.saveCity(kyiv)
            if (weatherState.data == null && !weatherState.isLoadingWeather) {
                refreshWeather(kyiv)
            }
        }
        addHomeSpecHeader(pageTitle = "Weather")
        addPlainText("Kyiv, UA", bold = true, sizeSp = 12f)
        addMetaText("Daily weather page")
        addDivider()
        when {
            weatherState.isLoadingWeather -> addPlainText("Loading weather...", sizeSp = 10f, color = COLOR_MUTED)
            weatherState.error != null -> addPlainText(weatherState.error ?: "", color = COLOR_ERROR)
            weatherState.data != null -> renderWeatherSnapshot(weatherState.data!!)
        }
    }

    private fun renderWeatherSnapshot(snapshot: WeatherSnapshot) {
        addPlainText("Now  ${snapshot.temperature.format(1)} C", bold = true, sizeSp = 12f)
        addPlainText("Feels like  ${snapshot.apparentTemperature.format(1)} C")
        addPlainText("Wind  ${snapshot.windSpeed.format(1)} km/h")
        addPlainText("Sky  ${weatherCodeToText(snapshot.weatherCode)}")
        addMetaText("Local time: ${snapshot.currentTime}  ${snapshot.timezone}")
        addDivider()
        addPlainText("Forecast", bold = true, sizeSp = 11f)
        snapshot.forecast.forEach { item ->
            addPlainText(
                "${formatDate(item.date)}  ${weatherCodeToText(item.weatherCode)}  " +
                    "${item.minTemperature.format(0)}/${item.maxTemperature.format(0)} C",
            )
        }
    }

    private fun createYumodeGlobalRssSearchRow(): View {
        val searchField =
            EditText(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
                includeFontPadding = false
                setText(yumodeRssSearchQuery)
                setLineSpacing(0f, compactLineSpacing)
                textSize = scaledTextSize(11f)
                setTextColor(COLOR_TEXT)
                typeface = portalTypeface
                setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(1))
                setSingleLine(true)
                background = createHomeRectDrawable(COLOR_HOME_SEARCH_BG, COLOR_HOME_BORDER_LIGHT)
            }

        val findButton =
            TextView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                isFocusable = true
                isClickable = true
                isFocusableInTouchMode = false
                setPadding(activity.dp(6), activity.dp(2), activity.dp(6), activity.dp(2))
                text = "Find"
                fun applyState(focused: Boolean) {
                    setBackgroundColor(if (focused) Color.parseColor("#BDBDBD") else Color.parseColor("#E0E0E0"))
                    setTextColor(if (focused) Color.WHITE else Color.parseColor("#111111"))
                }

                setOnClickListener {
                    performYumodeGlobalRssSearch(searchField.text?.toString().orEmpty())
                }
                setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
                applyState(false)
            }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = activity.dp(2)
                }
            setPadding(activity.dp(2), activity.dp(2), activity.dp(2), 0)
            addView(searchField)
            addView(findButton)
        }
    }

    private fun performYumodeGlobalRssSearch(query: String) {
        val normalized = query.trim()
        yumodeRssSearchQuery = normalized
        yumodeRssSearchShown = normalized.isNotBlank()

        if (normalized.isBlank()) {
            yumodeRssSearchResults = emptyList()
            render()
            return
        }

        val q = normalized.lowercase(Locale.ROOT)
        val hits = ArrayList<YumodeRssSearchHit>(32)
        for (site in catalog.sites) {
            if (site.status != SiteStatus.ACTIVE || site.source !is PortalSource.Rss) {
                continue
            }
            val items = feedStates[site.id]?.items.orEmpty()
            for (item in items) {
                val haystack =
                    buildString {
                        append(item.title)
                        append(' ')
                        append(item.publishedAt ?: "")
                    }
                        .lowercase(Locale.ROOT)
                if (haystack.contains(q)) {
                    hits.add(YumodeRssSearchHit(site, item))
                    if (hits.size >= 32) break
                }
            }
            if (hits.size >= 32) break
        }

        yumodeRssSearchResults = hits
        render()
    }

    private fun renderYumodeRssSearchResults() {
        val q = yumodeRssSearchQuery.trim()
        addPlainText("Поиск", bold = true, sizeSp = 12f)
        addMetaText("query: $q")
        addDivider()

        if (yumodeRssSearchResults.isEmpty()) {
            addPlainText("no matches", sizeSp = 10f, color = COLOR_MUTED)
            return
        }

        yumodeRssSearchResults.forEach { hit ->
            addLink("${hit.site.title}: ${hit.item.title}") {
                openArticle(hit.site, hit.item)
            }
        }
    }

    private fun renderRssSite(site: PortalSite) {
        val rssHeaderBgColor =
            when (site.id) {
                "4pda" -> COLOR_HOME_FOCUS
                "futurism" -> Color.BLACK
                "overclockers_ua" -> overclockersOrange
                else -> COLOR_HEADER
            }

        val rssLinkFocusBgColor =
            when (site.id) {
                "4pda" -> COLOR_HOME_FOCUS
                "futurism" -> COLOR_FOCUS
                "overclockers_ua" -> overclockersOrange
                else -> COLOR_FOCUS
            }

        val rssLinkNormalColor =
            when (site.id) {
                "4pda" -> COLOR_HOME_FOCUS
                "futurism" -> COLOR_FOCUS
                "overclockers_ua" -> overclockersOrange
                else -> COLOR_LINK
            }

        val rssErrorColor =
            when (site.id) {
                "4pda" -> COLOR_HOME_FOCUS
                "futurism" -> COLOR_FOCUS
                "overclockers_ua" -> COLOR_ERROR
                else -> COLOR_ERROR
            }

        val entryFocusColor = rssLinkFocusBgColor
        val entryBadgeColor = rssLinkFocusBgColor
        val entryNormalLinkColor = rssLinkNormalColor

        // ANN / Anime News Network (light-blue dense i-mode)
        when {
            site.id.startsWith("ann") -> {
                renderAnnSiteV2(site)
                return
            }
            site.id == "ain" -> {
                renderAinSite(site)
                return
            }
            site.theme == SiteTheme.ARS -> {
                renderArsSite(site)
                return
            }
            site.theme == SiteTheme.ITC -> {
                renderItcSite(site)
                return
            }
            site.theme == SiteTheme.VERGE -> {
                renderVergeSite(site)
                return
            }
            site.theme == SiteTheme.GAGADGET -> {
                renderGagadgetSite(site)
                return
            }
            site.theme == SiteTheme.MEZHA -> {
                renderMezhaSite(site)
                return
            }
            site.theme == SiteTheme.STOPGAME -> {
                renderStopgameSite(site)
                return
            }
            site.theme == SiteTheme.PLAYGROUND -> {
                renderPlaygroundSite(site)
                return
            }
            LegacyPortalWapRegionalHost.isRegionalFeedTheme(site.theme) -> {
                val state = feedStates[site.id] ?: FeedUiState()
                val filteredItems = filteredFeedItems(site.id, state.items)
                val pagerState =
                    (feedPagerStates[site.id] ?: FeedPagerState())
                        .normalizeFor(filteredItems.size)
                LegacyPortalWapRegionalHost.renderFeed(
                    activity = activity,
                    container = container,
                    site = site,
                    state = state,
                    categories = displayFeedCategories(site, state.items),
                    selectedCategory = selectedFeedCategory(site.id, state.items),
                    filteredItems = filteredItems,
                    pagerState = pagerState,
                    scaledTextSize = ::scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = portalTypeface,
                    onCategorySelected = { category -> openFeedCategory(site.id, category) },
                    onOpenArticle = { item -> openArticle(site, item) },
                    onHeadlineFocused = { index ->
                        feedPagerStates = feedPagerStates + (
                            site.id to (feedPagerStates[site.id] ?: FeedPagerState()).copy(selectedSlot = index)
                                .normalizeFor(filteredItems.size)
                        )
                    },
                )
                return
            }
        }
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = availableFeedCategories(state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(filteredItems.size)
        if (categories.isNotEmpty()) {
            addSectionHeader(
                title = "sections",
                headerBgColor = rssHeaderBgColor,
                headerTextColor = Color.WHITE,
            )
            addLink(
                text = if (selectedCategory == null) "all *" else "all",
                onClick = { selectFeedCategory(site.id, null) },
                normalLinkColor = rssLinkNormalColor,
                focusBackgroundColor = rssLinkFocusBgColor,
            )
            categories.forEach { category ->
                addLink(
                    text = if (category == selectedCategory) "$category *" else category,
                    onClick = { selectFeedCategory(site.id, category) },
                    normalLinkColor = rssLinkNormalColor,
                    focusBackgroundColor = rssLinkFocusBgColor,
                )
            }
            addDivider()
        }
        addSectionHeader(
            title = if (site.id == "4pda") "Новости техники и технологий" else "headlines",
            headerBgColor = rssHeaderBgColor,
            headerTextColor = Color.WHITE,
        )
        when {
            state.isLoading -> addPlainText("loading feed...", sizeSp = 10f, color = COLOR_MUTED)
            state.error != null -> addPlainText(state.error, color = rssErrorColor)
            filteredItems.isEmpty() -> addPlainText("no items", sizeSp = 10f, color = COLOR_MUTED)
            else -> {
                if (selectedCategory != null) {
                    addPlainText(
                        "section $selectedCategory",
                        sizeSp = 10f,
                        color = COLOR_MUTED,
                        verticalPaddingDp = 0,
                        bottomMarginDp = 0,
                    )
                }
                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visibleItems = filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)
                addPlainText(
                    "page ${pagerState.pageIndex + 1}/$pageCount  1-${visibleItems.size} open",
                    sizeSp = 10f,
                    color = COLOR_MUTED,
                    verticalPaddingDp = 0,
                    bottomMarginDp = 0,
                )
                var selectedEntry: View? = null
                visibleItems.forEachIndexed { index, item ->
                    val entry = addFeedEntry(
                        number = index + 1,
                        title = item.title,
                        meta = item.publishedAt?.take(16),
                        onClick = { openArticle(site, item) },
                        onFocused = {
                            feedPagerStates = feedPagerStates + (
                                site.id to pagerState.copy(selectedSlot = index).normalizeFor(filteredItems.size)
                            )
                        },
                        focusedBackgroundColor = entryFocusColor,
                        focusedBadgeColor = entryBadgeColor,
                        normalLinkColor = entryNormalLinkColor,
                    )
                    if (site.theme == SiteTheme.ITC && item.categories.isNotEmpty()) {
                        addPlainText(
                            item.categories.joinToString("  "),
                            sizeSp = 10f,
                            color = COLOR_MUTED,
                            verticalPaddingDp = 0,
                            bottomMarginDp = 1,
                        )
                    }
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = entry
                    }
                }
                selectedEntry?.post { selectedEntry?.requestFocus() }
            }
        }
    }

    private fun renderAnnSiteV2(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val sectionFeeds = legacySectionFeedUrlsForSite(site.id)
        val sections = sectionFeeds?.keys?.toList().orEmpty()
        val selectedSection = feedCategorySelections[site.id]?.trim().takeIf { !it.isNullOrBlank() }
            ?: sections.firstOrNull()
        val selectedTagKey = feedTagSelections[site.id]?.trim()?.lowercase(Locale.ROOT).takeIf { !it.isNullOrBlank() }
        val annPagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(state.items.size)

        fun selectAnnSection(sectionLabel: String) {
            startSiteLoadingOverlay(
                totalDurationMs = siteTransitionDelayMs(),
                preloadAction = {
                    updateRssUiState { current ->
                        val nextCategory =
                            if (sectionLabel.isBlank()) {
                                current.feedCategorySelections - site.id
                            } else {
                                current.feedCategorySelections + (site.id to sectionLabel)
                            }
                        current.copy(
                            feedCategorySelections = nextCategory,
                            feedTagSelections = current.feedTagSelections - site.id,
                            feedPagerStates = resetPagerSelectionForSite(current.feedPagerStates, site.id),
                        )
                    }
                    maybeRefreshMultiSectionSite(site.id)
                },
                action = { render() },
            )
        }

        fun selectAnnTag(tagKey: String?) {
            startSiteLoadingOverlay(
                totalDurationMs = siteTransitionDelayMs(),
                preloadAction = {
                    updateRssUiState { current ->
                        val nextTags =
                            if (tagKey.isNullOrBlank()) {
                                current.feedTagSelections - site.id
                            } else {
                                current.feedTagSelections + (site.id to tagKey)
                            }
                        current.copy(
                            feedTagSelections = nextTags,
                            feedPagerStates = resetPagerSelectionForSite(current.feedPagerStates, site.id),
                        )
                    }
                },
                action = { render() },
            )
        }
        LegacyPortalAnnSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            sections = sections,
            selectedSection = selectedSection,
            selectedTagKey = selectedTagKey,
            pagerState = annPagerState,
            pageSize = FeedPageSize,
            compactLineSpacing = compactLineSpacing,
            portalTypeface = portalTypeface,
            buildCategoryFlowBar = { siteId, categories, selectedCategory, normalColor, highlightColor, focusBackground, lineSpacing, includeAll, center, onCategorySelected ->
                buildCategoryFlowBar(
                    siteId = siteId,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    normalColor = normalColor,
                    highlightColor = highlightColor,
                    focusBackground = focusBackground,
                    lineSpacing = lineSpacing,
                    includeAll = includeAll,
                    center = center,
                    onCategorySelected = onCategorySelected,
                )
            },
            normalizeCategoryKey = { it.categoryKey() },
            onSelectSection = { selectAnnSection(it) },
            onSelectTag = { selectAnnTag(it) },
            filterFeedSearch = { siteId, items -> applyFeedSearch(siteId, items) },
            createHeadlineEntry = { number, title, date, onClick, onFocused ->
                addAnnFeedEntryV2(number, title, date, onClick, onFocused)
            },
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index, totalCount ->
                feedPagerStates = feedPagerStates + (
                    site.id to annPagerState.copy(selectedSlot = index).normalizeFor(totalCount)
                )
            },
            addPlainText = { text, sizeSp, color, verticalPaddingDp, bottomMarginDp ->
                addPlainText(
                    text = text,
                    sizeSp = sizeSp,
                    color = color,
                    verticalPaddingDp = verticalPaddingDp,
                    bottomMarginDp = bottomMarginDp,
                )
            },
            requestFocus = { selectedEntry ->
                selectedEntry?.post { selectedEntry.requestFocus() }
            },
        )
    }

    private fun addAnnFeedEntryV2(
        number: Int,
        title: String,
        dateText: String,
        onClick: () -> Unit,
        onFocused: () -> Unit,
    ): View =
        LinearLayout(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(activity.dp(6), 0, activity.dp(6), 0)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            setBackgroundColor(Color.TRANSPARENT)

            val badgeSize = activity.dp(18)

            val badgeView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize)
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = scaledTextSize(9f)
                setTypeface(portalTypeface, Typeface.BOLD)
                text = number.toString()
                paint.isUnderlineText = false
            }

            val textWrap =
                LinearLayout(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        ).apply { leftMargin = activity.dp(6) }
                    orientation = LinearLayout.VERTICAL
                }

            val titleView = TextView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                includeFontPadding = false
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                paint.isUnderlineText = true
                text = title
                setTextColor(COLOR_ANN_TILE_TEXT)
            }

            val dateView = TextView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = activity.dp(1) }
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                setTextColor(COLOR_ANN_META_TEXT)
                text = dateText
            }

            fun applyState(focused: Boolean) {
                val active = focused
                setBackgroundColor(if (active) COLOR_ANN_SECTION_BAR else Color.TRANSPARENT)
                badgeView.background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 0f
                        setColor(if (active) COLOR_ANN_TILE_BG_FOCUS else Color.TRANSPARENT)
                        setStroke(activity.dp(1), if (active) COLOR_ANN_TILE_BG_FOCUS else COLOR_ANN_TAG_BORDER)
                    }
                badgeView.setTextColor(if (active) COLOR_ANN_TILE_TEXT_FOCUS else COLOR_ANN_TILE_TEXT)
                titleView.setTextColor(if (active) Color.WHITE else COLOR_ANN_TILE_TEXT)
                dateView.setTextColor(if (active) Color.WHITE else COLOR_ANN_META_TEXT)
                titleView.setTypeface(portalTypeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            }

            addView(badgeView)
            textWrap.addView(titleView)
            textWrap.addView(dateView)
            addView(textWrap)

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                if (hasFocus) onFocused()
            }
            applyState(false)

            container.addView(this)
        }

    private fun renderAnnArticle(screen: PortalScreen.Article) {
        val site = catalog.siteById(screen.siteId)
        val state = feedStates[site.id] ?: FeedUiState()
        val sectionFeeds = legacySectionFeedUrlsForSite(site.id)
        val sections = sectionFeeds?.keys?.toList().orEmpty()
        val selectedSection = feedCategorySelections[site.id]?.trim().takeIf { !it.isNullOrBlank() } ?: sections.firstOrNull()
        val selectedTagKey = feedTagSelections[site.id]?.trim()?.lowercase(Locale.ROOT).takeIf { !it.isNullOrBlank() }
        LegacyPortalAnnArticleRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            articleState = articleState,
            sections = sections,
            selectedSection = selectedSection,
            selectedTagKey = selectedTagKey,
            compactLineSpacing = compactLineSpacing,
            portalTypeface = portalTypeface,
            buildCategoryFlowBar = { siteId, categories, selectedCategory, normalColor, highlightColor, focusBackground, lineSpacing, includeAll, center, onCategorySelected ->
                buildCategoryFlowBar(
                    siteId = siteId,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    normalColor = normalColor,
                    highlightColor = highlightColor,
                    focusBackground = focusBackground,
                    lineSpacing = lineSpacing,
                    includeAll = includeAll,
                    center = center,
                    onCategorySelected = onCategorySelected,
                )
            },
            normalizeCategoryKey = { it.categoryKey() },
            onSelectSection = { label ->
                updateRssUiState { current ->
                    val nextCategory =
                        if (label.isNullOrBlank()) {
                            current.feedCategorySelections - site.id
                        } else {
                            current.feedCategorySelections + (site.id to label)
                        }
                    current.copy(
                        feedCategorySelections = nextCategory,
                        feedTagSelections = current.feedTagSelections - site.id,
                        feedPagerStates = resetPagerSelectionForSite(current.feedPagerStates, site.id),
                    )
                }
                maybeRefreshMultiSectionSite(site.id)
                setScreen(PortalScreen.Site(site.id))
            },
            onSelectTag = { key ->
                updateRssUiState { current ->
                    current.copy(
                        feedTagSelections =
                            if (key == null) {
                                current.feedTagSelections - site.id
                            } else {
                                current.feedTagSelections + (site.id to key)
                            },
                    )
                }
                setScreen(PortalScreen.Site(site.id))
            },
            addMetaText = { text, color -> addMetaText(text, color = color) },
            addArticleText = { text, bold -> addArticleText(text, bold = bold) },
            addDivider = { addDivider() },
            restoreArticleScrollAndFocus = {
                val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0
                val preferred = findFirstFocusableDescendant(container)
                if (preferred != null) {
                    restoreArticleScrollAndFocus(
                        scrollY = scroll,
                        preferredFocus = preferred,
                        articleScrollFocus = preferred,
                    )
                }
            },
        )
    }

    private fun renderAinSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = availableFeedCategories(state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(filteredItems.size)

        LegacyPortalAinSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            buildCategoryFlowBar = { siteId, cats, selected, normalColor, highlightColor, focusBackground, lineSpacing, includeAll, center ->
                buildCategoryFlowBar(
                    siteId = siteId,
                    categories = cats,
                    selectedCategory = selected,
                    normalColor = normalColor,
                    highlightColor = highlightColor,
                    focusBackground = focusBackground,
                    lineSpacing = lineSpacing,
                    includeAll = includeAll,
                    center = center,
                )
            },
            scaledTextSize = { value -> scaledTextSize(value) },
            typeface = portalTypeface,
            lineSpacing = compactLineSpacing,
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to pagerState.copy(selectedSlot = index).normalizeFor(filteredItems.size)
                )
            },
            onPageSelected = { pageIndex ->
                startSiteLoadingOverlay(siteTransitionDelayMs()) {
                    val currentPagerState = feedPagerStates[site.id] ?: FeedPagerState()
                    feedPagerStates = feedPagerStates + (
                        site.id to currentPagerState.copy(pageIndex = pageIndex, selectedSlot = 0).normalizeFor(filteredItems.size)
                    )
                    render()
                }
            },
        )
    }

    private fun renderAinArticle(screen: PortalScreen.Article) {
        val categories = availableFeedCategories(feedStates[screen.siteId]?.items.orEmpty())
        val selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty())
        val brandedHeader =
            LegacyPortalAinSiteRenderer.buildBrandedHeader(
                activity = activity,
                siteId = screen.siteId,
                categories = categories,
                selectedCategory = selectedCategory,
                scaledTextSize = ::scaledTextSize,
                lineSpacing = compactLineSpacing,
                typeface = portalTypeface,
                buildCategoryFlowBar = { siteId, cats, selected, normalColor, highlightColor, focusBackground, lineSpacing, includeAll, center ->
                    buildCategoryFlowBar(
                        siteId = siteId,
                        categories = cats,
                        selectedCategory = selected,
                        normalColor = normalColor,
                        highlightColor = highlightColor,
                        focusBackground = focusBackground,
                        lineSpacing = lineSpacing,
                        includeAll = includeAll,
                        center = center,
                        onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
                    )
                },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = LegacyPortalAinSiteRenderer.COLOR_AIN_ACCENT,
                focusedColor = Color.WHITE,
                focusBackground = LegacyPortalAinSiteRenderer.COLOR_AIN_ACCENT,
                lineSpacing = compactLineSpacing,
                underlineOnlyWhenFocused = true,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0

        LegacyPortalAinArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            brandedHeader = brandedHeader,
            articleLink = articleLink,
            scaledTextSize = ::scaledTextSize,
            compactLineSpacing = compactLineSpacing,
            typeface = portalTypeface,
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderArsSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = availableFeedCategories(state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = normalizePagerState(
            itemCount = filteredItems.size,
            pageSize = ARS_FEED_PAGE_SIZE,
            state = feedPagerStates[site.id] ?: FeedPagerState(),
        )

        LegacyPortalArsSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            currentSearchQuery = currentFeedSearchQuery(site.id),
            onRequestSearchQuery = { query -> setFeedSearchQuery(site.id, query) },
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to normalizePagerState(
                        itemCount = filteredItems.size,
                        pageSize = ARS_FEED_PAGE_SIZE,
                        state = pagerState.copy(selectedSlot = index),
                    )
                )
            },
            onPageSelected = { pageIndex ->
                startSiteLoadingOverlay(siteTransitionDelayMs()) {
                    val currentPagerState = feedPagerStates[site.id] ?: FeedPagerState()
                    feedPagerStates = feedPagerStates + (
                        site.id to normalizePagerState(
                            itemCount = filteredItems.size,
                            pageSize = ARS_FEED_PAGE_SIZE,
                            state = currentPagerState.copy(pageIndex = pageIndex, selectedSlot = 0),
                        )
                    )
                    render()
                }
            },
            buildArsSearchRow = { siteId, onSubmit ->
                buildArsSearchRow(siteId, onSubmit)
            },
            buildArsLogoView = { buildArsLogoView() },
            buildArsCategoryBar = { siteId, cats, selected ->
                buildArsCategoryBar(
                    siteId = siteId,
                    categories = cats,
                    selectedCategory = selected,
                )
            },
            createArsMetaText = { text, color ->
                createArsMetaText(text, color)
            },
            scaledTextSize = { value -> scaledTextSize(value) },
            lineSpacing = compactLineSpacing,
            typeface = portalTypeface,
            createArsFooterText = { text ->
                createArsFooterText(text)
            },
        )
    }

    private fun renderItcSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = availableFeedCategories(state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(filteredItems.size)

        LegacyPortalItcSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            shouldRenderSiteSummary = shouldRenderSiteSummary(site),
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            onRequestSearchQuery = { query ->
                setFeedSearchQuery(site.id, query)
            },
            onOpenArticle = { item ->
                openArticle(site, item)
            },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to pagerState.copy(selectedSlot = index).normalizeFor(filteredItems.size)
                )
            },
            formatItcDate = { value -> formatItcDate(value) },
            buildItcSearchRow = { siteId, onSubmit ->
                buildItcSearchRow(siteId, onSubmit)
            },
            buildItcLogoView = { buildItcLogoView() },
            buildItcCategoryLine = { siteId, cats, selected ->
                buildItcCategoryLine(siteId, cats, selected)
            },
            createItcMetaText = { text, color -> createItcMetaText(text, color) },
            addItcFeedEntry = { number, title, meta, onClick, onFocused ->
                addItcFeedEntry(
                    number = number,
                    title = title,
                    meta = meta,
                    onClick = onClick,
                    onFocused = onFocused,
                )
            },
            createItcFooterText = { text -> createItcFooterText(text) },
            buildItcPaginationBar = { siteId, pageCount, selectedPage ->
                buildItcPaginationBar(siteId, pageCount, selectedPage)
            },
        )
    }

    private fun renderVergeSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = availableFeedCategories(state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(filteredItems.size)

        LegacyPortalVergeSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to pagerState.copy(selectedSlot = index).normalizeFor(filteredItems.size)
                )
            },
            createVergeLogoText = { createVergeLogoText() },
            buildCategoryFlowBar = { siteId, cats, selected, normalColor, highlightColor, focusBg, spacing, includeAll, center ->
                buildCategoryFlowBar(
                    siteId = siteId,
                    categories = cats,
                    selectedCategory = selected,
                    normalColor = normalColor,
                    highlightColor = highlightColor,
                    focusBackground = focusBg,
                    lineSpacing = spacing,
                    includeAll = includeAll,
                    center = center,
                )
            },
            createVergeMetaText = { text, color -> createVergeMetaText(text, color) },
            createVergeFeedEntry = { number, title, meta, onClick, onFocused ->
                createVergeFeedEntry(
                    number = number,
                    title = title,
                    meta = meta,
                    onClick = onClick,
                    onFocused = onFocused,
                )
            },
            buildVergePaginationBar = { siteId, pageCount, selectedPage ->
                buildVergePaginationBar(siteId, pageCount, selectedPage)
            },
        )
    }

    private fun renderGagadgetSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = displayFeedCategories(site, state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = normalizePagerState(
            itemCount = filteredItems.size,
            pageSize = GAGADGET_FEED_PAGE_SIZE,
            state = feedPagerStates[site.id] ?: FeedPagerState(),
        )

        LegacyPortalGagadgetSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to normalizePagerState(
                        itemCount = filteredItems.size,
                        pageSize = GAGADGET_FEED_PAGE_SIZE,
                        state = pagerState.copy(selectedSlot = index),
                    )
                )
            },
            buildGagadgetLogoView = { buildGagadgetLogoView() },
            buildCategoryFlowBar = { siteId, cats, selected, normalColor, highlightColor, focusBg, spacing, includeAll, center ->
                buildCategoryFlowBar(
                    siteId = siteId,
                    categories = cats,
                    selectedCategory = selected,
                    normalColor = normalColor,
                    highlightColor = highlightColor,
                    focusBackground = focusBg,
                    lineSpacing = spacing,
                    includeAll = includeAll,
                    center = center,
                )
            },
            createGagadgetMetaText = { text, color -> createGagadgetMetaText(text, color) },
            scaledTextSize = { value -> scaledTextSize(value) },
            typeface = portalTypeface,
            onPageSelected = { pageIndex ->
                startSiteLoadingOverlay(siteTransitionDelayMs()) {
                    val currentPagerState = feedPagerStates[site.id] ?: FeedPagerState()
                    feedPagerStates = feedPagerStates + (
                        site.id to normalizePagerState(
                            itemCount = (feedStates[site.id]?.items.orEmpty()).size,
                            pageSize = GAGADGET_FEED_PAGE_SIZE,
                            state = currentPagerState.copy(pageIndex = pageIndex, selectedSlot = 0),
                        )
                    )
                    render()
                }
            },
        )
    }

    private fun renderMezhaSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = displayFeedCategories(site, state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(filteredItems.size)

        LegacyPortalMezhaSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to pagerState.copy(selectedSlot = index).normalizeFor(filteredItems.size)
                )
            },
            createMezhaLogoText = { createMezhaLogoText() },
            buildCategoryFlowBar = { siteId, cats, selected, normalColor, highlightColor, focusBg, spacing, includeAll, center ->
                buildCategoryFlowBar(
                    siteId = siteId,
                    categories = cats,
                    selectedCategory = selected,
                    normalColor = normalColor,
                    highlightColor = highlightColor,
                    focusBackground = focusBg,
                    lineSpacing = spacing,
                    includeAll = includeAll,
                    center = center,
                )
            },
            createMezhaMetaText = { text, color -> createMezhaMetaText(text, color) },
            scaledTextSize = { value -> scaledTextSize(value) },
            typeface = portalTypeface,
            lineSpacing = compactLineSpacing,
            onPageSelected = { pageIndex ->
                startSiteLoadingOverlay(siteTransitionDelayMs()) {
                    val currentPagerState = feedPagerStates[site.id] ?: FeedPagerState()
                    feedPagerStates = feedPagerStates + (
                        site.id to currentPagerState.copy(pageIndex = pageIndex, selectedSlot = 0)
                            .normalizeFor((feedStates[site.id]?.items.orEmpty()).size)
                    )
                    render()
                }
            },
        )
    }

    private fun renderStopgameSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = displayFeedCategories(site, state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(filteredItems.size)
        LegacyPortalStopgameSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            buildLogo = {
                LegacyPortalStopgameSiteRenderer.buildLogoView(
                    activity = activity,
                    scaledTextSize = { value -> scaledTextSize(value) },
                    compactLineSpacing = compactLineSpacing,
                    typeface = portalTypeface,
                )
            },
            scaledTextSize = ::scaledTextSize,
            compactLineSpacing = compactLineSpacing,
            typeface = portalTypeface,
            onCategorySelected = { category -> selectFeedCategory(site.id, category) },
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to (feedPagerStates[site.id] ?: FeedPagerState()).copy(selectedSlot = index)
                        .normalizeFor(filteredItems.size)
                )
            },
        )
    }

    private fun renderPlaygroundSite(site: PortalSite) {
        val state = feedStates[site.id] ?: FeedUiState()
        val categories = displayFeedCategories(site, state.items)
        val selectedCategory = selectedFeedCategory(site.id, state.items)
        val filteredItems = filteredFeedItems(site.id, state.items)
        val pagerState = (feedPagerStates[site.id] ?: FeedPagerState()).normalizeFor(filteredItems.size)
        LegacyPortalPlaygroundSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            buildLogo = {
                LegacyPortalPlaygroundSiteRenderer.buildLogoView(
                    activity = activity,
                    scaledTextSize = { value -> scaledTextSize(value) },
                    compactLineSpacing = playgroundLineSpacing,
                    typeface = portalTypeface,
                )
            },
            scaledTextSize = ::scaledTextSize,
            compactLineSpacing = playgroundLineSpacing,
            typeface = portalTypeface,
            onCategorySelected = { category -> selectFeedCategory(site.id, category) },
            onOpenArticle = { item -> openArticle(site, item) },
            onHeadlineFocused = { index ->
                feedPagerStates = feedPagerStates + (
                    site.id to (feedPagerStates[site.id] ?: FeedPagerState()).copy(selectedSlot = index)
                        .normalizeFor(filteredItems.size)
                )
            },
        )
    }

    private fun selectFeedCategory(
        siteId: String,
        category: String?,
    ) {
        startSiteLoadingOverlay(
            totalDurationMs = siteTransitionDelayMs(),
            preloadAction = {
                updateFeedCategorySelection(siteId, category)
                if (legacySectionFeedUrlsForSite(siteId) != null) {
                    maybeRefreshMultiSectionSite(siteId)
                }
            },
            action = {
                render()
            },
        )
    }

    private fun updateFeedCategorySelection(
        siteId: String,
        category: String?,
    ) {
        updateRssUiState { current ->
            val nextCategory =
                if (category.isNullOrBlank()) {
                    current.feedCategorySelections - siteId
                } else {
                    current.feedCategorySelections + (siteId to category)
                }
            current.copy(
                feedCategorySelections = nextCategory,
                feedPagerStates = resetPagerSelectionForSite(current.feedPagerStates, siteId),
            )
        }
    }

    private fun openFeedCategory(
        siteId: String,
        category: String?,
    ) {
        startSiteLoadingOverlay(
            totalDurationMs = siteTransitionDelayMs(),
            preloadAction = {
                updateFeedCategorySelection(siteId, category)
                maybeRefreshMultiSectionSite(siteId)
            },
            action = { setScreen(PortalScreen.Site(siteId)) },
        )
    }

    private fun updatePortalFont(fontId: String) {
        preferences.savePortalFontId(fontId)
        applyPortalFont(fontId)
        render()
    }

    private fun applyPortalFont(fontId: String) {
        portalTypeface = resolvePortalTypeface(fontId)
        portalTextSizeScale = resolvePortalTextSizeScale(fontId)
    }

    private fun resolvePortalTypeface(fontId: String): Typeface =
        when (fontId) {
            FONT_ID_LANA_PIXEL -> loadLanaPixelTypeface() ?: Typeface.MONOSPACE
            else -> Typeface.MONOSPACE
        }

    private fun resolvePortalTextSizeScale(fontId: String): Float =
        when (fontId) {
            FONT_ID_LANA_PIXEL -> LANA_PIXEL_TEXT_SCALE
            else -> 1f
        }

    private fun loadLanaPixelTypeface(): Typeface? {
        lanaPixelTypeface?.let { return it }
        return runCatching {
            Typeface.createFromAsset(activity.assets, LANA_PIXEL_FONT_ASSET)
        }.getOrNull()?.also { loaded ->
            lanaPixelTypeface = loaded
        }
    }

    private fun scaledTextSize(sizeSp: Float): Float =
        sizeSp * portalTextSizeScale

    private fun setFeedSearchQuery(
        siteId: String,
        query: String,
    ) {
        val normalized = query.trim()
        if (normalized == currentFeedSearchQuery(siteId)) {
            return
        }
        updateRssUiState { current ->
            val nextSearch =
                if (normalized.isBlank()) {
                    current.feedSearchQueries - siteId
                } else {
                    current.feedSearchQueries + (siteId to normalized)
                }
            current.copy(
                feedSearchQueries = nextSearch,
                feedPagerStates = resetPagerSelectionForSite(current.feedPagerStates, siteId),
            )
        }
        render()
    }

    private fun selectedFeedCategory(
        siteId: String,
        items: List<FeedItem>,
    ): String? {
        val selectedCategory = feedCategorySelections[siteId] ?: return null
        val site = catalog.siteById(siteId)
        return displayFeedCategories(site, items)
            .firstOrNull { category -> category.categoryKey() == selectedCategory.categoryKey() }
    }

    private fun filteredFeedItems(
        siteId: String,
        items: List<FeedItem>,
    ): List<FeedItem> {
        val site = catalog.siteById(siteId)
        val selectedCategory = selectedFeedCategory(siteId, items)
        val categoryItems =
            if (selectedCategory == null) {
                items
            } else {
                items.filter { item -> itemMatchesSelectedCategory(site, item, selectedCategory) }
        }
        return applyFeedSearch(siteId, categoryItems)
    }

    private fun navigableCategoriesFor(
        site: PortalSite,
        items: List<FeedItem>,
    ): List<String?> =
        when (site.theme) {
            SiteTheme.ARS -> listOf(null) + availableFeedCategories(items)
            SiteTheme.ITC -> {
                val raw = availableFeedCategories(items).take(2)
                val sorted = raw.sortedWith(compareBy { it.lowercase(Locale.ROOT).contains("РїР°СЂС‚РЅРµСЂ") })
                if (sorted.isEmpty()) listOf(null) else sorted
            }
            SiteTheme.VERGE -> listOf(null) + availableFeedCategories(items)
            SiteTheme.GAGADGET -> displayFeedCategories(site, items)
            SiteTheme.MEZHA -> displayFeedCategories(site, items)
            SiteTheme.STOPGAME,
            SiteTheme.PLAYGROUND,
            SiteTheme.KYIV_VLADA,
            SiteTheme.UA_44,
            SiteTheme.VGORODE,
            -> displayFeedCategories(site, items)

            else -> listOf(null) + availableFeedCategories(items)
        }

    private fun displayFeedCategories(
        site: PortalSite,
        items: List<FeedItem>,
    ): List<String> =
        when (site.theme) {
            SiteTheme.GAGADGET -> gagadgetFeedSections()
            else -> legacySectionFeedUrlsForSite(site.id)?.keys?.toList() ?: availableFeedCategories(items)
        }

    private fun siteForSelectedFeedSection(site: PortalSite): PortalSite {
        val source = site.source as? PortalSource.Rss ?: return site
        val sectionFeeds = legacySectionFeedUrlsForSite(site.id) ?: return site
        val selectedSection = feedCategorySelections[site.id]
        val sectionUrl = sectionFeeds[selectedSection] ?: sectionFeeds.values.firstOrNull() ?: return site
        return site.copy(
            source = source.copy(feedUrl = sectionUrl),
        )
    }

    private fun maybeRefreshMultiSectionSite(siteId: String) {
        val sectionFeeds = legacySectionFeedUrlsForSite(siteId) ?: return
        val site = catalog.siteById(siteId)
        val selected = feedCategorySelections[siteId]
        if (selected == null || !sectionFeeds.containsKey(selected)) {
            return
        }
        refreshFeed(site)
    }

    private fun itemMatchesSelectedCategory(
        site: PortalSite,
        item: FeedItem,
        selectedCategory: String,
    ): Boolean =
        when (site.theme) {
            SiteTheme.GAGADGET -> {
                val selectedKey = selectedCategory.categoryKey()
                item.categories.any { category -> category.categoryKey() == selectedKey }
            }

            else ->
                if (legacySectionFeedUrlsForSite(site.id) != null) {
                    true
                } else {
                    item.primaryCategory() == selectedCategory
                }

        }

    private fun String.categoryKey(): String =
        trim().lowercase(Locale.ROOT)

    private fun applyFeedSearch(
        siteId: String,
        items: List<FeedItem>,
    ): List<FeedItem> {
        val query = currentFeedSearchQuery(siteId)
        if (query.isBlank()) {
            return items
        }
        val needle = query.lowercase(Locale.ROOT)
        return items.filter { item ->
            item.title.lowercase(Locale.ROOT).contains(needle) ||
                item.summary.orEmpty().lowercase(Locale.ROOT).contains(needle) ||
                item.categories.any { category -> category.lowercase(Locale.ROOT).contains(needle) }
        }
    }

    private fun currentFeedSearchQuery(siteId: String): String =
        feedSearchQueries[siteId].orEmpty()

    private fun normalizePagerState(
        itemCount: Int,
        pageSize: Int,
        state: FeedPagerState,
    ): FeedPagerState {
        if (itemCount <= 0) {
            return FeedPagerState()
        }
        val maxPageIndex = ((itemCount - 1) / pageSize).coerceAtLeast(0)
        val normalizedPage = state.pageIndex.coerceIn(0, maxPageIndex)
        val visibleCount = (itemCount - normalizedPage * pageSize).coerceAtMost(pageSize)
        val normalizedSlot = state.selectedSlot.coerceIn(0, (visibleCount - 1).coerceAtLeast(0))
        return state.copy(pageIndex = normalizedPage, selectedSlot = normalizedSlot)
    }

    private fun FeedPagerState.normalizeFor(itemCount: Int): FeedPagerState =
        normalizePagerState(
            itemCount = itemCount,
            pageSize = FeedPageSize,
            state = this,
        )

    private fun availableFeedCategories(items: List<FeedItem>): List<String> =
        buildList {
            val seen = linkedSetOf<String>()
            items.forEach { item ->
                val category = item.primaryCategory()
                val categoryKey = category?.categoryKey()
                if (!category.isNullOrBlank() && categoryKey != null && seen.add(categoryKey)) {
                    add(category)
                }
            }
        }

    private fun renderArticle(screen: PortalScreen.Article) {
        val site = catalog.siteById(screen.siteId)
        when {
            site.id.startsWith("ann") -> {
                renderAnnArticle(screen)
                return
            }
            site.id == "ain" -> {
                renderAinArticle(screen)
                return
            }
            site.theme == SiteTheme.ARS -> {
                renderArsArticle(screen)
                return
            }
            site.theme == SiteTheme.ITC -> {
                renderItcArticle(screen)
                return
            }
            site.theme == SiteTheme.VERGE -> {
                renderVergeArticle(screen)
                return
            }
            site.theme == SiteTheme.GAGADGET -> {
                renderGagadgetArticle(screen)
                return
            }
            site.theme == SiteTheme.MEZHA -> {
                renderMezhaArticle(screen)
                return
            }
            site.theme == SiteTheme.STOPGAME -> {
                renderStopgameArticle(screen)
                return
            }
            site.theme == SiteTheme.PLAYGROUND -> {
                renderPlaygroundArticle(screen)
                return
            }
            LegacyPortalWapRegionalHost.isRegionalFeedTheme(site.theme) -> {
                val (palette, brandedHeader) =
                    LegacyPortalWapRegionalHost.buildArticleChrome(
                        activity = activity,
                        site = site,
                        categories = displayFeedCategories(site, feedStates[screen.siteId]?.items.orEmpty()),
                        selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty()),
                        scaledTextSize = ::scaledTextSize,
                        compactLineSpacing = compactLineSpacing,
                        typeface = portalTypeface,
                        onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
                    )
                val articleLink =
                    createArticleLink(
                        label = "",
                        normalColor = palette.text,
                        focusedColor = Color.WHITE,
                        focusBackground = palette.focus,
                        lineSpacing = compactLineSpacing,
                        underlineOnlyWhenFocused = true,
                    )
                val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0
                LegacyPortalWapRegionalArticleRenderer.render(
                    activity = activity,
                    container = container,
                    articleState = articleState,
                    palette = palette,
                    brandedHeader = brandedHeader,
                    articleLink = articleLink,
                    scaledTextSize = ::scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = portalTypeface,
                    centerContent = site.theme == SiteTheme.KYIV_VLADA || site.theme == SiteTheme.UA_44,
                    restoreScrollAndFocus = { y, preferred, scrollFocus ->
                        restoreArticleScrollAndFocus(y, preferred, scrollFocus)
                    },
                    scrollY = scroll,
                )
                return
            }
        }
        if (site.id == "overclockers_ua") {
            addPlainText(site.title, bold = true, sizeSp = 12f)
        }
        when {
            articleState.isLoading -> addMetaText("loading article...")
            articleState.error != null -> addMetaText(articleState.error ?: "", color = COLOR_ERROR)
            articleState.article == null -> addMetaText("article unavailable")
            else -> {
                val article = articleState.article!!
                val articleLink =
                    addArticleLink(
                        label = "",
                        normalColor = if (site.id == "overclockers_ua") overclockersOrange else COLOR_LINK,
                        focusedColor = Color.WHITE,
                        focusBackground = if (site.id == "overclockers_ua") overclockersOrange else COLOR_FOCUS,
                    )
                addArticleText(article.title, bold = true)
                article.publishedAt?.let { addMetaText(it) }
                addMetaText("host ${article.sourceHost}")
                addDivider()
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING -> addArticleText(block.text, bold = true)
                        ArticleBlockType.LIST_ITEM -> addArticleText("• ${block.text}")
                        ArticleBlockType.PARAGRAPH -> addArticleText(block.text)
                    }
                }
                val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0
                restoreArticleScrollAndFocus(
                    scrollY = scroll,
                    preferredFocus = articleLink,
                    articleScrollFocus = articleLink,
                )
            }
        }
    }

    private fun renderArsArticle(screen: PortalScreen.Article) {
        val (_, searchRow) = buildArsSearchRow(screen.siteId) { query ->
            setFeedSearchQuery(screen.siteId, query)
            setScreen(PortalScreen.Site(screen.siteId))
        }
        val categoryBar =
            buildArsCategoryBar(
                siteId = screen.siteId,
                categories = availableFeedCategories(feedStates[screen.siteId]?.items.orEmpty()),
                selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty()),
                onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = COLOR_LINK,
                focusedColor = Color.WHITE,
                focusBackground = COLOR_FOCUS,
                lineSpacing = compactLineSpacing,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0

        LegacyPortalArsArticleRenderer.render(
            activity = activity,
            container = container,
            site = catalog.siteById(screen.siteId),
            siteId = screen.siteId,
            articleState = articleState,
            categoryBar = categoryBar,
            articleLink = articleLink,
            searchRow = searchRow,
            buildArsLogoView = { buildArsLogoView() },
            createArsMetaText = { text, color -> createArsMetaText(text, color) },
            createArsArticleText = { text, bold -> createArsArticleText(text, bold) },
            dividerView = {
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        activity.dp(1),
                    ).apply {
                        topMargin = activity.dp(2)
                        bottomMargin = activity.dp(2)
                    }
                    setBackgroundColor(COLOR_DIVIDER)
                }
            },
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderItcArticle(screen: PortalScreen.Article) {
        val (_, searchRow) = buildItcSearchRow(screen.siteId) { query ->
            setFeedSearchQuery(screen.siteId, query)
            setScreen(PortalScreen.Site(screen.siteId))
        }
        val categoryLine =
            buildItcCategoryLine(
                screen.siteId,
                availableFeedCategories(feedStates[screen.siteId]?.items.orEmpty()),
                selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty()),
                onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = COLOR_ITC_ACCENT,
                focusedColor = Color.WHITE,
                focusBackground = COLOR_ITC_ACCENT,
                lineSpacing = compactLineSpacing,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0
        val published = articleState.article?.publishedAt?.let(::formatItcDate)
        val tagLine = screen.feedItem.categories.takeIf { it.isNotEmpty() }?.let { createItcTagLine(it) }

        LegacyPortalItcArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            publishedAtLabel = published,
            categoryLine = categoryLine,
            articleLink = articleLink,
            searchRow = searchRow,
            buildItcLogoView = { buildItcLogoView() },
            createItcMetaText = { text, color -> createItcMetaText(text, color) },
            createItcArticleText = { text, bold -> createItcArticleText(text, bold) },
            dividerView = {
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        activity.dp(1),
                    ).apply {
                        topMargin = activity.dp(2)
                        bottomMargin = activity.dp(2)
                    }
                    setBackgroundColor(COLOR_DIVIDER)
                }
            },
            tagLine = tagLine,
            footerView = createItcFooterText("itc.ua"),
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderVergeArticle(screen: PortalScreen.Article) {
        val site = catalog.siteById(screen.siteId)
        val categoryBar =
            buildCategoryFlowBar(
                siteId = screen.siteId,
                categories = displayFeedCategories(site, feedStates[screen.siteId]?.items.orEmpty()),
                selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty()),
                normalColor = COLOR_VERGE_GLOW,
                highlightColor = COLOR_VERGE_ACCENT,
                focusBackground = COLOR_VERGE_PANEL,
                lineSpacing = VERGE_LINE_SPACING,
                includeAll = true,
                center = false,
                onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = COLOR_VERGE_GLOW,
                focusedColor = COLOR_VERGE_TEXT,
                focusBackground = COLOR_VERGE_PANEL,
                lineSpacing = VERGE_LINE_SPACING,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0

        LegacyPortalVergeArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            categoryBar = categoryBar,
            articleLink = articleLink,
            createVergeLogoText = { createVergeLogoText() },
            createVergeMetaText = { text, color -> createVergeMetaText(text, color) },
            createVergeTitleText = { text -> createVergeTitleText(text) },
            createVergeArticleText = { text, bold -> createVergeArticleText(text, bold) },
            dividerView = {
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        activity.dp(1),
                    ).apply {
                        topMargin = activity.dp(3)
                        bottomMargin = activity.dp(3)
                    }
                    setBackgroundColor(COLOR_VERGE_PANEL)
                }
            },
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderGagadgetArticle(screen: PortalScreen.Article) {
        val site = catalog.siteById(screen.siteId)
        val categoryBar =
            buildCategoryFlowBar(
                siteId = screen.siteId,
                categories = displayFeedCategories(site, feedStates[screen.siteId]?.items.orEmpty()),
                selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty()),
                normalColor = COLOR_GG_CAT_TEXT,
                highlightColor = COLOR_GG_HEADLINE,
                focusBackground = null,
                lineSpacing = GAGADGET_LINE_SPACING,
                includeAll = false,
                center = true,
                onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = COLOR_GG_HEADLINE,
                focusedColor = COLOR_GG_HEADLINE,
                focusBackground = null,
                lineSpacing = GAGADGET_LINE_SPACING,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0

        LegacyPortalGagadgetArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            categoryBar = categoryBar,
            articleLink = articleLink,
            buildGagadgetLogoView = { buildGagadgetLogoView() },
            createGagadgetMetaText = { text, color -> createGagadgetMetaText(text, color) },
            createGagadgetTitleText = { text -> createGagadgetTitleText(text) },
            createGagadgetArticleText = { text, bold -> createGagadgetArticleText(text, bold) },
            dividerView = {
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        activity.dp(1),
                    ).apply {
                        topMargin = activity.dp(2)
                        bottomMargin = activity.dp(2)
                    }
                    setBackgroundColor(COLOR_DIVIDER)
                }
            },
            formatGagadgetDateTime = { value -> formatGagadgetDateTime(value) },
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderMezhaArticle(screen: PortalScreen.Article) {
        val site = catalog.siteById(screen.siteId)
        val categoryBar =
            buildCategoryFlowBar(
                siteId = screen.siteId,
                categories = displayFeedCategories(site, feedStates[screen.siteId]?.items.orEmpty()),
                selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty()),
                normalColor = Color.BLACK,
                highlightColor = Color.BLACK,
                focusBackground = COLOR_MEZHA_HEADLINE,
                lineSpacing = MEZHA_LINE_SPACING,
                includeAll = true,
                center = true,
                onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = COLOR_MEZHA_HEADLINE,
                focusedColor = Color.BLACK,
                focusBackground = COLOR_MEZHA_HEADLINE,
                lineSpacing = MEZHA_LINE_SPACING,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0

        LegacyPortalMezhaArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            categoryBar = categoryBar,
            articleLink = articleLink,
            createMezhaLogoText = { createMezhaLogoText() },
            createMezhaMetaText = { text, color -> createMezhaMetaText(text, color) },
            createMezhaTitleText = { text -> createMezhaTitleText(text) },
            createMezhaBodyText = { text, bold -> createMezhaBodyText(text, bold) },
            dividerView = {
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        activity.dp(1),
                    ).apply {
                        topMargin = activity.dp(2)
                        bottomMargin = activity.dp(2)
                    }
                    setBackgroundColor(COLOR_DIVIDER)
                }
            },
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderStopgameArticle(screen: PortalScreen.Article) {
        val site = catalog.siteById(screen.siteId)
        val categories = displayFeedCategories(site, feedStates[screen.siteId]?.items.orEmpty())
        val selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty())
        val brandedHeader =
            LegacyPortalStopgameSiteRenderer.buildBrandedHeader(
                activity = activity,
                siteId = screen.siteId,
                categories = categories,
                selectedCategory = selectedCategory,
                buildLogo = {
                    LegacyPortalStopgameSiteRenderer.buildLogoView(
                        activity = activity,
                        scaledTextSize = { value -> scaledTextSize(value) },
                        compactLineSpacing = compactLineSpacing,
                        typeface = portalTypeface,
                    )
                },
                scaledTextSize = ::scaledTextSize,
                compactLineSpacing = compactLineSpacing,
                typeface = portalTypeface,
                onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = COLOR_STOPGAME_TEXT,
                focusedColor = Color.WHITE,
                focusBackground = COLOR_STOPGAME_RSS_BAR,
                lineSpacing = compactLineSpacing,
                underlineOnlyWhenFocused = true,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0
        LegacyPortalStopgameArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            brandedHeader = brandedHeader,
            articleLink = articleLink,
            scaledTextSize = ::scaledTextSize,
            compactLineSpacing = compactLineSpacing,
            typeface = portalTypeface,
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderPlaygroundArticle(screen: PortalScreen.Article) {
        val site = catalog.siteById(screen.siteId)
        val categories = displayFeedCategories(site, feedStates[screen.siteId]?.items.orEmpty())
        val selectedCategory = selectedFeedCategory(screen.siteId, feedStates[screen.siteId]?.items.orEmpty())
        val brandedHeader =
            LegacyPortalPlaygroundSiteRenderer.buildBrandedHeader(
                activity = activity,
                siteId = screen.siteId,
                categories = categories,
                selectedCategory = selectedCategory,
                buildLogo = {
                    LegacyPortalPlaygroundSiteRenderer.buildLogoView(
                        activity = activity,
                        scaledTextSize = { value -> scaledTextSize(value) },
                        compactLineSpacing = playgroundLineSpacing,
                        typeface = portalTypeface,
                    )
                },
                scaledTextSize = ::scaledTextSize,
                compactLineSpacing = playgroundLineSpacing,
                typeface = portalTypeface,
                onCategorySelected = { category -> openFeedCategory(screen.siteId, category) },
            )
        val articleLink =
            createArticleLink(
                label = "",
                normalColor = COLOR_PLAYGROUND_TEXT,
                focusedColor = Color.WHITE,
                focusBackground = COLOR_PLAYGROUND_FOCUS,
                lineSpacing = playgroundLineSpacing,
                underlineOnlyWhenFocused = true,
            )
        val scroll = articleScrollPositions[articleScrollKey(screen.siteId, screen.feedItem)] ?: 0
        LegacyPortalPlaygroundArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            brandedHeader = brandedHeader,
            articleLink = articleLink,
            scaledTextSize = ::scaledTextSize,
            compactLineSpacing = playgroundLineSpacing,
            typeface = portalTypeface,
            restoreScrollAndFocus = { y, preferred, scrollFocus ->
                restoreArticleScrollAndFocus(y, preferred, scrollFocus)
            },
            scrollY = scroll,
        )
    }

    private fun renderBookmarks() {
        val sections = mutableListOf<SettingsListSection>()
        val bookmarkedIds = preferences.loadBookmarkedSiteIds()
        if (bookmarkedIds.isEmpty()) {
            sections += SettingsListSection("Bookmarks", listOf(SettingsListItem("No bookmarks yet")))
        } else {
            val items =
                catalog.sites
                .filter { site -> bookmarkedIds.contains(site.id) }
                .map { site ->
                    SettingsListItem(
                        title = site.title,
                        subtitle = site.summary.ifBlank { site.sectionId },
                        onClick = { openSite(site.id) },
                    )
                }
            sections += SettingsListSection("Bookmarks", items)
        }
        LegacyPortalSettingsListRenderer.render(
            activity = activity,
            container = container,
            sections = sections,
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            visualStyle = SettingsListVisualStyle.MENU_DENSE,
            showRowDividers = true,
        )
    }

    private fun renderSavedArticles() {
        val sections = mutableListOf<SettingsListSection>()
        val saved = savedArticleStore.list()
        if (saved.isEmpty()) {
            sections += SettingsListSection("Saved Articles", listOf(SettingsListItem("No saved articles")))
        } else {
            val items =
                saved.map { item ->
                    val siteName = catalog.sites.firstOrNull { it.id == item.siteId }?.title ?: item.siteId
                    SettingsListItem(
                        title = item.article.title.ifBlank { "article" },
                        subtitle = "$siteName · ${item.article.publishedAt ?: "offline copy"}",
                        onClick = { openSavedArticle(item) },
                    )
                }
            sections += SettingsListSection("Saved Articles", items)
        }
        LegacyPortalSettingsListRenderer.render(
            activity = activity,
            container = container,
            sections = sections,
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            visualStyle = SettingsListVisualStyle.MENU_DENSE,
        )
    }

    private fun renderHistory() {
        val entries = historyStore.list()
        val sections = mutableListOf<SettingsListSection>()
        if (entries.isEmpty()) {
            sections += SettingsListSection("History", listOf(SettingsListItem("No history yet")))
        } else {
            val grouped = HistoryStore.groupByDay(entries)
            val sortedKeys =
                grouped.keys.sortedWith(
                    compareByDescending<String> {
                        when (it) {
                            "Today" -> Long.MAX_VALUE
                            "Yesterday" -> Long.MAX_VALUE - 1
                            else -> 0L
                        }
                    }.thenByDescending { it },
                )
            sortedKeys.forEach { key ->
                val items =
                    grouped[key].orEmpty().map { entry ->
                        val siteName = resolveHistorySiteName(entry.siteId)
                        val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US)
                            .format(
                                java.time.ZonedDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(entry.visitedAtMs),
                                    java.time.ZoneId.systemDefault(),
                                ),
                            )
                        val subtitle = "$siteName · $time"
                        SettingsListItem(
                            title = entry.title,
                            subtitle = subtitle,
                            onClick = { openHistoryEntry(entry) },
                        )
                    }
                sections += SettingsListSection(key, items)
            }
        }
        LegacyPortalSettingsListRenderer.render(
            activity = activity,
            container = container,
            sections = sections,
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            visualStyle = SettingsListVisualStyle.MENU_DENSE,
        )
    }

    private fun resolveHistorySiteName(siteId: String): String =
        when (siteId) {
            "portal_home" -> "Portal"
            "bookmarks" -> "Bookmarks"
            "saved_articles" -> "Saved Articles"
            "history" -> "History"
            "page_info" -> "Page Info"
            else -> catalog.sites.firstOrNull { it.id == siteId }?.title ?: siteId
        }

    private fun renderPageInfo(info: BrowserPageInfo) {
        val sizeLabel = info.byteCount?.let { "${it} bytes" } ?: "unknown"
        val items =
            buildList {
                add(SettingsListItem("Title", info.title))
                add(SettingsListItem("Type", info.kind))
                add(SettingsListItem("Safe", info.secureLabel))
                add(SettingsListItem("Cache", info.cacheLabel))
                add(SettingsListItem("Size", sizeLabel))
                info.contentType?.let { add(SettingsListItem("MIME", it)) }
                info.requestedUrl?.let { add(SettingsListItem("Requested", it)) }
                info.finalUrl?.let { add(SettingsListItem("Final", it)) }
            }
        LegacyPortalSettingsListRenderer.render(
            activity = activity,
            container = container,
            sections = listOf(SettingsListSection("Page Info", items)),
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = { value -> scaledTextSize(value) },
            visualStyle = SettingsListVisualStyle.MENU_DENSE,
        )
    }

    private fun buildArsLogoView(): View =
        loadArsLogoBitmap()?.let { bitmap ->
            ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    rightMargin = activity.dp(6)
                }
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        } ?: TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(6)
            }
            includeFontPadding = false
            text = "ars"
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, compactLineSpacing)
            setTypeface(portalTypeface, Typeface.BOLD)
            setTextColor(COLOR_ARS_HIGHLIGHT)
            gravity = Gravity.CENTER
        }

    private fun loadArsLogoBitmap(): Bitmap? {
        arsLogoBitmap?.let { return it }
        return runCatching {
            activity.assets.open(ARS_LOGO_ASSET).use { stream ->
                val sourceBitmap = BitmapFactory.decodeStream(stream) ?: return null
                scaleLogoBitmapForHeader(sourceBitmap, activity.dp(72).coerceAtLeast(1)).also { scaledBitmap ->
                    arsLogoBitmap = scaledBitmap
                }
            }
        }.getOrNull()
    }

    private fun buildItcLogoView(): View =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(6)
            }
            includeFontPadding = false
            text = "ITC.ua"
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, compactLineSpacing)
            setTypeface(portalTypeface, Typeface.BOLD)
            setTextColor(COLOR_ITC_ACCENT)
        }

    private fun loadItcLogoBitmap(): Bitmap? {
        itcLogoBitmap?.let { return it }
        return runCatching {
            activity.assets.open(ITC_LOGO_ASSET).use { stream ->
                val sourceBitmap = BitmapFactory.decodeStream(stream) ?: return null
                softenLogoBitmap(sourceBitmap, activity.dp(68).coerceAtLeast(1)).also { softenedBitmap ->
                    itcLogoBitmap = softenedBitmap
                }
            }
        }.getOrNull()
    }

    private fun createArsSearchField(
        siteId: String,
        onSubmit: (String) -> Unit,
    ): EditText =
        EditText(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            includeFontPadding = false
            setText(currentFeedSearchQuery(siteId))
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setTextColor(COLOR_ARS_TEXT)
            typeface = portalTypeface
            setPadding(activity.dp(3), activity.dp(1), activity.dp(3), activity.dp(1))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                setStroke(activity.dp(1), COLOR_ARS_BAR)
            }
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            setOnEditorActionListener { _, actionId, event ->
                val isSubmit =
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        event?.keyCode == KeyEvent.KEYCODE_ENTER ||
                        event?.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                if (isSubmit) {
                    onSubmit(text?.toString().orEmpty())
                    true
                } else {
                    false
                }
            }
        }

    private fun createArsSearchButton(
        searchField: EditText,
        onSubmit: (String) -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = activity.dp(4)
            }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(activity.dp(5), activity.dp(1), activity.dp(5), activity.dp(1))
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false

            fun applyState(focused: Boolean) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) COLOR_ARS_HIGHLIGHT else Color.TRANSPARENT)
                    setStroke(activity.dp(1), COLOR_ARS_BAR)
                }
                text =
                    if (focused) {
                        buildStyledText("search", COLOR_ARS_TEXT)
                    } else {
                        buildStyledText("search", COLOR_ARS_BAR, underline = false)
                    }
                setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }

            setOnClickListener { onSubmit(searchField.text?.toString().orEmpty()) }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun buildArsSearchRow(
        siteId: String,
        onSubmit: (String) -> Unit,
    ): Pair<EditText, LinearLayout> {
        val searchField = createArsSearchField(siteId, onSubmit)
        val searchButton = createArsSearchButton(searchField, onSubmit)
        val row =
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(searchField)
                addView(searchButton)
            }
        return searchField to row
    }

    private fun buildArsCategoryBar(
        siteId: String,
        categories: List<String>,
        selectedCategory: String?,
        onCategorySelected: (String?) -> Unit = { category -> selectFeedCategory(siteId, category) },
    ): View {
        val tokens = buildList {
            add("all")
            categories.forEach { add(it) }
        }
        val maxRowWidth = activity.wapContentWidthPx(sidePaddingDp = 3)
        val paint = TextView(activity).paint.apply {
            textSize = activity.spToPx(scaledTextSize(10f))
            typeface = portalTypeface
        }
        val pieceGapWidth = activity.dp(8).toFloat()

        val rows = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(2)
                bottomMargin = activity.dp(2)
            }
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_ARS_BAR)
            setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(1))
        }

        var currentRow = createArsCategoryRow()
        var currentWidth = 0f

        fun commitRow() {
            rows.addView(currentRow)
            currentRow = createArsCategoryRow()
            currentWidth = 0f
        }

        tokens.forEachIndexed { index, label ->
            val isAll = index == 0
            val isSelected = if (isAll) selectedCategory == null else selectedCategory == label
            val basePrefix = ""
            val pieceText = "${basePrefix}${label}"
            val pieceWidth = paint.measureText(pieceText) + pieceGapWidth
            if (currentWidth > 0f && currentWidth + pieceWidth > maxRowWidth) {
                commitRow()
            }
            val prefix = ""
            val finalText = "$prefix$label"
            val pieceView = createArsCategoryPiece(
                siteId = siteId,
                category = if (isAll) null else label,
                text = finalText,
                selected = isSelected,
                onClick = {
                    onCategorySelected(if (isAll) null else label)
                },
            )
            currentRow.addView(pieceView)
            currentWidth += paint.measureText(finalText) + pieceGapWidth
        }
        if (currentRow.childCount > 0) {
            rows.addView(currentRow)
        }

        return rows
    }

    private fun createArsCategoryRow(): LinearLayout =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.HORIZONTAL
        }

    private fun createArsCategoryPiece(
        siteId: String,
        category: String?,
        text: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(4)
            }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setSingleLine(true)
            typeface = portalTypeface
            setPadding(0, 0, 0, 0)
            setTextColor(COLOR_ARS_BAR_TEXT)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = categoryFocusTag(siteId, category)
            fun applyState(focused: Boolean) {
                when {
                    selected -> {
                        setTypeface(portalTypeface, Typeface.BOLD)
                        setBackgroundColor(COLOR_ARS_HIGHLIGHT)
                        setTextColor(COLOR_ARS_TEXT)
                        this.text = text
                        setPadding(activity.dp(2), 0, activity.dp(2), 0)
                    }

                    focused -> {
                        setTypeface(portalTypeface, Typeface.BOLD)
                        setBackgroundColor(Color.WHITE)
                        setTextColor(COLOR_ARS_TEXT)
                        this.text = text
                        setPadding(activity.dp(2), 0, activity.dp(2), 0)
                    }

                    else -> {
                        setTypeface(portalTypeface, Typeface.NORMAL)
                        setBackgroundColor(Color.TRANSPARENT)
                        this.text = buildStyledText(text, COLOR_ARS_BAR_TEXT, underline = false)
                        setPadding(0, 0, 0, 0)
                    }
                }
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun createArsFooterText(text: String): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(4)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            setTextColor(Color.BLACK)
        }

    private fun buildItcCategoryLine(
        siteId: String,
        categories: List<String>,
        selectedCategory: String?,
        onCategorySelected: (String?) -> Unit = { category -> selectFeedCategory(siteId, category) },
    ): View {
        val raw = categories.take(2)
        val sorted = raw.sortedWith(compareBy { it.lowercase(Locale.ROOT).contains("партнер") })
        val primary = sorted.getOrNull(0) ?: "all"
        val secondary = sorted.getOrNull(1)
        val row = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, activity.dp(2))
        }
        row.addView(
            createItcCategoryPiece(
                siteId = siteId,
                category = if (primary == "all") null else primary,
                label = primary,
                selected = selectedCategory == primary || (selectedCategory == null && primary == "all"),
                onClick = { onCategorySelected(if (primary == "all") null else primary) },
            ),
        )
        secondary?.let {
            row.addView(createItcSeparatorText())
            row.addView(
                createItcCategoryPiece(
                    siteId = siteId,
                    category = it,
                    label = it,
                    selected = selectedCategory == it,
                    onClick = { onCategorySelected(it) },
                ),
            )
        }
        return row
    }

    private fun createItcCategoryPiece(
        siteId: String,
        category: String?,
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            val displayLabel = itcCategoryDisplayLabel(label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(4)
            }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setPadding(0, 0, 0, 0)
            setTextColor(COLOR_ITC_ACCENT)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = categoryFocusTag(siteId, category)
            fun applyState(focused: Boolean) {
                setTypeface(portalTypeface, if (selected || focused) Typeface.BOLD else Typeface.NORMAL)
                this.text =
                    if (selected) {
                        buildStyledText(displayLabel, COLOR_ITC_ACCENT)
                    } else {
                        buildStyledText(displayLabel, COLOR_ITC_ACCENT, underline = true)
                    }
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun addItcFeedEntry(
        number: Int,
        title: String,
        meta: String?,
        onClick: () -> Unit,
        onFocused: () -> Unit,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(activity.dp(2), 0, activity.dp(2), 0)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            setBackgroundColor(Color.TRANSPARENT)

            val badgeView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(16), activity.dp(16))
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setTypeface(portalTypeface, Typeface.BOLD)
                text = number.toString()
            }

            val textWrap = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    leftMargin = activity.dp(2)
                }
                orientation = LinearLayout.VERTICAL
            }

            val titleView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                paint.isUnderlineText = true
            }

            val metaView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
                text = meta.orEmpty()
            }

            fun applyState(focused: Boolean) {
                setBackgroundColor(if (focused) COLOR_ITC_ACCENT else Color.TRANSPARENT)

                badgeView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) Color.TRANSPARENT else Color.TRANSPARENT)
                    setStroke(activity.dp(1), if (focused) Color.WHITE else COLOR_ITC_TEXT)
                }
                badgeView.setTextColor(if (focused) Color.WHITE else COLOR_ITC_TEXT)

                titleView.text = title
                titleView.setTextColor(if (focused) Color.WHITE else COLOR_ITC_TEXT)
                titleView.setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(if (focused) Color.WHITE else COLOR_ITC_TEXT)
            }

            addView(badgeView)
            textWrap.addView(titleView)
            textWrap.addView(metaView)
            addView(textWrap)

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                if (hasFocus) {
                    onFocused()
                }
            }
            applyState(false)
        }

    private fun createItcHeaderBar(text: String): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(2)
                bottomMargin = activity.dp(2)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(1))
            typeface = portalTypeface
            setBackgroundColor(COLOR_ITC_ACCENT)
            setTextColor(Color.parseColor("#0A0A0A"))
        }

    private fun createItcSeparatorText(): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(4)
            }
            includeFontPadding = false
            text = "  "
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setTextColor(COLOR_ITC_ACCENT)
        }

    private fun looksLikeItcPartnerCategory(label: String): Boolean {
        val normalized = label.lowercase(Locale.ROOT)
        return normalized.contains("партнер") ||
            normalized.contains("партнерсь") ||
            normalized.contains("partner")
    }

    private fun itcCategoryDisplayLabel(label: String): String =
        if (looksLikeItcPartnerCategory(label)) {
            "Партнеры"
        } else {
            label
        }

    private fun createItcSearchField(
        siteId: String,
        onSubmit: (String) -> Unit,
    ): EditText =
        EditText(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            setText(currentFeedSearchQuery(siteId))
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setTextColor(COLOR_ITC_TEXT)
            typeface = portalTypeface
            setPadding(activity.dp(3), activity.dp(1), activity.dp(3), activity.dp(1))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                setStroke(activity.dp(1), COLOR_ITC_ACCENT)
            }
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            setOnEditorActionListener { _, actionId, event ->
                val isSubmit =
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        event?.keyCode == KeyEvent.KEYCODE_ENTER ||
                        event?.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                if (isSubmit) {
                    onSubmit(text?.toString().orEmpty())
                    true
                } else {
                    false
                }
            }
        }

    private fun createItcSearchButton(
        searchField: EditText,
        onSubmit: (String) -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = activity.dp(4)
            }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(activity.dp(5), activity.dp(1), activity.dp(5), activity.dp(1))
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false

            fun applyState(focused: Boolean) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) COLOR_ITC_ACCENT else Color.TRANSPARENT)
                    setStroke(activity.dp(1), COLOR_ITC_ACCENT)
                }
                text =
                    if (focused) {
                        buildStyledText("search", Color.WHITE)
                    } else {
                        buildStyledText("search", COLOR_ITC_ACCENT, underline = true)
                    }
                setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }

            setOnClickListener { onSubmit(searchField.text?.toString().orEmpty()) }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun buildItcSearchRow(
        siteId: String,
        onSubmit: (String) -> Unit,
    ): Pair<EditText, LinearLayout> {
        val searchField = createItcSearchField(siteId, onSubmit).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }
        val searchButton = createItcSearchButton(searchField, onSubmit)
        val row =
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(searchField)
                addView(searchButton)
            }
        return searchField to row
    }

    private fun buildItcPaginationBar(
        siteId: String,
        pageCount: Int,
        selectedPage: Int,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(4)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            for (pageIndex in 0 until pageCount) {
                if (pageIndex > 0) {
                    addView(createItcPagerSpacer())
                }
                addView(
                    createItcPagerLink(
                        label = (pageIndex + 1).toString(),
                        selected = pageIndex == selectedPage,
                        onClick = {
                            val pagerState = feedPagerStates[siteId] ?: FeedPagerState()
                            feedPagerStates = feedPagerStates + (
                                siteId to pagerState.copy(pageIndex = pageIndex, selectedSlot = 0)
                                    .normalizeFor((feedStates[siteId]?.items.orEmpty()).size)
                            )
                            render()
                        },
                    ),
                )
            }
        }

    private fun createItcPagerSpacer(): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(4)
            }
            includeFontPadding = false
            text = " "
            textSize = scaledTextSize(11f)
            typeface = portalTypeface
            setTextColor(COLOR_ITC_ACCENT)
        }

    private fun createItcPagerLink(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(4)
            }
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            isFocusable = !selected
            isClickable = !selected
            isFocusableInTouchMode = false

            fun applyState(focused: Boolean) {
                if (selected || focused) {
                    setTypeface(portalTypeface, Typeface.BOLD)
                    text = buildStyledText(label, COLOR_ITC_TEXT)
                } else {
                    setTypeface(portalTypeface, Typeface.NORMAL)
                    text = buildStyledText(label, COLOR_ITC_ACCENT, underline = true)
                }
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun createItcTagLine(categories: List<String>): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(1)
            }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            setTextColor(COLOR_ITC_ACCENT)
            text = categories.joinToString("  ")
        }

    private fun createItcMetaText(
        text: String,
        color: Int = COLOR_ITC_TEXT,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(2)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            setTextColor(color)
        }

    private fun createItcArticleText(
        text: String,
        bold: Boolean = false,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            setTextColor(COLOR_ITC_TEXT)
            setTypeface(portalTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun createItcFooterText(text: String): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(4)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            setTextColor(COLOR_ITC_TEXT)
        }

    private fun formatItcDate(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val trimmed = value.trim()
        return runCatching {
            val parsed = try {
                java.time.ZonedDateTime.parse(trimmed, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            } catch (e: java.time.format.DateTimeParseException) {
                // Fall back to parsing just the date portion
                java.time.LocalDate.parse(trimmed.substring(0, 10))
                    .atStartOfDay(java.time.ZoneId.of("UTC"))
            }
            parsed.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        }.getOrNull() ?: trimmed
    }

    private fun createVergeLogoText(): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(3)
            }
            includeFontPadding = false
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = scaledTextSize(13f)
            setLineSpacing(0f, VERGE_LINE_SPACING)
            setTypeface(portalTypeface, Typeface.BOLD)
            text =
                SpannableString("the verge").apply {
                    setSpan(
                        ForegroundColorSpan(COLOR_VERGE_GLOW),
                        0,
                        3,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    setSpan(
                        ForegroundColorSpan(COLOR_VERGE_ACCENT),
                        4,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
        }

    private fun createVergeFeedEntry(
        number: Int,
        title: String,
        meta: String?,
        onClick: () -> Unit,
        onFocused: () -> Unit,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(1))
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false

            val badgeView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(16), activity.dp(16))
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setTypeface(portalTypeface, Typeface.BOLD)
                text = number.toString()
            }

            val textWrap = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    leftMargin = activity.dp(3)
                }
                orientation = LinearLayout.VERTICAL
            }

            val titleView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, VERGE_LINE_SPACING)
                typeface = portalTypeface
                paint.isUnderlineText = true
            }

            val metaView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, VERGE_LINE_SPACING)
                typeface = portalTypeface
                visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
                text = meta.orEmpty()
            }

            fun applyState(focused: Boolean) {
                setBackgroundColor(if (focused) COLOR_VERGE_PANEL else Color.TRANSPARENT)
                badgeView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) COLOR_VERGE_ACCENT else Color.TRANSPARENT)
                    setStroke(activity.dp(1), if (focused) COLOR_VERGE_ACCENT else COLOR_VERGE_GLOW)
                }
                badgeView.setTextColor(if (focused) Color.BLACK else COLOR_VERGE_GLOW)
                titleView.text = title
                titleView.setTextColor(if (focused) COLOR_VERGE_ACCENT else COLOR_VERGE_TEXT)
                titleView.setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(if (focused) COLOR_VERGE_GLOW else COLOR_VERGE_MUTED)
            }

            addView(badgeView)
            textWrap.addView(titleView)
            textWrap.addView(metaView)
            addView(textWrap)

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                if (hasFocus) {
                    onFocused()
                }
            }
            applyState(false)
        }

    private fun buildVergePaginationBar(
        siteId: String,
        pageCount: Int,
        selectedPage: Int,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(4)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            for (pageIndex in 0 until pageCount) {
                if (pageIndex > 0) {
                    addView(createVergePagerSpacer())
                }
                addView(
                    createVergePagerLink(
                        label = (pageIndex + 1).toString(),
                        selected = pageIndex == selectedPage,
                        onClick = {
                            val pagerState = feedPagerStates[siteId] ?: FeedPagerState()
                            feedPagerStates = feedPagerStates + (
                                siteId to pagerState.copy(pageIndex = pageIndex, selectedSlot = 0)
                                    .normalizeFor((feedStates[siteId]?.items.orEmpty()).size)
                            )
                            render()
                        },
                    ),
                )
            }
        }

    private fun createVergePagerSpacer(): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            text = " "
            textSize = scaledTextSize(11f)
            typeface = portalTypeface
            setTextColor(COLOR_VERGE_GLOW)
        }

    private fun createVergePagerLink(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = activity.dp(4)
            }
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, VERGE_LINE_SPACING)
            setPadding(activity.dp(1), 0, activity.dp(1), 0)
            typeface = portalTypeface
            isFocusable = !selected
            isClickable = !selected
            isFocusableInTouchMode = false

            fun applyState(focused: Boolean) {
                val active = selected || focused
                setBackgroundColor(if (focused) COLOR_VERGE_PANEL else Color.TRANSPARENT)
                setTypeface(portalTypeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                text =
                    if (active) {
                        buildStyledText(label, if (selected) COLOR_VERGE_ACCENT else COLOR_VERGE_TEXT)
                    } else {
                        buildStyledText(label, COLOR_VERGE_GLOW, underline = true)
                    }
            }

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun createVergeMetaText(
        text: String,
        color: Int = COLOR_VERGE_MUTED,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, VERGE_LINE_SPACING)
            typeface = portalTypeface
            setTextColor(color)
        }

    private fun createVergeTitleText(text: String): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, VERGE_LINE_SPACING)
            setTypeface(portalTypeface, Typeface.BOLD)
            setTextColor(COLOR_VERGE_TEXT)
        }

    private fun createVergeArticleText(
        text: String,
        bold: Boolean = false,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, VERGE_LINE_SPACING)
            setTypeface(portalTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(COLOR_VERGE_TEXT)
        }

    private fun formatGagadgetDateTime(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val trimmed = value.trim()
        return runCatching {
            val parsed = java.time.ZonedDateTime.parse(trimmed, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            parsed.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
        }.getOrNull() ?: trimmed
    }

    private fun buildGagadgetLogoView(): View {
        val label = "gagadget.com"
        return TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            text =
                SpannableString(label).apply {
                    label.forEachIndexed { index, char ->
                        val color =
                            if (char == 'g' || char == 'G') {
                                COLOR_GG_HEADLINE
                            } else {
                                Color.BLACK
                            }
                        setSpan(
                            ForegroundColorSpan(color),
                            index,
                            index + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, GAGADGET_LINE_SPACING)
            typeface = portalTypeface
        }
    }

    private fun loadGagadgetLogoBitmap(): Bitmap? {
        gagadgetLogoBitmap?.let { return it }
        return runCatching {
            activity.assets.open(GAGADGET_LOGO_ASSET).use { stream ->
                val sourceBitmap = BitmapFactory.decodeStream(stream) ?: return null
                softenLogoBitmap(sourceBitmap, activity.dp(72).coerceAtLeast(1)).also { softenedBitmap ->
                    gagadgetLogoBitmap = softenedBitmap
                }
            }
        }.getOrNull()
    }

    private fun shouldRenderSiteSummary(site: PortalSite): Boolean =
        when (site.theme) {
            SiteTheme.ARS,
            SiteTheme.ITC,
            SiteTheme.VERGE,
            SiteTheme.GAGADGET,
            SiteTheme.MEZHA,
            SiteTheme.STOPGAME,
            SiteTheme.PLAYGROUND,
            SiteTheme.KYIV_VLADA,
            SiteTheme.UA_44,
            SiteTheme.VGORODE,
            -> false

            else -> !site.id.startsWith("ann")
        }

    private fun buildCategoryFlowBar(
        siteId: String,
        categories: List<String>,
        selectedCategory: String?,
        normalColor: Int,
        highlightColor: Int,
        focusBackground: Int?,
        lineSpacing: Float,
        includeAll: Boolean,
        center: Boolean,
        onCategorySelected: (String?) -> Unit = { category -> selectFeedCategory(siteId, category) },
    ): View {
        val tokens = buildList {
            if (includeAll) {
                add("all")
            }
            categories.forEach { add(it) }
        }
        val maxRowWidth = (activity.resources.displayMetrics.widthPixels - activity.dp(8)).coerceAtLeast(1)
        val paint = TextView(activity).paint.apply {
            textSize = activity.spToPx(scaledTextSize(10f))
            typeface = portalTypeface
        }
        val pieceGapWidth = activity.dp(4).toFloat()

        val rows = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            orientation = LinearLayout.VERTICAL
        }

        var currentRow = createCategoryRow(center)
        var currentWidth = 0f

        fun commitRow() {
            rows.addView(currentRow)
            currentRow = createCategoryRow(center)
            currentWidth = 0f
        }

        tokens.forEachIndexed { index, label ->
            val isAll = includeAll && index == 0
            val isSelected = if (isAll) selectedCategory == null else selectedCategory == label
            val prefix = ""
            val pieceText = "$prefix$label"
            val spacerWidth = if (currentRow.childCount > 0) pieceGapWidth else 0f
            val pieceWidth = paint.measureText(pieceText) + spacerWidth
            if (currentWidth > 0f && currentWidth + pieceWidth > maxRowWidth) {
                commitRow()
            }
            if (currentRow.childCount > 0) {
                currentRow.addView(
                    Space(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(activity.dp(4), 0)
                    },
                )
            }
            val pieceView = createCategoryPiece(
                siteId = siteId,
                category = if (isAll) null else label,
                text = pieceText,
                selected = isSelected,
                normalColor = normalColor,
                highlightColor = highlightColor,
                focusBackground = focusBackground,
                lineSpacing = lineSpacing,
                onClick = { onCategorySelected(if (isAll) null else label) },
            )
            currentRow.addView(pieceView)
            currentWidth += pieceWidth
        }
        if (currentRow.childCount > 0) {
            rows.addView(currentRow)
        }

        return rows
    }

    private fun createCategoryRow(center: Boolean): LinearLayout =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = if (center) Gravity.CENTER_HORIZONTAL else Gravity.START
        }

    private fun createCategoryPiece(
        siteId: String,
        category: String?,
        text: String,
        selected: Boolean,
        normalColor: Int,
        highlightColor: Int,
        focusBackground: Int?,
        lineSpacing: Float,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, lineSpacing)
            typeface = portalTypeface
            setPadding(0, 0, 0, 0)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = categoryFocusTag(siteId, category)

            fun applyState(focused: Boolean) {
                val active = selected || focused
                setTypeface(portalTypeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                this.text = buildStyledText(text, if (active) highlightColor else normalColor, underline = true)
                if (focusBackground != null && active) {
                    setBackgroundColor(focusBackground)
                } else {
                    setBackgroundColor(Color.TRANSPARENT)
                }
                val horizontalPadding = if (active) activity.dp(1) else 0
                setPadding(horizontalPadding, 0, horizontalPadding, 0)
            }

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun gagadgetFeedSections(): List<String> =
        listOf(
            "\u041A\u043E\u043C\u043F\u044C\u044E\u0442\u0435\u0440\u043D\u044B\u0435 \u0438\u0433\u0440\u044B",
            "\u041C\u043E\u0431\u0438\u043B\u044C\u043D\u044B\u0435 \u0438\u0433\u0440\u044B",
            "\u0422\u0435\u0445\u043D\u043E\u043B\u043E\u0433\u0438\u0438",
            "\u0418\u0441\u043A\u0443\u0441\u0441\u0442\u0432\u0435\u043D\u043D\u044B\u0439 \u0438\u043D\u0442\u0435\u043B\u043B\u0435\u043A\u0442",
            "\u0421\u043C\u0430\u0440\u0442\u0444\u043E\u043D\u044B",
            "\u041A\u043E\u043C\u043F\u044C\u044E\u0442\u0435\u0440\u044B",
        )

    private fun unusedGagadgetDisplaySections(): List<String> =
        listOf(
            "Компьютерные игры",
            "Мобильные игры",
            "Технологии",
            "Искусственный интеллект",
            "Смартфоны",
            "Компьютеры",
        )

    private fun unusedGagadgetSections(): List<String> =
        listOf(
            "Компьютерные игры",
            "Мобильные игры",
            "Технологии",
            "Искусственный интеллект",
            "Смартфоны",
            "Компьютеры",
        )

    private fun createGagadgetMetaText(
        text: String,
        color: Int = COLOR_GG_TEXT,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, GAGADGET_LINE_SPACING)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            setTextColor(color)
        }

    private fun createGagadgetTitleText(text: String): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, GAGADGET_LINE_SPACING)
            typeface = portalTypeface
            setTextColor(COLOR_GG_HEADLINE)
        }

    private fun createGagadgetArticleText(
        text: String,
        bold: Boolean,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, GAGADGET_LINE_SPACING)
            typeface = portalTypeface
            setTextColor(COLOR_GG_ARTICLE)
            setTypeface(portalTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun createMezhaLogoText(): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            text = "Mezha.media"
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setTextColor(COLOR_MEZHA_HEADLINE)
        }

    private fun createMezhaMetaText(
        text: String,
        color: Int = Color.BLACK,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setTextColor(color)
        }

    private fun createMezhaTitleText(text: String): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setTextColor(COLOR_MEZHA_HEADLINE)
        }

    private fun createMezhaBodyText(
        text: String,
        bold: Boolean,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setTextColor(Color.BLACK)
            setTypeface(portalTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun createArsMetaText(
        text: String,
        color: Int = COLOR_ARS_MUTED,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(2)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            setTextColor(color)
        }

    private fun createArsArticleText(
        text: String,
        bold: Boolean = false,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            setTextColor(COLOR_ARS_TEXT)
            setTypeface(portalTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun addSectionHeader(
        title: String,
        headerBgColor: Int = COLOR_HEADER,
        headerTextColor: Int = Color.WHITE,
    ) {
        container.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(headerBgColor)
                setTextColor(headerTextColor)
                textSize = scaledTextSize(10f)
                setTypeface(portalTypeface, Typeface.BOLD)
                setPadding(activity.dp(4), activity.dp(2), activity.dp(4), activity.dp(2))
                text = title
            },
        )
    }

    private fun createHomeRectDrawable(
        fillColor: Int,
        strokeColor: Int,
        strokeWidthDp: Int = 1,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(activity.dp(strokeWidthDp), strokeColor)
        }

    private fun buildHomePortalLogoText(): CharSequence =
        SpannableString("Yumode").apply {
            setSpan(ForegroundColorSpan(COLOR_HOME_LOGO), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun addHomeSpecHeader(pageTitle: String? = null) {
        val wrap =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = activity.dp(1)
                }
                background = createHomeRectDrawable(Color.WHITE, Color.TRANSPARENT)
                setPadding(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2))
            }

        val logoView =
            TextView(activity).apply {
                layoutParams =
                    if (pageTitle.isNullOrBlank()) {
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    } else {
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                includeFontPadding = false
                text = buildHomePortalLogoText()
                textSize = scaledTextSize(14f)
                setLineSpacing(0f, compactLineSpacing)
                setTypeface(portalTypeface, Typeface.BOLD)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                isFocusableInTouchMode = false
                setOnClickListener { openPortalHome() }
            }
        wrap.addView(
            logoView,
        )

        if (pageTitle.isNullOrBlank()) {
            val weatherView =
                TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    includeFontPadding = false
                    textSize = scaledTextSize(10f)
                    setLineSpacing(0f, compactLineSpacing)
                    typeface = portalTypeface
                    setTextColor(COLOR_HOME_MUTED)
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    text = buildHomeWeatherText()
                    isFocusable = true
                    isClickable = true
                    isFocusableInTouchMode = false
                    setOnClickListener { openSite("weather_city") }
                }
            wrap.addView(
                weatherView,
            )
        } else {
            wrap.addView(
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            marginStart = activity.dp(6)
                        }
                    includeFontPadding = false
                    text = pageTitle
                    textSize = scaledTextSize(12f)
                    setLineSpacing(0f, compactLineSpacing)
                    typeface = portalTypeface
                    setTypeface(portalTypeface, Typeface.BOLD)
                    setTextColor(COLOR_HOME_TEXT)
                    gravity = Gravity.CENTER_VERTICAL
                },
            )
            wrap.addView(
                Space(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            0,
                            1f,
                        )
                },
            )
        }

        container.addView(wrap)
    }

    private fun catalogPageBySectionId(sectionId: String): CatalogPage? =
        catalogPages().firstOrNull { catalogSectionId(it.id) == sectionId }

    private fun catalogPages(): List<CatalogPage> {
        fun siteEntry(
            title: String,
            summary: String,
            siteId: String,
        ): CatalogPageEntry =
            CatalogPageEntry(
                title = title,
                summary = summary,
                onClick = { openSite(siteId) },
            )

        fun urlEntry(
            title: String,
            summary: String,
            url: String,
        ): CatalogPageEntry =
            CatalogPageEntry(
                title = title,
                summary = summary,
                onClick = { openExternalUrl(url) },
            )

        return listOf(
            CatalogPage(
                id = CATALOG_REGION_ID,
                title = "Region",
                summary = "Regional and city feeds",
                entries = listOf(
                    siteEntry("KyivVlada", "Kyiv and city authority news", "kyiv_vlada"),
                    siteEntry("44.ua", "Kyiv city news", "ua_44"),
                    siteEntry("VGorode", "Kyiv city feed", "vgorode"),
                ),
            ),
            CatalogPage(
                id = CATALOG_TECH_ID,
                title = "Technology",
                summary = "Selected tech and gadget sources",
                entries = listOf(
                    siteEntry("Ars Technica", "Hardware, science, software", ARS_SITE_ID),
                    siteEntry("The Verge", "Gadgets and industry", "theverge"),
                    siteEntry("ITC.ua", "Tech news", "itc_ua"),
                    siteEntry("Gagadget", "Gadgets and reviews", "gagadget"),
                    siteEntry("Mezha", "Tech and culture", "mezha"),
                    siteEntry("AIN", "Startups, business and tech", "ain"),
                    siteEntry("Futurism", "Future tech and science", "futurism"),
                    siteEntry("Overclockers.ua", "Hardware, games, reviews", "overclockers_ua"),
                ),
            ),
            CatalogPage(
                id = CATALOG_FUN_ID,
                title = "Entertainment",
                summary = "Culture, media and fun links",
                entries = emptyList(),
            ),
            CatalogPage(
                id = CATALOG_VIDEO_ID,
                title = "Video",
                summary = "Video sites and watch links",
                entries = emptyList(),
            ),
            CatalogPage(
                id = CATALOG_ANIM_ID,
                title = "Animation",
                summary = "Anime and related feeds",
                entries = listOf(
                    siteEntry("Shikimori", "Community and anime news", "shikimori_news"),
                    siteEntry("ANN", "Newsroom, views and reviews", "ann"),
                ),
            ),
            CatalogPage(
                id = CATALOG_GAMES_ID,
                title = "Games",
                summary = "Game news and article sections",
                entries = listOf(
                    siteEntry("Playground", "News and articles inside one site", "playground"),
                    siteEntry("StopGame", "All and news sections inside one site", "stopgame"),
                ),
            ),
            CatalogPage(
                id = CATALOG_PC_ID,
                title = "PC Sites",
                summary = "External PC web directory",
                entries =
                    defaultPcSiteCatalogEntries().map { entry ->
                        urlEntry(
                            title = entry.title,
                            summary = entry.summary,
                            url = entry.targetId,
                        )
                    },
            ),
        )
    }

    private fun currentCatalogPageIndex(
        pageId: String,
        entryCount: Int,
    ): Int {
        if (entryCount <= 0) {
            return 0
        }
        val maxPageIndex = ((entryCount - 1) / CATALOG_PAGE_SIZE).coerceAtLeast(0)
        return (catalogPageIndices[pageId] ?: 0).coerceIn(0, maxPageIndex)
    }

    private fun createCatalogPaginationBar(
        pageId: String,
        pageCount: Int,
        currentPage: Int,
    ): LinearLayout =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(1)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            for (pageIndex in 0 until pageCount) {
                if (pageIndex > 0) {
                    addView(
                        Space(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(activity.dp(4), 0)
                        },
                    )
                }
                addView(
                    TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        includeFontPadding = false
                        textSize = scaledTextSize(10f)
                        setLineSpacing(0f, compactLineSpacing)
                        typeface = portalTypeface
                        isFocusable = true
                        isClickable = true
                        isFocusableInTouchMode = false
                        tag = catalogPaginationTag(pageId, pageIndex)

                        fun applyState(focused: Boolean) {
                            val selected = pageIndex == currentPage
                            text = buildStyledText(
                                (pageIndex + 1).toString(),
                                if (selected || focused) COLOR_HOME_FOCUS else COLOR_LINK,
                                underline = focused,
                            )
                            setTypeface(portalTypeface, if (selected || focused) Typeface.BOLD else Typeface.NORMAL)
                            setBackgroundColor(Color.TRANSPARENT)
                        }

                        setOnClickListener {
                            catalogPageIndices = catalogPageIndices + (pageId to pageIndex)
                            pendingFocusTag = catalogPaginationTag(pageId, pageIndex)
                            render()
                        }
                        setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
                        applyState(false)
                    },
                )
            }
        }

    private fun createCatalogMenuItem(
        page: CatalogPage,
        number: Int,
        selected: Boolean,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            setPadding(0, 0, 0, 0)
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = catalogMenuTag(page.id)

            fun applyState(focused: Boolean) {
                val active = selected || focused
                text = buildStyledText("$number ${page.title}", if (active) COLOR_HOME_FOCUS else COLOR_HOME_LINK, underline = focused)
                setTypeface(portalTypeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            }

            setOnClickListener {
                pendingFocusTag = catalogMenuTag(page.id)
                openCatalogPage(page.id)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && !selected) {
                    pendingFocusTag = catalogMenuTag(page.id)
                    openCatalogPage(page.id)
                } else {
                    applyState(hasFocus)
                }
            }
            applyState(false)
        }

    private fun catalogMenuTag(pageId: String): String =
        "${CATEGORY_FOCUS_TAG_PREFIX}catalog:$pageId"

    private fun catalogPaginationTag(
        pageId: String,
        pageIndex: Int,
    ): String = "${CATEGORY_FOCUS_TAG_PREFIX}catalog-page:$pageId:$pageIndex"

    private fun catalogSectionId(pageId: String): String =
        "catalog:$pageId"

    private fun addDivider() {
        container.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.dp(1),
                )
                setBackgroundColor(COLOR_DIVIDER)
            },
        )
    }

    private fun addPlainText(
        text: String,
        sizeSp: Float = 11f,
        color: Int = COLOR_TEXT,
        bold: Boolean = false,
        lineSpacingMultiplier: Float = compactLineSpacing,
        verticalPaddingDp: Int = 1,
        bottomMarginDp: Int = 0,
    ) {
        container.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = activity.dp(bottomMarginDp)
                }
                includeFontPadding = false
                setTextColor(color)
                textSize = scaledTextSize(sizeSp)
                setLineSpacing(0f, lineSpacingMultiplier)
                setTypeface(portalTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
                setPadding(activity.dp(2), activity.dp(verticalPaddingDp), activity.dp(2), activity.dp(verticalPaddingDp))
                this.text = text
            },
        )
    }

    private fun addMetaText(
        text: String,
        color: Int = COLOR_MUTED,
    ) {
        addPlainText(
            text = text,
            sizeSp = 10f,
            color = color,
            lineSpacingMultiplier = compactLineSpacing,
            verticalPaddingDp = 0,
            bottomMarginDp = 1,
        )
    }

    private fun addArticleText(
        text: String,
        bold: Boolean = false,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(2)
            }
            includeFontPadding = false
            setTextColor(COLOR_TEXT)
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            setTypeface(portalTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setPadding(activity.dp(2), 0, activity.dp(2), 0)
            this.text = text
            container.addView(this)
        }

    private fun addArticleLink(
        label: String,
        normalColor: Int,
        focusedColor: Int,
        focusBackground: Int?,
        lineSpacing: Float = compactLineSpacing,
    ): TextView =
        createArticleLink(
            label = label,
            normalColor = normalColor,
            focusedColor = focusedColor,
            focusBackground = focusBackground,
            lineSpacing = lineSpacing,
        ).also(container::addView)

    private fun createArticleLink(
        label: String,
        normalColor: Int,
        focusedColor: Int,
        focusBackground: Int?,
        lineSpacing: Float,
        underlineOnlyWhenFocused: Boolean = false,
    ): TextView =
        TextView(activity).apply {
            val hiddenAnchor = label.isBlank()
            layoutParams = LinearLayout.LayoutParams(
                if (hiddenAnchor) activity.dp(1) else ViewGroup.LayoutParams.WRAP_CONTENT,
                if (hiddenAnchor) activity.dp(1) else ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = if (hiddenAnchor) 0 else activity.dp(2)
            }
            includeFontPadding = false
            textSize = if (hiddenAnchor) 1f else scaledTextSize(10f)
            setLineSpacing(0f, lineSpacing)
            if (hiddenAnchor) {
                setPadding(0, 0, 0, 0)
            } else {
                setPadding(activity.dp(2), 0, activity.dp(2), 0)
            }
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = ARTICLE_SCROLL_FOCUS_TAG

            fun applyState(focused: Boolean) {
                if (hiddenAnchor) {
                    text = ""
                    setBackgroundColor(Color.TRANSPARENT)
                    return
                }
                setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                if (focused) {
                    text = buildStyledText(label, focusedColor, underline = true)
                    setBackgroundColor(focusBackground ?: Color.TRANSPARENT)
                } else {
                    text = buildStyledText(label, normalColor, underline = !underlineOnlyWhenFocused)
                    setBackgroundColor(Color.TRANSPARENT)
                }
            }

            setOnClickListener { }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun addLink(
        text: String,
        normalLinkColor: Int = COLOR_LINK,
        focusBackgroundColor: Int = COLOR_FOCUS,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(1))
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            setBackgroundColor(Color.TRANSPARENT)
            applyLinkText(
                this,
                text,
                focused = false,
                focusBackgroundColor = focusBackgroundColor,
                normalLinkColor = normalLinkColor,
            )
            setOnClickListener { onClick() }
            setOnFocusChangeListener { view, hasFocus ->
                applyLinkText(
                    view as TextView,
                    text,
                    focused = hasFocus,
                    focusBackgroundColor = focusBackgroundColor,
                    normalLinkColor = normalLinkColor,
                )
            }
            container.addView(this)
        }

    private fun addFeedEntry(
        number: Int,
        title: String,
        meta: String?,
        onClick: () -> Unit,
        onFocused: () -> Unit,
        focusedBackgroundColor: Int = COLOR_FOCUS,
        focusedBadgeColor: Int = COLOR_FOCUS,
        normalLinkColor: Int = COLOR_LINK,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(activity.dp(2), 0, activity.dp(2), 0)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            setBackgroundColor(Color.TRANSPARENT)

            val badgeView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(16), activity.dp(16))
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setTypeface(portalTypeface, Typeface.BOLD)
                text = number.toString()
            }

            val textWrap = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    leftMargin = activity.dp(2)
                }
                orientation = LinearLayout.VERTICAL
            }

            val titleView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                paint.isUnderlineText = true
            }

            val metaView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
                text = meta.orEmpty()
            }

            fun applyState(focused: Boolean) {
                setBackgroundColor(if (focused) focusedBackgroundColor else Color.TRANSPARENT)

                badgeView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) Color.WHITE else Color.TRANSPARENT)
                    setStroke(activity.dp(1), if (focused) focusedBadgeColor else COLOR_TEXT)
                }
                badgeView.setTextColor(if (focused) focusedBadgeColor else COLOR_TEXT)

                titleView.text = title
                titleView.setTextColor(if (focused) Color.WHITE else normalLinkColor)
                titleView.setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(if (focused) Color.WHITE else COLOR_MUTED)
            }

            addView(badgeView)
            textWrap.addView(titleView)
            textWrap.addView(metaView)
            addView(textWrap)

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                if (hasFocus) {
                    onFocused()
                }
            }
            applyState(false)
            container.addView(this)
        }


    private fun createSearchInput(): EditText =
        EditText(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            includeFontPadding = false
            setText(cityQuery)
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            setTextColor(COLOR_TEXT)
            typeface = portalTypeface
            setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(1))
            background = null
            setSingleLine(true)
        }

    private fun applySearchResult(result: CitySearchResult) {
        val city = result.toSavedCity()
        selectedCity = city
        cityQuery = city.name
        preferences.saveCity(city)
        weatherState = weatherState.copy(searchResults = emptyList(), searchError = null)
        render()
        refreshWeather(city)
    }

    private fun focusFirstLink() {
        container.post {
            requestFirstFocusableDescendant(container)
        }
    }

    private fun focusFocusableTextAt(targetIndex: Int) {
        var focusableIndex = 0
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child is TextView && child.isFocusable) {
                if (focusableIndex == targetIndex) {
                    child.post { child.requestFocus() }
                    return
                }
                focusableIndex += 1
            }
        }
        focusFirstLink()
    }

    private fun restoreArticleScrollAndFocus(
        scrollY: Int,
        preferredFocus: View?,
        articleScrollFocus: View,
    ) {
        mainHandler.post {
            if (scrollY > 0) {
                requestFirstFocusableDescendant(articleScrollFocus)
                scrollView.scrollTo(0, scrollY)
            } else {
                val focusTarget = preferredFocus ?: articleScrollFocus
                requestFirstFocusableDescendant(focusTarget) ||
                    requestFirstFocusableDescendant(articleScrollFocus)
            }
        }
    }

    private fun findViewWithTagRecursive(root: View?, targetTag: String): View? {
        return com.myapp.ui.findViewWithTagRecursive(root, targetTag)
    }

    private fun handleCyclicVerticalFocus(keyCode: Int): Boolean {
        return com.myapp.ui.handleCyclicVerticalFocus(
            keyCode = keyCode,
            activity = activity,
            container = container,
            scrollView = scrollView,
            mainHandler = mainHandler,
            categoryFocusTagPrefix = CATEGORY_FOCUS_TAG_PREFIX,
        )
    }

    private fun handleCategoryVerticalFocus(keyCode: Int): Boolean {
        return com.myapp.ui.handleCategoryVerticalFocus(
            keyCode = keyCode,
            activity = activity,
            container = container,
            scrollView = scrollView,
            mainHandler = mainHandler,
            categoryFocusTagPrefix = CATEGORY_FOCUS_TAG_PREFIX,
        )
    }

    private fun focusViewAndBringIntoView(view: View) {
        com.myapp.ui.focusViewAndBringIntoView(
            view = view,
            scrollView = scrollView,
            mainHandler = mainHandler,
        )
    }

    private fun handleOverlayMenuVerticalFocus(keyCode: Int): Boolean {
        val focusables =
            com.myapp.ui.buildFocusableList(overlayContainer, overlayContainer)
                .filter { view ->
                    val tag = view.tag as? String
                    tag?.startsWith(MENU_OVERLAY_ITEM_TAG_PREFIX) == true
                }
        if (focusables.isEmpty()) {
            return true
        }
        val currentIndex = focusables.indexOf(activity.currentFocus)
        val fallbackIndex = lastMenuFocusedIndex.coerceIn(0, focusables.lastIndex)
        val nextIndex =
            when {
                currentIndex == -1 -> fallbackIndex
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN && currentIndex < focusables.lastIndex -> currentIndex + 1
                keyCode == KeyEvent.KEYCODE_DPAD_UP && currentIndex > 0 -> currentIndex - 1
                else -> currentIndex
            }
        focusOverlayMenuItem(focusables[nextIndex])
        return true
    }

    private fun focusOverlayMenuItem(view: View) {
        view.requestFocus()
        val overlayScroll = menuOverlayScrollView ?: return
        mainHandler.post {
            if (!view.isAttachedToWindow) {
                return@post
            }
            var currentParent: ViewParent? = view.parent
            var isInsideOverlayScroll = false
            while (currentParent is View) {
                if (currentParent === overlayScroll) {
                    isInsideOverlayScroll = true
                    break
                }
                currentParent = (currentParent as View).getParent()
            }
            if (!isInsideOverlayScroll) {
                return@post
            }
            val rect = Rect()
            view.getDrawingRect(rect)
            overlayScroll.offsetDescendantRectToMyCoords(view, rect)
            val viewportTop = overlayScroll.scrollY
            val viewportBottom = overlayScroll.scrollY + overlayScroll.height
            val extra = (overlayScroll.height * 0.08f).toInt().coerceAtLeast(8)
            val nextScrollY =
                when {
                    rect.top - extra < viewportTop -> (rect.top - extra).coerceAtLeast(0)
                    rect.bottom + extra > viewportBottom -> (rect.bottom - overlayScroll.height + extra).coerceAtLeast(0)
                    else -> overlayScroll.scrollY
                }
            if (nextScrollY != overlayScroll.scrollY) {
                overlayScroll.scrollTo(0, nextScrollY)
            }
        }
    }

    private fun restoreUnderlyingFocus(targetTag: String?) {
        if (targetTag.isNullOrBlank()) {
            return
        }
        mainHandler.post {
            findViewWithTagRecursive(container, targetTag)?.requestFocus()
        }
    }

    private fun buildFocusableList(root: View): List<View> {
        return com.myapp.ui.buildFocusableList(root, container)
    }

    private fun collectFocusableViews(view: View, result: MutableList<View>) {
        com.myapp.ui.collectFocusableViews(view, container, result)
    }

    private fun addPassiveFocusAnchor() {
        container.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(1), activity.dp(1))
                isFocusable = true
                isFocusableInTouchMode = false
                post { requestFocus() }
            },
        )
    }

    private fun buildStatusLine(): String {
        val weatherLine = weatherState.data?.let {
            "${selectedCity.name} ${it.temperature.format(0)} C"
        } ?: selectedCity.name
        val usd = ratesState.rates.firstOrNull { it.code == "USD" }?.rate?.format(2)
        val eur = ratesState.rates.firstOrNull { it.code == "EUR" }?.rate?.format(2)

        return buildString {
            append(weatherLine)
            if (usd != null || eur != null) {
                append("  /  ")
                append(listOfNotNull(
                    usd?.let { "USD $it" },
                    eur?.let { "EUR $it" },
                ).joinToString("  "))
            }
        }
    }

    private fun buildHomeWeatherText(): String =
        weatherState.data?.let { "${selectedCity.name} ${it.temperature.format(0)}°" } ?: selectedCity.name

    /**
     * Очистить ресурсы контроллера при выходе (выключение телефона/приложения).
     * Вызывается из MainActivity при destroy или при выходе из приложения.
     */
    fun cleanup() {
        // Очищаем браузер симулятор (кэш и навигационные стеки)
        browserPageManager.reset()
        
        // Отмена всех корутин
        uiScope.cancel()
    }

}
