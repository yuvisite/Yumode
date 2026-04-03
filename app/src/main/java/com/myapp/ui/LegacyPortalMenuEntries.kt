package com.myapp.ui

import com.myapp.model.SanitizedArticle

internal data class LegacyPortalMenuEntry(
    val id: Int,
    val title: String,
    val enabled: Boolean,
    val action: (() -> Unit)?,
)

internal data class LegacyPortalMenuBuildInput(
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val openHomeAction: () -> Unit,
    val reloadAction: (() -> Unit)?,
    val openBookmarksAction: () -> Unit,
    val openSavedArticlesAction: () -> Unit,
    val openHistoryAction: () -> Unit,
    val bookmarkSiteId: String?,
    val bookmarkSiteAlreadyAdded: Boolean,
    val onAddBookmark: (String) -> Unit,
    val onRemoveBookmark: (String) -> Unit,
    val articleSiteId: String?,
    val article: SanitizedArticle?,
    val isArticleSaved: Boolean,
    val onSaveArticle: (String, SanitizedArticle) -> Unit,
    val onRemoveSavedArticle: (String) -> Unit,
    val openPcSiteAction: (() -> Unit)?,
    val openPageInfoAction: (() -> Unit)?,
    val exitAction: () -> Unit,
)

internal object LegacyPortalMenuEntries {
    internal fun build(input: LegacyPortalMenuBuildInput): List<LegacyPortalMenuEntry> {
        val bookmarkTitle =
            when {
                input.bookmarkSiteId.isNullOrBlank() -> "Add Bookmark"
                input.bookmarkSiteAlreadyAdded -> "Remove Bookmark"
                else -> "Add Bookmark"
            }

        val bookmarkAction =
            input.bookmarkSiteId?.let { siteId ->
                if (input.bookmarkSiteAlreadyAdded) {
                    { input.onRemoveBookmark(siteId) }
                } else {
                    { input.onAddBookmark(siteId) }
                }
            }

        val articleKeyUrl = input.article?.finalUrl?.ifBlank { input.article.sourceUrl }.orEmpty()

        val articleTitle =
            when {
                input.articleSiteId == null -> "Add Article"
                input.article == null -> "Add Article"
                input.isArticleSaved -> "Delete Saved Article"
                else -> "Add Article"
            }

        val articleAction =
            when {
                input.articleSiteId == null -> null
                input.article == null -> null
                articleKeyUrl.isBlank() -> null
                input.isArticleSaved -> ({ input.onRemoveSavedArticle(articleKeyUrl) })
                else -> {
                    val articleSiteId = input.articleSiteId
                    val article = input.article
                    ({ input.onSaveArticle(articleSiteId, article) })
                }
            }

        return listOf(
            LegacyPortalMenuEntry(
                id = MENU_RELOAD,
                title = "Reload",
                enabled = input.reloadAction != null,
                action = input.reloadAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_HOME,
                title = "Home",
                enabled = true,
                action = input.openHomeAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_BACK,
                title = "Back",
                enabled = input.canGoBack,
                action = if (input.canGoBack) input.onBack else null,
            ),
            LegacyPortalMenuEntry(
                id = MENU_FORWARD,
                title = "Forward",
                enabled = input.canGoForward,
                action = if (input.canGoForward) input.onForward else null,
            ),
            LegacyPortalMenuEntry(
                id = MENU_ADD_BOOKMARK,
                title = bookmarkTitle,
                enabled = bookmarkAction != null,
                action = bookmarkAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_SAVE_ARTICLE,
                title = articleTitle,
                enabled = articleAction != null,
                action = articleAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_OPEN_PC_SITE,
                title = "Open PC site",
                enabled = input.openPcSiteAction != null,
                action = input.openPcSiteAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_HISTORY,
                title = "History",
                enabled = true,
                action = input.openHistoryAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_BOOKMARKS,
                title = "Bookmarks",
                enabled = true,
                action = input.openBookmarksAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_SAVED_ARTICLES,
                title = "Saved Articles",
                enabled = true,
                action = input.openSavedArticlesAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_PAGE_INFO,
                title = "Page Info",
                enabled = input.openPageInfoAction != null,
                action = input.openPageInfoAction,
            ),
            LegacyPortalMenuEntry(
                id = MENU_EXIT,
                title = "Exit",
                enabled = true,
                action = input.exitAction,
            ),
        )
    }
}

