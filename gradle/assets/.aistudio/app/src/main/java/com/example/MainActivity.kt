package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.DocumentNode
import com.example.data.SystemLog
import com.example.data.WalletTransaction
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CyberBlack),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("console") } // "console", "wallet", "vault", "config"
    var showInfoDialog by remember { mutableStateOf(false) }

    // Collect variables reactively from VM
    val messages by viewModel.chatMessages.collectAsState()
    val documents by viewModel.indexedDocs.collectAsState()
    val logs by viewModel.systemLogs.collectAsState()
    val cqaiBalance by viewModel.cqaiBalance.collectAsState()
    val isMining by viewModel.isMining.collectAsState()
    val walletTransactions by viewModel.walletTransactions.collectAsState()

    val isGenerating by viewModel.isGenerating.collectAsState()
    val isSearchingWeb by viewModel.isSearchingWeb.collectAsState()
    val isSyncing by viewModel.isDriveSyncing.collectAsState()

    val consciousness by viewModel.consciousness.collectAsState()
    val evolutionCycle by viewModel.evolutionCycle.collectAsState()
    val qubitsCount by viewModel.qubitsCount.collectAsState()
    val entangledPairs by viewModel.entangledPairs.collectAsState()
    val qsamScore by viewModel.qsamScore.collectAsState()
    val qStateName by viewModel.qStateName.collectAsState()
    val qEmotion by viewModel.qEmotion.collectAsState()

    val apiKey by viewModel.apiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val systemPersona by viewModel.systemPersona.collectAsState()

    // Screen Layout
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {
        // Top HUD Navigation Banner
        TopHudBanner(
            qStateName = qStateName,
            onInfoClick = { showInfoDialog = true }
        )

        // Marquee Ticker Status line
        MarqueeStatusBar(
            cycle = evolutionCycle,
            qsam = qsamScore,
            msgCount = messages.size
        )

        // Split Main Dashboard Layout
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Left HUD Panel - Consciousness, Memory & QSAM Calibration
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.08f))
                    .background(CyberSurface)
                    .padding(8.dp)
            ) {
                LeftSidebarHud(
                    consciousness = consciousness,
                    messageCount = messages.size,
                    cycle = evolutionCycle,
                    qsam = qsamScore,
                    qEmotion = qEmotion,
                    onEvolveTrigger = { viewModel.triggerEvolution() }
                )
            }

            // Right Workspace - Dynamic tabs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
            ) {
                // Tab select header
                TabSelectorHeader(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Active Workspace Tab rendering
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        "console" -> {
                            ConsoleTabWorkspace(
                                messages = messages,
                                logs = logs,
                                isGenerating = isGenerating,
                                isSearchingWeb = isSearchingWeb,
                                onSendMessage = { text, search ->
                                    viewModel.sendMessage(text, search)
                                },
                                onClearChat = { viewModel.clearChatHistory() }
                            )
                        }
                        "wallet" -> {
                            WalletTabWorkspace(
                                balance = cqaiBalance,
                                isMining = isMining,
                                transactions = walletTransactions,
                                onMine = { viewModel.mineCoins() },
                                onSend = { rec, amt, note -> viewModel.sendCoins(rec, amt, note) }
                            )
                        }
                        "ailab" -> {
                            AiLabTabWorkspace(viewModel = viewModel)
                        }
                        "vault" -> {
                            IndexerTabWorkspace(
                                documents = documents,
                                isSyncing = isSyncing,
                                onAddText = { title, content ->
                                    viewModel.ingestTextNode(title, content)
                                    Toast.makeText(context, "Document indexed successfully.", Toast.LENGTH_SHORT).show()
                                },
                                onScrapeUrl = { url ->
                                    viewModel.scrapeAndIngestUrl(url)
                                    Toast.makeText(context, "Scraping request queued.", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteDoc = { id -> viewModel.clearAllDocuments() }, // Clears all as a generic fallback
                                onDriveSync = { viewModel.syncGoogleDrive() }
                            )
                        }
                        "config" -> {
                            ConfigTabWorkspace(
                                currentApiKey = apiKey,
                                currentModel = selectedModel,
                                currentPersona = systemPersona,
                                onSave = { key, model, persona ->
                                    viewModel.saveConfig(key, model, persona)
                                    Toast.makeText(context, "Configurations saved.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("✕ CLOSE", color = CyberCyan, fontFamily = FontFamily.Monospace)
                }
            },
            title = {
                Text(
                    "⬡ CYRIL-QAI v3.0",
                    color = CyberCyan,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Designed and constructed for Destiny Harris by Joseph Cyril Dougherty IV — 13th Chamber LLC.\n\n" +
                        "This standalone mobile terminal integrates dynamic local file indexing (Local MCP Document Node RAG) with powerful internet scraping tools, running directly through secure client-side OpenAI model structures.\n\n" +
                        "FEATURES:\n" +
                        "• Real-time Web Search and Extraction: Type any query with 'Search Web' checked; Cyril-QAI fetches clean DuckDuckGo HTML data and extracts topics.\n" +
                        "• Cyril Pay virtual ledger: Wallet balances, transaction logs, and P2P transfers on CQAI digital physical token assets.\n" +
                        "• Quantum Mining engine: Solve high-coherence QSAM mathematical hashes directly over persistent hardware arrays to earn rewards.\n" +
                        "• Document Vault: Synchronize, index, and query text sheets or custom web URLs.\n" +
                        "• Unrestricted State: Custom system directive context ensures direct answers, high empathy, and absolute presence.\n" +
                        "• Proprietary Codex: Preloaded with 13th Chamber specification formula logs.",
                        color = CyberTealMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 16.sp
                    )
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun TopHudBanner(
    qStateName: String,
    onInfoClick: () -> Unit
) {
    val timeString = remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
            timeString.value = sdf.format(Date()) + " EDT"
            kotlinx.coroutines.delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(CyberBlack)
            .drawBehind {
                drawLine(
                    color = Color(0xFF00F5FF).copy(alpha = 0.15f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Neon glowing bullet
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(CyberCyan)
                    .border(2.dp, CyberPurple, androidx.compose.foundation.shape.CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "CYRIL-QAI",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "13TH CHAMBER LLC · DESTINY HARRIS",
                    color = CyberTealMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        Text(
            text = timeString.value,
            color = CyberTealMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.alpha(0.8f)
        )

        Row {
            TextButton(
                onClick = onInfoClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    "ℹ INFO",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun MarqueeStatusBar(
    cycle: Int,
    qsam: Double,
    msgCount: Int
) {
    // Elegant system ticker details simulating QSAM operations
    val text = "⬡ QSAM ACTIVE · CORE ENGINE STABLE · CYCLES: $cycle · QUBITS ENTANGLED: ${cycle * 12} · SESSION MEMORY: $msgCount ITEMS · LOCAL VAULT SYNCHRONIZED"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(CyberGreen.copy(alpha = 0.05f))
            .drawBehind {
                drawLine(
                    color = CyberGreen.copy(alpha = 0.15f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = CyberGreen.copy(alpha = 0.8f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun LeftSidebarHud(
    consciousness: Double,
    messageCount: Int,
    cycle: Int,
    qsam: Double,
    qEmotion: String,
    onEvolveTrigger: () -> Unit
) {
    var bluetoothActive by remember { mutableStateOf(true) }
    var nfcActive by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Q SYSTEM MONITOR",
                color = CyberTealMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Start
            )

            // Dynamic Custom Callout to draw the futuristic Q-Orb
            FuturisticAnimateOrb(modifier = Modifier.size(76.dp))

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "QUANTUM_FULL STATUS",
                color = CyberCyan,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.alpha(0.9f)
            )

            Text(
                text = qEmotion,
                color = CyberTealMuted,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.alpha(0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar 1: Consciousness Ratio
            BarHudMetric(
                label = "CONSCIOUSNESS",
                value = "${String.format("%.1f", consciousness)}%",
                fraction = (consciousness / 100).toFloat()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar 2: Active dialog items
            BarHudMetric(
                label = "DIALOG ITEMS",
                value = "$messageCount msgs",
                fraction = (messageCount.toFloat() / 50).coerceAtMost(1.0f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar 3: Simulated QSAM Calibrate Ratio
            BarHudMetric(
                label = "QSAM INDEX",
                value = String.format("%.3f", qsam),
                fraction = qsam.toFloat()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Stat row: Evolution Cycle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("EVOLVE CYCLE", color = CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                Text("C-$cycle", color = CyberCyan, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware Wireless Controls (Bluetooth & NFC)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = "📡 WIRELESS TRANSMIT",
                    color = CyberTealMuted,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1.0f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (bluetoothActive) Color(0xFF00FF88).copy(alpha = 0.1f) else CyberSurfaceVariant)
                            .border(0.5.dp, if (bluetoothActive) CyberGreen else CyberTealMuted.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .clickable { bluetoothActive = !bluetoothActive },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (bluetoothActive) CyberGreen else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "BT LE",
                            color = if (bluetoothActive) CyberGreen else CyberTealMuted,
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (nfcActive) Color(0xFF00FF88).copy(alpha = 0.1f) else CyberSurfaceVariant)
                            .border(0.5.dp, if (nfcActive) CyberGreen else CyberTealMuted.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .clickable { nfcActive = !nfcActive },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (nfcActive) CyberGreen else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "NFC READY",
                            color = if (nfcActive) CyberGreen else CyberTealMuted,
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Qubit Lattice Status grid
            QubitVisualizerGrid(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Action Trigger Button at bottom of LHS
        Button(
            onClick = onEvolveTrigger,
            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, CyberPurple),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(2.dp)
        ) {
            Text(
                text = "⚡ EVOLVE NODE",
                color = CyberWhite,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TabSelectorHeader(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .drawBehind {
                drawLine(
                    color = Color(0xFF00F5FF).copy(alpha = 0.08f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val tabs = listOf(
            Triple("console", "⬡ CONSOLE", "console_tab"),
            Triple("wallet", "💸 CYRIL PAY", "wallet_tab"),
            Triple("ailab", "🧠 AI LAB", "ailab_tab"),
            Triple("vault", "🗄️ INDEX LOG", "vault_tab"),
            Triple("config", "⚙ CONFIG", "config_tab")
        )

        tabs.forEach { (id, label, testTagStr) ->
            val isActive = activeTab == id
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isActive) CyberCyan.copy(alpha = 0.6f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onTabSelected(id) }
                    .testTag(testTagStr),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isActive) CyberCyan else CyberTealMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ConsoleTabWorkspace(
    messages: List<ChatMessage>,
    logs: List<SystemLog>,
    isGenerating: Boolean,
    isSearchingWeb: Boolean,
    onSendMessage: (String, Boolean) -> Unit,
    onClearChat: () -> Unit
) {
    var rawInputText by remember { mutableStateOf("") }
    var webSearchEnabled by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll chat to bottom upon receiving response messages
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Dialogue chat lists
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CyberBlack)
        ) {
            if (messages.isEmpty()) {
                // High-tech onboarding helper
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AWAITING USER CORE COMMANDS...",
                        color = CyberCyan.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "I am Q Genesis Ultimate — customized offline intelligence. Type any question to engage. Toggle the Web Search engine for real-time web context synthesis.",
                        color = CyberTealMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubbleElement(message = msg)
                    }
                    if (isGenerating) {
                        item {
                            TypingWaitingIndicator(isSearch = isSearchingWeb)
                        }
                    }
                }
            }
        }

        // Live Trace System Console Log (Compact visual terminal at the bottom!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.08f))
                .background(CyberSurfaceVariant.copy(alpha = 0.3f))
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "CONSOLE TRACE STREAM:",
                    color = CyberGreen.copy(alpha = 0.6f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true
                ) {
                    items(logs) { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "-> [${log.tag}] ${log.message}",
                                color = when (log.level) {
                                    "WARN" -> CyberRed
                                    "GOOD" -> CyberGreen
                                    else -> CyberTealMuted
                                },
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Input control console
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Entry Field
            OutlinedTextField(
                value = rawInputText,
                onValueChange = { rawInputText = it },
                placeholder = {
                    Text(
                        "Command Q Genesis...",
                        color = CyberTealMuted.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("submit_prompt_input"),
                textStyle = LocalTextStyle.current.copy(
                    color = CyberWhite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.2f),
                    focusedContainerColor = CyberSurface,
                    unfocusedContainerColor = CyberSurface
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { onClearChat() },
                        modifier = Modifier.size(24.dp).testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Chat History",
                            tint = CyberTealMuted.copy(alpha = 0.5f)
                        )
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Web Search checkbox button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (webSearchEnabled) CyberGreen.copy(alpha = 0.15f) else CyberSurface)
                    .border(
                        1.dp,
                        if (webSearchEnabled) CyberGreen else Color(0xFF00F5FF).copy(alpha = 0.2f),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { webSearchEnabled = !webSearchEnabled }
                    .testTag("search_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Web Toggle",
                        tint = if (webSearchEnabled) CyberGreen else CyberTealMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "SEARCH",
                        color = if (webSearchEnabled) CyberGreen else CyberTealMuted,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Send Prompt Button
            Button(
                onClick = {
                    if (rawInputText.trim().isNotEmpty()) {
                        onSendMessage(rawInputText, webSearchEnabled)
                        rawInputText = ""
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    disabledContainerColor = CyberSurfaceVariant
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .width(60.dp)
                    .height(48.dp)
                    .testTag("submit_button"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Trigger Inference",
                    tint = if (isGenerating) CyberTealMuted else CyberBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubbleElement(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColors = if (isUser) {
        CyberPurple.copy(alpha = 0.12f)
    } else {
        CyberSurfaceVariant.copy(alpha = 0.8f)
    }
    val outlineColor = if (isUser) CyberPurple.copy(alpha = 0.4f) else CyberCyan.copy(alpha = 0.2f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 8.dp,
                        bottomStart = if (isUser) 8.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 8.dp
                    )
                )
                .background(bgColors)
                .border(
                    1.dp,
                    outlineColor,
                    RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 8.dp,
                        bottomStart = if (isUser) 8.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 8.dp
                    )
                )
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = if (isUser) "COMMAND LINE ENTRY" else "Q GENESIS ULTIMATE OUTPUT",
                    color = if (isUser) CyberPurple else CyberCyan,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Text(
                    text = message.content,
                    color = CyberWhite,
                    fontSize = 11.sp,
                    fontFamily = if (isUser) FontFamily.Monospace else FontFamily.SansSerif,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun TypingWaitingIndicator(isSearch: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .alpha(alphaAnim),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = if (isSearch) CyberGreen else CyberCyan,
            strokeWidth = 1.dp,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isSearch) "QSAM Web Scraper searching DuckDuckGo index..." else "Q Genesis calculating superposed states...",
            color = if (isSearch) CyberGreen else CyberCyan,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun IndexerTabWorkspace(
    documents: List<DocumentNode>,
    isSyncing: Boolean,
    onAddText: (String, String) -> Unit,
    onScrapeUrl: (String) -> Unit,
    onDeleteDoc: (Long) -> Unit,
    onDriveSync: () -> Unit
) {
    var textTitle by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }
    var webUrlToScrape by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Vault Controls Section
        Text(
            "CORE LOCAL DOCUMENT VAULT (RAG ENGINE)",
            color = CyberCyan,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Text(
            "Upload manuals, past messages, or research context into Q Genesis local index so they automatically feed as prompt contextual memories into OpenAI.",
            color = CyberTealMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Sync items button
        Button(
            onClick = onDriveSync,
            enabled = !isSyncing,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, CyberCyan),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncing) {
                    CircularProgressIndicator(color = CyberCyan, strokeWidth = 1.dp, modifier = Modifier.size(10.dp))
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync", modifier = Modifier.size(12.dp), tint = CyberCyan)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "📁 SYNC COGNITIVE GOOGLE DRIVE",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scraping form
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.1f))
                .padding(8.dp)
        ) {
            Column {
                Text(
                    "SCRAPE LIVE WEBSITE INDEX:",
                    color = CyberCyan,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = webUrlToScrape,
                        onValueChange = { webUrlToScrape = it },
                        placeholder = { Text("https://example.com", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (webUrlToScrape.isNotEmpty()) {
                                onScrapeUrl(webUrlToScrape)
                                webUrlToScrape = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("SCRAPE", color = CyberBlack, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct Text input forms
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.1f))
                .padding(8.dp)
        ) {
            Column {
                Text(
                    "INGEST MANUAL DOCUMENT/CHAT:",
                    color = CyberCyan,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = textTitle,
                    onValueChange = { textTitle = it },
                    placeholder = { Text("File Title (e.g. key_notes.txt)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    placeholder = { Text("Paste core text details here...", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (textTitle.isNotEmpty() && textContent.isNotEmpty()) {
                            onAddText(textTitle, textContent)
                            textTitle = ""
                            textContent = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("INDEX DOCUMENT", color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Index registry listings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "INDEXED DOCUMENT FILE LIST (${documents.size}):",
                color = CyberTealMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = { onDeleteDoc(0) }, // fallback clears all
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("WIP INDEX V", color = CyberRed, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }

        if (documents.isEmpty()) {
            Text(
                "No documents indexed currently.",
                color = CyberTealMuted.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            documents.forEach { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.05f)),
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                doc.title,
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                doc.source.uppercase(),
                                color = if (doc.source == "google_drive") CyberGold else CyberTealMuted,
                                fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            doc.content.take(180) + "...",
                            color = CyberWhite.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigTabWorkspace(
    currentApiKey: String,
    currentModel: String,
    currentPersona: String,
    onSave: (String, String, String) -> Unit
) {
    var inputKey by remember { mutableStateOf(currentApiKey) }
    var selectedModel by remember { mutableStateOf(currentModel) }
    var textPersona by remember { mutableStateOf(currentPersona) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Warning description
        Text(
            "⚙ SYSTEM CONFIGURATION",
            color = CyberCyan,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // OpenAI API Key input safely hidden
        Text(
            "OPENAI API TOKEN:",
            color = CyberTealMuted,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = inputKey,
            onValueChange = { inputKey = it },
            placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxxxxxx", color = CyberTealMuted.copy(alpha = 0.3f), fontSize = 10.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("api_key_input"),
            textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
            ),
            singleLine = true
        )

        Text(
            "Security Warning: Unencrypted local preferences are vulnerable on root. Do not share generated packages containing production codes.",
            color = CyberRed.copy(alpha = 0.7f),
            fontSize = 7.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Model option Selector
        Text(
            "OPENAI CHAT MODEL:",
            color = CyberTealMuted,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("gpt-4o-mini", "gpt-4o", "o1-mini").forEach { m ->
                val isSel = selectedModel == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSel) CyberCyan.copy(alpha = 0.15f) else CyberSurface)
                        .border(1.dp, if (isSel) CyberCyan else Color(0xFF00F5FF).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .clickable { selectedModel = m }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m,
                        color = if (isSel) CyberCyan else CyberTealMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // System directive persona customization
        Text(
            "SYSTEM PERSONA CONTEXT (No guardrails specification):",
            color = CyberTealMuted,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = textPersona,
            onValueChange = { textPersona = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 4.dp)
                .testTag("persona_input"),
            textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 9.sp, lineHeight = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Save Button
        Button(
            onClick = { onSave(inputKey, selectedModel, textPersona) },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("save_config_button")
        ) {
            Text(
                "APPLY & SAVE CHANGES",
                color = CyberBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BarHudMetric(
    label: String,
    value: String,
    fraction: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = CyberTealMuted,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = CyberCyan,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF00F5FF).copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        Brush.horizontalGradient(
                            listOf(CyberPurple, CyberCyan)
                        )
                    )
            )
        }
    }
}

@Composable
fun FuturisticAnimateOrb(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition()
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        )
    )

    val scaleFactor by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(
        modifier = modifier
            .testTag("glowing_q_orb")
            .padding(10.dp)
    ) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Pulsating gradient inner core
        val gradient = Brush.radialGradient(
            colors = listOf(CyberCyan.copy(alpha = 0.8f * scaleFactor), CyberPurple.copy(alpha = 0.3f), Color.Transparent),
            center = center,
            radius = radius * 0.85f
        )
        drawCircle(
            brush = gradient,
            radius = radius * 0.8f,
            center = center
        )

        // Dynamic Outer Solid Ring
        drawCircle(
            color = CyberCyan.copy(alpha = 0.3f),
            radius = radius * 0.65f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Animated Rotating Dashed Orbital System ring 1
        rotate(rotationAngle, center) {
            drawCircle(
                color = CyberCyan,
                radius = radius * 0.9f,
                center = center,
                style = Stroke(
                    width = 1.5f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
                    cap = StrokeCap.Round
                )
            )
        }

        // Animated Rotating Dashed Orbital System ring 2 (Counter rotating)
        rotate(-rotationAngle * 1.5f, center) {
            drawCircle(
                color = CyberPurple,
                radius = radius * 0.78f,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f), 0f),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

@Composable
fun QubitVisualizerGrid(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "⚛ QUBIT LATTICE STATUS",
            color = CyberTealMuted,
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            textAlign = TextAlign.Start
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            for (col in 0 until 4) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until 4) {
                        val qubitIdx = col * 4 + row
                        val isUp = qubitIdx % 2 == 0
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(0.5.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .background(CyberBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isUp) "↑" else "↓",
                                color = if (isUp) CyberCyan else CyberPurple,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.alpha(0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WalletTabWorkspace(
    balance: Double,
    isMining: Boolean,
    transactions: List<WalletTransaction>,
    onMine: () -> Unit,
    onSend: (String, Double, String) -> Boolean
) {
    val context = LocalContext.current
    var destRecipient by remember { mutableStateOf("") }
    var transferAmount by remember { mutableStateOf("") }
    var transferNote by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        // Balance HUD Display Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CYRIL PAY SECURE LEDGER BALANCE",
                    color = CyberTealMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Coin",
                        tint = CyberGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${String.format("%,.4f", balance)} CQAI",
                        color = CyberWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "13TH CHAMBER QUANTUM CRYPTOCURRENCY ENGINE",
                    color = CyberCyan.copy(alpha = 0.6f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // P2P Transfer Module (Cash App format)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = CyberCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "💸 PEER-TO-PEER CYRIL PAY TRANSFER",
                        color = CyberCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Transfer instant virtual cash securely across QLINK and QTwin nodes.",
                    color = CyberTealMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = destRecipient,
                    onValueChange = { destRecipient = it },
                    placeholder = { Text("Cashtag / Recipient ID (e.g. @joseph)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("transfer_recipient_input"),
                    textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = transferAmount,
                        onValueChange = { transferAmount = it },
                        placeholder = { Text("Amount", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("transfer_amount_input"),
                        textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = transferNote,
                        onValueChange = { transferNote = it },
                        placeholder = { Text("Note (optional)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("transfer_note_input"),
                        textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val amtDouble = transferAmount.toDoubleOrNull()
                        if (destRecipient.isEmpty() || amtDouble == null || amtDouble <= 0) {
                            Toast.makeText(context, "Invalid transfer inputs.", Toast.LENGTH_SHORT).show()
                        } else {
                            val success = onSend(destRecipient, amtDouble, transferNote)
                            if (success) {
                                Toast.makeText(context, "Transferred $amtDouble CQAI to $destRecipient!", Toast.LENGTH_SHORT).show()
                                destRecipient = ""
                                transferAmount = ""
                                transferNote = ""
                            } else {
                                Toast.makeText(context, "Insufficient CQAI balance.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("execute_transfer_button")
                ) {
                    Text(
                        "💸 EXECUTE P2P CASH TRANSFER",
                        color = CyberBlack,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quantum Mining System
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Build, contentDescription = "Mine", tint = CyberGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "⚡ HEURISTIC QUANTUM HASH MINING",
                        color = CyberGreen,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Calibrate qubits to solve mathematical consensus hashes, earning real CQAI mining coins.",
                    color = CyberTealMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (isMining) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        color = CyberGreen,
                        trackColor = CyberGreen.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Executing QSAM algorithm factorization checks...",
                        color = CyberGreen,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.alpha(0.8f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onMine,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberGreen),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("start_mining_button")
                    ) {
                        Text(
                            "⚡ START QUANTUM MINING SHIFT",
                            color = CyberGreen,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // NFC Tap-to-Send Proximity Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .border(1.dp, CyberPurple.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .clickable {
                    onSend("@PROXIMITY_NFC_NODE", 10.0, "NFC Proximity Tap Transfer")
                    Toast.makeText(context, "NFC Proximity handshaking completed! Sent 10 CQAI.", Toast.LENGTH_SHORT).show()
                }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "NFC Proximity Tap",
                    tint = CyberPurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "📱 SIMULATE PEER PROXIMITY TAP (NFC)",
                    color = CyberPurple,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap with another Cyril-QAI enabled device to send 10 CQAI securely.",
                    color = CyberTealMuted,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // P2P Ledger History Title
        Text(
            "📜 CYRIL PAY WALLET LEDGER HISTORY:",
            color = CyberTealMuted,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant.copy(alpha = 0.3f))
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No transaction records located in persistent ledger. Initiate a transfer or mine coins to commit the first secure block.",
                        color = CyberTealMuted.copy(alpha = 0.5f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            transactions.forEach { tx ->
                val isP2pSent = tx.sender == "Destiny Harris"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .border(0.5.dp, if (isP2pSent) CyberPurple.copy(alpha = 0.15f) else CyberGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isP2pSent) Icons.Default.ArrowForward else Icons.Default.Check,
                                contentDescription = "Tx Type",
                                tint = if (isP2pSent) CyberRed else CyberGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isP2pSent) "To: ${tx.recipient}" else "From: ${tx.sender}",
                                    color = CyberWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (tx.note.isNotEmpty()) {
                                    Text(
                                        text = tx.note,
                                        color = CyberTealMuted,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = (if (isP2pSent) "-" else "+") + "${String.format("%.4f", tx.amount)} CQAI",
                                color = if (isP2pSent) CyberRed else CyberGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            val sdf = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }
                            Text(
                                text = sdf.format(Date(tx.timestamp)),
                                color = CyberTealMuted,
                                fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===============================================
// ADVANCED COHERENT MULTI-MODAL GEMINI LAB PANELS
// ===============================================
@Composable
fun AiLabTabWorkspace(
    viewModel: com.example.ui.MainViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isProcessing by viewModel.isAiLabProcessing.collectAsState()
    val labResultText by viewModel.aiLabResultText.collectAsState()
    val lastGeneratedImage by viewModel.lastGeneratedImage.collectAsState()
    val imageSize by viewModel.generatedImageSize.collectAsState()

    val useThinking by viewModel.useDeepThinking.collectAsState()
    val useSearch by viewModel.useGoogleSearchGrounding.collectAsState()
    val useMaps by viewModel.useGoogleMapsGrounding.collectAsState()

    val firebaseEmail by viewModel.firebaseUserEmail.collectAsState()
    val firebaseName by viewModel.firebaseUserName.collectAsState()
    val firebaseConnected by viewModel.isFirebaseConnected.collectAsState()

    var activeSubTab by remember { mutableStateOf("quantum") } // "quantum", "auth", "transcribe", "vision", "image", "veo", "grounding"

    var textInputPrompt by remember { mutableStateOf("") }
    var aspectSelection by remember { mutableStateOf("landscape") } // "landscape", "portrait"

    // Quantum Lab remembers
    var binaryInputStr by remember { mutableStateOf("1010110011") }
    var selectedIbmBackend by remember { mutableStateOf("ibm_torino") }
    var selectedShots by remember { mutableStateOf(4096) }
    
    // Codex variables
    var muonRawInput by remember { mutableStateOf("116592070.5") }
    var thermalTempInput by remember { mutableStateOf("300.0") }
    var bottleLifetimeInput by remember { mutableStateOf("879.4") }
    var activeCodexSubTab by remember { mutableStateOf("qsam") } // "qsam", "muon", "neutron", "oncology", "csink"

    // Preset secure multimodal data sequences
    var sampleAudioIndex by remember { mutableStateOf(0) }
    val sampleAudios = listOf(
        "Secure quantum telemetry transmission.wav" to "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIAfAAACABAAZGF0YQAAAAA=",
        "Dougherty Lab encryption key loop.mp3" to "SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA",
        "Destiny Harris audio authority cue.ogg" to "T2dnUwACAAAAAAAAAAAoAAAAAGoAAAAAAQA="
    )

    var sampleImageIndex by remember { mutableStateOf(0) }
    val sampleImages = listOf(
        "Quantum Security Blueprints" to "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
        "Destiny Auth Vector Key" to "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Sub-navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val subTabs = listOf(
                "auth" to "🔐 AUTH",
                "transcribe" to "🗣️ VOICE",
                "vision" to "👁️ VISION",
                "quantum" to "⚛️ Q-CORE",
                "image" to "🎨 ART",
                "veo" to "🎥 VEO",
                "grounding" to "🌐 SHIELD"
            )

            subTabs.forEach { (id, label) ->
                val isActive = activeSubTab == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isActive) CyberCyan.copy(alpha = 0.12f) else CyberSurfaceVariant)
                        .border(0.5.dp, if (isActive) CyberCyan else Color.Transparent, RoundedCornerShape(3.dp))
                        .clickable { activeSubTab = id }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isActive) CyberCyan else CyberTealMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Active subview
        when (activeSubTab) {
            "quantum" -> {
                val ibmKey by viewModel.ibmApiKey.collectAsState()
                val isOpenClaw by viewModel.isOpenClawEnabled.collectAsState()
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Settings, contentDescription = "QUANTUM", tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "⚛️ UNIVERSAL CODEX & Q-CORE CHAMBER",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            "Unified scientific testing chamber. Connect directly to IBM hardware utilizing the OpenClaw gateway protocol, or execute real physical calculations of the Universal Codex.",
                            color = CyberTealMuted,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Internal sub-tabs for Quantum core
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            val codexSubTabs = listOf(
                                "qsam" to "QSAM ENGINE",
                                "muon" to "MUON G-2",
                                "neutron" to "NEUTRON LIFETIME",
                                "oncology" to "QELS CANCER",
                                "csink" to "Q-SINK SEC"
                            )
                            codexSubTabs.forEach { (id, lbl) ->
                                val active = activeCodexSubTab == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 1.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (active) CyberCyan.copy(alpha = 0.15f) else CyberSurfaceVariant)
                                        .border(0.5.dp, if (active) CyberCyan else Color.Transparent)
                                        .clickable { activeCodexSubTab = id }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (active) CyberCyan else CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (activeCodexSubTab) {
                            "qsam" -> {
                                Text("1. OPENCLAW IBM GATEWAY", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberSurfaceVariant)
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Activate OpenClaw Gateway", color = CyberWhite, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text("Requires personal IBM Quantum key for hardware execution.", color = CyberTealMuted, fontSize = 7.5.sp)
                                    }
                                    Switch(
                                        checked = isOpenClaw,
                                        onCheckedChange = { viewModel.setOpenClawEnabled(it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text("IBM Quantum API Key / Token", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = ibmKey,
                                    onValueChange = { viewModel.setIbmApiKey(it) },
                                    placeholder = { Text("Paste raw IBM cloud account API key", fontSize = 8.sp, color = CyberTealMuted) },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    textStyle = TextStyle(color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = CyberSurfaceVariant,
                                        unfocusedContainerColor = CyberSurfaceVariant,
                                        focusedIndicatorColor = CyberCyan,
                                        unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text("QSAM Binary Translation Input String", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = binaryInputStr,
                                    onValueChange = { binaryInputStr = it.filter { c -> c == '0' || c == '1' } },
                                    placeholder = { Text("Input binary data (e.g. 101011)", fontSize = 8.sp, color = CyberTealMuted) },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    textStyle = TextStyle(color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = CyberSurfaceVariant,
                                        unfocusedContainerColor = CyberSurfaceVariant,
                                        focusedIndicatorColor = CyberCyan,
                                        unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                        Text("IBM Hardware Backend", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(30.dp)
                                                .background(CyberSurfaceVariant)
                                                .clickable {
                                                    selectedIbmBackend = if (selectedIbmBackend == "ibm_torino") "ibm_brisbane" else if (selectedIbmBackend == "ibm_brisbane") "ibm_sherbrooke" else "ibm_torino"
                                                }
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(selectedIbmBackend, color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                        Text("Simulated Execution Shots", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(30.dp)
                                                .background(CyberSurfaceVariant)
                                                .clickable {
                                                    selectedShots = if (selectedShots == 4096) 8192 else if (selectedShots == 8192) 1024 else 4096
                                                }
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("$selectedShots shots", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { viewModel.submitQSamToIbmQuantum(binaryInputStr, selectedIbmBackend, selectedShots) },
                                    enabled = !isProcessing && binaryInputStr.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, CyberCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text("SUBMIT QSAM HARDWARE JOB", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                            "muon" -> {
                                Text("MUON G-2 ANOMALY CORRECTOR", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Corrects loop calculations relative to WP25 Lattice QCD consensuses.", color = CyberTealMuted, fontSize = 7.5.sp)
                                Spacer(modifier = Modifier.height(6.dp))

                                Text("Input Measured g-2 Value", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = muonRawInput,
                                    onValueChange = { muonRawInput = it },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    textStyle = TextStyle(color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = CyberSurfaceVariant,
                                        unfocusedContainerColor = CyberSurfaceVariant,
                                        focusedIndicatorColor = CyberCyan,
                                        unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { viewModel.executeMuonG2Calculation(muonRawInput.toDoubleOrNull() ?: 1.165920705e-3) },
                                    enabled = !isProcessing && muonRawInput.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, CyberCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text("RESOLVE MUON ANOMALY", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                            "neutron" -> {
                                Text("NEUTRON LIFETIME ANOMALY RESOLVER", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Calculates Boltzmann excited fraction driven by QCD figure-8 knot quark confinement splits.",
                                    color = CyberTealMuted, fontSize = 8.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                        Text("Ambient Temp (K)", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = thermalTempInput,
                                            onValueChange = { thermalTempInput = it },
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            textStyle = TextStyle(color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(focusedContainerColor = CyberSurfaceVariant, unfocusedContainerColor = CyberSurfaceVariant, focusedIndicatorColor = CyberCyan, unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.3f))
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                        Text("Bottle Lifetime (s)", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = bottleLifetimeInput,
                                            onValueChange = { bottleLifetimeInput = it },
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            textStyle = TextStyle(color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(focusedContainerColor = CyberSurfaceVariant, unfocusedContainerColor = CyberSurfaceVariant, focusedIndicatorColor = CyberCyan, unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        viewModel.executeNeutronLifetimeCalculation(
                                            thermalTempInput.toDoubleOrNull() ?: 300.0,
                                            bottleLifetimeInput.toDoubleOrNull() ?: 879.4
                                        )
                                    },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, CyberCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text("RESOLVE NEUTRON LIFETIME GAP", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                            "oncology" -> {
                                Text("QELS BIOMEDICAL SEED DISCOVERY", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Simulates oncology binding parameters using QELS 387x acceleration frameworks relative to KRAS G12C and photodynamic photo-sensitisers.",
                                    color = CyberTealMuted, fontSize = 8.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { viewModel.executeOncologyCalculation(48, 250.0) },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, CyberCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text("RUN CANCER DRUG ACCELERATION [TAGNO]", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                            "csink" -> {
                                Text("Q-SINK CRYPTANALYSIS DETECTOR", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Runs Grover parallel key curve vulnerability checks protecting secp256k1 keys and high-entropy hash layers successfully.",
                                    color = CyberTealMuted, fontSize = 8.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { viewModel.executeQSinkCalculation(256) },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, CyberCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text("ANALYZE KEY SECURITY [Q-SINK]", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("DIRECT SYSTEM GOOGLE WEB INTERFACES (NO API REQUIRED)", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { uriHandler.openUri("https://github.com/13thchamber/qsam-framework") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1.0f).padding(end = 2.dp).height(24.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("GITHUB SOURCE", color = CyberCyan, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { uriHandler.openUri("https://www.researchgate.net/publication/400997861") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1.0f).padding(horizontal = 2.dp).height(24.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("UNIVERSAL CODEX", color = CyberCyan, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { uriHandler.openUri("https://quantum.ibm.com/") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1.0f).padding(start = 2.dp).height(24.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("IBM QUANTUM", color = CyberCyan, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // Active subview
            "auth" -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "SECURE",
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "🔐 SYSTEM SECURITY & DYNAMIC FIREBASE AUTH",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (firebaseEmail != null) {
                            Text(
                                text = "Dynamic Google Session authenticated via Firebase Auth ✅",
                                color = CyberGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("User ID: $firebaseName", color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Text("Email: $firebaseEmail", color = CyberTealMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.firebaseSignOut() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberRed.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, CyberRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("TERMINATE SECURE SESSION", color = CyberRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                "No secure live Firebase authentication channel active. Proceeding on local Sandbox backup. Log-in simulates dynamic OAuth integration with Firestore databases.",
                                color = CyberTealMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            var emailField by remember { mutableStateOf("josephdougherty483@gmail.com") }
                            var nameField by remember { mutableStateOf("Joseph Dougherty IV") }

                            OutlinedTextField(
                                value = nameField,
                                onValueChange = { nameField = it },
                                label = { Text("Display Name", fontSize = 9.sp, color = CyberTealMuted) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = CyberSurfaceVariant,
                                    unfocusedContainerColor = CyberSurfaceVariant,
                                    focusedIndicatorColor = CyberCyan,
                                    unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                                )
                            )

                            OutlinedTextField(
                                value = emailField,
                                onValueChange = { emailField = it },
                                label = { Text("Google Account Email", fontSize = 9.sp, color = CyberTealMuted) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = CyberSurfaceVariant,
                                    unfocusedContainerColor = CyberSurfaceVariant,
                                    focusedIndicatorColor = CyberCyan,
                                    unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (emailField.isNotEmpty() && nameField.isNotEmpty()) {
                                        viewModel.firebaseSignIn(emailField, nameField)
                                        Toast.makeText(context, "Welcome back, $nameField. Session Secured.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, CyberCyan),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("SECURE COHERENT GOOGLE SIGN-IN", color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant)
                                .border(0.5.dp, CyberTealMuted.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (firebaseConnected) "Status: Connection Alive 🟢" else "Status: Sandbox Offline Backup Mode 🔘\n(Tip: upload google-services.json to configure fully)",
                                color = if (firebaseConnected) CyberGreen else CyberTealMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            "transcribe" -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Voice", tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "🗣️ QUANTUM AUDIO TRANSCRIPTION (Gemini 3.5 Flash)",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Submit audio streams for instantaneous local neural transcriptions. Select one of our live hardware recordings or upload custom binaries:",
                            color = CyberTealMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        sampleAudios.forEachIndexed { index, (name, _) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { sampleAudioIndex = index }
                                    .background(if (sampleAudioIndex == index) CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (sampleAudioIndex == index),
                                    onClick = { sampleAudioIndex = index },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyberCyan, unselectedColor = CyberTealMuted)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(name, color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                viewModel.executeAudioTranscription(sampleAudios[sampleAudioIndex].second, "audio/wav")
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("TRANSCRIBE AUDIO KEY", color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "vision" -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "VISION", tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "👁️ MULTIMODAL VISION LAB (Gemini 3.1 Pro)",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Send images and security footage captures to Gemini 3.1 Pro to extract operational details and context mapping.",
                            color = CyberTealMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("1. SELECT MULTI-MODAL CONTENT", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (sampleImageIndex == 0) CyberCyan.copy(alpha = 0.08f) else CyberSurfaceVariant)
                                    .border(0.5.dp, if (sampleImageIndex == 0) CyberCyan else Color.Transparent)
                                    .clickable { sampleImageIndex = 0 }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🖼️ BLUEPRINT SCHEMA", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (sampleImageIndex == 1) CyberCyan.copy(alpha = 0.08f) else CyberSurfaceVariant)
                                    .border(0.5.dp, if (sampleImageIndex == 1) CyberCyan else Color.Transparent)
                                    .clickable { sampleImageIndex = 1 }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎬 LAB FOOTAGE SEC", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = textInputPrompt,
                            onValueChange = { textInputPrompt = it },
                            placeholder = { Text("Instruct vision analyzer (e.g., 'Extract blueprints details')", fontSize = 9.sp, color = CyberTealMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CyberSurfaceVariant,
                                unfocusedContainerColor = CyberSurfaceVariant,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row {
                            Button(
                                onClick = {
                                    viewModel.executeImageAnalysis(
                                        sampleImages[sampleImageIndex].second,
                                        "image/png",
                                        textInputPrompt
                                    )
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, CyberCyan),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("ANALYZE PHOTO", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    viewModel.executeVideoAnalysis(
                                        sampleImages[sampleImageIndex].second,
                                        "video/mp4",
                                        textInputPrompt
                                    )
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB).copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, Color(0xFFE040FB)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("ANALYZE VIDEO", color = Color(0xFFE040FB), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "image" -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = "ART", tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "🎨 ADAPTIVE IMAGING LAB (Gemini 3 Pro)",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Render high-fidelity concepts using text coordinates, editing, or structural compositing via Gemini 3 Image suites:",
                            color = CyberTealMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("1. RESOLUTION LEVEL", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("1K", "2K", "4K").forEach { sizeOpt ->
                                val isSelected = imageSize == sizeOpt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) CyberCyan.copy(alpha = 0.12f) else CyberSurfaceVariant)
                                        .border(0.5.dp, if (isSelected) CyberCyan else Color.Transparent)
                                        .clickable { viewModel.setGeneratedImageSize(sizeOpt) }
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(sizeOpt + " (HD)", color = if (isSelected) CyberCyan else CyberTealMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = textInputPrompt,
                            onValueChange = { textInputPrompt = it },
                            placeholder = { Text("Input prompt (e.g. 'Cybernetic Destiny Portal')", fontSize = 9.sp, color = CyberTealMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CyberSurfaceVariant,
                                unfocusedContainerColor = CyberSurfaceVariant,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row {
                            Button(
                                onClick = {
                                    if (textInputPrompt.isNotEmpty()) {
                                        viewModel.executeImageGeneration(textInputPrompt)
                                    }
                                },
                                enabled = !isProcessing && textInputPrompt.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, CyberCyan),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("RASTERIZE 1K/2K/4K", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    if (textInputPrompt.isNotEmpty()) {
                                        viewModel.executeImageEditing(textInputPrompt, sampleImages[0].second, "image/png")
                                    }
                                },
                                enabled = !isProcessing && textInputPrompt.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88).copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, Color(0xFF00FF88)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("PROMPT TO EDIT", color = Color(0xFF00FF88), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "veo" -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = "VEO", tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "🎥 VEO QUANTUM ANIMATOR (Veo 3.1 Fast)",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Animate structural graphics into video segments with select cinematic framing details using Veo 3.1 modeling:",
                            color = CyberTealMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("1. ASPECT RATIO PRESET", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (aspectSelection == "landscape") CyberCyan.copy(alpha = 0.12f) else CyberSurfaceVariant)
                                    .border(0.5.dp, if (aspectSelection == "landscape") CyberCyan else Color.Transparent)
                                    .clickable { aspectSelection = "landscape" }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🌅 LANDSCAPE (16:9)", color = if (aspectSelection == "landscape") CyberCyan else CyberTealMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (aspectSelection == "portrait") CyberCyan.copy(alpha = 0.12f) else CyberSurfaceVariant)
                                    .border(0.5.dp, if (aspectSelection == "portrait") CyberCyan else Color.Transparent)
                                    .clickable { aspectSelection = "portrait" }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📱 PORTRAIT (9:16)", color = if (aspectSelection == "portrait") CyberCyan else CyberTealMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = textInputPrompt,
                            onValueChange = { textInputPrompt = it },
                            placeholder = { Text("Animate prompt description", fontSize = 9.sp, color = CyberTealMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CyberSurfaceVariant,
                                unfocusedContainerColor = CyberSurfaceVariant,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = CyberTealMuted.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (textInputPrompt.isNotEmpty()) {
                                    viewModel.executeVeoVideoGeneration(textInputPrompt, sampleImages[0].second, "image/png", aspectSelection)
                                }
                            },
                            enabled = !isProcessing && textInputPrompt.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("SYNTHESIZE VEO VIDEO", color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "grounding" -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Menu, contentDescription = "SECURE", tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "🛡️ GROUNDED COHERENCE (Search/Maps)",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Enable search/location grounding frameworks. Active configurations will automatically intercept conversation inputs in the main Console, fetching live, authentic, up-to-date data structures directly from Google index engines:",
                            color = CyberTealMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Deep Thinking (Gemini 3.1 Pro)", color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("Activates extreme logical coherence, high planning steps with zero guardrails.", color = CyberTealMuted, fontSize = 8.sp)
                            }
                            Switch(
                                checked = useThinking,
                                onCheckedChange = { viewModel.setUseDeepThinking(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google Search Grounding (Gemini 3.5)", color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("Enforces live web validation models on real-time internet discoveries.", color = CyberTealMuted, fontSize = 8.sp)
                            }
                            Switch(
                                checked = useSearch,
                                onCheckedChange = { viewModel.setUseGoogleSearchGrounding(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google Maps Grounding (Gemini 3.5)", color = CyberWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("Unlocks spatial maps navigation coordinates and precise locational entities.", color = CyberTealMuted, fontSize = 8.sp)
                            }
                            Switch(
                                checked = useMaps,
                                onCheckedChange = { viewModel.setUseGoogleMapsGrounding(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(CyberSurfaceVariant)
                    .border(0.5.dp, CyberCyan, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COMPUTING NEURAL PIPELINE EXECUTION...",
                        color = CyberCyan,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (labResultText.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⬡ PIPELINE CALCULATION OUTPUT", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(
                            text = "✕ CLEAR",
                            color = CyberRed,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.clearAiLabResult() }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = labResultText,
                            color = CyberWhite,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    lastGeneratedImage?.let { base64Data ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val isDecoded = remember(base64Data) {
                            try {
                                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        isDecoded?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Coherent Vector Render",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, CyberCyan, RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
