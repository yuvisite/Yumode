package com.myapp.model

enum class PortalSourceType {
    API,
    RSS,
    STATIC,
}

enum class SiteTemplate {
    PORTAL_LIST,
    FEED_LIST,
    ARTICLE_VIEW,
    API_PANEL,
}

enum class SiteTheme {
    DEFAULT,
    ARS,
    ITC,
    VERGE,
    GAGADGET,
    MEZHA,
    STOPGAME,
    PLAYGROUND,
    KYIV_VLADA,
    UA_44,
    VGORODE,
}

enum class SiteStatus {
    ACTIVE,
    PLANNED,
}

enum class ApiModuleKind {
    WEATHER,
    CURRENCY,
}

data class PortalSection(
    val id: String,
    val title: String,
    val summary: String,
)

data class ArticleParserSpec(
    val parserId: String,
    val allowedHosts: List<String>,
    val contentSelectors: List<String>,
    val titleSelectors: List<String> = listOf("h1"),
    val removeSelectors: List<String> = emptyList(),
)

data class SitePolicy(
    val allowedHosts: Set<String>,
    val allowedSchemes: Set<String> = setOf("https"),
    val maxFeedBytes: Int = 256_000,
    val maxArticleBytes: Int = 1_000_000,
    val maxRedirects: Int = 3,
)

sealed interface PortalSource {
    data class Api(
        val module: ApiModuleKind,
    ) : PortalSource

    data class Rss(
        val feedUrl: String,
        val homeUrl: String,
        val policy: SitePolicy,
        val parserSpec: ArticleParserSpec?,
    ) : PortalSource

    data class Static(
        val slug: String,
    ) : PortalSource
}

data class PortalSite(
    val id: String,
    val sectionId: String,
    val title: String,
    val subtitle: String,
    val summary: String,
    val sourceType: PortalSourceType,
    val template: SiteTemplate,
    val theme: SiteTheme = SiteTheme.DEFAULT,
    val status: SiteStatus,
    val source: PortalSource,
)

data class PortalCatalog(
    val sections: List<PortalSection>,
    val sites: List<PortalSite>,
) {
    fun sectionById(sectionId: String): PortalSection =
        sections.first { it.id == sectionId }

    fun siteById(siteId: String): PortalSite =
        sites.first { it.id == siteId }

    fun sitesInSection(sectionId: String): List<PortalSite> =
        sites.filter { it.sectionId == sectionId }

    fun siteCount(sectionId: String): Int =
        sites.count { it.sectionId == sectionId }
}
