package com.myapp.ui

import android.graphics.Color
import com.myapp.model.SavedCity

internal val LEGACY_QUICK_CITIES =
    listOf(
        SavedCity("Kyiv", "Kyiv", "UA", 50.4501, 30.5234, "Europe/Kiev"),
        SavedCity("Lviv", "Lviv", "UA", 49.8397, 24.0297, "Europe/Kiev"),
        SavedCity("Odesa", "Odesa", "UA", 46.4825, 30.7233, "Europe/Kiev"),
        SavedCity("Dnipro", "Dnipropetrovsk", "UA", 48.4647, 35.0462, "Europe/Kiev"),
        SavedCity("Kharkiv", "Kharkiv", "UA", 49.9935, 36.2304, "Europe/Kiev"),
    )
internal const val ARS_SITE_ID = "ars_news"
internal const val ARS_LOGO_ASSET = "sites/ars/logo.png"
internal const val ARS_FEED_PAGE_SIZE = 10
internal const val ITC_SITE_ID = "itc_ua"
internal const val ITC_LOGO_ASSET = "sites/itc/logo.png"
internal const val GAGADGET_LOGO_ASSET = "sites/gagadget/logo.png"
internal const val LANA_PIXEL_FONT_ASSET = "fonts/LanaPixel.ttf"
internal const val GAGADGET_FEED_PAGE_SIZE = 10
internal const val FONT_ID_CLASSIC = "classic"
internal const val FONT_ID_LANA_PIXEL = "lana_pixel"
internal const val HOME_HEADLINE_COUNT = 4
internal const val LANA_PIXEL_TEXT_SCALE = 1.4f
internal const val MENU_BACK = 30_001
internal const val MENU_FORWARD = 30_002
internal const val MENU_HOME = 30_013
internal const val MENU_RELOAD = 30_003
internal const val MENU_BOOKMARKS = 30_004
internal const val MENU_ADD_BOOKMARK = 30_005
internal const val MENU_REMOVE_BOOKMARK = 30_006
internal const val MENU_PAGE_INFO = 30_007
internal const val MENU_EXIT = 30_008
internal const val MENU_SAVE_ARTICLE = 30_009
internal const val MENU_REMOVE_SAVED_ARTICLE = 30_010
internal const val MENU_HISTORY = 30_011
internal const val MENU_SAVED_ARTICLES = 30_012
internal const val MENU_OPEN_PC_SITE = 30_014
internal const val CATEGORY_FOCUS_TAG_PREFIX = "category-focus:"
internal const val CATALOG_PAGE_SIZE = 9
internal const val CATALOG_NEWS_ID = "news"
internal const val CATALOG_TECH_ID = "technology"
internal const val CATALOG_FUN_ID = "entertainment"
internal const val CATALOG_VIDEO_ID = "video"
internal const val CATALOG_ANIM_ID = "animation"
internal const val CATALOG_GAMES_ID = "games"
internal const val CATALOG_PC_ID = "pc_sites"
internal const val CATALOG_REGION_ID = "region"
internal const val NEWS_HUB_TECH_ID = "technology"
internal const val BULLET_PREFIX = "\u25cf "
internal const val ARTICLE_SCROLL_FOCUS_TAG = "article-scroll-focus"
internal const val CURRENCY_CONVERTER_AMOUNT_TAG = "currency-converter-amount"
internal const val CURRENCY_CONVERTER_FROM_TAG = "currency-converter-from"
internal const val CURRENCY_CONVERTER_TO_TAG = "currency-converter-to"
internal const val COLOR_HEADER = -3407718
internal const val COLOR_DIVIDER = -5592406
internal const val COLOR_LINK = -16776978
internal const val COLOR_TEXT = -15724528
internal const val COLOR_MUTED = -7829368
internal const val COLOR_ERROR = -5636096
internal const val COLOR_FOCUS = -16777088
internal val COLOR_HOME_BG: Int = Color.parseColor("#FFFFFF")
internal val COLOR_HOME_PANEL: Int = Color.parseColor("#F0F0F0")
internal val COLOR_HOME_PANEL_LIGHT: Int = Color.parseColor("#F0F0F0")
internal val COLOR_HOME_BORDER: Int = Color.parseColor("#CCCCCC")
internal val COLOR_HOME_BORDER_LIGHT: Int = Color.parseColor("#CCCCCC")
internal val COLOR_HOME_CELL: Int = Color.parseColor("#E8E8E8")
internal val COLOR_HOME_CELL_BORDER: Int = Color.parseColor("#999999")
internal val COLOR_HOME_TEXT: Int = Color.parseColor("#222222")
internal val COLOR_HOME_LINK: Int = Color.parseColor("#0000FF")
internal val COLOR_HOME_MUTED: Int = Color.parseColor("#555555")
internal val COLOR_HOME_SEARCH_BG: Int = Color.parseColor("#F0F0F0")
internal val COLOR_HOME_FOCUS: Int = Color.parseColor("#1E50B5")
internal val COLOR_HOME_FOCUS_LIGHT: Int = Color.parseColor("#E8EEF9")
internal val COLOR_HOME_SUBTEXT_FOCUS: Int = Color.parseColor("#1E50B5")
internal val COLOR_HOME_HEADER: Int = Color.parseColor("#FFFFFF")
internal val COLOR_HOME_LOGO: Int = Color.parseColor("#C72A20")
internal val COLOR_HOME_ALERT: Int = Color.parseColor("#C72A20")
internal val COLOR_ARS_BG: Int = Color.parseColor("#E9EAED")
internal val COLOR_ARS_BAR: Int = Color.parseColor("#232428")
internal val COLOR_ARS_TEXT: Int = Color.parseColor("#232428")
internal val COLOR_ARS_BAR_TEXT: Int = Color.parseColor("#E9EAED")
internal val COLOR_ARS_HIGHLIGHT: Int = Color.parseColor("#FF4E00")
internal val COLOR_ARS_MUTED: Int = Color.parseColor("#64686D")
internal val COLOR_ARS_LINK: Int = Color.parseColor("#D5DAE0")
internal val COLOR_ARS_SEPARATOR: Int = Color.parseColor("#8E949B")
internal val COLOR_ITC_BG: Int = Color.parseColor("#EAF7F7")
internal val COLOR_ITC_ACCENT: Int = Color.parseColor("#2EBEE8")
internal val COLOR_ITC_TEXT: Int = Color.parseColor("#1A1B1C")
internal val COLOR_VERGE_BG: Int = Color.parseColor("#12081E")
internal val COLOR_VERGE_PANEL: Int = Color.parseColor("#2A1542")
internal val COLOR_VERGE_ACCENT: Int = Color.parseColor("#FF69F8")
internal val COLOR_VERGE_GLOW: Int = Color.parseColor("#78F1FF")
internal val COLOR_VERGE_TEXT: Int = Color.parseColor("#F7F1FF")
internal val COLOR_VERGE_MUTED: Int = Color.parseColor("#B9A9D6")
internal val COLOR_GG_BG: Int = Color.parseColor("#F5E7E2")
internal val COLOR_GG_HEADLINE: Int = Color.parseColor("#FF0000")
internal val COLOR_GG_TEXT: Int = Color.parseColor("#000000")
internal val COLOR_GG_ARTICLE: Int = Color.parseColor("#000000")
internal val COLOR_GG_CAT_TEXT: Int = Color.parseColor("#000000")
internal val COLOR_MEZHA_BG: Int = Color.parseColor("#F5F3F3")
internal val COLOR_MEZHA_HEADLINE: Int = Color.parseColor("#E0D41A")
internal val HOME_HEADLINE_FALLBACKS =
    listOf(
        "Новые устройства и сервисы дня",
        "Главные новости технологий и медиа",
        "Короткая подборка видео и развлечений",
        "Курсы валют и полезные обновления",
    )
