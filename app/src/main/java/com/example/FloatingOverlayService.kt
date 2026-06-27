package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.ChatMessage
import com.example.data.SystemLog
import com.example.ui.MainViewModel
import com.example.ui.theme.*

class FloatingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // Lifecycle Management for Jetpack Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var isExpanded = false

    // Drag-touch variables
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        private const val CHANNEL_ID = "q_genesis_floating_channel"
        private const val NOTIFICATION_ID = 4839

        fun startService(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        savedStateRegistryController.performRestore(null)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        setupFloatingView()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Q-Genesis Floating Assistive Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the Q-Genesis standalone floating deck overlay on top of other applications."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Q-Genesis Floating Assistant")
            .setContentText("Cyril-QAI active on top. Tap to open full screen.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setupFloatingView() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)

            setContent {
                MyApplicationTheme {
                    FloatingOverlayContent()
                }
            }
        }

        // Standard collapsed layout params
        params = WindowManager.LayoutParams(
            dpToPx(72),
            dpToPx(72),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        setupTouchListener()
        windowManager.addView(composeView, params)
    }

    private fun setupTouchListener() {
        val clickDragTolerance = 12f
        var isDragging = false

        composeView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > clickDragTolerance || Math.abs(dy) > clickDragTolerance) {
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(composeView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Click detected: Toggle expand/collapse state
                        toggleConsoleState()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleConsoleState() {
        isExpanded = !isExpanded
        if (isExpanded) {
            // Expand Window Dimensions & Enable Keyboard Input
            params.width = dpToPx(340)
            params.height = dpToPx(500)
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            // Collapse back to compact floating ball
            params.width = dpToPx(72)
            params.height = dpToPx(72)
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        windowManager.updateViewLayout(composeView, params)
    }

    @Composable
    fun FloatingOverlayContent() {
        var activeTab by remember { mutableStateOf("chat") } // "chat", "quantum", "logs"
        val viewModel = MainViewModel.activeInstance

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(CyberSurface.copy(alpha = 0.95f))
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            if (!isExpanded) {
                // Collapsed "Small Ball" Core Animation & Display
                val transition = rememberInfiniteTransition(label = "pulse")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotate"
                )
                val scale by transition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .scale(scale)
                        .rotate(rotation)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(CyberCyan, CyberPurple, CyberCyan)
                            )
                        )
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(CyberBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Q Genesis floating state",
                        tint = CyberCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                // Expanded Standalone Floating Deck Dashboard
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Title Bar with Drag & View Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Floating active",
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CYRIL Q-AI COGNITIVE DECK",
                                color = CyberWhite,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Shrink to ball
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Minimize to Ball",
                                tint = CyberTealMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { toggleConsoleState() }
                                    .padding(2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Fullscreen
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Go Fullscreen",
                                tint = CyberCyan,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                        startActivity(launchIntent)
                                        toggleConsoleState() // Collapse back to ball in overlay
                                    }
                                    .padding(2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Terminate service
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Overlay Engine",
                                tint = CyberRed,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { stopSelf() }
                                    .padding(2.dp)
                            )
                        }
                    }

                    // Navigation Tabs (Chat, Quantum, Autopilot, Logs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurface)
                            .border(width = (0.5).dp, color = CyberGray)
                    ) {
                        listOf("chat" to "CHATS", "quantum" to "QUANTUM", "autopilot" to "AUTOPILOT", "logs" to "LOGS").forEach { (tabKey, tabLabel) ->
                            val active = activeTab == tabKey
                            Box(
                                modifier = Modifier
                                    .weight(1.0f)
                                    .background(if (active) CyberSurfaceVariant else CyberSurface)
                                    .clickable { activeTab = tabKey }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabLabel,
                                    color = if (active) CyberCyan else CyberTealMuted,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Tab contents
                    Box(
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        if (viewModel == null) {
                            // Error: Active app not bound
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CONNECTING TO MAIN THREAD...\nPLEASE LAUNCH CYRIL-QAI APPLICATION ONCE.",
                                    color = CyberTealMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            when (activeTab) {
                                "chat" -> ChatOverlayTab(viewModel)
                                "quantum" -> QuantumOverlayTab(viewModel)
                                "autopilot" -> AutopilotOverlayTab(viewModel)
                                "logs" -> LogsOverlayTab(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ChatOverlayTab(viewModel: MainViewModel) {
        val messages by viewModel.chatMessages.collectAsState()
        var inputText by remember { mutableStateOf("") }
        var isSearchChecked by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth(),
                reverseLayout = true
            ) {
                // Show latest messages first in overlay list
                items(messages.reversed()) { msg ->
                    val isUser = msg.role == "user"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isUser) CyberGray else CyberSurfaceVariant)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = msg.content,
                                color = if (isUser) CyberWhite else CyberCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Web Search toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSearchChecked,
                    onCheckedChange = { isSearchChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CyberGreen,
                        uncheckedColor = CyberTealMuted,
                        checkmarkColor = CyberBlack
                    ),
                    modifier = Modifier.size(16.dp).scale(0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SEARCH WEB VIA QSAM EXTENSION",
                    color = if (isSearchChecked) CyberGreen else CyberTealMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Input Send Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Enter prompt...", color = CyberTealMuted.copy(alpha = 0.5f), fontSize = 10.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberGray,
                        focusedContainerColor = CyberBlack,
                        unfocusedContainerColor = CyberBlack
                    ),
                    modifier = Modifier.weight(1.0f).height(44.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (inputText.trim().isNotEmpty()) {
                            viewModel.sendMessage(inputText, isSearchChecked)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(CyberCyan, RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = CyberBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun QuantumOverlayTab(viewModel: MainViewModel) {
        val uriHandler = LocalUriHandler.current
        val entropy by viewModel.consciousness.collectAsState()
        val entangledCount by viewModel.entangledPairs.collectAsState()
        val qsamScore by viewModel.qsamScore.collectAsState()
        val isMining by viewModel.isMining.collectAsState()
        val consoleOutput by viewModel.aiLabResultText.collectAsState()

        var qasmInput by remember { mutableStateOf("110101") }
        var pythonScript by remember {
            mutableStateOf(
                "import qsam, escort, score\n" +
                "psi = qsam.BellState(0, 1)\n" +
                "mod = qsam.phase_modulation(psi)\n" +
                "score.mitigate(mod)"
            )
        }
        var selectedBackend by remember { mutableStateOf("ibm_torino") }
        var shotsAmount by remember { mutableStateOf("1024") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "QSAM SUPERPOSED QUANTUM CO-PROCESSOR",
                color = CyberCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Secure direct connection to physical/simulated IBM Quantum systems with QSAM, ESCORT, SCORE error correction, and phase modulation.",
                color = CyberTealMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            // State Readout Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurfaceVariant)
                    .border(0.5.dp, CyberCyan.copy(alpha = 0.2f))
                    .padding(5.dp)
            ) {
                Column {
                    Text("COHERENT METRICS (LIVE)", color = CyberWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("System Entropy: $entropy", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        Text("Bell State Pairs: $entangledCount", color = CyberPurple, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text("QSAM Coherence Score: $qsamScore", color = CyberGreen, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Input Fields section
            Text(
                text = "QSAM BINARY / QASM 2.0 CIRCUIT BUILDER",
                color = CyberWhite,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = qasmInput,
                onValueChange = { qasmInput = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = CyberGray,
                    focusedContainerColor = CyberBlack,
                    unfocusedContainerColor = CyberBlack
                ),
                modifier = Modifier.fillMaxWidth().height(42.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "PROPRIETARY PYTHON SCRIPT (ESCORT / SCORE / QEL)",
                color = CyberWhite,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = pythonScript,
                onValueChange = { pythonScript = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPurple,
                    unfocusedBorderColor = CyberGray,
                    focusedContainerColor = CyberBlack,
                    unfocusedContainerColor = CyberBlack
                ),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = CyberPurple, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Backend Selector and Shots
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("IBM HARDWARE BACKEND", color = CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                    Row {
                        listOf("ibm_torino", "ibm_brisbane").forEach { b ->
                            val isSel = selectedBackend == b
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .background(if (isSel) CyberCyan else CyberSurfaceVariant)
                                    .clickable { selectedBackend = b }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(b.substringAfter("_").uppercase(), color = if (isSel) CyberBlack else CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                Column(modifier = Modifier.width(60.dp)) {
                    Text("SHOTS", color = CyberTealMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = shotsAmount,
                        onValueChange = { shotsAmount = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberGray,
                            focusedContainerColor = CyberBlack,
                            unfocusedContainerColor = CyberBlack
                        ),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Run & Action Row
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val shotsVal = shotsAmount.toIntOrNull() ?: 1024
                        viewModel.submitQSamToIbmQuantum(qasmInput, selectedBackend, shotsVal)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1.0f).padding(end = 2.dp).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("SUBMIT QSAM HARDWARE JOB", color = CyberBlack, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        viewModel.executeNeutronLifetimeCalculation(300.0, 879.4)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(0.7f).padding(start = 2.dp).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("CHSH BELL", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Coherent Harvest Mining
            Button(
                onClick = { viewModel.mineCoins() },
                colors = ButtonDefaults.buttonColors(containerColor = if (isMining) CyberRed else CyberGreen),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (isMining) "HARVEST ACTIVE..." else "LAUNCH COHERENT HARVEST",
                    color = CyberBlack,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Console output display
            if (consoleOutput.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "HARDWARE EXECUTION STREAM",
                    color = CyberGreen,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberBlack)
                        .border(0.5.dp, CyberGreen.copy(alpha = 0.3f))
                        .padding(4.dp)
                        .heightIn(max = 100.dp)
                ) {
                    Text(
                        text = consoleOutput,
                        color = CyberGreen,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    @Composable
    fun AutopilotOverlayTab(viewModel: MainViewModel) {
        val autopilotUrl by viewModel.autopilotUrl.collectAsState()
        val autopilotContext by viewModel.autopilotContext.collectAsState()
        val autopilotPrompt by viewModel.autopilotPrompt.collectAsState()
        val isAutopilotRunning by viewModel.isAutopilotRunning.collectAsState()
        val autopilotOutput by viewModel.autopilotOutput.collectAsState()

        val cometLoss by viewModel.cometLoss.collectAsState()
        val cometAccuracy by viewModel.cometAccuracy.collectAsState()
        val cometLearningRate by viewModel.cometLearningRate.collectAsState()
        val cometEpoch by viewModel.cometEpoch.collectAsState()
        val cometTotalRuns by viewModel.cometTotalRuns.collectAsState()

        var pastedUrl by remember { mutableStateOf(autopilotUrl) }
        var pastedContext by remember { mutableStateOf(autopilotContext) }
        var activePrompt by remember { mutableStateOf(autopilotPrompt) }
        var simulatedLrVal by remember { mutableStateOf(cometLearningRate.toString()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "QSAM BROWSER CO-PROCESSOR",
                color = CyberCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Autonomous pilot analyzing page content overlaying active screen.",
                color = CyberTealMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Webpage target URL
            Text(
                text = "ACTIVE WEBPAGE TARGET HOST (ANY BROWSER)",
                color = CyberWhite,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = pastedUrl,
                onValueChange = {
                    pastedUrl = it
                    viewModel.updateAutopilotUrl(it)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = CyberGray,
                    focusedContainerColor = CyberBlack,
                    unfocusedContainerColor = CyberBlack
                ),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Web context content
            Text(
                text = "PASTED BROWSER PAGE CONTEXT (HTML/TEXT)",
                color = CyberWhite,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = pastedContext,
                onValueChange = {
                    pastedContext = it
                    viewModel.updateAutopilotContext(it)
                },
                placeholder = { Text("Paste any text, metrics, tables or code from your browser window here...", color = CyberTealMuted.copy(alpha = 0.5f), fontSize = 8.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPurple,
                    unfocusedBorderColor = CyberGray,
                    focusedContainerColor = CyberBlack,
                    unfocusedContainerColor = CyberBlack
                ),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Pilot Task Prompt
            Text(
                text = "AUTOPILOT COGNITIVE TASK DESCRIPTION",
                color = CyberWhite,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = activePrompt,
                onValueChange = {
                    activePrompt = it
                    viewModel.updateAutopilotPrompt(it)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = CyberGray,
                    focusedContainerColor = CyberBlack,
                    unfocusedContainerColor = CyberBlack
                ),
                modifier = Modifier.fillMaxWidth().height(42.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action triggers
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.executeAutopilotAction() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isAutopilotRunning) CyberRed else CyberCyan),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1.0f).padding(end = 2.dp).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (isAutopilotRunning) "AUTOPILOT CONNECTING..." else "COGNITIVE EXECUTE",
                        color = CyberBlack,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        pastedContext = ""
                        viewModel.updateAutopilotContext("")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(0.4f).padding(start = 2.dp).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("CLEAR", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Comet ML Tracking Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurfaceVariant)
                    .border(0.5.dp, CyberPurple.copy(alpha = 0.3f))
                    .padding(6.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("13TH CHAMBER COMET TRACKER", color = CyberPurple, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Box(
                            modifier = Modifier
                                .background(CyberBlack)
                                .border(0.5.dp, CyberGreen)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("RUNS ACTIVE: $cometTotalRuns", color = CyberGreen, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Hyper-Loss: ${String.format("%.4f", cometLoss)}", color = CyberRed, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text("Accuracy: ${String.format("%.2f", cometAccuracy * 100)}%", color = CyberGreen, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column {
                            Text("LR Rate: $cometLearningRate", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text("Epoch Iter: $cometEpoch", color = CyberWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Output Terminal
            if (autopilotOutput.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AUTOPILOT COGNITIVE LOGSTREAM",
                    color = CyberGreen,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberBlack)
                        .border(0.5.dp, CyberGreen.copy(alpha = 0.3f))
                        .padding(5.dp)
                        .heightIn(max = 140.dp)
                ) {
                    Text(
                        text = autopilotOutput,
                        color = CyberGreen,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    @Composable
    fun LogsOverlayTab(viewModel: MainViewModel) {
        val logs by viewModel.systemLogs.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "REAL-TIME LOGSTREAM SHARDS",
                color = CyberTealMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .background(CyberBlack)
                    .border(0.5.dp, CyberGray)
                    .padding(4.dp)
            ) {
                items(logs.takeLast(30).reversed()) { log ->
                    val color = when (log.level) {
                        "WARN" -> CyberRed
                        "GOOD" -> CyberGreen
                        else -> CyberCyan
                    }
                    Text(
                        text = "[${log.tag}] ${log.message}",
                        color = color,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if not present
            }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
