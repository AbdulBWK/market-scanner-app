package com.wassem.marketscanner

import org.json.JSONObject

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    GERMAN("de");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            if (code == "de") GERMAN else ENGLISH
    }
}

data class Flag(
    val symbol: String,
    val name: String,
    val direction: String, // "up", "down", "watch" - what already happened
    val summary: String,
    val historicalContext: String = "", // general base-rate tendency, not a prediction
    // Current technical-indicator reading (moving averages / oscillators already observed
    // right now), NOT a forecast of where the price is headed next. "bullish", "bearish",
    // or "neutral".
    val technicalBias: String = "neutral"
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
        // Reads "<baseKey>_<lang>", falling back to "<baseKey>_en", then the bare
        // "<baseKey>" (for older/un-translated feed entries), then finally [fallback].
        private fun localized(obj: JSONObject, baseKey: String, lang: AppLanguage, fallback: String = ""): String {
            val langSpecific = obj.optString("${baseKey}_${lang.code}", "")
            if (langSpecific.isNotBlank()) return langSpecific
            val en = obj.optString("${baseKey}_en", "")
            if (en.isNotBlank()) return en
            return obj.optString(baseKey, fallback)
        }

        fun fromJson(raw: String, lang: AppLanguage = AppLanguage.ENGLISH): MarketData {
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
                            summary = localized(f, "summary", lang),
                            historicalContext = localized(f, "historical_context", lang),
                            technicalBias = f.optString("technical_bias", "neutral")
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
                            headline = localized(n, "headline", lang),
                            summary = localized(n, "summary", lang)
                        )
                    )
                }
            }

            val fallbackDisclaimer = if (lang == AppLanguage.GERMAN)
                "Dies ist ein Muster-Hinweis auf Basis öffentlicher Webdaten, keine Anlageberatung - bitte selbst prüfen."
            else
                "Pattern-spotting from public web data, not financial advice."

            return MarketData(
                updatedAt = obj.optString("updated_at", ""),
                disclaimer = localized(obj, "disclaimer", lang, fallbackDisclaimer),
                flags = flags,
                movers = movers,
                news = news
            )
        }
    }
}
