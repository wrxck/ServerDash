package com.serverdash.app.data.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_ALIAS = "serverdash_db_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTED_PREFS_FILE = "serverdash_encryption_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val KEY_ENCRYPTION_ENABLED = "encryption_enabled"
        private const val KEY_ENCRYPTION_PROMPT_DISMISSED = "encryption_prompt_dismissed"
        private const val KEY_ENCRYPTION_PROMPT_DISMISS_TIME = "encryption_prompt_dismiss_time"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DB_MIGRATED = "db_migrated"
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(false)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val isEncryptionEnabled: Boolean
        get() = encryptedPrefs.getBoolean(KEY_ENCRYPTION_ENABLED, false)

    val isBiometricEnabled: Boolean
        get() = encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    val needsDatabaseMigration: Boolean
        get() = isEncryptionEnabled && !encryptedPrefs.getBoolean(KEY_DB_MIGRATED, false)

    fun markDatabaseMigrated() {
        encryptedPrefs.edit().putBoolean(KEY_DB_MIGRATED, true).apply()
    }

    val shouldShowEncryptionPrompt: Boolean
        get() {
            if (isEncryptionEnabled) return false
            val dismissed = encryptedPrefs.getBoolean(KEY_ENCRYPTION_PROMPT_DISMISSED, false)
            if (!dismissed) return true
            // Check if 30 days have passed since dismissal
            val dismissTime = encryptedPrefs.getLong(KEY_ENCRYPTION_PROMPT_DISMISS_TIME, 0L)
            return System.currentTimeMillis() - dismissTime > THIRTY_DAYS_MS
        }

    fun getDatabasePassphrase(): ByteArray {
        val stored = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) {
            return stored.toByteArray(Charsets.UTF_8)
        }

        // Generate a new passphrase and store it
        val passphrase = generatePassphrase()
        encryptedPrefs.edit()
            .putString(KEY_DB_PASSPHRASE, String(passphrase, Charsets.UTF_8))
            .apply()
        return passphrase
    }

    fun enableEncryption(withBiometric: Boolean): Result<Unit> {
        return try {
            // Ensure passphrase is generated
            getDatabasePassphrase()
            encryptedPrefs.edit()
                .putBoolean(KEY_ENCRYPTION_ENABLED, true)
                .putBoolean(KEY_BIOMETRIC_ENABLED, withBiometric)
                .remove(KEY_ENCRYPTION_PROMPT_DISMISSED)
                .remove(KEY_ENCRYPTION_PROMPT_DISMISS_TIME)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun dismissEncryptionPrompt() {
        encryptedPrefs.edit()
            .putBoolean(KEY_ENCRYPTION_PROMPT_DISMISSED, true)
            .putLong(KEY_ENCRYPTION_PROMPT_DISMISS_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun generatePassphrase(): ByteArray {
        // Generate random 32-byte passphrase backed by Android KeyStore
        ensureKeyStoreKey()
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        // Encode as hex string for SQLCipher compatibility
        return bytes.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
    }

    private fun ensureKeyStoreKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) return

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }
}
