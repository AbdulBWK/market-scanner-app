package com.wassem.marketscanner

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Change these two constants to your own GitHub username/repo before building.
 * The app polls this raw file for the latest market summary.
 */
object FeedConfig {
    const val OWNER = "AbdulBWK"
    const val REPO = "market-scanner-app"
    const val BRANCH = "main"
    const val PATH = "data/latest.json"

    val FEED_URL: String
        get() = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$PATH"
}

object MarketRepository {

    private const val PREFS = "market_scanner_prefs"
    private const val KEY_CACHE = "cached_json"
    private const val KEY_LAST_SEEN = "last_seen_updated_at"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun fetchLatest(): MarketData? {
        return try {
            val request = Request.Builder()
                .url(FeedConfig.FEED_URL + "?t=" + System.currentTimeMillis()) // cache-bust
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                MarketData.fromJson(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveCache(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHE, raw)
            .apply()
    }

    fun loadCache(context: Context): MarketData? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHE, null) ?: return null
        return try {
            MarketData.fromJson(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun getLastSeenUpdatedAt(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SEEN, null)
    }

    fun setLastSeenUpdatedAt(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SEEN, value)
            .apply()
    }
}
