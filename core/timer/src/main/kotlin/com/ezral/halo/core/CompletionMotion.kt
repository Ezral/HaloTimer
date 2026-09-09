package com.ezral.halo.core

/** A monotonic reveal, hold, and reverse reveal, including early dismissal mid-entry. */
class CompletionMotion(private val holdMs: Long, private val transitionMs: Long = 420) {
    private var closeAt: Long? = null
    private var closeFrom = 1f
    fun close(elapsed: Long) {
        if (closeAt != null) return
        closeFrom = progress(elapsed)
        closeAt = elapsed
    }
    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    fun progress(elapsed: Long): Float {
        closeAt?.let { return closeFrom * (1 - smooth((elapsed - it).toFloat() / transitionMs.coerceAtLeast(1))) }
        if (elapsed < transitionMs) return smooth(elapsed.toFloat() / transitionMs.coerceAtLeast(1))
        val closing = elapsed - transitionMs - holdMs
        return if (closing < 0) 1f else 1 - smooth(closing.toFloat() / transitionMs.coerceAtLeast(1))
    }
    fun finished(elapsed: Long) = elapsed >= (closeAt ?: (transitionMs + holdMs)) + transitionMs
}
