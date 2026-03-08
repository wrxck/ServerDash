package com.serverdash.app.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.serverdash.app.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastAuthenticatedTime: Long = 0L

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
        val elapsed = System.currentTimeMillis() - lastAuthenticatedTime
        val timeoutMs = prefs.appLockTimeout.seconds * 1000L
        if (lastAuthenticatedTime == 0L || elapsed > timeoutMs) {
            _isLocked.value = true
        }
    }

    /**
     * Called when the app goes to background. Records the time for timeout calculation.
     */
    fun onAppPaused() {
        // Time is already tracked via lastAuthenticatedTime
    }

    /**
     * Trigger biometric/device-credential authentication.
     */
    fun authenticate(
        activity: FragmentActivity,
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

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ServerDash")
            .setSubtitle("Authenticate to access the app")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Check if the device has any authentication method set up.
     */
    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
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
