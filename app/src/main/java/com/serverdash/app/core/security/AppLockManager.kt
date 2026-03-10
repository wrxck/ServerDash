package com.serverdash.app.core.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.serverdash.app.data.preferences.PreferencesManager
import com.serverdash.app.domain.model.AppLockAuthMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastAuthenticatedTime: Long = 0L
    private var wasDeviceLocked: Boolean = false

    /**
     * Called when the app resumes. Checks if the app should be locked
     * based on the lock setting and timeout.
     */
    suspend fun onAppResumed() {
        val prefs = preferencesManager.preferences.first()
        if (!prefs.appLockEnabled) {
            _isLocked.value = false
            return
        }

        // If device is currently locked or was locked while we were in background,
        // force re-authentication regardless of timeout
        if (prefs.lockOnDeviceLock) {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isDeviceLocked == true || wasDeviceLocked) {
                wasDeviceLocked = false
                _isLocked.value = true
                return
            }
        }

        val elapsed = System.currentTimeMillis() - lastAuthenticatedTime
        val timeoutMs = prefs.appLockTimeout.seconds * 1000L
        if (lastAuthenticatedTime == 0L || elapsed > timeoutMs) {
            _isLocked.value = true
        }
    }

    /**
     * Called when the app goes to background. Checks if device screen is locked.
     */
    fun onAppPaused() {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        wasDeviceLocked = keyguardManager?.isKeyguardLocked == true
    }

    private fun getAuthenticators(authMethod: AppLockAuthMethod): Int = when (authMethod) {
        AppLockAuthMethod.BIOMETRIC_ONLY ->
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        AppLockAuthMethod.DEVICE_CREDENTIAL_ONLY ->
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        AppLockAuthMethod.ANY ->
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    /**
     * Trigger biometric/device-credential authentication.
     */
    fun authenticate(
        activity: FragmentActivity,
        authMethod: AppLockAuthMethod = AppLockAuthMethod.ANY,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lastAuthenticatedTime = System.currentTimeMillis()
                _isLocked.value = false
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User cancelled or no biometrics enrolled - stay locked
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                // Individual attempt failed, prompt stays open
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        val authenticators = getAuthenticators(authMethod)

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ServerDash")
            .setSubtitle("Authenticate to access the app")
            .setAllowedAuthenticators(authenticators)

        // Negative button is required when DEVICE_CREDENTIAL is not included
        if (authMethod == AppLockAuthMethod.BIOMETRIC_ONLY) {
            builder.setNegativeButtonText("Cancel")
        }

        biometricPrompt.authenticate(builder.build())
    }

    /**
     * Check if the device has any authentication method set up.
     */
    fun canAuthenticate(activity: FragmentActivity, authMethod: AppLockAuthMethod = AppLockAuthMethod.ANY): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val result = biometricManager.canAuthenticate(getAuthenticators(authMethod))
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Immediately lock the app (e.g. quick lock button).
     */
    fun lock() {
        lastAuthenticatedTime = 0L
        _isLocked.value = true
    }

    /**
     * Mark as authenticated (e.g. when lock is first enabled and user is already in app).
     */
    fun markAuthenticated() {
        lastAuthenticatedTime = System.currentTimeMillis()
        _isLocked.value = false
    }
}
