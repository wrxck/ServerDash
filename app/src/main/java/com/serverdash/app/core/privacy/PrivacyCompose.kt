package com.serverdash.app.core.privacy

import androidx.compose.runtime.*

/**
 * CompositionLocal providing the PrivacyFilter for the entire composable tree.
 * Use [redact] to filter text through the current privacy settings.
 */
val LocalPrivacyFilter = staticCompositionLocalOf<PrivacyFilter?> { null }

/**
 * Applies the current privacy filter to the given text.
 * Returns the text unmodified if no filter is available or streaming mode is off.
 */
@Composable
fun redact(text: String): String {
    val filter = LocalPrivacyFilter.current ?: return text
    val enabled by filter.isEnabled.collectAsState()
    return if (enabled) remember(text, enabled) { filter.filter(text) } else text
}

/**
 * Non-composable version for use in remember blocks or callbacks.
 * Requires the filter instance directly.
 */
fun redactWith(filter: PrivacyFilter?, text: String): String {
    return filter?.filter(text) ?: text
}
