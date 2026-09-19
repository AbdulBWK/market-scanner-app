package com.wassem.marketscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf(MarketRepository.loadCache(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val fetched = withContext(Dispatchers.IO) { MarketRepository.fetchLatest() }
            if (fetched != null) {
                data = fetched
            } else {
                errorMessage = "Couldn't reach the data feed. Showing the last saved result."
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Market Scanner") },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                        errorMessage ?: "Loading the latest scan…",
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
                            "Last updated: ${d.updatedAt.ifBlank { "unknown" }}",
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

                    item { SectionHeader("Flagged signals") }
                    if (d.flags.isEmpty()) {
                        item { Text("Nothing cleared the bar right now.", color = Color.Gray) }
                    } else {
                        items(d.flags) { flag -> FlagCard(flag) }
                    }

                    item { SectionHeader("Top movers") }
                    if (d.movers.isEmpty()) {
                        item { Text("No mover data yet.", color = Color.Gray) }
                    } else {
                        items(d.movers) { mover -> MoverRow(mover) }
                    }

                    item { SectionHeader("Key news") }
                    if (d.news.isEmpty()) {
                        item { Text("No news items yet.", color = Color.Gray) }
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
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun FlagCard(flag: Flag) {
    val color = when (flag.direction) {
        "up" -> Color(0xFF22C55E)
        "down" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
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
            Spacer(modifier = Modifier.height(4.dp))
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
                        "What setups like this have tended to do",
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
