package com.myapp.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.myapp.model.SavedCity

class YumodePreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "yumode_preferences",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun loadSavedCity(): SavedCity? {
        if (!preferences.contains(KEY_CITY_NAME)) {
            return null
        }

        return SavedCity(
            name = preferences.getString(KEY_CITY_NAME, null) ?: return null,
            admin1 = preferences.getString(KEY_CITY_ADMIN1, null),
            countryCode = preferences.getString(KEY_CITY_COUNTRY, null) ?: return null,
            latitude = preferences.getFloat(KEY_CITY_LATITUDE, Float.NaN).toDouble(),
            longitude = preferences.getFloat(KEY_CITY_LONGITUDE, Float.NaN).toDouble(),
            timezone = preferences.getString(KEY_CITY_TIMEZONE, null) ?: "auto",
        )
    }

    fun saveCity(city: SavedCity) {
        preferences
            .edit()
            .putString(KEY_CITY_NAME, city.name)
            .putString(KEY_CITY_ADMIN1, city.admin1)
            .putString(KEY_CITY_COUNTRY, city.countryCode)
            .putFloat(KEY_CITY_LATITUDE, city.latitude.toFloat())
            .putFloat(KEY_CITY_LONGITUDE, city.longitude.toFloat())
            .putString(KEY_CITY_TIMEZONE, city.timezone)
            .apply()
    }

    fun loadBookmarkedSiteIds(): Set<String> =
        preferences.getStringSet(KEY_BOOKMARKED_SITE_IDS, emptySet()).orEmpty()

    fun isSiteBookmarked(siteId: String): Boolean =
        loadBookmarkedSiteIds().contains(siteId)

    fun addBookmarkedSite(siteId: String) {
        val updated = loadBookmarkedSiteIds().toMutableSet().apply { add(siteId) }
        preferences
            .edit()
            .putStringSet(KEY_BOOKMARKED_SITE_IDS, updated)
            .apply()
    }

    fun removeBookmarkedSite(siteId: String) {
        val updated = loadBookmarkedSiteIds().toMutableSet().apply { remove(siteId) }
        preferences
            .edit()
            .putStringSet(KEY_BOOKMARKED_SITE_IDS, updated)
            .apply()
    }

    fun loadPortalFontId(): String =
        preferences.getString(KEY_PORTAL_FONT_ID, FONT_ID_CLASSIC) ?: FONT_ID_CLASSIC

    fun savePortalFontId(fontId: String) {
        preferences
            .edit()
            .putString(KEY_PORTAL_FONT_ID, fontId)
            .apply()
    }

    private companion object {
        const val KEY_CITY_NAME = "city_name"
        const val KEY_CITY_ADMIN1 = "city_admin1"
        const val KEY_CITY_COUNTRY = "city_country"
        const val KEY_CITY_LATITUDE = "city_latitude"
        const val KEY_CITY_LONGITUDE = "city_longitude"
        const val KEY_CITY_TIMEZONE = "city_timezone"
        const val KEY_BOOKMARKED_SITE_IDS = "bookmarked_site_ids"
        const val KEY_PORTAL_FONT_ID = "portal_font_id"
        const val FONT_ID_CLASSIC = "classic"
    }
}
