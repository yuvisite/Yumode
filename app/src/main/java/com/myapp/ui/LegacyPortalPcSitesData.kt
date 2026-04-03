package com.myapp.ui

internal data class PcSiteLinkSeed(
    val title: String,
    val url: String,
)

internal data class PcSiteCategorySeed(
    val title: String,
    val links: List<PcSiteLinkSeed>,
)

internal fun defaultPcSiteCategories(): List<PcSiteCategorySeed> =
    listOf(
        PcSiteCategorySeed(
            title = "weather",
            links = listOf(
                PcSiteLinkSeed("Sinoptik", "https://sinoptik.ua/ru/pohoda/kyiv"),
                PcSiteLinkSeed("Meteo", "https://meteo.ua/34/kiev#2026-03-26--16-00"),
                PcSiteLinkSeed("Meteofor", "https://meteofor.com.ua/ru/"),
                PcSiteLinkSeed("Foreca", "https://www.foreca.com/ru/100703448/Kyiv-Ukraine"),
                PcSiteLinkSeed("AccuWeather", "https://www.accuweather.com/ru/ua/kyiv/324505/daily-weather-forecast/324505"),
            ),
        ),
        PcSiteCategorySeed(
            title = "maps",
            links = listOf(
                PcSiteLinkSeed("Google Maps", "https://www.google.com/maps/@50.4424231,30.530913,12.75z?entry=ttu&g_ep=EgoyMDI2MDMxOC4xIKXMDSoASAFQAw%3D%3D"),
                PcSiteLinkSeed("EasyWay", "https://easyway.in.ua"),
                PcSiteLinkSeed("OpenStreetMap", "https://www.openstreetmap.org/#map=12/50.4385/30.5080"),
            ),
        ),
        PcSiteCategorySeed(
            title = "video",
            links = listOf(
                PcSiteLinkSeed("Youtube", "https://youtube.com"),
                PcSiteLinkSeed("Invidious", "https://redirect.invidious.io"),
                PcSiteLinkSeed("Odysee", "https://odysee.com"),
            ),
        ),
        PcSiteCategorySeed(
            title = "news",
            links = listOf(
                PcSiteLinkSeed("Ukr Net", "https://ukr.net"),
                PcSiteLinkSeed("Gigazine", "https://gigazine.net/gsc_news/en/"),
            ),
        ),
        PcSiteCategorySeed(
            title = "images",
            links = listOf(
                PcSiteLinkSeed("Tineye", "https://www.tineye.com"),
                PcSiteLinkSeed("Pixiv", "https://pixiv.net"),
            ),
        ),
        PcSiteCategorySeed(
            title = "forums",
            links = listOf(
                PcSiteLinkSeed("Neogaf", "https://neogaf.com"),
                PcSiteLinkSeed("LTT Forum", "https://linustechtips.com"),
                PcSiteLinkSeed("SkyscraperCity", "https://skyscrapercity.com"),
                PcSiteLinkSeed("XDA", "https://forum.xda-developers.com"),
                PcSiteLinkSeed("ARS OpenForum", "https://arstechnica.com/civis/"),
                PcSiteLinkSeed("AnimeSuki", "https://forum.animesuki.com"),
                PcSiteLinkSeed("MAL Forums", "https://myanimelist.net/forum/"),
            ),
        ),
        PcSiteCategorySeed(
            title = "downloads",
            links = listOf(
                PcSiteLinkSeed("MyAbandonware", "https://www.myabandonware.com/"),
                PcSiteLinkSeed("Dedomil", "http://dedomil.net/games/"),
            ),
        ),
        PcSiteCategorySeed(
            title = "entertainment",
            links = listOf(
                PcSiteLinkSeed("MyAnimeList", "https://myanimelist.net"),
                PcSiteLinkSeed("Shikimori", "https://shikimori.one"),
                PcSiteLinkSeed("Rate Your Music", "https://rateyourmusic.com"),
                PcSiteLinkSeed("VNDB", "https://vndb.org"),
                PcSiteLinkSeed("IMDB", "https://imdb.com"),
                PcSiteLinkSeed("Kinorium", "https://kinorium.com"),
                PcSiteLinkSeed("Vse TV", "http://www.vsetv.com/schedule_package_uabase_day_2026-03-24.html"),
            ),
        ),
        PcSiteCategorySeed(
            title = "shopping",
            links = listOf(
                PcSiteLinkSeed("OLX", "https://olx.ua"),
                PcSiteLinkSeed("Hotline", "https://hotline.ua"),
                PcSiteLinkSeed("E-katalog", "https://ek.ua"),
                PcSiteLinkSeed("Rozetka", "https://rozetka.ua"),
                PcSiteLinkSeed("Prom", "https://prom.ua"),
                PcSiteLinkSeed("Shafa", "https://shafa.ua"),
                PcSiteLinkSeed("AliExpress", "https://aliexpress.com"),
            ),
        ),
        PcSiteCategorySeed(
            title = "translators",
            links = listOf(
                PcSiteLinkSeed("DeepL", "https://www.deepl.com/ru/translator"),
                PcSiteLinkSeed("Reverso", "https://context.reverso.net/"),
                PcSiteLinkSeed("GTranslate", "https://translate.google.com/"),
                PcSiteLinkSeed("Goroh", "https://goroh.pp.ua"),
            ),
        ),
        PcSiteCategorySeed(
            title = "file sharing",
            links = listOf(
                PcSiteLinkSeed("GDrive", "https://drive.google.com/"),
                PcSiteLinkSeed("GPhotos", "https://photos.google.com/"),
                PcSiteLinkSeed("Mega", "https://mega.nz/"),
                PcSiteLinkSeed("eDisk", "https://edisk.ukr.net/"),
            ),
        ),
        PcSiteCategorySeed(
            title = "utils",
            links = listOf(
                PcSiteLinkSeed("Wayback", "https://web.archive.org"),
                PcSiteLinkSeed("RemovePaywall", "https://www.removepaywall.com"),
                PcSiteLinkSeed("Postimg", "http://postimg.cc/"),
                PcSiteLinkSeed("Koukoutu", "https://www.koukoutu.com/removebgtool/all"),
                PcSiteLinkSeed("Kaomoji", "https://kaomoji.ru/"),
            ),
        ),
    )

internal fun defaultPcSiteCatalogEntries(): List<CatalogEntrySeed> =
    defaultPcSiteCategories().flatMap { category ->
        category.links.map { link ->
            CatalogEntrySeed(
                title = link.title,
                summary = category.title,
                targetType = "url",
                targetId = link.url,
            )
        }
    }

internal fun defaultPcSiteHomeNavRows(): List<HomeNavRowSeed> {
    var slot = 1
    return defaultPcSiteCategories().map { category ->
        HomeNavRowSeed(
            category = category.title,
            links = category.links.map { link ->
                HomeShortcutSeed(
                    slot = (slot++).toString(),
                    title = link.title,
                    subtitle = category.title,
                    targetType = "url",
                    targetId = link.url,
                )
            },
        )
    }
}
