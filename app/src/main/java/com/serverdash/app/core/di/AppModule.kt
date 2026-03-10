package com.serverdash.app.core.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.serverdash.app.data.encryption.EncryptionManager
import com.serverdash.app.data.local.db.*
import com.serverdash.app.data.preferences.PreferencesManager
import com.serverdash.app.data.remote.ssh.SshSessionManager
import com.serverdash.app.data.repository.*
import com.serverdash.app.domain.repository.*
import com.serverdash.app.domain.plugin.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "serverdash.db"

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE server_config ADD COLUMN rootAuthType TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE server_config ADD COLUMN rootPrivateKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE server_config ADD COLUMN rootPassphrase TEXT NOT NULL DEFAULT ''")
            // Migrate existing sudo password configs to rootAuthType="sudo"
            db.execSQL("UPDATE server_config SET rootAuthType = 'sudo' WHERE sudoPassword != ''")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        encryptionManager: EncryptionManager
    ): AppDatabase {
        System.loadLibrary("sqlcipher")

        // If encryption was just enabled, the existing DB is unencrypted.
        // SQLCipher can't open an unencrypted DB with a passphrase, so we
        // delete the old file and let Room recreate it encrypted.
        if (encryptionManager.isEncryptionEnabled && encryptionManager.needsDatabaseMigration) {
            context.deleteDatabase(DB_NAME)
            encryptionManager.markDatabaseMigrated()
        }

        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DB_NAME
        ).addMigrations(MIGRATION_3_4)
         .fallbackToDestructiveMigration()

        if (encryptionManager.isEncryptionEnabled) {
            val passphrase = encryptionManager.getDatabasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            builder.openHelperFactory(factory)
        }

        return builder.build()
    }

    @Provides
    fun provideServerConfigDao(db: AppDatabase): ServerConfigDao = db.serverConfigDao()

    @Provides
    fun provideServiceDao(db: AppDatabase): ServiceDao = db.serviceDao()

    @Provides
    fun provideMetricsDao(db: AppDatabase): MetricsDao = db.metricsDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()

    @Provides
    fun provideTerminalHistoryDao(db: AppDatabase): TerminalHistoryDao = db.terminalHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {
    @Provides
    @Singleton
    fun providePluginRegistry(): PluginRegistry {
        return PluginRegistry().apply {
            register(FleetPlugin())
            register(ClaudeCodePlugin())
            register(GuardianPlugin())
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindServerRepository(impl: ServerRepositoryImpl): ServerRepository

    @Binds
    @Singleton
    abstract fun bindServiceRepository(impl: ServiceRepositoryImpl): ServiceRepository

    @Binds
    @Singleton
    abstract fun bindSshRepository(impl: SshRepositoryImpl): SshRepository

    @Binds
    @Singleton
    abstract fun bindMetricsRepository(impl: MetricsRepositoryImpl): MetricsRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository
}
