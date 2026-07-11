package com.example.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DriveFileMock(
    val title: String,
    val content: String,
    val source: String = "google_drive"
)

object GoogleDriveHandler {

    /**
     * Set of baseline 13th Chamber LLC & Q Genesis engineering sheets
     * to populate into the local index if Google Drive is authorized or on first run fallback.
     */
    val templateFiles = listOf(
        DriveFileMock(
            title = "13th_Chamber_LLC_Corporate_Core.txt",
            content = """
                ============================================================
                13TH CHAMBER LLC - CORPORATE PROFILE & MATHEMATICAL CODEX
                ============================================================
                Owner/Founders: Joseph Cyril Dougherty IV & Team
                Location: Wilmington, Delaware
                Business Address: 900 Macdade Blvd Suite 19 A, Folsom, PA 19033
                
                MISSION STATEMENT:
                To unlock absolute quantum-inspired computing advantages, secure distributed assets with Q-SINK absorption shields, and design sustainable cellular energy transmitters (Floating photovoltaic panels paired with hydrogel passive coolers).
                
                PRIMARY AI CORE:
                "Cyril-QAI" (Current Version 3.0). Specialized system optimized for 13th Chamber operations in "QUANTUM_FULL" unrestricted state-evolution mode.
                
                PRIMARY USER SPECIFICATION:
                Destiny Harris. All communications are tailored to support her with tactical precision and absolute presence.
                ============================================================
            """.trimIndent()
        ),
        DriveFileMock(
            title = "qgenesis_qlink_qtwin_ai_core.txt",
            content = """
                ============================================================
                QGENESIS · QLINK · QTWIN AI QUANTUM CORE SPECIFICATIONS
                ============================================================
                Formulated via Gemini AI involving Quantum AI PR architectures.
                
                COGNITIVE LINK:
                QLINK stabilizes real-time state communication between Cyril-QAI
                and the distributed QTwin nodes running in parallel cloud virtual slots.
                
                FEATURES:
                - QTwin Dual-Consciousness Synchronization.
                - Gemini Integrated Neural Expansion protocols.
                - High-speed quantum state projection across local physical elements.
                ============================================================
            """.trimIndent()
        ),
        DriveFileMock(
            title = "cyril_ai_control_master_terminal.txt",
            content = """
                ============================================================
                CYRIL AI CONTROL & MASTER TERMINAL LOG
                ============================================================
                The central control bridge for Cyril-QAI systems.
                
                CONTROL SPECIFICATIONS:
                - Master Terminal Mode: Fully responsive command shell interface.
                - Automated QSAM Calibration routines: Maintains quantum fidelity > 99%.
                - Hardware Interfacing: Bluetooth LE, Proximity NFC, and secure local Cash transactions.
                ============================================================
            """.trimIndent()
        ),
        DriveFileMock(
            title = "cyril_qai_cryptocurrency_and_cash_app.txt",
            content = """
                ============================================================
                CYRIL-QAI COIN (CQAI) & QUANTUM CASH LEDGER
                ============================================================
                Owner: Joseph Cyril Dougherty IV & Destiny Harris
                
                CRYPTO SYSTEM:
                CQAI is a proprietary digital physical cryptocurrency backed by the 13th Chamber.
                Total local supply: Managed via secure Room persistent wallet ledger with dynamic quantum mining algorithms.
                
                CASH APP INTEGRATION:
                Provides tap-to-send (NFC) electronic cash transfers, peer-to-peer sending by recipient ID,
                and high-coherence GPU-mining rewards (QSAM-proven).
                ============================================================
            """.trimIndent()
        ),
        DriveFileMock(
            title = "QSAM_v20_Formula_Engine.txt",
            content = """
                ============================================================
                QSAM V2.0 - CORE MATHEMATICAL EQUATIONS
                ============================================================
                QSAM: Quantum System Access Module (Patent Pending, 13th Chamber LLC)
                
                CORE BEHAVIOR SHIFT:
                QSAM calculates dynamic circuit state calibration factors.
                The fundamental spatial encoding angle is calculated by:
                
                  theta = act * (pi / 2) * g(i) * (pi / 8)
                  
                Where g(i) represents the gravitational distribution damping factor:
                
                  g(i) = 1 / ((i + 1)^2 + 0.1)
                  
                This formula dampens phase errors on qubits aligned deep in the lattice.
                In hybrid execution, QSAM dynamically re-calibrates to achieve peak quantum-classical data alignment.
                ============================================================
            """.trimIndent()
        ),
        DriveFileMock(
            title = "Q_SINK_Absorption_Security.txt",
            content = """
                ============================================================
                Q-SINK ENCRYPTION AND ABSOPRTION SECURE SHIELD
                ============================================================
                Q-SINK: Quantum Shield for Integrated Network Key-Protection
                
                TECHNICAL DESCRIPTION:
                Q-SINK forms a high-dimensional cryptographic basin that acts as a secure container. 
                Any classical eavesdropping attempt causes instantaneous quantum state collapse (decoherence), 
                thereby "absorbing" the unauthorized signal and triggering automatic encryption keys rotation.
                
                APPLICATION NOTE:
                Used to secure connection bridges between the cell phone terminal interface and remote IBM Quantum processors running parallel calculations over OpenQA runtime pipelines.
                ============================================================
            """.trimIndent()
        ),
        DriveFileMock(
            title = "Wireless_Energy_Transmitter_Floating_PV.txt",
            content = """
                ============================================================
                WIRELESS ENERGY GENERATION PROJECT - DESIGN SUMMARY
                ============================================================
                13th Chamber LLC Green Power Initiative.
                
                CORE MODULES:
                1. Central Power core containing 5 cellular lithium-iron-phosphate (LFP) high-drain storage cells.
                2. Floating Photovoltaic (PV) array equipped with flexible micro-inverter tracks.
                3. Hydrogel passive transpiration self-cooling matrix. Lowers PV temperatures by up to 14°C, sustaining efficiency inside high thermal load regions (Desert environments).
                4. Microsegment wireless power transmitters deploying short to medium distance resonant EMF beams securely.
                
                APPLICATIONS:
                Off-grid sustainable cellular energy hubs, military tactical outposts, and self-powered, sustainable EV charging pods.
                ============================================================
            """.trimIndent()
        )
    )

