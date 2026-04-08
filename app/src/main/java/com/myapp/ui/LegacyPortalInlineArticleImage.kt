package com.myapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

internal data class LegacyPortalInlineImageStyle(
    val decodeWidthPx: Int,
    val layoutWidthPx: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    val frameColor: Int,
    val mutedColor: Int,
    val captionColor: Int,
    val frameFillColor: Int = Color.TRANSPARENT,
    val topMarginDp: Int = 6,
    val bottomMarginDp: Int = 6,
    val imagePaddingDp: Int = 0,
    val statusTextSizeSp: Float = 9.5f,
    val captionTextSizeSp: Float = 9.5f,
)

internal fun buildLegacyPortalInlineArticleImage(
    activity: AppCompatActivity,
    imageUrl: String,
    caption: String?,
    refererUrl: String?,
    style: LegacyPortalInlineImageStyle,
    scaledTextSize: ((Float) -> Float)? = null,
    compactLineSpacing: Float = 1f,
    typeface: Typeface? = null,
): View =
    LegacyPortalInlineArticleImageLoader.createView(
        activity = activity,
        imageUrl = imageUrl,
        caption = caption,
        refererUrl = refererUrl,
        style = style,
        scaledTextSize = scaledTextSize,
        compactLineSpacing = compactLineSpacing,
        typeface = typeface,
    )

internal fun legacyPortalWideImageWidthPx(
    activity: AppCompatActivity,
    horizontalPaddingDp: Int = 8,
): Int =
    (activity.resources.displayMetrics.widthPixels - activity.dp(horizontalPaddingDp))
        .coerceAtLeast(activity.dp(120))

