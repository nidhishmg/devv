package com.devaki.app.net

import android.os.SystemClock

/**
 * Simple rate limiter to prevent sending commands too frequently.
 * Tracks last send time per key and enforces minimum interval.
 */
class RateLimiter(private val minIntervalMs: Long) {
    
    private val lastSendTimes = mutableMapOf<String, Long>()
    
    /**
     * Check if enough time has passed since last send for this key.
     * @param key Identifier for the rate-limited operation
     * @return true if should proceed, false if should skip (too soon)
     */
    fun shouldSend(key: String = "default"): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastTime = lastSendTimes[key] ?: 0
        
        if (now - lastTime >= minIntervalMs) {
            lastSendTimes[key] = now
            return true
        }
        return false
    }
    
    /**
     * Reset rate limiter for a specific key.
     */
    fun reset(key: String = "default") {
        lastSendTimes.remove(key)
    }
    
    /**
     * Reset all rate limiters.
     */
    fun resetAll() {
        lastSendTimes.clear()
    }
}
