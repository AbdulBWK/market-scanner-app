package com.wassem.marketscanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

class ScanWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val data = MarketRepository.fetchLatest() ?: return Result.retry()

        // Cache the raw response so the UI can show it instantly on next open.
        val raw = fetchRawOrNull()
        if (raw != null) {
            MarketRepository.saveCache(applicationContext, raw)
        }

        val lastSeen = MarketRepository.getLastSeenUpdatedAt(applicationContext)
        val isNew = data.updatedAt.isNotBlank() && data.updatedAt != lastSeen

        if (isNew && data.flags.isNotEmpty()) {
            val top = data.flags.first()
            val title = if (data.flags.size == 1) {
                "Market signal: ${top.symbol}"
            } else {
                "${data.flags.size} market signals flagged"
            }
            val text = if (data.flags.size == 1) {
                top.summary
            } else {
                data.flags.joinToString(" | ") { "${it.symbol} (${it.direction})" }
            }
            NotificationHelper.postSignalNotification(applicationContext, title, text)
        }

        if (data.updatedAt.isNotBlank()) {
            MarketRepository.setLastSeenUpdatedAt(applicationContext, data.updatedAt)
        }

        return Result.success()
    }

    private fun fetchRawOrNull(): String? {
        return try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(FeedConfig.FEED_URL + "?t=" + System.currentTimeMillis())
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val WORK_NAME = "market_scan_poll"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScanWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
