package com.myapp.ui

import com.myapp.model.PortalCatalog
import com.myapp.model.PortalSite

internal data class HomeShortcutSeed(
    val slot: String,
    val title: String,
    val subtitle: String,
    val targetType: String,
    val targetId: String,
)

internal data class HomeNavRowSeed(
    val category: String,
    val links: List<HomeShortcutSeed>,
)

internal fun defaultHomeNavRowSeeds(): List<HomeNavRowSeed> =
    defaultPcSiteHomeNavRows()

internal data class NewsHubCategory(
    val id: String,
    val title: String,
    val description: String,
)

internal fun newsHubCategories(): List<NewsHubCategory> =
    listOf(
        NewsHubCategory(CATALOG_REGION_ID, "Regi", "regional city feeds"),
        NewsHubCategory(NEWS_HUB_TECH_ID, "Tech", "all current sources"),
        NewsHubCategory(CATALOG_GAMES_ID, "Game", "games and updates"),
        NewsHubCategory(CATALOG_ANIM_ID, "Anim", "anime and clips"),
    )

internal fun regionSites(catalog: PortalCatalog): List<PortalSite> =
    listOfNotNull(
        catalog.sites.firstOrNull { it.id == "kyiv_vlada" },
        catalog.sites.firstOrNull { it.id == "ua_44" },
        catalog.sites.firstOrNull { it.id == "vgorode" },
    )

internal fun technologySites(catalog: PortalCatalog): List<PortalSite> =
    listOfNotNull(
        catalog.sites.firstOrNull { it.id == ARS_SITE_ID },
        catalog.sites.firstOrNull { it.id == "itc_ua" },
        catalog.sites.firstOrNull { it.id == "mezha" },
        catalog.sites.firstOrNull { it.id == "gagadget" },
        catalog.sites.firstOrNull { it.id == "theverge" },
        catalog.sites.firstOrNull { it.id == "ain" },
        catalog.sites.firstOrNull { it.id == "futurism" },
        catalog.sites.firstOrNull { it.id == "4pda" },
        catalog.sites.firstOrNull { it.id == "overclockers_ua" },
    )

internal fun gameSites(catalog: PortalCatalog): List<PortalSite> =
    listOfNotNull(
        catalog.sites.firstOrNull { it.id == "stopgame" },
        catalog.sites.firstOrNull { it.id == "playground" },
    )

internal fun animationSites(catalog: PortalCatalog): List<PortalSite> =
    listOfNotNull(
        catalog.sites.firstOrNull { it.id == "playground" },
    )

internal fun newsHubCategoryTag(categoryId: String): String =
    "${CATEGORY_FOCUS_TAG_PREFIX}news-hub:$categoryId"
