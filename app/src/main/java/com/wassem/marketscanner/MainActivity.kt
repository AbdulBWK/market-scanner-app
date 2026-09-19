package com.wassem.marketscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ScanWorker.schedule(applicationContext)
        maybeRequestNotificationPermission()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MarketScreen()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

// --- Localized UI strings ------------------------------------------------

data class UiStrings(
    val appTitle: String,
    val refresh: String,
    val lastUpdated: String,
    val unknown: String,
    val loading: String,
    val feedError: String,
    val flaggedSignals: String,
    val noFlags: String,
    val topMovers: String,
    val noMovers: String,
    val keyNews: String,
    val noNews: String,
    val historicalLabel: String,
    val biasLabel: String,
    val biasBullish: String,
    val biasBearish: String,
    val biasNeutral: String
)

val EN_STRINGS = UiStrings(
    appTitle = "Market Scanner",
    refresh = "Refresh",
    lastUpdated = "Last updated",
    unknown = "unknown",
    loading = "Loading the latest scan…",
    feedError = "Couldn't reach the data feed. Showing the last saved result.",
    flaggedSignals = "Flagged signals",
    noFlags = "Nothing cleared the bar right now.",
    topMovers = "Top movers",
    noMovers = "No mover data yet.",
    keyNews = "Key news",
    noNews = "No news items yet.",
    historicalLabel = "What setups like this have tended to do",
    biasLabel = "Current technical bias",
    biasBullish = "Bullish",
    biasBearish = "Bearish",
    biasNeutral = "Neutral"
)

val DE_STRINGS = UiStrings(
    appTitle = "Market Scanner",
    refresh = "Aktualisieren",
    lastUpdated = "Letzte Aktualisierung",
    unknown = "unbekannt",
    loading = "Neuester Scan wird geladen…",
    feedError = "Datenquelle nicht erreichbar. Zeige das letzte gespeicherte Ergebnis.",
    flaggedSignals = "Markierte Signale",
    noFlags = "Gerade nichts, das die Schwelle überschreitet.",
    topMovers = "Größte Bewegungen",
    noMovers = "Noch keine Daten zu Kursbewegungen.",
    keyNews = "Wichtige Nachrichten",
    noNews = "Noch keine Nachrichten.",
    historicalLabel = "Wie sich ähnliche Situationen historisch verhalten haben",
    biasLabel = "Aktuelle technische Tendenz",
    biasBullish = "Bullisch",
    biasBearish = "Bärisch",
    biasNeutral = "Neutral"
)

fun stringsFor(lang: AppLanguage): UiStrings =
    if (lang == AppLanguage.GERMAN) DE_STRINGS else EN_STRINGS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(MarketRepository.getLanguage(context)) }
    val strings = stringsFor(language)

    var data by remember { mutableStateOf(MarketRepository.loadCache(context, language)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val fetched = withContext(Dispatchers.IO) {
                val raw = MarketRepository.fetchLatestRaw()
                if (raw != null) MarketRepository.saveCache(context, raw)
                raw?.let {
                    try {
                        MarketData.fromJson(it, language)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            if (fetched != null) {
                data = fetched
            } else {
                errorMessage = strings.feedError
            }
            isLoading = false
        }
    }

    fun switchLanguage(newLang: AppLanguage) {
        if (newLang == language) return
        language = newLang
        MarketRepository.setLanguage(context, newLang)
        // Re-parse whatever is cached immediately (no network call needed);
        // a background refresh will follow to pick up anything newer.
        data = MarketRepository.loadCache(context, newLang)
        refresh()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.appTitle) },
                actions = {
                    LanguageToggle(language, onSelect = { switchLanguage(it) })
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.refresh)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (data == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        errorMessage ?: strings.loading,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                val d = data!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "${strings.lastUpdated}: ${d.updatedAt.ifBlank { strings.unknown }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    if (errorMessage != null) {
                        item {
                            Text(
                                errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    item { SectionHeader(strings.flaggedSignals) }
                    if (d.flags.isEmpty()) {
                        item { Text(strings.noFlags, color = Color.Gray) }
                    } else {
                        items(d.flags) { flag -> FlagCard(flag, strings) }
                    }

                    item { SectionHeader(strings.topMovers) }
                    if (d.movers.isEmpty()) {
                        item { Text(strings.noMovers, color = Color.Gray) }
                    } else {
                        items(d.movers) { mover -> MoverRow(mover) }
                    }

                    item { SectionHeader(strings.keyNews) }
                    if (d.news.isEmpty()) {
                        item { Text(strings.noNews, color = Color.Gray) }
                    } else {
                        items(d.news) { news -> NewsRow(news) }
                    }

                    item {
                        Text(
                            d.disclaimer,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageToggle(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LanguageChip("EN", current == AppLanguage.ENGLISH) { onSelect(AppLanguage.ENGLISH) }
        Spacer(modifier = Modifier.width(4.dp))
        LanguageChip("DE", current == AppLanguage.GERMAN) { onSelect(AppLanguage.GERMAN) }
        Spacer(modifier = Modifier.width(4.dp))
    }
}

@Composable
fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else Color.LightGray
    Box(
        modifier = Modifier
            .background(bg, shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .then(Modifier.clickableNoRipple(onClick))
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

// Small helper so the language chips don't need a full Button's padding/shape.
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick
        )
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun FlagCard(flag: Flag, strings: UiStrings) {
    val color = when (flag.direction) {
        "up" -> Color(0xFF22C55E)
        "down" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }
    val biasColor = when (flag.technicalBias) {
        "bullish" -> Color(0xFF22C55E)
        "bearish" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }
    val biasText = when (flag.technicalBias) {
        "bullish" -> strings.biasBullish
        "bearish" -> strings.biasBearish
        else -> strings.biasNeutral
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${flag.symbol} — ${flag.name}",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${strings.biasLabel}: $biasText",
                color = biasColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(flag.summary, style = MaterialTheme.typography.bodyMedium)
            if (flag.historicalContext.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        strings.historicalLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        flag.historicalContext,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun MoverRow(mover: Mover) {
    val color = if (mover.changePct >= 0) Color(0xFF22C55E) else Color(0xFFEF4444)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${mover.symbol} (${mover.market})")
        Text(
            "${if (mover.changePct >= 0) "+" else ""}${mover.changePct}%",
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NewsRow(news: NewsItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(news.headline, fontWeight = FontWeight.Bold)
        if (news.summary.isNotBlank()) {
            Text(news.summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
