package com.ezral.halo.core

import kotlinx.serialization.Serializable

/** Reusable configuration only: never a running session, deadline or device preference. */
@Serializable
data class TimerPreset(val id: String, val name: String, val definition: Definition) {
    fun forSlot(slot: Definition): Definition = definition.copy(
        id = slot.id, active = slot.active, hidden = slot.hidden,
        dock = slot.dock, x = slot.x, y = slot.y,
    )
}

fun Definition.presetConfiguration(): Definition = copy(
    id = 0, active = true, hidden = false, dock = DockSide.NONE, x = .82f, y = .20f,
    steps = steps.map { it.copy() },
)

fun presetNameError(name: String, presets: List<TimerPreset>, replacingId: String? = null): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length) > 40 -> "Use a preset name of 1–40 characters"
        presets.any { it.id != replacingId && it.name.equals(trimmed, ignoreCase = true) } -> "A preset with this name already exists"
        else -> null
    }
}
