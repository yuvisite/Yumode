package com.myapp.data.registry

import android.content.Context
import com.myapp.model.ApiModuleKind
import com.myapp.model.ArticleParserSpec
import com.myapp.model.PortalCatalog
import com.myapp.model.PortalSection
import com.myapp.model.PortalSite
import com.myapp.model.PortalSource
import com.myapp.model.PortalSourceType
import com.myapp.model.SitePolicy
import com.myapp.model.SiteStatus
import com.myapp.model.SiteTemplate
import com.myapp.model.SiteTheme
import org.json.JSONArray
import org.json.JSONObject

class AssetPortalRepository(
    private val context: Context,
) {
    fun loadCatalog(): PortalCatalog =
        PortalCatalog(
            sections = parseSections(readAsset("sections.json")),
            sites = parseSites(readAsset("sites.json")),
        )

    private fun readAsset(fileName: String): String =
        context.assets.open(fileName).bufferedReader().use { it.readText() }

    private fun parseSections(json: String): List<PortalSection> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PortalSection(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        summary = item.getString("summary"),
                    ),
                )
            }
        }
    }

    private fun parseSites(json: String): List<PortalSite> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(parseSite(item))
            }
        }
    }

    private fun parseSite(item: JSONObject): PortalSite {
        val sourceType = item.getString("sourceType").toEnum<PortalSourceType>()
        val sourceJson = item.getJSONObject("source")

        return PortalSite(
            id = item.getString("id"),
            sectionId = item.getString("sectionId"),
            title = item.getString("title"),
            subtitle = item.getString("subtitle"),
            summary = item.getString("summary"),
            sourceType = sourceType,
            template = item.getString("template").toEnum<SiteTemplate>(),
            theme = item.optString("theme", "default").toEnum<SiteTheme>(),
            status = item.getString("status").toEnum<SiteStatus>(),
            source = parseSource(sourceType, sourceJson),
        )
    }

    private fun parseSource(
        sourceType: PortalSourceType,
        sourceJson: JSONObject,
    ): PortalSource =
        when (sourceType) {
            PortalSourceType.API -> PortalSource.Api(
                module = sourceJson.getString("module").toEnum<ApiModuleKind>(),
            )

            PortalSourceType.RSS -> PortalSource.Rss(
                feedUrl = sourceJson.getString("feedUrl"),
                homeUrl = sourceJson.getString("homeUrl"),
                policy = parsePolicy(sourceJson.getJSONObject("policy")),
                parserSpec = sourceJson.optJSONObject("parserSpec")?.let(::parseParserSpec),
            )

            PortalSourceType.STATIC -> PortalSource.Static(
                slug = sourceJson.getString("slug"),
            )
        }

    private fun parsePolicy(item: JSONObject): SitePolicy =
        SitePolicy(
            allowedHosts = item.getJSONArray("allowedHosts").toStringSet(),
            allowedSchemes = item.optJSONArray("allowedSchemes")?.toStringSet() ?: setOf("https"),
            maxFeedBytes = item.optInt("maxFeedBytes", 256_000),
            maxArticleBytes = item.optInt("maxArticleBytes", 1_000_000),
            maxRedirects = item.optInt("maxRedirects", 3),
        )

    private fun parseParserSpec(item: JSONObject): ArticleParserSpec =
        ArticleParserSpec(
            parserId = item.getString("parserId"),
            allowedHosts = item.getJSONArray("allowedHosts").toStringList(),
            contentSelectors = item.getJSONArray("contentSelectors").toStringList(),
            titleSelectors = item.optJSONArray("titleSelectors")?.toStringList() ?: listOf("h1"),
            removeSelectors = item.optJSONArray("removeSelectors")?.toStringList() ?: emptyList(),
        )

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (index in 0 until length()) {
                add(getString(index))
            }
        }

    private fun JSONArray.toStringSet(): Set<String> =
        toStringList().toSet()

    private inline fun <reified T : Enum<T>> String.toEnum(): T =
        enumValueOf(normalizeEnumName())

    private fun String.normalizeEnumName(): String =
        replace('-', '_')
            .replace(' ', '_')
            .uppercase()
}
