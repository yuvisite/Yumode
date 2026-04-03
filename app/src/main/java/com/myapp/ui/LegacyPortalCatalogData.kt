package com.myapp.ui

internal data class CatalogEntrySeed(
    val title: String,
    val summary: String,
    val targetType: String,
    val targetId: String,
)

internal data class CatalogPageSeed(
    val id: String,
    val title: String,
    val summary: String,
    val entries: List<CatalogEntrySeed>,
)

internal fun defaultCatalogPageSeeds(): List<CatalogPageSeed> =
    listOf(
        CatalogPageSeed(
            id = CATALOG_REGION_ID,
            title = "Region",
            summary = "Regional and city feeds",
            entries = listOf(
                CatalogEntrySeed("KyivVlada", "Kyiv and city authority news", "site", "kyiv_vlada"),
                CatalogEntrySeed("44.ua", "Kyiv city news", "site", "ua_44"),
                CatalogEntrySeed("VGorode", "Kyiv city feed", "site", "vgorode"),
            ),
        ),
        CatalogPageSeed(
            id = CATALOG_TECH_ID,
            title = "Technology",
            summary = "Selected tech and gadget sources",
            entries = listOf(
                CatalogEntrySeed("Ars Technica", "Hardware, science, software", "site", ARS_SITE_ID),
                CatalogEntrySeed("The Verge", "Gadgets and industry", "site", "theverge"),
                CatalogEntrySeed("ITC.ua", "Tech news", "site", "itc_ua"),
                CatalogEntrySeed("Gagadget", "Gadgets and reviews", "site", "gagadget"),
                CatalogEntrySeed("Mezha", "Tech and culture", "site", "mezha"),
                CatalogEntrySeed("AIN", "Startups, business and tech", "site", "ain"),
                CatalogEntrySeed("Futurism", "Future tech and science", "site", "futurism"),
                CatalogEntrySeed("4PDA", "Devices, Android and mobile tech", "site", "4pda"),
                CatalogEntrySeed("Overclockers.ua", "Hardware, games, reviews", "site", "overclockers_ua"),
            ),
        ),
        CatalogPageSeed(
            id = CATALOG_FUN_ID,
            title = "Entertainment",
            summary = "Culture, media and fun links",
            entries = emptyList(),
        ),
        CatalogPageSeed(
            id = CATALOG_VIDEO_ID,
            title = "Video",
            summary = "Video sites and watch links",
            entries = emptyList(),
        ),
        CatalogPageSeed(
            id = CATALOG_ANIM_ID,
            title = "Animation",
            summary = "Anime and related feeds",
            entries = listOf(
                CatalogEntrySeed("Shikimori", "Community and anime news", "site", "shikimori_news"),
                CatalogEntrySeed("ANN", "Newsroom, views and reviews", "site", "ann"),
            ),
        ),
        CatalogPageSeed(
            id = CATALOG_GAMES_ID,
            title = "Games",
            summary = "Game news and article sections",
            entries = listOf(
                CatalogEntrySeed("Playground", "News and articles inside one site", "site", "playground"),
                CatalogEntrySeed("StopGame", "All and news sections inside one site", "site", "stopgame"),
            ),
        ),
        CatalogPageSeed(
            id = CATALOG_PC_ID,
            title = "PC Sites",
            summary = "External PC web directory",
            entries = defaultPcSiteCatalogEntries(),
        ),
    )
