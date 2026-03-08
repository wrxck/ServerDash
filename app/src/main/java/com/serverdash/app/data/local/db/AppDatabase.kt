package com.serverdash.app.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [ServerConfigEntity::class, ServiceEntity::class, MetricsEntity::class, AlertRuleEntity::class, AlertEntity::class, TerminalHistoryEntity::class],
    version = 3,
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
    val label: String = "",
    val sudoPassword: String = ""
)

@Entity(
    tableName = "services",
    indices = [Index(value = ["serverId", "name"], unique = true)]
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val displayName: String,
    val type: ServiceTypeDb,
    val status: ServiceStatusDb = ServiceStatusDb.UNKNOWN,
    val isPinned: Boolean = false,
    val subState: String = "",
    val description: String = "",
    @ColumnInfo(defaultValue = "") val group: String = ""
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
abstract class ServiceDao {
    @Query("SELECT * FROM services WHERE serverId = :serverId ORDER BY isPinned DESC, name ASC")
    abstract fun observeServices(serverId: Long): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE serverId = :serverId AND isPinned = 1 ORDER BY name ASC")
    abstract fun observePinnedServices(serverId: Long): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE serverId = :serverId")
    abstract suspend fun getServices(serverId: Long): List<ServiceEntity>

    @Query("""
        INSERT INTO services (serverId, name, displayName, type, status, subState, description, isPinned, `group`)
        VALUES (:serverId, :name, :displayName, :type, :status, :subState, :description, 0, '')
        ON CONFLICT(serverId, name) DO UPDATE SET
            status = excluded.status,
            subState = excluded.subState,
            description = excluded.description,
            displayName = excluded.displayName
    """)
    abstract suspend fun upsertService(
        serverId: Long,
        name: String,
        displayName: String,
        type: ServiceTypeDb,
        status: ServiceStatusDb,
        subState: String,
        description: String
    )

    @Query("UPDATE services SET status = :status, subState = :subState WHERE id = :id")
    abstract suspend fun updateStatus(id: Long, status: ServiceStatusDb, subState: String)

    @Query("UPDATE services SET isPinned = :pinned WHERE id = :id")
    abstract suspend fun updatePinned(id: Long, pinned: Boolean)

    @Query("UPDATE services SET `group` = :group WHERE id = :id")
    abstract suspend fun updateGroup(id: Long, group: String)

    @Query("SELECT DISTINCT `group` FROM services WHERE serverId = :serverId AND `group` != '' ORDER BY `group` ASC")
    abstract fun observeGroups(serverId: Long): Flow<List<String>>

    @Query("DELETE FROM services WHERE serverId = :serverId AND name NOT IN (:activeNames)")
    abstract suspend fun deleteStale(serverId: Long, activeNames: List<String>)

    @Query("DELETE FROM services WHERE serverId = :serverId")
    abstract suspend fun deleteByServer(serverId: Long)

    @Transaction
    open suspend fun syncServices(serverId: Long, services: List<ServiceEntity>) {
        // Upsert all current services (preserves isPinned and group)
        for (s in services) {
            upsertService(
                serverId = serverId,
                name = s.name,
                displayName = s.displayName,
                type = s.type,
                status = s.status,
                subState = s.subState,
                description = s.description
            )
        }
        // Remove services that no longer exist on the server
        val activeNames = services.map { it.name }
        if (activeNames.isNotEmpty()) {
            deleteStale(serverId, activeNames)
        }
    }
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