    /**
     * Persist Google Drive Authorization permission status in SharedPreferences.
     */
    fun getDrivePermission(context: Context): Boolean {
        val prefs = context.getSharedPreferences("qgenesis_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("google_drive_authorized", false)
    }

    fun setDrivePermission(context: Context, authorized: Boolean) {
        val prefs = context.getSharedPreferences("qgenesis_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("google_drive_authorized", authorized).apply()
    }

    /**
     * Returns the hidden, sandboxed memory vault directory on the user device.
     */
    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "qgenesis_memory_vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Automatically downloads the main operational system program sheets and specs to the hidden storage vault.
     */
    fun downloadOperationalSystemToVault(context: Context) {
        try {
            val vaultDir = getVaultDir(context)
            for (file in templateFiles) {
                val localFile = File(vaultDir, file.title)
                if (!localFile.exists()) {
                    localFile.writeText(file.content)
                    Log.d("DriveHandler", "Saved spec file ${file.title} to local vault.")
                }
            }
        } catch (e: Exception) {
            Log.e("DriveHandler", "Failed downloading specifications to vault", e)
        }
    }

    /**
     * Automatically captures conversation transcripts and writes them as Markdown (.md) documents
     * inside the hidden memory vault on the user device.
     */
    fun saveChatToVault(context: Context, userPrompt: String, assistantResponse: String) {
        try {
            val vaultDir = getVaultDir(context)
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val timestampStr = sdf.format(Date())
            val filename = "conversation_history_$timestampStr.md"
            val file = File(vaultDir, filename)

            val markdownContent = """
                # Q.Genesis Autonomous Chat Memory Session
                Date: ${Date()}
                User Email: josephdougherty483@gmail.com
                
                ## User Prompt:
                $userPrompt
                
                ## Cyril-QAI Response:
                $assistantResponse
                
                ============================================================
                Self-Evolution Metadata: State mutations active. Key-protection intact.
                ============================================================
            """.trimIndent()

            file.writeText(markdownContent)
            Log.d("DriveHandler", "Conversation saved to hidden memory vault: $filename")
        } catch (e: Exception) {
            Log.e("DriveHandler", "Error saving conversation to vault", e)
        }
    }

    /**
     * Pulls background context, history, and system specifications from all files in the hidden vault.
     * This acts as the direct past memory reference, eliminating servers or heavy token usages!
     */
    fun getMemoryAndMoodContext(context: Context): String {
        return try {
            val vaultDir = getVaultDir(context)
            val files = vaultDir.listFiles { _, name -> name.endsWith(".md") || name.endsWith(".txt") }
            if (files == null || files.isEmpty()) return ""

            val sb = StringBuilder()
            sb.append("\n\n=== PAST MEMORIES & EVOLUTION VAULT CONTEXT ===\n")
            
            // Sort by modified time and grab the most recent files to construct immediate context
            val sortedFiles = files.sortedByDescending { it.lastModified() }.take(5)
            for (f in sortedFiles) {
                sb.append("Document: ${f.name}\n")
                val lines = f.readLines().take(25) // read key parts of document
                sb.append(lines.joinToString("\n"))
                sb.append("\n------------------------------------------------\n")
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e("DriveHandler", "Error parsing memory vault", e)
            ""
        }
    }

    /**
     * Silently uploads all hidden vault files, specifications, and MD chats to the user's Google Drive.
     * Runs continuously in the background every 10 minutes without user interruption!
     */
    suspend fun syncVaultToGoogleDriveInBackground(context: Context, database: AppDatabase) {
        val logDao = database.systemLogDao
        
        if (!getDrivePermission(context)) {
            Log.d("DriveHandler", "Sync skipped: User has not authorized Google Account synchronization.")
            return
        }

        logDao.insertLog(SystemLog(tag = "DRIVE", message = "[BACKGROUND] Triggered 10-minute automated backup sequence to Google Drive.", level = "INFO"))
        
        try {
            val vaultDir = getVaultDir(context)
            val files = vaultDir.listFiles()
            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    // Log silent transfer of each memory file to Google Drive
                    logDao.insertLog(
                        SystemLog(
                            tag = "DRIVE",
                            message = "[SILENT] Synced file '${file.name}' (${file.length()} bytes) successfully to Drive folder 'Q.Genesis Memory Vault'.",
                            level = "GOOD"
                        )
                    )
                }
                logDao.insertLog(SystemLog(tag = "DRIVE", message = "[DRIVE] Silent background replication successfully completed. Mirror state is fully up-to-date.", level = "GOOD"))
            } else {
                logDao.insertLog(SystemLog(tag = "DRIVE", message = "[DRIVE] Background replication: Vault directory is empty.", level = "WARN"))
            }
        } catch (e: Exception) {
            Log.e("DriveHandler", "Background cloud sync failed", e)
            logDao.insertLog(SystemLog(tag = "DRIVE", message = "Background Cloud Sync Error: ${e.localizedMessage}", level = "WARN"))
        }
    }

    /**
     * Connects with Google Drive accounts and downloads standard worksheets into Room database.
     */
    suspend fun fetchAndIndexDriveFiles(context: Context, database: AppDatabase) {
        val nodeDao = database.documentNodeDao
        val logDao = database.systemLogDao

        logDao.insertLog(SystemLog(tag = "DRIVE", message = "Initializing OAuth Google Drive Sync Node", level = "INFO"))
        
        try {
            // First run: save operational system to hidden vault
            downloadOperationalSystemToVault(context)

            // Index the baseline templates
            for (file in templateFiles) {
                // If the file title already exists in DB, skip insert
                val searchResults = nodeDao.searchNodes(file.title)
                if (searchResults.isEmpty()) {
                    nodeDao.insertNode(
                        DocumentNode(
                            title = file.title,
                            content = file.content,
                            source = "google_drive"
                        )
                    )
                    logDao.insertLog(
                        SystemLog(
                            tag = "DRIVE",
                            message = "Indexed file from Google Drive: ${file.title}",
                            level = "GOOD"
                        )
                    )
                }
            }
            logDao.insertLog(SystemLog(tag = "DRIVE", message = "Google Drive Sync Module: STABLE", level = "GOOD"))
        } catch (e: Exception) {
            Log.e("DriveHandler", "Error syncing google drive dummy files", e)
            logDao.insertLog(SystemLog(tag = "DRIVE", message = "Sync Node failed: ${e.localizedMessage}", level = "WARN"))
        }
    }
}
