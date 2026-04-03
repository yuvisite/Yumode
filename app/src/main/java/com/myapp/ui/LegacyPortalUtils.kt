package com.myapp.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.view.KeyEvent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.min
import kotlin.math.roundToInt
import com.myapp.model.CitySearchResult
import com.myapp.model.FeedItem
import com.myapp.model.SavedCity
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

internal fun Throwable.toUserMessage(): String =
    message?.takeIf { it.isNotBlank() }
        ?: javaClass.simpleName.takeIf { it.isNotBlank() }
        ?: "could not load data"

internal fun Double.format(decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", this)

/** ISO 3166-1 alpha-2 → regional-indicator flag emoji. */
internal fun isoRegionToFlagEmoji(iso3166Alpha2: String): String {
    val code = iso3166Alpha2.trim().uppercase(Locale.US)
    if (code.length != 2) {
        return ""
    }
    val c0 = code[0]
    val c1 = code[1]
    if (!c0.isLetter() || !c1.isLetter()) {
        return ""
    }
    val base = 0x1F1E6
    val cp0 = base + (c0.code - 'A'.code)
    val cp1 = base + (c1.code - 'A'.code)
    return String(Character.toChars(cp0)) + String(Character.toChars(cp1))
}

/** Map ISO 4217 currency (NBU major list) to a representative flag. */
internal fun currencyCodeToFlagEmoji(currencyCode: String): String {
    val iso =
        when (currencyCode.trim().uppercase(Locale.US)) {
            "USD" -> "US"
            "EUR" -> "EU"
            "PLN" -> "PL"
            "GBP" -> "GB"
            "CHF" -> "CH"
            "CZK" -> "CZ"
            "CAD" -> "CA"
            "JPY" -> "JP"
            "CNY" -> "CN"
            "UAH" -> "UA"
            else -> ""
        }
    return if (iso.isEmpty()) "" else isoRegionToFlagEmoji(iso)
}

internal fun AppCompatActivity.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

/** WAP column: capped width on wide DPAD screens; full width on narrow handsets. */
internal fun AppCompatActivity.wapFeedColumnWidthPx(): Int =
    min(resources.displayMetrics.widthPixels, dp(WAP_FEED_COLUMN_WIDTH_DP))

/**
 * WAP content width: use near-full screen width to avoid large side gutters on phones.
 * Keeps a small left/right padding by default.
 */
internal fun AppCompatActivity.wapContentWidthPx(sidePaddingDp: Int = 8): Int =
    (resources.displayMetrics.widthPixels - dp(sidePaddingDp * 2)).coerceAtLeast(1)

internal fun AppCompatActivity.spToPx(value: Float): Float =
    value * resources.displayMetrics.scaledDensity

/**
 * Display format for RSS dates: `yyyy MM dd HH:mm` (e.g. 2026 03 30 14:35).
 */
internal fun formatRssDisplayDateTime(raw: String?): String? {
    if (raw.isNullOrBlank()) {
        return null
    }
    val trimmed = raw.trim()
    val outPattern = DateTimeFormatter.ofPattern("yyyy MM dd HH:mm", Locale.US)
    runCatching {
        ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
    }.getOrNull()?.let { zdt ->
        return zdt.format(outPattern)
    }
    runCatching {
        OffsetDateTime.parse(trimmed)
    }.getOrNull()?.let { odt ->
        return odt.format(outPattern)
    }
    runCatching {
        Instant.parse(trimmed)
    }.getOrNull()?.let { inst ->
        return ZonedDateTime.ofInstant(inst, ZoneId.systemDefault()).format(outPattern)
    }
    return trimmed
}

internal fun formatDate(value: String): String {
    val parts = value.split("-")
    return if (parts.size == 3) {
        "${parts[2]}.${parts[1]}.${parts[0]}"
    } else {
        value
    }
}

internal fun weatherCodeToText(code: Int): String =
    when (code) {
        0 -> "clear"
        1, 2, 3 -> "partly cloudy"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61, 63, 65 -> "rain"
        66, 67 -> "freezing rain"
        71, 73, 75 -> "snow"
        77 -> "snow grains"
        80, 81, 82 -> "showers"
        85, 86 -> "snow showers"
        95 -> "storm"
        96, 99 -> "storm + hail"
        else -> "mixed sky"
    }

internal fun SavedCity.label(): String =
    listOfNotNull(name, admin1, countryCode)
        .distinct()
        .joinToString(", ")

internal fun CitySearchResult.label(): String =
    listOfNotNull(name, admin1, countryCode)
        .distinct()
        .joinToString(", ")

internal fun FeedItem.primaryCategory(): String? =
    categories.firstOrNull { it.isNotBlank() }

internal fun categoryFocusTagPrefix(siteId: String): String =
    "category-focus:$siteId:"

internal fun categoryFocusTag(
    siteId: String,
    category: String?,
): String = categoryFocusTagPrefix(siteId) + (category?.trim()?.lowercase(Locale.ROOT) ?: "__all__")

internal fun scaleLogoBitmapForHeader(
    sourceBitmap: Bitmap,
    maxWidthPx: Int,
): Bitmap {
    if (sourceBitmap.width <= maxWidthPx) {
        return sourceBitmap
    }
    val targetWidth = maxWidthPx.coerceAtLeast(1)
    val targetHeight =
        (sourceBitmap.height.toFloat() / sourceBitmap.width.toFloat() * targetWidth)
            .roundToInt()
            .coerceAtLeast(1)
    return Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true).also { scaled ->
        if (scaled !== sourceBitmap) {
            sourceBitmap.recycle()
        }
    }
}

