package com.myapp.ui

internal fun legacySectionFeedUrlsForSite(siteId: String): LinkedHashMap<String, String>? =
    when (siteId) {
        "playground" ->
            linkedMapOf(
                "News" to "https://www.playground.ru/rss/news.xml",
                "Articles" to "https://www.playground.ru/rss/articles.xml",
            )

        "stopgame" ->
            linkedMapOf(
                "All" to "https://rss.stopgame.ru/rss_all.xml",
                "News" to "https://rss.stopgame.ru/rss_news.xml",
            )

        "ann" ->
            linkedMapOf(
                "Newsroom" to "https://www.animenewsnetwork.com/newsroom/atom.xml?ann-edition=w",
                "Views" to "https://www.animenewsnetwork.com/views/rss.xml?ann-edition=w",
                "Reviews" to "https://www.animenewsnetwork.com/all-reviews/atom.xml?ann-edition=w",
            )

        else -> null
    }