private object LegacyPortalInlineArticleImageLoader {
    private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 8.1.0; Sharp 806SH) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36 Yumode/0.1"

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 2
                    maxRequestsPerHost = 2
                },
            )
            .build()

    private val memoryCache =
        object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    fun createView(
        activity: AppCompatActivity,
        imageUrl: String,
        caption: String?,
        refererUrl: String?,
        style: LegacyPortalInlineImageStyle,
        scaledTextSize: ((Float) -> Float)?,
        compactLineSpacing: Float,
        typeface: Typeface?,
    ): View {
        val cacheKey = "$imageUrl#${style.decodeWidthPx.coerceAtLeast(1)}"
        val frame =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        style.layoutWidthPx,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = activity.dp(style.topMarginDp)
                        bottomMargin = activity.dp(style.bottomMarginDp)
                    }
                orientation = LinearLayout.VERTICAL
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(style.frameFillColor)
                        setStroke(activity.dp(1), style.frameColor)
                    }
                setPadding(
                    activity.dp(style.imagePaddingDp),
                    activity.dp(style.imagePaddingDp),
                    activity.dp(style.imagePaddingDp),
                    activity.dp(style.imagePaddingDp),
                )
            }

        val imageView =
            ImageView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
            }

        val statusView =
            defaultTextView(
                activity = activity,
                text = "loading image...",
                color = style.mutedColor,
                textSizeSp = style.statusTextSizeSp,
                scaledTextSize = scaledTextSize,
                compactLineSpacing = compactLineSpacing,
                typeface = typeface,
            )

        val captionText = caption?.trim().orEmpty()
        val captionView =
            if (captionText.isNotBlank()) {
                defaultTextView(
                    activity = activity,
                    text = captionText.take(180),
                    color = style.captionColor,
                    textSizeSp = style.captionTextSizeSp,
                    scaledTextSize = scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = typeface,
                ).apply {
                    (layoutParams as LinearLayout.LayoutParams).topMargin = activity.dp(4)
                }
            } else {
                null
            }

        frame.addView(statusView)
        frame.addView(imageView)
        captionView?.let { frame.addView(it) }

        fun showBitmap(bitmap: Bitmap) {
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
            statusView.visibility = View.GONE
        }

        fun showError() {
            statusView.text = "image unavailable"
            statusView.visibility = View.VISIBLE
            imageView.visibility = View.GONE
        }

        memoryCache.get(cacheKey)?.let { cached ->
            showBitmap(cached)
            return frame
        }

        var loadJob: Job? = null
        var loadStarted = false

        fun startLoad() {
            if (loadStarted) return
            loadStarted = true

            loadJob =
                activity.lifecycleScope.launch {
                    val bitmap =
                        try {
                            withContext(Dispatchers.IO) {
                                loadBitmap(
                                    imageUrl = imageUrl,
                                    refererUrl = refererUrl,
                                    decodeWidthPx = style.decodeWidthPx.coerceAtLeast(1),
                                    cacheKey = cacheKey,
                                )
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }

                    if (!frame.isAttachedToWindow) {
                        return@launch
                    }

                    if (bitmap != null) {
                        showBitmap(bitmap)
                    } else {
                        showError()
                    }
                }
        }

        frame.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    startLoad()
                }

                override fun onViewDetachedFromWindow(v: View) {
                    loadJob?.cancel()
                    frame.removeOnAttachStateChangeListener(this)
                }
            },
        )

        frame.post {
            if (frame.isAttachedToWindow) {
                startLoad()
            }
        }

        return frame
    }

    private fun defaultTextView(
        activity: AppCompatActivity,
        text: String,
        color: Int,
        textSizeSp: Float,
        scaledTextSize: ((Float) -> Float)?,
        compactLineSpacing: Float,
        typeface: Typeface?,
    ): TextView =
        TextView(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            includeFontPadding = false
            this.text = text
            this.typeface = typeface
            this.textSize = scaledTextSize?.invoke(textSizeSp) ?: textSizeSp
            setLineSpacing(0f, compactLineSpacing)
            setTextColor(color)
        }

    private fun loadBitmap(
        imageUrl: String,
        refererUrl: String?,
        decodeWidthPx: Int,
        cacheKey: String,
    ): Bitmap? {
        memoryCache.get(cacheKey)?.let { return it }

        val requestBuilder =
            Request.Builder()
                .url(imageUrl)
                .get()
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header("User-Agent", DEFAULT_USER_AGENT)

        if (!refererUrl.isNullOrBlank()) {
            requestBuilder.header("Referer", refererUrl)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}")
            }

            val body = resp.body ?: return null
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_IMAGE_BYTES) {
                return null
            }

            val bytes = body.byteStream().use { stream -> readBoundedBytes(stream, MAX_IMAGE_BYTES) }
            val bitmap = decodeSampledBitmap(bytes, decodeWidthPx) ?: return null
            memoryCache.put(cacheKey, bitmap)
            return bitmap
        }
    }

    private fun readBoundedBytes(
        stream: java.io.InputStream,
        maxBytes: Int,
    ): ByteArray {
        val buffer = ByteArray(8_192)
        val output = ByteArrayOutputStream()
        var totalRead = 0

        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            totalRead += read
            if (totalRead > maxBytes) {
                throw IOException("image too large")
            }
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private fun decodeSampledBitmap(
        bytes: ByteArray,
        decodeWidthPx: Int,
    ): Bitmap? {
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, decodeWidthPx)
        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
        if (decoded.width <= decodeWidthPx || decodeWidthPx <= 0) {
            return decoded
        }

        val scaledHeight = max(1, (decoded.height * (decodeWidthPx / decoded.width.toFloat())).roundToInt())
        val scaledBitmap = Bitmap.createScaledBitmap(decoded, decodeWidthPx, scaledHeight, true)
        if (scaledBitmap != decoded) {
            decoded.recycle()
        }
        return scaledBitmap
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        targetWidthPx: Int,
    ): Int {
        var sampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        val safeTargetWidth = targetWidthPx.coerceAtLeast(1)
        val safeTargetHeight = (safeTargetWidth * 3 / 2).coerceAtLeast(1)

        while (halfWidth / sampleSize >= safeTargetWidth && halfHeight / sampleSize >= safeTargetHeight) {
            sampleSize *= 2
        }

        return sampleSize.coerceAtLeast(1)
    }
}
