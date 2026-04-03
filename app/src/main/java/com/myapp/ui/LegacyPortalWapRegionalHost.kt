package com.myapp.ui

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import com.myapp.model.SiteTheme

/**
 * Wires regional WAP sites into the portal without growing [LegacyPortalController].
 */
internal object LegacyPortalWapRegionalHost {
    internal fun isRegionalFeedTheme(theme: SiteTheme): Boolean =
        theme == SiteTheme.KYIV_VLADA ||
            theme == SiteTheme.UA_44 ||
            theme == SiteTheme.VGORODE

    internal fun renderFeed(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        state: FeedUiState,
        categories: List<String>,
        selectedCategory: String?,
        filteredItems: List<FeedItem>,
        pagerState: FeedPagerState,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onCategorySelected: (String?) -> Unit,
        onOpenArticle: (FeedItem) -> Unit,
        onHeadlineFocused: (Int) -> Unit,
    ) {
        val palette = LegacyPortalWapRegionalSiteRenderer.paletteFor(site.theme) ?: return
        LegacyPortalWapRegionalSiteRenderer.render(
            activity = activity,
            container = container,
            site = site,
            palette = palette,
            state = state,
            categories = categories,
            selectedCategory = selectedCategory,
            filteredItems = filteredItems,
            pagerState = pagerState,
            buildLogo = {
                LegacyPortalWapRegionalSiteRenderer.buildLogoView(
                    activity = activity,
                    theme = site.theme,
                    scaledTextSize = scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = typeface,
                )
            },
            scaledTextSize = scaledTextSize,
            compactLineSpacing = compactLineSpacing,
            typeface = typeface,
            onCategorySelected = onCategorySelected,
            onOpenArticle = onOpenArticle,
            onHeadlineFocused = onHeadlineFocused,
        )
    }

    internal fun buildArticleChrome(
        activity: AppCompatActivity,
        site: PortalSite,
        categories: List<String>,
        selectedCategory: String?,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onCategorySelected: (String?) -> Unit,
    ): Pair<WapRegionalPalette, View> {
        val palette = LegacyPortalWapRegionalSiteRenderer.paletteFor(site.theme)!!
        val header =
            LegacyPortalWapRegionalSiteRenderer.buildBrandedHeader(
                activity = activity,
                siteId = site.id,
                siteTheme = site.theme,
                palette = palette,
                categories = categories,
                selectedCategory = selectedCategory,
                buildLogo = {
                    LegacyPortalWapRegionalSiteRenderer.buildLogoView(
                        activity = activity,
                        theme = site.theme,
                        scaledTextSize = scaledTextSize,
                        compactLineSpacing = compactLineSpacing,
                        typeface = typeface,
                    )
                },
                scaledTextSize = scaledTextSize,
                compactLineSpacing = compactLineSpacing,
                typeface = typeface,
                onCategorySelected = onCategorySelected,
            )
        return palette to header
    }
}
