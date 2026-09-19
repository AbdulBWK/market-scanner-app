package com.wassem.marketscanner

import org.json.JSONObject

data class Flag(
    val symbol: String,
    val name: String,
    val direction: String, // "up", "down", "watch"
    val summary: String,
    val historicalContext: String = "" // general base-rate tendency, not a prediction
)

data class Mover(
    val symbol: String,
    val changePct: Double,
    val market: String
)

data class NewsItem(
    val headline: String,
    val summary: String
)

data class MarketData(
    val updatedAt: String,
    val disclaimer: String,
    val flags: List<Flag>,
    val movers: List<Mover>,
    val news: List<NewsItem>
) {
    companion object {
        fun fromJson(raw: String): MarketData {
            val obj = JSONObject(raw)

            val flags = mutableListOf<Flag>()
            val flagsArr = obj.optJSONArray("flags")
            if (flagsArr != null) {
                for (i in 0 until flagsArr.length()) {
                    val f = flagsArr.getJSONObject(i)
                    flags.add(
                        Flag(
                            symbol = f.optString("symbol", ""),
                            name = f.optString("name", ""),
                            direction = f.optString("direction", "watch"),
                            summary = f.optString("summary", ""),
                            historicalContext = f.optString("historical_context", "")
                        )
                    )
                }
            }

            val movers = mutableListOf<Mover>()
            val moversArr = obj.optJSONArray("movers")
            if (moversArr != null) {
                for (i in 0 until moversArr.length()) {
                    val m = moversArr.getJSONObject(i)
                    movers.add(
                        Mover(
                            symbol = m.optString("symbol", ""),
                            changePct = m.optDouble("change_pct", 0.0),
                            market = m.optString("market", "")
                        )
                    )
                }
            }

            val news = mutableListOf<NewsItem>()
            val newsArr = obj.optJSONArray("news")
            if (newsArr != null) {
                for (i in 0 until newsArr.length()) {
                    val n = newsArr.getJSONObject(i)
                    news.add(
                        NewsItem(
                            headline = n.optString("headline", ""),
                            summary = n.optString("summary", "")
                        )
                    )
                }
            }

            return MarketData(
                updatedAt = obj.optString("updated_at", ""),
                disclaimer = obj.optString(
                    "disclaimer",
                    "Pattern-spotting from public web data, not financial advice."
                ),
                flags = flags,
                movers = movers,
                news = news
            )
        }
    }
}
