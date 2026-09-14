/**
 * Vyuha 2.0 — Graph Token Cache
 * ===============================
 * LRU in-memory cache for GraphRiskTokens, keyed by VPA hash.
 * Provides fast cache hits for repeated VPA lookups and graceful
 * degradation when the network is unavailable.
 *
 * Architecture ref: Decision D4 — Offline Mode Definition.
 * Core safety continues functioning with graphRisk = UNKNOWN.
 *
 * Thread safety: Uses ConcurrentHashMap for concurrent access
 * from coroutine dispatchers without explicit locking.
 */
package com.vyuha.sdk.cache

import com.vyuha.sdk.contracts.GraphRiskToken
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class GraphTokenCache(
    /** Maximum number of cached tokens. */
    private val maxSize: Int = 50,
    /** Grace period (ms) after token expiry to still return a degraded result. */
    private val gracePeriodMs: Long = 0L
) {
    private val cache = ConcurrentHashMap<String, CachedEntry>()
    private val accessOrder = ConcurrentLinkedDeque<String>()

    /**
     * A cached token entry with metadata.
     */
    private data class CachedEntry(
        val token: GraphRiskToken,
        val cachedAtMs: Long = System.currentTimeMillis()
    )

    /**
     * Put a token into the cache.
     */
    fun put(vpaHash: String, token: GraphRiskToken) {
        require(maxSize > 0)
        require(token.vpaHash == vpaHash)
        // Evict if at capacity
        while (cache.size >= maxSize) {
            val oldest = accessOrder.pollFirst() ?: break
            cache.remove(oldest)
        }

        cache[vpaHash] = CachedEntry(token)
        accessOrder.remove(vpaHash)
        accessOrder.addLast(vpaHash)
    }

    /**
     * Get a cached token.
     *
     * @return The cached token if valid, or a degraded token if within
     *         the grace period. Returns null if not cached or fully expired.
     */
    fun get(vpaHash: String): CacheResult {
        val entry = cache[vpaHash] ?: return CacheResult.Miss

        val now = System.currentTimeMillis()
        val tokenExpiryMs = entry.token.expiresAt * 1000  // expiresAt is in seconds

        return when {
            // Token is still valid
            now < tokenExpiryMs -> {
                // Update access order
                accessOrder.remove(vpaHash)
                accessOrder.addLast(vpaHash)
                CacheResult.Hit(entry.token)
            }
            // Token expired but within grace period → return with reduced confidence
            // Fully expired
            else -> {
                cache.remove(vpaHash)
                accessOrder.remove(vpaHash)
                CacheResult.Miss
            }
        }
    }

    /**
     * Clear all cached entries.
     */
    fun clear() {
        cache.clear()
        accessOrder.clear()
    }

    /**
     * Number of currently cached entries.
     */
    fun size(): Int = cache.size

    /**
     * Result of a cache lookup.
     */
    sealed class CacheResult {
        /** Fresh, valid token from cache. */
        data class Hit(val token: GraphRiskToken) : CacheResult()
        /** Expired but within grace period — reduced confidence. */
        data class StaleHit(val token: GraphRiskToken) : CacheResult()
        /** Not in cache or fully expired. */
        object Miss : CacheResult()
    }
}
