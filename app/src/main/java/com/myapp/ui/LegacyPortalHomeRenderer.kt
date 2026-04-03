package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalCatalog
import com.myapp.model.PortalSite
import com.myapp.model.PortalSource
import com.myapp.model.SiteStatus
import java.util.Locale

internal object LegacyPortalHomeRenderer {
    private data class HomeShortcut(
        val slot: String,
        val title: String,
        val subtitle: String,
        val onClick: () -> Unit,
    )

    private data class HomeNavRow(
        val category: String,
        val links: List<HomeShortcut>,
    )

    private data class HomeNewsHeadline(
        val site: PortalSite,
        val item: FeedItem,
    )

    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        catalog: PortalCatalog,
        feedStates: Map<String, FeedUiState>,
        selectedNewsHubCategoryId: String,
        setSelectedNewsHubCategoryId: (String) -> Unit,
        setPendingFocusTag: (String) -> Unit,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        statusLine: String,
        weatherText: String,
        searchRow: View,
        openSite: (String) -> Unit,
        openSection: (String) -> Unit,
        openExternalUrl: (String) -> Unit,
        openCatalogPage: (String) -> Unit,
        openArticle: (PortalSite, FeedItem) -> Unit,
        rerender: () -> Unit,
        focusFirstLink: () -> Unit,
        onOpenSettings: () -> Unit,
        createCategoryRow: (Boolean) -> LinearLayout,
        buildHomePortalLogoText: () -> CharSequence,
    ) {
        addHomeSpecHeader(
            activity, container, portalTypeface, compactLineSpacing, scaledTextSize,
            weatherText, openSite, buildHomePortalLogoText,
        )
        container.addView(searchRow)
        addHomeCatalogTopLinks(
            activity, container, portalTypeface, compactLineSpacing, scaledTextSize, createCategoryRow,
            listOf(
                "Weather" to { openSite("weather_city") },
                "Region" to { openCatalogPage(CATALOG_REGION_ID) },
                "Tech" to { openCatalogPage(CATALOG_TECH_ID) },
                "PC Sites" to { openCatalogPage(CATALOG_PC_ID) },
                "Animation" to { openCatalogPage(CATALOG_ANIM_ID) },
                "Games" to { openCatalogPage(CATALOG_GAMES_ID) },
                "Rates" to { openSite("money_rates") },
            ),
        )
        addDivider(activity, container)
        addHomeNewsHubBlock(
            activity = activity,
            container = container,
            catalog = catalog,
            feedStates = feedStates,
            selectedNewsHubCategoryId = selectedNewsHubCategoryId,
            setSelectedNewsHubCategoryId = setSelectedNewsHubCategoryId,
            setPendingFocusTag = setPendingFocusTag,
            portalTypeface = portalTypeface,
            compactLineSpacing = compactLineSpacing,
            scaledTextSize = scaledTextSize,
            openArticle = openArticle,
            rerender = rerender,
        )
        addDivider(activity, container)
        container.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = activity.dp(2)
                }
                includeFontPadding = false
                text = "PC Sites Catalogue"
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, compactLineSpacing)
                setTypeface(portalTypeface, Typeface.BOLD)
                setTextColor(COLOR_HOME_TEXT)
            },
        )
        val rows =
            defaultHomeNavRowSeeds().map { row ->
                HomeNavRow(
                    category = row.category,
                    links = row.links.map { shortcut ->
                        HomeShortcut(
                            slot = shortcut.slot,
                            title = shortcut.title,
                            subtitle = shortcut.subtitle,
                            onClick = {
                                when (shortcut.targetType) {
                                    "site" -> openSite(shortcut.targetId)
                                    "section" -> openSection(shortcut.targetId)
                                    "url" -> openExternalUrl(shortcut.targetId)
                                    "settings" -> onOpenSettings()
                                }
                            },
                        )
                    },
                )
            }
        addHomeNavRows(activity, container, rows, portalTypeface, compactLineSpacing, scaledTextSize)
        focusFirstLink()
    }

    internal fun renderNewsHubSection(
        activity: AppCompatActivity,
        container: LinearLayout,
        catalog: PortalCatalog,
        selectedNewsHubCategoryId: String,
        setPendingFocusTag: (String) -> Unit,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        openSite: (String) -> Unit,
        onCategorySelected: (String) -> Unit,
        focusViewAndBringIntoView: (View) -> Unit,
    ) {
        val wrap = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
        }
        container.addView(wrap)
        val categoriesColumn = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(84), ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
        }
        val contentColumn = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), 0, 0, 0)
        }
        val categories = newsHubCategories()
        categories.forEachIndexed { index, category ->
            categoriesColumn.addView(
                createNewsHubCategoryView(
                    activity, category, index + 1, selectedNewsHubCategoryId, portalTypeface, compactLineSpacing, scaledTextSize,
                    onClick = {
                        setPendingFocusTag(newsHubCategoryTag(category.id))
                        onCategorySelected(category.id)
                    },
                ).apply {
                    if (category.id == selectedNewsHubCategoryId) {
                        post { focusViewAndBringIntoView(this) }
                    }
                },
            )
        }
        val selectedCategory = categories.firstOrNull { it.id == selectedNewsHubCategoryId } ?: categories.first()
        contentColumn.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = activity.dp(2)
                }
                includeFontPadding = false
                text = selectedCategory.title
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, compactLineSpacing)
                setPadding(activity.dp(2), 0, 0, 0)
                typeface = portalTypeface
                setTypeface(portalTypeface, Typeface.BOLD)
                setTextColor(COLOR_HOME_TEXT)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(activity.dp(2), COLOR_HOME_FOCUS)
                }
            },
        )
        val sites = newsHubSitesForCategory(catalog, selectedCategory.id)
        if (sites.isNotEmpty()) {
            sites.forEachIndexed { index, site ->
                contentColumn.addView(
                    createNewsHubContentLink(
                        activity = activity,
                        label = "${index + 1}. ${site.title}",
                        summary = site.summary,
                        portalTypeface = portalTypeface,
                        compactLineSpacing = compactLineSpacing,
                        scaledTextSize = scaledTextSize,
                        onClick = { openSite(site.id) },
                    ),
                )
            }
        } else {
            contentColumn.addView(createNewsHubPlaceholderText(activity, "${selectedCategory.title} / coming soon", portalTypeface, compactLineSpacing, scaledTextSize))
            contentColumn.addView(createNewsHubPlaceholderText(activity, selectedCategory.description, portalTypeface, compactLineSpacing, scaledTextSize))
        }
        wrap.addView(categoriesColumn)
        wrap.addView(contentColumn)
    }

    private fun addHomeNewsHubBlock(
        activity: AppCompatActivity,
        container: LinearLayout,
        catalog: PortalCatalog,
        feedStates: Map<String, FeedUiState>,
        selectedNewsHubCategoryId: String,
        setSelectedNewsHubCategoryId: (String) -> Unit,
        setPendingFocusTag: (String) -> Unit,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        openArticle: (PortalSite, FeedItem) -> Unit,
        rerender: () -> Unit,
    ) {
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, activity.dp(2), 0, 0)
        }
        val categoriesColumn = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(52), ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
        }
        val contentColumn = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), 0, 0, 0)
        }
        val categories = newsHubCategories()
        categories.forEachIndexed { index, category ->
            categoriesColumn.addView(
                createNewsHubCategoryView(
                    activity, category, index + 1, selectedNewsHubCategoryId, portalTypeface, compactLineSpacing, scaledTextSize,
                    onClick = {
                        setSelectedNewsHubCategoryId(category.id)
                        setPendingFocusTag(newsHubCategoryTag(category.id))
                        rerender()
                    },
                ),
            )
        }
        val selectedCategory = categories.firstOrNull { it.id == selectedNewsHubCategoryId } ?: categories.first()
        val headlines = homeNewsHeadlines(catalog, feedStates, selectedCategory.id)
        if (headlines.isEmpty()) {
            contentColumn.addView(createNewsHubPlaceholderText(activity, "${selectedCategory.title}:", portalTypeface, compactLineSpacing, scaledTextSize))
            contentColumn.addView(createNewsHubPlaceholderText(activity, "no entries yet", portalTypeface, compactLineSpacing, scaledTextSize))
            contentColumn.addView(createNewsHubPlaceholderText(activity, selectedCategory.description, portalTypeface, compactLineSpacing, scaledTextSize))
        } else {
            headlines.forEachIndexed { index, headline ->
                contentColumn.addView(
                    createNewsHubContentLink(
                        activity, "${index + 1}.",
                        headline.item.title.ifBlank { headline.item.url },
                        portalTypeface, compactLineSpacing, scaledTextSize,
                        onClick = { openArticle(headline.site, headline.item) },
                    ),
                )
            }
        }
        wrap.addView(categoriesColumn)
        wrap.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(1), ViewGroup.LayoutParams.MATCH_PARENT).apply { marginStart = activity.dp(2) }
                setBackgroundColor(COLOR_DIVIDER)
            },
        )
        wrap.addView(contentColumn)
        container.addView(wrap)
    }

    private fun homeNewsHeadlines(catalog: PortalCatalog, feedStates: Map<String, FeedUiState>, categoryId: String): List<HomeNewsHeadline> {
        val sites = newsHubSitesForCategory(catalog, categoryId)
        return sites.asSequence()
            .filter { it.status == SiteStatus.ACTIVE && it.source is PortalSource.Rss }
            .flatMap { site -> feedStates[site.id]?.items.orEmpty().asSequence().take(2).map { item -> HomeNewsHeadline(site, item) } }
            .take(5)
            .toList()
    }

    private fun newsHubSitesForCategory(catalog: PortalCatalog, categoryId: String): List<PortalSite> =
        when (categoryId) {
            CATALOG_REGION_ID -> regionSites(catalog)
            NEWS_HUB_TECH_ID -> technologySites(catalog)
            CATALOG_GAMES_ID -> gameSites(catalog)
            CATALOG_ANIM_ID -> animationSites(catalog)
            else -> emptyList()
        }

    private fun addHomeNavRows(
        activity: AppCompatActivity,
        container: LinearLayout,
        rows: List<HomeNavRow>,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
    ) {
        rows.forEach { navRow ->
            val wrap = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(2) }
            }
            val labelWidth =
                TextPaint().apply {
                    textSize = activity.spToPx(scaledTextSize(12f))
                    typeface = Typeface.create(portalTypeface, Typeface.BOLD)
                }.measureText("${navRow.category} ").toInt() + activity.dp(4)
            val maxWidth = activity.resources.displayMetrics.widthPixels - activity.dp(4)
            var row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER_VERTICAL
            }
            var currentWidth = 0
            row.addView(
                TextView(activity).apply {
                    includeFontPadding = false
                    text = "${navRow.category} "
                    textSize = scaledTextSize(12f)
                    setLineSpacing(0f, compactLineSpacing)
                    setTypeface(portalTypeface, Typeface.BOLD)
                    setTextColor(COLOR_HOME_TEXT)
                },
            )
            currentWidth += labelWidth
            navRow.links.forEachIndexed { index, shortcut ->
                val itemWidth =
                    TextPaint().apply {
                        textSize = activity.spToPx(scaledTextSize(12f))
                        typeface = portalTypeface
                    }.measureText(shortcut.title).toInt() + activity.dp(if (index > 0) 12 else 8)
                if (currentWidth > labelWidth && currentWidth + itemWidth > maxWidth) {
                    wrap.addView(row)
                    row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(Space(activity).apply { layoutParams = LinearLayout.LayoutParams(labelWidth, 0) })
                    currentWidth = labelWidth
                }
                row.addView(
                    createHomeNavLink(activity, shortcut, portalTypeface, compactLineSpacing, scaledTextSize).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            if (currentWidth > labelWidth) marginStart = activity.dp(4)
                        }
                    },
                )
                currentWidth += itemWidth
            }
            wrap.addView(row)
            container.addView(wrap)
        }
    }

    private fun createHomeNavLink(
        activity: AppCompatActivity,
        shortcut: HomeShortcut,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
    ): TextView =
        TextView(activity).apply {
            includeFontPadding = false
            text = shortcut.title
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            fun applyState(focused: Boolean) {
                setBackgroundColor(Color.TRANSPARENT)
                text = if (focused) buildStyledText(shortcut.title, COLOR_HOME_FOCUS, underline = true) else buildStyledText(shortcut.title, COLOR_HOME_LINK)
                setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }
            setOnClickListener { shortcut.onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun addHomeCatalogTopLinks(
        activity: AppCompatActivity,
        container: LinearLayout,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        createCategoryRow: (Boolean) -> LinearLayout,
        items: List<Pair<String, () -> Unit>>,
    ) {
        val rows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val maxWidth = activity.resources.displayMetrics.widthPixels - activity.dp(8)
        var currentRow = createCategoryRow(false)
        var currentWidth = 0
        items.forEachIndexed { index, item ->
            val pieceWidth =
                TextPaint().apply {
                    textSize = activity.spToPx(scaledTextSize(11f))
                    typeface = portalTypeface
                }.measureText(item.first).toInt() + activity.dp(if (index == 0 || currentWidth == 0) 4 else 12)
            if (currentWidth > 0 && currentWidth + pieceWidth > maxWidth) {
                rows.addView(currentRow)
                currentRow = createCategoryRow(false)
                currentWidth = 0
            }
            if (currentRow.childCount > 0) {
                currentRow.addView(Space(activity).apply { layoutParams = LinearLayout.LayoutParams(activity.dp(4), 0) })
            }
            currentRow.addView(
                createHomeCatalogTopLink(activity, item.first, portalTypeface, compactLineSpacing, scaledTextSize, item.second),
            )
            currentWidth += pieceWidth
        }
        if (currentRow.childCount > 0) rows.addView(currentRow)
        container.addView(rows)
    }

    private fun createHomeCatalogTopLink(
        activity: AppCompatActivity,
        title: String,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            fun applyState(focused: Boolean) {
                setBackgroundColor(Color.TRANSPARENT)
                text = buildStyledText(title, if (focused) COLOR_HOME_FOCUS else COLOR_HOME_LINK, underline = focused)
                setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun addHomeSpecHeader(
        activity: AppCompatActivity,
        container: LinearLayout,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        weatherText: String,
        openSite: (String) -> Unit,
        buildHomePortalLogoText: () -> CharSequence,
    ) {
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(1) }
            background = createHomeRectDrawable(activity, Color.WHITE, Color.TRANSPARENT)
            setPadding(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2))
        }
        wrap.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                includeFontPadding = false
                text = buildHomePortalLogoText()
                textSize = scaledTextSize(14f)
                setLineSpacing(0f, compactLineSpacing)
                setTypeface(portalTypeface, Typeface.BOLD)
                isFocusable = true
                isClickable = true
                isFocusableInTouchMode = false
            },
        )
        wrap.addView(
            TextView(activity).apply {
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                setTextColor(COLOR_HOME_MUTED)
                text = weatherText
                isFocusable = true
                isClickable = true
                isFocusableInTouchMode = false
                setOnClickListener { openSite("weather_city") }
            },
        )
        container.addView(wrap)
    }

    private fun createNewsHubCategoryView(
        activity: AppCompatActivity,
        category: NewsHubCategory,
        number: Int,
        selectedNewsHubCategoryId: String,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(2) }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = newsHubCategoryTag(category.id)
            fun applyState(focused: Boolean) {
                val selected = category.id == selectedNewsHubCategoryId
                text =
                    when {
                        selected && focused -> buildStyledText("$number ${category.title}", COLOR_HOME_FOCUS, underline = true)
                        selected -> buildStyledText("$number ${category.title}", COLOR_HOME_TEXT)
                        focused -> buildStyledText("$number ${category.title}", COLOR_HOME_FOCUS, underline = true)
                        else -> buildStyledText("$number ${category.title}", COLOR_HOME_LINK)
                    }
                setTypeface(portalTypeface, if (selected || focused) Typeface.BOLD else Typeface.NORMAL)
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun createNewsHubContentLink(
        activity: AppCompatActivity,
        label: String,
        summary: String,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(2) }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            fun applyState(focused: Boolean) {
                text = buildNewsHubContentText(label, summary, focused)
                setTypeface(portalTypeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun createNewsHubPlaceholderText(
        activity: AppCompatActivity,
        text: String,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(2) }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setTextColor(COLOR_HOME_MUTED)
        }

    private fun buildNewsHubContentText(label: String, summary: String, focused: Boolean): CharSequence =
        SpannableString("$label $summary").apply {
            setSpan(ForegroundColorSpan(if (focused) COLOR_HOME_FOCUS else COLOR_HOME_LINK), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(if (focused) COLOR_HOME_TEXT else COLOR_HOME_MUTED), label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun createHomeRectDrawable(activity: AppCompatActivity, fillColor: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(activity.dp(1), strokeColor)
        }

    private fun addDivider(activity: AppCompatActivity, container: LinearLayout) {
        container.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(1))
                setBackgroundColor(COLOR_DIVIDER)
            },
        )
    }
}
