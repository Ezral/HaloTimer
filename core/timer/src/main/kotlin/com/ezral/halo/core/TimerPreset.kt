package com.ezral.halo.core

import kotlinx.serialization.Serializable

/** Keeps the 1.3.0 storage format; sequence presets apply only sequence settings. */
@Serializable
data class TimerPreset(val id: String, val name: String, val definition: Definition) {
    fun forSlot(slot: Definition): Definition = slot.copy(
        steps = definition.steps.map { it.copy() }, repetitions = definition.repetitions,
        hoursEnabled = slot.hoursEnabled || definition.hoursEnabled || definition.steps.any { it.durationMs > MAX_MS },
    )
}

fun Definition.presetConfiguration(): Definition = Definition(
    id = 0, sequence = true, hoursEnabled = hoursEnabled, repetitions = repetitions,
    steps = steps.map { it.copy() },
)

/** Legacy single-timer entries are retained on disk but are not sequence choices. */
fun Snapshot.sequencePresets(): List<TimerPreset> = presets.filter { it.definition.sequence }

fun presetNameError(name: String, presets: List<TimerPreset>, replacingId: String? = null): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length) > 40 -> "Use a preset name of 1–40 characters"
        presets.any { it.id != replacingId && it.name.equals(trimmed, ignoreCase = true) } -> "A preset with this name already exists"
        else -> null
    }
}
