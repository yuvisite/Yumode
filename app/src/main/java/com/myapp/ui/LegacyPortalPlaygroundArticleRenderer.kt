package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

internal object LegacyPortalPlaygroundArticleRenderer {
    private val theme =
        LegacyPortalArticleTheme(
            shellPaddingTopDp = 1,
            shellPaddingBottomDp = 2,
            contentWidthPxProvider = { activity -> activity.wapContentWidthPx() },
            titleColor = COLOR_PLAYGROUND_TEXT,
            bodyColor = COLOR_PLAYGROUND_TEXT,
            mutedColor = COLOR_PLAYGROUND_MUTED,
            dividerColor = Color.parseColor("#CCCCCC"),
        )

    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        articleState: ArticleUiState,
        brandedHeader: View,
        articleLink: View,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        restoreScrollAndFocus: (Int, View, View) -> Unit,
        scrollY: Int,
    ) {
        LegacyPortalArticleRenderer.render(
            activity = activity,
            container = container,
            articleState = articleState,
            headerView = brandedHeader,
            articleLink = articleLink,
            scrollAnchorView = brandedHeader,
            focusTargetView = articleLink,
            scaledTextSize = scaledTextSize,
            compactLineSpacing = compactLineSpacing,
            typeface = typeface,
            restoreScrollAndFocus = restoreScrollAndFocus,
            scrollY = scrollY,
            theme = theme,
        )
    }
}