internal const val VERGE_LINE_SPACING = 0.9f
internal const val GAGADGET_LINE_SPACING = 0.78f
internal const val MEZHA_LINE_SPACING = 0.82f
/** Tighter WAP line height for playground.ru feeds / articles. */
internal const val PLAYGROUND_LINE_SPACING = 0.72f
internal const val STOPGAME_LOGO_ASSET = "sites/stopgame/logo.png"
internal const val PLAYGROUND_LOGO_ASSET = "sites/playground/logo.png"

/** WAP/i-mode: max reading column width; on narrow screens equals full width. */
internal const val WAP_FEED_COLUMN_WIDTH_DP: Int = 236

internal val COLOR_STOPGAME_BG: Int = Color.parseColor("#FFFFFF")
internal val COLOR_STOPGAME_HEADER: Int = Color.parseColor("#FFFFFF")
internal val COLOR_STOPGAME_RSS_BAR: Int = Color.parseColor("#CC0000")
internal val COLOR_STOPGAME_TEXT: Int = Color.parseColor("#000000")
internal val COLOR_STOPGAME_META: Int = Color.parseColor("#666666")
internal val COLOR_STOPGAME_RULE: Int = Color.parseColor("#999999")

internal val COLOR_PLAYGROUND_BG: Int = Color.parseColor("#FFEBEE")
internal val COLOR_PLAYGROUND_LOGO_BAND: Int = Color.parseColor("#000000")
internal val COLOR_PLAYGROUND_NAV_BAR: Int = Color.parseColor("#CC0000")
internal val COLOR_PLAYGROUND_FOCUS: Int = Color.parseColor("#B71C1C")
internal val COLOR_PLAYGROUND_TEXT: Int = Color.parseColor("#000000")
internal val COLOR_PLAYGROUND_MUTED: Int = Color.parseColor("#5D4037")

