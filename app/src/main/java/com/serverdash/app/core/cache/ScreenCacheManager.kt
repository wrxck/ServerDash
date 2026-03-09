package com.serverdash.app.core.cache

import com.serverdash.app.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class CacheEntry<T>(
    val data: T,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class ScreenCacheManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private val cache = ConcurrentHashMap<String, CacheEntry<Any>>()
    private val mutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(key: String): T? {
        val entry = cache[key] ?: return null
        val ttl = getTtlMs()
        return if (System.currentTimeMillis() - entry.timestamp < ttl) {
            entry.data as? T
        } else {
            cache.remove(key)
            null
        }
    }

    suspend fun <T : Any> put(key: String, data: T) {
        cache[key] = CacheEntry(data)
    }

    fun invalidate(key: String) {
        cache.remove(key)
    }

    fun invalidatePrefix(prefix: String) {
        cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    fun invalidateAll() {
        cache.clear()
    }

    private suspend fun getTtlMs(): Long {
        return try {
            val prefs = preferencesManager.preferences.first()
            prefs.cacheTtlSeconds * 1000L
        } catch (_: Exception) {
            300_000L // 5 min fallback
        }
    }

    companion object Keys {
        const val CLAUDE_CODE_OVERVIEW = "claude_code_overview"
        const val CLAUDE_CODE_MCP = "claude_code_mcp"
        const val CLAUDE_CODE_SETTINGS = "claude_code_settings"
        const val CLAUDE_CODE_CLAUDE_MD = "claude_code_claude_md"
        const val CLAUDE_CODE_SKILLS = "claude_code_skills"
        const val CLAUDE_CODE_USAGE = "claude_code_usage"
        const val GIT_PREFIX = "git_"
        const val SECURITY_PREFIX = "security_"
        const val SERVICE_DETAIL_PREFIX = "service_detail_"
        const val SERVER_PREFIX = "server_"
    }
}
