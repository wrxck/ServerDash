package com.serverdash.app.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [ServerConfigEntity::class, ServiceEntity::class, MetricsEntity::class, AlertRuleEntity::class, AlertEntity::class, TerminalHistoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun serviceDao(): ServiceDao
    abstract fun metricsDao(): MetricsDao
    abstract fun alertDao(): AlertDao
    abstract fun terminalHistoryDao(): TerminalHistoryDao
}

class Converters {
    @TypeConverter
    fun fromServiceType(value: String): ServiceTypeDb = ServiceTypeDb.valueOf(value)
    @TypeConverter
    fun toServiceType(value: ServiceTypeDb): String = value.name
    @TypeConverter
    fun fromServiceStatus(value: String): ServiceStatusDb = ServiceStatusDb.valueOf(value)
    @TypeConverter
    fun toServiceStatus(value: ServiceStatusDb): String = value.name
}

enum class ServiceTypeDb { SYSTEMD, DOCKER }
enum class ServiceStatusDb { RUNNING, STOPPED, FAILED, UNKNOWN }

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val label: String = ""
)

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val displayName: String,
    val type: ServiceTypeDb,
    val status: ServiceStatusDb = ServiceStatusDb.UNKNOWN,
    val isPinned: Boolean = false,
    val subState: String = "",
    val description: String = ""
)

@Entity(tableName = "metrics")
data class MetricsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cpuUsage: Float,
    val memoryUsed: Long,
    val memoryTotal: Long,
    val diskUsed: Long,
    val diskTotal: Long,
    val loadAvg1: Float,
    val loadAvg5: Float,
    val loadAvg15: Float,
    val uptimeSeconds: Long,
    val timestamp: Long
)

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val conditionType: String,
    val conditionValue: String,
    val isEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val webhookUrl: String = ""
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val message: String,
    val timestamp: Long,
    val acknowledged: Boolean = false
)

@Entity(tableName = "terminal_history")
data class TerminalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: Long
)

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config LIMIT 1")
    suspend fun getConfig(): ServerConfigEntity?

    @Query("SELECT * FROM server_config LIMIT 1")
    fun observeConfig(): Flow<ServerConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ServerConfigEntity): Long

    @Query("DELETE FROM server_config")
    suspend fun deleteAll()
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services WHERE serverId = :serverId ORDER BY isPinned DESC, name ASC")
    fun observeServices(serverId: Long): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE serverId = :serverId AND isPinned = 1 ORDER BY name ASC")
    fun observePinnedServices(serverId: Long): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE serverId = :serverId")
    suspend fun getServices(serverId: Long): List<ServiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(services: List<ServiceEntity>)

    @Query("UPDATE services SET status = :status, subState = :subState WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ServiceStatusDb, subState: String)

    @Query("UPDATE services SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM services WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: Long)
}

@Dao
interface MetricsDao {
    @Insert
    suspend fun insert(metrics: MetricsEntity)

    @Query("SELECT * FROM metrics ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MetricsEntity>

    @Query("SELECT * FROM metrics ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<MetricsEntity?>

    @Query("DELETE FROM metrics WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AlertRuleEntity): Long

    @Query("SELECT * FROM alert_rules WHERE serverId = :serverId")
    suspend fun getRules(serverId: Long): List<AlertRuleEntity>

    @Query("SELECT * FROM alert_rules WHERE serverId = :serverId")
    fun observeRules(serverId: Long): Flow<List<AlertRuleEntity>>

    @Query("DELETE FROM alert_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Insert
    suspend fun insertAlert(alert: AlertEntity)

    @Query("SELECT * FROM alerts WHERE acknowledged = 0 ORDER BY timestamp DESC")
    suspend fun getActiveAlerts(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE acknowledged = 0 ORDER BY timestamp DESC")
    fun observeActiveAlerts(): Flow<List<AlertEntity>>

    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledgeAlert(id: Long)
}

@Dao
interface TerminalHistoryDao {
    @Insert
    suspend fun insert(entry: TerminalHistoryEntity)

    @Query("SELECT * FROM terminal_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<TerminalHistoryEntity>

    @Query("SELECT * FROM terminal_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TerminalHistoryEntity>>

    @Query("DELETE FROM terminal_history")
    suspend fun deleteAll()
}