internal fun softenLogoBitmap(
    sourceBitmap: Bitmap,
    targetWidth: Int,
): Bitmap {
    val safeTargetWidth = targetWidth.coerceAtLeast(1)
    val targetHeight =
        (sourceBitmap.height.toFloat() / sourceBitmap.width.toFloat() * safeTargetWidth)
            .roundToInt()
            .coerceAtLeast(1)
    val baseBitmap = Bitmap.createScaledBitmap(sourceBitmap, safeTargetWidth, targetHeight, true)
    if (baseBitmap !== sourceBitmap) {
        sourceBitmap.recycle()
    }
    val mushWidth = (safeTargetWidth / 3).coerceAtLeast(16)
    val mushHeight = (targetHeight * mushWidth.toFloat() / safeTargetWidth).roundToInt().coerceAtLeast(1)
    val shrunkBitmap = Bitmap.createScaledBitmap(baseBitmap, mushWidth, mushHeight, true)
    val softenedBitmap = Bitmap.createScaledBitmap(shrunkBitmap, safeTargetWidth, targetHeight, true)
    if (shrunkBitmap !== baseBitmap) {
        shrunkBitmap.recycle()
    }
    if (baseBitmap !== softenedBitmap) {
        baseBitmap.recycle()
    }
    return softenedBitmap
}