// KyivVlada (WAP: warm off-white, serif logo, orange «В»)
internal val COLOR_KV_ORANGE_V: Int = Color.parseColor("#E65100")
internal val COLOR_KV_BG: Int = Color.parseColor("#F0EDE8")
internal val COLOR_KV_HEADER: Int = Color.parseColor("#D7D2C8")
internal val COLOR_KV_HEADER_TEXT: Int = Color.parseColor("#2B2B2B")
internal val COLOR_KV_NAV_BAR: Int = Color.parseColor("#8D8A85")
internal val COLOR_KV_NAV_MUTED: Int = Color.parseColor("#D0CCC4")
internal val COLOR_KV_TEXT: Int = Color.parseColor("#1A1A1A")
internal val COLOR_KV_MUTED: Int = Color.parseColor("#6B6560")
internal val COLOR_KV_FOCUS: Int = Color.parseColor("#4E4332")
internal val COLOR_KV_ROW_INDEX: Int = Color.parseColor("#BF360C")

// 44.ua (WAP: green)
internal val COLOR_UA44_BG: Int = Color.parseColor("#E8F5E9")
internal val COLOR_UA44_HEADER: Int = Color.parseColor("#1B5E20")
internal val COLOR_UA44_HEADER_TEXT: Int = Color.parseColor("#FFFFFF")
internal val COLOR_UA44_NAV_BAR: Int = Color.parseColor("#2E7D32")
internal val COLOR_UA44_NAV_MUTED: Int = Color.parseColor("#A5D6A7")
internal val COLOR_UA44_TEXT: Int = Color.parseColor("#1B1B1B")
internal val COLOR_UA44_MUTED: Int = Color.parseColor("#33691E")
internal val COLOR_UA44_FOCUS: Int = Color.parseColor("#145A1E")
internal val COLOR_UA44_ROW_INDEX: Int = Color.parseColor("#66BB6A")

// VGorode (WAP: blue)
internal val COLOR_VG_BG: Int = Color.parseColor("#E8EEF9")
internal val COLOR_VG_HEADER: Int = Color.parseColor("#0D47A1")
internal val COLOR_VG_HEADER_TEXT: Int = Color.parseColor("#FFFFFF")
internal val COLOR_VG_NAV_BAR: Int = Color.parseColor("#1565C0")
internal val COLOR_VG_NAV_MUTED: Int = Color.parseColor("#BBDEFB")
internal val COLOR_VG_TEXT: Int = Color.parseColor("#0D1B2A")
internal val COLOR_VG_MUTED: Int = Color.parseColor("#37474F")
internal val COLOR_VG_FOCUS: Int = Color.parseColor("#0A3A82")
internal val COLOR_VG_ROW_INDEX: Int = Color.parseColor("#42A5F5")

// ANN / Anime News Network (light-blue dense I-mode / WAP-like layout)
internal val COLOR_ANN_BG: Int = Color.parseColor("#EAF7FF")
internal val COLOR_ANN_SECTION_BAR: Int = Color.parseColor("#0B2E5B")
internal val COLOR_ANN_SECTION_TEXT: Int = Color.parseColor("#FFFFFF")
internal val COLOR_ANN_TILE_BG: Int = Color.parseColor("#D7ECFF")
internal val COLOR_ANN_TILE_BG_FOCUS: Int = Color.parseColor("#1E50B5")
internal val COLOR_ANN_TILE_TEXT: Int = Color.parseColor("#0B2E5B")
internal val COLOR_ANN_TILE_TEXT_FOCUS: Int = Color.parseColor("#FFFFFF")
internal val COLOR_ANN_HEADLINES_TEXT: Int = Color.parseColor("#0B2E5B")
internal val COLOR_ANN_META_TEXT: Int = Color.parseColor("#2B6CB0")
internal val COLOR_ANN_TAG_BG: Int = Color.parseColor("#F0FAFF")
internal val COLOR_ANN_TAG_BORDER: Int = Color.parseColor("#88BFEA")
