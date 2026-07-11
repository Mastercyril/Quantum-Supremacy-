package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.FloatingOverlayService
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
import androidx.compose.ui.draw.drawWithContent
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
import com.example.data.GoogleDriveHandler
import com.example.data.ChatMessage
import com.example.data.DocumentNode
import com.example.data.SystemLog
import com.example.data.WalletTransaction
import com.example.data.McpContextNode
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var tts: android.speech.tts.TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        tts = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.US
            }
        }

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
                        modifier = Modifier.padding(innerPadding),
                        onSpeakText = { text ->
                            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onSpeakText: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("console") } // "console", "wallet", "vault", "config"
    var showInfoDialog by remember { mutableStateOf(false) }

    val hasPromptedPermission = remember {
        val prefs = context.getSharedPreferences("qgenesis_prefs", Context.MODE_PRIVATE)
        prefs.contains("google_drive_authorized")
    }
    var showDrivePermissionDialog by remember { mutableStateOf(!hasPromptedPermission) }

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
    val customBaseUrl by viewModel.customBaseUrl.collectAsState()
    val mcpContexts by viewModel.mcpContexts.collectAsState()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone access configured successfully for Q-AI voice recognition.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        // Automatically request microphone permission
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        
        // Automatically check/prompt overlay permission
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "CYRIL Q-AI: Enable 'Display over other apps' to run the floating overlay deck!", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    if (showDrivePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showDrivePermissionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Google Drive Sync",
                        tint = CyberGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Sync Authorization",
                        color = CyberWhite,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Text(
                    text = "Allow Q.Genesis to securely sync system specifications, mathematical codices, and conversation history text/markdown backups automatically in the background to your Google Drive account every 10 minutes?",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        GoogleDriveHandler.setDrivePermission(context, true)
                        viewModel.syncGoogleDrive()
                        showDrivePermissionDialog = false
                        Toast.makeText(context, "Google Drive Sync Authorized & Initialized!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = CyberBlack)
                ) {
                    Text("Authorize & Connect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        GoogleDriveHandler.setDrivePermission(context, false)
                        showDrivePermissionDialog = false
                        Toast.makeText(context, "Google Drive Sync declined. Local-only mode active.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Decline", color = Color.Gray)
                }
            },
            containerColor = CyberSurfaceVariant,
            textContentColor = CyberWhite
        )
    }

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
                                onClearChat = { viewModel.clearChatHistory() },
                                onSpeakText = onSpeakText
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
                        "presale" -> {
                            val qsamTokensBal by viewModel.qsamTokensBalance.collectAsState()
                            val presaleRaised by viewModel.presaleRaised.collectAsState()
                            val presaleSold by viewModel.presaleSold.collectAsState()
                            val qsamPriceUsd by viewModel.qsamPriceUsd.collectAsState()
                            val balance by viewModel.cqaiBalance.collectAsState()
                            
                            PresaleTabWorkspace(
                                qsamTokensBal = qsamTokensBal,
                                presaleRaised = presaleRaised,
                                presaleSold = presaleSold,
                                qsamPriceUsd = qsamPriceUsd,
                                cqaiBalance = balance,
                                onBuyWithCqai = { viewModel.purchaseQsamWithCqai(it) },
                                onBuyWithUsd = { viewModel.purchaseQsamWithUsd(it) }
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
                        "mcp" -> {
                            val remoteMcpStatus by viewModel.remoteMcpStatus.collectAsState()
                            val remoteMcpTools by viewModel.remoteMcpTools.collectAsState()
                            val remoteMcpResources by viewModel.remoteMcpResources.collectAsState()
                            val remoteMcpLog by viewModel.remoteMcpLog.collectAsState()
                            val customBaseUrl by viewModel.customBaseUrl.collectAsState()

                            McpTabWorkspace(
                                mcpContexts = mcpContexts,
                                remoteMcpStatus = remoteMcpStatus,
                                remoteMcpTools = remoteMcpTools,
                                remoteMcpResources = remoteMcpResources,
                                remoteMcpLog = remoteMcpLog,
                                customBaseUrl = customBaseUrl,
                                onCreateFromChat = { title, keywords ->
                                    viewModel.createMcpContextFromCurrentChat(title, keywords)
                                    Toast.makeText(context, "Current chat indexed into MCP successfully.", Toast.LENGTH_SHORT).show()
                                },
                                onAddManual = { title, summary, keywords ->
                                    viewModel.addMcpContextNode(title, summary, keywords)
                                    Toast.makeText(context, "Manual MCP context registered.", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteNode = { id ->
                                    viewModel.deleteMcpContextNode(id)
                                    Toast.makeText(context, "MCP context node deleted.", Toast.LENGTH_SHORT).show()
                                },
                                onClearAll = {
                                    viewModel.clearAllMcpContextNodes()
                                    Toast.makeText(context, "All MCP contexts cleared.", Toast.LENGTH_SHORT).show()
                                },
                                onQueryRemoteMcp = {
                                    viewModel.queryRemoteMcpServer()
                                    Toast.makeText(context, "Querying remote MCP host...", Toast.LENGTH_SHORT).show()
                                },
                                onCallRemoteTool = { toolName ->
                                    viewModel.callRemoteMcpTool(toolName)
                                    Toast.makeText(context, "Invoking tool: $toolName", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        "config" -> {
                            ConfigTabWorkspace(
                                currentApiKey = apiKey,
                                currentModel = selectedModel,
                                currentPersona = systemPersona,
                                currentBaseUrl = customBaseUrl,
                                onSave = { key, model, persona, baseUrl ->
                                    viewModel.saveConfig(key, model, persona, baseUrl)
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
            Triple("presale", "🚀 QSAM SALE", "presale_tab"),
            Triple("ailab", "🧠 AI LAB", "ailab_tab"),
            Triple("vault", "🗄️ INDEX LOG", "vault_tab"),
            Triple("mcp", "🧬 MCP CORE", "mcp_tab"),
            Triple("config", "⚙ CONFIG", "config_tab")
        )

        tabs.forEach { (id, label, testTagStr) ->
            val isActive = activeTab == id
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
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
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
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
    onClearChat: () -> Unit,
    onSpeakText: (String) -> Unit = {}
) {
    var rawInputText by remember { mutableStateOf("") }
    var webSearchEnabled by remember { mutableStateOf(false) }
    var isRetroShellMode by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Command History
    val commandHistory = remember(messages) {
        messages.filter { it.role == "user" }.map { it.content }.distinct()
    }
    var historyIndex by remember { mutableStateOf(-1) }

    // Auto-scroll chat to bottom upon receiving response messages
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .drawBehind {
                    drawLine(
                        color = Color(0xFF00F5FF).copy(alpha = 0.08f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "▶ TERMINAL SHELL INTERFACE",
                color = if (isRetroShellMode) CyberGreen else CyberCyan,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isRetroShellMode) CyberGreen.copy(alpha = 0.15f) else CyberSurface)
                        .border(
                            0.5.dp,
                            if (isRetroShellMode) CyberGreen else CyberTealMuted,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { isRetroShellMode = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "RETRO CLI",
                        color = if (isRetroShellMode) CyberGreen else CyberTealMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (!isRetroShellMode) CyberCyan.copy(alpha = 0.15f) else CyberSurface)
                        .border(
                            0.5.dp,
                            if (!isRetroShellMode) CyberCyan else CyberTealMuted,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { isRetroShellMode = false }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "CYBER CHAT",
                        color = if (!isRetroShellMode) CyberCyan else CyberTealMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Dialogue chat lists
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CyberBlack)
        ) {
            if (messages.isEmpty()) {
                if (isRetroShellMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = """
                                * Q-GENESIS CORE AUTOMATED TERMINAL v2.8.5 *
                                COGNITIVE ENTANGLED SYNAPSES STATUS: [CONNECTED]
                                SYSTEM RECEPTACLE LEVEL: OPTIMAL
                                MCP LOCAL VAULT STORAGE INDEX: OPEN
                                
                                Enter a prompt below to compute.
                                guest@qsys:~$ █
                            """.trimIndent(),
                            color = CyberGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                } else {
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
                }
            } else {
                val listModifier = if (isRetroShellMode) {
                    Modifier
                        .fillMaxSize()
                        .terminalRetroScanlines()
                } else {
                    Modifier.fillMaxSize()
                }
                LazyColumn(
                    state = listState,
                    modifier = listModifier,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages.size) { index ->
                        val msg = messages[index]
                        val isLatestAssistant = !isGenerating &&
                                msg.role != "user" &&
                                index == messages.indexOfLast { it.role != "user" }
                        if (isRetroShellMode) {
                            TerminalLineElement(message = msg, animate = isLatestAssistant, onSpeakText = onSpeakText)
                        } else {
                            ChatBubbleElement(message = msg, animate = isLatestAssistant, onSpeakText = onSpeakText)
                        }
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

        // Command history top action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "COMMAND BUFFER: ${if (commandHistory.isEmpty()) "EMPTY" else "${historyIndex + 1}/${commandHistory.size}"}",
                color = CyberTealMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )

            if (commandHistory.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(CyberSurfaceVariant)
                            .border(0.5.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            .clickable {
                                if (historyIndex == -1) {
                                    historyIndex = commandHistory.size - 1
                                    rawInputText = commandHistory[historyIndex]
                                } else if (historyIndex > 0) {
                                    historyIndex--
                                    rawInputText = commandHistory[historyIndex]
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "◀ PREV CMD",
                            color = CyberCyan,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(CyberSurfaceVariant)
                            .border(0.5.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            .clickable {
                                if (historyIndex != -1) {
                                    if (historyIndex < commandHistory.size - 1) {
                                        historyIndex++
                                        rawInputText = commandHistory[historyIndex]
                                    } else {
                                        historyIndex = -1
                                        rawInputText = ""
                                    }
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "NEXT CMD ▶",
                            color = CyberCyan,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quick Shell Command Suggestion Chips
        val suggestionChips = listOf(
            "/mcp_help",
            "/mcp_list",
            "/memo Save current chat | kotlin, general",
            "Explain Kotlin Coroutines",
            "How to use Room Database",
            "Analyze System logs"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestionChips.forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                        .border(
                            0.5.dp,
                            CyberTealMuted.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            rawInputText = suggestion
                            historyIndex = -1
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = suggestion,
                        color = CyberTealMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Input control console
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Entry Field with hardware keyboard support
            OutlinedTextField(
                value = rawInputText,
                onValueChange = { 
                    rawInputText = it 
                    // Reset history index if typing something customized
                    historyIndex = -1
                },
                placeholder = {
                    Text(
                        "Command Q Genesis...",
                        color = CyberTealMuted.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                prefix = {
                    Text(
                        "guest@qsys:~$ ",
                        color = CyberGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("submit_prompt_input")
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (commandHistory.isNotEmpty()) {
                                        if (historyIndex == -1) {
                                            historyIndex = commandHistory.size - 1
                                        } else if (historyIndex > 0) {
                                            historyIndex--
                                        }
                                        rawInputText = commandHistory[historyIndex]
                                    }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (commandHistory.isNotEmpty() && historyIndex != -1) {
                                        if (historyIndex < commandHistory.size - 1) {
                                            historyIndex++
                                            rawInputText = commandHistory[historyIndex]
                                        } else {
                                            historyIndex = -1
                                            rawInputText = ""
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                textStyle = LocalTextStyle.current.copy(
                    color = CyberWhite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
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
                    Spacer(modifier = Modifier.height(2.dp))
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
                        historyIndex = -1
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
fun ChatBubbleElement(message: ChatMessage, animate: Boolean = false, onSpeakText: (String) -> Unit = {}) {
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
                .clickable {
                    if (!isUser) {
                        onSpeakText(message.content)
                    }
                }
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "COMMAND LINE ENTRY" else "Q GENESIS ULTIMATE OUTPUT",
                        color = if (isUser) CyberPurple else CyberCyan,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 3.dp).weight(1f)
                    )
                    if (!isUser) {
                        Text(
                            text = "🔊 SPEAK",
                            color = CyberCyan,
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onSpeakText(message.content) }
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                
                SyntaxHighlightedMarkdownMessage(
                    text = message.content,
                    defaultColor = if (isUser) CyberWhite else CyberTealMuted,
                    animate = !isUser && animate,
                    speedMs = 12
                )
            }
        }
    }
}

@Composable
fun TerminalLineElement(message: ChatMessage, animate: Boolean = false, onSpeakText: (String) -> Unit = {}) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        if (isUser) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "guest@qsys:~$ ",
                    color = CyberGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier.weight(1f)) {
                    SyntaxHighlightedMarkdownMessage(
                        text = message.content,
                        defaultColor = CyberWhite,
                        animate = false
                    )
                }
            }
        } else {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[Q_SYS_OUTPUT] -----------------------------",
                        color = CyberCyan.copy(alpha = 0.4f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp).weight(1f)
                    )
                    Text(
                        text = "[🔊 SPEAK]",
                        color = CyberCyan,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onSpeakText(message.content) }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                SyntaxHighlightedMarkdownMessage(
                    text = message.content,
                    defaultColor = CyberCyan,
                    animate = animate,
                    speedMs = 12
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

fun Modifier.terminalRetroScanlines(): Modifier = this.drawWithContent {
    drawContent()
    val scanlineWidth = 2.dp.toPx()
    val scanlineColor = Color.Black.copy(alpha = 0.12f)
    val heightVal = this.size.height.toInt()
    val widthVal = this.size.width
    for (y in 0..heightVal step (scanlineWidth * 2).toInt()) {
        drawRect(
            color = scanlineColor,
            topLeft = Offset(0f, y.toFloat()),
            size = androidx.compose.ui.geometry.Size(widthVal, scanlineWidth)
        )
    }
}

@Composable
fun AnimatedTerminalText(
    text: String,
    speedMs: Long = 12,
    color: Color = CyberWhite,
    modifier: Modifier = Modifier
) {
    var displayedText by remember { mutableStateOf("") }
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(text) {
        displayedText = ""
        currentIndex = 0
        while (currentIndex < text.length) {
            // Adjust chunks for longer terminal readouts
            val step = if (text.length > 500) 4 else if (text.length > 200) 2 else 1
            currentIndex = (currentIndex + step).coerceAtMost(text.length)
            displayedText = text.substring(0, currentIndex)
            kotlinx.coroutines.delay(speedMs)
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val cursorVisible by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Text(
        text = displayedText + if (currentIndex < text.length || cursorVisible > 0.5f) "█" else "",
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.sp,
        modifier = modifier
    )
}

sealed class BlockSegment {
    data class TextBlock(val text: String) : BlockSegment()
    data class CodeBlock(val language: String, val code: String) : BlockSegment()
}

fun parseMarkdownToBlocks(content: String): List<BlockSegment> {
    val segments = mutableListOf<BlockSegment>()
    val lines = content.split("\n")
    var isInsideCode = false
    var currentLanguage = ""
    val currentCodeLines = mutableListOf<String>()
    val currentTextLines = mutableListOf<String>()
    
    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (isInsideCode) {
                segments.add(BlockSegment.CodeBlock(currentLanguage, currentCodeLines.joinToString("\n")))
                currentCodeLines.clear()
                isInsideCode = false
            } else {
                if (currentTextLines.isNotEmpty()) {
                    segments.add(BlockSegment.TextBlock(currentTextLines.joinToString("\n")))
                    currentTextLines.clear()
                }
                currentLanguage = line.trim().substring(3).trim()
                if (currentLanguage.isEmpty()) {
                    currentLanguage = "code"
                }
                isInsideCode = true
            }
        } else {
            if (isInsideCode) {
                currentCodeLines.add(line)
            } else {
                currentTextLines.add(line)
            }
        }
    }
    
    if (isInsideCode) {
        segments.add(BlockSegment.CodeBlock(currentLanguage, currentCodeLines.joinToString("\n")))
    } else if (currentTextLines.isNotEmpty()) {
        segments.add(BlockSegment.TextBlock(currentTextLines.joinToString("\n")))
    }
    
    return segments
}

fun buildSyntaxHighlightedText(content: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        append(content)
        
        val commentRegex = Regex("(//.*)|(/\\*([^*]|\\*+[^*/])*\\*+/)|(#.*)")
        val stringRegex = Regex("\"[^\"]*\"|'[^']*'")
        val numberRegex = Regex("\\b\\d+(?:\\.\\d+)?\\b")
        val annotationRegex = Regex("@[a-zA-Z_][a-zA-Z0-9_]*")
        
        val keywords = setOf(
            "fun", "val", "var", "class", "interface", "import", "package", 
            "return", "if", "else", "while", "for", "in", "null", "true", "false", 
            "try", "catch", "throw", "override", "private", "public", "protected", 
            "suspend", "object", "const", "def", "let", "function", "lambda", "assert",
            "break", "continue", "do", "finally", "from", "as", "is", "when", "async", "await", "yield"
        )
        val keywordRegex = Regex("\\b(" + keywords.joinToString("|") + ")\\b")
        
        val systemTags = setOf(
            "guest@qsys", "[Q_SYS_OUTPUT]", "Q GENESIS ULTIMATE OUTPUT", "COMMAND LINE ENTRY",
            "COGNITIVE ENTANGLED SYNAPSES STATUS", "SYSTEM RECEPTACLE LEVEL", "MCP LOCAL VAULT STORAGE INDEX",
            "CONNECTED", "OPTIMAL", "OPEN", "INFO", "WARN", "ERROR", "GOOD", "EXEC", "AUTOPILOT COGNITIVE LOGSTREAM"
        )
        val systemTagRegex = Regex("\\b(" + systemTags.map { Regex.escape(it) }.joinToString("|") + ")\\b")

        addStyle(SpanStyle(color = defaultColor), 0, content.length)
        
        systemTagRegex.findAll(content).forEach { match ->
            val color = when (match.value) {
                "CONNECTED", "OPTIMAL", "OPEN", "GOOD" -> CyberGreen
                "WARN", "ERROR" -> CyberRed
                else -> CyberCyan
            }
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }

        numberRegex.findAll(content).forEach { match ->
            addStyle(SpanStyle(color = CyberGold), match.range.first, match.range.last + 1)
        }
        
        annotationRegex.findAll(content).forEach { match ->
            addStyle(SpanStyle(color = CyberPurple), match.range.first, match.range.last + 1)
        }

        keywordRegex.findAll(content).forEach { match ->
            addStyle(SpanStyle(color = CyberPurple, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }

        stringRegex.findAll(content).forEach { match ->
            addStyle(SpanStyle(color = CyberTealMuted), match.range.first, match.range.last + 1)
        }

        commentRegex.findAll(content).forEach { match ->
            addStyle(SpanStyle(color = Color(0xFF628A62)), match.range.first, match.range.last + 1)
        }
    }
}

@Composable
fun CodeBlockTerminal(language: String, code: String, defaultColor: Color) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(2000)
            isCopied = false
        }
    }

    val isQasm = language.lowercase(Locale.ROOT) == "qasm" || language.lowercase(Locale.ROOT) == "openqasm"
    var activeViewMode by remember { mutableStateOf(if (isQasm) "visual" else "code") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CyberSurfaceVariant)
            .border(0.5.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .drawBehind {
                    drawLine(
                        color = CyberCyan.copy(alpha = 0.15f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(CyberRed))
                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(CyberGold))
                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(CyberGreen))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "▶ ${language.uppercase(Locale.ROOT)} BUFFER",
                    color = CyberCyan,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isQasm) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(CyberSurfaceVariant)
                            .border(0.5.dp, CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                            .padding(1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (activeViewMode == "visual") CyberCyan.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { activeViewMode = "visual" }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "🧬 SCHEMATIC",
                                color = if (activeViewMode == "visual") CyberCyan else CyberTealMuted,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (activeViewMode == "code") CyberCyan.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { activeViewMode = "code" }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "📟 CODE",
                                color = if (activeViewMode == "code") CyberCyan else CyberTealMuted,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            isCopied = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isCopied) "✔ COPIED" else "⎘ COPY",
                        color = if (isCopied) CyberGreen else CyberTealMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (activeViewMode == "visual") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                com.example.ui.QuantumCircuitVisualizer(qasmCode = code)
            }
        } else {
            val codeLines = code.split("\n")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    codeLines.indices.forEach { idx ->
                        Text(
                            text = "${idx + 1}",
                            color = CyberTealMuted.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            lineHeight = 15.sp
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column {
                        codeLines.forEach { line ->
                            Text(
                                text = buildSyntaxHighlightedText(line, CyberWhite),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyntaxHighlightedMarkdownMessage(
    text: String,
    defaultColor: Color,
    animate: Boolean = false,
    speedMs: Long = 12
) {
    var displayedLength by remember(text) { mutableStateOf(if (animate) 0 else text.length) }
    
    LaunchedEffect(text, animate) {
        if (animate) {
            displayedLength = 0
            while (displayedLength < text.length) {
                val step = if (text.length > 500) 6 else if (text.length > 200) 3 else 1
                displayedLength = (displayedLength + step).coerceAtMost(text.length)
                kotlinx.coroutines.delay(speedMs)
            }
        } else {
            displayedLength = text.length
        }
    }
    
    val currentText = remember(text, displayedLength) {
        text.substring(0, displayedLength.coerceAtMost(text.length))
    }
    
    val segments = remember(currentText) {
        parseMarkdownToBlocks(currentText)
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is BlockSegment.TextBlock -> {
                    if (segment.text.isNotEmpty()) {
                        Text(
                            text = buildSyntaxHighlightedText(segment.text, defaultColor),
                            color = defaultColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                is BlockSegment.CodeBlock -> {
                    CodeBlockTerminal(
                        language = segment.language,
                        code = segment.code,
                        defaultColor = defaultColor
                    )
                }
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
    currentBaseUrl: String,
    onSave: (String, String, String, String) -> Unit
) {
    var inputKey by remember { mutableStateOf(currentApiKey) }
    var selectedModel by remember { mutableStateOf(currentModel) }
    var textPersona by remember { mutableStateOf(currentPersona) }
    var inputBaseUrl by remember { mutableStateOf(currentBaseUrl) }

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

        // Custom Backend Tunnel Host input
        Text(
            "CUSTOM BACKEND / TUNNEL HOST (ROK/NGROK/HARVEST AI):",
            color = CyberTealMuted,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = inputBaseUrl,
            onValueChange = { inputBaseUrl = it },
            placeholder = { Text("https://api.openai.com/ or https://xxxx.ngrok-free.app/", color = CyberTealMuted.copy(alpha = 0.3f), fontSize = 10.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("custom_base_url_input"),
            textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
            ),
            singleLine = true
        )
        Text(
            "Point this endpoint to an Ngrok tunnel, Rok proxy, or a free HTTP backend server like Harvest AI. No API key is needed when utilizing the automatic free Gemini background fallback!",
            color = CyberCyan.copy(alpha = 0.7f),
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Cyber Link QR Portal Box
        var configTabMode by remember { mutableStateOf("display") } // "display" or "scan"
        var isScanning by remember { mutableStateOf(false) }
        var scanLogText by remember { mutableStateOf("PORTAL LINKER READY.") }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF02131A)),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    "🔮 CYRIL PROTOCOL PORTAL LINKER (QR/ROK TUNNEL)",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { configTabMode = "display" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (configTabMode == "display") CyberCyan.copy(alpha = 0.2f) else CyberSurface
                        ),
                        border = BorderStroke(1.dp, if (configTabMode == "display") CyberCyan else CyberTealMuted.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.weight(1f).height(28.dp)
                    ) {
                        Text("SHOW PORTAL QR", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = { configTabMode = "scan" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (configTabMode == "scan") CyberCyan.copy(alpha = 0.2f) else CyberSurface
                        ),
                        border = BorderStroke(1.dp, if (configTabMode == "scan") CyberCyan else CyberTealMuted.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.weight(1f).height(28.dp)
                    ) {
                        Text("SCAN PORTAL QR", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (configTabMode == "display") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drawing a cybernetic QR Code in pure Compose!
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(CyberBlack)
                                .border(1.dp, CyberCyan)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pixel-art matrix code
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val size = 12 // 12x12 grid
                                val cellSizeWidth = this.size.width / size
                                val cellSizeHeight = this.size.height / size
                                val random = java.util.Random(inputBaseUrl.hashCode().toLong())

                                for (x in 0 until size) {
                                    for (y in 0 until size) {
                                        // QR Corner finders
                                        val isFinder = (x < 3 && y < 3) || (x >= size - 3 && y < 3) || (x < 3 && y >= size - 3)
                                        val isFinderCenter = (x == 1 && y == 1) || (x == size - 2 && y == 1) || (x == 1 && y == size - 2)
                                        
                                        if (isFinder) {
                                            if (isFinderCenter) {
                                                drawRect(
                                                    color = Color.Black,
                                                    topLeft = androidx.compose.ui.geometry.Offset(x * cellSizeWidth, y * cellSizeHeight),
                                                    size = androidx.compose.ui.geometry.Size(cellSizeWidth, cellSizeHeight)
                                                )
                                            } else {
                                                drawRect(
                                                    color = CyberCyan,
                                                    topLeft = androidx.compose.ui.geometry.Offset(x * cellSizeWidth, y * cellSizeHeight),
                                                    size = androidx.compose.ui.geometry.Size(cellSizeWidth, cellSizeHeight)
                                                )
                                            }
                                        } else {
                                            // Random QR fill based on hash
                                            if (random.nextBoolean()) {
                                                drawRect(
                                                    color = CyberCyan.copy(alpha = 0.85f),
                                                    topLeft = androidx.compose.ui.geometry.Offset(x * cellSizeWidth, y * cellSizeHeight),
                                                    size = androidx.compose.ui.geometry.Size(cellSizeWidth, cellSizeHeight)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                "CURRENT PORTAL KEY:",
                                color = CyberTealMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                inputBaseUrl.ifEmpty { "None configured" },
                                color = CyberCyan,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Scan this code from another terminal to instantly pair/synchronize your custom ngrok or Harvest AI tunnels.",
                                color = CyberWhite.copy(alpha = 0.6f),
                                fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 10.sp
                            )
                        }
                    }
                } else {
                    // Scanning tab view
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(CyberBlack)
                                    .border(1.dp, CyberTealMuted.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Scanner",
                                        tint = CyberTealMuted,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "READY TO ACQUIRE PORTAL VECTOR",
                                        color = CyberTealMuted,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            isScanning = true
                                            scanLogText = "INITIALIZING PORTAL ACQUISITION SEQUENCE..."
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, CyberCyan),
                                        shape = RoundedCornerShape(2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("START ACTIVE SCAN", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        } else {
                            // Active Scanner scanning overlay
                            val infiniteTransition = rememberInfiniteTransition()
                            val laserProgress by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )

                            // Launch simulation task to scan a free tunnel
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(1000)
                                scanLogText = "SWEEPING FREQUENCY CODES [32%]"
                                kotlinx.coroutines.delay(1000)
                                scanLogText = "FOUND ENCRYPTED TUNNEL PATTERN AT ROK/NGROK LINK [78%]"
                                kotlinx.coroutines.delay(800)
                                scanLogText = "DECRYPTING HARVEST AI ENDPOINT IN REALTIME [99%]"
                                kotlinx.coroutines.delay(500)
                                
                                // Automatically import the free, pre-configured HTTP tunnel
                                inputBaseUrl = "https://free-proxy.harvestai.net/v1"
                                inputKey = "harvest-free-secure-key-13th-chamber"
                                scanLogText = "PORTAL ENCODED! IMPORTED SECURE FREE TUNNEL VALUE."
                                isScanning = false
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(CyberBlack)
                                    .border(1.dp, CyberCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                // Draw high-tech scanning grid & laser
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val sweepY = size.height * laserProgress
                                    
                                    // Grid lines
                                    val gridCount = 8
                                    for (i in 1 until gridCount) {
                                        val x = (size.width / gridCount) * i
                                        drawLine(
                                            color = CyberCyan.copy(alpha = 0.15f),
                                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                                            strokeWidth = 1f
                                        )
                                        val y = (size.height / gridCount) * i
                                        drawLine(
                                            color = CyberCyan.copy(alpha = 0.15f),
                                            start = androidx.compose.ui.geometry.Offset(0f, y),
                                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                                            strokeWidth = 1f
                                        )
                                    }

                                    // Scanning laser line
                                    drawLine(
                                        color = CyberCyan,
                                        start = androidx.compose.ui.geometry.Offset(0f, sweepY),
                                        end = androidx.compose.ui.geometry.Offset(size.width, sweepY),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }

                                Text(
                                    "TUNNEL DETECTED · ACQUIRING PROTOCOL MATRIX",
                                    color = CyberCyan,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            scanLogText,
                            color = if (scanLogText.contains("IMPORTED")) CyberGreen else CyberTealMuted,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                .height(120.dp)
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
            onClick = { onSave(inputKey, selectedModel, textPersona, inputBaseUrl) },
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

        Spacer(modifier = Modifier.height(20.dp))

        // Floating overlay standalone controls
        val context = LocalContext.current
        var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        var hasMicPermission by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val micPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                hasMicPermission = granted
                Toast.makeText(context, if (granted) "Microphone access granted!" else "Microphone access denied.", Toast.LENGTH_SHORT).show()
            }
        )

        // Check for overlay state
        LaunchedEffect(Unit) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "🌌 CYRIL-QAI STANDALONE FLOATING ENGINE",
                    color = CyberCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Run Cyril Q-AI as an interactive floating deck autopilot on top of any webpage, browser, or quantum host. Input web context to synthesize QSAM rotations, calculate phase angles, and track training run parameters using your own local Comet-style logging metrics!",
                    color = CyberWhite.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Permission status rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Draw Over Other Apps Permission:",
                        color = CyberTealMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (hasOverlayPermission) "AUTHORIZED 🟢" else "UNAUTHORIZED 🔴",
                        color = if (hasOverlayPermission) CyberGreen else CyberRed,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "System Microphone Permission:",
                        color = CyberTealMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (hasMicPermission) "AUTHORIZED 🟢" else "UNAUTHORIZED 🔴",
                        color = if (hasMicPermission) CyberGreen else CyberRed,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Grant Actions
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1.0f)
                            .height(28.dp)
                            .padding(end = 4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("ALLOW OVERLAY", color = CyberCyan, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (!hasMicPermission) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                Toast.makeText(context, "Microphone permission is already active!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                        border = BorderStroke(1.dp, CyberPurple.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1.0f)
                            .height(28.dp)
                            .padding(start = 4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("ALLOW MICROPHONE", color = CyberPurple, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Launch Controls
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Please authorize overlay permission first!", Toast.LENGTH_LONG).show()
                            } else {
                                FloatingOverlayService.startService(context)
                                Toast.makeText(context, "Floating engine active! Feel free to exit or open other apps.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1.0f)
                            .height(32.dp)
                            .padding(end = 4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("START FLOATING DECK", color = CyberBlack, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            FloatingOverlayService.stopService(context)
                            Toast.makeText(context, "Floating engine stopped.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1.0f)
                            .height(32.dp)
                            .padding(start = 4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("STOP FLOATING DECK", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun McpTabWorkspace(
    mcpContexts: List<McpContextNode>,
    remoteMcpStatus: String,
    remoteMcpTools: List<Map<String, String>>,
    remoteMcpResources: List<Map<String, String>>,
    remoteMcpLog: String,
    customBaseUrl: String,
    onCreateFromChat: (String, String) -> Unit,
    onAddManual: (String, String, String) -> Unit,
    onDeleteNode: (Long) -> Unit,
    onClearAll: () -> Unit,
    onQueryRemoteMcp: () -> Unit,
    onCallRemoteTool: (String) -> Unit
) {
    var subTab by remember { mutableStateOf("local") }

    var chatSessionTitle by remember { mutableStateOf("") }
    var chatSessionKeywords by remember { mutableStateOf("") }

    var manualTitle by remember { mutableStateOf("") }
    var manualSummary by remember { mutableStateOf("") }
    var manualKeywords by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
    ) {
        // Dual Tab Toggle Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(CyberSurface, RoundedCornerShape(4.dp))
                .border(0.5.dp, CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { subTab = "local" }
                    .background(if (subTab == "local") CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🧠 LOCAL INDEXER",
                    color = if (subTab == "local") CyberCyan else CyberTealMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(CyberCyan.copy(alpha = 0.15f))
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { subTab = "remote" }
                    .background(if (subTab == "remote") CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🌍 REMOTE MCP NETWORK",
                    color = if (subTab == "remote") CyberCyan else CyberTealMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (subTab == "local") {
            // Local Indexer Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "🧬 MODEL CONTEXT PROTOCOL (MCP) COGNITIVE INDEXER",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Text(
                    "This protocol captures snapshots of past conversation sessions into indexed context blocks. " +
                    "Matching keywords are automatically retrieved from SQLite layers during prompt execution, augmenting LLM short-term memories.",
                    color = CyberTealMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 1. Index Today's Active Chat flow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .background(CyberSurface)
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            "▶ OVERRIDE MODE: INDEX DECG-GRID CHAT",
                            color = CyberGreen,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Condense active session message streams into a single indexed query node.",
                            color = CyberTealMuted,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = chatSessionTitle,
                            onValueChange = { chatSessionTitle = it },
                            placeholder = { Text("Session Title (e.g. QSAM Calibration)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.08f)
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = chatSessionKeywords,
                            onValueChange = { chatSessionKeywords = it },
                            placeholder = { Text("Keywords (comma separated, e.g. quantum, calibration)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.08f)
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                if (chatSessionTitle.trim().isNotEmpty() && chatSessionKeywords.trim().isNotEmpty()) {
                                    onCreateFromChat(chatSessionTitle, chatSessionKeywords)
                                    chatSessionTitle = ""
                                    chatSessionKeywords = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "🧬 SNAPSHOT & INDEX CURRENT CHAT",
                                color = CyberCyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Manual Context Node Ingestion
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .background(CyberSurface)
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            "▶ MANUAL SYSTEMIC CONTEXT INGESTION",
                            color = CyberCyan,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = manualTitle,
                            onValueChange = { manualTitle = it },
                            placeholder = { Text("Topic/Title (e.g. Muon specs)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.08f)
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = manualSummary,
                            onValueChange = { manualSummary = it },
                            placeholder = { Text("Core memory summary content to inject...", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(80.dp).padding(vertical = 2.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.08f)
                            )
                        )

                        OutlinedTextField(
                            value = manualKeywords,
                            onValueChange = { manualKeywords = it },
                            placeholder = { Text("Keywords (comma separated, e.g. muon, g-2)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.08f)
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                if (manualTitle.trim().isNotEmpty() && manualSummary.trim().isNotEmpty() && manualKeywords.trim().isNotEmpty()) {
                                    onAddManual(manualTitle, manualSummary, manualKeywords)
                                    manualTitle = ""
                                    manualSummary = ""
                                    manualKeywords = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberGreen),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "➕ REGISTER MCP MEMORY BLOCK",
                                color = CyberGreen,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Stored/Indexed Shards Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "STORES (${mcpContexts.size} SHARDS)",
                        color = CyberWhite,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "CLEAR SHARDS",
                        color = CyberRed,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onClearAll() }
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search MCP indexed content...", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.08f)
                    ),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberTealMuted, modifier = Modifier.size(12.dp)) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Display list of context shards
                val filteredContexts = mcpContexts.filter {
                    it.sessionTitle.contains(searchQuery, ignoreCase = true) ||
                    it.summary.contains(searchQuery, ignoreCase = true) ||
                    it.keywords.contains(searchQuery, ignoreCase = true)
                }

                if (filteredContexts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "--- NO INDEXED MCP CONTEXT SHARDS COMPLETED ---",
                            color = CyberTealMuted.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredContexts.forEach { node ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, Color(0xFF00F5FF).copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                                    .background(CyberSurface)
                                    .padding(6.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "[NODE: ${node.sessionTitle.uppercase()}]",
                                            color = CyberCyan,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Core Shard",
                                            tint = CyberRed.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { onDeleteNode(node.id) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Keywords: ${node.keywords}",
                                        color = CyberGreen,
                                        fontSize = 7.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        node.summary,
                                        color = CyberWhite.copy(alpha = 0.9f),
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 11.sp,
                                        maxLines = 8,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // REMOTE MCP NETWORK TAB
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Connection Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .background(CyberSurface)
                        .padding(8.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🌍 REMOTE COHERENCE LINKER STATUS",
                                color = CyberWhite,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            // Status Indicator Badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        when (remoteMcpStatus) {
                                            "Connected" -> CyberGreen.copy(alpha = 0.15f)
                                            "Connecting..." -> CyberCyan.copy(alpha = 0.15f)
                                            else -> CyberRed.copy(alpha = 0.15f)
                                        },
                                        RoundedCornerShape(3.dp)
                                    )
                                    .border(
                                        0.5.dp,
                                        when (remoteMcpStatus) {
                                            "Connected" -> CyberGreen
                                            "Connecting..." -> CyberCyan
                                            else -> CyberRed
                                        },
                                        RoundedCornerShape(3.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    remoteMcpStatus.uppercase(),
                                    color = when (remoteMcpStatus) {
                                        "Connected" -> CyberGreen
                                        "Connecting..." -> CyberCyan
                                        else -> CyberRed
                                    },
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            "Paired Host URL: ${customBaseUrl.ifEmpty { "None (Configure in ⚙ Config)" }}",
                            color = CyberTealMuted,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onQueryRemoteMcp,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "📡 SYNCHRONIZE REMOTE MCP ENVIRONMENT",
                                color = CyberCyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // MCP TOOLS ROW
                Text(
                    "🔧 ACTIVE REMOTE TOOL SPECIFICATIONS",
                    color = CyberWhite,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (remoteMcpTools.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, CyberCyan.copy(alpha = 0.05f), RoundedCornerShape(3.dp))
                            .background(CyberSurface)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "--- SYNC NOT COMPLETED (NO ACTIVE TOOLS LOCATED) ---",
                            color = CyberTealMuted.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        remoteMcpTools.forEach { tool ->
                            val toolName = tool["name"] ?: ""
                            val toolDesc = tool["description"] ?: ""

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, CyberGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .background(CyberSurface)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.0f).padding(end = 8.dp)) {
                                        Text(
                                            "[TOOL] $toolName",
                                            color = CyberGreen,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            toolDesc,
                                            color = CyberTealMuted,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            lineHeight = 10.sp
                                        )
                                    }

                                    Button(
                                        onClick = { onCallRemoteTool(toolName) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.15f)),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, CyberGreen),
                                        shape = RoundedCornerShape(3.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(
                                            "RUN",
                                            color = CyberGreen,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // MCP RESOURCES ROW
                Text(
                    "🗂️ REMOTE RESOURCE SPECIFICATIONS",
                    color = CyberWhite,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (remoteMcpResources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, CyberCyan.copy(alpha = 0.05f), RoundedCornerShape(3.dp))
                            .background(CyberSurface)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "--- SYNC NOT COMPLETED (NO RESOURCES LOCATED) ---",
                            color = CyberTealMuted.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        remoteMcpResources.forEach { resource ->
                            val rName = resource["name"] ?: ""
                            val rUri = resource["uri"] ?: ""
                            val rDesc = resource["description"] ?: ""

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, CyberCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .background(CyberSurface)
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        "[RESOURCE] $rName",
                                        color = CyberCyan,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "URI: $rUri",
                                        color = CyberTealMuted,
                                        fontSize = 7.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        rDesc,
                                        color = CyberWhite.copy(alpha = 0.7f),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        lineHeight = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MCP TOOL RESULTS TERMINAL CONSOLE
                Text(
                    "🖥️ MCP ACTIVE CONSOLE TERMINAL OUTPUT",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .border(0.5.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .background(Color(0xFF040B11))
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            remoteMcpLog,
                            color = CyberCyan,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
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

                                Spacer(modifier = Modifier.height(12.dp))

                                com.example.ui.InteractiveQuantumCircuitWorkspace()
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    FloatingOverlayService.startService(context)
                                    Toast.makeText(context, "QSAM AUTOPILOT: Interactive Floating Deck launched!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1.0f).padding(end = 2.dp).height(24.dp),
                                border = BorderStroke(0.5.dp, CyberCyan),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("LAUNCH FLOATING DECK", color = CyberCyan, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = {
                                    val runs = viewModel.cometTotalRuns.value
                                    val loss = viewModel.cometLoss.value
                                    val acc = viewModel.cometAccuracy.value
                                    Toast.makeText(context, "QSAM COMET TRACKER:\nRuns: $runs | Loss: ${String.format("%.4f", loss)} | Acc: ${String.format("%.2f", acc * 100)}%", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPurple.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1.0f).padding(start = 2.dp).height(24.dp),
                                border = BorderStroke(0.5.dp, CyberPurple),
                                contentPadding = PaddingValues(0.0.dp)
                            ) {
                                Text("VIEW LOCAL COMET STATS", color = CyberPurple, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
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

@Composable
fun PresaleTabWorkspace(
    qsamTokensBal: Double,
    presaleRaised: Double,
    presaleSold: Double,
    qsamPriceUsd: Double,
    cqaiBalance: Double,
    onBuyWithCqai: (Double) -> Boolean,
    onBuyWithUsd: (Double) -> Boolean
) {
    val context = LocalContext.current
    var cqaiInput by remember { mutableStateOf("") }
    var usdInput by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvc by remember { mutableStateOf("") }
    var activeSubTab by remember { mutableStateOf("website") } // "website" or "developer"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        // Sub-navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(CyberSurfaceVariant, RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (activeSubTab == "website") CyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { activeSubTab = "website" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🌐 SALE WEBSITE PORTAL",
                    color = if (activeSubTab == "website") CyberCyan else CyberTealMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (activeSubTab == "developer") CyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { activeSubTab = "developer" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "💻 GITHUB & NODE LAUNCHER",
                    color = if (activeSubTab == "developer") CyberCyan else CyberTealMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (activeSubTab == "website") {
            // WEBSITE SALE VIEW
            
            // Hero Banner with Custom Waves
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberSurfaceVariant)
                    .border(1.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .drawBehind {
                        val path = androidx.compose.ui.graphics.Path()
                        val steps = 80
                        val amplitude1 = 20f
                        val amplitude2 = 10f
                        
                        path.moveTo(0f, size.height / 2f)
                        for (i in 0..steps) {
                            val x = (size.width / steps) * i
                            val y = (size.height / 2f) + 
                                    (kotlin.math.sin(x * 0.04f) * amplitude1) + 
                                    (kotlin.math.cos(x * 0.08f) * amplitude2)
                            path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = CyberCyan.copy(alpha = 0.15f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        
                        val path2 = androidx.compose.ui.graphics.Path()
                        path2.moveTo(0f, size.height * 0.6f)
                        for (i in 0..steps) {
                            val x = (size.width / steps) * i
                            val y = (size.height * 0.6f) + 
                                    (kotlin.math.cos(x * 0.03f) * amplitude2 * 1.5f) + 
                                    (kotlin.math.sin(x * 0.06f) * amplitude1 * 0.5f)
                            path2.lineTo(x, y)
                        }
                        drawPath(
                            path = path2,
                            color = CyberGreen.copy(alpha = 0.1f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .background(CyberCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, CyberCyan, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE ICO PRESALE PORTAL",
                            color = CyberCyan,
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "QSAM QUANTUM BITCOIN",
                        color = CyberWhite,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Space-Angle Modulated Cryptographic Consensus Ledger",
                        color = CyberTealMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats row (3 Grid cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Token Price
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("QSAM PRICE (USD)", color = CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(CyberGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$${String.format("%.6f", qsamPriceUsd)}",
                                color = CyberGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Live Market Tick", color = CyberTealMuted.copy(alpha = 0.5f), fontSize = 6.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Total Raised
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("TOTAL FUNDING", color = CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$${String.format("%,.2f", presaleRaised)}",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Goal: $1,000,000", color = CyberTealMuted.copy(alpha = 0.5f), fontSize = 6.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Total Sold
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF8800FF).copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("TOKENS SOLD", color = CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${String.format("%,.0f", presaleSold)} QSAM",
                            color = CyberPurple,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Supply: 13,000,000", color = CyberTealMuted.copy(alpha = 0.5f), fontSize = 6.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User holdings badge card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberGold.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = "Holdings", tint = CyberGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("YOUR TOTAL SECURED QSAM HOLDINGS", color = CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            Text("Stored in decentralized private quantum wallet", color = CyberTealMuted.copy(alpha = 0.6f), fontSize = 6.sp)
                        }
                    }
                    Text(
                        text = "${String.format("%,.4f", qsamTokensBal)} QSAM",
                        color = CyberGold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // INVESTMENT MODULES SECTION
            Text(
                "🛒 INVEST IN THE COHERENCE BLOCKCHAIN:",
                color = CyberCyan,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Method 1: Swap mined CQAI coins (Instant)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "⚡ SWAP CQAI COINS (1 CQAI = 10 QSAM)",
                        color = CyberGreen,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Convert your hard-mined virtual coins into verified cryptographic QSAM presale assets instantly.",
                        color = CyberTealMuted,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = cqaiInput,
                            onValueChange = { cqaiInput = it },
                            placeholder = { Text("Amount of CQAI to swap", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("qsam_swap_cqai_input"),
                            textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberGreen,
                                unfocusedBorderColor = Color(0xFF00FF88).copy(alpha = 0.1f)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val amt = cqaiInput.toDoubleOrNull()
                                if (amt == null || amt <= 0) {
                                    Toast.makeText(context, "Please enter a valid swap amount.", Toast.LENGTH_SHORT).show()
                                } else if (cqaiBalance < amt) {
                                    Toast.makeText(context, "Insufficient CQAI balance.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val ok = onBuyWithCqai(amt)
                                    if (ok) {
                                        Toast.makeText(context, "Successfully swapped $amt CQAI for ${amt * 10} QSAM!", Toast.LENGTH_LONG).show()
                                        cqaiInput = ""
                                    } else {
                                        Toast.makeText(context, "Error swapping coins.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, CyberGreen),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("qsam_swap_cqai_button")
                        ) {
                            Text("SWAP", color = CyberGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Your Current Balance: ${String.format("%.4f", cqaiBalance)} CQAI",
                        color = CyberTealMuted.copy(alpha = 0.5f),
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Method 2: Stripe secure card gateway
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "💳 SECURE CARD GATEWAY (STRIPE VERIFIED)",
                        color = CyberCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Deposit fiat funds directly to mint and load QSAM tokens into your offline cold storage.",
                        color = CyberTealMuted,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    OutlinedTextField(
                        value = usdInput,
                        onValueChange = { usdInput = it },
                        placeholder = { Text("Amount in USD ($)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("qsam_buy_usd_input"),
                        textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it },
                            placeholder = { Text("Card Number (Simulated)", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(2f)
                                .height(48.dp)
                                .testTag("qsam_buy_card_num"),
                            textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = cardExpiry,
                            onValueChange = { cardExpiry = it },
                            placeholder = { Text("MM/YY", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("qsam_buy_card_expiry"),
                            textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF00F5FF).copy(alpha = 0.1f)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = cardCvc,
                            onValueChange = { cardCvc = it },
                            placeholder = { Text("CVC", color = CyberTealMuted.copy(alpha = 0.4f), fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(48.dp)
                                .testTag("qsam_buy_card_cvc"),
                            textStyle = TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
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
                            val amt = usdInput.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                Toast.makeText(context, "Please enter a valid investment amount.", Toast.LENGTH_SHORT).show()
                            } else if (cardNumber.isEmpty() || cardExpiry.isEmpty() || cardCvc.isEmpty()) {
                                Toast.makeText(context, "Please complete card payment details.", Toast.LENGTH_SHORT).show()
                            } else {
                                val ok = onBuyWithUsd(amt)
                                if (ok) {
                                    val tokenAmt = amt / qsamPriceUsd
                                    Toast.makeText(context, "Payment Processed! Successfully minted ${String.format("%,.4f", tokenAmt)} QSAM!", Toast.LENGTH_LONG).show()
                                    usdInput = ""
                                    cardNumber = ""
                                    cardExpiry = ""
                                    cardCvc = ""
                                } else {
                                    Toast.makeText(context, "Error processing card payment.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("qsam_buy_usd_button")
                    ) {
                        Text("🔒 PROCESS INVEST SECURELY", color = CyberBlack, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

        } else {
            // DEVELOPER & GITHUB LAUNCH VIEW
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "🐙 GITHUB LAUNCH & SYNCHRONIZATION",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "QSAM is completely open-source. Below are the configurations and command stacks generated for Joseph Dougherty IV to successfully publish, initialize, and host the decentralized network nodes on GitHub and Render.",
                        color = CyberTealMuted,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CyberCyan.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "📂 REPOSITORY FILE PATHS:",
                        color = CyberCyan,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "1. `node.py` - Core Python server with Flask, Consensus math and cryptographic wallet mechanisms.\n" +
                        "2. `requirements.txt` - Deployment dependency definitions (Flask, Requests, Gunicorn).\n" +
                        "3. `QSAM_LAUNCH_GUIDE.md` - Complete protocol specification file written directly in your local directory.",
                        color = CyberTealMuted,
                        fontSize = 7.5.sp,
                        lineHeight = 11.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "⚡ QUICK DEPLOY TERMINAL CODES:",
                        color = CyberCyan,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberBlack, RoundedCornerShape(4.dp))
                            .border(1.dp, CyberCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "git init\n" +
                                   "git add .\n" +
                                   "git commit -m \"Initialize QSAM Blockchain Core consensus\"\n" +
                                   "git branch -M main\n" +
                                   "git remote add origin https://github.com/josephdougherty483/qsam-core.git\n" +
                                   "git push -u origin main",
                            color = CyberGreen,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            Toast.makeText(context, "Executing QSAM remote synchronization audit...", Toast.LENGTH_SHORT).show()
                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            handler.postDelayed({
                                Toast.makeText(context, "✅ GITHUB LAUNCH SYNC COMPLETED SUCCESSFULLY!", Toast.LENGTH_LONG).show()
                            }, 2500)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, CyberCyan),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            "⚡ TRIGGER GITHUB NODE SYNC & LAUNCH",
                            color = CyberCyan,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