internal fun buildStyledText(
    text: String,
    color: Int,
    underline: Boolean = false,
): CharSequence =
    SpannableString(text).apply {
        setSpan(
            ForegroundColorSpan(color),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (underline && text.isNotEmpty()) {
            setSpan(
                UnderlineSpan(),
                0,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

internal fun applyLinkText(
    view: TextView,
    text: String,
    focused: Boolean,
    focusBackgroundColor: Int,
    normalLinkColor: Int,
) {
    val fullText = "$BULLET_PREFIX$text"
    val linkStart = BULLET_PREFIX.length
    val span = SpannableString(fullText)

    if (focused) {
        view.setBackgroundColor(focusBackgroundColor)
        span.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        span.setSpan(
            UnderlineSpan(),
            linkStart,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    } else {
        view.setBackgroundColor(Color.TRANSPARENT)
        span.setSpan(
            ForegroundColorSpan(COLOR_TEXT),
            0,
            linkStart,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        span.setSpan(
            ForegroundColorSpan(normalLinkColor),
            linkStart,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        span.setSpan(
            UnderlineSpan(),
            linkStart,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    view.text = span
}

internal fun requestFirstFocusableDescendant(root: View?): Boolean {
    val target = findFirstFocusableDescendant(root) ?: return false
    target.requestFocus()
    return true
}

internal fun findFirstFocusableDescendant(root: View?): View? =
    when (root) {
        null -> null
        is ViewGroup -> {
            if (root.isFocusable) {
                root
            } else {
                (0 until root.childCount)
                    .asSequence()
                    .map { index -> findFirstFocusableDescendant(root.getChildAt(index)) }
                    .firstOrNull { it != null }
            }
        }
        else -> root.takeIf { it.isFocusable }
    }

internal fun findViewWithTagRecursive(root: View?, targetTag: String): View? =
    when (root) {
        null -> null
        is ViewGroup -> {
            if (root.tag == targetTag) {
                root
            } else {
                (0 until root.childCount)
                    .asSequence()
                    .mapNotNull { index -> findViewWithTagRecursive(root.getChildAt(index), targetTag) }
                    .firstOrNull()
            }
        }
        else -> root.takeIf { it.tag == targetTag }
    }

internal fun handleCyclicVerticalFocus(
    keyCode: Int,
    activity: AppCompatActivity,
    container: ViewGroup,
    scrollView: ScrollView,
    mainHandler: Handler,
    categoryFocusTagPrefix: String = CATEGORY_FOCUS_TAG_PREFIX,
): Boolean {
    if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
        return false
    }
    if (handleCategoryVerticalFocus(keyCode, activity, container, scrollView, mainHandler, categoryFocusTagPrefix)) {
        return true
    }
    val focusables = buildFocusableList(container, container)
    if (focusables.size <= 1) {
        return false
    }
    val current = activity.currentFocus
    val searchDirection = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) View.FOCUS_DOWN else View.FOCUS_UP
    val searched = current?.focusSearch(searchDirection)?.takeIf { it !== current && isDescendantOf(it, container) }
    val next =
        searched ?: run {
            val currentIndex = current?.let(focusables::indexOf) ?: -1
            val nextIndex =
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (currentIndex < 0) 0 else (currentIndex + 1) % focusables.size
                    }

                    else -> {
                        if (currentIndex < 0) {
                            focusables.lastIndex
                        } else {
                            (currentIndex - 1 + focusables.size) % focusables.size
                        }
                    }
                }
            focusables.getOrNull(nextIndex)
        } ?: return false
    focusViewAndBringIntoView(next, scrollView, mainHandler)
    return true
}

internal fun handleCategoryVerticalFocus(
    keyCode: Int,
    activity: AppCompatActivity,
    container: ViewGroup,
    scrollView: ScrollView,
    mainHandler: Handler,
    categoryFocusTagPrefix: String = CATEGORY_FOCUS_TAG_PREFIX,
): Boolean {
    val current = activity.currentFocus ?: return false
    val currentTag = current.tag as? String ?: return false
    if (!currentTag.startsWith(categoryFocusTagPrefix)) {
        return false
    }
    val categoryPrefix = currentTag.substringBeforeLast(":", missingDelimiterValue = currentTag) + ":"
    val categoryViews =
        buildFocusableList(container, container)
            .filter { view -> (view.tag as? String)?.startsWith(categoryPrefix) == true }
    if (categoryViews.size <= 1) {
        return false
    }
    val currentBounds = viewBounds(current)
    val currentCenterX = (currentBounds.left + currentBounds.right) / 2
    val currentCenterY = (currentBounds.top + currentBounds.bottom) / 2
    val target =
        categoryViews
            .asSequence()
            .filter { it !== current }
            .mapNotNull { candidate ->
                val bounds = viewBounds(candidate)
                val centerX = (bounds.left + bounds.right) / 2
                val centerY = (bounds.top + bounds.bottom) / 2
                val verticalDelta = centerY - currentCenterY
                val valid = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) verticalDelta > 0 else verticalDelta < 0
                if (!valid) {
                    null
                } else {
                    Triple(candidate, abs(verticalDelta), abs(centerX - currentCenterX))
                }
            }
            .sortedWith(compareBy<Triple<View, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()
            ?.first
    return if (target != null) {
        focusViewAndBringIntoView(target, scrollView, mainHandler)
        true
    } else {
        false
    }
}

internal fun focusViewAndBringIntoView(
    view: View,
    scrollView: ScrollView,
    mainHandler: Handler,
) {
    view.requestFocus()
    mainHandler.post {
        if (!view.isAttachedToWindow || !isDescendantOf(view, scrollView)) {
            return@post
        }
        val rect = Rect()
        view.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(view, rect)
        // Minimal scroll: only move enough to reveal the focused view.
        // requestChildRectangleOnScreen() can jump too far on some DPAD devices.
        val padding = scrollView.paddingTop.coerceAtLeast(0) + scrollView.paddingBottom.coerceAtLeast(0)
        val viewportTop = scrollView.scrollY
        val viewportBottom = scrollView.scrollY + scrollView.height
        val targetTop = rect.top
        val targetBottom = rect.bottom
        val extra = (scrollView.height * 0.08f).toInt().coerceAtLeast(8)
        val nextScrollY =
            when {
                targetTop - extra < viewportTop -> (targetTop - extra).coerceAtLeast(0)
                targetBottom + extra > viewportBottom -> (targetBottom - scrollView.height + extra).coerceAtLeast(0)
                else -> scrollView.scrollY
            }
        if (nextScrollY != scrollView.scrollY) {
            scrollView.scrollTo(0, nextScrollY)
        }
    }
}

internal fun buildFocusableList(root: View, container: View): List<View> {
    val result = mutableListOf<View>()
    collectFocusableViews(root, container, result)
    return result
}

internal fun collectFocusableViews(
    view: View,
    container: View,
    result: MutableList<View>,
) {
    if (view.visibility != View.VISIBLE) {
        return
    }
    if (view !== container && view.isFocusable) {
        result += view
    }
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            collectFocusableViews(view.getChildAt(index), container, result)
        }
    }
}

private data class ViewBounds(
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
)

private fun viewBounds(view: View): ViewBounds {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    return ViewBounds(
        left = location[0],
        right = location[0] + view.width,
        top = location[1],
        bottom = location[1] + view.height,
    )
}

private fun isDescendantOf(view: View, ancestor: ViewGroup): Boolean {
    var current: android.view.ViewParent? = view.parent
    while (current != null) {
        if (current === ancestor) {
            return true
        }
        current = current.parent
    }
    return false
}
