package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_nodes")
data class DocumentNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val source: String, // "google_drive", "scraped_web", "manual_input"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "system_logs")
data class SystemLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String, // "BOOT", "QSAM", "WEB", "DRIVE", "EXEC"
    val message: String,
    val level: String = "INFO", // "INFO", "WARN", "GOOD"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Dao
interface DocumentNodeDao {
    @Query("SELECT * FROM document_nodes ORDER BY timestamp DESC")
    fun getAllNodes(): Flow<List<DocumentNode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: DocumentNode)

    @Query("DELETE FROM document_nodes WHERE id = :id")
    suspend fun deleteNode(id: Long)

    @Query("SELECT * FROM document_nodes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    suspend fun searchNodes(query: String): List<DocumentNode>

    @Query("DELETE FROM document_nodes")
    suspend fun clearNodes()
}

@Dao
interface SystemLogDao {
    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC LIMIT 60")
    fun getRecentLogs(): Flow<List<SystemLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SystemLog)

    @Query("DELETE FROM system_logs")
    suspend fun clearLogs()
}

@Entity(tableName = "wallet_transactions")
data class WalletTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val recipient: String,
    val amount: Double,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "mcp_context_nodes")
data class McpContextNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionTitle: String,
    val summary: String,
    val keywords: String, // comma-separated list of keywords for index lookup
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface WalletTransactionDao {
    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<WalletTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: WalletTransaction)

    @Query("DELETE FROM wallet_transactions")
    suspend fun clearTransactions()
}

@Dao
interface McpContextNodeDao {
    @Query("SELECT * FROM mcp_context_nodes ORDER BY timestamp DESC")
    fun getAllMcpNodes(): Flow<List<McpContextNode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMcpNode(node: McpContextNode)

    @Query("DELETE FROM mcp_context_nodes WHERE id = :id")
    suspend fun deleteMcpNode(id: Long)

    @Query("SELECT * FROM mcp_context_nodes WHERE sessionTitle LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR keywords LIKE '%' || :query || '%'")
    suspend fun searchMcpNodes(query: String): List<McpContextNode>

    @Query("DELETE FROM mcp_context_nodes")
    suspend fun clearAllMcpNodes()
}

@Database(entities = [ChatMessage::class, DocumentNode::class, SystemLog::class, WalletTransaction::class, McpContextNode::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val chatMessageDao: ChatMessageDao
    abstract val documentNodeDao: DocumentNodeDao
    abstract val systemLogDao: SystemLogDao
    abstract val walletTransactionDao: WalletTransactionDao
    abstract val mcpContextNodeDao: McpContextNodeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "q_genesis_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
