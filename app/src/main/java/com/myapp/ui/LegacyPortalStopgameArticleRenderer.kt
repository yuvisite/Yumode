package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

internal object LegacyPortalStopgameArticleRenderer {
    private val theme =
        LegacyPortalArticleTheme(
            shellPaddingTopDp = 1,
            shellPaddingBottomDp = 2,
            shellGravity = Gravity.CENTER_HORIZONTAL,
            contentWidthPxProvider = { activity -> activity.wapFeedColumnWidthPx() },
            titleColor = COLOR_STOPGAME_TEXT,
            bodyColor = COLOR_STOPGAME_TEXT,
            mutedColor = COLOR_STOPGAME_META,
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
